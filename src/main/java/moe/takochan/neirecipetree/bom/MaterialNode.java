package moe.takochan.neirecipetree.bom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.PositionedStack;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;
import moe.takochan.neirecipetree.recipe.RecipeAdapter;
import moe.takochan.neirecipetree.recipe.RecipeInputKey;

public class MaterialNode {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final int MAX_DEPTH = 64;

    public ItemStack ingredient;
    public final ItemStack[] permutations;
    public ItemStack remainder;
    public final List<RecipeInputKey> sourceInputKeys = new ArrayList<>();
    private final List<Map<ItemStackKey, ItemStack>> sourcePermutationSets = new ArrayList<>();
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
        this(ps, ps.items[0]);
    }

    public MaterialNode(PositionedStack ps, ItemStack selected) {
        this(ps, selected, null);
    }

    public MaterialNode(PositionedStack ps, ItemStack selected, RecipeInputKey sourceInputKey) {
        this.ingredient = selected.copy();
        this.ingredient.stackSize = 1;
        this.amount = selected.stackSize;
        this.permutations = RecipeAdapter.getPermutations(ps);
        this.remainder = RecipeAdapter.getContainerItem(selected);
        if (this.remainder != null) {
            this.remainderAmount = this.remainder.stackSize;
            this.remainder.stackSize = 1;
        }
        this.catalyst = RecipeAdapter.isCatalyst(selected);
        if (sourceInputKey != null) {
            addSourceInput(ps, sourceInputKey);
        }
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
        this.sourceInputKeys.addAll(node.sourceInputKeys);
        for (Map<ItemStackKey, ItemStack> sourcePermutations : node.sourcePermutationSets) {
            this.sourcePermutationSets.add(copyPermutationSet(sourcePermutations));
        }
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

        if (key != null && BoM.userExpandedNodes.contains(key)) {
            this.state = FoldState.EXPANDED;
        }

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
            outer: for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                PositionedStack inputPs = inputs.get(inputIndex);
                if (inputPs == null || inputPs.items.length == 0) continue;

                ItemStack inputStack = getSelectedInput(recipeRef, inputIndex, inputPs);
                if (inputStack == null || inputStack.getItem() == null) continue;
                RecipeInputKey inputKey = RecipeInputKey.of(recipeRef, inputIndex);

                // Merge duplicate inputs
                for (MaterialNode existing : children) {
                    if (ItemStackKey.matches(inputStack, existing.ingredient)) {
                        existing.amount += inputStack.stackSize;
                        existing.addSourceInput(inputPs, inputKey);
                        ItemStack existingRemainder = RecipeAdapter.getContainerItem(inputStack);
                        if (existingRemainder != null) {
                            existing.remainderAmount += existingRemainder.stackSize;
                        }
                        continue outer;
                    }
                }

                MaterialNode child = new MaterialNode(inputPs, inputStack, inputKey);
                // Restore fold state from previous children
                ItemStackKey childKey = ItemStackKey.of(child.ingredient);
                if (childKey != null && savedFoldStates.containsKey(childKey)) {
                    child.state = savedFoldStates.get(childKey);
                }
                children.add(child);
            }
        }
    }

    public List<ItemStack> getUniquePermutations() {
        if (sourcePermutationSets.isEmpty()) {
            return new ArrayList<>(collectUniquePermutations(permutations).values());
        }

        List<ItemStack> shared = new ArrayList<>();
        for (ItemStack candidate : sourcePermutationSets.get(0)
            .values()) {
            shared.add(candidate.copy());
        }
        for (int i = 1; i < sourcePermutationSets.size(); i++) {
            Map<ItemStackKey, ItemStack> sourcePermutations = sourcePermutationSets.get(i);
            shared.removeIf(candidate -> !matchesAny(candidate, sourcePermutations));
            if (shared.isEmpty()) {
                break;
            }
        }
        return shared;
    }

    public boolean hasMultiplePermutations() {
        return getUniquePermutations().size() > 1;
    }

    public ItemStackKey getSelectionKeyForSource(int sourceIndex, ItemStack selected) {
        if (sourceIndex >= 0 && sourceIndex < sourcePermutationSets.size()) {
            for (Map.Entry<ItemStackKey, ItemStack> candidate : sourcePermutationSets.get(sourceIndex)
                .entrySet()) {
                if (ItemStackKey.matches(selected, candidate.getValue())) {
                    return candidate.getKey();
                }
            }
        }
        return ItemStackKey.of(selected);
    }

    private void addSourceInput(PositionedStack input, RecipeInputKey inputKey) {
        sourceInputKeys.add(inputKey);
        sourcePermutationSets.add(collectUniquePermutations(RecipeAdapter.getPermutations(input)));
    }

    private static Map<ItemStackKey, ItemStack> collectUniquePermutations(ItemStack[] candidates) {
        Map<ItemStackKey, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack candidate : candidates) {
            ItemStackKey key = ItemStackKey.of(candidate);
            if (key != null && !unique.containsKey(key)) {
                unique.put(key, candidate.copy());
            }
        }
        return unique;
    }

    private static Map<ItemStackKey, ItemStack> copyPermutationSet(Map<ItemStackKey, ItemStack> source) {
        Map<ItemStackKey, ItemStack> copy = new LinkedHashMap<>();
        for (Map.Entry<ItemStackKey, ItemStack> candidate : source.entrySet()) {
            copy.put(
                candidate.getKey(),
                candidate.getValue()
                    .copy());
        }
        return copy;
    }

    private static boolean matchesAny(ItemStack selected, Map<ItemStackKey, ItemStack> candidates) {
        for (ItemStack candidate : candidates.values()) {
            if (ItemStackKey.matches(selected, candidate)) {
                return true;
            }
        }
        return false;
    }

    public void selectIngredient(ItemStack selected) {
        ItemStack oldIngredient = ingredient;
        ingredient = selected.copy();
        ingredient.stackSize = 1;
        remainder = RecipeAdapter.getContainerItem(selected);
        remainderAmount = 0;
        if (remainder != null) {
            remainderAmount = remainder.stackSize * Math.max(1, sourceInputKeys.size());
            remainder.stackSize = 1;
        }
        catalyst = RecipeAdapter.isCatalyst(selected);
        recipe = null;
        children = null;
        divisor = 1;
        state = catalyst ? FoldState.COLLAPSED : FoldState.EXPANDED;
        BoM.clearRecipeState(oldIngredient);
    }

    private ItemStack getSelectedInput(NEIRecipeRef recipeRef, int inputIndex, PositionedStack input) {
        ItemStackKey selected = BoM.getInputSelection(recipeRef, inputIndex);
        if (selected != null) {
            for (ItemStack candidate : input.items) {
                if (candidate != null && candidate.getItem() != null && selected.equals(ItemStackKey.of(candidate))) {
                    BoM.rememberOreDictionarySelection(candidate, input.items);
                    return candidate;
                }
            }
        }

        // When no material was selected manually, prefer a candidate whose recipe is favorited in NEI.
        // This must happen before choosing the first permutation; otherwise favorites on later candidates
        // can never become the node ingredient and therefore never get a chance to auto-expand.
        for (ItemStack candidate : input.items) {
            if (candidate != null && candidate.getItem() != null && BoM.hasUsableFavoriteRecipe(candidate)) {
                BoM.rememberOreDictionarySelection(candidate, input.items);
                return candidate;
            }
        }

        // Reuse the exact representative already selected for a shared ore-dictionary entry elsewhere
        // in this tree. Recipe lookup still uses the concrete ItemStack; only the default choice is
        // normalized, so unrelated metadata/NBT variants are not globally treated as equal.
        ItemStack preferred = BoM.getPreferredOreDictionaryCandidate(input.items);
        if (preferred != null) {
            return preferred;
        }

        for (ItemStack candidate : input.items) {
            if (candidate != null && candidate.getItem() != null) {
                BoM.rememberOreDictionarySelection(candidate, input.items);
                return candidate;
            }
        }
        return null;
    }

    public void applyResolution(ItemStackKey targetKey, NEIRecipeRef recipeRef) {
        ItemStackKey selfKey = ItemStackKey.of(ingredient);
        if (selfKey != null && selfKey.equals(targetKey)) {
            defineRecipe(recipeRef);
            state = FoldState.EXPANDED;
        }

        if (children != null) {
            for (MaterialNode child : children) {
                child.applyResolution(targetKey, recipeRef);
            }
        }
    }

    public void pruneUnusedAutoResolutions(MaterialTree tree, boolean isRoot) {
        ItemStackKey key = ItemStackKey.of(ingredient);
        boolean manuallySelected = key != null && BoM.addedRecipes.containsKey(key);

        if (!isRoot && totalNeeded <= 0 && !manuallySelected) {
            recipe = null;
            children = null;
            divisor = 1;
            state = FoldState.COLLAPSED;
            if (key != null) {
                BoM.userExpandedNodes.remove(key);
            }
            return;
        }

        if (children != null) {
            for (MaterialNode child : children) {
                child.pruneUnusedAutoResolutions(tree, false);
            }
        }
    }
}
