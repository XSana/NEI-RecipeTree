package moe.takochan.neirecipetree.nei;

import net.minecraft.item.ItemStack;

import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.RecipeHandlerRef;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;

public class RecipeTreeButtonHandler {

    @SubscribeEvent
    public void onRecipeButtons(UpdateRecipeButtonsEvent.Post event) {
        RecipeHandlerRef handlerRef = event.recipeWidget.getRecipeHandlerRef();
        if (handlerRef == null) return;

        int x = Math.min(168, event.recipeWidget.w) - GuiRecipeButton.BUTTON_WIDTH;
        int y = event.recipeWidget.h - GuiRecipeButton.BUTTON_HEIGHT - 6;
        int existingButtons = event.buttonList.size();
        y -= existingButtons * (GuiRecipeButton.BUTTON_HEIGHT + 1);

        if (BoM.pendingResolution != null && BoM.tree != null) {
            // Always show select button when in resolution mode (like EMI's RecipeScreen.resolve)
            RecipeSelectButton selectBtn = new RecipeSelectButton(handlerRef, x, y);
            // Disable button if this recipe doesn't produce the pending ingredient
            if (!recipeProduces(handlerRef, BoM.pendingResolution)) {
                selectBtn.enabled = false;
            }
            event.buttonList.add(selectBtn);
            return;
        }

        event.buttonList.add(new RecipeTreeButton(handlerRef, x, y));
    }

    private static boolean recipeProduces(RecipeHandlerRef handlerRef, ItemStack target) {
        try {
            NEIRecipeRef ref = new NEIRecipeRef(handlerRef.handler, handlerRef.recipeIndex);
            for (ItemStack output : ref.getAllOutputs()) {
                if (ItemStackKey.matches(output, target)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
