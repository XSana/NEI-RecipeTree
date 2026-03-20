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

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import codechicken.nei.ItemPanels;
import codechicken.nei.recipe.GuiCraftingRecipe;
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
    private CostEntry hoveredCost = null;

    // Recipe preview tooltip — reuses NEI's own RecipeTooltipLineHandler
    private RecipeTooltipLineHandler recipeTooltipHandler = null;
    private NEIRecipeRef recipeTooltipRef = null;

    private final GuiScreen parentScreen;

    // Export to bookmarks button
    private GuiRecipeTreeButton exportButton;
    // Export to image button
    private GuiRecipeTreeButton exportImageButton;

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

        // Add export to bookmarks button (top-right corner)
        int buttonX = width - 22;
        int buttonY = 4;
        exportButton = new GuiRecipeTreeButton(buttonX, buttonY, "B", this::exportToBookmarks);

        // Add export to image button (next to bookmarks button)
        int imgButtonX = width - 40;
        int imgButtonY = 4;
        exportImageButton = new GuiRecipeTreeButton(imgButtonX, imgButtonY, "I", this::exportToImage);

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

        // Draw batch counter (screen space)
        String batchText = "x" + BoM.tree.batches;
        int batchX = width / 2 + 20;
        int batchY = 10;
        drawRect(
            batchX - 2,
            batchY - 2,
            batchX + fontRendererObj.getStringWidth(batchText) + 4,
            batchY + 12,
            0x88000000);
        fontRendererObj.drawStringWithShadow(batchText, batchX, batchY, 0xFFFFFFFF);

        // Draw help text (localized)
        String helpText = "\u00a77" + StatCollector.translateToLocal("neirecipetree.help");
        fontRendererObj.drawStringWithShadow(helpText, 4, height - 12, 0xFF666666);

        // Draw tooltips (in screen space, after teardownZoom)
        if (hoveredNode != null) {
            drawNodeTooltip(hoveredNode, mouseX, mouseY);
        } else {
            // Clear tooltip handler when not hovering a node to avoid stale NEI widget state
            recipeTooltipHandler = null;
            recipeTooltipRef = null;
            if (hoveredCost != null) {
                drawCostTooltip(hoveredCost, mouseX, mouseY);
            }
        }

        // Draw buttons (super.drawScreen renders buttonList)
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void renderTreeLayer(int renderWidth, int renderHeight, float scale, double renderOffX, double renderOffY,
        boolean interactive, boolean opaqueNodeBackgrounds, int mouseX, int mouseY) {
        TreeRenderer.setupZoom(scale, renderOffX, renderOffY, renderWidth, renderHeight);

        double worldMouseX = 0;
        double worldMouseY = 0;
        hoveredNode = null;
        hoveredCost = null;
        if (interactive) {
            worldMouseX = (mouseX - renderWidth / 2.0 - renderOffX) / scale;
            worldMouseY = (mouseY - renderHeight / 3.0 - renderOffY) / scale;
        }

        for (TreeNode node : nodes) {
            if (node.materialNode.children != null && node.materialNode.state == FoldState.EXPANDED
                && node.materialNode.recipe != null) {
                int parentCenterX = node.x + node.midOffset;
                int parentBottomY = node.y + 20;

                for (MaterialNode child : node.materialNode.children) {
                    TreeNode childNode = findTreeNode(child);
                    if (childNode != null) {
                        int childCenterX = childNode.x + childNode.midOffset;
                        int childTopY = childNode.y;
                        int lineColor = getLineColor(child);
                        int midY = (parentBottomY + childTopY) / 2;
                        TreeRenderer.drawLine(parentCenterX, parentBottomY, parentCenterX, midY, lineColor);
                        TreeRenderer.drawLine(parentCenterX, midY, childCenterX, midY, lineColor);
                        TreeRenderer.drawLine(childCenterX, midY, childCenterX, childTopY, lineColor);
                    }
                }
            }
        }

        for (TreeNode node : nodes) {
            drawNode(node, opaqueNodeBackgrounds);
            if (interactive && worldMouseX >= node.x && worldMouseX < node.x + node.width
                && worldMouseY >= node.y
                && worldMouseY < node.y + 20) {
                hoveredNode = node;
            }
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

        // Draw text tooltip first
        drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);

        // Draw recipe preview below the text tooltip (like NEI's favorite recipe tooltip)
        if (mn.recipe != null) {
            drawRecipePreview(mn.recipe, mouseX, mouseY, tooltip.size());
        }
    }

    private void drawCostTooltip(CostEntry cost, int mouseX, int mouseY) {
        List<String> tooltip = new ArrayList<>();
        tooltip.addAll(cost.stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips));
        tooltip.add("\u00a78" + StatCollector.translateToLocalFormatted("neirecipetree.tooltip.amount", cost.amount));
        drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    private void drawNode(TreeNode node, boolean opaqueBackground) {
        MaterialNode mn = node.materialNode;
        int x = node.x;
        int y = node.y;
        int recipeCount = RecipeLookup.getRecipeCount(mn.ingredient);

        // Node background
        int bgColor = 0x88333333;
        if (mn.catalyst) {
            bgColor = 0x88000055; // Blue tint for catalysts
        } else if (mn.progress == ProgressState.COMPLETED) {
            bgColor = 0x88005500;
        } else if (mn.progress == ProgressState.PARTIAL) {
            bgColor = 0x88553300;
        }
        if (opaqueBackground) {
            bgColor = 0xFF000000 | (bgColor & 0x00FFFFFF);
        }
        TreeRenderer.drawNodeBackground(x + 1, y + 1, TreeLayout.NODE_WIDTH - 2, 18, bgColor);

        // Node border
        int borderColor = 0xFF555555;
        if (mn.recipe != null) {
            borderColor = 0xFF888888;
        }
        if (mn.catalyst) {
            borderColor = 0xFF5555FF; // Blue border for catalysts
        }
        if (node.chance != null && node.chance.chanced()) {
            borderColor = 0xFFFFAA00;
        }
        if (mn.state == FoldState.COLLAPSED && mn.recipe != null) {
            borderColor = 0xFF4488FF;
        }
        if (mn.recipe == null && recipeCount > 0) {
            borderColor = 0xFF55FF55; // Green: expandable
        }

        TreeRenderer.drawNodeBorder(x, y, TreeLayout.NODE_WIDTH, 20, borderColor);

        // Item icon — cycle through permutations like EMI (1 second per variant)
        ItemStack displayStack = getDisplayStack(mn);
        TreeRenderer.drawItemStack(x + 7, y + 2, displayStack);

        // Amount text below node (always shown)
        String amountText = String.valueOf(node.amount);
        TreeRenderer.drawCenteredText(x + TreeLayout.NODE_WIDTH / 2, y + 22, amountText, 0xFFCCCCCC);

        // Fold/expand indicator
        if (mn.recipe != null && mn.children != null && !mn.children.isEmpty()) {
            String foldText = mn.state == FoldState.COLLAPSED ? "+" : "-";
            TreeRenderer.drawAmountText(x + TreeLayout.NODE_WIDTH - 6, y + 1, foldText, 0xFF888888);
        } else if (mn.recipe == null && recipeCount > 0) {
            TreeRenderer.drawAmountText(x + TreeLayout.NODE_WIDTH - 6, y + 1, "+", 0xFF55FF55);
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
                Recipe.RecipeId recipeId = Recipe.RecipeId.of(recipe.handler, recipe.recipeIndex);
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
        if (child.progress == ProgressState.COMPLETED) return 0xFF00AA00;
        if (child.progress == ProgressState.PARTIAL) return 0xFFAA5500;
        if (child.catalyst) return 0xFF5555FF;
        return 0xFF666666;
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

            if (mouseButton == 1) {
                // RMB: matches EMI exactly
                if (isShiftKeyDown()) {
                    // Shift+RMB: clear resolution (revert to raw material)
                    BoM.clearResolution(mn.ingredient);
                    recalculateTree();
                } else if (mn.recipe != null && mn.children != null && !mn.children.isEmpty()) {
                    // RMB on resolved node: toggle fold/expand
                    mn.state = mn.state == FoldState.EXPANDED ? FoldState.COLLAPSED : FoldState.EXPANDED;
                    recalculateTree();
                }
                return;
            }

            if (mouseButton == 0) {
                if (isShiftKeyDown()) {
                    // Shift+LMB: auto-resolve with first available recipe (EMI's getAutoResolutions)
                    NEIRecipeRef found = RecipeLookup.findFirstRecipe(mn.ingredient);
                    if (found != null) {
                        BoM.addResolution(mn.ingredient, found);
                        BoM.addedRecipes.put(ItemStackKey.of(mn.ingredient), found);
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

        // Check batch counter click
        if (mouseButton == 0 && BoM.tree != null) {
            String batchText = "x" + BoM.tree.batches;
            int batchX = width / 2 + 20;
            int batchY = 10;
            int batchW = fontRendererObj.getStringWidth(batchText) + 6;
            if (mouseX >= batchX - 2 && mouseX < batchX + batchW && mouseY >= batchY - 2 && mouseY < batchY + 14) {
                long ideal = BoM.tree.cost.getIdealBatch(BoM.tree.goal, 1, BoM.tree.goal.amount);
                if (ideal > 0 && ideal <= 10000) {
                    BoM.tree.batches = ideal;
                    recalculateTree();
                }
            }
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
            offX += mouseX - lastMouseX;
            offY += mouseY - lastMouseY;
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

            String batchText = "x" + (BoM.tree != null ? BoM.tree.batches : 1);
            int batchX = width / 2 + 20;
            int batchY = 10;
            int batchW = fontRendererObj.getStringWidth(batchText) + 6;

            if (mouseX >= batchX - 2 && mouseX < batchX + batchW && mouseY >= batchY - 2 && mouseY < batchY + 14) {
                if (BoM.tree != null) {
                    if (isCtrlKeyDown()) {
                        BoM.tree.batches = scroll > 0 ? Math.min(BoM.tree.batches * 2, 10000)
                            : Math.max(BoM.tree.batches / 2, 1);
                    } else if (isShiftKeyDown()) {
                        BoM.tree.batches = scroll > 0 ? Math.min(BoM.tree.batches + 16, 10000)
                            : Math.max(BoM.tree.batches - 16, 1);
                    } else {
                        BoM.tree.batches = scroll > 0 ? Math.min(BoM.tree.batches + 1, 10000)
                            : Math.max(BoM.tree.batches - 1, 1);
                    }
                    recalculateTree();
                }
            } else {
                zoom = scroll > 0 ? Math.min(zoom + 1, 8) : Math.max(zoom - 1, -8);
            }
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
                    codechicken.nei.BookmarkPanel.BookmarkViewMode.DEFAULT,
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
            double renderOffX = (EXPORT_PADDING - bounds.minX - logicalWidth / 2.0) * exportScale;
            double renderOffY = (EXPORT_PADDING - bounds.minY - logicalHeight / 3.0) * exportScale;

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
            bounds.include(node.x, node.y, node.x + TreeLayout.NODE_WIDTH, node.y + 20);

            String amountText = String.valueOf(node.amount);
            int amountWidth = fontRendererObj.getStringWidth(amountText);
            int amountLeft = node.x + TreeLayout.NODE_WIDTH / 2 - amountWidth / 2;
            bounds.include(amountLeft, node.y + 22, amountLeft + amountWidth, node.y + 22 + fontRendererObj.FONT_HEIGHT);

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
            bounds.include(0, 0, TreeLayout.NODE_WIDTH, 20);
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
    public static class GuiRecipeTreeButton extends codechicken.nei.GuiNEIButton {

        private final Runnable onExport;
        private final String text;

        public GuiRecipeTreeButton(int x, int y, String text, Runnable onExport) {
            super(-1, x, y, 16, 16, text);
            this.text = text;
            this.onExport = onExport;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
            // Draw button background using GuiDraw from codechicken.lib.gui
            int bgColor = 0xFF333333;
            boolean hovered = mouseX >= xPosition && mouseX < xPosition + width
                && mouseY >= yPosition
                && mouseY < yPosition + height;
            int borderColor = hovered ? 0xFFAAAAAA : 0xFF555555;

            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition, width, height, bgColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition, width, 1, borderColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition + height - 1, width, 1, borderColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition, yPosition, 1, height, borderColor);
            codechicken.lib.gui.GuiDraw.drawRect(xPosition + width - 1, yPosition, 1, height, borderColor);

            // Draw text centered
            int textX = xPosition + (width - mc.fontRenderer.getStringWidth(text)) / 2;
            int textY = yPosition + (height - mc.fontRenderer.FONT_HEIGHT) / 2;
            mc.fontRenderer.drawStringWithShadow(text, textX, textY, 0xFFFFFFFF);
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
            if ("B".equals(text)) {
                tooltip.add(net.minecraft.util.StatCollector.translateToLocal("neirecipetree.button.bookmark.tooltip"));
            } else if ("I".equals(text)) {
                tooltip.add(
                    net.minecraft.util.StatCollector.translateToLocal("neirecipetree.button.export_image.tooltip"));
            }
            return tooltip;
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
