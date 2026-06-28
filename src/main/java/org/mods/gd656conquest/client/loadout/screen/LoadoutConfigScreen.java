package org.mods.gd656conquest.client.loadout.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LoadoutConfigScreen extends Screen {
    private static final ResourceLocation CLASS_ICON_ASSAULT = ResourceLocation.parse("gd656conquest:textures/gui/class_assault.png");
    private static final ResourceLocation CLASS_ICON_SUPPORT = ResourceLocation.parse("gd656conquest:textures/gui/class_support.png");
    private static final ResourceLocation CLASS_ICON_ENGINEER = ResourceLocation.parse("gd656conquest:textures/gui/class_engineer.png");
    private static final ResourceLocation CLASS_ICON_RECON = ResourceLocation.parse("gd656conquest:textures/gui/class_recon.png");
    private static final int LEFT = 18;
    private static final int TOP = 36;
    private static final int WIDTH = 170;
    private static final int HEIGHT = 20;
    private static final int GAP = 4;
    private static final long REQUEST_INTERVAL_MS = 1200L;

    private final Screen parent;
    private ChoiceListDialog choiceDialog;
    private final List<GDButton> actionButtons = new ArrayList<>();
    private GDButton closeButton;
    private GDButton vehicleDeployButton;
    private GDButton editRoomsButton;
    private GDButton editVehiclesButton;
    private GDButton editItemsButton;
    private long lastRequestAt;
    private long outOfGameCloseAtMs;

    public LoadoutConfigScreen(Screen parent) {
        super(Component.translatable("gd656conquest.client.gui.loadout.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.choiceDialog = new ChoiceListDialog(minecraft);
        updateOutOfGameTimer();
        rebuildButtons();
        if (!isOutOfGame()) {
            requestSnapshotIfNeeded();
        }
    }

    public Screen parentScreen() {
        return parent;
    }

    private void rebuildButtons() {
        actionButtons.clear();
        int y = TOP;
        actionButtons.add(new GDButton(LEFT, y, WIDTH, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.class"), btn -> openClassDialog()));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, WIDTH, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.primary"), btn -> openSlotDialog(CustomLoadoutSlot.PRIMARY_WEAPON)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, WIDTH, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.secondary"), btn -> openSlotDialog(CustomLoadoutSlot.SECONDARY_WEAPON)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, WIDTH, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.melee"), btn -> openSlotDialog(CustomLoadoutSlot.MELEE)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, WIDTH, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.gadget1"), btn -> openSlotDialog(CustomLoadoutSlot.GADGET_1)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, WIDTH, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.gadget2"), btn -> openSlotDialog(CustomLoadoutSlot.GADGET_2)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, WIDTH, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.throwable"), btn -> openSlotDialog(CustomLoadoutSlot.THROWABLE)));
        closeButton = new GDButton(this.width - 86, this.height - 26, 72, 20, Component.translatable("gd656conquest.client.gui.loadout.close"), btn -> onClose());
        vehicleDeployButton = new GDButton(LEFT, this.height - 26, 92, 20, Component.translatable("gd656conquest.client.gui.loadout.vehicle.button"), btn -> openVehicleDeployScreen());
        editVehiclesButton = new GDButton(this.width - 86, this.height - 92, 72, 20, Component.literal("编辑房间载具"), btn -> openVehicleAdminEditorScreen());
        editRoomsButton = new GDButton(this.width - 86, this.height - 70, 72, 20, Component.literal("编辑房间数据"), btn -> openRoomEditorScreen());
        editItemsButton = new GDButton(this.width - 86, this.height - 48, 72, 20, Component.literal("编辑物品数据"), btn -> openItemEditorScreen());
    }

    @Override
    public void tick() {
        super.tick();
        updateOutOfGameTimer();
        if (isOutOfGame() && outOfGameCloseAtMs > 0L && System.currentTimeMillis() >= outOfGameCloseAtMs) {
            onClose();
            return;
        }
        requestSnapshotIfNeeded();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        if (isOutOfGame()) {
            int remain = Math.max(0, (int) Math.ceil((outOfGameCloseAtMs - System.currentTimeMillis()) / 1000.0));
            guiGraphics.drawCenteredString(this.font, "请在游戏内打开配置界面", this.width / 2, this.height / 2 - 10, 0xFFFF8080);
            guiGraphics.drawCenteredString(this.font, "将在 " + remain + " 秒后自动关闭", this.width / 2, this.height / 2 + 6, 0xFFB8C6D6);
            if (closeButton != null) {
                closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        for (GDButton button : actionButtons) {
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (closeButton != null) {
            closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (vehicleDeployButton != null) {
            ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
            boolean available = snapshot != null && snapshot.vehiclePageAvailable();
            vehicleDeployButton.active = true;
            vehicleDeployButton.setTextColor(available ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            vehicleDeployButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (editItemsButton != null) {
            boolean showEditButton = shouldShowItemEditorButton();
            editItemsButton.active = showEditButton;
            editItemsButton.setTextColor(showEditButton ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showEditButton) {
                editItemsButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
        if (editVehiclesButton != null) {
            boolean showVehicleButton = shouldShowRoomEditorButton();
            editVehiclesButton.active = showVehicleButton;
            editVehiclesButton.setTextColor(showVehicleButton ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showVehicleButton) {
                editVehiclesButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
        if (editRoomsButton != null) {
            boolean showRoomButton = shouldShowRoomEditorButton();
            editRoomsButton.active = showRoomButton;
            editRoomsButton.setTextColor(showRoomButton ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showRoomButton) {
                editRoomsButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
        guiGraphics.drawString(this.font, this.title, LEFT, 12, 0xFFFFFF, false);
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        String levelText = snapshot == null ? "-" : String.valueOf(snapshot.playerLevel());
        String classText = resolveCurrentClassName(snapshot);
        guiGraphics.drawString(this.font, "玩家: " + (minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "-"), LEFT + WIDTH + 18, TOP, 0xD7D7D7, false);
        guiGraphics.drawString(this.font, "等级: " + levelText, LEFT + WIDTH + 18, TOP + 12, 0xFFD700, false);
        guiGraphics.drawString(this.font, "兵种: " + classText, LEFT + WIDTH + 18, TOP + 24, 0x7EC8FF, false);
        guiGraphics.drawString(this.font, "ESC 或 关闭按钮退出", LEFT + WIDTH + 18, TOP + 40, 0xAAAAAA, false);
        if (snapshot == null) {
            guiGraphics.drawString(this.font, Component.translatable("gd656conquest.client.gui.loadout.loading"), LEFT, TOP + (HEIGHT + GAP) * 7 + 12, 0xFFAA00, false);
        }
        if (choiceDialog != null) {
            choiceDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            if (isOutOfGame()) {
                if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                return true;
            }
            if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (vehicleDeployButton != null && vehicleDeployButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (editVehiclesButton != null && shouldShowRoomEditorButton() && editVehiclesButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (editItemsButton != null && shouldShowItemEditorButton() && editItemsButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (editRoomsButton != null && shouldShowRoomEditorButton() && editRoomsButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (GDButton actionButton : actionButtons) {
                if (actionButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (choiceDialog != null) {
            choiceDialog.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void openClassDialog() {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || snapshot.classes().isEmpty() || choiceDialog == null) {
            requestSnapshotIfNeeded();
            return;
        }
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        Map<String, ChoiceListDialog.RowIcon> iconByClassKey = new HashMap<>();
        for (ClientLoadoutUiState.ClassState classState : snapshot.classes().values()) {
            choices.add(new FixedChoiceConfigEntry.Choice(classState.classKey(), classState.className()));
            ChoiceListDialog.RowIcon icon = resolveClassIcon(classState.classKey());
            if (icon != null && !icon.isEmpty()) {
                iconByClassKey.put(classState.classKey(), icon);
            }
        }
        choiceDialog.show(
                snapshot.currentClassKey(),
                "兵种选择",
                choices,
                selectedClass -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectClass(selectedClass)),
                null,
                null,
                null,
                "",
                0xFFFF0000,
                17,
                null,
                null,
                null,
                choice -> choice == null ? null : iconByClassKey.get(choice.value())
        );
    }

    private void openSlotDialog(CustomLoadoutSlot slot) {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || snapshot.classes().isEmpty() || choiceDialog == null) {
            requestSnapshotIfNeeded();
            return;
        }
        ClientLoadoutUiState.ClassState currentClass = snapshot.classes().get(snapshot.currentClassKey());
        if (currentClass == null) {
            requestSnapshotIfNeeded();
            return;
        }
        ClientLoadoutUiState.SlotState slotState = currentClass.slots().get(slot.getIndex());
        if (slotState == null || slotState.entries().isEmpty()) {
            return;
        }
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        Set<String> lockedIds = new HashSet<>();
        Map<String, ChoiceListDialog.RowIcon> iconByEntryId = new HashMap<>();
        for (ClientLoadoutUiState.EntryState entry : slotState.sortedEntries()) {
            String display = entry.displayName().isBlank() ? entry.id() : entry.displayName();
            String lockSuffix = entry.unlocked() ? "" : ("  Lv." + entry.unlockLevel() + "未解锁");
            String prefix = entry.unlocked() ? "§f" : "§7";
            String label = prefix + display + " §8[" + entry.id() + "]" + (lockSuffix.isBlank() ? "" : " §7" + lockSuffix);
            choices.add(new FixedChoiceConfigEntry.Choice(entry.id(), label));
            ChoiceListDialog.RowIcon icon = buildItemIcon(entry.itemSnbt());
            if (icon != null && !icon.isEmpty()) {
                iconByEntryId.put(entry.id(), icon);
            }
            if (!entry.unlocked()) {
                lockedIds.add(entry.id());
            }
        }
        String selectedId = slotState.selectedId();
        if (selectedId == null || selectedId.isBlank() || !slotState.entries().containsKey(selectedId)) {
            selectedId = choices.isEmpty() ? "" : choices.get(0).value();
        }
        String classKey = currentClass.classKey();
        choiceDialog.show(
                selectedId,
                slotState.slotLabel().isBlank() ? slot.getKey() : slotState.slotLabel(),
                choices,
                selected -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectSlot(classKey, slot.getIndex(), selected)),
                null,
                null,
                null,
                "",
                0xFFFF0000,
                17,
                null,
                null,
                choice -> choice != null && !lockedIds.contains(choice.value()),
                choice -> choice == null ? null : iconByEntryId.get(choice.value())
        );
    }

    private ChoiceListDialog.RowIcon buildItemIcon(String itemSnbt) {
        if (itemSnbt == null || itemSnbt.isBlank()) {
            return null;
        }
        try {
            CompoundTag tag = TagParser.parseTag(itemSnbt);
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty()) {
                return null;
            }
            return ChoiceListDialog.RowIcon.item(stack);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ChoiceListDialog.RowIcon resolveClassIcon(String classKey) {
        if (classKey == null || classKey.isBlank()) {
            return null;
        }
        return switch (classKey.toLowerCase()) {
            case "assault" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_ASSAULT);
            case "support" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_SUPPORT);
            case "engineer" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_ENGINEER);
            case "recon" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_RECON);
            default -> null;
        };
    }

    private String resolveCurrentClassName(ClientLoadoutUiState.Snapshot snapshot) {
        if (snapshot == null || snapshot.currentClassKey() == null || snapshot.currentClassKey().isBlank()) {
            return "-";
        }
        ClientLoadoutUiState.ClassState classState = snapshot.classes().get(snapshot.currentClassKey());
        if (classState == null || classState.className() == null || classState.className().isBlank()) {
            return snapshot.currentClassKey();
        }
        return classState.className();
    }

    private void requestSnapshotIfNeeded() {
        if (isOutOfGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRequestAt < REQUEST_INTERVAL_MS) {
            return;
        }
        lastRequestAt = now;
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));
    }

    private boolean shouldShowItemEditorButton() {
        if (minecraft == null || minecraft.player == null || !minecraft.player.hasPermissions(2)) {
            return false;
        }
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        return snapshot != null && snapshot.admin();
    }

    private void openItemEditorScreen() {
        if (minecraft == null || !shouldShowItemEditorButton()) {
            return;
        }
        minecraft.setScreen(new LoadoutItemEditorScreen(this));
    }

    private boolean shouldShowRoomEditorButton() {
        return shouldShowItemEditorButton();
    }

    private void openVehicleDeployScreen() {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new LoadoutVehicleDeployScreen(this));
    }

    private void openRoomEditorScreen() {
        if (minecraft == null || !shouldShowRoomEditorButton()) {
            return;
        }
        minecraft.setScreen(new LoadoutRoomEditorScreen(this));
    }

    private void openVehicleAdminEditorScreen() {
        if (minecraft == null || !shouldShowRoomEditorButton()) {
            return;
        }
        minecraft.setScreen(new LoadoutVehicleAdminEditorScreen(this));
    }

    private boolean isOutOfGame() {
        return minecraft == null || minecraft.player == null || minecraft.level == null;
    }

    private void updateOutOfGameTimer() {
        if (!isOutOfGame()) {
            outOfGameCloseAtMs = -1L;
            return;
        }
        if (outOfGameCloseAtMs <= 0L) {
            outOfGameCloseAtMs = System.currentTimeMillis() + 3000L;
        }
    }
}
