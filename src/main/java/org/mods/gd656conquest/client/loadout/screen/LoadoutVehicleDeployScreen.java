/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.loadout.screen;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;

public final class LoadoutVehicleDeployScreen
extends Screen {
    private static final int LEFT = 18;
    private static final int TOP = 38;
    private static final int ROW_HEIGHT = 46;
    private static final int ROW_GAP = 6;
    private static final int BUTTON_WIDTH = 86;
    private static final long REQUEST_INTERVAL_MS = 1000L;
    private final Screen parent;
    private final Map<String, GDButton> buttonByEntryId = new LinkedHashMap<String, GDButton>();
    private GDButton backButton;
    private GDButton closeButton;
    private long lastRequestAt;
    private long outOfGameCloseAtMs;
    private final GDScrollState listScrollState = new GDScrollState();
    private boolean awaitingSuccessfulDeployClose;
    private long cachedSortedEntriesUpdatedAtMs = Long.MIN_VALUE;
    private List<ClientLoadoutUiState.VehicleEntryState> cachedSortedEntries = List.of();

    public LoadoutVehicleDeployScreen(Screen parent) {
        super(Component.translatable("gd656conquest.client.gui.loadout.vehicle.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.updateOutOfGameTimer();
        this.listScrollState.reset();
        this.rebuildStaticButtons();
        this.syncVehicleButtons();
        if (!this.isOutOfGame()) {
            this.requestSnapshotIfNeeded();
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.updateOutOfGameTimer();
        if (this.isOutOfGame() && this.outOfGameCloseAtMs > 0L && System.currentTimeMillis() >= this.outOfGameCloseAtMs) {
            this.onClose();
            return;
        }
        if (this.awaitingSuccessfulDeployClose && !ClientRoomLockState.shouldReleaseMouseForUi()) {
            this.closeToGameplay();
            return;
        }
        this.requestSnapshotIfNeeded();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int totalCount;
        BattlefieldUiTheme.background(guiGraphics, this.width, this.height);
        if (this.isOutOfGame()) {
            int remain = Math.max(0, (int)Math.ceil((double)(this.outOfGameCloseAtMs - System.currentTimeMillis()) / 1000.0));
            BattlefieldUiTheme.centered(guiGraphics, "\u8bf7\u5728\u6e38\u620f\u5185\u6253\u5f00\u8f7d\u5177\u90e8\u7f72\u9875", this.width / 2, this.height / 2 - 10, -32640);
            BattlefieldUiTheme.centered(guiGraphics, "\u5c06\u5728 " + remain + " \u79d2\u540e\u81ea\u52a8\u5173\u95ed", this.width / 2, this.height / 2 + 6, -4667690);
            if (this.closeButton != null) {
                this.closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        BattlefieldUiTheme.drawText(guiGraphics, this.title, 18, 12, 0xFFFFFF);
        BattlefieldUiTheme.drawText(guiGraphics, Component.translatable(ClientRoomLockState.shouldReleaseMouseForUi() ? "gd656conquest.client.gui.loadout.vehicle.deploy_enabled" : "gd656conquest.client.gui.loadout.vehicle.deploy_disabled"), 18, 24, -4667690);
        int listY1 = 38;
        int listY2 = this.height - 36;
        int viewHeight = Math.max(1, listY2 - listY1);
        int contentHeight = this.vehicleListContentHeight();
        this.listScrollState.update(mouseY, viewHeight, contentHeight);
        this.syncVehicleButtons();
        List<ClientLoadoutUiState.VehicleEntryState> entries = this.sortedEntries();
        if (entries.isEmpty()) {
            String key = this.currentEmptyMessageKey();
            BattlefieldUiTheme.drawText(guiGraphics, Component.translatable(key), 18, 46, -4667690);
        } else {
            org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,18, listY1, this.width - 18, listY2);
            for (int index = 0; index < entries.size(); ++index) {
                int rowY = 38 + index * 52 - (int)Math.round(this.listScrollState.scrollY());
                if (rowY + 46 <= listY1 || rowY >= listY2) continue;
                this.renderVehicleRow(guiGraphics, entries.get(index), this.buttonByEntryId.get(entries.get(index).entryId()), mouseX, mouseY, partialTick, rowY);
            }
            guiGraphics.disableScissor();
            this.listScrollState.renderScrollbar(guiGraphics, 18, listY1, this.width - 18, listY2, contentHeight);
        }
        if (this.backButton != null) {
            this.backButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.closeButton != null) {
            this.closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if ((totalCount = this.sortedEntries().size()) > 0 && contentHeight > viewHeight) {
            int stride = 52;
            int firstVisible = Math.min(totalCount, Math.max(1, (int)Math.floor(this.listScrollState.scrollY() / (double)stride) + 1));
            int lastVisible = Math.min(totalCount, Math.max(firstVisible, (int)Math.ceil((this.listScrollState.scrollY() + (double)viewHeight) / (double)stride)));
            String pageText = String.format("%d-%d / %d", firstVisible, lastVisible, totalCount);
            BattlefieldUiTheme.drawText(guiGraphics, pageText, this.width - 84, 12, -4667690);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.closeButton != null && this.closeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.backButton != null && this.backButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (mouseY >= 38 && mouseY < this.height - 36) for (GDButton rowButton : this.buttonByEntryId.values()) {
                if (!rowButton.mouseClicked(mouseX, mouseY, button)) continue;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int vehicleListContentHeight() {
        int rowCount = this.sortedEntries().size();
        if (rowCount <= 0) {
            return 0;
        }
        return (rowCount - 1) * 52 + 46;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= 18.0 && mouseX <= (double)(this.width - 18) && mouseY >= 38.0 && mouseY <= (double)(this.height - 36)) {
            return this.listScrollState.mouseScrolled(delta, this.height - 36 - 38, this.vehicleListContentHeight());
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 295) { BattlefieldUiTheme.cycle(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.resolveCloseTarget());
        }
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private Screen resolveCloseTarget() {
        Screen screen = this.parent;
        if (screen instanceof LoadoutConfigScreen) {
            LoadoutConfigScreen configScreen = (LoadoutConfigScreen)screen;
            return configScreen.parentScreen();
        }
        return this.parent;
    }

    private void renderVehicleRow(GuiGraphics guiGraphics, ClientLoadoutUiState.VehicleEntryState entry, GDButton rowButton, int mouseX, int mouseY, float partialTick, int rowY) {
        boolean selected;
        int rowLeft = 18;
        int rowRight = this.width - 18;
        int accentColor = this.resolveStateColor(entry);
        boolean selectedWaiting = this.isWaitingForQueuedDeploy(entry);
        boolean bl = selected = entry != null && entry.selected();
        int backgroundColor = selectedWaiting || selected ? BattlefieldUiTheme.palette().header() : BattlefieldUiTheme.palette().panel();
        guiGraphics.fill(rowLeft, rowY, rowRight, rowY + 46, backgroundColor);
        guiGraphics.fill(rowLeft, rowY, rowLeft + 4, rowY + 46, accentColor);
        if (selectedWaiting || selected) {
            int outlineColor = BattlefieldUiTheme.palette().accent();
            org.mods.gd656conquest.client.gui.BattlefieldRectStroke.hairline(guiGraphics, rowLeft, rowY, rowRight - rowLeft, 46, outlineColor);
        }
        String displayName = entry.displayName().isBlank() ? entry.entryId() : entry.displayName();
        BattlefieldUiTheme.drawText(guiGraphics, displayName, rowLeft + 10, rowY + 5, -1);
        BattlefieldUiTheme.drawText(guiGraphics, this.resolveDisplayedStatusText(entry), rowLeft + 10, rowY + 18, accentColor);
        BattlefieldUiTheme.drawText(guiGraphics, entry.detailText(), rowLeft + 10, rowY + 31, -7167825);
        if (entry.healthPercent() >= 0) {
            String healthText = entry.healthCurrent() > 0 && entry.healthMax() > 0 ? "\u8010\u4e45 " + entry.healthCurrent() + "/" + entry.healthMax() + "  (" + entry.healthPercent() + "%)" : "\u8010\u4e45 " + entry.healthPercent() + "%";
            BattlefieldUiTheme.drawText(guiGraphics, healthText, rowRight - 86 - 124, rowY + 8, entry.lowHealth() ? -6237968 : -6302807);
        }
        if (entry.seatCount() > 0) {
            String seatText = "\u5e2d\u4f4d " + entry.occupiedSeatCount() + "/" + entry.seatCount();
            BattlefieldUiTheme.drawText(guiGraphics, seatText, rowRight - 86 - 124, rowY + 22, -7551745);
        } else if (entry.occupiedSeatCount() > 0) {
            String crewText = "\u4e58\u5458 " + entry.occupiedSeatCount();
            BattlefieldUiTheme.drawText(guiGraphics, crewText, rowRight - 86 - 124, rowY + 22, -7551745);
        } else if (this.resolveDisplayedReadyRemainSeconds(entry) > 0) {
            String readyText = "\u5012\u8ba1\u65f6 " + this.resolveDisplayedReadyRemainSeconds(entry) + "\u79d2";
            BattlefieldUiTheme.drawText(guiGraphics, readyText, rowRight - 86 - 124, rowY + 22, -6237968);
        }
        if (rowButton != null) {
            rowButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void rebuildStaticButtons() {
        this.backButton = new GDButton(18, this.height - 28, 88, 20, Component.translatable("gd656conquest.client.gui.loadout.vehicle.back"), btn -> this.closeToParent());
        this.closeButton = new GDButton(this.width - 86, this.height - 28, 72, 20, Component.translatable("gd656conquest.client.gui.loadout.close"), btn -> this.onClose());
    }

    private void syncVehicleButtons() {
        List<ClientLoadoutUiState.VehicleEntryState> entries = this.sortedEntries();
        int buttonX = this.width - 18 - 86 - 8;
        this.pruneUnusedButtons(entries);
        for (int index = 0; index < entries.size(); ++index) {
            ClientLoadoutUiState.VehicleEntryState entry = entries.get(index);
            int rowY = 38 + index * 52 - (int)Math.round(this.listScrollState.scrollY());
            GDButton button = this.buttonByEntryId.computeIfAbsent(entry.entryId(), entryId -> new GDButton(buttonX, rowY + 13, 86, 20, Component.literal(entry.actionText()), btn -> this.requestVehicleDeployById((String)entryId)));
            button.setX(buttonX);
            button.setY(rowY + 13);
            button.setWidth(86);
            button.setHeight(20);
            button.setMessage(Component.literal(this.resolveButtonLabel(entry)));
            button.active = this.isButtonActive(entry);
            button.setTextColor(button.active ? -1 : -5592406);
        }
    }

    private void requestVehicleDeployById(String entryId) {
        if (entryId == null || entryId.isBlank()) {
            return;
        }
        ClientLoadoutUiState.VehicleEntryState entry = this.findEntry(entryId);
        if (entry == null) {
            return;
        }
        this.requestVehicleDeploy(entry);
    }

    private void requestVehicleDeploy(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (!ClientRoomLockState.shouldReleaseMouseForUi() || !entry.actionable() || entry.entryId().isBlank()) {
            return;
        }
        this.awaitingSuccessfulDeployClose = true;
        NetworkManager.getChannel().sendToServer(RoomDeploySelectPacket.confirmVehicle(entry.entryId()));
        if (!entry.immediateDeploy()) {
            this.requestSnapshotNow();
        }
        if (entry.immediateDeploy()) {
            this.closeToGameplay();
        }
    }

    private String currentEmptyMessageKey() {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) {
            return "gd656conquest.client.gui.loadout.loading";
        }
        if (!snapshot.vehiclePageAvailable()) {
            return "gd656conquest.client.gui.loadout.vehicle.unavailable";
        }
        return "gd656conquest.client.gui.loadout.vehicle.empty";
    }

    private int resolveStateColor(ClientLoadoutUiState.VehicleEntryState entry) {
        String token;
        if (entry == null) {
            return -1;
        }
        if (this.isWaitingForQueuedDeploy(entry)) {
            return -6642771;
        }
        if (entry.lowHealth()) {
            return -6237968;
        }
        return switch (token = entry.stateToken() == null ? "" : entry.stateToken().trim().toUpperCase()) {
            case "EMPTY" -> -3092272;
            case "SQUAD" -> -9576309;
            case "TEAM" -> -10246657;
            case "FULL", "ENEMY" -> -38037;
            case "WAIT", "WAITING" -> -6642771;
            default -> -1;
        };
    }

    private void requestSnapshotIfNeeded() {
        if (this.isOutOfGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastRequestAt < 1000L) {
            return;
        }
        this.lastRequestAt = now;
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));
    }

    private void pruneUnusedButtons(List<ClientLoadoutUiState.VehicleEntryState> visibleEntries) {
        HashSet<String> activeIds = new HashSet<String>();
        for (ClientLoadoutUiState.VehicleEntryState vehicleEntryState : visibleEntries) {
            activeIds.add(vehicleEntryState.entryId());
        }
        Iterator<Map.Entry<String, GDButton>> iterator = this.buttonByEntryId.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GDButton> entry = iterator.next();
            if (activeIds.contains(entry.getKey())) continue;
            iterator.remove();
        }
    }

    private boolean isButtonActive(ClientLoadoutUiState.VehicleEntryState entry) {
        if (!ClientRoomLockState.shouldReleaseMouseForUi() || entry == null || !entry.actionable()) {
            return false;
        }
        return !this.isWaitingForQueuedDeploy(entry);
    }

    private boolean isWaitingForQueuedDeploy(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null) {
            return false;
        }
        return this.isSelectedWaiting(entry);
    }

    private String resolveButtonLabel(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null) {
            return "";
        }
        return this.isWaitingForQueuedDeploy(entry) ? "\u7b49\u5f85\u90e8\u7f72" : entry.actionText();
    }

    private String resolveDisplayedStatusText(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null) {
            return "";
        }
        int remainSeconds = this.resolveDisplayedReadyRemainSeconds(entry);
        if (this.isSelectedWaiting(entry)) {
            return "\u5df2\u9009\u5b9a\u90e8\u7f72\uff0c\u7b49\u5f85\u5012\u8ba1\u65f6\u7ed3\u675f" + (String)(remainSeconds > 0 ? "\uff08\u7ea6 " + remainSeconds + " \u79d2\uff09" : "");
        }
        return entry.statusText();
    }

    private int resolveDisplayedReadyRemainSeconds(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null || entry.readyRemainSeconds() <= 0) {
            return 0;
        }
        long ageMs = ClientLoadoutUiState.snapshotAgeMs();
        if (ageMs == Long.MAX_VALUE) {
            return entry.readyRemainSeconds();
        }
        int elapsedSeconds = (int)(ageMs / 1000L);
        return Math.max(0, entry.readyRemainSeconds() - elapsedSeconds);
    }

    private boolean isSelectedWaiting(ClientLoadoutUiState.VehicleEntryState entry) {
        return entry != null && entry.selected() && this.resolveDisplayedReadyRemainSeconds(entry) > 0;
    }

    private void requestSnapshotNow() {
        this.lastRequestAt = System.currentTimeMillis();
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));
    }

    private ClientLoadoutUiState.VehicleEntryState findEntry(String entryId) {
        if (entryId == null || entryId.isBlank()) {
            return null;
        }
        for (ClientLoadoutUiState.VehicleEntryState entry : this.sortedEntries()) {
            if (!entryId.equalsIgnoreCase(entry.entryId())) continue;
            return entry;
        }
        return null;
    }

    private void closeToGameplay() {
        this.awaitingSuccessfulDeployClose = false;
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private List<ClientLoadoutUiState.VehicleEntryState> sortedEntries() {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) {
            this.cachedSortedEntriesUpdatedAtMs = Long.MIN_VALUE;
            this.cachedSortedEntries = List.of();
            return this.cachedSortedEntries;
        }
        long updatedAtMs = ClientLoadoutUiState.snapshotUpdatedAtMs();
        if (updatedAtMs != this.cachedSortedEntriesUpdatedAtMs) {
            this.cachedSortedEntries = snapshot.vehicleEntries().stream().sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName())).toList();
            this.cachedSortedEntriesUpdatedAtMs = updatedAtMs;
        }
        return this.cachedSortedEntries;
    }

    private boolean isOutOfGame() {
        Minecraft minecraft = this.minecraft;
        return minecraft == null || minecraft.player == null || minecraft.level == null;
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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

