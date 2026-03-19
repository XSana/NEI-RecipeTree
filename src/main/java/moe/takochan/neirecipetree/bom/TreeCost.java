package moe.takochan.neirecipetree.bom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;

public class TreeCost {

    public Map<ItemStackKey, FlatMaterialCost> costs = new HashMap<>();
    public Map<ItemStackKey, ChanceMaterialCost> chanceCosts = new HashMap<>();
    public Map<ItemStackKey, FlatMaterialCost> remainders = new HashMap<>();
    public Map<ItemStackKey, ChanceMaterialCost> chanceRemainders = new HashMap<>();

    public void calculate(MaterialNode node, long batches) {
        costs.clear();
        chanceCosts.clear();
        remainders.clear();
        chanceRemainders.clear();
        calculateCost(node, batches * node.amount, ChanceState.DEFAULT, false);
    }

    public void calculateProgress(MaterialNode node, long batches, Map<ItemStackKey, Long> inventory) {
        costs.clear();
        chanceCosts.clear();
        remainders.clear();
        chanceRemainders.clear();
        if (inventory != null) {
            for (Map.Entry<ItemStackKey, Long> entry : inventory.entrySet()) {
                ItemStack stack = entry.getKey()
                    .toItemStack();
                remainders.put(entry.getKey(), new FlatMaterialCost(stack, entry.getValue()));
            }
        }
        calculateCost(node, batches * node.amount, ChanceState.DEFAULT, true);
    }

    private void addCost(ItemStack stack, long amount, long minBatch, ChanceState chance) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return;

        if (chance.chanced()) {
            ChanceMaterialCost existing = chanceCosts.get(key);
            if (existing != null) {
                existing.merge(amount, chance.chance());
            } else {
                chanceCosts.put(key, new ChanceMaterialCost(stack, amount, chance.chance()));
            }
            chanceCosts.get(key)
                .minBatch(minBatch);
        } else {
            FlatMaterialCost existing = costs.get(key);
            if (existing != null) {
                existing.amount += amount;
            } else {
                costs.put(key, new FlatMaterialCost(stack, amount));
            }
        }
    }

    private void addRemainder(ItemStack stack, long amount, ChanceState chance) {
        if (amount <= 0 || stack == null) return;

        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return;

        if (chance.chanced()) {
            ChanceMaterialCost existing = chanceRemainders.get(key);
            if (existing != null) {
                existing.merge(amount, chance.chance());
            } else {
                chanceRemainders.put(key, new ChanceMaterialCost(stack, amount, chance.chance()));
            }
        } else {
            FlatMaterialCost existing = remainders.get(key);
            if (existing != null) {
                existing.amount += amount;
            } else {
                remainders.put(key, new FlatMaterialCost(stack, amount));
            }
        }
    }

    private long getRemainder(ItemStack stack, long desired, boolean catalyst) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return 0;

        FlatMaterialCost remainder = remainders.get(key);
        if (remainder != null) {
            if (remainder.amount >= desired) {
                if (!catalyst) {
                    remainder.amount -= desired;
                    if (remainder.amount == 0) {
                        remainders.remove(key);
                    }
                }
                return desired;
            } else {
                if (!catalyst) {
                    remainders.remove(key);
                }
                return remainder.amount;
            }
        }
        return 0;
    }

    private double getChancedRemainder(ItemStack stack, double desired, boolean catalyst, ChanceState chance) {
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return 0;

        double given = 0;
        ChanceMaterialCost chancedRemainder = chanceRemainders.get(key);
        if (chancedRemainder != null) {
            double remainderEff = chancedRemainder.amount * chancedRemainder.chance;
            if (remainderEff >= desired) {
                if (!catalyst) {
                    chancedRemainder.amount = 1;
                    chancedRemainder.chance = (float) (remainderEff - desired);
                    if (chancedRemainder.chance == 0) {
                        chanceRemainders.remove(key);
                    }
                }
                return desired;
            } else {
                given = remainderEff;
                if (!catalyst) {
                    double leftover = remainderEff - (given * chance.chance());
                    if (leftover == 0) {
                        chanceRemainders.remove(key);
                    } else {
                        chancedRemainder.amount = 1;
                        chancedRemainder.chance = (float) leftover;
                    }
                }
            }
        } else {
            FlatMaterialCost remainder = remainders.get(key);
            if (remainder != null) {
                if (remainder.amount >= desired) {
                    if (!catalyst) {
                        remainder.amount -= (long) desired;
                        if (remainder.amount == 0) {
                            remainders.remove(key);
                        }
                    }
                    return desired;
                } else {
                    if (!catalyst) {
                        remainders.remove(key);
                    }
                    given += remainder.amount;
                }
            }
        }
        return given;
    }

    private void complete(MaterialNode node) {
        node.progress = ProgressState.COMPLETED;
        node.totalNeeded = 0;
        node.neededBatches = 0;
        if (node.children != null) {
            for (MaterialNode child : node.children) {
                complete(child);
            }
        }
    }

    private void calculateCost(MaterialNode node, long amount, ChanceState chance, boolean trackProgress) {
        if (trackProgress) {
            node.progress = ProgressState.UNSTARTED;
            node.totalNeeded = 0;
            node.neededBatches = 0;
        }

        boolean catalyst = node.catalyst;
        if (catalyst) {
            amount = node.amount;
        }

        NEIRecipeRef recipe = node.recipe;
        long original = amount;

        // Try to use remainders first
        for (ItemStack permStack : node.permutations) {
            if (chance.chanced()) {
                double desired = amount * chance.chance();
                double given = getChancedRemainder(permStack, desired, catalyst, chance);
                if (given > 0) {
                    double scaled = given / chance.chance();
                    amount -= (long) scaled;
                    if (amount > 0) {
                        chance = new ChanceState((float) ((amount - (scaled % 1)) * chance.chance() / amount), true);
                    }
                }
            } else {
                amount -= getRemainder(permStack, amount, catalyst);
            }
        }

        if (amount == 0) {
            if (trackProgress) {
                complete(node);
            }
            return;
        }
        if (trackProgress && amount != original) {
            node.progress = ProgressState.PARTIAL;
        }

        long effectiveCrafts = amount;
        if (recipe != null && node.children != null) {
            long minBatches = (long) Math.ceil(amount / (double) node.divisor);
            effectiveCrafts = minBatches * node.divisor;
            if (trackProgress) {
                node.totalNeeded = amount;
                node.neededBatches = minBatches;
            }
            ChanceState produced = chance.produce(node.produceChance);
            for (MaterialNode n : node.children) {
                calculateCost(n, minBatches * n.amount, produced.consume(n.consumeChance), trackProgress);
            }
            // Add remainders from overproduction
            ItemStack ingredientStack = node.ingredient;
            addRemainder(ingredientStack, effectiveCrafts - amount, produced);

            // Add byproduct remainders
            List<ItemStack> allOutputs = recipe.getAllOutputs();
            for (ItemStack output : allOutputs) {
                if (!ItemStackKey.matches(output, ingredientStack)) {
                    addRemainder(output, minBatches * output.stackSize, produced);
                }
            }

            // Add container item remainders
            for (MaterialNode n : node.children) {
                if (n.remainder != null && n.remainderAmount > 0) {
                    if (n.catalyst) {
                        addRemainder(n.remainder, n.remainderAmount, produced.consume(n.consumeChance));
                    } else {
                        addRemainder(n.remainder, minBatches * n.remainderAmount, produced.consume(n.consumeChance));
                    }
                }
            }
        } else {
            addCost(node.ingredient, amount, node.amount, chance);
        }
    }

    public long getIdealBatch(MaterialNode node, long total, long amount) {
        if (node.recipe == null) {
            return total;
        }
        if (node.divisor > 0) {
            long mod = node.divisor / gcd(node.divisor, amount);
            total *= mod / gcd(total, mod);
        }
        if (node.children != null) {
            for (MaterialNode n : node.children) {
                total = getIdealBatch(n, total, amount * n.amount);
            }
        }
        return total;
    }

    public static long gcd(long a, long b) {
        if (b == 0) return a;
        return gcd(b, a % b);
    }
}
