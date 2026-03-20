package moe.takochan.neirecipetree.gui;

import moe.takochan.neirecipetree.bom.ChanceState;
import moe.takochan.neirecipetree.bom.MaterialNode;

public class TreeNode {

    public static final int FRAME_HEIGHT = 20;
    public static final int ICON_SIZE = 16;
    public static final int RECIPE_NODE_BASE_WIDTH = 20;
    public static final int ITEM_NODE_BASE_WIDTH = 20;
    public static final int RECIPE_ICON_X = -4;
    public static final int RECIPE_ICON_Y = -4;
    public static final int RECIPE_ICON_SIZE = 9;
    public static final int ITEM_ICON_Y = 2;

    public final MaterialNode materialNode;
    public int x;
    public int y;
    public int width;
    public int midOffset;
    public long amount;
    public ChanceState chance;

    public TreeNode(MaterialNode materialNode, int x, int y, int width, int midOffset, long amount,
        ChanceState chance) {
        this.materialNode = materialNode;
        this.x = x;
        this.y = y;
        this.width = width;
        this.midOffset = midOffset;
        this.amount = amount;
        this.chance = chance;
    }

    public boolean hasRecipeIcon() {
        return materialNode.recipe != null;
    }

    public int getCenterX() {
        return x + width / 2;
    }

    public int getBottomY() {
        return y + FRAME_HEIGHT;
    }

    public int getItemIconX() {
        return x + 2;
    }

    public int getItemIconY() {
        return y + ITEM_ICON_Y;
    }

    public int getRecipeIconX() {
        return x + RECIPE_ICON_X;
    }

    public int getRecipeIconY() {
        return y + RECIPE_ICON_Y;
    }

    public boolean containsFrame(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + FRAME_HEIGHT;
    }

    public boolean containsItem(double mouseX, double mouseY) {
        int itemX = getItemIconX();
        int itemY = getItemIconY();
        return mouseX >= itemX && mouseX < itemX + ICON_SIZE && mouseY >= itemY && mouseY < itemY + ICON_SIZE;
    }

    public boolean containsRecipe(double mouseX, double mouseY) {
        if (!hasRecipeIcon()) {
            return false;
        }
        int recipeX = getRecipeIconX();
        int recipeY = getRecipeIconY();
        return mouseX >= recipeX && mouseX < recipeX + RECIPE_ICON_SIZE
            && mouseY >= recipeY
            && mouseY < recipeY + RECIPE_ICON_SIZE;
    }
}
