package org.mods.gd656conquest.client.loadout.screen;

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
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.title.PlayerTitleService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final int LEFT_WIDTH_MAX = 170;
    private static final int LEFT_WIDTH_MIN = 128;
    private static final int PANEL_GAP = 12;
    private static final int HEIGHT = 20;
    private static final int GAP = 4;
    private static final int RIGHT = 18;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private static final int INFO_H = 60;
    private static final int CARD_H = 132;
    private static final int CARD_GAP = 12;
    private static final int CARD_BUTTON_GAP = 8;
    private static final int CARD_BUTTON_H = 18;
    private static final int BOTTOM_BUTTON_W = 72;
    private static final int BOTTOM_BUTTON_H = 20;
    private static final int BOTTOM_BAR_GAP = 4;
    private static final List<String> ATTACHMENT_TYPE_ORDER = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");

    private final Screen parent;
    private ChoiceListDialog choiceDialog;
    private final List<GDButton> actionButtons = new ArrayList<>();
    private final Map<String, GDButton> attachmentButtons = new LinkedHashMap<>();
    private GDButton closeButton;
    private GDButton vehicleDeployButton;
    private GDButton editRoomsButton;
    private GDButton editVehiclesButton;
    private GDButton editItemsButton;
    private GDButton editAttachmentsButton;
    private long lastRequestAt;
    private long outOfGameCloseAtMs;
    private final GDScrollState rightContentScroll = new GDScrollState();

    public LoadoutConfigScreen(Screen parent) {
        super(Component.translatable("gd656conquest.client.gui.loadout.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.choiceDialog = new ChoiceListDialog(minecraft);
        updateOutOfGameTimer();
        rightContentScroll.reset();
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
        int leftWidth = getLeftColumnWidth();
        int bottomButtonWidth = getBottomButtonWidth();
        int y = TOP;
        actionButtons.add(new GDButton(LEFT, y, leftWidth, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.class"), btn -> openClassDialog()));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, leftWidth, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.primary"), btn -> openSlotDialog(CustomLoadoutSlot.PRIMARY_WEAPON)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, leftWidth, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.secondary"), btn -> openSlotDialog(CustomLoadoutSlot.SECONDARY_WEAPON)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, leftWidth, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.melee"), btn -> openSlotDialog(CustomLoadoutSlot.MELEE)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, leftWidth, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.gadget1"), btn -> openSlotDialog(CustomLoadoutSlot.GADGET_1)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, leftWidth, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.gadget2"), btn -> openSlotDialog(CustomLoadoutSlot.GADGET_2)));
        y += HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, y, leftWidth, HEIGHT, Component.translatable("gd656conquest.client.gui.loadout.throwable"), btn -> openSlotDialog(CustomLoadoutSlot.THROWABLE)));
        int bottomY = getBottomButtonY();
        closeButton = new GDButton(getCloseButtonX(), bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.translatable("gd656conquest.client.gui.loadout.close"), btn -> onClose());
        vehicleDeployButton = new GDButton(LEFT, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.translatable("gd656conquest.client.gui.loadout.vehicle.button"), btn -> openVehicleDeployScreen());
        int adminLeft = getAdminButtonStartX();
        editVehiclesButton = new GDButton(adminLeft, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("房间载具"), btn -> openVehicleAdminEditorScreen());
        editRoomsButton = new GDButton(adminLeft + bottomButtonWidth + BOTTOM_BAR_GAP, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("房间数据"), btn -> openRoomEditorScreen());
        editAttachmentsButton = new GDButton(adminLeft + (bottomButtonWidth + BOTTOM_BAR_GAP) * 2, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("配件数据"), btn -> openAttachmentEditorScreen());
        editItemsButton = new GDButton(adminLeft + (bottomButtonWidth + BOTTOM_BAR_GAP) * 3, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("物品数据"), btn -> openItemEditorScreen());
        attachmentButtons.clear();
        createAttachmentButtons(CustomLoadoutSlot.PRIMARY_WEAPON, getPrimaryCardTop());
        createAttachmentButtons(CustomLoadoutSlot.SECONDARY_WEAPON, getSecondaryCardTop());
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
            vehicleDeployButton.active = available;
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
        if (editAttachmentsButton != null) {
            boolean showAttachmentEditor = shouldShowItemEditorButton();
            editAttachmentsButton.active = showAttachmentEditor;
            editAttachmentsButton.setTextColor(showAttachmentEditor ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showAttachmentEditor) {
                editAttachmentsButton.render(guiGraphics, mouseX, mouseY, partialTick);
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
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        rightContentScroll.update(mouseY, getRightContentViewportBottom() - getRightContentViewportTop(), getRightContentHeight());
        layoutAttachmentButtons(CustomLoadoutSlot.PRIMARY_WEAPON, getPrimaryCardTop());
        layoutAttachmentButtons(CustomLoadoutSlot.SECONDARY_WEAPON, getSecondaryCardTop());
        guiGraphics.drawString(this.font, this.title, LEFT, 12, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "选中枪械后右侧可快速自定义配件", LEFT, 24, 0xFFB8C6D6, false);
        guiGraphics.enableScissor(getRightPanelLeft(), getRightContentViewportTop(), getRightPanelRight(), getRightContentViewportBottom());
        renderOverviewPanel(guiGraphics, snapshot);
        renderAttachmentCard(guiGraphics, mouseX, mouseY, partialTick, CustomLoadoutSlot.PRIMARY_WEAPON, getPrimaryCardTop());
        renderAttachmentCard(guiGraphics, mouseX, mouseY, partialTick, CustomLoadoutSlot.SECONDARY_WEAPON, getSecondaryCardTop());
        guiGraphics.disableScissor();
        if (snapshot == null) {
            guiGraphics.drawString(this.font, Component.translatable("gd656conquest.client.gui.loadout.loading"), LEFT, TOP + (HEIGHT + GAP) * 7 + 12, 0xFFAA00, false);
        }
        rightContentScroll.renderScrollbar(guiGraphics, getRightPanelLeft(), getRightContentViewportTop(), getRightPanelRight(), getRightContentViewportBottom(), getRightContentHeight());
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
            if (editAttachmentsButton != null && shouldShowItemEditorButton() && editAttachmentsButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (editRoomsButton != null && shouldShowRoomEditorButton() && editRoomsButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (GDButton attachmentButton : attachmentButtons.values()) {
                if (attachmentButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            for (GDButton actionButton : actionButtons) {
                if (actionButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (isMouseOverRightContent(mouseX, mouseY)) {
                rightContentScroll.startDragging(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (choiceDialog != null) {
            choiceDialog.mouseReleased(mouseX, mouseY, button);
        }
        rightContentScroll.stopDragging();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (isMouseOverRightContent(mouseX, mouseY)) {
            return rightContentScroll.mouseScrolled(delta, getRightContentViewportBottom() - getRightContentViewportTop(), getRightContentHeight());
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

    private void createAttachmentButtons(CustomLoadoutSlot slot, int topY) {
        int buttonWidth = Math.max(74, (getCardInnerWidth() - CARD_BUTTON_GAP) / 2);
        for (int i = 0; i < ATTACHMENT_TYPE_ORDER.size(); i++) {
            String attachmentType = ATTACHMENT_TYPE_ORDER.get(i);
            int column = i % 2;
            int row = i / 2;
            int x = getRightPanelLeft() + 8 + column * (buttonWidth + CARD_BUTTON_GAP);
            int y = topY + 42 + row * (CARD_BUTTON_H + 6);
            GDButton button = new GDButton(x, y, buttonWidth, CARD_BUTTON_H, Component.literal(resolveAttachmentLabel(attachmentType)), btn -> openAttachmentDialog(slot, attachmentType));
            attachmentButtons.put(slot.getKey() + ":" + attachmentType, button);
        }
    }

    private void layoutAttachmentButtons(CustomLoadoutSlot slot, int topY) {
        int buttonWidth = Math.max(74, (getCardInnerWidth() - CARD_BUTTON_GAP) / 2);
        for (int i = 0; i < ATTACHMENT_TYPE_ORDER.size(); i++) {
            String attachmentType = ATTACHMENT_TYPE_ORDER.get(i);
            GDButton button = attachmentButtons.get(slot.getKey() + ":" + attachmentType);
            if (button == null) {
                continue;
            }
            int column = i % 2;
            int row = i / 2;
            int x = getRightPanelLeft() + 8 + column * (buttonWidth + CARD_BUTTON_GAP);
            int y = topY + 42 + row * (CARD_BUTTON_H + 6);
            button.setX(x);
            button.setY(y);
            button.setWidth(buttonWidth);
            button.setHeight(CARD_BUTTON_H);
        }
    }

    private void renderAttachmentCard(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CustomLoadoutSlot slot, int topY) {
        int left = getRightPanelLeft();
        int right = getRightPanelRight();
        int bottom = topY + CARD_H;
        guiGraphics.fill(left, topY, right, bottom, 0x4410181F);
        guiGraphics.fill(left, topY, right, topY + 1, 0x66FFD78A);
        String title = slot == CustomLoadoutSlot.PRIMARY_WEAPON ? "主武器配件" : "副武器配件";
        guiGraphics.drawString(this.font, title, left + 8, topY + 6, 0xFFFFD78A, false);
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = getAttachmentPanel(slot);
        if (panel == null || !panel.available()) {
            drawClampedString(guiGraphics, "当前未选择武器，请先在左侧选择 " + (slot == CustomLoadoutSlot.PRIMARY_WEAPON ? "主武器" : "副武器"), left + 8, topY + 20, 0xFF9AA6B8, getCardInnerWidth());
            drawClampedString(guiGraphics, "仅 TACZ 主武器 / 副武器支持独立配件配置", left + 8, topY + CARD_H - 12, 0xFF7E8B98, getCardInnerWidth());
            setAttachmentButtonsState(slot, false);
            return;
        }
        drawClampedString(guiGraphics, safe(panel.weaponDisplayName()).isBlank() ? panel.loadoutEntryId() : panel.weaponDisplayName(), left + 8, topY + 20, 0xFFEDEDED, getCardInnerWidth());
        drawClampedString(guiGraphics, "条目ID: " + safe(panel.loadoutEntryId()), left + 8, topY + 32, 0xFF8FB3CF, getCardInnerWidth());
        if (!panel.taczGun()) {
            drawClampedString(guiGraphics, "当前武器不是 TACZ 枪械，无法配置配件", left + 8, topY + CARD_H - 14, 0xFFFF8080, getCardInnerWidth());
            setAttachmentButtonsState(slot, false);
            renderAttachmentButtons(guiGraphics, mouseX, mouseY, partialTick, slot);
            return;
        }
        drawClampedString(guiGraphics, buildAttachmentSummary(panel), left + 8, topY + CARD_H - 14, 0xFFB8C6D6, getCardInnerWidth());
        setAttachmentButtonsState(slot, true);
        renderAttachmentButtons(guiGraphics, mouseX, mouseY, partialTick, slot);
    }

    private void renderAttachmentButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CustomLoadoutSlot slot) {
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = getAttachmentPanel(slot);
        for (String attachmentType : ATTACHMENT_TYPE_ORDER) {
            GDButton button = attachmentButtons.get(slot.getKey() + ":" + attachmentType);
            if (button == null) {
                continue;
            }
            ClientLoadoutUiState.AttachmentTypeState state = panel == null ? null : panel.attachmentTypes().get(attachmentType);
            boolean active = panel != null && panel.taczGun() && state != null && state.available();
            button.active = active;
            button.setTextColor(active ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void setAttachmentButtonsState(CustomLoadoutSlot slot, boolean active) {
        for (String attachmentType : ATTACHMENT_TYPE_ORDER) {
            GDButton button = attachmentButtons.get(slot.getKey() + ":" + attachmentType);
            if (button == null) {
                continue;
            }
            button.active = active;
            button.setTextColor(active ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
        }
    }

    private void renderOverviewPanel(GuiGraphics guiGraphics, ClientLoadoutUiState.Snapshot snapshot) {
        int left = getRightPanelLeft();
        int right = getRightPanelRight();
        int top = getOverviewTop();
        int bottom = top + INFO_H;
        guiGraphics.fill(left, top, right, bottom, 0x4410181F);
        guiGraphics.fill(left, top, right, top + 1, 0x667EC8FF);
        drawClampedString(guiGraphics, "当前配置概览", left + 8, top + 6, 0xFF7EC8FF, getCardInnerWidth());
        String playerName = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "-";
        String classText = resolveCurrentClassName(snapshot);
        String stateText = snapshot == null
            ? "正在同步服务器数据..."
            : "您所更改的配置将在下次部署时或在靶场立即生效";
        drawClampedString(guiGraphics, "玩家: " + playerName + "    兵种: " + classText, left + 8, top + 20, 0xFFD7D7D7, getCardInnerWidth());
        renderTitleAndLevelLine(guiGraphics, snapshot, left + 8, top + 32, getCardInnerWidth());
        drawClampedString(guiGraphics, stateText, left + 8, top + 46, snapshot == null ? 0xFFAA9900 : 0xFF9AA6B8, getCardInnerWidth());
    }

    private int getPrimaryCardTop() {
        return getOverviewTop() + INFO_H + CARD_GAP;
    }

    private int getSecondaryCardTop() {
        return getPrimaryCardTop() + CARD_H + CARD_GAP;
    }

    private int getCardInnerWidth() {
        return Math.max(40, getRightPanelRight() - getRightPanelLeft() - 16);
    }

    private int getLeftColumnWidth() {
        int reservedRightWidth = 360;
        int computed = this.width - LEFT - RIGHT - PANEL_GAP - reservedRightWidth;
        return Math.max(LEFT_WIDTH_MIN, Math.min(LEFT_WIDTH_MAX, computed));
    }

    private int getRightPanelLeft() {
        return LEFT + getLeftColumnWidth() + PANEL_GAP;
    }

    private int getRightPanelRight() {
        return this.width - RIGHT;
    }

    private int getBottomButtonY() {
        return this.height - 26;
    }

    private int getCloseButtonX() {
        return this.width - RIGHT - getBottomButtonWidth();
    }

    private int getAdminButtonStartX() {
        int adminCount = 4;
        int bottomButtonWidth = getBottomButtonWidth();
        int totalWidth = adminCount * bottomButtonWidth + (adminCount - 1) * BOTTOM_BAR_GAP;
        return Math.max(LEFT + bottomButtonWidth + 8, getCloseButtonX() - BOTTOM_BAR_GAP - totalWidth);
    }

    private int getBottomButtonWidth() {
        int availableWidth = this.width - LEFT - RIGHT - BOTTOM_BAR_GAP * 5;
        return Math.max(42, Math.min(BOTTOM_BUTTON_W, availableWidth / 6));
    }

    private int getRightContentViewportTop() {
        return TOP;
    }

    private int getRightContentViewportBottom() {
        return getBottomButtonY() - 8;
    }

    private int getOverviewTop() {
        return getRightContentViewportTop() - (int) Math.round(rightContentScroll.scrollY());
    }

    private int getRightContentHeight() {
        return INFO_H + CARD_GAP + CARD_H + CARD_GAP + CARD_H;
    }

    private boolean isMouseOverRightContent(double mouseX, double mouseY) {
        return mouseX >= getRightPanelLeft()
            && mouseX <= getRightPanelRight()
            && mouseY >= getRightContentViewportTop()
            && mouseY <= getRightContentViewportBottom();
    }

    private void renderTitleAndLevelLine(GuiGraphics guiGraphics, ClientLoadoutUiState.Snapshot snapshot, int x, int y, int maxWidth) {
        String titleLabel = "称号: ";
        String levelLabel = "等级: ";
        String levelText = snapshot == null ? "-" : String.valueOf(snapshot.playerLevel());
        int labelColor = 0xFFD7D7D7;
        int cursor = x;
        guiGraphics.drawString(this.font, titleLabel, cursor, y, labelColor, false);
        cursor += this.font.width(titleLabel);
        int levelLabelWidth = this.font.width(levelLabel);
        int levelTextWidth = this.font.width(levelText);
        int levelX = Math.max(cursor + 50, x + maxWidth - levelLabelWidth - levelTextWidth);
        int titleWidth = Math.max(40, levelX - cursor - 10);
        drawClippedComponent(guiGraphics, resolveCurrentTitleComponent(snapshot), cursor, y, titleWidth);
        guiGraphics.drawString(this.font, levelLabel, levelX, y, labelColor, false);
        int levelColor = snapshot == null ? GuiConstants.COLOR_GRAY : PlayerLevelService.resolveLevelColorRgb(snapshot.playerLevel());
        guiGraphics.drawString(this.font, levelText, levelX + levelLabelWidth, y, levelColor, false);
    }

    private Component resolveCurrentTitleComponent(ClientLoadoutUiState.Snapshot snapshot) {
        if (snapshot == null || snapshot.playerTitleText() == null || snapshot.playerTitleText().isBlank()) {
            return Component.literal("-").withStyle(style -> style.withColor(0xFF7E8B98));
        }
        return PlayerTitleService.parseLegacyText(snapshot.playerTitleText());
    }

    private void drawClippedComponent(GuiGraphics guiGraphics, Component component, int x, int y, int maxWidth) {
        if (maxWidth <= 0) {
            return;
        }
        guiGraphics.enableScissor(x, y - 1, x + maxWidth, y + 10);
        guiGraphics.drawString(this.font, component, x, y, GuiConstants.COLOR_WHITE, false);
        guiGraphics.disableScissor();
    }

    private void drawClampedString(GuiGraphics guiGraphics, String text, int x, int y, int color, int maxWidth) {
        guiGraphics.drawString(this.font, clampToWidth(text, maxWidth), x, y, color, false);
    }

    private String clampToWidth(String text, int maxWidth) {
        String normalized = safe(text);
        if (normalized.isBlank() || this.font.width(normalized) <= maxWidth) {
            return normalized;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return this.font.plainSubstrByWidth(normalized, maxWidth);
        }
        return this.font.plainSubstrByWidth(normalized, maxWidth - ellipsisWidth) + ellipsis;
    }

    private void openAttachmentDialog(CustomLoadoutSlot slot, String attachmentType) {
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = getAttachmentPanel(slot);
        if (panel == null || !panel.available() || !panel.taczGun() || choiceDialog == null) {
            return;
        }
        ClientLoadoutUiState.AttachmentTypeState typeState = panel.attachmentTypes().get(attachmentType);
        if (typeState == null || !typeState.available()) {
            return;
        }
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        Set<String> lockedIds = new HashSet<>();
        Map<String, ChoiceListDialog.RowIcon> iconById = new HashMap<>();
        choices.add(new FixedChoiceConfigEntry.Choice("", "§f无配件 §8[卸下外置件]"));
        for (ClientLoadoutUiState.AttachmentChoiceState choice : typeState.sortedChoices()) {
            String display = choice.displayName().isBlank() ? choice.id() : choice.displayName();
            String lockSuffix = choice.unlocked() ? "" : ("  Lv." + choice.unlockLevel() + "未解锁");
            String prefix = choice.unlocked() ? "§f" : "§7";
            choices.add(new FixedChoiceConfigEntry.Choice(choice.id(), prefix + display + " §8[" + choice.id() + "]" + (lockSuffix.isBlank() ? "" : " §7" + lockSuffix)));
            ChoiceListDialog.RowIcon icon = buildItemIcon(choice.itemSnbt());
            if (icon != null && !icon.isEmpty()) {
                iconById.put(choice.id(), icon);
            }
            if (!choice.unlocked()) {
                lockedIds.add(choice.id());
            }
        }
        String selectedId = typeState.hasStoredSelection() ? typeState.selectedAttachmentId() : "";
        choiceDialog.show(
            selectedId,
            typeState.attachmentLabel(),
            choices,
            selected -> NetworkManager.getChannel().sendToServer(
                LoadoutUiActionPacket.selectAttachment(slot.getIndex(), panel.loadoutEntryId(), attachmentType, selected)
            ),
            null,
            null,
            null,
            "",
            0xFFFF0000,
            17,
            null,
            null,
            choice -> choice != null && (!lockedIds.contains(choice.value()) || choice.value().isBlank()),
            choice -> choice == null ? null : iconById.get(choice.value())
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

    private void openAttachmentEditorScreen() {
        if (minecraft == null || !shouldShowItemEditorButton()) {
            return;
        }
        minecraft.setScreen(new LoadoutAttachmentEditorScreen(this));
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

    private ClientLoadoutUiState.WeaponAttachmentPanelState getAttachmentPanel(CustomLoadoutSlot slot) {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || slot == null) {
            return null;
        }
        return snapshot.attachmentPanels().get(slot.getIndex());
    }

    private String buildAttachmentSummary(ClientLoadoutUiState.WeaponAttachmentPanelState panel) {
        if (panel == null || panel.attachmentTypes().isEmpty()) {
            return "暂无可用配件";
        }
        List<String> selectedLabels = new ArrayList<>();
        for (String type : ATTACHMENT_TYPE_ORDER) {
            ClientLoadoutUiState.AttachmentTypeState state = panel.attachmentTypes().get(type);
            if (state == null || !state.hasStoredSelection()) {
                continue;
            }
            String value = state.selectedAttachmentId().isBlank() ? "无配件" : (state.selectedDisplayName().isBlank() ? state.selectedAttachmentId() : state.selectedDisplayName());
            selectedLabels.add(state.attachmentLabel() + ":" + value);
            if (selectedLabels.size() >= 2) {
                break;
            }
        }
        return selectedLabels.isEmpty() ? "已保留枪械默认外置状态" : String.join("  |  ", selectedLabels);
    }

    private String resolveAttachmentLabel(String attachmentType) {
        return switch (attachmentType) {
            case "scope" -> "瞄准镜";
            case "muzzle" -> "枪口配件";
            case "stock" -> "枪托";
            case "grip" -> "前握把";
            case "laser" -> "激光指示器";
            case "extended_mag" -> "扩容弹匣";
            default -> attachmentType;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
