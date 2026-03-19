package moe.takochan.neirecipetree.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;

public class NEIRecipeRef {

    public final IRecipeHandler handler;
    public final int recipeIndex;

    public NEIRecipeRef(IRecipeHandler handler, int recipeIndex) {
        this.handler = handler;
        this.recipeIndex = recipeIndex;
    }

    public List<PositionedStack> getInputs() {
        return handler.getIngredientStacks(recipeIndex);
    }

    public PositionedStack getOutputPositioned() {
        return handler.getResultStack(recipeIndex);
    }

    public ItemStack getOutput() {
        PositionedStack result = handler.getResultStack(recipeIndex);
        return result != null && result.items.length > 0 ? result.items[0] : null;
    }

    public List<PositionedStack> getOtherOutputs() {
        return handler.getOtherStacks(recipeIndex);
    }

    public List<ItemStack> getAllOutputs() {
        List<ItemStack> outputs = new ArrayList<>();
        PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null) {
            for (ItemStack item : result.items) {
                if (item != null) {
                    outputs.add(item);
                }
            }
        }
        List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        if (others != null) {
            for (PositionedStack ps : others) {
                if (ps != null && ps.items.length > 0) {
                    outputs.add(ps.items[0]);
                }
            }
        }
        return outputs;
    }

    public String getHandlerName() {
        // Use getRecipeName() for display name (e.g. "Assembler" instead of "gt.recipe.assembler")
        try {
            String name = handler.getRecipeName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Exception ignored) {}
        HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
        return info != null ? info.getHandlerName() : handler.getHandlerId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NEIRecipeRef other)) return false;
        return recipeIndex == other.recipeIndex && Objects.equals(handler.getHandlerId(), other.handler.getHandlerId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(handler.getHandlerId(), recipeIndex);
    }
}
