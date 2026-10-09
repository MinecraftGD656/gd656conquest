package org.mods.gd656conquest.client.loadout.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.ConfirmDialog;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.TextInputDialog;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.client.loadout.ClientRoomEditorState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomEditorRequestPacket;
import org.mods.gd656conquest.network.packet.RoomEditorUpdatePacket;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

public class LoadoutRoomEditorScreen extends Screen {
    private static final int PADDING = 18;
    private static final int TOP = 20;
    private static final int ROW_H = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ROW_GAP = 1;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private static final long SNAPSHOT_STALE_MS = 4000L;
    private static final int LEFT_ID_W = 42;
    private static final int LEFT_STATE_W = 32;
    private static final int LEFT_PLAYERS_W = 24;
    private static final int RIGHT_VALUE_W = 60;
    private static final int RIGHT_CONFIGURED_W = 50;
    private static final String GROUP_EDIT_HINT = "右键以编辑";

    private final Screen parent;
    private final List<ClientRoomEditorState.RoomState> roomEntries = new ArrayList<>();
    private final List<GDRowRenderer> leftRows = new ArrayList<>();
    private final List<RightRow> rightRows = new ArrayList<>();
    private final List<GDRowRenderer> rightRowRenderers = new ArrayList<>();
    private final List<String> expandedGroupKeys = new ArrayList<>();
    private GDRowRenderer leftHeader;
    private GDRowRenderer rightHeader;
    private GDButton backButton;
    private GDButton refreshButton;
    private TextInputDialog textInputDialog;
    private ChoiceListDialog choiceListDialog;
    private ConfirmDialog confirmDialog;

    private String selectedRoomId = "";
    private long lastRequestAtMs = 0L;
    private final GDScrollState leftScrollState = new GDScrollState();
    private final GDScrollState rightScrollState = new GDScrollState();

    public LoadoutRoomEditorScreen(Screen parent) {
        super(Component.literal("编辑房间数据"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.backButton = new GDButton(this.width - 86, this.height - 28, 72, 20, Component.literal("返回"), btn -> closeToParent());
        this.refreshButton = new GDButton(this.width - 166, this.height - 28, 72, 20, Component.literal("刷新"), btn -> requestSnapshot(false));
        this.textInputDialog = new TextInputDialog(minecraft, null, null);
        this.choiceListDialog = new ChoiceListDialog(minecraft);
        this.confirmDialog = new ConfirmDialog(minecraft, null, null);
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
            guiGraphics.drawCenteredString(this.font, "仅管理员可编辑房间数据", this.width / 2, this.height / 2, 0xFFFF8080);
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
        if (confirmDialog != null && confirmDialog.isVisible()) {
            confirmDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean hasBlockingDialog() {
        return (choiceListDialog != null && choiceListDialog.isVisible())
                || (textInputDialog != null && textInputDialog.isVisible())
                || (confirmDialog != null && confirmDialog.isVisible());
    }

    private void renderPanels(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int split = (this.width - PADDING * 3) / 2;
        int leftX1 = PADDING;
        int leftX2 = leftX1 + split;
        int rightX1 = leftX2 + PADDING;
        int rightX2 = this.width - PADDING;
        int contentY1 = TOP + ROW_H + 1;
        int contentY2 = this.height - 36;

        guiGraphics.drawString(this.font, "房间数据编辑器", PADDING, 6, GuiConstants.COLOR_TITLE_TEXT, false);
        ClientRoomEditorState.RoomState selected = getSelectedRoom();
        boolean hasMissingRequired = selected != null && selected.hasMissingRequirements();
        if (selected != null && selected.hasMissingRequirements()) {
            guiGraphics.drawString(this.font, "存在必填未配置项: " + selected.missingSummaryCount(), rightX1, 6, 0xFFFF7070, false);
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
        rightHeader.addColumn(hasMissingRequired ? "缺失" : "已配置", 50, hasMissingRequired ? 0xFFFF8787 : 0xFFBDF5BD, false, true, null);
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
            ClientRoomEditorState.RoomState room = roomEntries.get(i);
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
            row.addColumn(safe(room.displayName()), -1, 0xFFEDEDED, true, false, btn -> openRoomDisplayNameEditor(room));
            row.addColumn(room.stateDisplayName(), LEFT_STATE_W, GuiConstants.COLOR_DATA_TEXT, true, true, btn -> openRoomStateChoice(room));
            row.addColumn(room.currentPlayers() + "/" + room.maxPlayers(), LEFT_PLAYERS_W, 0xFFB6FFB6, false, true, null);
            row.render(guiGraphics, mouseX, (int) (mouseY + leftScrollState.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        leftScrollState.renderScrollbar(guiGraphics, x1, y1, x2, y2, contentH);
    }

    private void renderRightRows(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int x2, int y1, int y2) {
        ClientRoomEditorState.RoomState selected = getSelectedRoom();
        if (selected == null) {
            guiGraphics.drawString(this.font, "点击左侧房间ID查看配置", x1 + 4, y1 + 8, 0xFF9AA6B8, false);
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
                row.addColumn(" " + rowData.leftText, -1, GuiConstants.COLOR_GOLD, true, false, btn -> toggleCategory(rowData.categoryKey));
                row.addColumn(isCategoryExpanded(rowData.categoryKey) ? "▼" : "▶", 20, GuiConstants.COLOR_GOLD, true, true, btn -> toggleCategory(rowData.categoryKey));
            } else {
                row.setBackgroundColor((i % 2 == 0 ? 0x00161F2C : 0x0010181F) & 0xFFFFFF);
                row.setBackgroundAlpha(rowData.applicable ? (i % 2 == 0 ? 0.18f : 0.30f) : 0.10f);
                int cfgColor = rowData.applicable
                    ? (rowData.configured ? 0xFF8EF7A2 : 0xFFFF8787)
                    : 0xFF7E8794;
                row.addColumn(rowData.leftText, -1, resolveTypeColor(rowData), true, false, btn -> editConfigField(rowData, false, btn));
                row.addColumn(rowData.middleText, RIGHT_VALUE_W, rowData.applicable ? 0xFFEDEDED : 0xFF7E8794, true, false, btn -> editConfigField(rowData, true, btn));
                if (supportsQuickGroupEditor(rowData) && isValueColumnHovered(mouseX, (int) (mouseY + rightScrollState.scrollY()), x1, x2, rowTop, rowBottom)) {
                    row.addColumn(GROUP_EDIT_HINT, RIGHT_CONFIGURED_W, GuiConstants.COLOR_GOLD, false, true, null);
                } else {
                    row.addColumn(rowData.configured ? "已配置" : "缺失", RIGHT_CONFIGURED_W, cfgColor, false, true, null);
                }
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

    private void openRoomDisplayNameEditor(ClientRoomEditorState.RoomState room) {
        if (room == null || textInputDialog == null) {
            return;
        }
        textInputDialog.show(safe(room.displayName()), "编辑房间显示名 [" + room.roomId() + "]", value ->
                NetworkManager.getChannel().sendToServer(new RoomEditorUpdatePacket(room.roomId(), "room.displayName", value))
        );
    }

    private void openRoomStateChoice(ClientRoomEditorState.RoomState room) {
        if (room == null || choiceListDialog == null || confirmDialog == null) {
            return;
        }
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        for (RoomLifecycleState state : RoomLifecycleState.values()) {
            choices.add(new FixedChoiceConfigEntry.Choice(state.name(), resolveRoomStateDisplayName(state)));
        }
        choiceListDialog.show(
                room.stateKey(),
                "选择房间状态",
                choices,
                selected -> confirmDialog.show(
                        "将房间 " + room.roomId() + " 状态改为 " + selected + " ?",
                        ConfirmDialog.PromptType.WARNING,
                        () -> NetworkManager.getChannel().sendToServer(new RoomEditorUpdatePacket(room.roomId(), "room.state", selected))
                ),
                null
        );
    }

    private void editConfigField(RightRow rowData, boolean fromValueColumn, int mouseButton) {
        if (rowData == null || rowData.header || textInputDialog == null) {
            return;
        }
        ClientRoomEditorState.RoomState room = getSelectedRoom();
        if (room == null) {
            return;
        }
        if (rowData.groupParent) {
            if (fromValueColumn) {
                if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT && supportsQuickGroupEditor(rowData)) {
                    openGroupQuickEditor(room, rowData);
                } else {
                    toggleGroup(rowData.keyPath);
                }
            } else {
                openGroupParentEditor(room, rowData);
            }
            return;
        }
        if (rowData.groupChild) {
            if (!rowData.editable) {
                return;
            }
            editGroupChildField(room, rowData);
            return;
        }
        if (!rowData.editable) {
            return;
        }
        List<FixedChoiceConfigEntry.Choice> fixedChoices = resolveFixedChoices(rowData);
        if (shouldUseChoiceDialog(rowData, fixedChoices) && choiceListDialog != null) {
            choiceListDialog.show(
                    rowData.rawValue,
                    "选择配置值 [" + rowData.leftText.trim() + "]",
                    fixedChoices,
                    selected -> NetworkManager.getChannel().sendToServer(new RoomEditorUpdatePacket(room.roomId(), rowData.editKey, selected)),
                    null
            );
            return;
        }
        textInputDialog.show(
                rowData.rawValue,
                buildEditorTitle(rowData),
                value -> NetworkManager.getChannel().sendToServer(new RoomEditorUpdatePacket(room.roomId(), rowData.editKey, value)),
                buildEditorValidator(rowData)
        );
    }

    private void openGroupParentEditor(ClientRoomEditorState.RoomState room, RightRow rowData) {
        if (room == null || rowData == null || textInputDialog == null) {
            return;
        }
        textInputDialog.show(
                rowData.rawValue,
                "编辑数据组 [" + rowData.leftText.trim() + "]",
                value -> NetworkManager.getChannel().sendToServer(new RoomEditorUpdatePacket(room.roomId(), rowData.editKey, value)),
                buildGroupJsonValidator()
        );
    }

    private void openGroupQuickEditor(ClientRoomEditorState.RoomState room, RightRow rowData) {
        if (room == null || rowData == null || choiceListDialog == null) {
            return;
        }
        openGroupQuickEditor(room, rowData, new ArrayList<>(rowData.groupValues()));
    }

    private void openGroupQuickEditor(ClientRoomEditorState.RoomState room, RightRow rowData, List<String> enabledValues) {
        if (room == null || rowData == null || choiceListDialog == null) {
            return;
        }
        List<String> effectiveEnabledValues = enabledValues == null ? new ArrayList<>() : enabledValues;
        List<FixedChoiceConfigEntry.Choice> choices = buildQuickGroupChoices(rowData, effectiveEnabledValues);
        if (choices.isEmpty()) {
            return;
        }
        String selectedValue = effectiveEnabledValues.isEmpty() ? choices.get(0).value() : effectiveEnabledValues.get(0);
        updateGroupQuickEditorDialog(room, rowData, effectiveEnabledValues, selectedValue);
    }

    private void updateGroupQuickEditorDialog(
            ClientRoomEditorState.RoomState room,
            RightRow rowData,
            List<String> enabledValues,
            String selectedValue
    ) {
        if (room == null || rowData == null || choiceListDialog == null) {
            return;
        }
        List<FixedChoiceConfigEntry.Choice> choices = buildQuickGroupChoices(rowData, enabledValues);
        if (choices.isEmpty()) {
            return;
        }
        if (!choiceListDialog.isVisible()) {
            choiceListDialog.show(
                    selectedValue,
                    "快速编辑数据组 [" + rowData.leftText.trim() + "]",
                    choices,
                    selected -> enableGroupChoice(room, rowData, enabledValues, selected),
                    null,
                    choice -> containsIgnoreCase(enabledValues, choice.value()),
                    choice -> disableGroupChoice(room, rowData, enabledValues, choice.value()),
                    "禁用",
                    GuiConstants.COLOR_RED,
                    30,
                    null,
                    null,
                    choice -> !containsIgnoreCase(enabledValues, choice.value()),
                    this::resolveGroupChoiceIcon,
                    null,
                    choice -> containsIgnoreCase(enabledValues, choice.value()),
                    "启用"
            );
            return;
        }
        choiceListDialog.refreshContent(
                selectedValue,
                choices,
                selected -> enableGroupChoice(room, rowData, enabledValues, selected),
                choice -> containsIgnoreCase(enabledValues, choice.value()),
                choice -> disableGroupChoice(room, rowData, enabledValues, choice.value()),
                "禁用",
                GuiConstants.COLOR_RED,
                30,
                choice -> !containsIgnoreCase(enabledValues, choice.value()),
                this::resolveGroupChoiceIcon,
                choice -> containsIgnoreCase(enabledValues, choice.value()),
                "启用"
        );
    }

    private void enableGroupChoice(
            ClientRoomEditorState.RoomState room,
            RightRow rowData,
            List<String> enabledValues,
            String selected
    ) {
        if (!containsIgnoreCase(enabledValues, selected)) {
            enabledValues.add(selected);
        }
        NetworkManager.getChannel().sendToServer(RoomEditorUpdatePacket.groupAdd(room.roomId(), rowData.editKey, selected));
        updateGroupQuickEditorDialog(room, rowData, enabledValues, selected);
    }

    private void disableGroupChoice(
            ClientRoomEditorState.RoomState room,
            RightRow rowData,
            List<String> enabledValues,
            String selected
    ) {
        enabledValues.removeIf(value -> value.equalsIgnoreCase(selected));
        NetworkManager.getChannel().sendToServer(RoomEditorUpdatePacket.groupDelete(room.roomId(), rowData.editKey, selected));
        updateGroupQuickEditorDialog(room, rowData, enabledValues, selected);
    }

    private void refreshFromState() {
        ClientRoomEditorState.Snapshot snapshot = ClientRoomEditorState.snapshot();
        roomEntries.clear();
        if (snapshot == null || !snapshot.admin()) {
            rightRows.clear();
            return;
        }
        roomEntries.addAll(snapshot.sortedRooms());
        if (selectedRoomId == null || selectedRoomId.isBlank()) {
            if (!roomEntries.isEmpty()) {
                selectedRoomId = roomEntries.get(0).roomId();
            }
        } else if (getSelectedRoom() == null && !roomEntries.isEmpty()) {
            selectedRoomId = roomEntries.get(0).roomId();
        }
        rebuildRightRows();
    }

    private final List<String> collapsedCategories = new ArrayList<>();

    private void rebuildRightRows() {
        rightRows.clear();
        ClientRoomEditorState.RoomState selected = getSelectedRoom();
        if (selected == null) {
            return;
        }
        List<String> scopes = List.of("COMMON", "CONQUEST", "BREAKTHROUGH", "DEATHMATCH", "GUNMASTER");
        for (String scope : scopes) {
            rightRows.add(RightRow.header(scope, scope));
            if (!isCategoryExpanded(scope)) {
                continue;
            }
            List<ClientRoomEditorState.RoomConfigEntry> scopedEntries = selected.configEntries().stream()
                .filter(entry -> entry != null && scope.equalsIgnoreCase(safe(entry.scope())))
                .toList();
            for (ClientRoomEditorState.RoomConfigEntry entry : scopedEntries) {
                boolean group = entry.supportsGroupOperations() || isJsonGroup(entry.value());
                String rowLabel = "  [" + safe(entry.category()) + "] " + entry.displayName();
                rightRows.add(RightRow.config(
                    entry.keyPath(),
                    entry.editKey(),
                    rowLabel,
                    group ? resolveGroupValueCount(entry) + "个值" : preview(entry.value()),
                    entry.value(),
                    entry.configured(),
                    entry.required(),
                    entry.editable(),
                    scope,
                    entry.applicable(),
                    entry.highlighted(),
                    group,
                    entry.valueType(),
                    entry.suggestionValues(),
                    entry.supportsQuotedValue(),
                    entry.groupValues(),
                    entry.groupChoices()
                ));
                if (group && isGroupExpanded(entry.keyPath())) {
                    rightRows.addAll(buildGroupChildren(entry, scope));
                }
            }
        }
    }

    private boolean isCategoryExpanded(String category) {
        return !collapsedCategories.contains(category.toLowerCase(Locale.ROOT));
    }

    private void toggleCategory(String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        String key = category.toLowerCase(Locale.ROOT);
        if (collapsedCategories.contains(key)) {
            collapsedCategories.remove(key);
        } else {
            collapsedCategories.add(key);
        }
        rebuildRightRows();
    }

    private void toggleGroup(String keyPath) {
        if (keyPath == null || keyPath.isBlank()) {
            return;
        }
        String key = keyPath.toLowerCase(Locale.ROOT);
        if (expandedGroupKeys.contains(key)) {
            expandedGroupKeys.remove(key);
        } else {
            expandedGroupKeys.add(key);
        }
        rebuildRightRows();
    }

    private boolean isGroupExpanded(String keyPath) {
        return keyPath != null && expandedGroupKeys.contains(keyPath.toLowerCase(Locale.ROOT));
    }

    private boolean isJsonGroup(String rawValue) {
        JsonElement element = parseJson(rawValue);
        return element != null && (element.isJsonObject() || element.isJsonArray());
    }

    private int countGroupValues(String rawValue) {
        JsonElement element = parseJson(rawValue);
        if (element == null) {
            return 0;
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().entrySet().size();
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().size();
        }
        return 0;
    }

    private int resolveGroupValueCount(ClientRoomEditorState.RoomConfigEntry entry) {
        if (entry == null) {
            return 0;
        }
        if (entry.supportsGroupOperations()) {
            return entry.groupValues().size();
        }
        return countGroupValues(entry.value());
    }

    private List<RightRow> buildGroupChildren(ClientRoomEditorState.RoomConfigEntry entry, String category) {
        List<RightRow> children = new ArrayList<>();
        if (entry == null) {
            return children;
        }
        if (entry.supportsGroupOperations()) {
            List<String> values = entry.groupValues();
            for (int index = 0; index < values.size(); index++) {
                String value = safe(values.get(index));
                children.add(RightRow.groupChild(
                    entry.keyPath(),
                    entry.editKey(),
                    "",
                    category,
                    "    值 " + (index + 1),
                    preview(value),
                    value,
                    !value.isBlank(),
                    entry.required(),
                    false,
                    entry.applicable()
                ));
            }
            return children;
        }
        JsonElement element = parseJson(entry.value());
        if (element == null) {
            return children;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : object.keySet()) {
                JsonElement child = object.get(key);
                String childRaw = child == null ? "" : child.toString();
                children.add(RightRow.groupChild(entry.keyPath(), entry.editKey(), key, category, "    " + key, preview(childRaw), childRaw, !isJsonNullOrEmpty(child), entry.required(), true, entry.applicable()));
            }
            return children;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                String childRaw = child == null ? "" : child.toString();
                String childPath = "[" + i + "]";
                children.add(RightRow.groupChild(entry.keyPath(), entry.editKey(), childPath, category, "    " + childPath, preview(childRaw), childRaw, !isJsonNullOrEmpty(child), entry.required(), true, entry.applicable()));
            }
        }
        return children;
    }

    private boolean isJsonNullOrEmpty(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return true;
        }
        if (element.isJsonPrimitive()) {
            String text = element.getAsString();
            return text == null || text.isBlank();
        }
        return false;
    }

    private JsonElement parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (!(text.startsWith("{") || text.startsWith("["))) {
            return null;
        }
        try {
            return JsonParser.parseString(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void editGroupChildField(ClientRoomEditorState.RoomState room, RightRow rowData) {
        textInputDialog.show(stripJsonQuote(rowData.rawValue), "编辑数据组子项 [" + rowData.leftText.trim() + "]", value -> {
            String rebuilt = rebuildParentJsonValue(rowData.parentKeyPath, rowData.childPath, value);
            if (rebuilt == null) {
                return;
            }
            NetworkManager.getChannel().sendToServer(new RoomEditorUpdatePacket(room.roomId(), rowData.parentEditKey, rebuilt));
        });
    }

    private String rebuildParentJsonValue(String parentKeyPath, String childPath, String inputValue) {
        ClientRoomEditorState.RoomState room = getSelectedRoom();
        if (room == null) {
            return null;
        }
        ClientRoomEditorState.RoomConfigEntry parent = room.configEntries().stream()
                .filter(entry -> entry.keyPath().equals(parentKeyPath))
                .findFirst()
                .orElse(null);
        if (parent == null) {
            return null;
        }
        JsonElement root = parseJson(parent.value());
        if (root == null) {
            return null;
        }
        JsonElement valueElement = parseSmartValue(inputValue);
        if (root.isJsonObject()) {
            root.getAsJsonObject().add(childPath, valueElement);
            return root.toString();
        }
        if (root.isJsonArray() && childPath.startsWith("[") && childPath.endsWith("]")) {
            try {
                int index = Integer.parseInt(childPath.substring(1, childPath.length() - 1));
                JsonArray array = root.getAsJsonArray();
                if (index >= 0 && index < array.size()) {
                    array.set(index, valueElement);
                    return array.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private JsonElement parseSmartValue(String inputValue) {
        String text = inputValue == null ? "" : inputValue.trim();
        if (text.isEmpty()) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (text.startsWith("{") || text.startsWith("[") || text.startsWith("\"")) {
            try {
                return JsonParser.parseString(text);
            } catch (Exception ignored) {
            }
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return new JsonPrimitive(Boolean.parseBoolean(text));
        }
        try {
            if (text.contains(".")) {
                return new JsonPrimitive(Double.parseDouble(text));
            }
            return new JsonPrimitive(Long.parseLong(text));
        } catch (Exception ignored) {
        }
        return new JsonPrimitive(text);
    }

    private String stripJsonQuote(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private List<FixedChoiceConfigEntry.Choice> resolveFixedChoices(RightRow rowData) {
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        if (rowData == null) {
            return choices;
        }
        if (rowData.suggestionValues != null && !rowData.suggestionValues.isEmpty()) {
            for (String suggestionValue : rowData.suggestionValues) {
                choices.add(new FixedChoiceConfigEntry.Choice(suggestionValue, suggestionValue));
            }
            return choices;
        }
        String key = rowData.keyPath == null ? "" : rowData.keyPath.toLowerCase(Locale.ROOT);
        if (isBooleanField(rowData.valueType, key, rowData.rawValue)) {
            choices.add(new FixedChoiceConfigEntry.Choice("true", "是 / true"));
            choices.add(new FixedChoiceConfigEntry.Choice("false", "否 / false"));
            return choices;
        }
        return choices;
    }

    private List<FixedChoiceConfigEntry.Choice> buildQuickGroupChoices(RightRow rowData, List<String> enabledValues) {
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        if (rowData == null) {
            return choices;
        }
        if (rowData.groupChoices() != null && !rowData.groupChoices().isEmpty()) {
            for (ClientRoomEditorState.GroupChoice choice : rowData.groupChoices()) {
                if (choice == null || choice.value() == null || choice.value().isBlank()) {
                    continue;
                }
                choices.add(new FixedChoiceConfigEntry.Choice(
                        choice.value(),
                        choice.label() == null || choice.label().isBlank() ? choice.value() : choice.label(),
                        choice.secondaryLabel()
                ));
            }
        } else {
            choices.addAll(resolveFixedChoices(rowData));
        }
        return choices;
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private ChoiceListDialog.RowIcon resolveGroupChoiceIcon(FixedChoiceConfigEntry.Choice choice) {
        if (choice == null) {
            return null;
        }
        ClientRoomEditorState.RoomState room = getSelectedRoom();
        if (room == null) {
            return null;
        }
        for (ClientRoomEditorState.RoomConfigEntry entry : room.configEntries()) {
            if (entry == null || entry.groupChoices() == null) {
                continue;
            }
            for (ClientRoomEditorState.GroupChoice groupChoice : entry.groupChoices()) {
                if (groupChoice != null && choice.value().equalsIgnoreCase(groupChoice.value())) {
                    return buildItemIcon(groupChoice.itemSnbt());
                }
            }
        }
        return null;
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

    private boolean supportsQuickGroupEditor(RightRow rowData) {
        return rowData != null && rowData.groupParent && rowData.quickEditableGroup();
    }

    private boolean isValueColumnHovered(int mouseX, int mouseY, int rowX1, int rowX2, int rowY1, int rowY2) {
        if (mouseY < rowY1 || mouseY > rowY2) {
            return false;
        }
        int configuredStart = rowX2 - RIGHT_CONFIGURED_W;
        int valueStart = configuredStart - RIGHT_VALUE_W;
        return mouseX >= valueStart && mouseX < configuredStart;
    }

    private boolean shouldUseChoiceDialog(RightRow rowData, List<FixedChoiceConfigEntry.Choice> fixedChoices) {
        if (rowData == null || fixedChoices == null || fixedChoices.isEmpty()) {
            return false;
        }
        String valueType = rowData.valueType == null ? "" : rowData.valueType.trim().toUpperCase(Locale.ROOT);
        return "BOOLEAN".equals(valueType) || "STRING".equals(valueType);
    }

    private String buildEditorTitle(RightRow rowData) {
        String base = "编辑配置 [" + rowData.leftText.trim() + "]";
        if (rowData == null) {
            return base;
        }
        String valueType = rowData.valueType == null ? "" : rowData.valueType.trim().toUpperCase(Locale.ROOT);
        return switch (valueType) {
            case "NUMBER" -> base + " (数字)";
            case "BOOLEAN" -> base + " (布尔值 true/false)";
            case "POSITION" -> base + " (坐标 JSON)";
            case "VIEW_POSITION" -> base + " (朝向坐标 JSON)";
            case "POSITION_GROUP" -> base + " (坐标组 JSON)";
            case "DATA_GROUP" -> base + " (数据组 JSON)";
            case "STRING" -> rowData.supportsQuotedValue ? base + " (字符串，支持空格)" : base + " (字符串)";
            default -> base;
        };
    }

    private String resolveRoomStateDisplayName(RoomLifecycleState state) {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case DISABLED -> "禁用";
            case MAINTENANCE -> "维护中";
            case IDLE -> "空闲";
            case MATCHING -> "匹配中";
            case RUNNING -> "进行中";
            case ENDING -> "结算中";
            case SAVING -> "保存中";
            case CLEANING -> "清理中";
        };
    }

    private java.util.function.Predicate<String> buildEditorValidator(RightRow rowData) {
        if (rowData == null) {
            return null;
        }
        String valueType = rowData.valueType == null ? "" : rowData.valueType.trim().toUpperCase(Locale.ROOT);
        return switch (valueType) {
            case "NUMBER" -> value -> isValidNumberInput(value);
            case "BOOLEAN" -> value -> isValidBooleanInput(value);
            default -> null;
        };
    }

    private java.util.function.Predicate<String> buildGroupJsonValidator() {
        return value -> {
            if (value == null || value.trim().isBlank()) {
                return true;
            }
            String trimmed = value.trim();
            if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                return false;
            }
            try {
                JsonParser.parseString(trimmed);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        };
    }

    private boolean isValidNumberInput(String value) {
        if (value == null || value.trim().isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isValidBooleanInput(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed);
    }

    private boolean isBooleanField(String valueType, String key, String rawValue) {
        if ("BOOLEAN".equalsIgnoreCase(valueType)) {
            return true;
        }
        if (key.contains(".bool") || key.endsWith(".enabled") || key.contains(".enable_")) {
            return true;
        }
        if (rawValue == null) {
            return false;
        }
        return "true".equalsIgnoreCase(rawValue.trim()) || "false".equalsIgnoreCase(rawValue.trim());
    }

    private int resolveTypeColor(RightRow rowData) {
        if (rowData == null) {
            return 0xFF9FD3FF;
        }
        if (!rowData.applicable) {
            return 0xFF7E8794;
        }
        if (rowData.highlighted) {
            return GuiConstants.COLOR_DATA_TEXT;
        }
        if (rowData.groupParent || rowData.groupChild) {
            return 0xFF8FD7A8;
        }
        if ("BOOLEAN".equalsIgnoreCase(rowData.valueType)) {
            return 0xFF9FD3FF;
        }
        if ("NUMBER".equalsIgnoreCase(rowData.valueType)) {
            return 0xFFBDF5BD;
        }
        if ("POSITION".equalsIgnoreCase(rowData.valueType) || "VIEW_POSITION".equalsIgnoreCase(rowData.valueType)) {
            return GuiConstants.COLOR_DATA_TEXT;
        }
        if (!rowData.suggestionValues.isEmpty() || "room.state".equalsIgnoreCase(rowData.keyPath)) {
            return GuiConstants.COLOR_DATA_TEXT;
        }
        String value = rowData.rawValue == null ? "" : rowData.rawValue.trim();
        if ("POSITION_GROUP".equalsIgnoreCase(rowData.valueType) || "DATA_GROUP".equalsIgnoreCase(rowData.valueType) || isJsonGroup(value)) {
            return 0xFF8FD7A8;
        }
        return rowData.required ? 0xFFB0C0F0 : 0xFFEDEDED;
    }

    private boolean isNumericValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private ClientRoomEditorState.RoomState getSelectedRoom() {
        if (selectedRoomId == null || selectedRoomId.isBlank()) {
            return null;
        }
        for (ClientRoomEditorState.RoomState room : roomEntries) {
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
        if (!force && ClientRoomEditorState.hasFreshSnapshot(SNAPSHOT_STALE_MS)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastRequestAtMs < REQUEST_INTERVAL_MS) {
            return;
        }
        lastRequestAtMs = now;
        NetworkManager.getChannel().sendToServer(new RoomEditorRequestPacket(force));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmDialog != null && confirmDialog.isVisible() && confirmDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (choiceListDialog != null && choiceListDialog.isVisible() && choiceListDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (textInputDialog != null && textInputDialog.isVisible() && textInputDialog.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
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
                    if (adjustedY >= rowTop && adjustedY <= rowBottom) {
                        if (leftRows.get(i).mouseClicked(mouseX, adjustedY, button)) {
                            return true;
                        }
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
                    if (adjustedY >= rowTop && adjustedY <= rowBottom) {
                        if (rightRowRenderers.get(i).mouseClicked(mouseX, adjustedY, button)) {
                            return true;
                        }
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
        if (confirmDialog != null && confirmDialog.isVisible()) {
            return confirmDialog.mouseScrolled(mouseX, mouseY, delta);
        }
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
        if (confirmDialog != null && confirmDialog.isVisible() && confirmDialog.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
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
        if (confirmDialog != null && confirmDialog.isVisible() && confirmDialog.charTyped(codePoint, modifiers)) {
            return true;
        }
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
            String categoryKey,
            String keyPath,
            String editKey,
            String leftText,
            String middleText,
            String rawValue,
            boolean configured,
            boolean required,
            boolean editable,
            boolean applicable,
            boolean highlighted,
            boolean groupParent,
            boolean groupChild,
            String parentKeyPath,
            String parentEditKey,
            String childPath,
            String valueType,
            List<String> suggestionValues,
            boolean supportsQuotedValue,
            List<String> groupValues,
            List<ClientRoomEditorState.GroupChoice> groupChoices
    ) {
        static RightRow header(String categoryKey, String text) {
            return new RightRow(true, categoryKey, "", "", text, "", "", true, false, false, true, false, false, false, "", "", "", "", List.of(), false, List.of(), List.of());
        }

        static RightRow config(String keyPath, String editKey, String leftText, String middleText, String rawValue, boolean configured, boolean required, boolean editable, String category, boolean applicable, boolean highlighted, boolean groupParent, String valueType, List<String> suggestionValues, boolean supportsQuotedValue, List<String> groupValues, List<ClientRoomEditorState.GroupChoice> groupChoices) {
            String effectiveEditKey = editKey == null || editKey.isBlank() ? keyPath : editKey;
            return new RightRow(false, category, keyPath, effectiveEditKey, leftText, middleText, rawValue, configured, required, editable, applicable, highlighted, groupParent, false, keyPath, effectiveEditKey, "", valueType, suggestionValues == null ? List.of() : List.copyOf(suggestionValues), supportsQuotedValue, groupValues == null ? List.of() : List.copyOf(groupValues), groupChoices == null ? List.of() : List.copyOf(groupChoices));
        }

        static RightRow groupChild(String parentKeyPath, String parentEditKey, String childPath, String category, String leftText, String middleText, String rawValue, boolean configured, boolean required, boolean editable, boolean applicable) {
            String effectiveEditKey = parentEditKey == null || parentEditKey.isBlank() ? parentKeyPath : parentEditKey;
            return new RightRow(false, category, parentKeyPath, effectiveEditKey, leftText, middleText, rawValue, configured, required, editable, applicable, false, false, true, parentKeyPath, effectiveEditKey, childPath, "", List.of(), false, List.of(), List.of());
        }

        boolean quickEditableGroup() {
            if (!groupParent || groupChoices == null || groupChoices.isEmpty()) {
                return false;
            }
            String normalizedKey = editKey == null ? "" : editKey.trim().toLowerCase(Locale.ROOT);
            return normalizedKey.startsWith("campa")
                    || normalizedKey.startsWith("campb")
                    || "supportcampaherokits".equals(normalizedKey)
                    || "supportcampbherokits".equals(normalizedKey)
                    || "supportcampashellings".equals(normalizedKey)
                    || "supportcampbshellings".equals(normalizedKey)
                    || "gminitialweaponpool".equals(normalizedKey)
                    || "gmmiddleweaponpool".equals(normalizedKey)
                    || "gmfinalweaponpool".equals(normalizedKey);
        }
    }
}
