package moe.takochan.neirecipetree.bom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.PositionedStack;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;
import moe.takochan.neirecipetree.recipe.RecipeAdapter;

public class MaterialNode {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final int MAX_DEPTH = 64;

    public final ItemStack ingredient;
    public final ItemStack[] permutations;
    public final ItemStack remainder;
    public NEIRecipeRef recipe;
    public List<MaterialNode> children;
    public float consumeChance = 1;
    public float produceChance = 1;
    public long amount = 1;
    public long divisor = 1;
    public long remainderAmount = 0;
    public boolean catalyst = false;
    public FoldState state = FoldState.EXPANDED;
    public ProgressState progress = ProgressState.UNSTARTED;
    public long neededBatches = 0;
    public long totalNeeded = 0;

    public MaterialNode(ItemStack stack) {
        this.ingredient = stack.copy();
        this.ingredient.stackSize = 1;
        this.amount = stack.stackSize;
        this.permutations = new ItemStack[] { this.ingredient };
        this.remainder = RecipeAdapter.getContainerItem(stack);
        if (this.remainder != null) {
            this.remainderAmount = this.remainder.stackSize;
            this.remainder.stackSize = 1;
        }
        this.catalyst = RecipeAdapter.isCatalyst(stack);
        // Catalysts default to collapsed since they are not consumed
        if (this.catalyst) {
            this.state = FoldState.COLLAPSED;
        }
    }

    public MaterialNode(PositionedStack ps) {
        ItemStack first = ps.items[0];
        this.ingredient = first.copy();
        this.ingredient.stackSize = 1;
        this.amount = first.stackSize;
        this.permutations = RecipeAdapter.getPermutations(ps);
        this.remainder = RecipeAdapter.getContainerItem(first);
        if (this.remainder != null) {
            this.remainderAmount = this.remainder.stackSize;
            this.remainder.stackSize = 1;
        }
        this.catalyst = RecipeAdapter.isCatalyst(first);
        // Catalysts default to collapsed since they are not consumed
        if (this.catalyst) {
            this.state = FoldState.COLLAPSED;
        }
    }

    public MaterialNode(MaterialNode node) {
        this.ingredient = node.ingredient;
        this.permutations = node.permutations;
        this.remainder = node.remainder;
        this.recipe = node.recipe;
        this.amount = node.amount;
        this.divisor = node.divisor;
        this.remainderAmount = node.remainderAmount;
    }

    public void recalculate(MaterialTree tree) {
        // Use IdentityHashMap as a set — comparing recipe objects by reference (==), not equals().
        // This matches EMI's approach: each recipe object is unique, so reference equality avoids
        // false cycle detection when different items happen to share the same handler class + index.
        recalculate(tree, new IdentityHashMap<>(), 0);
    }

    private void recalculate(MaterialTree tree, IdentityHashMap<NEIRecipeRef, Boolean> used, int depth) {
        if (depth > MAX_DEPTH) {
            LOG.warn("Max recursion depth reached for {}", ingredient.getDisplayName());
            return;
        }

        // Check if this ingredient was explicitly cleared (null in resolutions map)
        ItemStackKey key = ItemStackKey.of(ingredient);
        if (key != null && tree.resolutions.containsKey(key) && tree.resolutions.get(key) == null) {
            // Explicitly cleared by user — revert to leaf node
            this.recipe = null;
            this.children = null;
            return;
        }

        // Determine which recipe to use: tree resolution takes priority
        NEIRecipeRef resolvedRecipe = tree.getRecipe(ingredient);
        if (resolvedRecipe == null) {
            resolvedRecipe = this.recipe; // Keep existing if tree has none
        }

        if (resolvedRecipe == null) return; // No recipe available, leaf node

        // Cycle detection: compare by object reference (identity), like EMI
        if (used.containsKey(resolvedRecipe)) {
            return;
        }
        used.put(resolvedRecipe, Boolean.TRUE);

        // Pin this recipe in tree resolutions so the same reference is used on subsequent recalculates.
        if (key != null && !tree.resolutions.containsKey(key)) {
            tree.resolutions.put(key, resolvedRecipe);
        }

        // Only rebuild children if recipe actually changed or hasn't been built yet
        if (this.children == null || this.recipe == null
            || (this.recipe != resolvedRecipe && !this.recipe.equals(resolvedRecipe))) {
            defineRecipe(resolvedRecipe);
        }

        if (children != null) {
            for (MaterialNode node : children) {
                if (!node.catalyst) {
                    node.recalculate(tree, used, depth + 1);
                }
            }
        }
        used.remove(resolvedRecipe); // Backtrack — same recipe can appear in other branches
    }

    public void defineRecipe(NEIRecipeRef recipeRef) {
        produceChance = 1;
        if (recipeRef == null) return;

        // If the same recipe is already set and children exist, preserve them (keeps fold state)
        if (this.recipe != null && (this.recipe == recipeRef || this.recipe.equals(recipeRef))
            && this.children != null) {
            return;
        }

        // Save existing children fold states before recreating
        Map<ItemStackKey, FoldState> savedFoldStates = new HashMap<>();
        if (this.children != null) {
            for (MaterialNode child : this.children) {
                ItemStackKey key = ItemStackKey.of(child.ingredient);
                if (key != null) {
                    savedFoldStates.put(key, child.state);
                }
            }
        }

        this.recipe = recipeRef;
        divisor = 0;

        // Calculate divisor from recipe output
        PositionedStack outputPs = recipeRef.getOutputPositioned();
        if (outputPs != null) {
            for (ItemStack outputItem : outputPs.items) {
                if (ItemStackKey.matches(outputItem, ingredient)) {
                    divisor += outputItem.stackSize;
                    break;
                }
            }
        }

        // Also check other outputs
        List<PositionedStack> otherOutputs = recipeRef.getOtherOutputs();
        if (otherOutputs != null) {
            for (PositionedStack ps : otherOutputs) {
                if (ps != null && ps.items.length > 0 && ItemStackKey.matches(ps.items[0], ingredient)) {
                    divisor += ps.items[0].stackSize;
                }
            }
        }

        if (divisor <= 0) {
            divisor = 1;
        }

        // Build children from recipe inputs
        this.children = new ArrayList<>();
        List<PositionedStack> inputs = recipeRef.getInputs();
        if (inputs != null) {
            outer: for (PositionedStack inputPs : inputs) {
                if (inputPs == null || inputPs.items.length == 0) continue;

                ItemStack inputStack = inputPs.items[0];
                if (inputStack == null || inputStack.getItem() == null) continue;

                // Merge duplicate inputs
                for (MaterialNode existing : children) {
                    if (ItemStackKey.matches(inputStack, existing.ingredient)) {
                        existing.amount += inputStack.stackSize;
                        ItemStack existingRemainder = RecipeAdapter.getContainerItem(inputStack);
                        if (existingRemainder != null) {
                            existing.remainderAmount += existingRemainder.stackSize;
                        }
                        continue outer;
                    }
                }

                MaterialNode child = new MaterialNode(inputPs);
                // Restore fold state from previous children
                ItemStackKey childKey = ItemStackKey.of(child.ingredient);
                if (childKey != null && savedFoldStates.containsKey(childKey)) {
                    child.state = savedFoldStates.get(childKey);
                }
                children.add(child);
            }
        }
    }
}
