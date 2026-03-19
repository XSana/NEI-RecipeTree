package moe.takochan.neirecipetree.recipe;

import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.recipe.StackInfo;

public class ItemStackKey {

    private final Item item;
    private final int damage;
    private final NBTTagCompound nbt;
    private final int hashCode;

    public ItemStackKey(ItemStack stack) {
        this.item = stack.getItem();
        this.damage = stack.getItemDamage();
        this.nbt = stack.getTagCompound() != null ? (NBTTagCompound) stack.getTagCompound()
            .copy() : null;
        this.hashCode = computeHashCode();
    }

    private int computeHashCode() {
        int result = Item.getIdFromItem(item);
        result = 31 * result + damage;
        result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStackKey other)) return false;
        return item == other.item && damage == other.damage && Objects.equals(nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(item, 1, damage);
        if (nbt != null) {
            stack.setTagCompound((NBTTagCompound) nbt.copy());
        }
        return stack;
    }

    public static ItemStackKey of(ItemStack stack) {
        return stack != null && stack.getItem() != null ? new ItemStackKey(stack) : null;
    }

    public static boolean matches(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        // Try exact match first (item + damage + NBT)
        if (a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && Objects.equals(a.getTagCompound(), b.getTagCompound())) {
            return true;
        }
        // Fallback: compare as fluids if either is a fluid container/display
        try {
            FluidStack fluidA = StackInfo.getFluid(a);
            FluidStack fluidB = StackInfo.getFluid(b);
            if (fluidA != null && fluidB != null) {
                return fluidA.getFluid() == fluidB.getFluid();
            }
        } catch (Exception ignored) {}
        // Fallback: use NEI's own comparison (handles edge cases)
        return StackInfo.equalItemAndNBT(a, b, true);
    }
}
