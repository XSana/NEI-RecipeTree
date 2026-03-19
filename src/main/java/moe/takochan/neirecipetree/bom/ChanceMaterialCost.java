package moe.takochan.neirecipetree.bom;

import net.minecraft.item.ItemStack;

public class ChanceMaterialCost {

    public final ItemStack stack;
    public long amount;
    public float chance;
    private long minBatch;

    public ChanceMaterialCost(ItemStack stack, long amount, float chance) {
        this.stack = stack.copy();
        this.stack.stackSize = 1;
        this.amount = amount;
        this.chance = chance;
        this.minBatch = amount;
    }

    public void merge(long addAmount, float addChance) {
        float totalEff = amount * chance + addAmount * addChance;
        amount += addAmount;
        chance = totalEff / amount;
    }

    public void minBatch(long batch) {
        if (batch > minBatch) {
            minBatch = batch;
        }
    }

    public long getMinBatch() {
        return minBatch;
    }
}
