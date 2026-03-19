package moe.takochan.neirecipetree.nei;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.RecipeHandlerRef;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.gui.GuiRecipeTree;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;

public class RecipeTreeButton extends GuiRecipeButton {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final int BUTTON_ID_OFFSET = 100;

    public RecipeTreeButton(RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, BUTTON_ID_OFFSET + handlerRef.recipeIndex, "T");
    }

    @Override
    protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
        GL11.glColor4f(1, 1, 1, this.enabled ? 1 : 0.5f);
        int textColor = mouseOver ? 0xFF55FF55 : 0xFF88CC88;
        minecraft.fontRenderer.drawStringWithShadow("T", this.xPosition + 3, this.yPosition + 2, textColor);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        try {
            NEIRecipeRef recipeRef = new NEIRecipeRef(handlerRef.handler, handlerRef.recipeIndex);
            BoM.setGoal(recipeRef);
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new GuiRecipeTree(mc.currentScreen));
        } catch (Exception e) {
            LOG.warn("Failed to open recipe tree: {}", e.getMessage());
        }
    }

    @Override
    public List<String> handleTooltip(List<String> currenttip) {
        currenttip.add("\u00a7a" + StatCollector.translateToLocal("neirecipetree.button.tooltip"));
        currenttip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.button.description"));
        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        return hotkeys;
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyID) {
        // No hotkey handling
    }

    @Override
    public void drawItemOverlay() {
        // No item overlay
    }
}
