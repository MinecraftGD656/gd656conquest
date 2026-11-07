/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.loadout.screen;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;
import org.mods.gd656conquest.client.loadout.preview.LoadoutItemPreview;
import org.mods.gd656conquest.client.loadout.preview.LoadoutWeaponPreview;
import org.mods.gd656conquest.client.loadout.preview.WeaponCustomizationView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.compat.tacz.TaczClientAttachmentCompat;
import org.mods.gd656conquest.client.compat.tacz.TaczGunPropertyPanelCompat;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.client.gui.tabs.AdminRoomEditorTabContent;
import org.mods.gd656conquest.client.gui.tabs.AttachmentEditorTabContent;
import org.mods.gd656conquest.client.gui.tabs.ItemEditorTabContent;
import org.mods.gd656conquest.client.gui.tabs.ParkourTabContent;
import org.mods.gd656conquest.client.gui.tabs.RoomSelectionTabContent;
import org.mods.gd656conquest.client.gui.tabs.RoomStatisticTabContent;
import org.mods.gd656conquest.client.gui.tabs.VehicleAdminEditorTabContent;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.title.PlayerTitleService;

public class LoadoutConfigScreen
extends Screen {
    private static final ResourceLocation CLASS_ICON_ASSAULT = ResourceLocation.parse("gd656conquest:textures/gui/class_assault.png");
    private static final ResourceLocation CLASS_ICON_SUPPORT = ResourceLocation.parse("gd656conquest:textures/gui/class_support.png");
    private static final ResourceLocation CLASS_ICON_ENGINEER = ResourceLocation.parse("gd656conquest:textures/gui/class_engineer.png");
    private static final ResourceLocation CLASS_ICON_RECON = ResourceLocation.parse("gd656conquest:textures/gui/class_recon.png");
    private static final int LEFT = 8;
    private static final int TOP_GAP = 6;
    private static final int LEFT_WIDTH_MAX = 170;
    private static final int LEFT_WIDTH_MIN = 128;
    private static final int PANEL_GAP = 12;
    private static final int BTN_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int RIGHT = 8;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private static final int INFO_H = 60;
    private static final int CARD_H = 132;
    private static final int CARD_GAP = 12;
    private static final int CARD_BUTTON_GAP = 8;
    private static final int CARD_BUTTON_H = 18;
    private static final int CHOICE_DIALOG_WIDTH = 227;
    private static final int CHOICE_DIALOG_HEIGHT = 192;
    private static final int WEAPON_PROPERTY_GAP = 12;
    private static final int WEAPON_PROPERTY_PANEL_GAP = 5;
    private static final int WEAPON_PROPERTY_BUTTON_W = 100;
    private static final int WEAPON_PROPERTY_BUTTON_H = 18;
    private static final List<String> ATTACHMENT_TYPE_ORDER = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");
    private ClientLoadoutUiState.Snapshot homeModelSnapshot;
    private final Map<String, ItemStack> homeModelCache = new HashMap<>();
    private final LoadoutItemPreview weaponPreview = new LoadoutItemPreview();
    private WeaponCustomizationView weaponEditor;
    private boolean embeddedEditorDrawn;
    private CustomLoadoutSlot homeDetailSlot = CustomLoadoutSlot.PRIMARY_WEAPON;
    private final Map<String, int[]> homeClassBounds = new LinkedHashMap<>();
    private static final CustomLoadoutSlot[] HOME_SLOTS = {CustomLoadoutSlot.PRIMARY_WEAPON, CustomLoadoutSlot.SECONDARY_WEAPON, CustomLoadoutSlot.MELEE, CustomLoadoutSlot.GADGET_1, CustomLoadoutSlot.GADGET_2, CustomLoadoutSlot.THROWABLE};
    private final Screen parent;
    private final int targetTabIndex;
    private ConfigScreenHeader header;
    private ChoiceListDialog choiceDialog;
    private final List<GDButton> actionButtons = new ArrayList<GDButton>();
    private final Map<String, GDButton> attachmentButtons = new LinkedHashMap<String, GDButton>();
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
    private RoomSelectionTabContent roomSelectionTab;
    private RoomStatisticTabContent roomStatisticTab;
    private VehicleAdminEditorTabContent vehicleEditorTab;
    private AdminRoomEditorTabContent roomEditorTab;
    private AttachmentEditorTabContent attachmentEditorTab;
    private ItemEditorTabContent itemEditorTab;
    private ParkourTabContent parkourTab;
    private boolean vehicleDeployPageVisible;
    private final Map<String, GDButton> vehicleDeployButtons = new LinkedHashMap<String, GDButton>();
    private final GDScrollState vehicleDeployScroll = new GDScrollState();
    private GDButton vehicleBackButton;
    private boolean awaitingSuccessfulDeployClose;
    private static final int VEHICLE_ROW_HEIGHT = 46;
    private static final int VEHICLE_ROW_GAP = 6;
    private static final int VEHICLE_BUTTON_W = 86;
    private static final int VEHICLE_LIST_TOP_OFFSET = 24;

    public LoadoutConfigScreen(Screen parent) {
        this(parent, 0);
    }

    public LoadoutConfigScreen(Screen parent, int targetTabIndex) {
        super(Component.translatable("gd656conquest.client.gui.loadout.title"));
        this.parent = parent;
        this.targetTabIndex = targetTabIndex;
    }

    @Override
    protected void init() {
        super.init();
        this.choiceDialog = new ChoiceListDialog(this.minecraft);
        this.weaponPropertyButton = new GDButton(0, 0, 100, 18, Component.literal("\u6b66\u5668\u6570\u636e\uff1a\u5173"), btn -> this.toggleWeaponPropertyPanel());
        this.weaponPropertyButton.setAutoScaleTextToFit(true);
        this.weaponPropertyButton.setMinAutoScale(0.58f);
        this.refreshWeaponPropertyButtonLabel();
        this.updateOutOfGameTimer();
        this.rightContentScroll.reset();
        this.header = new ConfigScreenHeader();
        this.header.addTab(new HomeTab());
        this.roomSelectionTab = new RoomSelectionTabContent(this.minecraft);
        this.header.addTab(this.roomSelectionTab);
        this.roomStatisticTab = new RoomStatisticTabContent(this.minecraft);
        this.header.addTab(this.roomStatisticTab);
        if (this.isAdmin()) {
            this.initAdminTabs();
            this.header.addTab(this.vehicleEditorTab);
            this.header.addTab(this.roomEditorTab);
            this.header.addTab(this.attachmentEditorTab);
            this.header.addTab(this.itemEditorTab);
        }
        this.header.addTab(this.getOrCreateParkourTab());
        if (this.targetTabIndex > 0 && this.targetTabIndex < this.header.getTabCount()) {
            this.header.setSelectedTab(this.targetTabIndex);
        }
        this.rebuildButtons();
        if (!this.isOutOfGame()) {
            this.requestSnapshotIfNeeded();
        }
    }

    private boolean isAdmin() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        return snapshot != null && snapshot.admin() && this.minecraft.player.hasPermissions(2);
    }

    private void initAdminTabs() {
        this.vehicleEditorTab = new VehicleAdminEditorTabContent(this.minecraft);
        this.roomEditorTab = new AdminRoomEditorTabContent(this.minecraft);
        this.attachmentEditorTab = new AttachmentEditorTabContent(this.minecraft);
        this.itemEditorTab = new ItemEditorTabContent(this.minecraft);
    }

    private ParkourTabContent getOrCreateParkourTab() {
        if (this.parkourTab == null) {
            this.parkourTab = new ParkourTabContent(this.minecraft);
        }
        return this.parkourTab;
    }

    public Screen parentScreen() {
        return this.parent;
    }

    private int getContentTop() {
        return this.header != null ? this.header.getContentTop() : 34;
    }

    private int getTop() {
        return this.getContentTop() + 6;
    }

    private void rebuildButtons() {
        this.actionButtons.clear();
        int leftWidth = this.getLeftColumnWidth();
        int contentTop = this.getContentTop();
        int btnY = this.getTop();
        this.actionButtons.add(new GDButton(8, btnY, leftWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.class"), btn -> this.openClassDialog()));
        this.actionButtons.add(new GDButton(8, btnY += 24, leftWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.primary"), btn -> this.openSlotDialog(CustomLoadoutSlot.PRIMARY_WEAPON)));
        this.actionButtons.add(new GDButton(8, btnY += 24, leftWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.secondary"), btn -> this.openSlotDialog(CustomLoadoutSlot.SECONDARY_WEAPON)));
        this.actionButtons.add(new GDButton(8, btnY += 24, leftWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.melee"), btn -> this.openSlotDialog(CustomLoadoutSlot.MELEE)));
        this.actionButtons.add(new GDButton(8, btnY += 24, leftWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.gadget1"), btn -> this.openSlotDialog(CustomLoadoutSlot.GADGET_1)));
        this.actionButtons.add(new GDButton(8, btnY += 24, leftWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.gadget2"), btn -> this.openSlotDialog(CustomLoadoutSlot.GADGET_2)));
        this.actionButtons.add(new GDButton(8, btnY += 24, leftWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.throwable"), btn -> this.openSlotDialog(CustomLoadoutSlot.THROWABLE)));
        int dualBtnY = this.getRightContentViewportBottom() - 20;
        int halfWidth = (leftWidth - 4) / 2;
        this.vehicleToTabButton = new GDButton(8, dualBtnY, halfWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.vehicle.button"), btn -> {
            this.vehicleDeployPageVisible = true;
            this.vehicleDeployScroll.reset();
        });
        this.vehicleToTabButton.setAutoScaleTextToFit(true);
        this.closeScreenButton = new GDButton(8 + halfWidth + 4, dualBtnY, halfWidth, 20, Component.translatable("gd656conquest.client.gui.loadout.close"), btn -> this.onClose());
        this.closeScreenButton.setAutoScaleTextToFit(true);
        this.attachmentButtons.clear();
        this.createAttachmentButtons(CustomLoadoutSlot.PRIMARY_WEAPON, this.getPrimaryCardTop());
        this.createAttachmentButtons(CustomLoadoutSlot.SECONDARY_WEAPON, this.getSecondaryCardTop());
    }

    @Override
    public void tick() {
        int parkourIdx;
        if (!this.isWeaponPreviewActive()) this.weaponPreview.clearInteraction();
        int tabIdx;
        super.tick();
        this.updateOutOfGameTimer();
        if (this.isOutOfGame() && this.outOfGameCloseAtMs > 0L && System.currentTimeMillis() >= this.outOfGameCloseAtMs) {
            this.onClose();
            return;
        }
        this.requestSnapshotIfNeeded();
        int n = tabIdx = this.header != null ? this.header.getSelectedTabIndex() : 0;
        if (tabIdx == 1 && this.roomSelectionTab != null) {
            this.roomSelectionTab.tick();
        } else if (tabIdx == 2 && this.roomStatisticTab != null) {
            this.roomStatisticTab.tick();
        }
        if (this.isAdmin()) {
            if (tabIdx == 3 && this.vehicleEditorTab != null) {
                this.vehicleEditorTab.tick();
            } else if (tabIdx == 4 && this.roomEditorTab != null) {
                this.roomEditorTab.tick();
            } else if (tabIdx == 5 && this.attachmentEditorTab != null) {
                this.attachmentEditorTab.tick();
            } else if (tabIdx == 6 && this.itemEditorTab != null) {
                this.itemEditorTab.tick();
            }
        }
        int n2 = parkourIdx = this.isAdmin() ? 7 : 3;
        if (tabIdx == parkourIdx && this.parkourTab != null) {
            this.parkourTab.tick();
        }
        if (this.vehicleDeployPageVisible) {
            this.tickVehicleDeployPage();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int parkourIdx;
        ConfigScreenHeader.TabContent content;
        int activeTab;
        this.embeddedEditorDrawn = false;
        BattlefieldUiTheme.background(guiGraphics, this.width, this.height);
        if (this.isOutOfGame()) {
            int remain = Math.max(0, (int)Math.ceil((double)(this.outOfGameCloseAtMs - System.currentTimeMillis()) / 1000.0));
            BattlefieldUiTheme.centered(guiGraphics, "\u8bf7\u5728\u6e38\u620f\u5185\u6253\u5f00\u914d\u7f6e\u754c\u9762", this.width / 2, this.height / 2 - 10, -32640);
            BattlefieldUiTheme.centered(guiGraphics, "\u5c06\u5728 " + remain + " \u79d2\u540e\u81ea\u52a8\u5173\u95ed", this.width / 2, this.height / 2 + 6, -4667690);
            return;
        }
        int contentTop = this.getContentTop();
        int n = activeTab = this.header != null ? this.header.getSelectedTabIndex() : 0;
        if (this.header != null) {
            this.header.render(guiGraphics, this.width, mouseX, mouseY, partialTick);
        }
        if (this.vehicleDeployPageVisible) {
            this.renderVehicleDeployPage(guiGraphics, mouseX, mouseY, partialTick, contentTop);
            return;
        }
        if (activeTab == 0) {
            this.renderHomeTab(guiGraphics, mouseX, mouseY, partialTick);
        } else if (activeTab == 1 && this.roomSelectionTab != null) {
            this.roomSelectionTab.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, contentTop);
        } else if (activeTab == 2 && this.roomStatisticTab != null) {
            this.roomStatisticTab.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, contentTop);
        } else if (this.isAdmin() && (content = this.getAdminTabContent(activeTab)) != null) {
            content.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, contentTop);
        }
        int n2 = parkourIdx = this.isAdmin() ? 7 : 3;
        if (activeTab == parkourIdx && this.parkourTab != null) {
            this.parkourTab.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, contentTop);
        }
        this.syncChoiceDialogHorizontalOffset();
        if (this.choiceDialog != null && this.choiceDialog.isVisible()) {
            this.choiceDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderHomeTab(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var p = BattlefieldUiTheme.palette();
        var theme = BattlefieldUiTheme.current();
        var snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot != this.homeModelSnapshot) { this.homeModelCache.clear(); this.homeModelSnapshot = snapshot; }
        int mx = this.getBaseUiMouseX(mouseX), my = this.getBaseUiMouseY(mouseY);
        int top = this.getContentTop() + 8;
        int footer = this.height - 23;
        int tileH = this.getHomeSlotHeight();
        int tileY = this.getHomeSlotTop();
        int detailX = this.width - 4;
        this.homeClassBounds.clear();
        switch (theme) {
            case BF6 -> {
                int identityW = Math.max(74, this.width / 5);
                this.homeTitle(g, this.resolveCurrentClassName(snapshot).toUpperCase(), 16, top + 8, identityW, 2.15f);
                this.drawClippedComponent(g, this.resolveCurrentTitleComponent(snapshot), 17, top + 34, identityW - 2);
                this.homeText(g, this.minecraft.player.getName().getString(), 17, top + 49, identityW - 2, p.text());
                this.homeText(g, "等级  " + (snapshot == null ? "—" : snapshot.playerLevel()), 17, top + 64, identityW - 2, p.muted());
                this.placeHomeButton(this.actionButtons.get(0), 16, top + 83, identityW, 22);
                this.actionButtons.get(0).render(g, mx, my, partialTick);
                this.renderHomeWeapon(g, snapshot, identityW + 26, top + 2, detailX - 12, tileY - 4, mx, my, partialTick);
                int stripX = Math.max(16, this.width * 8 / 100);
                this.layoutHomeSlots(g, snapshot, stripX, tileY, this.width - stripX - 16, tileH, false, mx, my);
            }
            case BF2042 -> {
                int classW = Math.max(86, this.width * 22 / 100);
                g.fill(0, top - 5, classW, footer, p.header());
                this.homeTitle(g, "COLLECTION", 12, top + 6, classW - 24, 1.25f);
                this.homeText(g, "LOADOUTS  /  " + this.resolveCurrentClassName(snapshot), classW + 14, top + 5, detailX - classW - 25, p.accent());
                g.fill(classW + 14, top + 21, detailX - 12, top + 22, p.accent());
                int classCount = snapshot == null ? 4 : Math.max(1, snapshot.classes().size());
                int classRowH = Math.min(26, Math.max(12, (tileY - top - 69) / classCount));
                this.renderHomeClasses(g, snapshot, 0, top + 37, classW, classRowH, false, mx, my);
                this.placeHomeButton(this.actionButtons.get(0), 10, tileY - 28, classW - 20, 21);
                this.actionButtons.get(0).render(g, mx, my, partialTick);
                this.renderHomeWeapon(g, snapshot, classW + 12, top + 32, detailX - 12, tileY - 4, mx, my, partialTick);
                this.layoutHomeSlots(g, snapshot, classW + 12, tileY, this.width - classW - 24, tileH, false, mx, my);
            }
            case BF1 -> {
                String title = "CUSTOMIZE " + this.resolveCurrentClassName(snapshot).toUpperCase();
                float titleScale = this.homeHeadingScale(title, this.width - 32, 1.6f);
                BattlefieldUiTheme.drawHeading(g, title, this.width / 2f, top + 1, titleScale, p.text(), true);
                g.fill(15, top + 19, this.width - 15, top + 20, p.line());
                int slotW = Math.max(68, this.width * 15 / 100);
                int slotsTop = top + 29;
                int slotH = Math.max(16, Math.min(44, (footer - slotsTop - 40) / 6));
                this.layoutHomeSlots(g, snapshot, 16, slotsTop, slotW, slotH, true, mx, my);
                this.renderHomeWeapon(g, snapshot, slotW + 28, top + 27, detailX - 12, footer - 25, mx, my, partialTick);
                this.renderHomeClasses(g, snapshot, slotW + 28, footer - 18, detailX - slotW - 44, 20, true, mx, my);
                this.placeHomeButton(this.actionButtons.get(0), 16, footer - 21, slotW, 20);
                this.actionButtons.get(0).render(g, mx, my, partialTick);
            }
        }
        for (GDButton button : this.attachmentButtons.values()) button.active = false;
        g.fill(0, footer, this.width, this.height, p.header());
        g.fill(0, footer, this.width, footer + 1, p.line());
        this.placeHomeButton(this.closeScreenButton, 10, footer + 3, 58, 17);
        this.closeScreenButton.render(g, mx, my, partialTick);
        this.placeHomeButton(this.vehicleToTabButton, this.width - 104, footer + 3, 94, 17);
        this.vehicleToTabButton.active = true;
        this.vehicleToTabButton.render(g, mx, my, partialTick);
        BattlefieldUiTheme.drawText(g, "F6  " + BattlefieldUiTheme.label(), 80, footer + 7, p.muted());
        if (snapshot == null) BattlefieldUiTheme.centered(g, "正在同步服务器配置…", this.width / 2, footer - 12, p.muted());
        this.renderWeaponPropertyPanel(g, mouseX, mouseY, partialTick);
    }

    private float homeHeadingScale(String title, int width, float scale) {
        float naturalWidth = this.font.width(BattlefieldUiTheme.heading(title)) * (9f / 16f);
        return naturalWidth <= 0 ? scale : Math.min(scale, Math.max(1, width) / naturalWidth);
    }

    private void homeTitle(GuiGraphics g, String title, int x, int y, int width, float scale) {
        BattlefieldUiTheme.drawHeading(g, title, x, y, this.homeHeadingScale(title, width, scale), BattlefieldUiTheme.palette().text(), false);
    }

    private void homeText(GuiGraphics g, String text, int x, int y, int width, int color) {
        BattlefieldUiTheme.drawText(g, this.clampToWidth(text == null || text.isBlank() ? "—" : text, Math.max(1, width)), x, y, color);
    }

    private void placeHomeButton(GDButton b, int x, int y, int w, int h) {
        b.setX(x); b.setY(y); b.setWidth(w); b.setHeight(h); b.setAutoScaleTextToFit(true);
    }

    private void renderHomeWeapon(GuiGraphics g, ClientLoadoutUiState.Snapshot snapshot,
            int left, int top, int right, int bottom, int mx, int my, float partialTick) {
        this.ensureWeaponEditor();
        float scale = Math.max(0.25f, Math.min(this.width / 960f, this.height / 540f));
        this.weaponEditor.renderEmbedded(g, mx, my, left, top, Math.max(1,right-left), Math.max(1,bottom-top), scale, partialTick,
            this.getAttachmentPanel(this.homeDetailSlot), this.homeSlotStack(snapshot,this.homeDetailSlot),
            this.homeSlotName(snapshot,this.homeDetailSlot), this.resolveCurrentClassName(snapshot),
            this.homeDetailSlot == CustomLoadoutSlot.PRIMARY_WEAPON ? "主武器" : "副武器", !this.isBlockingOverlayVisible());
        this.embeddedEditorDrawn = true;
    }

    private void renderHomeItem(GuiGraphics g, ItemStack stack, int cx, int cy, int size) {
        if (stack.isEmpty()) { BattlefieldUiTheme.centered(g, "—", cx, cy, BattlefieldUiTheme.palette().muted()); return; }
        g.pose().pushPose(); g.pose().translate(cx - size / 2f, cy - size / 2f, 0); g.pose().scale(size / 16f, size / 16f, 1);
        g.renderItem(stack, 0, 0); g.pose().popPose();
    }

    private ClientLoadoutUiState.EntryState homeEntry(ClientLoadoutUiState.Snapshot snapshot, CustomLoadoutSlot slot) {
        if (snapshot == null) return null;
        var cls = snapshot.classes().get(snapshot.currentClassKey());
        var state = cls == null ? null : cls.slots().get(slot.getIndex());
        return state == null ? null : state.entries().get(state.selectedId());
    }

    private ItemStack homeSlotStack(ClientLoadoutUiState.Snapshot snapshot, CustomLoadoutSlot slot) {
        var panel = this.getAttachmentPanel(slot);
        if (panel != null && panel.available() && panel.weaponItemSnbt() != null && !panel.weaponItemSnbt().isBlank()) return this.configuredWeaponStack(panel);
        var entry = this.homeEntry(snapshot, slot);
        return entry == null ? ItemStack.EMPTY : this.homeModelCache.computeIfAbsent(entry.itemSnbt(), this::parseStack);
    }

    private String homeSlotName(ClientLoadoutUiState.Snapshot snapshot, CustomLoadoutSlot slot) {
        var entry = this.homeEntry(snapshot, slot);
        return entry == null ? "—" : entry.displayName().isBlank() ? entry.id() : entry.displayName();
    }

    private ItemStack configuredWeaponStack(ClientLoadoutUiState.WeaponAttachmentPanelState panel) {
        String key="configured:"+panel.slotIndex()+":"+panel.loadoutEntryId();
        return this.homeModelCache.computeIfAbsent(key,ignored -> LoadoutWeaponPreview.compose(this.parseStack(panel.weaponItemSnbt()),panel.attachmentTypes()));
    }

    private void layoutHomeSlots(GuiGraphics g, ClientLoadoutUiState.Snapshot snapshot, int x, int y, int width, int height, boolean vertical, int mx, int my) {
        var p = BattlefieldUiTheme.palette();
        int[] order = {0, 1, 3, 4, 5, 2};
        int gap = 3;
        int availableWidth = width - gap * 5;
        int cursor = x;
        for (int visual = 0; visual < order.length; visual++) {
            int index = order[visual];
            CustomLoadoutSlot slot = HOME_SLOTS[index];
            GDButton button = this.actionButtons.get(index + 1);
            int w = vertical ? width : visual == 5 ? x + width - cursor : availableWidth * (visual == 0 ? 3 : 2) / 13;
            int tx = vertical ? x : cursor, ty = vertical ? y + visual * (height + 2) : y;
            this.placeHomeButton(button, tx, ty, w, height);
            boolean hovered = mx >= tx && mx < tx + w && my >= ty && my < ty + height;
            boolean selected = slot == this.homeDetailSlot;
            int bg = hovered || selected ? p.selection() : p.panel();
            g.fill(tx, ty, tx + w, ty + height, bg);
            g.fill(tx, ty + height - 1, tx + w, ty + height, selected ? p.accent() : p.line());
            int color = hovered || selected ? p.onSelection() : p.text();
            boolean compact = vertical && height < 30;
            this.homeText(g, button.getMessage().getString(), tx + 4, ty + (compact ? (height - 9) / 2 : 4), w - (compact ? 28 : 8), color);
            int itemSize = vertical ? Math.min(27, Math.max(10, height - 17)) : Math.max(12, height - 30);
            this.renderHomeItem(g, this.homeSlotStack(snapshot, slot), vertical ? tx + w - 17 : tx + w / 2, ty + height / 2 + (vertical && !compact ? 5 : 0), itemSize);
            if (!compact) this.homeText(g, this.homeSlotName(snapshot, slot), tx + 4, ty + height - 12, w - (vertical ? 38 : 8), color);
            cursor += w + gap;
        }
    }

    private void renderHomeClasses(GuiGraphics g, ClientLoadoutUiState.Snapshot snapshot, int x, int y, int width, int rowHeight, boolean horizontal, int mx, int my) {
        if (snapshot == null || snapshot.classes().isEmpty()) return;
        var p = BattlefieldUiTheme.palette();
        int count = snapshot.classes().size(), i = 0;
        for (var cls : snapshot.classes().values()) {
            int w = horizontal ? width / count : width;
            int px = horizontal ? x + i * w : x, py = horizontal ? y : y + i * rowHeight;
            boolean selected = cls.classKey().equals(snapshot.currentClassKey());
            boolean hovered = mx >= px && mx < px + w && my >= py && my < py + rowHeight;
            BattlefieldUiTheme.tab(g, px, py, w, rowHeight, selected, hovered);
            this.homeText(g, cls.className(), px + 6, py + (rowHeight - 9) / 2, w - 12, selected && BattlefieldUiTheme.current() != org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme.BF6 ? p.onSelection() : p.text());
            this.homeClassBounds.put(cls.classKey(), new int[]{px, py, w, rowHeight}); i++;
        }
    }

    private void renderHomeAttachments(GuiGraphics g, ClientLoadoutUiState.Snapshot snapshot, int mx, int my, float partialTick) {
        var p = BattlefieldUiTheme.palette();
        int x = this.getRightPanelLeft(), width = this.getRightPanelRight() - x;
        int top = this.getRightContentViewportTop(), bottom = this.getRightContentViewportBottom();
        g.fill(x, top, x + width, bottom, p.panel());
        BattlefieldUiTheme.drawText(g, "ATTACHMENTS", x + 6, top + 5, p.muted());
        int half = (width - 12) / 2;
        for (int i = 0; i < 2; i++) {
            boolean selected = (i == 0) == (this.homeDetailSlot == CustomLoadoutSlot.PRIMARY_WEAPON);
            BattlefieldUiTheme.tab(g, x + 6 + i * half, top + 19, half, 17, selected, false);
            this.homeText(g, i == 0 ? "主武器" : "副武器", x + 10 + i * half, top + 23, half - 8, selected && BattlefieldUiTheme.current() != org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme.BF6 ? p.onSelection() : p.text());
        }
        int listTop = top + 41;
        var panel = this.getAttachmentPanel(this.homeDetailSlot);
        this.rightContentScroll.update(my, Math.max(1, bottom - listTop), this.getRightContentHeight());
        for (var entry : this.attachmentButtons.entrySet()) entry.getValue().active = false;
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(g,x, listTop, x + width, bottom);
        for (int i = 0; i < ATTACHMENT_TYPE_ORDER.size(); i++) {
            String type = ATTACHMENT_TYPE_ORDER.get(i);
            GDButton button = this.attachmentButtons.get(this.homeDetailSlot.getKey() + ":" + type);
            int y = listTop + i * 26 - (int)Math.round(this.rightContentScroll.scrollY());
            this.placeHomeButton(button, x + 6, y, width - 12, 24);
            var state = panel == null ? null : panel.attachmentTypes().get(type);
            button.active = panel != null && panel.available() && panel.taczGun() && state != null && state.available();
            if (y + 26 <= listTop || y >= bottom) continue;
            boolean hovered = button.active && mx >= x + 6 && mx < x + width - 6 && my >= y && my < y + 24;
            if (hovered) g.fill(x + 6, y, x + width - 6, y + 24, p.selection());
            g.fill(x + 6, y + 25, x + width - 6, y + 26, p.line());
            this.homeText(g, button.getMessage().getString(), x + 8, y + 2, width - 20, hovered ? p.onSelection() : button.active ? p.text() : p.muted());
            String selected = state == null || !state.available() ? "—" : !state.hasStoredSelection() ? "默认外置状态" : state.selectedAttachmentId().isBlank() ? "无配件" : this.resolveSelectedAttachmentDisplayName(state);
            this.homeText(g, selected, x + 8, y + 14, width - 20, hovered ? p.onSelection() : p.muted());
        }
        g.disableScissor();
        this.rightContentScroll.renderScrollbar(g, x, listTop, x + width, bottom, this.getRightContentHeight());
    }

    private boolean clickThemedHome(double mx, double my, int button) {
        if (button != 0) return false;
        for (var entry : this.homeClassBounds.entrySet()) {
            int[] r = entry.getValue();
            if (mx >= r[0] && mx < r[0]+r[2] && my >= r[1] && my < r[1]+r[3]) {
                NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectClass(entry.getKey())); return true;
            }
        }
        return false;
    }

    private int getLeftColumnWidth() {
        int reservedRightWidth = 360;
        int computed = this.width - 8 - 8 - 12 - reservedRightWidth;
        return Math.max(128, Math.min(170, computed));
    }

    private int getRightPanelLeft() {
        return this.width - Math.max(100, this.width * 19 / 100);
    }

    private int getRightPanelRight() {
        return this.width - 8;
    }

    private int getRightContentViewportTop() {
        return this.getContentTop() + (BattlefieldUiTheme.current() == org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme.BF1 ? 29 : 8);
    }

    private int getRightContentViewportBottom() {
        int availableBottom = BattlefieldUiTheme.current() == org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme.BF1 ? this.height - 48 : this.getHomeSlotTop() - 5;
        return Math.min(availableBottom, this.getRightContentViewportTop() + 41 + this.getRightContentHeight());
    }

    private int getHomeSlotHeight() {
        return Math.max(40, Math.min(64, this.height * 12 / 100));
    }

    private int getHomeSlotTop() {
        return this.height - 23 - this.getHomeSlotHeight() - 10;
    }

    private int getRightContentHeight() {
        return ATTACHMENT_TYPE_ORDER.size() * 26;
    }

    private int getOverviewTop() {
        return this.getRightContentViewportTop() - (int)Math.round(this.rightContentScroll.scrollY());
    }

    private int getPrimaryCardTop() {
        return this.getOverviewTop() + 60 + 12;
    }

    private int getSecondaryCardTop() {
        return this.getPrimaryCardTop() + 132 + 12;
    }

    private int getCardInnerWidth() {
        return Math.max(40, this.getRightPanelRight() - this.getRightPanelLeft() - 16);
    }

    private boolean isMouseOverRightContent(double mouseX, double mouseY) {
        return mouseX >= (double)this.getRightPanelLeft() && mouseX <= (double)this.getRightPanelRight() && mouseY >= (double)this.getRightContentViewportTop() && mouseY <= (double)this.getRightContentViewportBottom();
    }

    private void createAttachmentButtons(CustomLoadoutSlot slot, int topY) {
        int buttonWidth = Math.max(74, (this.getCardInnerWidth() - 8) / 2);
        for (int i = 0; i < ATTACHMENT_TYPE_ORDER.size(); ++i) {
            String attachmentType = ATTACHMENT_TYPE_ORDER.get(i);
            int column = i % 2;
            int row = i / 2;
            int x = this.getRightPanelLeft() + 8 + column * (buttonWidth + 8);
            int y = topY + 42 + row * 24;
            GDButton button = new GDButton(x, y, buttonWidth, 18, Component.literal(this.resolveAttachmentLabel(attachmentType)), btn -> this.openAttachmentDialog(slot, attachmentType));
            this.attachmentButtons.put(slot.getKey() + ":" + attachmentType, button);
        }
    }

    private void layoutAttachmentButtons(CustomLoadoutSlot slot, int topY) {
        int buttonWidth = Math.max(74, (this.getCardInnerWidth() - 8) / 2);
        for (int i = 0; i < ATTACHMENT_TYPE_ORDER.size(); ++i) {
            String attachmentType = ATTACHMENT_TYPE_ORDER.get(i);
            GDButton button = this.attachmentButtons.get(slot.getKey() + ":" + attachmentType);
            if (button == null) continue;
            int column = i % 2;
            int row = i / 2;
            int x = this.getRightPanelLeft() + 8 + column * (buttonWidth + 8);
            int y = topY + 42 + row * 24;
            button.setX(x);
            button.setY(y);
            button.setWidth(buttonWidth);
            button.setHeight(18);
        }
    }

    private void renderOverviewPanel(GuiGraphics guiGraphics, ClientLoadoutUiState.Snapshot snapshot) {
        int left = this.getRightPanelLeft();
        int right = this.getRightPanelRight();
        int top = this.getOverviewTop();
        int bottom = top + 60;
        guiGraphics.fill(left, top, right, bottom, 1141905439);
        guiGraphics.fill(left, top, right, top + 1, 1719585023);
        this.drawClampedString(guiGraphics, "\u5f53\u524d\u914d\u7f6e\u6982\u89c8", left + 8, top + 6, -8468225, this.getCardInnerWidth());
        String playerName = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getName().getString() : "-";
        String classText = this.resolveCurrentClassName(snapshot);
        String stateText = snapshot == null ? "\u6b63\u5728\u540c\u6b65\u670d\u52a1\u5668\u6570\u636e..." : "\u60a8\u6240\u66f4\u6539\u7684\u914d\u7f6e\u5c06\u5728\u4e0b\u6b21\u90e8\u7f72\u65f6\u6216\u5728\u9776\u573a\u7acb\u5373\u751f\u6548";
        this.drawClampedString(guiGraphics, "\u73a9\u5bb6: " + playerName + "    \u5175\u79cd: " + classText, left + 8, top + 20, -2631721, this.getCardInnerWidth());
        this.renderTitleAndLevelLine(guiGraphics, snapshot, left + 8, top + 32, this.getCardInnerWidth());
        this.drawClampedString(guiGraphics, stateText, left + 8, top + 46, snapshot == null ? -5596928 : -6641992, this.getCardInnerWidth());
    }

    private void renderAttachmentCard(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CustomLoadoutSlot slot, int topY) {
        int left = this.getRightPanelLeft();
        int right = this.getRightPanelRight();
        int bottom = topY + 132;
        guiGraphics.fill(left, topY, right, bottom, 1141905439);
        guiGraphics.fill(left, topY, right, topY + 1, 1728042890);
        String title = slot == CustomLoadoutSlot.PRIMARY_WEAPON ? "\u4e3b\u6b66\u5668\u914d\u4ef6" : "\u526f\u6b66\u5668\u914d\u4ef6";
        BattlefieldUiTheme.drawText(guiGraphics, title, left + 8, topY + 6, -6237968);
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = this.getAttachmentPanel(slot);
        if (panel == null || !panel.available()) {
            this.drawClampedString(guiGraphics, "\u5f53\u524d\u672a\u9009\u62e9\u6b66\u5668\uff0c\u8bf7\u5148\u5728\u5de6\u4fa7\u9009\u62e9 " + (slot == CustomLoadoutSlot.PRIMARY_WEAPON ? "\u4e3b\u6b66\u5668" : "\u526f\u6b66\u5668"), left + 8, topY + 20, -6641992, this.getCardInnerWidth());
            this.drawClampedString(guiGraphics, "\u4ec5 TACZ \u4e3b\u6b66\u5668 / \u526f\u6b66\u5668\u652f\u6301\u72ec\u7acb\u914d\u4ef6\u914d\u7f6e", left + 8, topY + 132 - 12, -8483944, this.getCardInnerWidth());
            this.setAttachmentButtonsState(slot, false);
            return;
        }
        this.drawClampedString(guiGraphics, this.safe(panel.weaponDisplayName()).isBlank() ? panel.loadoutEntryId() : panel.weaponDisplayName(), left + 8, topY + 20, -1184275, this.getCardInnerWidth());
        this.drawClampedString(guiGraphics, "\u6761\u76eeID: " + this.safe(panel.loadoutEntryId()), left + 8, topY + 32, -7359537, this.getCardInnerWidth());
        if (!panel.taczGun()) {
            this.drawClampedString(guiGraphics, "\u5f53\u524d\u6b66\u5668\u4e0d\u662f TACZ \u67aa\u68b0\uff0c\u65e0\u6cd5\u914d\u7f6e\u914d\u4ef6", left + 8, topY + 132 - 14, -32640, this.getCardInnerWidth());
            this.setAttachmentButtonsState(slot, false);
            this.renderAttachmentButtons(guiGraphics, mouseX, mouseY, partialTick, slot);
            return;
        }
        this.drawClampedString(guiGraphics, this.buildAttachmentSummary(panel), left + 8, topY + 132 - 14, -4667690, this.getCardInnerWidth());
        this.setAttachmentButtonsState(slot, true);
        this.renderAttachmentButtons(guiGraphics, mouseX, mouseY, partialTick, slot);
    }

    private void renderAttachmentButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CustomLoadoutSlot slot) {
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = this.getAttachmentPanel(slot);
        for (String attachmentType : ATTACHMENT_TYPE_ORDER) {
            boolean active;
            GDButton button = this.attachmentButtons.get(slot.getKey() + ":" + attachmentType);
            if (button == null) continue;
            ClientLoadoutUiState.AttachmentTypeState state = panel == null ? null : panel.attachmentTypes().get(attachmentType);
            button.active = active = panel != null && panel.taczGun() && state != null && state.available();
            button.setTextColor(active ? -1 : -5592406);
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void setAttachmentButtonsState(CustomLoadoutSlot slot, boolean active) {
        for (String attachmentType : ATTACHMENT_TYPE_ORDER) {
            GDButton button = this.attachmentButtons.get(slot.getKey() + ":" + attachmentType);
            if (button == null) continue;
            button.active = active;
            button.setTextColor(active ? -1 : -5592406);
        }
    }

    private void renderWeaponPropertyPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderWeaponPropertyOverlayContents(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderWeaponPropertyOverlayContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean canRender;
        if (!this.shouldShowWeaponPropertyOverlay() || this.weaponPropertyButton == null) {
            this.weaponPropertyPanelVisible = false;
            this.refreshWeaponPropertyButtonLabel();
            return;
        }
        ItemStack displayedWeapon = this.resolveDisplayedWeaponStack();
        TaczGunPropertyPanelCompat propertyPanel = TaczGunPropertyPanelCompat.getInstance();
        boolean bl = canRender = !displayedWeapon.isEmpty() && propertyPanel.canRender(displayedWeapon);
        if (!canRender) {
            this.weaponPropertyPanelVisible = false;
        }
        this.refreshWeaponPropertyButtonLabel();
        int buttonX = this.getChoiceDialogOuterRight() - 100;
        int buttonY = this.getChoiceDialogOuterTop() - 18 - 5;
        this.weaponPropertyButton.setX(buttonX);
        this.weaponPropertyButton.setY(buttonY);
        this.weaponPropertyButton.setWidth(100);
        this.weaponPropertyButton.setHeight(18);
        this.weaponPropertyButton.active = canRender;
        this.weaponPropertyButton.setTextColor(canRender ? -1 : -5592406);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, 510.0f);
        this.weaponPropertyButton.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.pose().popPose();
        if (!this.weaponPropertyPanelVisible || !canRender) {
            return;
        }
        int overlayX = this.getChoiceDialogOuterRight() + 5;
        int panelY = this.getChoiceDialogOuterTop();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, 510.0f);
        propertyPanel.render(guiGraphics, this.font, overlayX, panelY, this.getChoiceDialogOuterHeight(), displayedWeapon);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int parkourIdx;
        ConfigScreenHeader.TabContent content;
        this.syncChoiceDialogHorizontalOffset();
        if (button == 0 && this.weaponPropertyButton != null && this.shouldShowWeaponPropertyOverlay() && this.weaponPropertyButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.choiceDialog != null && this.choiceDialog.isVisible() && this.choiceDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.choiceDialog != null && this.choiceDialog.isVisible()) return true;
        if (button == 0 && this.isOutOfGame()) {
            return true;
        }
        if (this.header != null && this.header.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int activeTab = this.header != null ? this.header.getSelectedTabIndex() : 0;
        int contentTop = this.getContentTop();
        if (this.vehicleDeployPageVisible) {
            return this.mouseClickedVehicleDeployPage(mouseX, mouseY, button);
        }
        if (activeTab == 0) {
            return this.mouseClickedHome(mouseX, mouseY, button);
        }
        if (activeTab == 1 && this.roomSelectionTab != null) {
            return this.roomSelectionTab.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab == 2 && this.roomStatisticTab != null) {
            if (this.roomStatisticTab.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return this.roomStatisticTab.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        }
        if (this.isAdmin() && (content = this.getAdminTabContent(activeTab)) != null) {
            if (content.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return this.mouseClickedAdminContent(content, mouseX, mouseY, button, contentTop);
        }
        int n = parkourIdx = this.isAdmin() ? 7 : 3;
        if (activeTab == parkourIdx && this.parkourTab != null) {
            if (this.parkourTab.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return this.parkourTab.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean mouseClickedHome(double mouseX, double mouseY, int button) {
        if (this.embeddedEditorDrawn && this.weaponEditor.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.clickThemedHome(mouseX, mouseY, button)) return true;
        if (button == 0) {
            if (this.vehicleToTabButton != null && this.vehicleToTabButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.closeScreenButton != null && this.closeScreenButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (GDButton actionButton : this.actionButtons) {
                if (!actionButton.mouseClicked(mouseX, mouseY, button)) continue;
                return true;
            }

        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.weaponEditor != null) this.weaponEditor.mouseReleased(mouseX, mouseY, button);
        this.weaponPreview.mouseReleased(mouseX, mouseY, button);
        int parkourIdx;
        ConfigScreenHeader.TabContent content;
        int activeTab;
        if (this.header != null) {
            this.header.mouseReleased(mouseX, mouseY, button);
        }
        if (this.choiceDialog != null) {
            this.choiceDialog.mouseReleased(mouseX, mouseY, button);
        }
        int n = activeTab = this.header != null ? this.header.getSelectedTabIndex() : 0;
        if (activeTab == 1 && this.roomSelectionTab != null) {
            this.roomSelectionTab.mouseReleased(mouseX, mouseY, button);
        }
        if (activeTab == 2 && this.roomStatisticTab != null) {
            this.roomStatisticTab.mouseReleased(mouseX, mouseY, button);
        }
        this.rightContentScroll.stopDragging();
        if (this.isAdmin() && (content = this.getAdminTabContent(activeTab)) != null) {
            content.mouseReleased(mouseX, mouseY, button);
        }
        int n2 = parkourIdx = this.isAdmin() ? 7 : 3;
        if (activeTab == parkourIdx && this.parkourTab != null) {
            this.parkourTab.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.embeddedEditorDrawn && !this.isBlockingOverlayVisible() && this.weaponEditor.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (this.isWeaponPreviewActive() && this.weaponPreview.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        int activeTab;
        if (this.header != null && this.header.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        int n = activeTab = this.header != null ? this.header.getSelectedTabIndex() : 0;
        if (activeTab == 1 && this.roomSelectionTab != null && this.roomSelectionTab.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.embeddedEditorDrawn && !this.isBlockingOverlayVisible() && this.weaponEditor.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (this.isWeaponPreviewActive() && this.weaponPreview.mouseScrolled(mouseX, mouseY, delta)) return true;
        int parkourIdx;
        this.syncChoiceDialogHorizontalOffset();
        if (this.choiceDialog != null && this.choiceDialog.isVisible() && this.choiceDialog.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (this.choiceDialog != null && this.choiceDialog.isVisible()) return true;
        if (this.header != null && this.header.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        int activeTab = this.header != null ? this.header.getSelectedTabIndex() : 0;
        int contentTop = this.getContentTop();
        if (this.vehicleDeployPageVisible) {
            return this.mouseScrolledVehicleDeployPage(mouseX, mouseY, delta);
        }
        if (activeTab == 0) {
            if (this.isMouseOverRightContent(mouseX, mouseY)) {
                return this.rightContentScroll.mouseScrolled(delta, this.getRightContentViewportBottom() - this.getRightContentViewportTop() - 41, this.getRightContentHeight());
            }
        } else {
            ConfigScreenHeader.TabContent content;
            if (activeTab == 1 && this.roomSelectionTab != null) {
                return this.roomSelectionTab.mouseScrolled(mouseX, mouseY, delta);
            }
            if (activeTab == 2 && this.roomStatisticTab != null) {
                return this.roomStatisticTab.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
            }
            if (this.isAdmin() && (content = this.getAdminTabContent(activeTab)) != null) {
                return this.mouseScrolledAdminContent(content, mouseX, mouseY, delta, contentTop);
            }
        }
        int n = parkourIdx = this.isAdmin() ? 7 : 3;
        if (activeTab == parkourIdx && this.parkourTab != null) {
            return this.parkourTab.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 295) { BattlefieldUiTheme.cycle(); return true; }
        int parkourIdx;
        ConfigScreenHeader.TabContent content;
        int activeTab;
        if (this.choiceDialog != null && this.choiceDialog.isVisible() && this.choiceDialog.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.choiceDialog != null && this.choiceDialog.isVisible()) return true;
        if (this.embeddedEditorDrawn && this.weaponEditor.keyPressed(keyCode, modifiers)) return true;
        int n = activeTab = this.header != null ? this.header.getSelectedTabIndex() : 0;
        if (this.vehicleDeployPageVisible) {
            if (keyCode == 256) {
                this.vehicleDeployPageVisible = false;
            }
            return true;
        }
        if (activeTab == 1 && this.roomSelectionTab != null && this.roomSelectionTab.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.isAdmin() && (content = this.getAdminTabContent(activeTab)) != null && content.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        int n2 = parkourIdx = this.isAdmin() ? 7 : 3;
        if (activeTab == parkourIdx && this.parkourTab != null && this.parkourTab.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        int parkourIdx;
        ConfigScreenHeader.TabContent content;
        int activeTab;
        if (this.choiceDialog != null && this.choiceDialog.isVisible() && this.choiceDialog.charTyped(codePoint, modifiers)) {
            return true;
        }
        int n = activeTab = this.header != null ? this.header.getSelectedTabIndex() : 0;
        if (this.vehicleDeployPageVisible) {
            return false;
        }
        if (this.isAdmin() && (content = this.getAdminTabContent(activeTab)) != null && content.charTyped(codePoint, modifiers)) {
            return true;
        }
        int n2 = parkourIdx = this.isAdmin() ? 7 : 3;
        if (activeTab == parkourIdx && this.parkourTab != null && this.parkourTab.charTyped(codePoint, modifiers)) {
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
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void openClassDialog() {
        this.clearWeaponPropertyOverlayState();
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || snapshot.classes().isEmpty() || this.choiceDialog == null) {
            this.requestSnapshotIfNeeded();
            return;
        }
        ArrayList<FixedChoiceConfigEntry.Choice> choices = new ArrayList<FixedChoiceConfigEntry.Choice>();
        HashMap<String, ChoiceListDialog.RowIcon> iconByClassKey = new HashMap<String, ChoiceListDialog.RowIcon>();
        for (ClientLoadoutUiState.ClassState classState : snapshot.classes().values()) {
            choices.add(new FixedChoiceConfigEntry.Choice(classState.classKey(), classState.className()));
            ChoiceListDialog.RowIcon icon = this.resolveClassIcon(classState.classKey());
            if (icon == null || icon.isEmpty()) continue;
            iconByClassKey.put(classState.classKey(), icon);
        }
        this.choiceDialog.show(snapshot.currentClassKey(), "\u5175\u79cd\u9009\u62e9", choices, selectedClass -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectClass(selectedClass)), null, null, null, "", -65536, 17, null, null, null, choice -> choice == null ? null : (ChoiceListDialog.RowIcon)iconByClassKey.get(choice.value()));
    }

    private void openSlotDialog(CustomLoadoutSlot slot) {
        if (this.isWeaponSlot(slot)) this.homeDetailSlot = slot;
        this.clearWeaponPropertyOverlayState();
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || snapshot.classes().isEmpty() || this.choiceDialog == null) {
            this.requestSnapshotIfNeeded();
            return;
        }
        ClientLoadoutUiState.ClassState currentClass = snapshot.classes().get(snapshot.currentClassKey());
        if (currentClass == null) {
            this.requestSnapshotIfNeeded();
            return;
        }
        ClientLoadoutUiState.SlotState slotState = currentClass.slots().get(slot.getIndex());
        if (slotState == null || slotState.entries().isEmpty()) {
            return;
        }
        HashSet<String> lockedIds = new HashSet<String>();
        HashSet<String> roomDisabledIds = new HashSet<String>();
        HashMap<String, ChoiceListDialog.RowIcon> iconByEntryId = new HashMap<String, ChoiceListDialog.RowIcon>();
        String classKey = currentClass.classKey();
        String selectedId = slotState.selectedId();
        if (selectedId == null || selectedId.isBlank() || !slotState.entries().containsKey(selectedId)) {
            selectedId = slotState.entries().isEmpty() ? "" : slotState.entries().keySet().iterator().next();
        }
        ArrayList<FixedChoiceConfigEntry.Choice> choices = new ArrayList<FixedChoiceConfigEntry.Choice>();
        for (ClientLoadoutUiState.EntryState entry : slotState.sortedEntries()) {
            boolean roomEnabled;
            String lockSuffix;
            String display = entry.displayName().isBlank() ? entry.id() : entry.displayName();
            boolean trial = entry.trial();
            String string = lockSuffix = entry.unlocked() || trial ? "" : "  Lv." + entry.unlockLevel() + "\u672a\u89e3\u9501";
            if (trial) {
                lockSuffix = "  \u00a7e[\u8bd5\u7528]";
            }
            String prefix = !(roomEnabled = entry.roomEnabled()) ? "\u00a78" : (entry.unlocked() || trial ? "\u00a7f" : "\u00a77");
            String label = prefix + display + " \u00a78[" + entry.id() + "]" + (String)(lockSuffix.isBlank() ? "" : " \u00a77" + lockSuffix);
            choices.add(new FixedChoiceConfigEntry.Choice(entry.id(), label));
            ChoiceListDialog.RowIcon icon = this.buildItemIcon(entry.itemSnbt());
            if (icon != null && !icon.isEmpty()) {
                iconByEntryId.put(entry.id(), icon);
            }
            if (!entry.unlocked() && !trial) {
                lockedIds.add(entry.id());
            }
            if (roomEnabled) continue;
            roomDisabledIds.add(entry.id());
        }
        if (slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON) {
            this.currentHoverSlot = slot;
            this.hoveredGunChoice = null;
            this.weaponPropertyPanelVisible = true;
            this.refreshWeaponPropertyButtonLabel();
            this.choiceDialog.show(selectedId, slotState.slotLabel().isBlank() ? slot.getKey() : slotState.slotLabel(), choices, selected -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectSlot(classKey, slot.getIndex(), selected)), null, null, null, "", -65536, 17, null, null, choice -> choice != null && !lockedIds.contains(choice.value()) && !roomDisabledIds.contains(choice.value()), choice -> choice == null ? null : (ChoiceListDialog.RowIcon)iconByEntryId.get(choice.value()), this::updateHoveredGunChoice, choice -> choice != null && roomDisabledIds.contains(choice.value()));
            this.syncChoiceDialogHorizontalOffset();
            return;
        }
        this.choiceDialog.show(selectedId, slotState.slotLabel().isBlank() ? slot.getKey() : slotState.slotLabel(), choices, selected -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectSlot(classKey, slot.getIndex(), selected)), null, null, null, "", -65536, 17, null, null, choice -> choice != null && !lockedIds.contains(choice.value()) && !roomDisabledIds.contains(choice.value()), choice -> choice == null ? null : (ChoiceListDialog.RowIcon)iconByEntryId.get(choice.value()), null, choice -> choice != null && roomDisabledIds.contains(choice.value()));
    }

    private void openAttachmentDialog(CustomLoadoutSlot slot, String attachmentType) {
        this.clearWeaponPropertyOverlayState();
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = this.getAttachmentPanel(slot);
        if (panel == null || !panel.available() || !panel.taczGun() || this.choiceDialog == null) {
            return;
        }
        ClientLoadoutUiState.AttachmentTypeState typeState = panel.attachmentTypes().get(attachmentType);
        if (typeState == null || !typeState.available()) {
            return;
        }
        ArrayList<FixedChoiceConfigEntry.Choice> choices = new ArrayList<FixedChoiceConfigEntry.Choice>();
        HashSet<String> lockedIds = new HashSet<String>();
        HashMap<String, ChoiceListDialog.RowIcon> iconById = new HashMap<String, ChoiceListDialog.RowIcon>();
        boolean filterByScope = "scope".equalsIgnoreCase(attachmentType) && typeState.maxScopeMagnification() > 0.0;
        choices.add(new FixedChoiceConfigEntry.Choice("", "\u00a7f\u65e0\u914d\u4ef6 \u00a78[\u5378\u4e0b\u5916\u7f6e\u4ef6]"));
        for (ClientLoadoutUiState.AttachmentChoiceState choice2 : typeState.sortedChoices()) {
            double cm;
            if (filterByScope && (cm = TaczClientAttachmentCompat.getInstance().resolveScopeMaxMagnification(choice2.itemSnbt())) > 0.0 && cm - typeState.maxScopeMagnification() > 1.0E-4) continue;
            String display = this.resolveAttachmentChoiceDisplayName(choice2);
            String lockSuffix = choice2.unlocked() ? "" : "  Lv." + choice2.unlockLevel() + "\u672a\u89e3\u9501";
            String prefix = choice2.unlocked() ? "\u00a7f" : "\u00a77";
            choices.add(new FixedChoiceConfigEntry.Choice(choice2.id(), prefix + display + " \u00a78[" + choice2.id() + "]" + (String)(lockSuffix.isBlank() ? "" : " \u00a77" + lockSuffix)));
            ChoiceListDialog.RowIcon icon = this.buildItemIcon(choice2.itemSnbt());
            if (icon != null && !icon.isEmpty()) {
                iconById.put(choice2.id(), icon);
            }
            if (choice2.unlocked()) continue;
            lockedIds.add(choice2.id());
        }
        String selectedId = typeState.hasStoredSelection() ? typeState.selectedAttachmentId() : "";
        this.currentHoverSlot = slot;
        this.currentAttachmentType = attachmentType;
        this.hoveredAttachmentChoice = null;
        this.weaponPropertyPanelVisible = true;
        this.refreshWeaponPropertyButtonLabel();
        this.choiceDialog.show(selectedId, typeState.attachmentLabel(), choices, selected -> {
            this.playAttachmentSelectionSound(typeState, selectedId, (String)selected);
            NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectAttachment(slot.getIndex(), panel.loadoutEntryId(), attachmentType, selected));
        }, null, null, null, "", -65536, 17, null, null, choice -> choice != null && (!lockedIds.contains(choice.value()) || choice.value().isBlank()), choice -> choice == null ? null : (ChoiceListDialog.RowIcon)iconById.get(choice.value()), this::updateHoveredAttachmentChoice);
        this.syncChoiceDialogHorizontalOffset();
    }

    private void playAttachmentSelectionSound(ClientLoadoutUiState.AttachmentTypeState typeState, String prevId, String selectedId) {
        if (typeState == null || Objects.equals(prevId, selectedId)) {
            return;
        }
        TaczClientAttachmentCompat compat = TaczClientAttachmentCompat.getInstance();
        if (selectedId == null || selectedId.isBlank()) {
            ItemStack prev = this.resolveAttachmentChoiceStack(typeState, prevId);
            if (!prev.isEmpty()) {
                compat.playAttachmentUninstallSound(prev);
            }
            return;
        }
        ItemStack sel = this.resolveAttachmentChoiceStack(typeState, selectedId);
        if (!sel.isEmpty()) {
            compat.playAttachmentInstallSound(sel);
        }
    }

    private void updateHoveredGunChoice(FixedChoiceConfigEntry.Choice choice) {
        if (choice != null) {
            this.hoveredGunChoice = choice;
        }
    }

    private void updateHoveredAttachmentChoice(FixedChoiceConfigEntry.Choice choice) {
        if (choice != null) {
            this.hoveredAttachmentChoice = choice;
        }
    }

    private ConfigScreenHeader.TabContent getAdminTabContent(int tabIndex) {
        return switch (tabIndex) {
            case 3 -> this.vehicleEditorTab;
            case 4 -> this.roomEditorTab;
            case 5 -> this.attachmentEditorTab;
            case 6 -> this.itemEditorTab;
            default -> null;
        };
    }

    private boolean mouseClickedAdminContent(ConfigScreenHeader.TabContent content, double mouseX, double mouseY, int button, int contentTop) {
        if (content instanceof AdminRoomEditorTabContent) {
            AdminRoomEditorTabContent r = (AdminRoomEditorTabContent)content;
            return r.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        }
        if (content instanceof AttachmentEditorTabContent) {
            AttachmentEditorTabContent a = (AttachmentEditorTabContent)content;
            return a.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        }
        if (content instanceof ItemEditorTabContent) {
            ItemEditorTabContent i = (ItemEditorTabContent)content;
            return i.mouseClickedContent(mouseX, mouseY, button, this.width, this.height, contentTop);
        }
        return false;
    }

    private boolean mouseScrolledAdminContent(ConfigScreenHeader.TabContent content, double mouseX, double mouseY, double delta, int contentTop) {
        if (content instanceof AdminRoomEditorTabContent) {
            AdminRoomEditorTabContent r = (AdminRoomEditorTabContent)content;
            return r.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        }
        if (content instanceof AttachmentEditorTabContent) {
            AttachmentEditorTabContent a = (AttachmentEditorTabContent)content;
            return a.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        }
        if (content instanceof ItemEditorTabContent) {
            ItemEditorTabContent i = (ItemEditorTabContent)content;
            return i.mouseScrolledContent(mouseX, mouseY, delta, this.width, this.height, contentTop);
        }
        return content.mouseScrolled(mouseX, mouseY, delta);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isOutOfGame() {
        return this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null;
    }

    private void updateOutOfGameTimer() {
        if (!this.isOutOfGame()) {
            this.outOfGameCloseAtMs = -1L;
            return;
        }
        if (this.outOfGameCloseAtMs <= 0L) {
            this.outOfGameCloseAtMs = System.currentTimeMillis() + 3000L;
        }
    }

    private void requestSnapshotIfNeeded() {
        if (this.isOutOfGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastRequestAt < 1200L) {
            return;
        }
        this.lastRequestAt = now;
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));
    }

    private ClientLoadoutUiState.WeaponAttachmentPanelState getAttachmentPanel(CustomLoadoutSlot slot) {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null || slot == null) {
            return null;
        }
        return snapshot.attachmentPanels().get(slot.getIndex());
    }

    private ItemStack parseStack(String snbt) {
        return ClientLoadoutUiState.parseAndCacheStack(snbt);
    }

    private String buildAttachmentSummary(ClientLoadoutUiState.WeaponAttachmentPanelState panel) {
        if (panel == null || panel.attachmentTypes().isEmpty()) {
            return "\u6682\u65e0\u53ef\u7528\u914d\u4ef6";
        }
        ArrayList<String> selectedLabels = new ArrayList<String>();
        for (String type : ATTACHMENT_TYPE_ORDER) {
            ClientLoadoutUiState.AttachmentTypeState state = panel.attachmentTypes().get(type);
            if (state == null || !state.hasStoredSelection()) continue;
            String value = this.resolveSelectedAttachmentDisplayName(state);
            selectedLabels.add(state.attachmentLabel() + ":" + value);
            if (selectedLabels.size() < 2) continue;
            break;
        }
        return selectedLabels.isEmpty() ? "\u5df2\u4fdd\u7559\u67aa\u68b0\u9ed8\u8ba4\u5916\u7f6e\u72b6\u6001" : String.join((CharSequence)"  |  ", selectedLabels);
    }

    private String resolveSelectedAttachmentDisplayName(ClientLoadoutUiState.AttachmentTypeState state) {
        if (state == null || !state.hasStoredSelection()) {
            return "";
        }
        if (state.selectedAttachmentId().isBlank()) {
            return "\u65e0\u914d\u4ef6";
        }
        ClientLoadoutUiState.AttachmentChoiceState sc = state.choices().get(state.selectedAttachmentId());
        return sc != null ? this.resolveAttachmentChoiceDisplayName(sc) : this.pickReadableAttachmentName(state.selectedDisplayName(), state.selectedAttachmentId());
    }

    private String resolveAttachmentChoiceDisplayName(ClientLoadoutUiState.AttachmentChoiceState choice) {
        if (choice == null) {
            return "";
        }
        String ld = TaczClientAttachmentCompat.getInstance().resolveLocalizedAttachmentDisplayName(choice.itemSnbt());
        return !ld.isBlank() ? ld : this.pickReadableAttachmentName(choice.displayName(), choice.id());
    }

    private String pickReadableAttachmentName(String preferred, String fallbackId) {
        String np = this.safe(preferred).trim();
        return !np.isBlank() && !this.looksLikeTranslationKey(np) ? np : this.safe(fallbackId);
    }

    private boolean looksLikeTranslationKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        boolean hd = value.indexOf(46) >= 0 || value.indexOf(58) >= 0;
        boolean hw = value.chars().anyMatch(Character::isWhitespace);
        boolean hc = value.chars().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        return hd && !hw && !hc;
    }

    private String resolveAttachmentLabel(String type) {
        return switch (type) {
            case "scope" -> "\u7784\u51c6\u955c";
            case "muzzle" -> "\u67aa\u53e3\u914d\u4ef6";
            case "stock" -> "\u67aa\u6258";
            case "grip" -> "\u524d\u63e1\u628a";
            case "laser" -> "\u6fc0\u5149\u6307\u793a\u5668";
            case "extended_mag" -> "\u6269\u5bb9\u5f39\u5323";
            default -> type;
        };
    }

    private void clearWeaponPropertyOverlayState() {
        this.currentHoverSlot = null;
        this.hoveredGunChoice = null;
        this.hoveredAttachmentChoice = null;
        this.currentAttachmentType = null;
        this.weaponPropertyPanelVisible = false;
        this.syncChoiceDialogHorizontalOffset();
        this.refreshWeaponPropertyButtonLabel();
    }

    private void refreshWeaponPropertyButtonLabel() {
        if (this.weaponPropertyButton != null) {
            this.weaponPropertyButton.setMessage(Component.literal(this.weaponPropertyPanelVisible ? "\u6b66\u5668\u6570\u636e\uff1a\u5f00" : "\u6b66\u5668\u6570\u636e\uff1a\u5173"));
        }
    }

    private boolean shouldShowWeaponPropertyOverlay() {
        return this.choiceDialog != null && this.choiceDialog.isVisible() && this.isWeaponSlot(this.currentHoverSlot);
    }

    private boolean isWeaponSlot(CustomLoadoutSlot slot) {
        return slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON;
    }

    private boolean isWeaponPreviewActive() {
        return !this.isOutOfGame() && !this.embeddedEditorDrawn && !this.vehicleDeployPageVisible && !this.isBlockingOverlayVisible()
            && (this.header == null || this.header.getSelectedTabIndex() == 0);
    }

    @Override
    public void removed() {
        if (this.weaponEditor != null) this.weaponEditor.close();
        this.weaponPreview.clearInteraction();
        super.removed();
    }

    private boolean isBlockingOverlayVisible() {
        return this.choiceDialog != null && this.choiceDialog.isVisible();
    }

    private void ensureWeaponEditor() {
        if (this.weaponEditor != null) return;
        this.weaponPreview.clearInteraction();
        this.clearWeaponPropertyOverlayState();
        if (this.weaponEditor == null) this.weaponEditor = new WeaponCustomizationView(new WeaponCustomizationView.Actions() {
            public void back() { }
            public void chooseWeapon() { LoadoutConfigScreen.this.openSlotDialog(LoadoutConfigScreen.this.homeDetailSlot); }
            public void switchSlot() {
                LoadoutConfigScreen.this.homeDetailSlot = LoadoutConfigScreen.this.homeDetailSlot == CustomLoadoutSlot.PRIMARY_WEAPON
                    ? CustomLoadoutSlot.SECONDARY_WEAPON : CustomLoadoutSlot.PRIMARY_WEAPON;
                LoadoutConfigScreen.this.rightContentScroll.reset();
            }
            public void selectAttachment(int slotIndex, String weaponEntryId, String type, String id) { LoadoutConfigScreen.this.selectEditorAttachment(slotIndex, weaponEntryId, type, id); }
        });
    }

    private void selectEditorAttachment(int slotIndex, String weaponEntryId, String type, String selectedId) {
        var snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) return;
        var panel = this.getAttachmentPanel(this.homeDetailSlot);
        if (panel == null || !panel.available() || !panel.taczGun() || type == null || selectedId == null) return;
        if (slotIndex != this.homeDetailSlot.getIndex() || !Objects.equals(weaponEntryId, panel.loadoutEntryId())) return;
        var state = panel.attachmentTypes().get(type);
        if (state == null || !state.available()) return;
        var choice = state.choices().get(selectedId);
        if (!selectedId.isBlank() && (choice == null || !choice.unlocked())) return;
        this.playAttachmentSelectionSound(state, state.hasStoredSelection() ? state.selectedAttachmentId() : "", selectedId);
        NetworkManager.getChannel().sendToServer(new LoadoutUiActionPacket("select_attachment",snapshot.currentClassKey(),
            slotIndex,selectedId,weaponEntryId,type));
    }

    private int getBaseUiMouseX(int mouseX) {
        return this.isBlockingOverlayVisible() ? -536870912 : mouseX;
    }

    private int getBaseUiMouseY(int mouseY) {
        return this.isBlockingOverlayVisible() ? -536870912 : mouseY;
    }

    private void toggleWeaponPropertyPanel() {
        if (!this.shouldShowWeaponPropertyOverlay()) {
            this.weaponPropertyPanelVisible = false;
            this.refreshWeaponPropertyButtonLabel();
            return;
        }
        ItemStack dw = this.resolveDisplayedWeaponStack();
        if (dw.isEmpty() || !TaczGunPropertyPanelCompat.getInstance().canRender(dw)) {
            this.weaponPropertyPanelVisible = false;
            this.refreshWeaponPropertyButtonLabel();
            return;
        }
        this.weaponPropertyPanelVisible = !this.weaponPropertyPanelVisible;
        this.syncChoiceDialogHorizontalOffset();
        this.refreshWeaponPropertyButtonLabel();
    }

    private ItemStack resolveDisplayedWeaponStack() {
        String tid;
        if (!this.isWeaponSlot(this.currentHoverSlot)) {
            return ItemStack.EMPTY;
        }
        if (this.currentAttachmentType != null && !this.currentAttachmentType.isBlank()) {
            return this.resolveDisplayedAttachmentPreviewStack();
        }
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.ClassState cc = snapshot.classes().get(snapshot.currentClassKey());
        if (cc == null) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.SlotState ss = cc.slots().get(this.currentHoverSlot.getIndex());
        if (ss == null) {
            return ItemStack.EMPTY;
        }
        String string = tid = this.hoveredGunChoice != null ? this.hoveredGunChoice.value() : ss.selectedId();
        if (tid == null || tid.isBlank() || !ss.entries().containsKey(tid)) {
            tid = this.resolveFallbackDisplayedWeaponId(ss);
        }
        if (tid == null || tid.isBlank()) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = this.getAttachmentPanel(this.currentHoverSlot);
        if (panel != null && panel.available() && tid.equals(ss.selectedId()) && tid.equals(panel.loadoutEntryId())) {
            return this.configuredWeaponStack(panel).copy();
        }
        ClientLoadoutUiState.EntryState entry = ss.entries().get(tid);
        return entry == null ? ItemStack.EMPTY : this.homeModelCache.computeIfAbsent(entry.itemSnbt(), this::parseStack);
    }

    private String resolveFallbackDisplayedWeaponId(ClientLoadoutUiState.SlotState ss) {
        if (ss == null || ss.entries().isEmpty()) {
            return "";
        }
        for (ClientLoadoutUiState.EntryState entry : ss.sortedEntries()) {
            if (entry == null || entry.id() == null || entry.id().isBlank() || this.parseStack(entry.itemSnbt()).isEmpty()) continue;
            return entry.id();
        }
        return ss.sortedEntries().stream().map(ClientLoadoutUiState.EntryState::id).filter(id -> id != null && !id.isBlank()).findFirst().orElse("");
    }

    private ItemStack resolveDisplayedAttachmentPreviewStack() {
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = this.getAttachmentPanel(this.currentHoverSlot);
        if (panel == null || !panel.available() || !panel.taczGun()) {
            return ItemStack.EMPTY;
        }
        ItemStack ws = this.configuredWeaponStack(panel).copy();
        if (ws.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (this.hoveredAttachmentChoice == null || this.hoveredAttachmentChoice.value() == null) {
            return ws;
        }
        if(this.hoveredAttachmentChoice.value().isBlank()) {
            return LoadoutWeaponPreview.apply(ws,List.of(new LoadoutWeaponPreview.Selection(this.currentAttachmentType,"",true)));
        }
        ClientLoadoutUiState.AttachmentTypeState ts = panel.attachmentTypes().get(this.currentAttachmentType);
        if (ts == null) {
            return ws;
        }
        ClientLoadoutUiState.AttachmentChoiceState cs = ts.choices().get(this.hoveredAttachmentChoice.value());
        if (cs == null) {
            return ws;
        }
        ItemStack as = this.parseStack(cs.itemSnbt());
        return as.isEmpty() ? ws : TaczClientAttachmentCompat.getInstance().installAttachment(ws, as);
    }

    private ItemStack resolveAttachmentChoiceStack(ClientLoadoutUiState.AttachmentTypeState typeState, String attachmentId) {
        if (typeState == null || attachmentId == null || attachmentId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.AttachmentChoiceState cs = typeState.choices().get(attachmentId);
        return cs == null ? ItemStack.EMPTY : this.parseStack(cs.itemSnbt());
    }

    private ChoiceListDialog.RowIcon buildItemIcon(String itemSnbt) {
        if (itemSnbt == null || itemSnbt.isBlank()) {
            return null;
        }
        ItemStack stack = ClientLoadoutUiState.parseAndCacheStack(itemSnbt);
        return stack.isEmpty() ? null : ChoiceListDialog.RowIcon.item(stack);
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
        ClientLoadoutUiState.ClassState cs = snapshot.classes().get(snapshot.currentClassKey());
        return cs == null || cs.className() == null || cs.className().isBlank() ? snapshot.currentClassKey() : cs.className();
    }

    private void renderTitleAndLevelLine(GuiGraphics guiGraphics, ClientLoadoutUiState.Snapshot snapshot, int x, int y, int maxWidth) {
        String titleLabel = "\u79f0\u53f7: ";
        String levelLabel = "\u7b49\u7ea7: ";
        String levelText = snapshot == null ? "-" : String.valueOf(snapshot.playerLevel());
        int labelColor = -2631721;
        int cursor = x;
        BattlefieldUiTheme.drawText(guiGraphics, titleLabel, cursor, y, labelColor);
        int llw = BattlefieldUiTheme.textWidth(levelLabel);
        int ltw = BattlefieldUiTheme.textWidth(levelText);
        int lx = Math.max((cursor += BattlefieldUiTheme.textWidth(titleLabel)) + 50, x + maxWidth - llw - ltw);
        int tw = Math.max(40, lx - cursor - 10);
        this.drawClippedComponent(guiGraphics, this.resolveCurrentTitleComponent(snapshot), cursor, y, tw);
        BattlefieldUiTheme.drawText(guiGraphics, levelLabel, lx, y, labelColor);
        int lc = snapshot == null ? -5592406 : PlayerLevelService.resolveLevelColorRgb(snapshot.playerLevel());
        BattlefieldUiTheme.drawText(guiGraphics, levelText, lx + llw, y, lc);
    }

    private Component resolveCurrentTitleComponent(ClientLoadoutUiState.Snapshot snapshot) {
        if (snapshot == null || snapshot.playerTitleText() == null || snapshot.playerTitleText().isBlank()) {
            return Component.literal("-").withStyle(style -> style.withColor(-8483944));
        }
        return PlayerTitleService.parseLegacyText(snapshot.playerTitleText());
    }

    private void drawClippedComponent(GuiGraphics guiGraphics, Component component, int x, int y, int maxWidth) {
        if (maxWidth <= 0) {
            return;
        }
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,x, y - 1, x + maxWidth, y + 10);
        BattlefieldUiTheme.drawText(guiGraphics, component, x, y, -1);
        guiGraphics.disableScissor();
    }

    private void drawClampedString(GuiGraphics guiGraphics, String text, int x, int y, int color, int maxWidth) {
        BattlefieldUiTheme.drawText(guiGraphics, this.clampToWidth(text, maxWidth), x, y, color);
    }

    private String clampToWidth(String text, int maxWidth) {
        String normalized = this.safe(text);
        if (normalized.isBlank() || BattlefieldUiTheme.textWidth(normalized) <= maxWidth) {
            return normalized;
        }
        String ellipsis = "...";
        int ew = BattlefieldUiTheme.textWidth(ellipsis);
        return ew >= maxWidth ? BattlefieldUiTheme.truncate(normalized, maxWidth) : BattlefieldUiTheme.truncate(normalized, maxWidth - ew) + ellipsis;
    }

    private static int vehicleListContentHeight(int rowCount) {
        if (rowCount <= 0) {
            return 0;
        }
        return (rowCount - 1) * 52 + 46;
    }

    private void renderVehicleDeployPage(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int contentTop) {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            return;
        }
        int left = 8;
        int right = this.width - 8;
        int listTop = contentTop + 24;
        int listBottom = this.height - 8;
        if (this.vehicleBackButton == null) {
            this.vehicleBackButton = new GDButton(right - 40, contentTop + 4, 40, 16, Component.literal("\u8fd4\u56de"), btn -> {
                this.vehicleDeployPageVisible = false;
            });
        }
        this.vehicleBackButton.setX(right - 40);
        this.vehicleBackButton.setY(contentTop + 4);
        this.vehicleBackButton.setWidth(40);
        this.vehicleBackButton.setHeight(16);
        this.vehicleBackButton.render(guiGraphics, mouseX, mouseY, partialTick);
        BattlefieldUiTheme.drawText(guiGraphics, "\u8f7d\u5177\u90e8\u7f72", 8, contentTop + 6, -1184275);
        List<ClientLoadoutUiState.VehicleEntryState> entries = this.sortedVehicleEntries();
        int viewHeight = Math.max(1, listBottom - listTop);
        int contentHeight = LoadoutConfigScreen.vehicleListContentHeight(entries.size());
        this.vehicleDeployScroll.update(mouseY, viewHeight, contentHeight);
        if (entries.isEmpty()) {
            ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
            String emptyKey = snapshot == null ? "gd656conquest.client.gui.loadout.loading" : !snapshot.vehiclePageAvailable() ? "gd656conquest.client.gui.loadout.vehicle.unavailable" : "gd656conquest.client.gui.loadout.vehicle.empty";
            BattlefieldUiTheme.drawText(guiGraphics, Component.translatable(emptyKey), 8, listTop + 8, BattlefieldUiTheme.palette().muted());
            return;
        }
        this.pruneVehicleButtons(entries);
        this.syncVehicleButtons(entries, contentTop);
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,8, listTop, right, listBottom);
        for (int i = 0; i < entries.size(); ++i) {
            int rowY = listTop + i * 52 - (int)Math.round(this.vehicleDeployScroll.scrollY());
            if (rowY + 46 <= listTop || rowY >= listBottom) continue;
            ClientLoadoutUiState.VehicleEntryState entry = entries.get(i);
            boolean selectedWaiting = this.isVehicleWaitingDeploy(entry);
            boolean selected = entry.selected();
            int bgColor = selectedWaiting || selected ? BattlefieldUiTheme.palette().header() : BattlefieldUiTheme.palette().panel();
            guiGraphics.fill(8, rowY, right, rowY + 46, bgColor);
            int accentColor = this.resolveVehicleStateColor(entry);
            guiGraphics.fill(8, rowY, 12, rowY + 46, accentColor);
            if (selectedWaiting || selected) {
                int outlineColor = BattlefieldUiTheme.palette().accent();
                guiGraphics.fill(8, rowY, right, rowY + 1, outlineColor);
                guiGraphics.fill(8, rowY + 46 - 1, right, rowY + 46, outlineColor);
                guiGraphics.fill(8, rowY, 9, rowY + 46, outlineColor);
                guiGraphics.fill(right - 1, rowY, right, rowY + 46, outlineColor);
            }
            String displayName = entry.displayName().isBlank() ? entry.entryId() : entry.displayName();
            BattlefieldUiTheme.drawText(guiGraphics, displayName, 18, rowY + 5, -1);
            BattlefieldUiTheme.drawText(guiGraphics, this.resolveVehicleStatusText(entry), 18, rowY + 18, accentColor);
            BattlefieldUiTheme.drawText(guiGraphics, entry.detailText(), 18, rowY + 31, -7167825);
            this.renderVehicleHealthLine(guiGraphics, entry, right, 8, rowY);
            GDButton btn2 = this.vehicleDeployButtons.get(entry.entryId());
            if (btn2 == null) continue;
            btn2.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.disableScissor();
        this.vehicleDeployScroll.renderScrollbar(guiGraphics, 8, listTop, right, listBottom, contentHeight);
    }

    private void renderVehicleHealthLine(GuiGraphics guiGraphics, ClientLoadoutUiState.VehicleEntryState entry, int right, int left, int rowY) {
        if (entry.healthPercent() >= 0) {
            String healthText = entry.healthCurrent() > 0 && entry.healthMax() > 0 ? "\u8010\u4e45 " + entry.healthCurrent() + "/" + entry.healthMax() + "  (" + entry.healthPercent() + "%)" : "\u8010\u4e45 " + entry.healthPercent() + "%";
            BattlefieldUiTheme.drawText(guiGraphics, healthText, right - 86 - 132, rowY + 8, entry.lowHealth() ? -6237968 : -6302807);
        }
        if (entry.seatCount() > 0) {
            BattlefieldUiTheme.drawText(guiGraphics, "\u5e2d\u4f4d " + entry.occupiedSeatCount() + "/" + entry.seatCount(), right - 86 - 132, rowY + 22, -7551745);
        } else if (entry.occupiedSeatCount() > 0) {
            BattlefieldUiTheme.drawText(guiGraphics, "\u4e58\u5458 " + entry.occupiedSeatCount(), right - 86 - 132, rowY + 22, -7551745);
        } else if (this.resolveVehicleDeployRemainSeconds(entry) > 0) {
            BattlefieldUiTheme.drawText(guiGraphics, "\u5012\u8ba1\u65f6 " + this.resolveVehicleDeployRemainSeconds(entry) + "\u79d2", right - 86 - 132, rowY + 22, -6237968);
        }
    }

    private void tickVehicleDeployPage() {
        if (this.awaitingSuccessfulDeployClose && ClientDeathFlowState.hasActiveCameraOverride() && !ClientRoomLockState.shouldReleaseMouseForUi()) {
            this.awaitingSuccessfulDeployClose = false;
            return;
        }
        this.requestSnapshotIfNeeded();
    }

    private void syncVehicleButtons(List<ClientLoadoutUiState.VehicleEntryState> entries, int contentTop) {
        int right = this.width - 8;
        int buttonX = right - 86 - 8;
        int listTop = contentTop + 24;
        for (int i = 0; i < entries.size(); ++i) {
            boolean active;
            ClientLoadoutUiState.VehicleEntryState entry = entries.get(i);
            int rowY = listTop + i * 52 - (int)Math.round(this.vehicleDeployScroll.scrollY());
            GDButton btn = this.vehicleDeployButtons.computeIfAbsent(entry.entryId(), id -> new GDButton(buttonX, rowY + 13, 86, 20, Component.literal(entry.actionText()), b -> this.deployVehicle((String)id)));
            btn.setX(buttonX);
            btn.setY(rowY + 13);
            btn.setWidth(86);
            btn.setHeight(20);
            btn.setMessage(Component.literal(this.isVehicleWaitingDeploy(entry) ? "\u7b49\u5f85\u90e8\u7f72" : entry.actionText()));
            btn.active = active = ClientRoomLockState.shouldReleaseMouseForUi() && entry.actionable() && !this.isVehicleWaitingDeploy(entry);
            btn.setTextColor(active ? -1 : -5592406);
        }
    }

    private void pruneVehicleButtons(List<ClientLoadoutUiState.VehicleEntryState> entries) {
        HashSet<String> activeIds = new HashSet<String>();
        for (ClientLoadoutUiState.VehicleEntryState e2 : entries) {
            activeIds.add(e2.entryId());
        }
        this.vehicleDeployButtons.entrySet().removeIf(e -> !activeIds.contains(e.getKey()));
    }

    private void deployVehicle(String entryId) {
        ClientLoadoutUiState.Snapshot snap = ClientLoadoutUiState.snapshot();
        if (snap == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        ClientLoadoutUiState.VehicleEntryState entry = snap.vehicleEntries().stream().filter(e -> entryId.equalsIgnoreCase(e.entryId())).findFirst().orElse(null);
        if (entry == null || !ClientRoomLockState.shouldReleaseMouseForUi() || !entry.actionable()) {
            return;
        }
        this.awaitingSuccessfulDeployClose = true;
        NetworkManager.getChannel().sendToServer(RoomDeploySelectPacket.confirmVehicle(entry.entryId()));
        if (entry.immediateDeploy()) {
            this.awaitingSuccessfulDeployClose = false;
            this.onClose();
        } else {
            this.requestSnapshotIfNeeded();
        }
    }

    private boolean mouseClickedVehicleDeployPage(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.vehicleBackButton != null && this.vehicleBackButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (mouseY >= this.getContentTop() + 24 && mouseY < this.height - 8) for (GDButton b : this.vehicleDeployButtons.values()) {
                if (!b.mouseClicked(mouseX, mouseY, button)) continue;
                return true;
            }
        }
        return false;
    }

    private boolean mouseScrolledVehicleDeployPage(double mouseX, double mouseY, double delta) {
        int left = 8;
        int right = this.width - 8;
        int listTop = this.getContentTop() + 24;
        int listBottom = this.height - 8;
        if (mouseX >= 8.0 && mouseX <= (double)right && mouseY >= (double)listTop && mouseY <= (double)listBottom) {
            return this.vehicleDeployScroll.mouseScrolled(delta, listBottom - listTop, LoadoutConfigScreen.vehicleListContentHeight(this.sortedVehicleEntries().size()));
        }
        return false;
    }

    private List<ClientLoadoutUiState.VehicleEntryState> sortedVehicleEntries() {
        ClientLoadoutUiState.Snapshot snap = ClientLoadoutUiState.snapshot();
        if (snap == null) {
            return List.of();
        }
        return snap.vehicleEntries().stream().sorted(Comparator.comparing(ClientLoadoutUiState.VehicleEntryState::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private boolean isVehicleWaitingDeploy(ClientLoadoutUiState.VehicleEntryState e) {
        return e != null && e.selected() && this.resolveVehicleDeployRemainSeconds(e) > 0;
    }

    private String resolveVehicleStatusText(ClientLoadoutUiState.VehicleEntryState e) {
        if (e == null) {
            return "";
        }
        int remain = this.resolveVehicleDeployRemainSeconds(e);
        if (e.selected() && remain > 0) {
            return "\u5df2\u9009\u5b9a\u90e8\u7f72\uff0c\u7b49\u5f85\u5012\u8ba1\u65f6\u7ed3\u675f\uff08\u7ea6 " + remain + " \u79d2\uff09";
        }
        return e.statusText();
    }

    private int resolveVehicleDeployRemainSeconds(ClientLoadoutUiState.VehicleEntryState e) {
        if (e == null || e.readyRemainSeconds() <= 0) {
            return 0;
        }
        long ageMs = ClientLoadoutUiState.snapshotAgeMs();
        if (ageMs == Long.MAX_VALUE) {
            return e.readyRemainSeconds();
        }
        return Math.max(0, e.readyRemainSeconds() - (int)(ageMs / 1000L));
    }

    private int resolveVehicleStateColor(ClientLoadoutUiState.VehicleEntryState e) {
        if (e == null) {
            return -1;
        }
        if (this.isVehicleWaitingDeploy(e)) {
            return -6642771;
        }
        if (e.lowHealth()) {
            return -6237968;
        }
        return switch (this.safe(e.stateToken()).toUpperCase()) {
            case "EMPTY" -> -3092272;
            case "SQUAD" -> -9576309;
            case "TEAM" -> -10246657;
            case "FULL", "ENEMY" -> -38037;
            case "WAIT", "WAITING" -> -6642771;
            default -> -1;
        };
    }

    private int getChoiceDialogLeft() {
        return this.getChoiceDialogBaseLeft() + this.resolveChoiceDialogHorizontalOffset();
    }

    private int getChoiceDialogTop() {
        return (this.height - 192) / 2 + 20;
    }

    private int getChoiceDialogBaseLeft() {
        return (this.width - 227) / 2;
    }

    private int getChoiceDialogOuterLeft() {
        return this.getChoiceDialogLeft() - 8;
    }

    private int getChoiceDialogOuterRight() {
        return this.getChoiceDialogLeft() + 227 + 8;
    }

    private int getChoiceDialogOuterTop() {
        int fh = this.font.lineHeight;
        int ty = this.getChoiceDialogTop() - fh - 8 + 6;
        return ty - 8;
    }

    private int getChoiceDialogOuterBottom() {
        return this.getChoiceDialogTop() + 192 + 8;
    }

    private int getChoiceDialogOuterHeight() {
        return this.getChoiceDialogOuterBottom() - this.getChoiceDialogOuterTop();
    }

    private int resolveChoiceDialogHorizontalOffset() {
        if (!(this.choiceDialog != null && this.choiceDialog.isVisible() && this.weaponPropertyPanelVisible && this.shouldShowWeaponPropertyOverlay())) {
            return 0;
        }
        ItemStack dw = this.resolveDisplayedWeaponStack();
        TaczGunPropertyPanelCompat pp = TaczGunPropertyPanelCompat.getInstance();
        if (dw.isEmpty() || !pp.canRender(dw)) {
            return 0;
        }
        int tw = this.getChoiceDialogOuterWidth() + 5 + pp.getPanelWidth();
        int dlo = (this.width - tw) / 2;
        return dlo - (this.getChoiceDialogBaseLeft() - 8);
    }

    private int getChoiceDialogOuterWidth() {
        return 243;
    }

    private void syncChoiceDialogHorizontalOffset() {
        if (this.choiceDialog != null) {
            this.choiceDialog.setHorizontalOffset(this.resolveChoiceDialogHorizontalOffset());
        }
    }

    private static class HomeTab
    implements ConfigScreenHeader.TabContent {
        private final Component title = Component.literal("\u4e3b\u9875");

        private HomeTab() {
        }

        @Override
        public Component getTitle() {
            return this.title;
        }

        @Override
        public void onTabOpen() {
        }

        @Override
        public void tick() {
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int contentTop) {
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return false;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return false;
        }
    }
}

