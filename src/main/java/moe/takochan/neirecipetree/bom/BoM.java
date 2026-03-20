package moe.takochan.neirecipetree.bom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;
import moe.takochan.neirecipetree.recipe.RecipeLookup;

public class BoM {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");

    public static MaterialTree tree;
    public static Map<ItemStackKey, NEIRecipeRef> defaultRecipes = new HashMap<>();
    public static Map<ItemStackKey, NEIRecipeRef> addedRecipes = new HashMap<>();
    public static Set<NEIRecipeRef> disabledRecipes = new HashSet<>();
    public static Set<ItemStackKey> userExpandedNodes = new HashSet<>();
    /** Tracks the selected recipe index per ingredient for cycling */
    public static Map<ItemStackKey, Integer> recipeIndices = new HashMap<>();
    public static boolean craftingMode = false;
    /** Set when user clicks a tree node to select a recipe from NEI. Cleared after selection. */
    public static ItemStack pendingResolution = null;

    public static void setGoal(NEIRecipeRef recipe) {
        tree = new MaterialTree(recipe);
        craftingMode = false;
    }

    /**
     * Get the resolved recipe for a given item.
     * Priority: user overrides → favorites → single-recipe auto-resolve.
     * Items with multiple recipes and no favorite stay as leaves until user chooses.
     */
    public static NEIRecipeRef getRecipe(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return null;

        // 1. User explicit overrides (from tree interaction)
        NEIRecipeRef recipe = addedRecipes.get(key);
        if (recipe != null) return recipe;

        recipe = defaultRecipes.get(key);
        if (recipe != null && !disabledRecipes.contains(recipe)) {
            return recipe;
        }

        // 2. Check NEI favorite/bookmarked recipes
        NEIRecipeRef favoriteRecipe = findFavoriteRecipe(stack);
        if (favoriteRecipe != null) {
            defaultRecipes.put(key, favoriteRecipe);
            return favoriteRecipe;
        }

        // 3. Auto-resolve only if exactly 1 recipe exists (EMI behavior).
        // Multiple recipes → leave as leaf, user must choose.
        List<NEIRecipeRef> found = RecipeLookup.findRecipes(stack);
        if (found.size() == 1) {
            defaultRecipes.put(key, found.get(0));
            return found.get(0);
        }
        return null;
    }

    /**
     * Look up the NEI favorite recipe for a given item.
     * Returns null if no favorite exists or if it can't be resolved to a handler.
     */
    private static NEIRecipeRef findFavoriteRecipe(ItemStack stack) {
        try {
            RecipeId recipeId = FavoriteRecipes.getFavorite(stack);
            if (recipeId == null) return null;

            RecipeHandlerRef handlerRef = RecipeHandlerRef.of(recipeId);
            if (handlerRef != null) {
                return new NEIRecipeRef(handlerRef.handler, handlerRef.recipeIndex);
            }
        } catch (Exception e) {
            LOG.debug("Could not resolve favorite recipe for {}: {}", stack.getDisplayName(), e.getMessage());
        }
        return null;
    }

    public static void addRecipe(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null) {
            disabledRecipes.remove(recipe);
            addedRecipes.put(key, recipe);
            recalculate();
        }
    }

    public static void removeRecipe(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null) {
            addedRecipes.remove(key);
            disabledRecipes.add(recipe);
            recalculate();
        }
    }

    public static void addResolution(ItemStack stack, NEIRecipeRef recipe) {
        if (tree != null) {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key != null) {
                userExpandedNodes.add(key);
            }
            tree.addResolution(stack, recipe);
        }
    }

    public static void clearResolution(ItemStack stack) {
        if (tree != null) {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key != null) {
                tree.resolutions.put(key, null);
                addedRecipes.remove(key);
                defaultRecipes.remove(key);
                recipeIndices.remove(key);
                userExpandedNodes.remove(key);
                tree.recalculate();
            }
        }
    }

    public static void setResolution(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (tree == null || key == null || recipe == null) {
            return;
        }

        if (tree.resolutions.containsKey(key) || addedRecipes.containsKey(key) || defaultRecipes.containsKey(key)) {
            clearResolution(stack);
        }

        disabledRecipes.remove(recipe);
        addResolution(stack, recipe);
        addedRecipes.put(key, recipe);
    }

    /**
     * Cycle to the next handler type's first recipe for this ingredient.
     * Skips to the first recipe of the next different handler, so user cycles
     * through machine types (Furnace → Assembler → ...) instead of individual recipes.
     * Returns true if a new recipe was selected.
     */
    public static boolean cycleRecipe(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return false;

        List<NEIRecipeRef> recipes = RecipeLookup.findRecipes(stack);
        if (recipes.size() <= 1) return false;

        int currentIndex = recipeIndices.getOrDefault(key, 0);
        String currentHandlerId = recipes.get(currentIndex % recipes.size()).handler.getHandlerId();

        // Find the first recipe from a different handler type
        int nextIndex = (currentIndex + 1) % recipes.size();
        while (nextIndex != currentIndex) {
            if (!recipes.get(nextIndex).handler.getHandlerId()
                .equals(currentHandlerId)) {
                break;
            }
            nextIndex = (nextIndex + 1) % recipes.size();
        }

        recipeIndices.put(key, nextIndex);

        NEIRecipeRef nextRecipe = recipes.get(nextIndex);
        setResolution(stack, nextRecipe);
        return true;
    }

    /**
     * Get the current recipe index and total count for display.
     */
    public static int getRecipeIndex(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        return key != null ? recipeIndices.getOrDefault(key, 0) : 0;
    }

    private static void recalculate() {
        if (tree != null) {
            tree.recalculate();
        }
    }

    /**
     * Resolve the pending ingredient with the given recipe, then return to tree view.
     */
    public static void resolveAndReturn(NEIRecipeRef recipe) {
        if (pendingResolution != null && tree != null) {
            setResolution(pendingResolution, recipe);
            pendingResolution = null;
            Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.displayGuiScreen(new moe.takochan.neirecipetree.gui.GuiRecipeTree(null));
        }
    }

    public static void clear() {
        tree = null;
        defaultRecipes.clear();
        addedRecipes.clear();
        disabledRecipes.clear();
        userExpandedNodes.clear();
        recipeIndices.clear();
        craftingMode = false;
        pendingResolution = null;
        RecipeLookup.clearCache();
    }
}
