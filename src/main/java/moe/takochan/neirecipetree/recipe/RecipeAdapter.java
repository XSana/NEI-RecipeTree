package moe.takochan.neirecipetree.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;

public class RecipeAdapter {

    public static List<ItemStack> getInputStacks(NEIRecipeRef ref) {
        List<ItemStack> inputs = new ArrayList<>();
        List<PositionedStack> ingredients = ref.getInputs();
        if (ingredients != null) {
            for (PositionedStack ps : ingredients) {
                if (ps != null && ps.items.length > 0) {
                    inputs.add(ps.items[0].copy());
                }
            }
        }
        return inputs;
    }

    public static ItemStack[] getPermutations(PositionedStack ps) {
        if (ps == null || ps.items == null) return new ItemStack[0];
        ItemStack[] result = new ItemStack[ps.items.length];
        for (int i = 0; i < ps.items.length; i++) {
            result[i] = ps.items[i].copy();
        }
        return result;
    }

    public static ItemStack getOutputStack(NEIRecipeRef ref) {
        ItemStack output = ref.getOutput();
        return output != null ? output.copy() : null;
    }

    public static int getOutputAmount(NEIRecipeRef ref) {
        ItemStack output = ref.getOutput();
        return output != null ? output.stackSize : 1;
    }

    public static ItemStack getContainerItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Item item = stack.getItem();
        if (item.hasContainerItem(stack)) {
            return item.getContainerItem(stack);
        }
        return null;
    }

    public static boolean isCatalyst(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        ItemStack container = getContainerItem(stack);
        return container != null && container.getItem() == stack.getItem()
            && container.getItemDamage() == stack.getItemDamage();
    }

    public static boolean matchesAnyPermutation(ItemStack target, ItemStack[] permutations) {
        if (target == null || permutations == null) return false;
        for (ItemStack perm : permutations) {
            if (ItemStackKey.matches(target, perm)) return true;
        }
        return false;
    }
}
