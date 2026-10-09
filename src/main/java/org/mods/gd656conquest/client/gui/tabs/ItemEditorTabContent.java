package org.mods.gd656conquest.client.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.ConfirmDialog;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.TextInputDialog;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.EquipmentEditorUpdatePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab content for editing item/equipment data (物品数据).
 * Adapted from LoadoutItemEditorScreen.
 */
public class ItemEditorTabContent implements ConfigScreenHeader.TabContent {

    private static final int LEFT = GuiConstants.SCREEN_EDGE_DISTANCE;
    private static final int ROW_H = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ROW_GAP = 1;

    private final Component title = Component.literal("物品数据");
    private final Minecraft minecraft;
    private final List<GDRowRenderer> rowRenderers = new ArrayList<>();
    private final List<ClientLoadoutUiState.AdminEquipmentState> entries = new ArrayList<>();
    private GDRowRenderer headerRenderer;
    private TextInputDialog textInputDialog;
    private ConfirmDialog confirmDialog;
    private final GDScrollState scrollState = new GDScrollState();

    public ItemEditorTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public void onTabOpen() {
        scrollState.reset();
        refreshFromSnapshot();
    }

    @Override
    public void tick() {
        refreshFromSnapshot();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, int contentTop) {
        initDialogsIfNeeded();

        int baseMouseX = hasBlockingDialog() ? Integer.MIN_VALUE / 4 : mouseX;
        int baseMouseY = hasBlockingDialog() ? Integer.MIN_VALUE / 4 : mouseY;
        int top = contentTop + 6;
        int x1 = LEFT;
        int y1 = top + ROW_H + 1;
        int x2 = screenWidth - LEFT;
        int y2 = screenHeight - 17;
        int viewH = Math.max(1, y2 - y1);
        int contentHeight = entries.size() * (ROW_H + ROW_GAP);
        scrollState.update(baseMouseY, viewH, contentHeight);

        guiGraphics.drawString(minecraft.font, "装备数据编辑器", LEFT, top - 10, GuiConstants.COLOR_TITLE_TEXT, false);

        if (headerRenderer == null) {
            headerRenderer = new GDRowRenderer(x1, top, x2, top + ROW_H,
                    GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        }
        headerRenderer.setBounds(x1, top, x2, top + ROW_H);
        headerRenderer.resetColumnConfig();
        headerRenderer.addColumn("", ROW_H, GuiConstants.COLOR_WHITE, true, true, null);
        headerRenderer.addColumn("物品ID", 72, 0xFF9FD3FF, false, false, null);
        headerRenderer.addColumn("物品显示名称", -1, GuiConstants.COLOR_HEADER_TEXT, true, false, null);
        headerRenderer.addColumn("物品槽位", 42, 0xFFC8B6FF, true, true, null);
        headerRenderer.addColumn("解锁等级", 42, 0xFFBDF5BD, false, true, null);
        headerRenderer.addColumn("可用兵种", 42, GuiConstants.COLOR_HEADER_TEXT, true, true, null);
        headerRenderer.addColumn("附加数据", 42, 0xFFD6D9FF, false, true, null);
        headerRenderer.addColumn("补全秒", 42, 0xFFC8F5C8, true, true, null);
        headerRenderer.addColumn("弹药补给", 52, 0xFFB8D8FF, false, true, null);
        headerRenderer.addColumn("物品SNBT", -1, 0xFF9FC7AF, true, false, null);
        headerRenderer.addColumn("", ROW_H, 0xFFFF8080, true, true, null);
        headerRenderer.render(guiGraphics, baseMouseX, baseMouseY, partialTick);

        guiGraphics.enableScissor(x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -scrollState.scrollY(), 0);
        for (int i = 0; i < entries.size(); i++) {
            int rowTop = y1 + i * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            float screenTop = (float) (rowTop - scrollState.scrollY());
            float screenBottom = (float) (rowBottom - scrollState.scrollY());
            if (screenBottom <= y1 || screenTop >= y2) continue;
            renderEntryRow(guiGraphics, baseMouseX, (int) (baseMouseY + scrollState.scrollY()),
                    partialTick, i, entries.get(i), x1, rowTop, x2, rowBottom);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        scrollState.renderScrollbar(guiGraphics, x1, y1, x2, y2, contentHeight);

        guiGraphics.drawString(minecraft.font, "总数: " + entries.size(), LEFT, screenHeight - 17, 0xFFB8C6D6, false);

        if (textInputDialog != null && textInputDialog.isVisible()) {
            textInputDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (confirmDialog != null && confirmDialog.isVisible()) {
            confirmDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void initDialogsIfNeeded() {
        if (textInputDialog == null) textInputDialog = new TextInputDialog(minecraft, null, null);
        if (confirmDialog == null) confirmDialog = new ConfirmDialog(minecraft, null, null);
    }

    private boolean hasBlockingDialog() {
        return (textInputDialog != null && textInputDialog.isVisible())
                || (confirmDialog != null && confirmDialog.isVisible());
    }

    private void renderEntryRow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                int index, ClientLoadoutUiState.AdminEquipmentState entry,
                                int x1, int y1, int x2, int y2) {
        while (rowRenderers.size() <= index) {
            rowRenderers.add(new GDRowRenderer(x1, y1, x2, y2,
                    GuiConstants.COLOR_BLACK & 0xFFFFFF, 0.26f, false));
        }
        GDRowRenderer row = rowRenderers.get(index);
        row.setBounds(x1, y1, x2, y2);
        row.setBackgroundColor((index % 2 == 0 ? 0x00141B24 : 0x0010181F) & 0xFFFFFF);
        row.setBackgroundAlpha(index % 2 == 0 ? 0.18f : 0.34f);
        row.resetColumnConfig();

        ItemStack stack = parseStack(entry.itemSnbt());
        if (!stack.isEmpty()) {
            row.addIconColumn(stack, ROW_H, true, true, null);
        } else {
            row.addColumn("", ROW_H, GuiConstants.COLOR_GRAY, true, true, null);
        }
        row.addColumn(entry.id(), 72, GuiConstants.COLOR_SKY_BLUE, false, false,
                btn -> openFieldEditor(entry, "id", "编辑ID", entry.id()));
        row.addColumn(safe(entry.displayName()), -1, 0xFFEDEDED, true, false,
                btn -> openFieldEditor(entry, "display_name", "编辑显示名", safe(entry.displayName())));
        row.addColumn(String.valueOf(entry.slot()), 42, GuiConstants.COLOR_DATA_TEXT, true, true,
                btn -> openFieldEditor(entry, "slot", "编辑槽位(1~6)", String.valueOf(entry.slot())));
        row.addColumn(String.valueOf(entry.unlockLevel()), 42, 0xFFB6FFB6, false, true,
                btn -> openFieldEditor(entry, "unlock_level", "编辑解锁等级", String.valueOf(entry.unlockLevel())));
        row.addColumn(String.valueOf(entry.availableClasses()), 42, GuiConstants.COLOR_HEADER_TEXT, true, true,
                btn -> openFieldEditor(entry, "available_classes", "编辑可用兵种数字", String.valueOf(entry.availableClasses())));
        row.addColumn(String.valueOf(entry.extraData()), 42, 0xFFD3C7FF, false, true,
                btn -> openFieldEditor(entry, "extra_data", "编辑额外数据", String.valueOf(entry.extraData())));
        row.addColumn(String.valueOf(entry.replenishSeconds()), 42, 0xFFB6FFB6, true, true,
                btn -> openFieldEditor(entry, "replenish_seconds", "编辑自动补全秒数(0关闭)", String.valueOf(entry.replenishSeconds())));
        row.addColumn(entry.ammoBoxRefillEnabled() ? "true" : "false", 52, 0xFFB8D8FF, false, true,
                btn -> openFieldEditor(entry, "ammo_box_refill_enabled", "是否允许弹药箱补给(true/false)", String.valueOf(entry.ammoBoxRefillEnabled())));
        row.addColumn(snbtPreview(entry.itemSnbt()), -1, 0xFF9FC7AF, true, false,
                btn -> openFieldEditor(entry, "item_snbt", "编辑物品SNBT", safe(entry.itemSnbt())));
        row.addColumn("\u00d7", ROW_H, 0xFFFF5A5A, true, true, btn -> confirmDelete(entry));
        row.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void confirmDelete(ClientLoadoutUiState.AdminEquipmentState entry) {
        if (entry == null || confirmDialog == null) return;
        confirmDialog.show("确认删除物品: " + entry.id() + " ?", ConfirmDialog.PromptType.WARNING,
                () -> NetworkManager.getChannel().sendToServer(
                        new EquipmentEditorUpdatePacket(entry.id(), "delete", "1")));
    }

    private void openFieldEditor(ClientLoadoutUiState.AdminEquipmentState entry, String fieldKey,
                                 String title, String initialValue) {
        if (textInputDialog == null || entry == null) return;
        textInputDialog.show(initialValue == null ? "" : initialValue, title + " [" + entry.id() + "]", value ->
                NetworkManager.getChannel().sendToServer(
                        new EquipmentEditorUpdatePacket(entry.id(), fieldKey, value)));
    }

    private void refreshFromSnapshot() {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        entries.clear();
        if (snapshot != null && snapshot.admin()) {
            entries.addAll(snapshot.sortedAdminEquipment());
        }
    }

    private ItemStack parseStack(String snbt) {
        if (snbt == null || snbt.isBlank()) return ItemStack.EMPTY;
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private String snbtPreview(String snbt) {
        String text = safe(snbt);
        if (text.isBlank()) return "<empty>";
        return text.length() <= 72 ? text : text.substring(0, 72) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        initDialogsIfNeeded();
        if (confirmDialog.isVisible() && confirmDialog.mouseClicked(mouseX, mouseY, button)) return true;
        if (textInputDialog.isVisible() && textInputDialog.mouseClicked(mouseX, mouseY, button)) return true;
        return false;
    }

    public boolean mouseClickedContent(double mouseX, double mouseY, int button,
                                        int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int x1 = LEFT;
        int y1 = top + ROW_H + 1;
        int x2 = screenWidth - LEFT;
        int y2 = screenHeight - 17;
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
            double adjustedY = mouseY + scrollState.scrollY();
            for (int i = 0; i < entries.size(); i++) {
                int rowTop = y1 + i * (ROW_H + ROW_GAP);
                int rowBottom = rowTop + ROW_H;
                if (adjustedY >= rowTop && adjustedY <= rowBottom && i < rowRenderers.size()) {
                    if (rowRenderers.get(i).mouseClicked(mouseX, adjustedY, button)) return true;
                }
            }
            scrollState.startDragging(mouseX, mouseY, x1, y1, x2, y2, entries.size() * (ROW_H + ROW_GAP));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollState.stopDragging();
        return false;
    }

    public boolean mouseScrolledContent(double mouseX, double mouseY, double delta,
                                         int screenWidth, int screenHeight, int contentTop) {
        if (confirmDialog != null && confirmDialog.isVisible()) return confirmDialog.mouseScrolled(mouseX, mouseY, delta);
        if (textInputDialog != null && textInputDialog.isVisible()) return textInputDialog.mouseScrolled(mouseX, mouseY, delta);
        int top = contentTop + 6;
        int x1 = LEFT;
        int y1 = top + ROW_H + 1;
        int x2 = screenWidth - LEFT;
        int y2 = screenHeight - 17;
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
            return scrollState.mouseScrolled(delta, y2 - y1, entries.size() * (ROW_H + ROW_GAP));
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmDialog != null && confirmDialog.isVisible()) return confirmDialog.keyPressed(keyCode, scanCode, modifiers);
        if (textInputDialog != null && textInputDialog.isVisible()) return textInputDialog.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (confirmDialog != null && confirmDialog.isVisible()) return confirmDialog.charTyped(codePoint, modifiers);
        if (textInputDialog != null && textInputDialog.isVisible()) return textInputDialog.charTyped(codePoint, modifiers);
        return false;
    }
}
