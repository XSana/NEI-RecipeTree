package moe.takochan.neirecipetree.gui;

import java.awt.Dimension;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.ItemPanels;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeTooltipLineHandler;
import moe.takochan.neirecipetree.bom.BoM;
import moe.takochan.neirecipetree.bom.ChanceMaterialCost;
import moe.takochan.neirecipetree.bom.ChanceState;
import moe.takochan.neirecipetree.bom.FlatMaterialCost;
import moe.takochan.neirecipetree.bom.FoldState;
import moe.takochan.neirecipetree.bom.MaterialNode;
import moe.takochan.neirecipetree.bom.ProgressState;
import moe.takochan.neirecipetree.recipe.ItemStackKey;
import moe.takochan.neirecipetree.recipe.NEIRecipeRef;
import moe.takochan.neirecipetree.recipe.RecipeLookup;

public class GuiRecipeTree extends GuiScreen {

    private static final Logger LOG = LogManager.getLogger("neirecipetree");
    private static final int EXPORT_PADDING = 24;
    private static final int EXPORT_BACKGROUND = 0xFF101018;
    private static final int EXPORT_SCALE = 4;
    private static final ResourceLocation BUTTON_ICONS_TEXTURE = new ResourceLocation("neirecipetree",
        "textures/gui/recipe_tree_buttons.png");
    private static final int NODE_BACKGROUND_COLOR = 0x88333333;
    private static final int NODE_BORDER_COLOR = 0xFF888888;
    private static final int NODE_LINE_COLOR = 0xFF727272;
    private static final int COLLAPSED_BORDER_COLOR = 0xFF6E96E6;
    private static final int SELECTABLE_BORDER_COLOR = 0xFF55FF55;
    private static final int CHANCED_BORDER_COLOR = 0xFFFFAA00;
    private static final float TRACE_HIGHLIGHT_STRENGTH = 0.65F;
    private static final float CHILD_HIGHLIGHT_STRENGTH = 0.55F;
    private static final int ITEM_HIGHLIGHT_COLOR = 0xFFF4F0B8;
    private static final int ACTION_BUTTON_SIZE = 18;
    private static final int ACTION_BUTTON_GAP = 4;
    private static final int ACTION_BUTTON_MARGIN = 4;
    private static final float BUTTON_TEXTURE_WIDTH = 36.0F;
    private static final float BUTTON_TEXTURE_HEIGHT = 18.0F;
    private static final int BATCH_PANEL_WIDTH = 92;
    private static final int BATCH_PANEL_HEIGHT = 24;
    private static final int BATCH_PANEL_MARGIN_RIGHT = 4;
    private static final int BATCH_PANEL_Y = ACTION_BUTTON_MARGIN + ACTION_BUTTON_SIZE + ACTION_BUTTON_GAP;
    private static final long MAX_BATCH_AMOUNT = 10000L;
    private static final float SMALL_TEXT_SCALE = 0.75F;
    private static int zoom = 0;
    private double offX = 0;
    private double offY = 0;
    private List<TreeNode> nodes = new ArrayList<>();
    private List<CostEntry> costs = new ArrayList<>();
    private List<CostEntry> remainderEntries = new ArrayList<>();
    private int lastMouseX;
    private int lastMouseY;
    private boolean dragging = false;
    private int nodeHeight = 0;
    private TreeNode hoveredNode = null;
    private HoverArea hoveredNodeArea = HoverArea.NONE;
    private CostEntry hoveredCost = null;

    // Recipe preview tooltip — reuses NEI's own RecipeTooltipLineHandler
    private RecipeTooltipLineHandler recipeTooltipHandler = null;
    private NEIRecipeRef recipeTooltipRef = null;

    private final GuiScreen parentScreen;

    // Export to bookmarks button
    private GuiRecipeTreeButton exportButton;
    // Export to image button
    private GuiRecipeTreeButton exportImageButton;

    private enum HoverArea {

        NONE,
        ITEM,
        RECIPE,
        FRAME
    }

    public GuiRecipeTree(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (BoM.tree != null) {
            offY = height / -3.0;
        }
        recalculateTree();

        int buttonY = ACTION_BUTTON_MARGIN;
        int buttonX = width - ACTION_BUTTON_MARGIN - ACTION_BUTTON_SIZE;
        exportButton = new GuiRecipeTreeButton(buttonX, buttonY, ButtonIcon.BOOKMARK,
            "neirecipetree.button.bookmark", "neirecipetree.button.bookmark.tooltip", this::exportToBookmarks);

        int imgButtonX = buttonX - ACTION_BUTTON_GAP - ACTION_BUTTON_SIZE;
        exportImageButton = new GuiRecipeTreeButton(imgButtonX, buttonY, ButtonIcon.EXPORT_IMAGE,
            "neirecipetree.button.export_image", "neirecipetree.button.export_image.tooltip", this::exportToImage);

        buttonList.add(exportButton);
        buttonList.add(exportImageButton);
    }

    private void recalculateTree() {
        nodes.clear();
        costs.clear();
        remainderEntries.clear();

        if (BoM.tree == null) return;

        // Always recalculate tree structure before layout and cost
        BoM.tree.recalculate();
        BoM.tree.calculateProgress(null);
        BoM.tree.goal.pruneUnusedAutoResolutions(BoM.tree, true);
        BoM.tree.calculateProgress(null);

        TreeLayout.TreeVolume volume = TreeLayout.layout(BoM.tree.goal, BoM.tree.batches, 1, 0, ChanceState.DEFAULT);
        nodes = volume.nodes;

        // Center nodes horizontally
        int horizontalOffset = (volume.getMaxRight() + volume.getMinLeft()) / 2;
        for (TreeNode node : nodes) {
            node.x -= horizontalOffset;
        }

        nodeHeight = TreeLayout.getNodeHeight(BoM.tree.goal);

        // Calculate costs
        BoM.tree.calculateCost();

        int sectionBaseY = (nodeHeight + 1) * TreeLayout.NODE_VERTICAL_SPACING + 20;

        // Collect cost entries, then center them
        List<CostEntry> tempCosts = new ArrayList<>();
        for (Map.Entry<ItemStackKey, FlatMaterialCost> entry : BoM.tree.cost.costs.entrySet()) {
            FlatMaterialCost cost = entry.getValue();
            tempCosts.add(new CostEntry(cost.stack, cost.amount, false));
        }
        for (Map.Entry<ItemStackKey, ChanceMaterialCost> entry : BoM.tree.cost.chanceCosts.entrySet()) {
            ChanceMaterialCost cost = entry.getValue();
            long effectiveAmount = (long) Math.ceil(cost.amount * cost.chance);
            tempCosts.add(new CostEntry(cost.stack, effectiveAmount, true));
        }

        // Layout costs centered horizontally
        int totalCostWidth = tempCosts.size() * 24;
        int costStartX = -totalCostWidth / 2;
        int costY = sectionBaseY + 15;
        for (int i = 0; i < tempCosts.size(); i++) {
            CostEntry tmp = tempCosts.get(i);
            tmp.x = costStartX + i * 24;
            tmp.y = costY;
            costs.add(tmp);
        }

        // Remainders section below costs
        List<CostEntry> tempRems = new ArrayList<>();
        for (Map.Entry<ItemStackKey, FlatMaterialCost> entry : BoM.tree.cost.remainders.entrySet()) {
            FlatMaterialCost rem = entry.getValue();
            if (rem.amount > 0) {
                tempRems.add(new CostEntry(rem.stack, rem.amount, false));
            }
        }

        int totalRemWidth = tempRems.size() * 24;
        int remStartX = -totalRemWidth / 2;
        int remY = costY + 45;
        for (int i = 0; i < tempRems.size(); i++) {
            CostEntry tmp = tempRems.get(i);
            tmp.x = remStartX + i * 24;
            tmp.y = remY;
            remainderEntries.add(tmp);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // Clean up NEI widget references to prevent state leaks
        recipeTooltipHandler = null;
        recipeTooltipRef = null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Dark semi-transparent background
        drawRect(0, 0, width, height, 0xDD000000);

        if (BoM.tree == null) {
            drawCenteredString(fontRendererObj, "No recipe tree set", width / 2, height / 2, 0xFFAAAAAA);
            return;
        }

        renderTreeLayer(width, height, getScale(), offX, offY, true, false, mouseX, mouseY);

        drawBatchPanel(mouseX, mouseY);
        drawHelpOverlay();

        // Draw buttons (super.drawScreen renders buttonList)
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw tooltips (in screen space, after teardownZoom)
        GuiRecipeTreeButton hoveredActionButton = getHoveredActionButton(mouseX, mouseY);
        if (hoveredActionButton != null) {
            clearRecipeTooltipPreview();
            drawHoveringText(hoveredActionButton.getToolTip(), mouseX, mouseY, fontRendererObj);
        } else if (isMouseOverBatchPanel(mouseX, mouseY)) {
            clearRecipeTooltipPreview();
            drawHoveringText(getBatchPanelTooltip(), mouseX, mouseY, fontRendererObj);
        } else if (hoveredNode != null && hoveredNodeArea == HoverArea.ITEM) {
            drawNodeTooltip(hoveredNode, mouseX, mouseY);
        } else if (hoveredNode != null && hoveredNodeArea == HoverArea.RECIPE) {
            drawRecipeTooltip(hoveredNode, mouseX, mouseY);
        } else {
            // Clear tooltip handler when not hovering a node to avoid stale NEI widget state
            clearRecipeTooltipPreview();
            if (hoveredCost != null) {
                drawCostTooltip(hoveredCost, mouseX, mouseY);
            }
        }
    }

    private GuiRecipeTreeButton getHoveredActionButton(int mouseX, int mouseY) {
        if (exportButton != null && exportButton.isMouseOver(mouseX, mouseY)) {
            return exportButton;
        }
        if (exportImageButton != null && exportImageButton.isMouseOver(mouseX, mouseY)) {
            return exportImageButton;
        }
        return null;
    }

    private void drawHelpOverlay() {
        String[] lines = new String[] {
            StatCollector.translateToLocal("neirecipetree.help.line1"),
            StatCollector.translateToLocal("neirecipetree.help.line2"),
            StatCollector.translateToLocal("neirecipetree.help.line3"),
            StatCollector.translateToLocal("neirecipetree.help.line4") };
        float lineHeight = fontRendererObj.FONT_HEIGHT * SMALL_TEXT_SCALE + 1.0F;
        int boxWidth = 0;
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, Math.round(fontRendererObj.getStringWidth(line) * SMALL_TEXT_SCALE));
        }
        int boxHeight = Math.round(lineHeight * lines.length) + 4;
        int boxX = 4;
        int boxY = height - boxHeight - 4;
        drawRect(boxX - 2, boxY - 2, boxX + boxWidth + 4, boxY + boxHeight, 0x66000000);
        for (int i = 0; i < lines.length; i++) {
            drawScaledString(lines[i], boxX, boxY + i * lineHeight, 0xFFB0B0B0, SMALL_TEXT_SCALE);
        }
    }

    private void drawBatchPanel(int mouseX, int mouseY) {
        int panelX = getBatchPanelX();
        int panelY = getBatchPanelY();
        boolean hovered = isMouseOverBatchPanel(mouseX, mouseY);
        int borderColor = hovered ? 0xFFAAAAAA : 0xFF555555;
        int titleColor = hovered ? 0xFFE8E8E8 : 0xFFC8C8C8;
        String title = StatCollector.translateToLocal("neirecipetree.batch.title");
        String value = Long.toString(BoM.tree.batches);
        String hint = StatCollector.translateToLocal("neirecipetree.batch.hint");

        drawRect(panelX, panelY, panelX + BATCH_PANEL_WIDTH, panelY + BATCH_PANEL_HEIGHT, 0x88000000);
        drawRect(panelX, panelY, panelX + BATCH_PANEL_WIDTH, panelY + 1, borderColor);
        drawRect(panelX, panelY + BATCH_PANEL_HEIGHT - 1, panelX + BATCH_PANEL_WIDTH, panelY + BATCH_PANEL_HEIGHT,
            borderColor);
        drawRect(panelX, panelY, panelX + 1, panelY + BATCH_PANEL_HEIGHT, borderColor);
        drawRect(panelX + BATCH_PANEL_WIDTH - 1, panelY, panelX + BATCH_PANEL_WIDTH, panelY + BATCH_PANEL_HEIGHT,
            borderColor);

        fontRendererObj.drawStringWithShadow(title, panelX + 6, panelY + 4, titleColor);
        fontRendererObj.drawStringWithShadow(value, panelX + BATCH_PANEL_WIDTH - 6 - fontRendererObj.getStringWidth(value),
            panelY + 4, 0xFFFFFFFF);
        drawScaledString(hint, panelX + 6, panelY + 15, 0xFF9A9A9A, SMALL_TEXT_SCALE);
    }

    private void drawScaledString(String text, float x, float y, int color, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);
        fontRendererObj.drawStringWithShadow(text, Math.round(x / scale), Math.round(y / scale), color);
        GL11.glPopMatrix();
    }

    private int getBatchPanelX() {
        return width - BATCH_PANEL_WIDTH - BATCH_PANEL_MARGIN_RIGHT;
    }

    private int getBatchPanelY() {
        return BATCH_PANEL_Y;
    }

    private boolean isMouseOverBatchPanel(int mouseX, int mouseY) {
        int panelX = getBatchPanelX();
        int panelY = getBatchPanelY();
        return mouseX >= panelX && mouseX < panelX + BATCH_PANEL_WIDTH
            && mouseY >= panelY
            && mouseY < panelY + BATCH_PANEL_HEIGHT;
    }

    private List<String> getBatchPanelTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add("\u00a7e" + StatCollector.translateToLocal("neirecipetree.batch.title"));
        tooltip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.batch.tooltip.line1"));
        tooltip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.batch.tooltip.line2"));
        return tooltip;
    }

    private long getBatchScrollStep() {
        if (NEIClientConfig.showItemQuantityWidget()) {
            int quantity = NEIClientConfig.getItemQuantity();
            if (quantity > 0) {
                return quantity;
            }
        }
        return 64L;
    }

    private void renderTreeLayer(int renderWidth, int renderHeight, float scale, double renderOffX, double renderOffY,
        boolean interactive, boolean opaqueNodeBackgrounds, int mouseX, int mouseY) {
        TreeRenderer.setupZoom(scale, renderOffX, renderOffY, renderWidth, renderHeight);

        double worldMouseX = 0;
        double worldMouseY = 0;
        hoveredNode = null;
        hoveredNodeArea = HoverArea.NONE;
        hoveredCost = null;
        Set<MaterialNode> highlightedChildNodes = new HashSet<>();
        MaterialNode highlightedNode = null;
        if (interactive) {
            worldMouseX = (mouseX - renderWidth / 2.0) / scale - renderOffX;
            worldMouseY = (mouseY - renderHeight / 2.0) / scale - renderOffY;
            updateHoveredNode(worldMouseX, worldMouseY);
            if (hoveredNode != null && hoveredNodeArea == HoverArea.ITEM) {
                highlightedNode = hoveredNode.materialNode;
                collectVisibleChildNodes(highlightedNode, highlightedChildNodes);
            }
        }

        for (TreeNode node : nodes) {
            if (node.materialNode.children != null && node.materialNode.state == FoldState.EXPANDED
                && node.materialNode.recipe != null) {
                int parentCenterX = node.getCenterX();
                int parentBottomY = node.getBottomY();

                for (MaterialNode child : node.materialNode.children) {
                    TreeNode childNode = findTreeNode(child);
                    if (childNode != null) {
                        drawConnection(node, childNode, getLineColor(child));
                    }
                }
            }
        }

        if (highlightedNode != null) {
            for (TreeNode node : nodes) {
                if (node.materialNode.children != null && node.materialNode.state == FoldState.EXPANDED
                    && node.materialNode.recipe != null) {
                    for (MaterialNode child : node.materialNode.children) {
                        if (!shouldHighlightTrace(node.materialNode, child, highlightedNode)) {
                            continue;
                        }
                        TreeNode childNode = findTreeNode(child);
                        if (childNode != null) {
                            int highlightColor = brightenColor(getLineColor(child), TRACE_HIGHLIGHT_STRENGTH);
                            drawConnection(node, childNode, highlightColor);
                        }
                    }
                }
            }
        }

        for (TreeNode node : nodes) {
            boolean highlightItem = interactive && hoveredNodeArea == HoverArea.ITEM && node == hoveredNode;
            boolean highlightChild = highlightedChildNodes.contains(node.materialNode);
            drawNode(node, opaqueNodeBackgrounds, highlightItem, highlightChild);
        }

        if (!costs.isEmpty()) {
            int sectionBaseY = (nodeHeight + 1) * TreeLayout.NODE_VERTICAL_SPACING + 20;
            String costTitle = "\u00a7e\u00a7l" + StatCollector.translateToLocal("neirecipetree.cost.total");
            TreeRenderer.drawCenteredText(0, sectionBaseY, costTitle, 0xFFFFAA00);
            for (CostEntry cost : costs) {
                drawCostEntry(cost);
                if (interactive && worldMouseX >= cost.x - 8 && worldMouseX < cost.x + 16
                    && worldMouseY >= cost.y
                    && worldMouseY < cost.y + 16) {
                    hoveredCost = cost;
                }
            }
        }

        if (!remainderEntries.isEmpty()) {
            int costItemsY = (nodeHeight + 1) * TreeLayout.NODE_VERTICAL_SPACING + 35;
            int remHeaderY = costItemsY + 30;
            String remTitle = "\u00a77" + StatCollector.translateToLocal("neirecipetree.cost.remainders");
            TreeRenderer.drawCenteredText(0, remHeaderY, remTitle, 0xFF888888);
            for (CostEntry rem : remainderEntries) {
                drawCostEntry(rem);
                if (interactive && worldMouseX >= rem.x - 8 && worldMouseX < rem.x + 16
                    && worldMouseY >= rem.y
                    && worldMouseY < rem.y + 16) {
                    hoveredCost = rem;
                }
            }
        }

        TreeRenderer.teardownZoom();
    }

    private void drawNodeTooltip(TreeNode treeNode, int mouseX, int mouseY) {
        MaterialNode mn = treeNode.materialNode;
        ItemStack displayStack = getDisplayStack(mn);
        List<String> tooltip = new ArrayList<>();
        tooltip.addAll(displayStack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips));

        if (treeNode.amount > 1) {
            tooltip.add(
                "\u00a78" + StatCollector.translateToLocalFormatted("neirecipetree.tooltip.amount", treeNode.amount));
        }
        if (mn.catalyst) {
            tooltip.add("\u00a7e" + StatCollector.translateToLocal("neirecipetree.tooltip.catalyst"));
        }

        // Show available recipe info
        int recipeCount = RecipeLookup.getRecipeCount(mn.ingredient);
        int handlerCount = RecipeLookup.getHandlerTypeCount(mn.ingredient);
        if (mn.recipe != null) {
            if (recipeCount > 1) {
                String handlerName = mn.recipe.getHandlerName();
                tooltip.add(
                    "\u00a7b" + StatCollector
                        .translateToLocalFormatted("neirecipetree.tooltip.handler_info", handlerName, handlerCount));
            }
        } else if (recipeCount == 0) {
            tooltip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.tooltip.no_recipes"));
        } else {
            tooltip.add("\u00a7a" + StatCollector.translateToLocal("neirecipetree.tooltip.lmb_select"));
            tooltip.add("\u00a78" + StatCollector.translateToLocal("neirecipetree.tooltip.shift_lmb_auto"));
            if (handlerCount > 1) {
                tooltip.add(
                    "\u00a78" + StatCollector
                        .translateToLocalFormatted("neirecipetree.tooltip.handlers_available", handlerCount));
            }
        }

        drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    private void drawRecipeTooltip(TreeNode treeNode, int mouseX, int mouseY) {
        MaterialNode materialNode = treeNode.materialNode;
        if (materialNode.recipe == null) {
            clearRecipeTooltipPreview();
            return;
        }
        drawRecipePreview(materialNode.recipe, mouseX, mouseY, 0);
    }

    private void drawCostTooltip(CostEntry cost, int mouseX, int mouseY) {
        List<String> tooltip = new ArrayList<>();
        tooltip.addAll(cost.stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips));
        tooltip.add("\u00a78" + StatCollector.translateToLocalFormatted("neirecipetree.tooltip.amount", cost.amount));
        drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    private void clearRecipeTooltipPreview() {
        recipeTooltipHandler = null;
        recipeTooltipRef = null;
    }

    private HoverArea getHoverArea(TreeNode node, double worldMouseX, double worldMouseY) {
        if (node.containsRecipe(worldMouseX, worldMouseY)) {
            return HoverArea.RECIPE;
        }
        if (node.containsItem(worldMouseX, worldMouseY)) {
            return HoverArea.ITEM;
        }
        if (node.containsFrame(worldMouseX, worldMouseY)) {
            return HoverArea.FRAME;
        }
        return HoverArea.NONE;
    }

    private void updateHoveredNode(double worldMouseX, double worldMouseY) {
        for (TreeNode node : nodes) {
            HoverArea hoverArea = getHoverArea(node, worldMouseX, worldMouseY);
            if (hoverArea != HoverArea.NONE) {
                hoveredNode = node;
                hoveredNodeArea = hoverArea;
            }
        }
    }

    private void collectVisibleChildNodes(MaterialNode node, Set<MaterialNode> childNodes) {
        if (node == null || node.children == null || node.children.isEmpty() || node.state != FoldState.EXPANDED
            || node.recipe == null) {
            return;
        }
        for (MaterialNode child : node.children) {
            if (findTreeNode(child) != null) {
                childNodes.add(child);
            }
        }
    }

    private boolean shouldHighlightTrace(MaterialNode parent, MaterialNode child, MaterialNode highlightedNode) {
        return highlightedNode != null && (parent == highlightedNode || child == highlightedNode);
    }

    private void drawConnection(TreeNode parentNode, TreeNode childNode, int lineColor) {
        int parentCenterX = parentNode.getCenterX();
        int parentBottomY = parentNode.getBottomY();
        int childCenterX = childNode.getCenterX();
        int childTopY = childNode.y;
        int midY = (parentBottomY + childTopY) / 2;
        TreeRenderer.drawLine(parentCenterX, parentBottomY, parentCenterX, midY, lineColor);
        TreeRenderer.drawLine(parentCenterX, midY, childCenterX, midY, lineColor);
        TreeRenderer.drawLine(childCenterX, midY, childCenterX, childTopY, lineColor);
    }

    private int brightenColor(int color, float strength) {
        int alpha = color >>> 24;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        red += Math.round((0xFF - red) * strength);
        green += Math.round((0xFF - green) * strength);
        blue += Math.round((0xFF - blue) * strength);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private int getNodeBorderColor(TreeNode node, int recipeCount) {
        MaterialNode materialNode = node.materialNode;
        if (node.chance != null && node.chance.chanced()) {
            return CHANCED_BORDER_COLOR;
        }
        if (materialNode.recipe == null && recipeCount > 0) {
            return SELECTABLE_BORDER_COLOR;
        }
        if (materialNode.recipe != null && materialNode.state == FoldState.COLLAPSED && materialNode.children != null
            && !materialNode.children.isEmpty()) {
            return COLLAPSED_BORDER_COLOR;
        }
        return NODE_BORDER_COLOR;
    }

    private void drawNodeFrame(TreeNode node, int borderColor) {
        int x = node.x;
        int y = node.y;
        int width = node.width;
        int height = TreeNode.FRAME_HEIGHT;
        if (!node.hasRecipeIcon()) {
            TreeRenderer.drawNodeBorder(x, y, width, height, borderColor);
            return;
        }

        int iconX = node.getRecipeIconX();
        int iconY = node.getRecipeIconY();
        int overlapRight = Math.min(x + width, iconX + TreeNode.RECIPE_ICON_SIZE);
        int overlapBottom = Math.min(y + height, iconY + TreeNode.RECIPE_ICON_SIZE);
        int topCut = Math.max(0, overlapRight - x);
        int leftCut = Math.max(0, overlapBottom - y);

        if (topCut < width) {
            Gui.drawRect(x + topCut, y, x + width, y + 1, borderColor);
        }
        Gui.drawRect(x, y + height - 1, x + width, y + height, borderColor);
        if (leftCut < height) {
            Gui.drawRect(x, y + leftCut, x + 1, y + height, borderColor);
        }
        Gui.drawRect(x + width - 1, y, x + width, y + height, borderColor);
    }

    private void drawRecipeIcon(TreeNode node, int borderColor) {
        MaterialNode materialNode = node.materialNode;
        if (materialNode.recipe == null) {
            return;
        }

        int iconX = node.getRecipeIconX();
        int iconY = node.getRecipeIconY();
        TreeRenderer.drawNodeBackground(iconX, iconY, TreeNode.RECIPE_ICON_SIZE, TreeNode.RECIPE_ICON_SIZE, 0xCC101010);
        TreeRenderer.drawNodeBorder(iconX, iconY, TreeNode.RECIPE_ICON_SIZE, TreeNode.RECIPE_ICON_SIZE, borderColor);
        HandlerInfo handlerInfo = GuiRecipeTab.getHandlerInfo(materialNode.recipe.handler);
        if (handlerInfo != null) {
            DrawableResource image = handlerInfo.getImage();
            if (image != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(iconX + 1.0F, iconY + 1.0F, 0.0F);
                GL11.glScalef(0.4375F, 0.4375F, 1.0F);
                GL11.glColor4f(0.92F, 0.92F, 0.92F, 0.95F);
                image.draw(0, 0);
                GL11.glPopMatrix();
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                return;
            }

            ItemStack stack = handlerInfo.getItemStack();
            if (stack != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(iconX + 1.0F, iconY + 1.0F, 0.0F);
                GL11.glScalef(0.4375F, 0.4375F, 1.0F);
                TreeRenderer.drawItemStack(0, 0, stack);
                GL11.glPopMatrix();
                return;
            }
        }

        GL11.glPushMatrix();
        GL11.glScalef(0.4375F, 0.4375F, 1.0F);
        TreeRenderer.drawCenteredText((int) ((iconX + TreeNode.RECIPE_ICON_SIZE / 2.0) / 0.4375F),
            (int) (iconY / 0.4375F), "R", 0xFFCCCCCC);
        GL11.glPopMatrix();
    }

    private String getNodeAmountText(TreeNode node) {
        return TreeLayout.getNodeAmountText(node.amount, node.chance, true);
    }

    private void drawNode(TreeNode node, boolean opaqueBackground, boolean highlightItem, boolean highlightChild) {
        MaterialNode mn = node.materialNode;
        int x = node.x;
        int y = node.y;
        int recipeCount = RecipeLookup.getRecipeCount(mn.ingredient);
        int itemIconX = node.getItemIconX();
        int itemIconY = node.getItemIconY();

        // Node background
        int bgColor = NODE_BACKGROUND_COLOR;
        if (opaqueBackground) {
            bgColor = 0xFF000000 | (bgColor & 0x00FFFFFF);
        }
        TreeRenderer.drawNodeBackground(x + 1, y + 1, node.width - 2, TreeNode.FRAME_HEIGHT - 2, bgColor);

        // Node border
        int borderColor = getNodeBorderColor(node, recipeCount);

        drawNodeFrame(node, borderColor);
        if (highlightChild && node.width > 2 && TreeNode.FRAME_HEIGHT > 2) {
            TreeRenderer.drawNodeBorder(x + 1, y + 1, node.width - 2, TreeNode.FRAME_HEIGHT - 2,
                brightenColor(borderColor, CHILD_HIGHLIGHT_STRENGTH));
        }

        if (mn.recipe != null) {
            drawRecipeIcon(node, borderColor);
        }

        // Item icon with EMI-style amount overlay
        ItemStack displayStack = getDisplayStack(mn);
        TreeRenderer.drawItemStack(itemIconX, itemIconY, displayStack, getNodeAmountText(node));
        if (highlightItem) {
            TreeRenderer.drawNodeBorder(itemIconX - 1, itemIconY - 1, TreeNode.ICON_SIZE + 2, TreeNode.ICON_SIZE + 2,
                ITEM_HIGHLIGHT_COLOR);
        }

        // Fold/expand indicator
        if (mn.recipe != null && mn.children != null && !mn.children.isEmpty()) {
            String foldText = mn.state == FoldState.COLLAPSED ? "+" : "-";
            TreeRenderer.drawAmountText(x + node.width - 6, y + 1, foldText,
                mn.state == FoldState.COLLAPSED ? COLLAPSED_BORDER_COLOR : NODE_BORDER_COLOR);
        } else if (mn.recipe == null && recipeCount > 0) {
            TreeRenderer.drawAmountText(x + node.width - 6, y + 1, "+", SELECTABLE_BORDER_COLOR);
        }

        // Catalyst indicator
        if (mn.catalyst) {
            TreeRenderer.drawAmountText(x + 1, y + 1, "C", 0xFF5555FF);
        }
    }

    private void drawCostEntry(CostEntry cost) {
        TreeRenderer.drawItemStack(cost.x - 8, cost.y, cost.stack);
        String amountStr = getCostAmountText(cost);
        int color = cost.chanced ? 0xFFFFAA00 : 0xFFFFFFFF;
        TreeRenderer.drawAmountText(cost.x + 10, cost.y + 4, amountStr, color);
    }

    private String getCostAmountText(CostEntry cost) {
        return cost.chanced ? "~" + cost.amount : String.valueOf(cost.amount);
    }

    /**
     * Get the currently displayed ItemStack for a node, cycling through permutations like EMI.
     * Uses system time to cycle through alternatives every 1 second.
     */
    private ItemStack getDisplayStack(MaterialNode mn) {
        if (mn.permutations == null || mn.permutations.length <= 1) {
            return mn.ingredient;
        }
        int index = (int) (System.currentTimeMillis() / 1000 % mn.permutations.length);
        ItemStack display = mn.permutations[index];
        return display != null ? display : mn.ingredient;
    }

    /**
     * Draw recipe preview using NEI's own RecipeTooltipLineHandler.
     */
    private void drawRecipePreview(NEIRecipeRef recipe, int mouseX, int mouseY, int tooltipLines) {
        // Create handler if recipe changed
        if (recipeTooltipRef != recipe) {
            recipeTooltipRef = recipe;
            try {
                String handlerName = GuiRecipeTab.getHandlerInfo(recipe.handler).getHandlerName();
                codechicken.nei.PositionedStack resultStack = recipe.handler.getResultStack(recipe.recipeIndex);
                if (resultStack == null) {
                    for (codechicken.nei.PositionedStack otherStack : recipe.handler.getOtherStacks(recipe.recipeIndex)) {
                        resultStack = otherStack;
                        break;
                    }
                }
                Recipe.RecipeId recipeId = Recipe.RecipeId.of(
                    resultStack,
                    handlerName,
                    recipe.handler.getIngredientStacks(recipe.recipeIndex));
                recipeTooltipHandler = new RecipeTooltipLineHandler(recipeId);
            } catch (Exception e) {
                recipeTooltipHandler = null;
            }
        }

        if (recipeTooltipHandler == null) return;

        Dimension size = recipeTooltipHandler.getSize();
        if (size.width == 0 || size.height == 0) return;

        // Position below the text tooltip
        int previewX = mouseX + 12;
        int previewY = mouseY + 12 + tooltipLines * 11 + 4;

        // Keep on screen
        if (previewX + size.width > width) {
            previewX = width - size.width - 4;
        }
        if (previewY + size.height > height) {
            previewY = mouseY - size.height - 4;
        }
        if (previewX < 0) previewX = 4;
        if (previewY < 0) previewY = 4;

        // NEI's RecipeTooltipLineHandler.draw() handles all GL state internally
        recipeTooltipHandler.draw(previewX, previewY);
    }

    private int getLineColor(MaterialNode child) {
        return NODE_LINE_COLOR;
    }

    private TreeNode findTreeNode(MaterialNode materialNode) {
        for (TreeNode tn : nodes) {
            if (tn.materialNode == materialNode) return tn;
        }
        return null;
    }

    private float getScale() {
        return (float) Math.pow(1.2, zoom);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Check if any button was clicked first
        if (exportButton != null && exportButton.mousePressed(mc, mouseX, mouseY)) {
            return;
        }
        if (exportImageButton != null && exportImageButton.mousePressed(mc, mouseX, mouseY)) {
            return;
        }

        if (hoveredNode != null) {
            MaterialNode mn = hoveredNode.materialNode;

            if (mouseButton == 1 && mn.recipe != null) {
                // RMB: matches EMI exactly
                if (isShiftKeyDown()) {
                    // Shift+RMB: clear resolution (revert to raw material)
                    BoM.clearResolution(mn.ingredient);
                    recalculateTree();
                } else if (mn.recipe != null && mn.children != null && !mn.children.isEmpty()) {
                    // RMB on resolved node: toggle fold/expand
                    mn.state = mn.state == FoldState.EXPANDED ? FoldState.COLLAPSED : FoldState.EXPANDED;
                    ItemStackKey key = ItemStackKey.of(mn.ingredient);
                    if (key != null) {
                        if (mn.state == FoldState.EXPANDED) {
                            BoM.userExpandedNodes.add(key);
                        } else {
                            BoM.userExpandedNodes.remove(key);
                        }
                    }
                    recalculateTree();
                }
                return;
            }

            if (mouseButton == 0 && hoveredNodeArea == HoverArea.ITEM) {
                if (isShiftKeyDown()) {
                    // Shift+LMB: auto-resolve with first available recipe (EMI's getAutoResolutions)
                    NEIRecipeRef found = RecipeLookup.findFirstRecipe(mn.ingredient);
                    if (found != null) {
                        BoM.setResolution(mn.ingredient, found);
                        recalculateTree();
                    }
                } else {
                    // LMB: open NEI recipe view in resolve mode (EMI's RecipeScreen.resolve)
                    // User browses recipes, clicks [S] button on desired recipe to select
                    ItemStack stack = mn.ingredient;
                    BoM.pendingResolution = stack.copy();
                    GuiCraftingRecipe.openRecipeGui("item", stack.copy());
                }
                return;
            }

            if (mouseButton == 0 && hoveredNodeArea != HoverArea.NONE) {
                return;
            }
        }

        // Cost/remainder click: open NEI for that item
        if (mouseButton == 0 && hoveredCost != null) {
            GuiCraftingRecipe.openRecipeGui("item", hoveredCost.stack.copy());
            return;
        }

        if (mouseButton == 0) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state == 0) {
            dragging = false;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging) {
            float scale = getScale();
            offX += (mouseX - lastMouseX) / scale;
            offY += (mouseY - lastMouseY) / scale;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int mouseX = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
            int mouseY = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;

            if (isMouseOverBatchPanel(mouseX, mouseY)) {
                if (BoM.tree != null && isCtrlKeyDown()) {
                    long multiplier = NEIClientUtils.altKey() ? getBatchScrollStep() : 1L;
                    long delta = scroll > 0 ? multiplier : -multiplier;
                    BoM.tree.batches = Math.max(1L, Math.min(MAX_BATCH_AMOUNT, BoM.tree.batches + delta));
                    recalculateTree();
                    return;
                }
            }
            zoom = scroll > 0 ? Math.min(zoom + 1, 8) : Math.max(zoom - 1, -8);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC
            BoM.pendingResolution = null;
            mc.displayGuiScreen(parentScreen);
        } else if (keyCode == 211) { // DELETE key
            if (isShiftKeyDown()) {
                // Shift+Delete: close and clear entire tree
                BoM.clear();
                mc.displayGuiScreen(parentScreen);
            } else {
                // Delete: reset all resolutions (keep root recipe)
                resetTree();
            }
        }
    }

    private void resetTree() {
        if (BoM.tree == null) return;
        NEIRecipeRef rootRecipe = BoM.tree.goal.recipe;
        BoM.tree.resolutions.clear();
        BoM.addedRecipes.clear();
        BoM.userExpandedNodes.clear();
        BoM.defaultRecipes.clear();
        BoM.recipeIndices.clear();
        // Re-add root recipe
        if (rootRecipe != null) {
            ItemStackKey rootKey = ItemStackKey.of(BoM.tree.goal.ingredient);
            if (rootKey != null) {
                BoM.tree.resolutions.put(rootKey, rootRecipe);
            }
        }
        recalculateTree();
    }

    /**
     * Export current recipe tree to NEI bookmarks as a crafting chain.
     * Collects all Recipe objects and lets NEI handle the crafting chain computation.
     */
    private void exportToBookmarks() {
        if (BoM.tree == null) return;

        // Collect all recipes from the tree
        List<codechicken.nei.recipe.Recipe> allRecipes = new ArrayList<>();
        collectRecipesToExport(BoM.tree.goal, allRecipes, new HashSet<>());

        if (allRecipes.isEmpty()) return;

        try {
            // Use reflection to access protected 'grid' field from parent class PanelWidget
            java.lang.reflect.Field gridField = ItemPanels.bookmarkPanel.getClass()
                .getSuperclass()
                .getDeclaredField("grid");
            gridField.setAccessible(true);
            codechicken.nei.bookmark.BookmarkGrid grid = (codechicken.nei.bookmark.BookmarkGrid) gridField
                .get(ItemPanels.bookmarkPanel);

            // Create a new bookmark group with DEFAULT view mode and crafting enabled
            int groupId = grid.addGroup(
                new codechicken.nei.bookmark.BookmarkGroup(
                    moe.takochan.neirecipetree.nei.NEIRecipeTreeConfig.isBookmarkExpanded()
                        ? codechicken.nei.BookmarkPanel.BookmarkViewMode.TODO_LIST
                        : codechicken.nei.BookmarkPanel.BookmarkViewMode.DEFAULT,
                    true));

            // Add each recipe to the group
            for (codechicken.nei.recipe.Recipe recipe : allRecipes) {
                grid.addRecipe(recipe, 1, groupId);
            }

            ItemPanels.bookmarkPanel.save();
            mc.ingameGUI.getChatGUI()
                .printChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7a[RecipeTree] " + StatCollector.translateToLocal("neirecipetree.chat.bookmark_added")));
        } catch (Exception e) {
            LOG.error("Failed to export recipe tree to bookmarks", e);
        }
    }

    /**
     * Recursively collect all recipes from the material node tree for export.
     * Exports all nodes that have a recipe defined.
     * Skips children of COLLAPSED nodes (user chose not to expand them).
     */
    private void collectRecipesToExport(MaterialNode node, List<codechicken.nei.recipe.Recipe> recipes,
        Set<NEIRecipeRef> visited) {
        if (node == null || node.recipe == null) return;

        // Avoid cycles
        if (!visited.add(node.recipe)) return;

        // Add this node's recipe
        codechicken.nei.recipe.Recipe recipe = codechicken.nei.recipe.Recipe
            .of(node.recipe.handler, node.recipe.recipeIndex);
        if (recipe != null) {
            recipes.add(recipe);
        }

        // Recurse into children only if this node is EXPANDED
        if (node.state == FoldState.EXPANDED && node.children != null) {
            for (MaterialNode child : node.children) {
                collectRecipesToExport(child, recipes, visited);
            }
        }
    }

    /**
     * Export current recipe tree to PNG image.
     */
    private void exportToImage() {
        if (BoM.tree == null) {
            return;
        }

        Framebuffer framebuffer = null;
        try {
            if (!OpenGlHelper.isFramebufferEnabled()) {
                throw new IllegalStateException("Framebuffer is not supported on this system");
            }

            recalculateTree();
            TreeSceneBounds bounds = calculateTreeSceneBounds();
            int logicalWidth = Math.max(64, bounds.getWidth() + EXPORT_PADDING * 2);
            int logicalHeight = Math.max(64, bounds.getHeight() + EXPORT_PADDING * 2);
            int maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            int widthScaleLimit = Math.max(1, maxTextureSize / logicalWidth);
            int heightScaleLimit = Math.max(1, maxTextureSize / logicalHeight);
            int exportScale = Math.max(1, Math.min(EXPORT_SCALE, Math.min(widthScaleLimit, heightScaleLimit)));
            int renderWidth = logicalWidth * exportScale;
            int renderHeight = logicalHeight * exportScale;
            double renderOffX = EXPORT_PADDING - bounds.minX - logicalWidth / 2.0;
            double renderOffY = EXPORT_PADDING - bounds.minY - logicalHeight / 2.0;

            framebuffer = new Framebuffer(renderWidth, renderHeight, true);
            renderTreeToFramebuffer(framebuffer, renderWidth, renderHeight, renderOffX, renderOffY, exportScale);

            java.awt.image.BufferedImage image = readFramebuffer(framebuffer, renderWidth, renderHeight);
            File screenshotsDir = new File(mc.mcDataDir, "screenshots/recipe_trees");
            if (!screenshotsDir.exists() && !screenshotsDir.mkdirs() && !screenshotsDir.exists()) {
                throw new IllegalStateException("Could not create screenshot directory");
            }

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date());
            File outputFile = new File(screenshotsDir, "recipe_tree_" + timestamp + ".png");
            ImageIO.write(image, "png", outputFile);

            mc.ingameGUI.getChatGUI()
                .printChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7a[RecipeTree] Saved to screenshots/recipe_trees/" + outputFile.getName()));
        } catch (Exception e) {
            LOG.error("Failed to export recipe tree to image", e);
            mc.ingameGUI.getChatGUI()
                .printChatMessage(
                    new net.minecraft.util.ChatComponentText("\u00a7c[RecipeTree] Export failed: " + e.getMessage()));
        } finally {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            if (mc.getFramebuffer() != null) {
                mc.getFramebuffer().bindFramebuffer(true);
            }
        }
    }

    private void renderTreeToFramebuffer(Framebuffer framebuffer, int renderWidth, int renderHeight, double renderOffX,
        double renderOffY, float renderScale) {
        framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);

        try {
            GL11.glViewport(0, 0, renderWidth, renderHeight);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, renderWidth, renderHeight, 0.0D, 1000.0D, 3000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

            drawRect(0, 0, renderWidth, renderHeight, EXPORT_BACKGROUND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            renderTreeLayer(renderWidth, renderHeight, renderScale, renderOffX, renderOffY, false, true, 0, 0);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            framebuffer.unbindFramebuffer();
        }
    }

    private java.awt.image.BufferedImage readFramebuffer(Framebuffer framebuffer, int width, int height) {
        ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
        framebuffer.bindFramebuffer(true);
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
        } finally {
            framebuffer.unbindFramebuffer();
        }

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (x + width * y) * 4;
                int r = pixelBuffer.get(index) & 0xFF;
                int g = pixelBuffer.get(index + 1) & 0xFF;
                int b = pixelBuffer.get(index + 2) & 0xFF;
                int a = pixelBuffer.get(index + 3) & 0xFF;
                image.setRGB(x, height - 1 - y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private TreeSceneBounds calculateTreeSceneBounds() {
        TreeSceneBounds bounds = new TreeSceneBounds();

        for (TreeNode node : nodes) {
            bounds.include(node.x, node.y, node.x + node.width, node.y + TreeNode.FRAME_HEIGHT);
        }

        if (!costs.isEmpty()) {
            int sectionBaseY = (nodeHeight + 1) * TreeLayout.NODE_VERTICAL_SPACING + 20;
            String costTitle = "\u00a7e\u00a7l" + StatCollector.translateToLocal("neirecipetree.cost.total");
            int titleWidth = fontRendererObj.getStringWidth(costTitle);
            bounds.include(-titleWidth / 2, sectionBaseY, titleWidth / 2, sectionBaseY + fontRendererObj.FONT_HEIGHT);
            for (CostEntry cost : costs) {
                String amountText = getCostAmountText(cost);
                int amountWidth = fontRendererObj.getStringWidth(amountText);
                bounds.include(cost.x - 8, cost.y, cost.x + 10 + amountWidth, cost.y + 16);
            }
        }

        if (!remainderEntries.isEmpty()) {
            int costItemsY = (nodeHeight + 1) * TreeLayout.NODE_VERTICAL_SPACING + 35;
            int remHeaderY = costItemsY + 30;
            String remTitle = "\u00a77" + StatCollector.translateToLocal("neirecipetree.cost.remainders");
            int remTitleWidth = fontRendererObj.getStringWidth(remTitle);
            bounds.include(-remTitleWidth / 2, remHeaderY, remTitleWidth / 2, remHeaderY + fontRendererObj.FONT_HEIGHT);
            for (CostEntry rem : remainderEntries) {
                String amountText = getCostAmountText(rem);
                int amountWidth = fontRendererObj.getStringWidth(amountText);
                bounds.include(rem.x - 8, rem.y, rem.x + 10 + amountWidth, rem.y + 16);
            }
        }

        if (bounds.empty()) {
            bounds.include(0, 0, TreeNode.RECIPE_NODE_BASE_WIDTH, TreeNode.FRAME_HEIGHT);
        }
        return bounds;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Simple button for exporting recipe tree to NEI bookmarks.
     */
    private enum ButtonIcon {

        BOOKMARK,
        EXPORT_IMAGE
    }

    public static class GuiRecipeTreeButton extends codechicken.nei.GuiNEIButton {

        private final Runnable onExport;
        private final ButtonIcon icon;
        private final String titleKey;
        private final String tooltipKey;

        public GuiRecipeTreeButton(int x, int y, ButtonIcon icon, String titleKey, String tooltipKey,
            Runnable onExport) {
            super(-1, x, y, 18, 18, "");
            this.icon = icon;
            this.titleKey = titleKey;
            this.tooltipKey = tooltipKey;
            this.onExport = onExport;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
            int bgColor = 0xFF333333;
            boolean hovered = isMouseOver(mouseX, mouseY);
            int borderColor = hovered ? 0xFFAAAAAA : 0xFF555555;

            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition, width, height, bgColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition, width, 1, borderColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition + height - 1, width, 1, borderColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition, 1, height, borderColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition + width - 1, yPosition, 1, height, borderColor);
            drawIcon(mc, hovered);
        }

        @Override
        public boolean mousePressed(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                onExport.run();
                return true;
            }
            return false;
        }

        public List<String> getToolTip() {
            List<String> tooltip = new ArrayList<>();
            tooltip.add("\u00a7e" + StatCollector.translateToLocal(titleKey));
            tooltip.add("\u00a78" + StatCollector.translateToLocal(tooltipKey));
            return tooltip;
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return visible && mouseX >= xPosition && mouseX < xPosition + width
                && mouseY >= yPosition
                && mouseY < yPosition + height;
        }

        private void drawIcon(net.minecraft.client.Minecraft mc, boolean hovered) {
            int iconU = icon == ButtonIcon.BOOKMARK ? 0 : ACTION_BUTTON_SIZE;
            mc.getTextureManager().bindTexture(BUTTON_ICONS_TEXTURE);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(hovered ? 1.0F : 0.82F, hovered ? 1.0F : 0.82F, hovered ? 1.0F : 0.82F, 1.0F);
            drawCustomSizedTexturedRect(
                xPosition,
                yPosition,
                iconU,
                0,
                ACTION_BUTTON_SIZE,
                ACTION_BUTTON_SIZE,
                BUTTON_TEXTURE_WIDTH,
                BUTTON_TEXTURE_HEIGHT);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private void drawCustomSizedTexturedRect(int x, int y, int u, int v, int width, int height, float textureWidth,
            float textureHeight) {
            float minU = u / textureWidth;
            float maxU = (u + width) / textureWidth;
            float minV = v / textureHeight;
            float maxV = (v + height) / textureHeight;

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + height, 0, minU, maxV);
            tessellator.addVertexWithUV(x + width, y + height, 0, maxU, maxV);
            tessellator.addVertexWithUV(x + width, y, 0, maxU, minV);
            tessellator.addVertexWithUV(x, y, 0, minU, minV);
            tessellator.draw();
        }
    }

    private static class TreeSceneBounds {

        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;

        private void include(int left, int top, int right, int bottom) {
            minX = Math.min(minX, left);
            minY = Math.min(minY, top);
            maxX = Math.max(maxX, right);
            maxY = Math.max(maxY, bottom);
        }

        private boolean empty() {
            return minX == Integer.MAX_VALUE;
        }

        private int getWidth() {
            return Math.max(0, maxX - minX);
        }

        private int getHeight() {
            return Math.max(0, maxY - minY);
        }
    }

    public static class CostEntry {

        public final ItemStack stack;
        public final long amount;
        public final boolean chanced;
        public int x;
        public int y;

        public CostEntry(ItemStack stack, long amount, boolean chanced) {
            this.stack = stack;
            this.amount = amount;
            this.chanced = chanced;
        }
    }
}
