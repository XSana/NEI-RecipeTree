package moe.takochan.neirecipetree.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class TreeRenderer {

    private static final RenderItem renderItem = new RenderItem();

    public static void setupZoom(float scale, double offX, double offY, int screenWidth, int screenHeight) {
        GL11.glPushMatrix();
        GL11.glTranslated(screenWidth / 2.0 + offX, screenHeight / 3.0 + offY, 0);
        GL11.glScalef(scale, scale, 1);
    }

    public static void teardownZoom() {
        GL11.glPopMatrix();
    }

    public static void drawLine(int x1, int y1, int x2, int y2, int color) {
        // Draw lines using thin rectangles
        if (x1 == x2) {
            // Vertical line
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            Gui.drawRect(x1, minY, x1 + 1, maxY, color);
        } else if (y1 == y2) {
            // Horizontal line
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            Gui.drawRect(minX, y1, maxX, y1 + 1, color);
        } else {
            // L-shaped connection: vertical then horizontal
            int midY = y2;
            Gui.drawRect(x1, y1, x1 + 1, midY, color);
            Gui.drawRect(Math.min(x1, x2), midY, Math.max(x1, x2), midY + 1, color);
        }
    }

    public static void drawItemStack(int x, int y, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;

        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        renderItem.renderItemAndEffectIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y);
        renderItem.renderItemOverlayIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y);

        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    public static void drawAmountText(int x, int y, String text, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        fr.drawStringWithShadow(text, x, y, color);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    public static void drawCenteredText(int x, int y, String text, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int width = fr.getStringWidth(text);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        fr.drawStringWithShadow(text, x - width / 2, y, color);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    public static void drawNodeBackground(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    public static void drawNodeBorder(int x, int y, int width, int height, int color) {
        // Top
        Gui.drawRect(x, y, x + width, y + 1, color);
        // Bottom
        Gui.drawRect(x, y + height - 1, x + width, y + height, color);
        // Left
        Gui.drawRect(x, y, x + 1, y + height, color);
        // Right
        Gui.drawRect(x + width - 1, y, x + width, y + height, color);
    }
}
