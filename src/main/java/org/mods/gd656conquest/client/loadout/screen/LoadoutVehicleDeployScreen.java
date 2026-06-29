package org.mods.gd656conquest.client.loadout.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LoadoutVehicleDeployScreen extends Screen {
    private static final int LEFT = 18;
    private static final int TOP = 38;
    private static final int ROW_HEIGHT = 46;
    private static final int ROW_GAP = 6;
    private static final int BUTTON_WIDTH = 86;
    private static final long REQUEST_INTERVAL_MS = 1000L;

    private final Screen parent;
    private final Map<String, GDButton> buttonByEntryId = new LinkedHashMap<>();
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
        updateOutOfGameTimer();
        listScrollState.reset();
        rebuildStaticButtons();
        syncVehicleButtons();
        if (!isOutOfGame()) {
            requestSnapshotIfNeeded();
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateOutOfGameTimer();
        if (isOutOfGame() && outOfGameCloseAtMs > 0L && System.currentTimeMillis() >= outOfGameCloseAtMs) {
            onClose();
            return;
        }
        if (awaitingSuccessfulDeployClose
            && ClientDeathFlowState.hasActiveCameraOverride()
            && !ClientRoomLockState.shouldReleaseMouseForUi()) {
            closeToGameplay();
            return;
        }
        syncVehicleButtons();
        requestSnapshotIfNeeded();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        if (isOutOfGame()) {
            int remain = Math.max(0, (int) Math.ceil((outOfGameCloseAtMs - System.currentTimeMillis()) / 1000.0));
            guiGraphics.drawCenteredString(this.font, "请在游戏内打开载具部署页", this.width / 2, this.height / 2 - 10, 0xFFFF8080);
            guiGraphics.drawCenteredString(this.font, "将在 " + remain + " 秒后自动关闭", this.width / 2, this.height / 2 + 6, 0xFFB8C6D6);
            if (closeButton != null) {
                closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        guiGraphics.drawString(this.font, this.title, LEFT, 12, 0xFFFFFF, false);
        guiGraphics.drawString(
            this.font,
            Component.translatable(
                ClientRoomLockState.shouldReleaseMouseForUi()
                    ? "gd656conquest.client.gui.loadout.vehicle.deploy_enabled"
                    : "gd656conquest.client.gui.loadout.vehicle.deploy_disabled"
            ),
            LEFT,
            24,
            0xFFB8C6D6,
            false
        );

        int listY1 = TOP;
        int listY2 = this.height - 36;
        int viewHeight = Math.max(1, listY2 - listY1);
        int contentHeight = sortedEntries().size() * (ROW_HEIGHT + ROW_GAP);
        listScrollState.update(mouseY, viewHeight, contentHeight);

        List<ClientLoadoutUiState.VehicleEntryState> entries = sortedEntries();
        if (entries.isEmpty()) {
            String key = currentEmptyMessageKey();
            guiGraphics.drawString(this.font, Component.translatable(key), LEFT, TOP + 8, 0xFFB8C6D6, false);
        } else {
            guiGraphics.enableScissor(LEFT, listY1, this.width - 18, listY2);
            for (int index = 0; index < entries.size(); index++) {
                int rowY = TOP + index * (ROW_HEIGHT + ROW_GAP) - (int) Math.round(listScrollState.scrollY());
                if (rowY + ROW_HEIGHT <= listY1 || rowY >= listY2) {
                    continue;
                }
                renderVehicleRow(guiGraphics, entries.get(index), buttonByEntryId.get(entries.get(index).entryId()), mouseX, mouseY, partialTick, rowY);
            }
            guiGraphics.disableScissor();
            listScrollState.renderScrollbar(guiGraphics, LEFT, listY1, this.width - 18, listY2, contentHeight);
        }

        if (backButton != null) {
            backButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (closeButton != null) {
            closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int totalCount = sortedEntries().size();
        if (totalCount > 0 && contentHeight > viewHeight) {
            int stride = ROW_HEIGHT + ROW_GAP;
            int firstVisible = Math.min(totalCount, Math.max(1, (int) Math.floor(listScrollState.scrollY() / stride) + 1));
            int lastVisible = Math.min(totalCount, Math.max(firstVisible, (int) Math.ceil((listScrollState.scrollY() + viewHeight) / stride)));
            String pageText = String.format("%d-%d / %d", firstVisible, lastVisible, totalCount);
            guiGraphics.drawString(this.font, pageText, this.width - 84, 12, 0xFFB8C6D6, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (GDButton rowButton : buttonByEntryId.values()) {
                if (rowButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (mouseX >= LEFT && mouseX <= this.width - 18 && mouseY >= TOP && mouseY <= this.height - 36) {
                listScrollState.startDragging(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= LEFT && mouseX <= this.width - 18 && mouseY >= TOP && mouseY <= this.height - 36) {
            return listScrollState.mouseScrolled(delta, this.height - 36 - TOP, sortedEntries().size() * (ROW_HEIGHT + ROW_GAP));
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(resolveCloseTarget());
        }
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private Screen resolveCloseTarget() {
        if (parent instanceof LoadoutConfigScreen configScreen) {
            return configScreen.parentScreen();
        }
        return parent;
    }

    private void renderVehicleRow(
        GuiGraphics guiGraphics,
        ClientLoadoutUiState.VehicleEntryState entry,
        GDButton rowButton,
        int mouseX,
        int mouseY,
        float partialTick,
        int rowY
    ) {
        int rowLeft = LEFT;
        int rowRight = this.width - 18;
        int accentColor = resolveStateColor(entry);
        boolean selectedWaiting = isWaitingForQueuedDeploy(entry);
        boolean selected = entry != null && entry.selected();
        int backgroundColor = selectedWaiting
            ? 0x44304A66
            : selected
                ? 0x3A22374E
                : 0x33151A20;
        guiGraphics.fill(rowLeft, rowY, rowRight, rowY + ROW_HEIGHT, backgroundColor);
        guiGraphics.fill(rowLeft, rowY, rowLeft + 4, rowY + ROW_HEIGHT, accentColor);
        if (selectedWaiting || selected) {
            int outlineColor = selectedWaiting ? 0xCC8FC4FF : 0x88E6EEF8;
            guiGraphics.fill(rowLeft, rowY, rowRight, rowY + 1, outlineColor);
            guiGraphics.fill(rowLeft, rowY + ROW_HEIGHT - 1, rowRight, rowY + ROW_HEIGHT, outlineColor);
            guiGraphics.fill(rowLeft, rowY, rowLeft + 1, rowY + ROW_HEIGHT, outlineColor);
            guiGraphics.fill(rowRight - 1, rowY, rowRight, rowY + ROW_HEIGHT, outlineColor);
        }

        String displayName = entry.displayName().isBlank() ? entry.entryId() : entry.displayName();
        guiGraphics.drawString(this.font, displayName, rowLeft + 10, rowY + 5, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font, resolveDisplayedStatusText(entry), rowLeft + 10, rowY + 18, accentColor, false);
        guiGraphics.drawString(this.font, entry.detailText(), rowLeft + 10, rowY + 31, 0xFF92A0AF, false);

        if (entry.healthPercent() >= 0) {
            String healthText = entry.healthCurrent() > 0 && entry.healthMax() > 0
                ? "耐久 " + entry.healthCurrent() + "/" + entry.healthMax() + "  (" + entry.healthPercent() + "%)"
                : "耐久 " + entry.healthPercent() + "%";
            guiGraphics.drawString(this.font, healthText, rowRight - BUTTON_WIDTH - 124, rowY + 8, entry.lowHealth() ? 0xFFFFD85A : 0xFF9FD3A9, false);
        }
        if (entry.seatCount() > 0) {
            String seatText = "席位 " + entry.occupiedSeatCount() + "/" + entry.seatCount();
            guiGraphics.drawString(this.font, seatText, rowRight - BUTTON_WIDTH - 124, rowY + 22, 0xFF8CC4FF, false);
        } else if (entry.occupiedSeatCount() > 0) {
            String crewText = "乘员 " + entry.occupiedSeatCount();
            guiGraphics.drawString(this.font, crewText, rowRight - BUTTON_WIDTH - 124, rowY + 22, 0xFF8CC4FF, false);
        } else if (resolveDisplayedReadyRemainSeconds(entry) > 0) {
            String readyText = "倒计时 " + resolveDisplayedReadyRemainSeconds(entry) + "秒";
            guiGraphics.drawString(this.font, readyText, rowRight - BUTTON_WIDTH - 124, rowY + 22, 0xFFFFD85A, false);
        }
        if (rowButton != null) {
            rowButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void rebuildStaticButtons() {
        backButton = new GDButton(LEFT, this.height - 26, 88, 20, Component.translatable("gd656conquest.client.gui.loadout.vehicle.back"), btn -> closeToParent());
        closeButton = new GDButton(this.width - 86, this.height - 26, 72, 20, Component.translatable("gd656conquest.client.gui.loadout.close"), btn -> onClose());
    }

    private void syncVehicleButtons() {
        List<ClientLoadoutUiState.VehicleEntryState> entries = sortedEntries();
        int buttonX = this.width - 18 - BUTTON_WIDTH - 8;
        pruneUnusedButtons(entries);
        for (int index = 0; index < entries.size(); index++) {
            ClientLoadoutUiState.VehicleEntryState entry = entries.get(index);
            int rowY = TOP + index * (ROW_HEIGHT + ROW_GAP) - (int) Math.round(listScrollState.scrollY());
            GDButton button = buttonByEntryId.computeIfAbsent(
                entry.entryId(),
                entryId -> new GDButton(buttonX, rowY + 13, BUTTON_WIDTH, 20, Component.literal(entry.actionText()), btn -> requestVehicleDeployById(entryId))
            );
            button.setX(buttonX);
            button.setY(rowY + 13);
            button.setWidth(BUTTON_WIDTH);
            button.setHeight(20);
            button.setMessage(Component.literal(resolveButtonLabel(entry)));
            button.active = isButtonActive(entry);
            button.setTextColor(button.active ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
        }
    }

    private void requestVehicleDeployById(String entryId) {
        if (entryId == null || entryId.isBlank()) {
            return;
        }
        ClientLoadoutUiState.VehicleEntryState entry = findEntry(entryId);
        if (entry == null) {
            return;
        }
        requestVehicleDeploy(entry);
    }

    private void requestVehicleDeploy(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null || minecraft == null || minecraft.player == null) {
            return;
        }
        if (!ClientRoomLockState.shouldReleaseMouseForUi() || !entry.actionable() || entry.entryId().isBlank()) {
            return;
        }
        awaitingSuccessfulDeployClose = true;
        NetworkManager.getChannel().sendToServer(RoomDeploySelectPacket.confirmVehicle(entry.entryId()));
        if (!entry.immediateDeploy()) {
            requestSnapshotNow();
        }
        if (entry.immediateDeploy()) {
            closeToGameplay();
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
        if (entry == null) {
            return 0xFFFFFFFF;
        }
        if (isWaitingForQueuedDeploy(entry)) {
            return 0xFF9AA3AD;
        }
        if (entry.lowHealth()) {
            return 0xFFFFD85A;
        }
        String token = entry.stateToken() == null ? "" : entry.stateToken().trim().toUpperCase();
        return switch (token) {
            case "EMPTY" -> 0xFFD0D0D0;
            case "SQUAD" -> 0xFF6DE08B;
            case "TEAM" -> 0xFF63A5FF;
            case "FULL", "ENEMY" -> 0xFFFF6B6B;
            case "WAIT", "WAITING" -> 0xFF9AA3AD;
            default -> 0xFFFFFFFF;
        };
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

    private void pruneUnusedButtons(List<ClientLoadoutUiState.VehicleEntryState> visibleEntries) {
        Set<String> activeIds = new HashSet<>();
        for (ClientLoadoutUiState.VehicleEntryState entry : visibleEntries) {
            activeIds.add(entry.entryId());
        }
        Iterator<Map.Entry<String, GDButton>> iterator = buttonByEntryId.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GDButton> entry = iterator.next();
            if (!activeIds.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    private boolean isButtonActive(ClientLoadoutUiState.VehicleEntryState entry) {
        if (!ClientRoomLockState.shouldReleaseMouseForUi() || entry == null || !entry.actionable()) {
            return false;
        }
        return !isWaitingForQueuedDeploy(entry);
    }

    private boolean isWaitingForQueuedDeploy(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null) {
            return false;
        }
        return isSelectedWaiting(entry);
    }

    private String resolveButtonLabel(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null) {
            return "";
        }
        return isWaitingForQueuedDeploy(entry) ? "等待部署" : entry.actionText();
    }

    private String resolveDisplayedStatusText(ClientLoadoutUiState.VehicleEntryState entry) {
        if (entry == null) {
            return "";
        }
        int remainSeconds = resolveDisplayedReadyRemainSeconds(entry);
        if (isSelectedWaiting(entry)) {
            return "已选定部署，等待倒计时结束" + (remainSeconds > 0 ? "（约 " + remainSeconds + " 秒）" : "");
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
        int elapsedSeconds = (int) (ageMs / 1000L);
        return Math.max(0, entry.readyRemainSeconds() - elapsedSeconds);
    }

    private boolean isSelectedWaiting(ClientLoadoutUiState.VehicleEntryState entry) {
        return entry != null && entry.selected() && resolveDisplayedReadyRemainSeconds(entry) > 0;
    }

    private void requestSnapshotNow() {
        lastRequestAt = System.currentTimeMillis();
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));
    }

    private ClientLoadoutUiState.VehicleEntryState findEntry(String entryId) {
        if (entryId == null || entryId.isBlank()) {
            return null;
        }
        for (ClientLoadoutUiState.VehicleEntryState entry : sortedEntries()) {
            if (entryId.equalsIgnoreCase(entry.entryId())) {
                return entry;
            }
        }
        return null;
    }

    private void closeToGameplay() {
        awaitingSuccessfulDeployClose = false;
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private List<ClientLoadoutUiState.VehicleEntryState> sortedEntries() {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        if (snapshot == null) {
            cachedSortedEntriesUpdatedAtMs = Long.MIN_VALUE;
            cachedSortedEntries = List.of();
            return cachedSortedEntries;
        }
        long updatedAtMs = ClientLoadoutUiState.snapshotUpdatedAtMs();
        if (updatedAtMs != cachedSortedEntriesUpdatedAtMs) {
            cachedSortedEntries = snapshot.vehicleEntries().stream()
                .sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()))
                .toList();
            cachedSortedEntriesUpdatedAtMs = updatedAtMs;
        }
        return cachedSortedEntries;
    }

    private boolean isOutOfGame() {
        Minecraft minecraft = this.minecraft;
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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        listScrollState.stopDragging();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
