package org.mods.gd656conquest.client.loadout.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.compat.tacz.TaczClientAttachmentCompat;
import org.mods.gd656conquest.client.compat.tacz.TaczGunPropertyPanelCompat;
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
import java.util.Objects;
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
    private static final int CHOICE_DIALOG_WIDTH = 64 + 8 + 155;
    private static final int CHOICE_DIALOG_HEIGHT = 192;
    private static final int WEAPON_PROPERTY_GAP = 12;
    private static final int WEAPON_PROPERTY_PANEL_GAP = 5;
    private static final int WEAPON_PROPERTY_BUTTON_W = 100;
    private static final int WEAPON_PROPERTY_BUTTON_H = 18;
    private static final List<String> ATTACHMENT_TYPE_ORDER = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");

    private final Screen parent;
    private ChoiceListDialog choiceDialog;
    private final List<GDButton> actionButtons = new ArrayList<>();
    private final Map<String, GDButton> attachmentButtons = new LinkedHashMap<>();
    private GDButton closeButton;
    private GDButton vehicleDeployButton;
    private GDButton parkourLeaderboardButton;
    private GDButton editRoomsButton;
    private GDButton editVehiclesButton;
    private GDButton editItemsButton;
    private GDButton editAttachmentsButton;
    private GDButton weaponPropertyButton;
    private long lastRequestAt;
    private long outOfGameCloseAtMs;
    private final GDScrollState rightContentScroll = new GDScrollState();
    private FixedChoiceConfigEntry.Choice hoveredGunChoice;
    private FixedChoiceConfigEntry.Choice hoveredAttachmentChoice;
    private CustomLoadoutSlot currentHoverSlot;
    private String currentAttachmentType;
    private boolean weaponPropertyPanelVisible;

    public LoadoutConfigScreen(Screen parent) {
        super(Component.translatable("gd656conquest.client.gui.loadout.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.choiceDialog = new ChoiceListDialog(minecraft);
        this.weaponPropertyButton = new GDButton(0, 0, WEAPON_PROPERTY_BUTTON_W, WEAPON_PROPERTY_BUTTON_H, Component.literal("武器数据：关"), btn -> toggleWeaponPropertyPanel());
        this.weaponPropertyButton.setAutoScaleTextToFit(true);
        this.weaponPropertyButton.setMinAutoScale(0.58F);
        refreshWeaponPropertyButtonLabel();
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
        parkourLeaderboardButton = new GDButton(getParkourButtonX(), bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("跑酷排行榜"), btn -> openParkourLeaderboardScreen());
        int adminLeft = getAdminButtonStartX();
        editVehiclesButton = new GDButton(adminLeft, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("房间载具"), btn -> openVehicleAdminEditorScreen());
        editRoomsButton = new GDButton(adminLeft + bottomButtonWidth + BOTTOM_BAR_GAP, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("房间数据"), btn -> openRoomEditorScreen());
        editAttachmentsButton = new GDButton(adminLeft + (bottomButtonWidth + BOTTOM_BAR_GAP) * 2, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("配件数据"), btn -> openAttachmentEditorScreen());
        editItemsButton = new GDButton(adminLeft + (bottomButtonWidth + BOTTOM_BAR_GAP) * 3, bottomY, bottomButtonWidth, BOTTOM_BUTTON_H, Component.literal("物品数据"), btn -> openItemEditorScreen());
        closeButton.setAutoScaleTextToFit(true);
        vehicleDeployButton.setAutoScaleTextToFit(true);
        parkourLeaderboardButton.setAutoScaleTextToFit(true);
        parkourLeaderboardButton.setMinAutoScale(0.52F);
        editVehiclesButton.setAutoScaleTextToFit(true);
        editRoomsButton.setAutoScaleTextToFit(true);
        editAttachmentsButton.setAutoScaleTextToFit(true);
        editItemsButton.setAutoScaleTextToFit(true);
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
        int baseMouseX = getBaseUiMouseX(mouseX);
        int baseMouseY = getBaseUiMouseY(mouseY);
        for (GDButton button : actionButtons) {
            button.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }
        if (closeButton != null) {
            closeButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }
        if (parkourLeaderboardButton != null) {
            parkourLeaderboardButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }
        if (vehicleDeployButton != null) {
            ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
            boolean available = snapshot != null && snapshot.vehiclePageAvailable();
            vehicleDeployButton.active = available;
            vehicleDeployButton.setTextColor(available ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            vehicleDeployButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }
        if (editItemsButton != null) {
            boolean showEditButton = shouldShowItemEditorButton();
            editItemsButton.active = showEditButton;
            editItemsButton.setTextColor(showEditButton ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showEditButton) {
                editItemsButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
            }
        }
        if (editAttachmentsButton != null) {
            boolean showAttachmentEditor = shouldShowItemEditorButton();
            editAttachmentsButton.active = showAttachmentEditor;
            editAttachmentsButton.setTextColor(showAttachmentEditor ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showAttachmentEditor) {
                editAttachmentsButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
            }
        }
        if (editVehiclesButton != null) {
            boolean showVehicleButton = shouldShowRoomEditorButton();
            editVehiclesButton.active = showVehicleButton;
            editVehiclesButton.setTextColor(showVehicleButton ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showVehicleButton) {
                editVehiclesButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
            }
        }
        if (editRoomsButton != null) {
            boolean showRoomButton = shouldShowRoomEditorButton();
            editRoomsButton.active = showRoomButton;
            editRoomsButton.setTextColor(showRoomButton ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            if (showRoomButton) {
                editRoomsButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
            }
        }
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        rightContentScroll.update(baseMouseY, getRightContentViewportBottom() - getRightContentViewportTop(), getRightContentHeight());
        layoutAttachmentButtons(CustomLoadoutSlot.PRIMARY_WEAPON, getPrimaryCardTop());
        layoutAttachmentButtons(CustomLoadoutSlot.SECONDARY_WEAPON, getSecondaryCardTop());
        guiGraphics.drawString(this.font, this.title, LEFT, 12, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "选中枪械后右侧可快速自定义配件", LEFT, 24, 0xFFB8C6D6, false);
        guiGraphics.enableScissor(getRightPanelLeft(), getRightContentViewportTop(), getRightPanelRight(), getRightContentViewportBottom());
        renderOverviewPanel(guiGraphics, snapshot);
        renderAttachmentCard(guiGraphics, baseMouseX, baseMouseY, partialTick, CustomLoadoutSlot.PRIMARY_WEAPON, getPrimaryCardTop());
        renderAttachmentCard(guiGraphics, baseMouseX, baseMouseY, partialTick, CustomLoadoutSlot.SECONDARY_WEAPON, getSecondaryCardTop());
        guiGraphics.disableScissor();
        if (snapshot == null) {
            guiGraphics.drawString(this.font, Component.translatable("gd656conquest.client.gui.loadout.loading"), LEFT, TOP + (HEIGHT + GAP) * 7 + 12, 0xFFAA00, false);
        }
        rightContentScroll.renderScrollbar(guiGraphics, getRightPanelLeft(), getRightContentViewportTop(), getRightPanelRight(), getRightContentViewportBottom(), getRightContentHeight());
        syncChoiceDialogHorizontalOffset();
        if (choiceDialog != null) {
            choiceDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        renderWeaponPropertyPanel(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderWeaponPropertyPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderWeaponPropertyOverlayContents(guiGraphics, mouseX, mouseY, partialTick);
        return;
        /*
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) return;
        
        ClientLoadoutUiState.ClassState currentClass = snapshot.classes().get(snapshot.currentClassKey());
        if (currentClass == null) return;
        
        ClientLoadoutUiState.SlotState slotState = currentClass.slots().get(slot.getIndex());
        if (slotState == null) return;
        
        String selectedId = slotState.selectedId();
        ClientLoadoutUiState.EntryState selectedEntry = slotState.entries().get(selectedId);
        
        ItemStack originalStack = ItemStack.EMPTY;
        if (selectedEntry != null) {
            originalStack = parseStack(selectedEntry.itemSnbt());
        }
        
        ItemStack compareStack = ItemStack.EMPTY;
        if (hoveredChoice != null) {
            ClientLoadoutUiState.EntryState hoveredEntry = slotState.entries().get(hoveredChoice.value());
            if (hoveredEntry != null) {
                compareStack = parseStack(hoveredEntry.itemSnbt());
            }
        }
        
        if (originalStack.isEmpty() && compareStack.isEmpty()) {
            return;
        }

        int panelWidth = 200;
        int panelHeight = 120;
        // Position it to the left of the ChoiceListDialog
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int dialogWidth = 64 + 8 + 155; // PREVIEW_WIDTH + PREVIEW_GAP + LIST_WIDTH
        int centerX = (screenWidth - dialogWidth) / 2;
        int panelX = centerX - panelWidth - 20;
        int panelY = (screenHeight - panelHeight) / 2;
        
        // Draw background
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDD10181F);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0x66FFD78A);
        
        guiGraphics.drawString(this.font, "枪械数据对比", panelX + 8, panelY + 6, 0xFFFFD78A, false);
        
        GunStatComparator.render(guiGraphics, this.font, panelX + 8, panelY + 24, panelWidth - 16, originalStack, compareStack);
        */
    }
    
    private void renderAttachmentStatComparator(GuiGraphics guiGraphics, CustomLoadoutSlot slot, FixedChoiceConfigEntry.Choice hoveredChoice) {
        return;
        /*
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = getAttachmentPanel(slot);
        if (panel == null || !panel.available() || !panel.taczGun()) return;
        
        ItemStack weaponStack = parseStack(panel.weaponItemSnbt());
        if (weaponStack.isEmpty()) return;
        
        ItemStack compareStack = weaponStack.copy();
        if (hoveredChoice != null && hoveredChoice.value() != null && !hoveredChoice.value().isBlank()) {
            ClientLoadoutUiState.AttachmentTypeState typeState = panel.attachmentTypes().get(hoveredChoice.value().split(":")[0]); // We don't have type in value...
            // Wait, hoveredChoice is from the ChoiceListDialog of ATTACHMENTS
            // In openAttachmentDialog, choices have id as choice.value()
            // And choice is an AttachmentChoiceState
            
            // To apply it, we can just deserialize the hovered attachment snbt and apply it using TaczClientAttachmentCompat
            String hoverId = hoveredChoice.value();
            for (ClientLoadoutUiState.AttachmentTypeState state : panel.attachmentTypes().values()) {
                ClientLoadoutUiState.AttachmentChoiceState hoverChoiceState = state.choices().get(hoverId);
                if (hoverChoiceState != null) {
                    ItemStack attachmentStack = parseStack(hoverChoiceState.itemSnbt());
                    if (!attachmentStack.isEmpty()) {
                        compareStack = TaczClientAttachmentCompat.getInstance().installAttachment(compareStack, attachmentStack);
                    }
                    break;
                }
            }
        }
        
        int panelWidth = 200;
        int panelHeight = 120;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int dialogWidth = 64 + 8 + 155;
        int centerX = (screenWidth - dialogWidth) / 2;
        int panelX = centerX - panelWidth - 20;
        int panelY = (screenHeight - panelHeight) / 2;
        
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDD10181F);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0x66FFD78A);
        guiGraphics.drawString(this.font, "安装配件后数据预览", panelX + 8, panelY + 6, 0xFFFFD78A, false);
        
        GunStatComparator.render(guiGraphics, this.font, panelX + 8, panelY + 24, panelWidth - 16, weaponStack, compareStack);
        */
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
        if (!canRender) {
            weaponPropertyPanelVisible = false;
        }
        refreshWeaponPropertyButtonLabel();

        int panelWidth = propertyPanel.getPanelWidth();
        int panelHeight = getChoiceDialogOuterHeight();
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

        if (!weaponPropertyPanelVisible || !canRender) {
            return;
        }

        int overlayX = getChoiceDialogOuterRight() + WEAPON_PROPERTY_PANEL_GAP;
        int panelY = getChoiceDialogOuterTop();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 510.0F);
        propertyPanel.render(guiGraphics, this.font, overlayX, panelY, panelHeight, displayedWeapon);
        guiGraphics.pose().popPose();
    }

    private boolean shouldShowWeaponPropertyOverlay() {
        return choiceDialog != null && choiceDialog.isVisible() && isWeaponSlot(currentHoverSlot);
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
        if (!shouldShowWeaponPropertyOverlay()) {
            weaponPropertyPanelVisible = false;
            refreshWeaponPropertyButtonLabel();
            return;
        }
        ItemStack displayedWeapon = resolveDisplayedWeaponStack();
        if (displayedWeapon.isEmpty() || !TaczGunPropertyPanelCompat.getInstance().canRender(displayedWeapon)) {
            weaponPropertyPanelVisible = false;
            refreshWeaponPropertyButtonLabel();
            return;
        }
        weaponPropertyPanelVisible = !weaponPropertyPanelVisible;
        syncChoiceDialogHorizontalOffset();
        refreshWeaponPropertyButtonLabel();
    }

    private ItemStack resolveDisplayedWeaponStack() {
        if (!isWeaponSlot(currentHoverSlot)) {
            return ItemStack.EMPTY;
        }
        if (currentAttachmentType != null && !currentAttachmentType.isBlank()) {
            return resolveDisplayedAttachmentPreviewStack();
        }
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.ClassState currentClass = snapshot.classes().get(snapshot.currentClassKey());
        if (currentClass == null) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.SlotState slotState = currentClass.slots().get(currentHoverSlot.getIndex());
        if (slotState == null) {
            return ItemStack.EMPTY;
        }

        String targetId = hoveredGunChoice != null ? hoveredGunChoice.value() : slotState.selectedId();
        if (targetId == null || targetId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.EntryState entry = slotState.entries().get(targetId);
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        return parseStack(entry.itemSnbt());
    }

    private ItemStack resolveDisplayedAttachmentPreviewStack() {
        ClientLoadoutUiState.WeaponAttachmentPanelState panel = getAttachmentPanel(currentHoverSlot);
        if (panel == null || !panel.available() || !panel.taczGun()) {
            return ItemStack.EMPTY;
        }
        ItemStack weaponStack = parseStack(panel.weaponItemSnbt());
        if (weaponStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (hoveredAttachmentChoice == null || hoveredAttachmentChoice.value() == null || hoveredAttachmentChoice.value().isBlank()) {
            return weaponStack;
        }
        ClientLoadoutUiState.AttachmentTypeState typeState = panel.attachmentTypes().get(currentAttachmentType);
        if (typeState == null) {
            return weaponStack;
        }
        ClientLoadoutUiState.AttachmentChoiceState choiceState = typeState.choices().get(hoveredAttachmentChoice.value());
        if (choiceState == null) {
            return weaponStack;
        }
        ItemStack attachmentStack = parseStack(choiceState.itemSnbt());
        if (attachmentStack.isEmpty()) {
            return weaponStack;
        }
        return TaczClientAttachmentCompat.getInstance().installAttachment(weaponStack, attachmentStack);
    }

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
        int fontHeight = this.font.lineHeight;
        int titleY = getChoiceDialogTop() - fontHeight - GuiConstants.DEFAULT_PADDING + 6;
        return titleY - GuiConstants.DEFAULT_PADDING;
    }

    private int getChoiceDialogOuterBottom() {
        return getChoiceDialogTop() + CHOICE_DIALOG_HEIGHT + GuiConstants.DEFAULT_PADDING;
    }

    private int getChoiceDialogOuterHeight() {
        return getChoiceDialogOuterBottom() - getChoiceDialogOuterTop();
    }

    private int resolveChoiceDialogHorizontalOffset() {
        if (choiceDialog == null || !choiceDialog.isVisible() || !weaponPropertyPanelVisible || !shouldShowWeaponPropertyOverlay()) {
            return 0;
        }
        ItemStack displayedWeapon = resolveDisplayedWeaponStack();
        TaczGunPropertyPanelCompat propertyPanel = TaczGunPropertyPanelCompat.getInstance();
        if (displayedWeapon.isEmpty() || !propertyPanel.canRender(displayedWeapon)) {
            return 0;
        }
        int totalWidth = getChoiceDialogOuterWidth() + WEAPON_PROPERTY_PANEL_GAP + propertyPanel.getPanelWidth();
        int desiredOuterLeft = (this.width - totalWidth) / 2;
        return desiredOuterLeft - (getChoiceDialogBaseLeft() - GuiConstants.DEFAULT_PADDING);
    }

    private int getChoiceDialogOuterWidth() {
        return CHOICE_DIALOG_WIDTH + GuiConstants.DEFAULT_PADDING * 2;
    }

    private void syncChoiceDialogHorizontalOffset() {
        if (choiceDialog != null) {
            choiceDialog.setHorizontalOffset(resolveChoiceDialogHorizontalOffset());
        }
    }

    private void refreshWeaponPropertyButtonLabel() {
        if (weaponPropertyButton != null) {
            weaponPropertyButton.setMessage(Component.literal(weaponPropertyPanelVisible ? "武器数据：开" : "武器数据：关"));
        }
    }

    private boolean isWeaponSlot(CustomLoadoutSlot slot) {
        return slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON;
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
                if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                return true;
            }
            if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (parkourLeaderboardButton != null && parkourLeaderboardButton.mouseClicked(mouseX, mouseY, button)) {
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
                rightContentScroll.startDragging(
                    mouseX,
                    mouseY,
                    getRightPanelLeft(),
                    getRightContentViewportTop(),
                    getRightPanelRight(),
                    getRightContentViewportBottom(),
                    getRightContentHeight()
                );
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
        syncChoiceDialogHorizontalOffset();
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
        clearWeaponPropertyOverlayState();
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
        clearWeaponPropertyOverlayState();
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

        Set<String> lockedIds = new HashSet<>();
        Set<String> roomDisabledIds = new HashSet<>();
        Map<String, ChoiceListDialog.RowIcon> iconByEntryId = new HashMap<>();
        String classKey = currentClass.classKey();
        String selectedId = slotState.selectedId();
        if (selectedId == null || selectedId.isBlank() || !slotState.entries().containsKey(selectedId)) {
            selectedId = slotState.entries().isEmpty() ? "" : slotState.entries().keySet().iterator().next();
        }

        /*
        if (slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON) {
            Map<String, ChoiceGroup> groupsMap = new LinkedHashMap<>();
            String[] gunTypes = {"rifle", "sniper", "pistol", "shotgun", "smg", "mg", "rpg"};
            String[] gunTypeLabels = {"突击步枪", "狙击步枪", "手枪", "霰弹枪", "冲锋枪", "机枪", "发射器"};
            
            for (int i = 0; i < gunTypes.length; i++) {
                groupsMap.put(gunTypes[i], new ChoiceGroup(gunTypes[i], gunTypeLabels[i]));
            }
            ChoiceGroup othersGroup = new ChoiceGroup("others", "其它武器");

            for (ClientLoadoutUiState.EntryState entry : slotState.sortedEntries()) {
                String display = entry.displayName().isBlank() ? entry.id() : entry.displayName();
                String lockSuffix = entry.unlocked() ? "" : ("  Lv." + entry.unlockLevel() + "未解锁");
                String prefix = entry.unlocked() ? "§f" : "§7";
                String label = prefix + display + " §8[" + entry.id() + "]" + (lockSuffix.isBlank() ? "" : " §7" + lockSuffix);
                FixedChoiceConfigEntry.Choice choice = new FixedChoiceConfigEntry.Choice(entry.id(), label);
                
                ChoiceListDialog.RowIcon icon = buildItemIcon(entry.itemSnbt());
                if (icon != null && !icon.isEmpty()) {
                    iconByEntryId.put(entry.id(), icon);
                }
                if (!entry.unlocked()) {
                    lockedIds.add(entry.id());
                }

                String gunType = TaczClientAttachmentCompat.getInstance().resolveGunType(entry.itemSnbt());
                ChoiceGroup targetGroup = groupsMap.get(gunType);
                if (targetGroup != null) {
                    targetGroup.getChoices().add(choice);
                } else {
                    othersGroup.getChoices().add(choice);
                }
            }

            List<ChoiceGroup> activeGroups = new ArrayList<>();
            for (ChoiceGroup group : groupsMap.values()) {
                if (!group.getChoices().isEmpty()) {
                    activeGroups.add(group);
                }
            }
            if (!othersGroup.getChoices().isEmpty()) {
                activeGroups.add(othersGroup);
            }

            if (slot == CustomLoadoutSlot.PRIMARY_WEAPON) {
                if (primaryWeaponChoiceState == null) {
                    primaryWeaponChoiceState = new ChoiceListState("primary_weapon");
                }
            } else {
                if (secondaryWeaponChoiceState == null) {
                    secondaryWeaponChoiceState = new ChoiceListState("secondary_weapon");
                }
            }
            
            currentHoverSlot = slot;
            hoveredGunChoice = null;

            choiceDialog.showWithGroups(
                selectedId,
                slotState.slotLabel().isBlank() ? slot.getKey() : slotState.slotLabel(),
                activeGroups,
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
                choice -> choice == null ? null : iconByEntryId.get(choice.value()),
                slot == CustomLoadoutSlot.PRIMARY_WEAPON ? primaryWeaponChoiceState : secondaryWeaponChoiceState,
                choice -> hoveredGunChoice = choice
            );
            return;
        }
        */

        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        for (ClientLoadoutUiState.EntryState entry : slotState.sortedEntries()) {
            String display = entry.displayName().isBlank() ? entry.id() : entry.displayName();
            String lockSuffix = entry.unlocked() ? "" : ("  Lv." + entry.unlockLevel() + "未解锁");
            boolean roomEnabled = entry.roomEnabled();
            String prefix = !roomEnabled ? "§8" : entry.unlocked() ? "§f" : "§7";
            String label = prefix + display + " §8[" + entry.id() + "]" + (lockSuffix.isBlank() ? "" : " §7" + lockSuffix);
            choices.add(new FixedChoiceConfigEntry.Choice(entry.id(), label));
            ChoiceListDialog.RowIcon icon = buildItemIcon(entry.itemSnbt());
            if (icon != null && !icon.isEmpty()) {
                iconByEntryId.put(entry.id(), icon);
            }
            if (!entry.unlocked()) {
                lockedIds.add(entry.id());
            }
            if (!roomEnabled) {
                roomDisabledIds.add(entry.id());
            }
        }

        if (slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON) {
            currentHoverSlot = slot;
            hoveredGunChoice = null;
            weaponPropertyPanelVisible = true;
            refreshWeaponPropertyButtonLabel();
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
                    choice -> choice != null && !lockedIds.contains(choice.value()) && !roomDisabledIds.contains(choice.value()),
                    choice -> choice == null ? null : iconByEntryId.get(choice.value()),
                    choice -> hoveredGunChoice = choice,
                    choice -> choice != null && roomDisabledIds.contains(choice.value())
            );
            syncChoiceDialogHorizontalOffset();
            return;
        }

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
                choice -> choice != null && !lockedIds.contains(choice.value()) && !roomDisabledIds.contains(choice.value()),
                choice -> choice == null ? null : iconByEntryId.get(choice.value()),
                null,
                choice -> choice != null && roomDisabledIds.contains(choice.value())
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

    private int getParkourButtonX() {
        return getCloseButtonX() - BOTTOM_BAR_GAP - getBottomButtonWidth();
    }

    private int getAdminButtonStartX() {
        int adminCount = 4;
        int bottomButtonWidth = getBottomButtonWidth();
        int totalWidth = adminCount * bottomButtonWidth + (adminCount - 1) * BOTTOM_BAR_GAP;
        return Math.max(LEFT + bottomButtonWidth + 8, getParkourButtonX() - BOTTOM_BAR_GAP - totalWidth);
    }

    private int getBottomButtonWidth() {
        int availableWidth = this.width - LEFT - RIGHT - BOTTOM_BAR_GAP * 6;
        return Math.max(42, Math.min(BOTTOM_BUTTON_W, availableWidth / 7));
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
        clearWeaponPropertyOverlayState();
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
        boolean filterByScopeMagnification = "scope".equalsIgnoreCase(attachmentType) && typeState.maxScopeMagnification() > 0.0D;
        double maxScopeMagnification = typeState.maxScopeMagnification();
        choices.add(new FixedChoiceConfigEntry.Choice("", "§f无配件 §8[卸下外置件]"));
        for (ClientLoadoutUiState.AttachmentChoiceState choice : typeState.sortedChoices()) {
            if (filterByScopeMagnification) {
                double choiceMagnification = TaczClientAttachmentCompat.getInstance().resolveScopeMaxMagnification(choice.itemSnbt());
                if (choiceMagnification > 0.0D && choiceMagnification - maxScopeMagnification > 0.0001D) {
                    continue;
                }
            }
            String display = resolveAttachmentChoiceDisplayName(choice);
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
        currentHoverSlot = slot;
        currentAttachmentType = attachmentType;
        hoveredAttachmentChoice = null;
        weaponPropertyPanelVisible = true;
        refreshWeaponPropertyButtonLabel();

        choiceDialog.show(
            selectedId,
            typeState.attachmentLabel(),
            choices,
            selected -> {
                playAttachmentSelectionSound(typeState, selectedId, selected);
                NetworkManager.getChannel().sendToServer(
                    LoadoutUiActionPacket.selectAttachment(slot.getIndex(), panel.loadoutEntryId(), attachmentType, selected)
                );
            },
            null,
            null,
            null,
            "",
            0xFFFF0000,
            17,
            null,
            null,
            choice -> choice != null && (!lockedIds.contains(choice.value()) || choice.value().isBlank()),
            choice -> choice == null ? null : iconById.get(choice.value()),
            choice -> hoveredAttachmentChoice = choice
        );
        syncChoiceDialogHorizontalOffset();
    }

    private void playAttachmentSelectionSound(ClientLoadoutUiState.AttachmentTypeState typeState, String previousSelectedId, String selectedId) {
        if (typeState == null || Objects.equals(previousSelectedId, selectedId)) {
            return;
        }
        TaczClientAttachmentCompat compat = TaczClientAttachmentCompat.getInstance();
        if (selectedId == null || selectedId.isBlank()) {
            ItemStack previousAttachment = resolveAttachmentChoiceStack(typeState, previousSelectedId);
            if (!previousAttachment.isEmpty()) {
                compat.playAttachmentUninstallSound(previousAttachment);
            }
            return;
        }
        ItemStack selectedAttachment = resolveAttachmentChoiceStack(typeState, selectedId);
        if (!selectedAttachment.isEmpty()) {
            compat.playAttachmentInstallSound(selectedAttachment);
        }
    }

    private ItemStack resolveAttachmentChoiceStack(ClientLoadoutUiState.AttachmentTypeState typeState, String attachmentId) {
        if (typeState == null || attachmentId == null || attachmentId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ClientLoadoutUiState.AttachmentChoiceState choiceState = typeState.choices().get(attachmentId);
        return choiceState == null ? ItemStack.EMPTY : parseStack(choiceState.itemSnbt());
    }

    private ChoiceListDialog.RowIcon buildItemIcon(String itemSnbt) {
        if (itemSnbt == null || itemSnbt.isBlank()) {
            return null;
        }
        ItemStack stack = ClientLoadoutUiState.parseAndCacheStack(itemSnbt);
        if (stack.isEmpty()) {
            return null;
        }
        return ChoiceListDialog.RowIcon.item(stack);
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

    private void openParkourLeaderboardScreen() {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new LoadoutParkourLeaderboardScreen(this));
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

    private ItemStack parseStack(String snbt) {
        return ClientLoadoutUiState.parseAndCacheStack(snbt);
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
            String value = resolveSelectedAttachmentDisplayName(state);
            selectedLabels.add(state.attachmentLabel() + ":" + value);
            if (selectedLabels.size() >= 2) {
                break;
            }
        }
        return selectedLabels.isEmpty() ? "已保留枪械默认外置状态" : String.join("  |  ", selectedLabels);
    }

    private String resolveSelectedAttachmentDisplayName(ClientLoadoutUiState.AttachmentTypeState state) {
        if (state == null || !state.hasStoredSelection()) {
            return "";
        }
        if (state.selectedAttachmentId().isBlank()) {
            return "无配件";
        }
        ClientLoadoutUiState.AttachmentChoiceState selectedChoice = state.choices().get(state.selectedAttachmentId());
        if (selectedChoice != null) {
            return resolveAttachmentChoiceDisplayName(selectedChoice);
        }
        return pickReadableAttachmentName(state.selectedDisplayName(), state.selectedAttachmentId());
    }

    private String resolveAttachmentChoiceDisplayName(ClientLoadoutUiState.AttachmentChoiceState choice) {
        if (choice == null) {
            return "";
        }
        String localizedDisplay = TaczClientAttachmentCompat.getInstance().resolveLocalizedAttachmentDisplayName(choice.itemSnbt());
        if (!localizedDisplay.isBlank()) {
            return localizedDisplay;
        }
        return pickReadableAttachmentName(choice.displayName(), choice.id());
    }

    private String pickReadableAttachmentName(String preferred, String fallbackId) {
        String normalizedPreferred = safe(preferred).trim();
        if (!normalizedPreferred.isBlank() && !looksLikeTranslationKey(normalizedPreferred)) {
            return normalizedPreferred;
        }
        return safe(fallbackId);
    }

    private boolean looksLikeTranslationKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        boolean hasDelimiter = value.indexOf('.') >= 0 || value.indexOf(':') >= 0;
        boolean hasWhitespace = value.chars().anyMatch(Character::isWhitespace);
        boolean hasCjk = value.chars().anyMatch(codePoint ->
            Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
        );
        return hasDelimiter && !hasWhitespace && !hasCjk;
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
