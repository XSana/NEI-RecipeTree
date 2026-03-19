package moe.takochan.neirecipetree.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;

public class RecipeLookup {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final int CACHE_SIZE = 256;
    private static final Map<ItemStackKey, List<NEIRecipeRef>> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<ItemStackKey, List<NEIRecipeRef>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public static List<NEIRecipeRef> findRecipes(ItemStack target) {
        if (target == null || target.getItem() == null) return Collections.emptyList();

        ItemStackKey key = ItemStackKey.of(target);
        if (key == null) return Collections.emptyList();

        List<NEIRecipeRef> cached = cache.get(key);
        if (cached != null) return cached;

        List<NEIRecipeRef> refs = new ArrayList<>();
        try {
            ArrayList<ICraftingHandler> handlers = GuiCraftingRecipe.getCraftingHandlers("item", target);
            LOG.debug("RecipeLookup: {} handlers found for {}", handlers.size(), target.getDisplayName());
            for (ICraftingHandler handler : handlers) {
                int numRecipes = handler.numRecipes();
                LOG.debug(
                    "  Handler {} has {} recipes",
                    handler.getClass()
                        .getSimpleName(),
                    numRecipes);
                for (int i = 0; i < numRecipes; i++) {
                    refs.add(new NEIRecipeRef(handler, i));
                }
            }
        } catch (Exception e) {
            LOG.warn("RecipeLookup: Exception finding recipes for {}: {}", target.getDisplayName(), e.getMessage());
        }

        LOG.debug("RecipeLookup: total {} recipe refs for {}", refs.size(), target.getDisplayName());
        List<NEIRecipeRef> result = Collections.unmodifiableList(refs);
        cache.put(key, result);
        return result;
    }

    public static NEIRecipeRef findFirstRecipe(ItemStack target) {
        List<NEIRecipeRef> recipes = findRecipes(target);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    /**
     * Returns a recipe at the given index (wrapping around) from available recipes.
     */
    public static NEIRecipeRef findRecipeAt(ItemStack target, int index) {
        List<NEIRecipeRef> recipes = findRecipes(target);
        if (recipes.isEmpty()) return null;
        return recipes.get(((index % recipes.size()) + recipes.size()) % recipes.size());
    }

    public static int getRecipeCount(ItemStack target) {
        return findRecipes(target).size();
    }

    /**
     * Returns the number of unique handler types that produce this item.
     */
    public static int getHandlerTypeCount(ItemStack target) {
        List<NEIRecipeRef> recipes = findRecipes(target);
        java.util.Set<String> handlerIds = new java.util.HashSet<>();
        for (NEIRecipeRef ref : recipes) {
            handlerIds.add(ref.handler.getHandlerId());
        }
        return handlerIds.size();
    }

    public static void clearCache() {
        cache.clear();
    }
}
