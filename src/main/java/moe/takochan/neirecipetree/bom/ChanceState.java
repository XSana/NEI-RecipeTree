package moe.takochan.neirecipetree.bom;

public class ChanceState {

    public static final ChanceState DEFAULT = new ChanceState(1.0f, false);

    private final float chance;
    private final boolean chanced;

    public ChanceState(float chance, boolean chanced) {
        this.chance = chance;
        this.chanced = chanced;
    }

    public float chance() {
        return chance;
    }

    public boolean chanced() {
        return chanced;
    }

    public ChanceState produce(float produceChance) {
        if (produceChance == 1) return this;
        return new ChanceState(chance / produceChance, true);
    }

    public ChanceState consume(float consumeChance) {
        if (consumeChance == 1) return this;
        return new ChanceState(chance * consumeChance, true);
    }
}
