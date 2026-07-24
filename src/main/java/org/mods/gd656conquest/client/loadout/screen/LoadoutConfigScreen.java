package org.mods.gd656conquest.client.loadout.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.compat.tacz.TaczClientAttachmentCompat;
import org.mods.gd656conquest.client.compat.tacz.TaczGunPropertyPanelCompat;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.client.gui.tabs.AdminRoomEditorTabContent;
import org.mods.gd656conquest.client.gui.tabs.AttachmentEditorTabContent;
import org.mods.gd656conquest.client.gui.tabs.ItemEditorTabContent;
import org.mods.gd656conquest.client.gui.tabs.ParkourTabContent;
import org.mods.gd656conquest.client.gui.tabs.VehicleAdminEditorTabContent;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.title.PlayerTitleService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LoadoutConfigScreen extends Screen {
    private static final ResourceLocation CLASS_ICON_ASSAULT = ResourceLocation.parse("gd656conquest:textures/gui/class_assault.png");
    private static final ResourceLocation CLASS_ICON_SUPPORT = ResourceLocation.parse("gd656conquest:textures/gui/class_support.png");
    private static final ResourceLocation CLASS_ICON_ENGINEER = ResourceLocation.parse("gd656conquest:textures/gui/class_engineer.png");
    private static final ResourceLocation CLASS_ICON_RECON = ResourceLocation.parse("gd656conquest:textures/gui/class_recon.png");
    private static final int LEFT = GuiConstants.SCREEN_EDGE_DISTANCE;
    private static final int TOP_GAP = 6;
    private static final int LEFT_WIDTH_MAX = 170;
    private static final int LEFT_WIDTH_MIN = 128;
    private static final int PANEL_GAP = 12;
    private static final int BTN_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int RIGHT = GuiConstants.SCREEN_EDGE_DISTANCE;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private static final int INFO_H = 60;
    private static final int CARD_H = 132;
    private static final int CARD_GAP = 12;
    private static final int CARD_BUTTON_GAP = 8;
    private static final int CARD_BUTTON_H = 18;
    private static final int CHOICE_DIALOG_WIDTH = 64 + 8 + 155;
    private static final int CHOICE_DIALOG_HEIGHT = 192;
    private static final int WEAPON_PROPERTY_GAP = 12;
    private static final int WEAPON_PROPERTY_PANEL_GAP = 5;
    private static final int WEAPON_PROPERTY_BUTTON_W = 100;
    private static final int WEAPON_PROPERTY_BUTTON_H = 18;
    private static final List<String> ATTACHMENT_TYPE_ORDER = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");

    private final Screen parent;
    private ConfigScreenHeader header;

    private ChoiceListDialog choiceDialog;
    private final List<GDButton> actionButtons = new ArrayList<>();
    private final Map<String, GDButton> attachmentButtons = new LinkedHashMap<>();
    private GDButton vehicleToTabButton;
    private GDButton closeScreenButton;
    private GDButton weaponPropertyButton;
    private long lastRequestAt;
    private long outOfGameCloseAtMs;
    private final GDScrollState rightContentScroll = new GDScrollState();
    private FixedChoiceConfigEntry.Choice hoveredGunChoice;
    private FixedChoiceConfigEntry.Choice hoveredAttachmentChoice;
    private CustomLoadoutSlot currentHoverSlot;
    private String currentAttachmentType;
    private boolean weaponPropertyPanelVisible;

    // Admin tab content
    private VehicleAdminEditorTabContent vehicleEditorTab;
    private AdminRoomEditorTabContent roomEditorTab;
    private AttachmentEditorTabContent attachmentEditorTab;
    private ItemEditorTabContent itemEditorTab;
    private ParkourTabContent parkourTab;

    // Vehicle deploy inline page state
    private boolean vehicleDeployPageVisible;
    private final Map<String, GDButton> vehicleDeployButtons = new LinkedHashMap<>();
    private final GDScrollState vehicleDeployScroll = new GDScrollState();
    private GDButton vehicleBackButton;
    private boolean awaitingSuccessfulDeployClose;

    public LoadoutConfigScreen(Screen parent) {
        super(Component.translatable("gd656conquest.client.gui.loadout.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.choiceDialog = new ChoiceListDialog(minecraft);
        this.weaponPropertyButton = new GDButton(0, 0, WEAPON_PROPERTY_BUTTON_W, WEAPON_PROPERTY_BUTTON_H,
                Component.literal("武器数据：关"), btn -> toggleWeaponPropertyPanel());
        this.weaponPropertyButton.setAutoScaleTextToFit(true);
        this.weaponPropertyButton.setMinAutoScale(0.58F);
        refreshWeaponPropertyButtonLabel();
        updateOutOfGameTimer();
        rightContentScroll.reset();

        // Create header and tabs
        header = new ConfigScreenHeader();
        header.addTab(new HomeTab());
        if (isAdmin()) {
            initAdminTabs();
            header.addTab(vehicleEditorTab);
            header.addTab(roomEditorTab);
            header.addTab(attachmentEditorTab);
            header.addTab(itemEditorTab);
        }
        header.addTab(getOrCreateParkourTab());

        rebuildButtons();
        if (!isOutOfGame()) {
            requestSnapshotIfNeeded();
        }
    }

    private boolean isAdmin() {
        if (minecraft == null || minecraft.player == null) return false;
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        return snapshot != null && snapshot.admin() && minecraft.player.hasPermissions(2);
    }

    private void initAdminTabs() {
        vehicleEditorTab = new VehicleAdminEditorTabContent(minecraft);
        roomEditorTab = new AdminRoomEditorTabContent(minecraft);
        attachmentEditorTab = new AttachmentEditorTabContent(minecraft);
        itemEditorTab = new ItemEditorTabContent(minecraft);
    }

    private ParkourTabContent getOrCreateParkourTab() {
        if (parkourTab == null) parkourTab = new ParkourTabContent(minecraft);
        return parkourTab;
    }

    public Screen parentScreen() {
        return parent;
    }

    private int getContentTop() {
        return header != null ? header.getContentTop() : GuiConstants.HEADER_HEIGHT + GuiConstants.GOLD_BAR_HEIGHT + GuiConstants.DEFAULT_PADDING;
    }

    private int getTop() {
        return getContentTop() + TOP_GAP;
    }

    private void rebuildButtons() {
        actionButtons.clear();
        int leftWidth = getLeftColumnWidth();
        int contentTop = getContentTop();
        int btnY = getTop();
        actionButtons.add(new GDButton(LEFT, btnY, leftWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.class"), btn -> openClassDialog()));
        btnY += BTN_HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, btnY, leftWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.primary"), btn -> openSlotDialog(CustomLoadoutSlot.PRIMARY_WEAPON)));
        btnY += BTN_HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, btnY, leftWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.secondary"), btn -> openSlotDialog(CustomLoadoutSlot.SECONDARY_WEAPON)));
        btnY += BTN_HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, btnY, leftWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.melee"), btn -> openSlotDialog(CustomLoadoutSlot.MELEE)));
        btnY += BTN_HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, btnY, leftWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.gadget1"), btn -> openSlotDialog(CustomLoadoutSlot.GADGET_1)));
        btnY += BTN_HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, btnY, leftWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.gadget2"), btn -> openSlotDialog(CustomLoadoutSlot.GADGET_2)));
        btnY += BTN_HEIGHT + GAP;
        actionButtons.add(new GDButton(LEFT, btnY, leftWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.throwable"), btn -> openSlotDialog(CustomLoadoutSlot.THROWABLE)));

        // Vehicle deploy button area: left = 载具部署 (open inline page), right = 关闭
        int dualBtnY = getRightContentViewportBottom() - BTN_HEIGHT;
        int halfWidth = (leftWidth - GAP) / 2;
        vehicleToTabButton = new GDButton(LEFT, dualBtnY, halfWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.vehicle.button"),
                btn -> { vehicleDeployPageVisible = true; vehicleDeployScroll.reset(); });
        vehicleToTabButton.setAutoScaleTextToFit(true);
        closeScreenButton = new GDButton(LEFT + halfWidth + GAP, dualBtnY, halfWidth, BTN_HEIGHT,
                Component.translatable("gd656conquest.client.gui.loadout.close"), btn -> onClose());
        closeScreenButton.setAutoScaleTextToFit(true);

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

        // Tick admin tabs
        int tabIdx = header != null ? header.getSelectedTabIndex() : 0;
        if (isAdmin()) {
            // Tab 1: 房间载具, Tab 2: 房间数据, Tab 3: 配件数据, Tab 4: 物品数据
            if (tabIdx == 1 && vehicleEditorTab != null) vehicleEditorTab.tick();
            else if (tabIdx == 2 && roomEditorTab != null) roomEditorTab.tick();
            else if (tabIdx == 3 && attachmentEditorTab != null) attachmentEditorTab.tick();
            else if (tabIdx == 4 && itemEditorTab != null) itemEditorTab.tick();
        }
        int parkourIdx = isAdmin() ? 5 : 1;
        if (tabIdx == parkourIdx && parkourTab != null) parkourTab.tick();

        // Tick vehicle deploy inline page
        if (vehicleDeployPageVisible) tickVehicleDeployPage();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        if (isOutOfGame()) {
            int remain = Math.max(0, (int) Math.ceil((outOfGameCloseAtMs - System.currentTimeMillis()) / 1000.0));
            guiGraphics.drawCenteredString(this.font, "请在游戏内打开配置界面", this.width / 2, this.height / 2 - 10, 0xFFFF8080);
            guiGraphics.drawCenteredString(this.font, "将在 " + remain + " 秒后自动关闭", this.width / 2, this.height / 2 + 6, 0xFFB8C6D6);
            return;
        }

        int contentTop = getContentTop();
        int activeTab = header != null ? header.getSelectedTabIndex() : 0;

        // Render header
        if (header != null) {
            header.render(guiGraphics, this.width, mouseX, mouseY, partialTick);
        }

        // If vehicle deploy inline page is active, render it instead of tab content
        if (vehicleDeployPageVisible) {
            renderVehicleDeployPage(guiGraphics, mouseX, mouseY, partialTick, contentTop);
            return;
        }

        // Render active tab content
        if (activeTab == 0) {
            renderHomeTab(guiGraphics, mouseX, mouseY, partialTick);
        } else if (isAdmin()) {
            ConfigScreenHeader.TabContent content = getAdminTabContent(activeTab);
            if (content != null)
                content.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, contentTop);
        }
        int parkourIdx = isAdmin() ? 5 : 1;
        if (activeTab == parkourIdx && parkourTab != null) {
            parkourTab.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, contentTop);
        }

        // Render dialogs on top
        syncChoiceDialogHorizontalOffset();
        if (choiceDialog != null && choiceDialog.isVisible()) {
            choiceDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    // ========== Home Tab ==========

    private void renderHomeTab(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int top = getTop();
        int baseMouseX = getBaseUiMouseX(mouseX);
        int baseMouseY = getBaseUiMouseY(mouseY);
        for (GDButton button : actionButtons) {
            button.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }

        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        rightContentScroll.update(baseMouseY, getRightContentViewportBottom() - getRightContentViewportTop(), getRightContentHeight());
        layoutAttachmentButtons(CustomLoadoutSlot.PRIMARY_WEAPON, getPrimaryCardTop());
        layoutAttachmentButtons(CustomLoadoutSlot.SECONDARY_WEAPON, getSecondaryCardTop());

        guiGraphics.enableScissor(getRightPanelLeft(), getRightContentViewportTop(), getRightPanelRight(), getRightContentViewportBottom());
        renderOverviewPanel(guiGraphics, snapshot);
        renderAttachmentCard(guiGraphics, baseMouseX, baseMouseY, partialTick, CustomLoadoutSlot.PRIMARY_WEAPON, getPrimaryCardTop());
        renderAttachmentCard(guiGraphics, baseMouseX, baseMouseY, partialTick, CustomLoadoutSlot.SECONDARY_WEAPON, getSecondaryCardTop());
        guiGraphics.disableScissor();
        if (snapshot == null) {
            guiGraphics.drawString(this.font, Component.translatable("gd656conquest.client.gui.loadout.loading"),
                    LEFT, top + (BTN_HEIGHT + GAP) * 7 + 12, 0xFFAA00, false);
        }
        rightContentScroll.renderScrollbar(guiGraphics, getRightPanelLeft(), getRightContentViewportTop(),
                getRightPanelRight(), getRightContentViewportBottom(), getRightContentHeight());

        // Vehicle deploy + close buttons (split)
        if (vehicleToTabButton != null && closeScreenButton != null) {
            ClientLoadoutUiState.Snapshot snap = ClientLoadoutUiState.snapshot();
            boolean available = snap != null && snap.vehiclePageAvailable();
            vehicleToTabButton.active = available;
            vehicleToTabButton.setTextColor(available ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            int leftWidth = getLeftColumnWidth();
            int dualBtnY = getRightContentViewportBottom() - BTN_HEIGHT;
            int halfWidth = (leftWidth - GAP) / 2;
            vehicleToTabButton.setX(LEFT);
            vehicleToTabButton.setY(dualBtnY);
            vehicleToTabButton.setWidth(halfWidth);
            vehicleToTabButton.setHeight(BTN_HEIGHT);
            vehicleToTabButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
            closeScreenButton.setX(LEFT + halfWidth + GAP);
            closeScreenButton.setY(dualBtnY);
            closeScreenButton.setWidth(halfWidth);
            closeScreenButton.setHeight(BTN_HEIGHT);
            closeScreenButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }

        renderWeaponPropertyPanel(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ========== Layout Helpers ==========

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

    private int getRightContentViewportTop() {
        return getTop();
    }

    private int getRightContentViewportBottom() {
        return this.height - 16;
    }

    private int getRightContentHeight() {
        return INFO_H + CARD_GAP + CARD_H + CARD_GAP + CARD_H;
    }

    private int getOverviewTop() {
        return getRightContentViewportTop() - (int) Math.round(rightContentScroll.scrollY());
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

    private boolean isMouseOverRightContent(double mouseX, double mouseY) {
        return mouseX >= getRightPanelLeft()
                && mouseX <= getRightPanelRight()
                && mouseY >= getRightContentViewportTop()
                && mouseY <= getRightContentViewportBottom();
    }

    // ========== Home Tab Content Rendering ==========

    private void createAttachmentButtons(CustomLoadoutSlot slot, int topY) {
        int buttonWidth = Math.max(74, (getCardInnerWidth() - CARD_BUTTON_GAP) / 2);
        for (int i = 0; i < ATTACHMENT_TYPE_ORDER.size(); i++) {
            String attachmentType = ATTACHMENT_TYPE_ORDER.get(i);
            int column = i % 2;
            int row = i / 2;
            int x = getRightPanelLeft() + 8 + column * (buttonWidth + CARD_BUTTON_GAP);
            int y = topY + 42 + row * (CARD_BUTTON_H + 6);
            GDButton button = new GDButton(x, y, buttonWidth, CARD_BUTTON_H,
                    Component.literal(resolveAttachmentLabel(attachmentType)),
                    btn -> openAttachmentDialog(slot, attachmentType));
            attachmentButtons.put(slot.getKey() + ":" + attachmentType, button);
        }
    }

    private void layoutAttachmentButtons(CustomLoadoutSlot slot, int topY) {
        int buttonWidth = Math.max(74, (getCardInnerWidth() - CARD_BUTTON_GAP) / 2);
        for (int i = 0; i < ATTACHMENT_TYPE_ORDER.size(); i++) {
            String attachmentType = ATTACHMENT_TYPE_ORDER.get(i);
            GDButton button = attachmentButtons.get(slot.getKey() + ":" + attachmentType);
            if (button == null) continue;
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
            if (button == null) continue;
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
            if (button == null) continue;
            button.active = active;
            button.setTextColor(active ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
        }
    }

    // ========== Weapon Property Panel ==========

    private void renderWeaponPropertyPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderWeaponPropertyOverlayContents(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderWeaponPropertyOverlayContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!shouldShowWeaponPropertyOverlay() || weaponPropertyButton == null) {
            weaponPropertyPanelVisible = false;
            refreshWeaponPropertyButtonLabel();
            return;
        }

        ItemStack displayedWeapon = resolveDisplayedWeaponStack();
        TaczGunPropertyPanelCompat propertyPanel = TaczGunPropertyPanelCompat.getInstance();
        boolean canRender = !displayedWeapon.isEmpty() && propertyPanel.canRender(displayedWeapon);
        if (!canRender) weaponPropertyPanelVisible = false;
        refreshWeaponPropertyButtonLabel();

        int buttonX = getChoiceDialogOuterRight() - WEAPON_PROPERTY_BUTTON_W;
        int buttonY = getChoiceDialogOuterTop() - WEAPON_PROPERTY_BUTTON_H - 5;

        weaponPropertyButton.setX(buttonX);
        weaponPropertyButton.setY(buttonY);
        weaponPropertyButton.setWidth(WEAPON_PROPERTY_BUTTON_W);
        weaponPropertyButton.setHeight(WEAPON_PROPERTY_BUTTON_H);
        weaponPropertyButton.active = canRender;
        weaponPropertyButton.setTextColor(canRender ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 510.0F);
        weaponPropertyButton.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.pose().popPose();

        if (!weaponPropertyPanelVisible || !canRender) return;

        int overlayX = getChoiceDialogOuterRight() + WEAPON_PROPERTY_PANEL_GAP;
        int panelY = getChoiceDialogOuterTop();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 510.0F);
        propertyPanel.render(guiGraphics, this.font, overlayX, panelY, getChoiceDialogOuterHeight(), displayedWeapon);
        guiGraphics.pose().popPose();
    }

    // ========== Event Handling ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncChoiceDialogHorizontalOffset();
        if (button == 0 && weaponPropertyButton != null && shouldShowWeaponPropertyOverlay() && weaponPropertyButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            if (isOutOfGame()) {
                return true;
            }
        }

        // Header receives click first
        if (header != null && header.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int activeTab = header != null ? header.getSelectedTabIndex() : 0;
        int contentTop = getContentTop();

        // Vehicle deploy inline page
        if (vehicleDeployPageVisible) {
            return mouseClickedVehicleDeployPage(mouseX, mouseY, button);
        }

        if (activeTab == 0) {
            return mouseClickedHome(mouseX, mouseY, button);
        }
        if (isAdmin()) {
            ConfigScreenHeader.TabContent content = getAdminTabContent(activeTab);
            if (content != null) {
                if (content.mouseClicked(mouseX, mouseY, button)) return true;
                return mouseClickedAdminContent(content, mouseX, mouseY, button, contentTop);
            }
        }
        int parkourIdx = isAdmin() ? 5 : 1;
        if (activeTab == parkourIdx && parkourTab != null) {
            if (parkourTab.mouseClicked(mouseX, mouseY, button)) return true;
            return parkourTab.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseClickedHome(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (vehicleToTabButton != null && vehicleToTabButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (closeScreenButton != null && closeScreenButton.mouseClicked(mouseX, mouseY, button)) return true;
            for (GDButton attachmentButton : attachmentButtons.values()) {
                if (attachmentButton.mouseClicked(mouseX, mouseY, button)) return true;
            }
            for (GDButton actionButton : actionButtons) {
                if (actionButton.mouseClicked(mouseX, mouseY, button)) return true;
            }
            if (isMouseOverRightContent(mouseX, mouseY)) {
                rightContentScroll.startDragging(mouseX, mouseY,
                        getRightPanelLeft(), getRightContentViewportTop(),
                        getRightPanelRight(), getRightContentViewportBottom(),
                        getRightContentHeight());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (header != null) header.mouseReleased(mouseX, mouseY, button);
        if (choiceDialog != null) choiceDialog.mouseReleased(mouseX, mouseY, button);
        rightContentScroll.stopDragging();

        int activeTab = header != null ? header.getSelectedTabIndex() : 0;
        if (vehicleDeployPageVisible) return false;
        if (isAdmin()) {
            ConfigScreenHeader.TabContent content = getAdminTabContent(activeTab);
            if (content != null) content.mouseReleased(mouseX, mouseY, button);
        }
        int parkourIdx = isAdmin() ? 5 : 1;
        if (activeTab == parkourIdx && parkourTab != null) parkourTab.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (header != null && header.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        syncChoiceDialogHorizontalOffset();
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.mouseScrolled(mouseX, mouseY, delta)) return true;

        if (header != null && header.mouseScrolled(mouseX, mouseY, delta)) return true;

        int activeTab = header != null ? header.getSelectedTabIndex() : 0;
        int contentTop = getContentTop();

        if (vehicleDeployPageVisible) {
            return mouseScrolledVehicleDeployPage(mouseX, mouseY, delta);
        }
        if (activeTab == 0) {
            if (isMouseOverRightContent(mouseX, mouseY)) {
                return rightContentScroll.mouseScrolled(delta,
                        getRightContentViewportBottom() - getRightContentViewportTop(), getRightContentHeight());
            }
        } else if (isAdmin()) {
            ConfigScreenHeader.TabContent content = getAdminTabContent(activeTab);
            if (content != null)
                return mouseScrolledAdminContent(content, mouseX, mouseY, delta, contentTop);
        }
        int parkourIdx = isAdmin() ? 5 : 1;
        if (activeTab == parkourIdx && parkourTab != null)
            return parkourTab.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.keyPressed(keyCode, scanCode, modifiers)) return true;

        int activeTab = header != null ? header.getSelectedTabIndex() : 0;
        if (vehicleDeployPageVisible) return false;
        if (isAdmin()) {
            ConfigScreenHeader.TabContent content = getAdminTabContent(activeTab);
            if (content != null && content.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        int parkourIdx = isAdmin() ? 5 : 1;
        if (activeTab == parkourIdx && parkourTab != null && parkourTab.keyPressed(keyCode, scanCode, modifiers)) return true;

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (choiceDialog != null && choiceDialog.isVisible() && choiceDialog.charTyped(codePoint, modifiers)) return true;

        int activeTab = header != null ? header.getSelectedTabIndex() : 0;
        if (vehicleDeployPageVisible) return false;
        if (isAdmin()) {
            ConfigScreenHeader.TabContent content = getAdminTabContent(activeTab);
            if (content != null && content.charTyped(codePoint, modifiers)) return true;
        }
        int parkourIdx = isAdmin() ? 5 : 1;
        if (activeTab == parkourIdx && parkourTab != null && parkourTab.charTyped(codePoint, modifiers)) return true;

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

    // ========== Dialog openers ==========

    private void openClassDialog() {
        clearWeaponPropertyOverlayState();
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || snapshot.classes().isEmpty() || choiceDialog == null) { requestSnapshotIfNeeded(); return; }
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        Map<String, ChoiceListDialog.RowIcon> iconByClassKey = new HashMap<>();
        for (ClientLoadoutUiState.ClassState classState : snapshot.classes().values()) {
            choices.add(new FixedChoiceConfigEntry.Choice(classState.classKey(), classState.className()));
            ChoiceListDialog.RowIcon icon = resolveClassIcon(classState.classKey());
            if (icon != null && !icon.isEmpty()) iconByClassKey.put(classState.classKey(), icon);
        }
        choiceDialog.show(snapshot.currentClassKey(), "兵种选择", choices,
                selectedClass -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectClass(selectedClass)),
                null, null, null, "", 0xFFFF0000, 17, null, null, null,
                choice -> choice == null ? null : iconByClassKey.get(choice.value()));
    }

    private void openSlotDialog(CustomLoadoutSlot slot) {
        clearWeaponPropertyOverlayState();
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || snapshot.classes().isEmpty() || choiceDialog == null) { requestSnapshotIfNeeded(); return; }
        ClientLoadoutUiState.ClassState currentClass = snapshot.classes().get(snapshot.currentClassKey());
        if (currentClass == null) { requestSnapshotIfNeeded(); return; }
        ClientLoadoutUiState.SlotState slotState = currentClass.slots().get(slot.getIndex());
        if (slotState == null || slotState.entries().isEmpty()) return;

        Set<String> lockedIds = new HashSet<>();
        Set<String> roomDisabledIds = new HashSet<>();
        Map<String, ChoiceListDialog.RowIcon> iconByEntryId = new HashMap<>();
        String classKey = currentClass.classKey();
        String selectedId = slotState.selectedId();
        if (selectedId == null || selectedId.isBlank() || !slotState.entries().containsKey(selectedId))
            selectedId = slotState.entries().isEmpty() ? "" : slotState.entries().keySet().iterator().next();

        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        for (ClientLoadoutUiState.EntryState entry : slotState.sortedEntries()) {
            String display = entry.displayName().isBlank() ? entry.id() : entry.displayName();
            String lockSuffix = entry.unlocked() ? "" : ("  Lv." + entry.unlockLevel() + "未解锁");
            boolean roomEnabled = entry.roomEnabled();
            String prefix = !roomEnabled ? "\u00a78" : entry.unlocked() ? "\u00a7f" : "\u00a77";
            String label = prefix + display + " \u00a78[" + entry.id() + "]" + (lockSuffix.isBlank() ? "" : " \u00a77" + lockSuffix);
            choices.add(new FixedChoiceConfigEntry.Choice(entry.id(), label));
            ChoiceListDialog.RowIcon icon = buildItemIcon(entry.itemSnbt());
            if (icon != null && !icon.isEmpty()) iconByEntryId.put(entry.id(), icon);
            if (!entry.unlocked()) lockedIds.add(entry.id());
            if (!roomEnabled) roomDisabledIds.add(entry.id());
        }

        if (slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON) {
            currentHoverSlot = slot;
            hoveredGunChoice = null;
            weaponPropertyPanelVisible = true;
            refreshWeaponPropertyButtonLabel();
            choiceDialog.show(selectedId, slotState.slotLabel().isBlank() ? slot.getKey() : slotState.slotLabel(), choices,
                    selected -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectSlot(classKey, slot.getIndex(), selected)),
                    null, null, null, "", 0xFFFF0000, 17, null, null,
                    choice -> choice != null && !lockedIds.contains(choice.value()) && !roomDisabledIds.contains(choice.value()),
                    choice -> choice == null ? null : iconByEntryId.get(choice.value()),
                    this::updateHoveredGunChoice,
                    choice -> choice != null && roomDisabledIds.contains(choice.value()));
            syncChoiceDialogHorizontalOffset();
            return;
        }

        choiceDialog.show(selectedId, slotState.slotLabel().isBlank() ? slot.getKey() : slotState.slotLabel(), choices,
                selected -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectSlot(classKey, slot.getIndex(), selected)),
                null, null, null, "", 0xFFFF0000, 17, null, null,
                choice -> choice != null && !lockedIds.contains(choice.value()) && !roomDisabledIds.contains(choice.value()),
                choice -> choice == null ? null : iconByEntryId.get(choice.value()),
                null,
                choice -> choice != null && roomDisabledIds.contains(choice.value()));
    }

    private void openAttachmentDialog(CustomLoadoutSlot slot, String attachmentType) {
        clearWeaponPropertyOverlayState();
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = getAttachmentPanel(slot);
        if (panel == null || !panel.available() || !panel.taczGun() || choiceDialog == null) return;
        ClientLoadoutUiState.AttachmentTypeState typeState = panel.attachmentTypes().get(attachmentType);
        if (typeState == null || !typeState.available()) return;
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        Set<String> lockedIds = new HashSet<>();
        Map<String, ChoiceListDialog.RowIcon> iconById = new HashMap<>();
        boolean filterByScope = "scope".equalsIgnoreCase(attachmentType) && typeState.maxScopeMagnification() > 0.0D;
        choices.add(new FixedChoiceConfigEntry.Choice("", "\u00a7f无配件 \u00a78[卸下外置件]"));
        for (ClientLoadoutUiState.AttachmentChoiceState choice : typeState.sortedChoices()) {
            if (filterByScope) {
                double cm = TaczClientAttachmentCompat.getInstance().resolveScopeMaxMagnification(choice.itemSnbt());
                if (cm > 0.0D && cm - typeState.maxScopeMagnification() > 0.0001D) continue;
            }
            String display = resolveAttachmentChoiceDisplayName(choice);
            String lockSuffix = choice.unlocked() ? "" : ("  Lv." + choice.unlockLevel() + "未解锁");
            String prefix = choice.unlocked() ? "\u00a7f" : "\u00a77";
            choices.add(new FixedChoiceConfigEntry.Choice(choice.id(), prefix + display + " \u00a78[" + choice.id() + "]" + (lockSuffix.isBlank() ? "" : " \u00a77" + lockSuffix)));
            ChoiceListDialog.RowIcon icon = buildItemIcon(choice.itemSnbt());
            if (icon != null && !icon.isEmpty()) iconById.put(choice.id(), icon);
            if (!choice.unlocked()) lockedIds.add(choice.id());
        }
        String selectedId = typeState.hasStoredSelection() ? typeState.selectedAttachmentId() : "";
        currentHoverSlot = slot;
        currentAttachmentType = attachmentType;
        hoveredAttachmentChoice = null;
        weaponPropertyPanelVisible = true;
        refreshWeaponPropertyButtonLabel();

        choiceDialog.show(selectedId, typeState.attachmentLabel(), choices,
                selected -> {
                    playAttachmentSelectionSound(typeState, selectedId, selected);
                    NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectAttachment(slot.getIndex(), panel.loadoutEntryId(), attachmentType, selected));
                },
                null, null, null, "", 0xFFFF0000, 17, null, null,
                choice -> choice != null && (!lockedIds.contains(choice.value()) || choice.value().isBlank()),
                choice -> choice == null ? null : iconById.get(choice.value()),
                this::updateHoveredAttachmentChoice);
        syncChoiceDialogHorizontalOffset();
    }

    private void playAttachmentSelectionSound(ClientLoadoutUiState.AttachmentTypeState typeState, String prevId, String selectedId) {
        if (typeState == null || Objects.equals(prevId, selectedId)) return;
        TaczClientAttachmentCompat compat = TaczClientAttachmentCompat.getInstance();
        if (selectedId == null || selectedId.isBlank()) {
            ItemStack prev = resolveAttachmentChoiceStack(typeState, prevId);
            if (!prev.isEmpty()) compat.playAttachmentUninstallSound(prev);
            return;
        }
        ItemStack sel = resolveAttachmentChoiceStack(typeState, selectedId);
        if (!sel.isEmpty()) compat.playAttachmentInstallSound(sel);
    }

    private void updateHoveredGunChoice(FixedChoiceConfigEntry.Choice choice) {
        if (choice != null) hoveredGunChoice = choice;
    }

    private void updateHoveredAttachmentChoice(FixedChoiceConfigEntry.Choice choice) {
        if (choice != null) hoveredAttachmentChoice = choice;
    }

    // ========== Utility methods (copied from original) ==========

    private ConfigScreenHeader.TabContent getAdminTabContent(int tabIndex) {
        return switch (tabIndex) {
            case 1 -> vehicleEditorTab;
            case 2 -> roomEditorTab;
            case 3 -> attachmentEditorTab;
            case 4 -> itemEditorTab;
            default -> null;
        };
    }

    private boolean mouseClickedAdminContent(ConfigScreenHeader.TabContent content, double mouseX, double mouseY, int button, int contentTop) {
        if (content instanceof AdminRoomEditorTabContent r) return r.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        if (content instanceof AttachmentEditorTabContent a) return a.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        if (content instanceof ItemEditorTabContent i) return i.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        return false;
    }

    private boolean mouseScrolledAdminContent(ConfigScreenHeader.TabContent content, double mouseX, double mouseY, double delta, int contentTop) {
        if (content instanceof AdminRoomEditorTabContent r) return r.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        if (content instanceof AttachmentEditorTabContent a) return a.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        if (content instanceof ItemEditorTabContent i) return i.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        return content.mouseScrolled(mouseX, mouseY, delta);
    }

    private String safe(String value) { return value == null ? "" : value; }

    private boolean isOutOfGame() {
        return minecraft == null || minecraft.player == null || minecraft.level == null;
    }

    private void updateOutOfGameTimer() {
        if (!isOutOfGame()) { outOfGameCloseAtMs = -1L; return; }
        if (outOfGameCloseAtMs <= 0L) outOfGameCloseAtMs = System.currentTimeMillis() + 3000L;
    }

    private void requestSnapshotIfNeeded() {
        if (isOutOfGame()) return;
        long now = System.currentTimeMillis();
        if (now - lastRequestAt < REQUEST_INTERVAL_MS) return;
        lastRequestAt = now;
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));
    }

    private ClientLoadoutUiState.WeaponAttachmentPanelState getAttachmentPanel(CustomLoadoutSlot slot) {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || slot == null) return null;
        return snapshot.attachmentPanels().get(slot.getIndex());
    }

    private ItemStack parseStack(String snbt) {
        return ClientLoadoutUiState.parseAndCacheStack(snbt);
    }

    private String buildAttachmentSummary(ClientLoadoutUiState.WeaponAttachmentPanelState panel) {
        if (panel == null || panel.attachmentTypes().isEmpty()) return "暂无可用配件";
        List<String> selectedLabels = new ArrayList<>();
        for (String type : ATTACHMENT_TYPE_ORDER) {
            ClientLoadoutUiState.AttachmentTypeState state = panel.attachmentTypes().get(type);
            if (state == null || !state.hasStoredSelection()) continue;
            String value = resolveSelectedAttachmentDisplayName(state);
            selectedLabels.add(state.attachmentLabel() + ":" + value);
            if (selectedLabels.size() >= 2) break;
        }
        return selectedLabels.isEmpty() ? "已保留枪械默认外置状态" : String.join("  |  ", selectedLabels);
    }

    private String resolveSelectedAttachmentDisplayName(ClientLoadoutUiState.AttachmentTypeState state) {
        if (state == null || !state.hasStoredSelection()) return "";
        if (state.selectedAttachmentId().isBlank()) return "无配件";
        ClientLoadoutUiState.AttachmentChoiceState sc = state.choices().get(state.selectedAttachmentId());
        return sc != null ? resolveAttachmentChoiceDisplayName(sc) : pickReadableAttachmentName(state.selectedDisplayName(), state.selectedAttachmentId());
    }

    private String resolveAttachmentChoiceDisplayName(ClientLoadoutUiState.AttachmentChoiceState choice) {
        if (choice == null) return "";
        String ld = TaczClientAttachmentCompat.getInstance().resolveLocalizedAttachmentDisplayName(choice.itemSnbt());
        return !ld.isBlank() ? ld : pickReadableAttachmentName(choice.displayName(), choice.id());
    }

    private String pickReadableAttachmentName(String preferred, String fallbackId) {
        String np = safe(preferred).trim();
        return !np.isBlank() && !looksLikeTranslationKey(np) ? np : safe(fallbackId);
    }

    private boolean looksLikeTranslationKey(String value) {
        if (value == null || value.isBlank()) return false;
        boolean hd = value.indexOf('.') >= 0 || value.indexOf(':') >= 0;
        boolean hw = value.chars().anyMatch(Character::isWhitespace);
        boolean hc = value.chars().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        return hd && !hw && !hc;
    }

    private String resolveAttachmentLabel(String type) {
        return switch (type) {
            case "scope" -> "瞄准镜"; case "muzzle" -> "枪口配件"; case "stock" -> "枪托";
            case "grip" -> "前握把"; case "laser" -> "激光指示器"; case "extended_mag" -> "扩容弹匣";
            default -> type;
        };
    }

    private void clearWeaponPropertyOverlayState() {
        currentHoverSlot = null;
        hoveredGunChoice = null;
        hoveredAttachmentChoice = null;
        currentAttachmentType = null;
        weaponPropertyPanelVisible = false;
        syncChoiceDialogHorizontalOffset();
        refreshWeaponPropertyButtonLabel();
    }

    private void refreshWeaponPropertyButtonLabel() {
        if (weaponPropertyButton != null)
            weaponPropertyButton.setMessage(Component.literal(weaponPropertyPanelVisible ? "武器数据：开" : "武器数据：关"));
    }

    private boolean shouldShowWeaponPropertyOverlay() {
        return choiceDialog != null && choiceDialog.isVisible() && isWeaponSlot(currentHoverSlot);
    }

    private boolean isWeaponSlot(CustomLoadoutSlot slot) {
        return slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON;
    }

    private boolean isBlockingOverlayVisible() {
        return choiceDialog != null && choiceDialog.isVisible();
    }

    private int getBaseUiMouseX(int mouseX) {
        return isBlockingOverlayVisible() ? Integer.MIN_VALUE / 4 : mouseX;
    }

    private int getBaseUiMouseY(int mouseY) {
        return isBlockingOverlayVisible() ? Integer.MIN_VALUE / 4 : mouseY;
    }

    private void toggleWeaponPropertyPanel() {
        if (!shouldShowWeaponPropertyOverlay()) { weaponPropertyPanelVisible = false; refreshWeaponPropertyButtonLabel(); return; }
        ItemStack dw = resolveDisplayedWeaponStack();
        if (dw.isEmpty() || !TaczGunPropertyPanelCompat.getInstance().canRender(dw)) { weaponPropertyPanelVisible = false; refreshWeaponPropertyButtonLabel(); return; }
        weaponPropertyPanelVisible = !weaponPropertyPanelVisible;
        syncChoiceDialogHorizontalOffset();
        refreshWeaponPropertyButtonLabel();
    }

    private ItemStack resolveDisplayedWeaponStack() {
        if (!isWeaponSlot(currentHoverSlot)) return ItemStack.EMPTY;
        if (currentAttachmentType != null && !currentAttachmentType.isBlank()) return resolveDisplayedAttachmentPreviewStack();
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) return ItemStack.EMPTY;
        ClientLoadoutUiState.ClassState cc = snapshot.classes().get(snapshot.currentClassKey());
        if (cc == null) return ItemStack.EMPTY;
        ClientLoadoutUiState.SlotState ss = cc.slots().get(currentHoverSlot.getIndex());
        if (ss == null) return ItemStack.EMPTY;
        String tid = hoveredGunChoice != null ? hoveredGunChoice.value() : ss.selectedId();
        if (tid == null || tid.isBlank() || !ss.entries().containsKey(tid)) tid = resolveFallbackDisplayedWeaponId(ss);
        if (tid == null || tid.isBlank()) return ItemStack.EMPTY;
        ClientLoadoutUiState.EntryState entry = ss.entries().get(tid);
        return entry == null ? ItemStack.EMPTY : parseStack(entry.itemSnbt());
    }

    private String resolveFallbackDisplayedWeaponId(ClientLoadoutUiState.SlotState ss) {
        if (ss == null || ss.entries().isEmpty()) return "";
        for (ClientLoadoutUiState.EntryState entry : ss.sortedEntries()) {
            if (entry != null && entry.id() != null && !entry.id().isBlank() && !parseStack(entry.itemSnbt()).isEmpty())
                return entry.id();
        }
        return ss.sortedEntries().stream().map(ClientLoadoutUiState.EntryState::id).filter(id -> id != null && !id.isBlank()).findFirst().orElse("");
    }

    private ItemStack resolveDisplayedAttachmentPreviewStack() {
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = getAttachmentPanel(currentHoverSlot);
        if (panel == null || !panel.available() || !panel.taczGun()) return ItemStack.EMPTY;
        ItemStack ws = parseStack(panel.weaponItemSnbt());
        if (ws.isEmpty()) return ItemStack.EMPTY;
        if (hoveredAttachmentChoice == null || hoveredAttachmentChoice.value() == null || hoveredAttachmentChoice.value().isBlank()) return ws;
        ClientLoadoutUiState.AttachmentTypeState ts = panel.attachmentTypes().get(currentAttachmentType);
        if (ts == null) return ws;
        ClientLoadoutUiState.AttachmentChoiceState cs = ts.choices().get(hoveredAttachmentChoice.value());
        if (cs == null) return ws;
        ItemStack as = parseStack(cs.itemSnbt());
        return as.isEmpty() ? ws : TaczClientAttachmentCompat.getInstance().installAttachment(ws, as);
    }

    private ItemStack resolveAttachmentChoiceStack(ClientLoadoutUiState.AttachmentTypeState typeState, String attachmentId) {
        if (typeState == null || attachmentId == null || attachmentId.isBlank()) return ItemStack.EMPTY;
        ClientLoadoutUiState.AttachmentChoiceState cs = typeState.choices().get(attachmentId);
        return cs == null ? ItemStack.EMPTY : parseStack(cs.itemSnbt());
    }

    private ChoiceListDialog.RowIcon buildItemIcon(String itemSnbt) {
        if (itemSnbt == null || itemSnbt.isBlank()) return null;
        ItemStack stack = ClientLoadoutUiState.parseAndCacheStack(itemSnbt);
        return stack.isEmpty() ? null : ChoiceListDialog.RowIcon.item(stack);
    }

    private ChoiceListDialog.RowIcon resolveClassIcon(String classKey) {
        if (classKey == null || classKey.isBlank()) return null;
        return switch (classKey.toLowerCase()) {
            case "assault" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_ASSAULT);
            case "support" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_SUPPORT);
            case "engineer" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_ENGINEER);
            case "recon" -> ChoiceListDialog.RowIcon.texture(CLASS_ICON_RECON);
            default -> null;
        };
    }

    private String resolveCurrentClassName(ClientLoadoutUiState.Snapshot snapshot) {
        if (snapshot == null || snapshot.currentClassKey() == null || snapshot.currentClassKey().isBlank()) return "-";
        ClientLoadoutUiState.ClassState cs = snapshot.classes().get(snapshot.currentClassKey());
        return cs == null || cs.className() == null || cs.className().isBlank() ? snapshot.currentClassKey() : cs.className();
    }

    private void renderTitleAndLevelLine(GuiGraphics guiGraphics, ClientLoadoutUiState.Snapshot snapshot, int x, int y, int maxWidth) {
        String titleLabel = "称号: ", levelLabel = "等级: ";
        String levelText = snapshot == null ? "-" : String.valueOf(snapshot.playerLevel());
        int labelColor = 0xFFD7D7D7, cursor = x;
        guiGraphics.drawString(this.font, titleLabel, cursor, y, labelColor, false);
        cursor += this.font.width(titleLabel);
        int llw = this.font.width(levelLabel), ltw = this.font.width(levelText);
        int lx = Math.max(cursor + 50, x + maxWidth - llw - ltw);
        int tw = Math.max(40, lx - cursor - 10);
        drawClippedComponent(guiGraphics, resolveCurrentTitleComponent(snapshot), cursor, y, tw);
        guiGraphics.drawString(this.font, levelLabel, lx, y, labelColor, false);
        int lc = snapshot == null ? GuiConstants.COLOR_GRAY : PlayerLevelService.resolveLevelColorRgb(snapshot.playerLevel());
        guiGraphics.drawString(this.font, levelText, lx + llw, y, lc, false);
    }

    private Component resolveCurrentTitleComponent(ClientLoadoutUiState.Snapshot snapshot) {
        if (snapshot == null || snapshot.playerTitleText() == null || snapshot.playerTitleText().isBlank())
            return Component.literal("-").withStyle(style -> style.withColor(0xFF7E8B98));
        return PlayerTitleService.parseLegacyText(snapshot.playerTitleText());
    }

    private void drawClippedComponent(GuiGraphics guiGraphics, Component component, int x, int y, int maxWidth) {
        if (maxWidth <= 0) return;
        guiGraphics.enableScissor(x, y - 1, x + maxWidth, y + 10);
        guiGraphics.drawString(this.font, component, x, y, GuiConstants.COLOR_WHITE, false);
        guiGraphics.disableScissor();
    }

    private void drawClampedString(GuiGraphics guiGraphics, String text, int x, int y, int color, int maxWidth) {
        guiGraphics.drawString(this.font, clampToWidth(text, maxWidth), x, y, color, false);
    }

    private String clampToWidth(String text, int maxWidth) {
        String normalized = safe(text);
        if (normalized.isBlank() || this.font.width(normalized) <= maxWidth) return normalized;
        String ellipsis = "...";
        int ew = this.font.width(ellipsis);
        return ew >= maxWidth ? this.font.plainSubstrByWidth(normalized, maxWidth)
                : this.font.plainSubstrByWidth(normalized, maxWidth - ew) + ellipsis;
    }

    // ========== Vehicle Deploy Inline Page ==========

    private static final int VEHICLE_ROW_HEIGHT = 46;
    private static final int VEHICLE_ROW_GAP = 6;
    private static final int VEHICLE_BUTTON_W = 86;

    private void renderVehicleDeployPage(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int contentTop) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;

        final int left = LEFT;
        final int right = this.width - RIGHT;
        final int listTop = contentTop + 24;
        final int listBottom = this.height - 16;

        // Back button
        if (vehicleBackButton == null)
            vehicleBackButton = new GDButton(right - 48, contentTop + 2, 40, 16, Component.literal("返回"), btn -> vehicleDeployPageVisible = false);
        vehicleBackButton.setX(right - 48);
        vehicleBackButton.setY(contentTop + 2);
        vehicleBackButton.setWidth(40);
        vehicleBackButton.setHeight(16);
        vehicleBackButton.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, "载具部署", left, contentTop + 4, 0xFFEDEDED, false);
        guiGraphics.drawString(this.font,
                ClientRoomLockState.shouldReleaseMouseForUi() ? "已就绪，可部署" : "未就绪，暂时无法部署",
                right - this.font.width("未就绪，暂时无法部署"), contentTop + 4, 0xFFB8C6D6, false);

        List<ClientLoadoutUiState.VehicleEntryState> entries = sortedVehicleEntries();
        int viewHeight = Math.max(1, listBottom - listTop);
        int contentHeight = entries.size() * (VEHICLE_ROW_HEIGHT + VEHICLE_ROW_GAP);
        vehicleDeployScroll.update(mouseY, viewHeight, contentHeight);

        if (entries.isEmpty()) {
            guiGraphics.drawString(this.font, "暂无可部署的载具", left, listTop + 8, 0xFFB8C6D6, false);
            return;
        }

        pruneVehicleButtons(entries);
        syncVehicleButtons(entries, contentTop);
        guiGraphics.enableScissor(left, listTop, right, listBottom);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = listTop + i * (VEHICLE_ROW_HEIGHT + VEHICLE_ROW_GAP) - (int) Math.round(vehicleDeployScroll.scrollY());
            if (rowY + VEHICLE_ROW_HEIGHT <= listTop || rowY >= listBottom) continue;
            ClientLoadoutUiState.VehicleEntryState entry = entries.get(i);
            boolean selectedWaiting = isVehicleWaitingDeploy(entry);
            boolean selected = entry.selected();
            int bgColor = selectedWaiting ? 0x44304A66 : selected ? 0x3A22374E : 0x33151A20;
            guiGraphics.fill(left, rowY, right, rowY + VEHICLE_ROW_HEIGHT, bgColor);
            int accentColor = resolveVehicleStateColor(entry);
            guiGraphics.fill(left, rowY, left + 4, rowY + VEHICLE_ROW_HEIGHT, accentColor);
            if (selectedWaiting || selected) {
                int outlineColor = selectedWaiting ? 0xCC8FC4FF : 0x88E6EEF8;
                guiGraphics.fill(left, rowY, right, rowY + 1, outlineColor);
                guiGraphics.fill(left, rowY + VEHICLE_ROW_HEIGHT - 1, right, rowY + VEHICLE_ROW_HEIGHT, outlineColor);
                guiGraphics.fill(left, rowY, left + 1, rowY + VEHICLE_ROW_HEIGHT, outlineColor);
                guiGraphics.fill(right - 1, rowY, right, rowY + VEHICLE_ROW_HEIGHT, outlineColor);
            }
            String displayName = entry.displayName().isBlank() ? entry.entryId() : entry.displayName();
            guiGraphics.drawString(this.font, displayName, left + 10, rowY + 5, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, resolveVehicleStatusText(entry), left + 10, rowY + 18, accentColor, false);
            guiGraphics.drawString(this.font, entry.detailText(), left + 10, rowY + 31, 0xFF92A0AF, false);
            renderVehicleHealthLine(guiGraphics, entry, right, left, rowY);
            GDButton btn = vehicleDeployButtons.get(entry.entryId());
            if (btn != null) btn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.disableScissor();
        vehicleDeployScroll.renderScrollbar(guiGraphics, left, listTop, right, listBottom, contentHeight);
    }

    private void renderVehicleHealthLine(GuiGraphics guiGraphics, ClientLoadoutUiState.VehicleEntryState entry, int right, int left, int rowY) {
        if (entry.healthPercent() >= 0) {
            String healthText = entry.healthCurrent() > 0 && entry.healthMax() > 0
                ? "耐久 " + entry.healthCurrent() + "/" + entry.healthMax() + "  (" + entry.healthPercent() + "%)"
                : "耐久 " + entry.healthPercent() + "%";
            guiGraphics.drawString(this.font, healthText, right - VEHICLE_BUTTON_W - 132, rowY + 8, entry.lowHealth() ? 0xFFFFD85A : 0xFF9FD3A9, false);
        }
        if (entry.seatCount() > 0)
            guiGraphics.drawString(this.font, "席位 " + entry.occupiedSeatCount() + "/" + entry.seatCount(), right - VEHICLE_BUTTON_W - 132, rowY + 22, 0xFF8CC4FF, false);
        else if (entry.occupiedSeatCount() > 0)
            guiGraphics.drawString(this.font, "乘员 " + entry.occupiedSeatCount(), right - VEHICLE_BUTTON_W - 132, rowY + 22, 0xFF8CC4FF, false);
        else if (resolveVehicleDeployRemainSeconds(entry) > 0)
            guiGraphics.drawString(this.font, "倒计时 " + resolveVehicleDeployRemainSeconds(entry) + "秒", right - VEHICLE_BUTTON_W - 132, rowY + 22, 0xFFFFD85A, false);
    }

    private void tickVehicleDeployPage() {
        if (awaitingSuccessfulDeployClose && ClientDeathFlowState.hasActiveCameraOverride()
                && !ClientRoomLockState.shouldReleaseMouseForUi()) {
            awaitingSuccessfulDeployClose = false;
            return;
        }
        requestSnapshotIfNeeded();
    }

    private void syncVehicleButtons(List<ClientLoadoutUiState.VehicleEntryState> entries, int contentTop) {
        final int right = this.width - RIGHT;
        int buttonX = right - VEHICLE_BUTTON_W - 8;
        final int listTop = contentTop + 4;
        for (int i = 0; i < entries.size(); i++) {
            ClientLoadoutUiState.VehicleEntryState entry = entries.get(i);
            int rowY = listTop + i * (VEHICLE_ROW_HEIGHT + VEHICLE_ROW_GAP) - (int) Math.round(vehicleDeployScroll.scrollY());
            GDButton btn = vehicleDeployButtons.computeIfAbsent(entry.entryId(),
                    id -> new GDButton(buttonX, rowY + 13, VEHICLE_BUTTON_W, 20, Component.literal(entry.actionText()), b -> deployVehicle(id)));
            btn.setX(buttonX);
            btn.setY(rowY + 13);
            btn.setWidth(VEHICLE_BUTTON_W);
            btn.setHeight(20);
            btn.setMessage(Component.literal(isVehicleWaitingDeploy(entry) ? "等待部署" : entry.actionText()));
            boolean active = ClientRoomLockState.shouldReleaseMouseForUi() && entry.actionable() && !isVehicleWaitingDeploy(entry);
            btn.active = active;
            btn.setTextColor(active ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
        }
    }

    private void pruneVehicleButtons(List<ClientLoadoutUiState.VehicleEntryState> entries) {
        Set<String> activeIds = new HashSet<>();
        for (ClientLoadoutUiState.VehicleEntryState e : entries) activeIds.add(e.entryId());
        vehicleDeployButtons.entrySet().removeIf(e -> !activeIds.contains(e.getKey()));
    }

    private void deployVehicle(String entryId) {
        ClientLoadoutUiState.Snapshot snap = ClientLoadoutUiState.snapshot();
        if (snap == null || minecraft == null || minecraft.player == null) return;
        ClientLoadoutUiState.VehicleEntryState entry = snap.vehicleEntries().stream()
                .filter(e -> entryId.equalsIgnoreCase(e.entryId())).findFirst().orElse(null);
        if (entry == null || !ClientRoomLockState.shouldReleaseMouseForUi() || !entry.actionable()) return;
        awaitingSuccessfulDeployClose = true;
        NetworkManager.getChannel().sendToServer(RoomDeploySelectPacket.confirmVehicle(entry.entryId()));
        if (entry.immediateDeploy()) { awaitingSuccessfulDeployClose = false; onClose(); }
        else requestSnapshotIfNeeded();
    }

    private boolean mouseClickedVehicleDeployPage(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (vehicleBackButton != null && vehicleBackButton.mouseClicked(mouseX, mouseY, button)) return true;
            for (GDButton b : vehicleDeployButtons.values()) if (b.mouseClicked(mouseX, mouseY, button)) return true;
            final int left = LEFT, right = this.width - RIGHT;
            final int listTop = getContentTop() + 4, listBottom = this.height - 16;
            if (mouseX >= left && mouseX <= right && mouseY >= listTop && mouseY <= listBottom) {
                vehicleDeployScroll.startDragging(mouseX, mouseY, left, listTop, right, listBottom,
                        sortedVehicleEntries().size() * (VEHICLE_ROW_HEIGHT + VEHICLE_ROW_GAP));
                return true;
            }
        }
        return false;
    }

    private boolean mouseScrolledVehicleDeployPage(double mouseX, double mouseY, double delta) {
        final int left = LEFT, right = this.width - RIGHT;
        final int listTop = getContentTop() + 4, listBottom = this.height - 16;
        if (mouseX >= left && mouseX <= right && mouseY >= listTop && mouseY <= listBottom)
            return vehicleDeployScroll.mouseScrolled(delta, listBottom - listTop, sortedVehicleEntries().size() * (VEHICLE_ROW_HEIGHT + VEHICLE_ROW_GAP));
        return false;
    }

    private List<ClientLoadoutUiState.VehicleEntryState> sortedVehicleEntries() {
        ClientLoadoutUiState.Snapshot snap = ClientLoadoutUiState.snapshot();
        if (snap == null) return List.of();
        return snap.vehicleEntries().stream()
                .sorted(Comparator.comparing(ClientLoadoutUiState.VehicleEntryState::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean isVehicleWaitingDeploy(ClientLoadoutUiState.VehicleEntryState e) {
        return e != null && e.selected() && resolveVehicleDeployRemainSeconds(e) > 0;
    }

    private String resolveVehicleStatusText(ClientLoadoutUiState.VehicleEntryState e) {
        if (e == null) return "";
        int remain = resolveVehicleDeployRemainSeconds(e);
        if (e.selected() && remain > 0) return "已选定部署，等待倒计时结束（约 " + remain + " 秒）";
        return e.statusText();
    }

    private int resolveVehicleDeployRemainSeconds(ClientLoadoutUiState.VehicleEntryState e) {
        if (e == null || e.readyRemainSeconds() <= 0) return 0;
        long ageMs = ClientLoadoutUiState.snapshotAgeMs();
        if (ageMs == Long.MAX_VALUE) return e.readyRemainSeconds();
        return Math.max(0, e.readyRemainSeconds() - (int)(ageMs / 1000L));
    }

    private int resolveVehicleStateColor(ClientLoadoutUiState.VehicleEntryState e) {
        if (e == null) return 0xFFFFFFFF;
        if (isVehicleWaitingDeploy(e)) return 0xFF9AA3AD;
        if (e.lowHealth()) return 0xFFFFD85A;
        return switch (safe(e.stateToken()).toUpperCase()) {
            case "EMPTY" -> 0xFFD0D0D0;
            case "SQUAD" -> 0xFF6DE08B;
            case "TEAM" -> 0xFF63A5FF;
            case "FULL", "ENEMY" -> 0xFFFF6B6B;
            case "WAIT", "WAITING" -> 0xFF9AA3AD;
            default -> 0xFFFFFFFF;
        };
    }

    // ========== Choice dialog layout ==========

    private int getChoiceDialogLeft() {
        return getChoiceDialogBaseLeft() + resolveChoiceDialogHorizontalOffset();
    }

    private int getChoiceDialogTop() {
        return (this.height - CHOICE_DIALOG_HEIGHT) / 2 + 20;
    }

    private int getChoiceDialogBaseLeft() {
        return (this.width - CHOICE_DIALOG_WIDTH) / 2;
    }

    private int getChoiceDialogOuterLeft() {
        return getChoiceDialogLeft() - GuiConstants.DEFAULT_PADDING;
    }

    private int getChoiceDialogOuterRight() {
        return getChoiceDialogLeft() + CHOICE_DIALOG_WIDTH + GuiConstants.DEFAULT_PADDING;
    }

    private int getChoiceDialogOuterTop() {
        int fh = this.font.lineHeight;
        int ty = getChoiceDialogTop() - fh - GuiConstants.DEFAULT_PADDING + 6;
        return ty - GuiConstants.DEFAULT_PADDING;
    }

    private int getChoiceDialogOuterBottom() {
        return getChoiceDialogTop() + CHOICE_DIALOG_HEIGHT + GuiConstants.DEFAULT_PADDING;
    }

    private int getChoiceDialogOuterHeight() {
        return getChoiceDialogOuterBottom() - getChoiceDialogOuterTop();
    }

    private int resolveChoiceDialogHorizontalOffset() {
        if (choiceDialog == null || !choiceDialog.isVisible() || !weaponPropertyPanelVisible || !shouldShowWeaponPropertyOverlay()) return 0;
        ItemStack dw = resolveDisplayedWeaponStack();
        TaczGunPropertyPanelCompat pp = TaczGunPropertyPanelCompat.getInstance();
        if (dw.isEmpty() || !pp.canRender(dw)) return 0;
        int tw = getChoiceDialogOuterWidth() + WEAPON_PROPERTY_PANEL_GAP + pp.getPanelWidth();
        int dlo = (this.width - tw) / 2;
        return dlo - (getChoiceDialogBaseLeft() - GuiConstants.DEFAULT_PADDING);
    }

    private int getChoiceDialogOuterWidth() {
        return CHOICE_DIALOG_WIDTH + GuiConstants.DEFAULT_PADDING * 2;
    }

    private void syncChoiceDialogHorizontalOffset() {
        if (choiceDialog != null) choiceDialog.setHorizontalOffset(resolveChoiceDialogHorizontalOffset());
    }

    // ========== HomeTab inner class ==========

    private static class HomeTab implements ConfigScreenHeader.TabContent {
        private final Component title = Component.literal("主页");

        @Override public Component getTitle() { return title; }
        @Override public void onTabOpen() {}
        @Override public void tick() {}
        @Override public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int contentTop) {}
        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) { return false; }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
        @Override public boolean charTyped(char codePoint, int modifiers) { return false; }
    }
}
