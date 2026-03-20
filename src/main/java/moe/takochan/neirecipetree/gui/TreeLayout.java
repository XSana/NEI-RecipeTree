package moe.takochan.neirecipetree.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import moe.takochan.neirecipetree.bom.ChanceState;
import moe.takochan.neirecipetree.bom.FoldState;
import moe.takochan.neirecipetree.bom.MaterialNode;

public class TreeLayout {

    public static final int NODE_WIDTH = TreeNode.RECIPE_NODE_BASE_WIDTH;
    public static final int NODE_HORIZONTAL_SPACING = 8;
    public static final int NODE_VERTICAL_SPACING = 36;

    /**
     * TreeVolume tracks horizontal bounds at each depth level, matching EMI's approach.
     * This prevents node overlap when subtrees have different widths at different depths.
     */
    public static class TreeVolume {

        public List<TreeNode> nodes = new ArrayList<>();
        private List<int[]> depthBounds = new ArrayList<>(); // [left, right] per depth

        public int getDepth() {
            return depthBounds.size();
        }

        public int getLeft(int depth) {
            return depth < depthBounds.size() ? depthBounds.get(depth)[0] : 0;
        }

        public int getRight(int depth) {
            return depth < depthBounds.size() ? depthBounds.get(depth)[1] : 0;
        }

        public int getMinLeft() {
            int min = Integer.MAX_VALUE;
            for (int[] b : depthBounds) {
                min = Math.min(min, b[0]);
            }
            return min == Integer.MAX_VALUE ? 0 : min;
        }

        public int getMaxRight() {
            int max = Integer.MIN_VALUE;
            for (int[] b : depthBounds) {
                max = Math.max(max, b[1]);
            }
            return max == Integer.MIN_VALUE ? 0 : max;
        }

        private void ensureDepth(int depth) {
            while (depthBounds.size() <= depth) {
                depthBounds.add(new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE });
            }
        }

        private void updateBounds(int depth, int left, int right) {
            ensureDepth(depth);
            int[] b = depthBounds.get(depth);
            b[0] = Math.min(b[0], left);
            b[1] = Math.max(b[1], right);
        }

        /**
         * Merge another TreeVolume to the right of this one, checking per-depth bounds
         * to find the minimum offset that avoids overlap at all depth levels.
         */
        public void addToRight(TreeVolume other, int spacing) {
            // Find minimum offset that prevents overlap at every shared depth
            int offset = Integer.MIN_VALUE;
            int sharedDepths = Math.min(getDepth(), other.getDepth());
            for (int i = 0; i < sharedDepths; i++) {
                if (depthBounds.get(i)[1] != Integer.MIN_VALUE && other.depthBounds.get(i)[0] != Integer.MAX_VALUE) {
                    offset = Math.max(offset, getRight(i) - other.getLeft(i) + spacing);
                }
            }
            if (offset == Integer.MIN_VALUE) {
                offset = spacing;
            }

            // Apply offset to all nodes in the other volume
            for (TreeNode node : other.nodes) {
                node.x += offset;
            }

            // Merge depth bounds
            for (int i = 0; i < other.getDepth(); i++) {
                int[] ob = other.depthBounds.get(i);
                if (ob[0] != Integer.MAX_VALUE) {
                    updateBounds(i, ob[0] + offset, ob[1] + offset);
                }
            }

            nodes.addAll(other.nodes);
        }

        public void addHead(TreeNode head, int depth) {
            nodes.add(0, head);
            updateBounds(depth, head.x, head.x + head.width);
        }
    }

    public static TreeVolume layout(MaterialNode root, long batches, long parentAmount, int depth, ChanceState chance) {
        TreeVolume volume = new TreeVolume();
        long amount = root.amount * batches / parentAmount;
        if (amount <= 0) {
            amount = root.amount;
        }
        int nodeWidth = getNodeWidth(root, amount, chance);

        if (shouldExpand(root)) {
            long minBatches = (long) Math.ceil(amount / (double) root.divisor);
            ChanceState produced = chance.produce(root.produceChance);

            TreeVolume combined = null;
            for (MaterialNode child : root.children) {
                TreeVolume childVol = layout(child, minBatches, 1, depth + 1, produced.consume(child.consumeChance));
                if (combined == null) {
                    combined = childVol;
                } else {
                    combined.addToRight(childVol, NODE_HORIZONTAL_SPACING);
                }
            }

            if (combined != null) {
                // Center parent above children
                int headX = (combined.getMinLeft() + combined.getMaxRight()) / 2 - nodeWidth / 2;
                int headY = depth * NODE_VERTICAL_SPACING;
                TreeNode head = new TreeNode(root, headX, headY, nodeWidth, 0, amount, chance);
                combined.addHead(head, depth);
                return combined;
            }
        }

        // Leaf node or collapsed
        int x = 0;
        int y = depth * NODE_VERTICAL_SPACING;
        TreeNode node = new TreeNode(root, x, y, nodeWidth, 0, amount, chance);
        volume.nodes.add(node);
        volume.updateBounds(depth, x, x + nodeWidth);
        return volume;
    }

    private static boolean shouldExpand(MaterialNode node) {
        return node.children != null && !node.children.isEmpty()
            && node.state == FoldState.EXPANDED
            && node.recipe != null;
    }

    public static int getNodeWidth(MaterialNode node, long amount, ChanceState chance) {
        int baseWidth = node.recipe != null ? TreeNode.RECIPE_NODE_BASE_WIDTH : TreeNode.ITEM_NODE_BASE_WIDTH;
        return baseWidth + getAmountOverflow(getNodeAmountText(amount, chance, false));
    }

    public static String getNodeAmountText(long amount, ChanceState chance, boolean formatted) {
        if (chance != null && chance.chanced()) {
            long adjusted = Math.round(amount * chance.chance());
            adjusted = Math.max(adjusted, amount);
            if (formatted) {
                return "\u00a76\u2248" + adjusted;
            }
            return "\u2248" + adjusted;
        }
        return String.valueOf(amount);
    }

    public static int getAmountOverflow(String amountText) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        if (fontRenderer == null) {
            return Math.max(0, amountText.length() * 6 - 14);
        }
        return Math.max(0, fontRenderer.getStringWidth(amountText) - 14);
    }

    public static int getNodeHeight(MaterialNode node) {
        if (!shouldExpand(node)) {
            return 0;
        }
        int maxChildHeight = 0;
        for (MaterialNode child : node.children) {
            maxChildHeight = Math.max(maxChildHeight, getNodeHeight(child));
        }
        return maxChildHeight + 1;
    }
}
