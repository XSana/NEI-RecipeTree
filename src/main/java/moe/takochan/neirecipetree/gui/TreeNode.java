package moe.takochan.neirecipetree.gui;

import moe.takochan.neirecipetree.bom.ChanceState;
import moe.takochan.neirecipetree.bom.MaterialNode;

public class TreeNode {

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
}
