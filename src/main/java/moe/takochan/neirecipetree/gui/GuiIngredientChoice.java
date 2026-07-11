package moe.takochan.neirecipetree.gui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import moe.takochan.neirecipetree.bom.MaterialNode;
import moe.takochan.neirecipetree.recipe.ItemStackKey;

/** Selects one concrete material for one merged tree node. */
public class GuiIngredientChoice extends GuiScreen {

    private static final int COLUMNS = 8;
    private static final int ROWS = 3;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int CELL_SIZE = 24;

    private final GuiScreen parent;
    private final Runnable onCancelled;
    private final Consumer<ItemStack> onConfirmed;
    private final List<ItemStack> candidates;
    private final ItemStackKey currentSelection;
    private final long amount;
    private int page;

    public static boolean openForNode(MaterialNode node, long amount, GuiScreen parent, Consumer<ItemStack> onConfirmed,
        Runnable onCancelled) {
        List<ItemStack> candidates = node.getUniquePermutations();
        if (candidates.size() <= 1) {
            onConfirmed.accept(node.ingredient.copy());
            return false;
        }
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiIngredientChoice(node, amount, parent, onConfirmed, onCancelled, candidates));
        return true;
    }

    private GuiIngredientChoice(MaterialNode node, long amount, GuiScreen parent, Consumer<ItemStack> onConfirmed,
        Runnable onCancelled, List<ItemStack> candidates) {
        this.parent = parent;
        this.onConfirmed = onConfirmed;
        this.onCancelled = onCancelled;
        this.candidates = candidates;
        this.currentSelection = ItemStackKey.of(node.ingredient);
        this.amount = amount;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int bottom = height / 2 + 76;
        buttonList.add(new GuiButton(0, width / 2 - 48, bottom, 96, 20, StatCollector.translateToLocal("gui.cancel")));
        int pageY = height / 2 + 51;
        buttonList.add(new GuiButton(1, width / 2 - 52, pageY, 48, 20, "<"));
        buttonList.add(new GuiButton(2, width / 2 + 4, pageY, 48, 20, ">"));
        updateButtonStates();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - COLUMNS * CELL_SIZE / 2;
        int top = height / 2 - 55;
        int right = left + COLUMNS * CELL_SIZE;
        drawRect(left - 12, top - 50, right + 12, top + ROWS * CELL_SIZE + 42, 0xEE181818);

        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("neirecipetree.ingredient.title"),
            width / 2,
            top - 40,
            0xFFFFFFFF);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocalFormatted("neirecipetree.ingredient.amount", amount),
            width / 2,
            top - 25,
            0xFFFFCC55);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("neirecipetree.ingredient.hint"),
            width / 2,
            top - 12,
            0xFFAAAAAA);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, candidates.size());
        for (int i = start; i < end; i++) {
            int local = i - start;
            int x = left + local % COLUMNS * CELL_SIZE + 4;
            int y = top + local / COLUMNS * CELL_SIZE + 4;
            ItemStack stack = candidates.get(i);
            int color = currentSelection != null && currentSelection.equals(ItemStackKey.of(stack)) ? 0xFFFFFF55
                : 0xFF666666;
            TreeRenderer.drawNodeBorder(x - 3, y - 3, 22, 22, color);
            TreeRenderer.drawItemStack(x, y, stack);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        ItemStack hovered = getCandidateAt(mouseX, mouseY);
        if (hovered != null) {
            @SuppressWarnings("unchecked")
            List<String> tooltip = hovered.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;
        ItemStack selected = getCandidateAt(mouseX, mouseY);
        if (selected != null) {
            onConfirmed.accept(selected.copy());
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            cancel();
        } else if (button.id == 1 && page > 0) {
            page--;
            updateButtonStates();
        } else if (button.id == 2 && (page + 1) * PAGE_SIZE < candidates.size()) {
            page++;
            updateButtonStates();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancel();
        }
    }

    private void updateButtonStates() {
        if (buttonList.size() < 3) return;
        ((GuiButton) buttonList.get(1)).enabled = page > 0;
        ((GuiButton) buttonList.get(2)).enabled = (page + 1) * PAGE_SIZE < candidates.size();
    }

    private void cancel() {
        if (onCancelled != null) {
            onCancelled.run();
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    private ItemStack getCandidateAt(int mouseX, int mouseY) {
        int left = width / 2 - COLUMNS * CELL_SIZE / 2;
        int top = height / 2 - 55;
        if (mouseX < left || mouseY < top) return null;
        int column = (mouseX - left) / CELL_SIZE;
        int row = (mouseY - top) / CELL_SIZE;
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) return null;
        int index = page * PAGE_SIZE + row * COLUMNS + column;
        return index < candidates.size() ? candidates.get(index) : null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
