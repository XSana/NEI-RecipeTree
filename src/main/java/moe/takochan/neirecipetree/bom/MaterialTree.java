package moe.takochan.neirecipetree.bom;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;

public class MaterialTree {

    public MaterialNode goal;
    public TreeCost cost = new TreeCost();
    public Map<ItemStackKey, NEIRecipeRef> resolutions = new HashMap<>();
    public long batches = 1;

    public MaterialTree(NEIRecipeRef recipe) {
        ItemStack output = recipe.getOutput();
        if (output == null) {
            // Fallback: try to get output from other stacks
            java.util.List<ItemStack> allOutputs = recipe.getAllOutputs();
            if (!allOutputs.isEmpty()) {
                output = allOutputs.get(0);
            }
        }
        if (output == null || output.getItem() == null) {
            throw new IllegalArgumentException("Recipe has no valid output");
        }
        goal = new MaterialNode(output);
        goal.defineRecipe(recipe);
        // Store root recipe in resolutions so recalculate uses the same reference.
        ItemStackKey key = ItemStackKey.of(output);
        if (key != null) {
            resolutions.put(key, recipe);
        }
        recalculate();
    }

    public NEIRecipeRef getRecipe(ItemStack stack) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null && resolutions.containsKey(key)) {
            return resolutions.get(key);
        }
        return BoM.getRecipe(stack);
    }

    public void addResolution(ItemStack stack, NEIRecipeRef recipe) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key != null) {
            resolutions.put(key, recipe);
            recalculate();
        }
    }

    public void recalculate() {
        goal.recalculate(this);
    }

    public void calculateCost() {
        cost.calculate(goal, batches);
    }

    public void calculateProgress(Map<ItemStackKey, Long> inventory) {
        cost.calculateProgress(goal, batches, inventory);
    }
}
