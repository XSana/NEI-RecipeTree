package moe.takochan.neirecipetree.bom;

import net.minecraft.item.ItemStack;

public class FlatMaterialCost {

    public final ItemStack stack;
    public long amount;

    public FlatMaterialCost(ItemStack stack, long amount) {
        this.stack = stack.copy();
        this.stack.stackSize = 1;
        this.amount = amount;
    }
}
