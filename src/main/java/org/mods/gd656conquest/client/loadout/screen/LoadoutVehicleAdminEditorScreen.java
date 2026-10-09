package org.mods.gd656conquest.client.loadout.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.TextInputDialog;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.client.loadout.ClientVehicleAdminEditorState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.VehicleAdminEditorRequestPacket;
import org.mods.gd656conquest.network.packet.VehicleAdminEditorUpdatePacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LoadoutVehicleAdminEditorScreen extends Screen {
    private static final int PADDING = 18;
    private static final int TOP = 20;
    private static final int ROW_H = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ROW_GAP = 1;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private static final long SNAPSHOT_STALE_MS = 4000L;
    private static final int LEFT_ID_W = 42;
    private static final int LEFT_STATE_W = 32;
    private static final int LEFT_PLAYERS_W = 24;
    private static final int RIGHT_VALUE_W = 118;

    private final Screen parent;
    private final List<ClientVehicleAdminEditorState.RoomState> roomEntries = new ArrayList<>();
    private final List<GDRowRenderer> leftRows = new ArrayList<>();
    private final List<RightRow> rightRows = new ArrayList<>();
    private final List<GDRowRenderer> rightRowRenderers = new ArrayList<>();
    private final List<String> collapsedTeamKeys = new ArrayList<>();
    private final List<String> expandedVehicleKeys = new ArrayList<>();
    private GDRowRenderer leftHeader;
    private GDRowRenderer rightHeader;
    private GDButton backButton;
    private GDButton refreshButton;
    private TextInputDialog textInputDialog;
    private ChoiceListDialog choiceListDialog;

    private String selectedRoomId = "";
    private long lastRequestAtMs = 0L;
    private final GDScrollState leftScrollState = new GDScrollState();
    private final GDScrollState rightScrollState = new GDScrollState();

    public LoadoutVehicleAdminEditorScreen(Screen parent) {
        super(Component.literal("编辑房间载具数据"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.backButton = new GDButton(this.width - 86, this.height - 28, 72, 20, Component.literal("返回"), btn -> closeToParent());
        this.refreshButton = new GDButton(this.width - 166, this.height - 28, 72, 20, Component.literal("刷新"), btn -> requestSnapshot(false));
        this.textInputDialog = new TextInputDialog(minecraft, null, null);
        this.choiceListDialog = new ChoiceListDialog(minecraft);
        this.leftScrollState.reset();
        this.rightScrollState.reset();
        requestSnapshot(false);
        refreshFromState();
    }

    @Override
    public void tick() {
        super.tick();
        requestSnapshot(false);
        refreshFromState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            guiGraphics.drawCenteredString(this.font, "请在游戏内打开配置界面", this.width / 2, this.height / 2, 0xFFFF8080);
            return;
        }
        if (!minecraft.player.hasPermissions(2)) {
            guiGraphics.drawCenteredString(this.font, "仅管理员可编辑房间载具数据", this.width / 2, this.height / 2, 0xFFFF8080);
            return;
        }
        int baseMouseX = hasBlockingDialog() ? Integer.MIN_VALUE / 4 : mouseX;
        int baseMouseY = hasBlockingDialog() ? Integer.MIN_VALUE / 4 : mouseY;
        renderPanels(guiGraphics, baseMouseX, baseMouseY, partialTick);
        if (backButton != null) {
            backButton.setX(this.width - 86);
            backButton.setY(this.height - 28);
            backButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }
        if (refreshButton != null) {
            refreshButton.setX(this.width - 166);
            refreshButton.setY(this.height - 28);
            refreshButton.render(guiGraphics, baseMouseX, baseMouseY, partialTick);
        }
        if (choiceListDialog != null && choiceListDialog.isVisible()) {
            choiceListDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (textInputDialog != null && textInputDialog.isVisible()) {
            textInputDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean hasBlockingDialog() {
        return (choiceListDialog != null && choiceListDialog.isVisible())
                || (textInputDialog != null && textInputDialog.isVisible());
    }

    private void renderPanels(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int split = (this.width - PADDING * 3) / 2;
        int leftX1 = PADDING;
        int leftX2 = leftX1 + split;
        int rightX1 = leftX2 + PADDING;
        int rightX2 = this.width - PADDING;
        int contentY1 = TOP + ROW_H + 1;
        int contentY2 = this.height - 36;

        guiGraphics.drawString(this.font, "房间载具编辑器", PADDING, 6, GuiConstants.COLOR_TITLE_TEXT, false);
        ClientVehicleAdminEditorState.RoomState selected = getSelectedRoom();
        if (selected != null) {
            guiGraphics.drawString(
                this.font,
                "gameplay=" + safe(selected.gameplayId()) + " | backend=" + safe(selected.backendValue()),
                rightX1,
                6,
                0xFF9FD3FF,
                false
            );
        }

        if (leftHeader == null) {
            leftHeader = new GDRowRenderer(leftX1, TOP, leftX2, TOP + ROW_H, GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        }
        leftHeader.setBounds(leftX1, TOP, leftX2, TOP + ROW_H);
        leftHeader.resetColumnConfig();
        leftHeader.addColumn("房间ID", LEFT_ID_W, 0xFF9FD3FF, true, false, null);
        leftHeader.addColumn("房间显示名", -1, GuiConstants.COLOR_HEADER_TEXT, true, false, null);
        leftHeader.addColumn("状态", LEFT_STATE_W, 0xFFC8B6FF, true, true, null);
        leftHeader.addColumn("人数", LEFT_PLAYERS_W, 0xFFBDF5BD, false, true, null);
        leftHeader.render(guiGraphics, mouseX, mouseY, partialTick);

        renderLeftRows(guiGraphics, mouseX, mouseY, partialTick, leftX1, leftX2, contentY1, contentY2);

        if (rightHeader == null) {
            rightHeader = new GDRowRenderer(rightX1, TOP, rightX2, TOP + ROW_H, GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        }
        rightHeader.setBounds(rightX1, TOP, rightX2, TOP + ROW_H);
        rightHeader.resetColumnConfig();
        rightHeader.addColumn("配置项", -1, 0xFF9FD3FF, true, false, null);
        rightHeader.addColumn("值", RIGHT_VALUE_W, GuiConstants.COLOR_HEADER_TEXT, true, false, null);
        rightHeader.addColumn("状态", 50, 0xFFBDF5BD, false, true, null);
        rightHeader.render(guiGraphics, mouseX, mouseY, partialTick);

        renderRightRows(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, contentY1, contentY2);
    }

    private void renderLeftRows(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int x2, int y1, int y2) {
        int viewH = Math.max(1, y2 - y1);
        int contentH = roomEntries.size() * (ROW_H + ROW_GAP);
        leftScrollState.update(mouseY, viewH, contentH);
        guiGraphics.enableScissor(x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -leftScrollState.scrollY(), 0);
        for (int i = 0; i < roomEntries.size(); i++) {
            ClientVehicleAdminEditorState.RoomState room = roomEntries.get(i);
            while (leftRows.size() <= i) {
                leftRows.add(new GDRowRenderer(x1, y1, x2, y1 + ROW_H, GuiConstants.COLOR_BLACK & 0xFFFFFF, 0.26f, false));
            }
            int rowTop = y1 + i * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            if (rowBottom - leftScrollState.scrollY() <= y1 || rowTop - leftScrollState.scrollY() >= y2) {
                continue;
            }
            GDRowRenderer row = leftRows.get(i);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            row.setBackgroundColor((i % 2 == 0 ? 0x00161F2C : 0x0010181F) & 0xFFFFFF);
            row.setBackgroundAlpha(i % 2 == 0 ? 0.18f : 0.32f);
            row.addColumn(room.roomId(), LEFT_ID_W, room.roomId().equals(selectedRoomId) ? GuiConstants.COLOR_GOLD : GuiConstants.COLOR_SKY_BLUE, true, false, btn -> selectRoom(room.roomId()));
            row.addColumn(safe(room.displayName()), -1, 0xFFEDEDED, true, false, btn -> selectRoom(room.roomId()));
            row.addColumn(room.stateDisplayName(), LEFT_STATE_W, GuiConstants.COLOR_DATA_TEXT, true, true, btn -> selectRoom(room.roomId()));
            row.addColumn(room.currentPlayers() + "/" + room.maxPlayers(), LEFT_PLAYERS_W, 0xFFB6FFB6, false, true, null);
            row.render(guiGraphics, mouseX, (int) (mouseY + leftScrollState.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        leftScrollState.renderScrollbar(guiGraphics, x1, y1, x2, y2, contentH);
    }

    private void renderRightRows(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int x2, int y1, int y2) {
        if (getSelectedRoom() == null) {
            guiGraphics.drawString(this.font, "点击左侧房间ID查看载具数据", x1 + 4, y1 + 8, 0xFF9AA6B8, false);
            return;
        }
        int viewH = Math.max(1, y2 - y1);
        int contentH = rightRows.size() * (ROW_H + ROW_GAP);
        rightScrollState.update(mouseY, viewH, contentH);
        guiGraphics.enableScissor(x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -rightScrollState.scrollY(), 0);
        for (int i = 0; i < rightRows.size(); i++) {
            while (rightRowRenderers.size() <= i) {
                rightRowRenderers.add(new GDRowRenderer(x1, y1, x2, y1 + ROW_H, GuiConstants.COLOR_BLACK & 0xFFFFFF, 0.26f, false));
            }
            int rowTop = y1 + i * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            if (rowBottom - rightScrollState.scrollY() <= y1 || rowTop - rightScrollState.scrollY() >= y2) {
                continue;
            }
            RightRow rowData = rightRows.get(i);
            GDRowRenderer row = rightRowRenderers.get(i);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            if (rowData.header) {
                row.setBackgroundColor(GuiConstants.COLOR_BLACK & 0xFFFFFF);
                row.setBackgroundAlpha(0.75f);
                row.addColumn(" " + rowData.leftText, -1, GuiConstants.COLOR_GOLD, true, false, btn -> toggleTeam(rowData.teamKey));
                row.addColumn(rowData.middleText, RIGHT_VALUE_W, 0xFFBDF5BD, true, false, btn -> toggleTeam(rowData.teamKey));
                row.addColumn(isTeamExpanded(rowData.teamKey) ? "▼" : "▶", 20, GuiConstants.COLOR_GOLD, false, true, btn -> toggleTeam(rowData.teamKey));
            } else if (rowData.vehicleRow) {
                row.setBackgroundColor((i % 2 == 0 ? 0x001A2332 : 0x00111922) & 0xFFFFFF);
                row.setBackgroundAlpha(i % 2 == 0 ? 0.28f : 0.36f);
                row.addColumn(rowData.leftText, -1, GuiConstants.COLOR_DATA_TEXT, true, false, btn -> toggleVehicle(rowData.roomVehicleKey));
                row.addColumn(rowData.middleText, RIGHT_VALUE_W, 0xFFEDEDED, true, false, btn -> toggleVehicle(rowData.roomVehicleKey));
                row.addColumn(rowData.enabled ? "启用" : "停用", 50, rowData.enabled ? 0xFF8EF7A2 : 0xFFFF8787, false, true, null);
            } else {
                row.setBackgroundColor((i % 2 == 0 ? 0x00161F2C : 0x0010181F) & 0xFFFFFF);
                row.setBackgroundAlpha(i % 2 == 0 ? 0.18f : 0.30f);
                int statusColor = rowData.configured ? 0xFF8EF7A2 : 0xFFFF8787;
                row.addColumn(rowData.leftText, -1, resolveTypeColor(rowData), true, false, btn -> editField(rowData));
                row.addColumn(rowData.middleText, RIGHT_VALUE_W, 0xFFEDEDED, true, false, btn -> editField(rowData));
                row.addColumn(rowData.configured ? "已配置" : "缺失", 50, statusColor, false, true, null);
            }
            row.render(guiGraphics, mouseX, (int) (mouseY + rightScrollState.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        rightScrollState.renderScrollbar(guiGraphics, x1, y1, x2, y2, contentH);
    }

    private void selectRoom(String roomId) {
        if (roomId == null) {
            return;
        }
        selectedRoomId = roomId;
        rightScrollState.setScrollInstant(0.0D, Math.max(1, this.height - (TOP + ROW_H + 1) - 36), rightRows.size() * (ROW_H + ROW_GAP));
        rebuildRightRows();
    }

    private void refreshFromState() {
        ClientVehicleAdminEditorState.Snapshot snapshot = ClientVehicleAdminEditorState.snapshot();
        roomEntries.clear();
        if (snapshot == null || !snapshot.admin()) {
            rightRows.clear();
            return;
        }
        roomEntries.addAll(snapshot.sortedRooms());
        if ((selectedRoomId == null || selectedRoomId.isBlank()) && !roomEntries.isEmpty()) {
            selectedRoomId = roomEntries.get(0).roomId();
        } else if (getSelectedRoom() == null && !roomEntries.isEmpty()) {
            selectedRoomId = roomEntries.get(0).roomId();
        }
        rebuildRightRows();
    }

    private void rebuildRightRows() {
        rightRows.clear();
        ClientVehicleAdminEditorState.RoomState room = getSelectedRoom();
        if (room == null) {
            return;
        }
        addTeamSection(room, "campA", room.campADisplayName());
        addTeamSection(room, "campB", room.campBDisplayName());
    }

    private void addTeamSection(ClientVehicleAdminEditorState.RoomState room, String teamKey, String displayName) {
        List<ClientVehicleAdminEditorState.VehicleEntryState> teamVehicles = room.vehicleEntries().stream()
            .filter(entry -> teamKey.equalsIgnoreCase(safe(entry.team())))
            .sorted(Comparator.comparing(ClientVehicleAdminEditorState.VehicleEntryState::entryId, String.CASE_INSENSITIVE_ORDER))
            .toList();
        String headerLabel = teamKey + "[" + safe(displayName) + "]";
        rightRows.add(RightRow.header(teamKey, headerLabel, teamVehicles.size() + "辆"));
        if (!isTeamExpanded(teamKey)) {
            return;
        }
        for (ClientVehicleAdminEditorState.VehicleEntryState entry : teamVehicles) {
            String roomVehicleKey = buildRoomVehicleKey(room.roomId(), entry.entryId());
            String vehicleLabel = "  " + (isVehicleExpanded(roomVehicleKey) ? "▼ " : "▶ ") + entry.entryId();
            String summary = safe(entry.displayName()).isBlank()
                ? preview(entry.vehicleId())
                : preview(entry.displayName() + " | " + entry.vehicleId());
            rightRows.add(RightRow.vehicle(teamKey, roomVehicleKey, entry.entryId(), vehicleLabel, summary, entry.enabled()));
            if (!isVehicleExpanded(roomVehicleKey)) {
                continue;
            }
            for (ClientVehicleAdminEditorState.VehicleFieldState field : entry.fields()) {
                rightRows.add(RightRow.field(
                    teamKey,
                    roomVehicleKey,
                    entry.entryId(),
                    field.fieldKey(),
                    "    " + field.displayName(),
                    preview(field.value()),
                    field.value(),
                    field.configured(),
                    field.editable(),
                    field.valueType(),
                    field.suggestionValues(),
                    field.supportsQuotedValue()
                ));
            }
        }
    }

    private void editField(RightRow rowData) {
        if (rowData == null || rowData.header || rowData.vehicleRow || !rowData.editable) {
            return;
        }
        ClientVehicleAdminEditorState.RoomState room = getSelectedRoom();
        if (room == null) {
            return;
        }
        List<FixedChoiceConfigEntry.Choice> choices = resolveChoices(rowData, room);
        if (!choices.isEmpty() && choiceListDialog != null && shouldUseChoiceDialog(rowData)) {
            choiceListDialog.show(
                rowData.rawValue,
                "选择载具字段 [" + rowData.leftText.trim() + "]",
                choices,
                selected -> NetworkManager.getChannel().sendToServer(new VehicleAdminEditorUpdatePacket(room.roomId(), rowData.entryId, rowData.fieldKey, selected)),
                null
            );
            return;
        }
        if (textInputDialog == null) {
            return;
        }
        textInputDialog.show(
            rowData.rawValue,
            buildEditorTitle(rowData),
            value -> NetworkManager.getChannel().sendToServer(new VehicleAdminEditorUpdatePacket(room.roomId(), rowData.entryId, rowData.fieldKey, value)),
            buildEditorValidator(rowData)
        );
    }

    private List<FixedChoiceConfigEntry.Choice> resolveChoices(RightRow rowData, ClientVehicleAdminEditorState.RoomState room) {
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        if (rowData == null) {
            return choices;
        }
        if ("team".equalsIgnoreCase(rowData.fieldKey)) {
            choices.add(new FixedChoiceConfigEntry.Choice("campA", "campA / " + safe(room.campADisplayName())));
            choices.add(new FixedChoiceConfigEntry.Choice("campB", "campB / " + safe(room.campBDisplayName())));
            return choices;
        }
        if (rowData.suggestionValues != null && !rowData.suggestionValues.isEmpty()) {
            for (String suggestionValue : rowData.suggestionValues) {
                choices.add(new FixedChoiceConfigEntry.Choice(suggestionValue, suggestionValue));
            }
            return choices;
        }
        if ("BOOLEAN".equalsIgnoreCase(rowData.valueType)) {
            choices.add(new FixedChoiceConfigEntry.Choice("true", "是 / true"));
            choices.add(new FixedChoiceConfigEntry.Choice("false", "否 / false"));
        }
        return choices;
    }

    private boolean shouldUseChoiceDialog(RightRow rowData) {
        return rowData != null
            && ("BOOLEAN".equalsIgnoreCase(rowData.valueType) || "team".equalsIgnoreCase(rowData.fieldKey));
    }

    private String buildEditorTitle(RightRow rowData) {
        String base = "编辑载具字段 [" + rowData.leftText.trim() + "]";
        if (rowData == null) {
            return base;
        }
        return switch (rowData.valueType == null ? "" : rowData.valueType.toUpperCase(Locale.ROOT)) {
            case "NUMBER" -> base + " (数字)";
            case "OPTIONAL_NUMBER" -> base + " (数字，可留空)";
            case "BOOLEAN" -> base + " (布尔值 true/false)";
            default -> base;
        };
    }

    private java.util.function.Predicate<String> buildEditorValidator(RightRow rowData) {
        if (rowData == null) {
            return null;
        }
        return switch (rowData.valueType == null ? "" : rowData.valueType.toUpperCase(Locale.ROOT)) {
            case "NUMBER" -> value -> isValidNumberInput(value, false);
            case "OPTIONAL_NUMBER" -> value -> isValidNumberInput(value, true);
            default -> null;
        };
    }

    private boolean isValidNumberInput(String value, boolean allowBlank) {
        if (value == null || value.trim().isBlank()) {
            return allowBlank;
        }
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int resolveTypeColor(RightRow rowData) {
        if (rowData == null) {
            return 0xFF9FD3FF;
        }
        if ("BOOLEAN".equalsIgnoreCase(rowData.valueType)) {
            return 0xFF9FD3FF;
        }
        if ("NUMBER".equalsIgnoreCase(rowData.valueType) || "OPTIONAL_NUMBER".equalsIgnoreCase(rowData.valueType)) {
            return 0xFFBDF5BD;
        }
        if ("team".equalsIgnoreCase(rowData.fieldKey)) {
            return GuiConstants.COLOR_DATA_TEXT;
        }
        return rowData.editable ? 0xFFEDEDED : 0xFF7E8794;
    }

    private boolean isTeamExpanded(String teamKey) {
        return !collapsedTeamKeys.contains(normalizeKey(teamKey));
    }

    private void toggleTeam(String teamKey) {
        String key = normalizeKey(teamKey);
        if (key.isBlank()) {
            return;
        }
        if (collapsedTeamKeys.contains(key)) {
            collapsedTeamKeys.remove(key);
        } else {
            collapsedTeamKeys.add(key);
        }
        rebuildRightRows();
    }

    private boolean isVehicleExpanded(String vehicleKey) {
        return expandedVehicleKeys.contains(normalizeKey(vehicleKey));
    }

    private void toggleVehicle(String vehicleKey) {
        String key = normalizeKey(vehicleKey);
        if (key.isBlank()) {
            return;
        }
        if (expandedVehicleKeys.contains(key)) {
            expandedVehicleKeys.remove(key);
        } else {
            expandedVehicleKeys.add(key);
        }
        rebuildRightRows();
    }

    private String buildRoomVehicleKey(String roomId, String entryId) {
        return normalizeKey(roomId) + ":" + normalizeKey(entryId);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private ClientVehicleAdminEditorState.RoomState getSelectedRoom() {
        if (selectedRoomId == null || selectedRoomId.isBlank()) {
            return null;
        }
        for (ClientVehicleAdminEditorState.RoomState room : roomEntries) {
            if (selectedRoomId.equalsIgnoreCase(room.roomId())) {
                return room;
            }
        }
        return null;
    }

    private String preview(String value) {
        String text = safe(value);
        if (text.length() <= 80) {
            return text;
        }
        return text.substring(0, 80) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void requestSnapshot(boolean force) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!force && ClientVehicleAdminEditorState.hasFreshSnapshot(SNAPSHOT_STALE_MS)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastRequestAtMs < REQUEST_INTERVAL_MS) {
            return;
        }
        lastRequestAtMs = now;
        NetworkManager.getChannel().sendToServer(new VehicleAdminEditorRequestPacket(force));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (choiceListDialog != null && choiceListDialog.isVisible() && choiceListDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (textInputDialog != null && textInputDialog.isVisible() && textInputDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (refreshButton != null && refreshButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            int split = (this.width - PADDING * 3) / 2;
            int leftX1 = PADDING;
            int leftX2 = leftX1 + split;
            int rightX1 = leftX2 + PADDING;
            int rightX2 = this.width - PADDING;
            int contentY1 = TOP + ROW_H + 1;
            int contentY2 = this.height - 36;
            if (mouseX >= leftX1 && mouseX <= leftX2 && mouseY >= contentY1 && mouseY <= contentY2) {
                for (int i = 0; i < leftRows.size() && i < roomEntries.size(); i++) {
                    int rowTop = contentY1 + i * (ROW_H + ROW_GAP);
                    int rowBottom = rowTop + ROW_H;
                    double adjustedY = mouseY + leftScrollState.scrollY();
                    if (adjustedY >= rowTop && adjustedY <= rowBottom && leftRows.get(i).mouseClicked(mouseX, adjustedY, button)) {
                        return true;
                    }
                }
                leftScrollState.startDragging(mouseX, mouseY, leftX1, contentY1, leftX2, contentY2, roomEntries.size() * (ROW_H + ROW_GAP));
                return true;
            }
            if (mouseX >= rightX1 && mouseX <= rightX2 && mouseY >= contentY1 && mouseY <= contentY2) {
                for (int i = 0; i < rightRowRenderers.size() && i < rightRows.size(); i++) {
                    int rowTop = contentY1 + i * (ROW_H + ROW_GAP);
                    int rowBottom = rowTop + ROW_H;
                    double adjustedY = mouseY + rightScrollState.scrollY();
                    if (adjustedY >= rowTop && adjustedY <= rowBottom && rightRowRenderers.get(i).mouseClicked(mouseX, adjustedY, button)) {
                        return true;
                    }
                }
                rightScrollState.startDragging(mouseX, mouseY, rightX1, contentY1, rightX2, contentY2, rightRows.size() * (ROW_H + ROW_GAP));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        leftScrollState.stopDragging();
        rightScrollState.stopDragging();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (choiceListDialog != null && choiceListDialog.isVisible()) {
            return choiceListDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (textInputDialog != null && textInputDialog.isVisible()) {
            return textInputDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        int split = (this.width - PADDING * 3) / 2;
        int leftX1 = PADDING;
        int leftX2 = leftX1 + split;
        int rightX1 = leftX2 + PADDING;
        int rightX2 = this.width - PADDING;
        int contentY1 = TOP + ROW_H + 1;
        int contentY2 = this.height - 36;
        if (mouseX >= leftX1 && mouseX <= leftX2 && mouseY >= contentY1 && mouseY <= contentY2) {
            return leftScrollState.mouseScrolled(delta, contentY2 - contentY1, roomEntries.size() * (ROW_H + ROW_GAP));
        }
        if (mouseX >= rightX1 && mouseX <= rightX2 && mouseY >= contentY1 && mouseY <= contentY2) {
            return rightScrollState.mouseScrolled(delta, contentY2 - contentY1, rightRows.size() * (ROW_H + ROW_GAP));
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (choiceListDialog != null && choiceListDialog.isVisible() && choiceListDialog.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (textInputDialog != null && textInputDialog.isVisible() && textInputDialog.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (choiceListDialog != null && choiceListDialog.isVisible() && choiceListDialog.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (textInputDialog != null && textInputDialog.isVisible() && textInputDialog.charTyped(codePoint, modifiers)) {
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

    private record RightRow(
        boolean header,
        boolean vehicleRow,
        String teamKey,
        String roomVehicleKey,
        String entryId,
        String fieldKey,
        String leftText,
        String middleText,
        String rawValue,
        boolean configured,
        boolean editable,
        boolean enabled,
        String valueType,
        List<String> suggestionValues,
        boolean supportsQuotedValue
    ) {
        static RightRow header(String teamKey, String leftText, String middleText) {
            return new RightRow(true, false, teamKey, "", "", "", leftText, middleText, "", true, false, true, "", List.of(), false);
        }

        static RightRow vehicle(String teamKey, String roomVehicleKey, String entryId, String leftText, String middleText, boolean enabled) {
            return new RightRow(false, true, teamKey, roomVehicleKey, entryId, "", leftText, middleText, "", true, false, enabled, "", List.of(), false);
        }

        static RightRow field(
            String teamKey,
            String roomVehicleKey,
            String entryId,
            String fieldKey,
            String leftText,
            String middleText,
            String rawValue,
            boolean configured,
            boolean editable,
            String valueType,
            List<String> suggestionValues,
            boolean supportsQuotedValue
        ) {
            return new RightRow(
                false,
                false,
                teamKey,
                roomVehicleKey,
                entryId,
                fieldKey,
                leftText,
                middleText,
                rawValue,
                configured,
                editable,
                true,
                valueType,
                suggestionValues == null ? List.of() : List.copyOf(suggestionValues),
                supportsQuotedValue
            );
        }
    }
}
