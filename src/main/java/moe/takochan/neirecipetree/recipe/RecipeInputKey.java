package moe.takochan.neirecipetree.recipe;

import java.util.Objects;

/** Identifies one input slot of one concrete NEI recipe. */
public final class RecipeInputKey {

    private final String handlerId;
    private final int recipeIndex;
    private final int inputIndex;

    public RecipeInputKey(String handlerId, int recipeIndex, int inputIndex) {
        this.handlerId = handlerId;
        this.recipeIndex = recipeIndex;
        this.inputIndex = inputIndex;
    }

    public static RecipeInputKey of(NEIRecipeRef recipe, int inputIndex) {
        return new RecipeInputKey(recipe.handler.getHandlerId(), recipe.recipeIndex, inputIndex);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeInputKey key)) return false;
        return recipeIndex == key.recipeIndex && inputIndex == key.inputIndex
            && Objects.equals(handlerId, key.handlerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handlerId, recipeIndex, inputIndex);
    }
}
