package moe.takochan.neirecipetree.nei;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.RecipeHandlerRef;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;

/**
 * Button shown in NEI recipe view when a tree resolution is pending.
 * Only appears on recipes that produce the pending ingredient.
 * Clicking selects this recipe for the pending ingredient and returns to the tree.
 */
public class RecipeSelectButton extends GuiRecipeButton {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final int BUTTON_ID_OFFSET = 200;

    public RecipeSelectButton(RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, BUTTON_ID_OFFSET + handlerRef.recipeIndex, "S");
    }

    @Override
    protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
        GL11.glColor4f(1, 1, 1, this.enabled ? 1 : 0.5f);
        int textColor;
        if (!this.enabled) {
            textColor = 0xFF666666;
        } else {
            textColor = mouseOver ? 0xFFFFFF55 : 0xFFFFAA00;
        }
        minecraft.fontRenderer.drawStringWithShadow("S", this.xPosition + 3, this.yPosition + 2, textColor);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (BoM.pendingResolution == null || BoM.tree == null) return;

        try {
            NEIRecipeRef recipeRef = new NEIRecipeRef(handlerRef.handler, handlerRef.recipeIndex);

            // Double-check: only resolve if this recipe actually produces the target
            boolean valid = false;
            for (ItemStack output : recipeRef.getAllOutputs()) {
                if (ItemStackKey.matches(output, BoM.pendingResolution)) {
                    valid = true;
                    break;
                }
            }

            if (valid) {
                BoM.resolveAndReturn(recipeRef);
            } else {
                LOG.warn("Recipe does not produce the pending ingredient, ignoring");
            }
        } catch (Exception e) {
            LOG.warn("Failed to select recipe: {}", e.getMessage());
        }
    }

    @Override
    public List<String> handleTooltip(List<String> currenttip) {
        if (this.enabled) {
            currenttip.add("\u00a7e" + StatCollector.translateToLocal("neirecipetree.button.select"));
            currenttip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.button.select_desc"));
        } else {
            currenttip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.button.select_disabled"));
        }
        if (BoM.pendingResolution != null) {
            currenttip.add("\u00a77" + BoM.pendingResolution.getDisplayName());
        }
        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        return hotkeys;
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyID) {}

    @Override
    public void drawItemOverlay() {}
}
