package org.mods.gd656conquest.client.gui.tabs;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.ConfirmDialog;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.TextInputDialog;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.client.loadout.ClientRoomEditorState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomEditorRequestPacket;
import org.mods.gd656conquest.network.packet.RoomEditorUpdatePacket;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tab content for editing room data (房间数据).
 * Adapted from LoadoutRoomEditorScreen.
 */
public class AdminRoomEditorTabContent implements ConfigScreenHeader.TabContent {

    private static final int PADDING = GuiConstants.SCREEN_EDGE_DISTANCE;
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

    private final Component title = Component.literal("房间数据");
    private final Minecraft minecraft;

    private final List<ClientRoomEditorState.RoomState> roomEntries = new ArrayList<>();
    private final List<GDRowRenderer> leftRows = new ArrayList<>();
    private final List<RightRow> rightRows = new ArrayList<>();
    private final List<GDRowRenderer> rightRowRenderers = new ArrayList<>();
    private final List<String> expandedGroupKeys = new ArrayList<>();
    private final List<String> collapsedCategories = new ArrayList<>();
    private GDRowRenderer leftHeader;
    private GDRowRenderer rightHeader;
    private TextInputDialog textInputDialog;
    private ChoiceListDialog choiceListDialog;
    private ConfirmDialog confirmDialog;
    private String selectedRoomId = "";
    private long lastRequestAtMs = 0L;
    private final GDScrollState leftScrollState = new GDScrollState();
    private final GDScrollState rightScrollState = new GDScrollState();

    public AdminRoomEditorTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public void onTabOpen() {
        leftScrollState.reset();
        rightScrollState.reset();
        requestSnapshot(false);
        refreshFromState();
    }

    @Override
    public void tick() {
        requestSnapshot(false);
        refreshFromState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, int contentTop) {
        initDialogsIfNeeded();
        int top = contentTop + 6;
        int baseMouseX = hasBlockingDialog() ? Integer.MIN_VALUE / 4 : mouseX;
        int baseMouseY = hasBlockingDialog() ? Integer.MIN_VALUE / 4 : mouseY;
        renderPanels(guiGraphics, baseMouseX, baseMouseY, partialTick, screenWidth, screenHeight, top);

        if (choiceListDialog != null && choiceListDialog.isVisible())
            choiceListDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        if (textInputDialog != null && textInputDialog.isVisible())
            textInputDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        if (confirmDialog != null && confirmDialog.isVisible())
            confirmDialog.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void initDialogsIfNeeded() {
        if (textInputDialog == null) textInputDialog = new TextInputDialog(minecraft, null, null);
        if (choiceListDialog == null) choiceListDialog = new ChoiceListDialog(minecraft);
        if (confirmDialog == null) confirmDialog = new ConfirmDialog(minecraft, null, null);
    }

    private boolean hasBlockingDialog() {
        return (choiceListDialog != null && choiceListDialog.isVisible())
                || (textInputDialog != null && textInputDialog.isVisible())
                || (confirmDialog != null && confirmDialog.isVisible());
    }

    private void renderPanels(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                              int screenWidth, int screenHeight, int top) {
        int split = (screenWidth - PADDING * 3) / 2;
        int leftX1 = PADDING;
        int leftX2 = leftX1 + split;
        int rightX1 = leftX2 + PADDING;
        int rightX2 = screenWidth - PADDING;
        int contentY1 = top + ROW_H + 1;
        int contentY2 = screenHeight - PADDING;

        guiGraphics.drawString(minecraft.font, "房间数据编辑器", PADDING, top - 10, GuiConstants.COLOR_TITLE_TEXT, false);
        ClientRoomEditorState.RoomState selected = getSelectedRoom();
        if (selected != null && selected.hasMissingRequirements()) {
            guiGraphics.drawString(minecraft.font, "存在必填未配置项: " + selected.missingSummaryCount(),
                    rightX1, top - 10, 0xFFFF7070, false);
        }

        if (leftHeader == null)
            leftHeader = new GDRowRenderer(leftX1, top, leftX2, top + ROW_H,
                    GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        leftHeader.setBounds(leftX1, top, leftX2, top + ROW_H);
        leftHeader.resetColumnConfig();
        leftHeader.addColumn("房间ID", LEFT_ID_W, 0xFF9FD3FF, true, false, null);
        leftHeader.addColumn("房间显示名", -1, GuiConstants.COLOR_HEADER_TEXT, true, false, null);
        leftHeader.addColumn("状态", LEFT_STATE_W, 0xFFC8B6FF, true, true, null);
        leftHeader.addColumn("人数", LEFT_PLAYERS_W, 0xFFBDF5BD, false, true, null);
        leftHeader.render(guiGraphics, mouseX, mouseY, partialTick);
        renderLeftRows(guiGraphics, mouseX, mouseY, partialTick, leftX1, leftX2, contentY1, contentY2);

        if (rightHeader == null)
            rightHeader = new GDRowRenderer(rightX1, top, rightX2, top + ROW_H,
                    GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        rightHeader.setBounds(rightX1, top, rightX2, top + ROW_H);
        rightHeader.resetColumnConfig();
        boolean hasMissing = selected != null && selected.hasMissingRequirements();
        rightHeader.addColumn("配置项", -1, 0xFF9FD3FF, true, false, null);
        rightHeader.addColumn("值", RIGHT_VALUE_W, GuiConstants.COLOR_HEADER_TEXT, true, false, null);
        rightHeader.addColumn(hasMissing ? "缺失" : "已配置", 50,
                hasMissing ? 0xFFFF8787 : 0xFFBDF5BD, false, true, null);
        rightHeader.render(guiGraphics, mouseX, mouseY, partialTick);
        renderRightRows(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, contentY1, contentY2);
    }

    private void renderLeftRows(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                int x1, int x2, int y1, int y2) {
        int viewH = Math.max(1, y2 - y1);
        int contentH = roomEntries.size() * (ROW_H + ROW_GAP);
        leftScrollState.update(mouseY, viewH, contentH);
        guiGraphics.enableScissor(x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -leftScrollState.scrollY(), 0);
        for (int i = 0; i < roomEntries.size(); i++) {
            ClientRoomEditorState.RoomState room = roomEntries.get(i);
            while (leftRows.size() <= i)
                leftRows.add(new GDRowRenderer(x1, y1, x2, y1 + ROW_H,
                        GuiConstants.COLOR_BLACK & 0xFFFFFF, 0.26f, false));
            int rowTop = y1 + i * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            if (rowBottom - leftScrollState.scrollY() <= y1 || rowTop - leftScrollState.scrollY() >= y2) continue;
            GDRowRenderer row = leftRows.get(i);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            row.setBackgroundColor((i % 2 == 0 ? 0x00161F2C : 0x0010181F) & 0xFFFFFF);
            row.setBackgroundAlpha(i % 2 == 0 ? 0.18f : 0.32f);
            row.addColumn(room.roomId(), LEFT_ID_W, room.roomId().equals(selectedRoomId) ? GuiConstants.COLOR_GOLD : GuiConstants.COLOR_SKY_BLUE,
                    true, false, btn -> selectRoom(room.roomId()));
            row.addColumn(safe(room.displayName()), -1, 0xFFEDEDED, true, false,
                    btn -> openRoomDisplayNameEditor(room));
            row.addColumn(room.stateDisplayName(), LEFT_STATE_W, GuiConstants.COLOR_DATA_TEXT, true, true,
                    btn -> openRoomStateChoice(room));
            row.addColumn(room.currentPlayers() + "/" + room.maxPlayers(), LEFT_PLAYERS_W, 0xFFB6FFB6, false, true, null);
            row.render(guiGraphics, mouseX, (int) (mouseY + leftScrollState.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        leftScrollState.renderScrollbar(guiGraphics, x1, y1, x2, y2, contentH);
    }

    private void renderRightRows(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                 int x1, int x2, int y1, int y2) {
        ClientRoomEditorState.RoomState selected = getSelectedRoom();
        if (selected == null) {
            guiGraphics.drawString(minecraft.font, "点击左侧房间ID查看配置", x1 + 4, y1 + 8, 0xFF9AA6B8, false);
            return;
        }
        int viewH = Math.max(1, y2 - y1);
        int contentH = rightRows.size() * (ROW_H + ROW_GAP);
        rightScrollState.update(mouseY, viewH, contentH);
        guiGraphics.enableScissor(x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -rightScrollState.scrollY(), 0);
        for (int i = 0; i < rightRows.size(); i++) {
            while (rightRowRenderers.size() <= i)
                rightRowRenderers.add(new GDRowRenderer(x1, y1, x2, y1 + ROW_H,
                        GuiConstants.COLOR_BLACK & 0xFFFFFF, 0.26f, false));
            int rowTop = y1 + i * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            if (rowBottom - rightScrollState.scrollY() <= y1 || rowTop - rightScrollState.scrollY() >= y2) continue;
            RightRow rowData = rightRows.get(i);
            GDRowRenderer row = rightRowRenderers.get(i);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            if (rowData.header) {
                row.setBackgroundColor(GuiConstants.COLOR_BLACK & 0xFFFFFF);
                row.setBackgroundAlpha(0.75f);
                row.addColumn(" " + rowData.leftText, -1, GuiConstants.COLOR_GOLD, true, false,
                        btn -> toggleCategory(rowData.categoryKey));
                row.addColumn(isCategoryExpanded(rowData.categoryKey) ? "\u25bc" : "\u25b6", 20,
                        GuiConstants.COLOR_GOLD, true, true, btn -> toggleCategory(rowData.categoryKey));
            } else {
                row.setBackgroundColor((i % 2 == 0 ? 0x00161F2C : 0x0010181F) & 0xFFFFFF);
                row.setBackgroundAlpha(rowData.applicable ? (i % 2 == 0 ? 0.18f : 0.30f) : 0.10f);
                int cfgColor = rowData.applicable
                        ? (rowData.configured ? 0xFF8EF7A2 : 0xFFFF8787) : 0xFF7E8794;
                row.addColumn(rowData.leftText, -1, resolveTypeColor(rowData), true, false,
                        btn -> editConfigField(rowData, false, btn));
                row.addColumn(rowData.middleText, RIGHT_VALUE_W,
                        rowData.applicable ? 0xFFEDEDED : 0xFF7E8794, true, false,
                        btn -> editConfigField(rowData, true, btn));
                if (supportsQuickGroupEditor(rowData)
                        && isValueColumnHovered(mouseX, (int) (mouseY + rightScrollState.scrollY()), x1, x2, rowTop, rowBottom)) {
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
        if (roomId == null) return;
        selectedRoomId = roomId;
        rebuildRightRows();
    }

    private void openRoomDisplayNameEditor(ClientRoomEditorState.RoomState room) {
        if (room == null || textInputDialog == null) return;
        textInputDialog.show(safe(room.displayName()), "编辑房间显示名 [" + room.roomId() + "]", value ->
                NetworkManager.getChannel().sendToServer(new RoomEditorUpdatePacket(room.roomId(), "room.displayName", value)));
    }

    private void openRoomStateChoice(ClientRoomEditorState.RoomState room) {
        if (room == null || choiceListDialog == null || confirmDialog == null) return;
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        for (RoomLifecycleState state : RoomLifecycleState.values())
            choices.add(new FixedChoiceConfigEntry.Choice(state.name(), resolveRoomStateDisplayName(state)));
        choiceListDialog.show(room.stateKey(), "选择房间状态", choices,
                selected -> confirmDialog.show("将房间 " + room.roomId() + " 状态改为 " + selected + " ?",
                        ConfirmDialog.PromptType.WARNING,
                        () -> NetworkManager.getChannel().sendToServer(
                                new RoomEditorUpdatePacket(room.roomId(), "room.state", selected))), null);
    }

    private void editConfigField(RightRow rowData, boolean fromValueColumn, int mouseButton) {
        if (rowData == null || rowData.header || textInputDialog == null) return;
        ClientRoomEditorState.RoomState room = getSelectedRoom();
        if (room == null) return;
        if (rowData.groupParent) {
            if (fromValueColumn) {
                if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT && supportsQuickGroupEditor(rowData))
                    openGroupQuickEditor(room, rowData);
                else toggleGroup(rowData.keyPath);
            } else {
                openGroupParentEditor(room, rowData);
            }
            return;
        }
        if (rowData.groupChild) {
            if (!rowData.editable) return;
            editGroupChildField(room, rowData);
            return;
        }
        if (!rowData.editable) return;
        List<FixedChoiceConfigEntry.Choice> fixedChoices = resolveFixedChoices(rowData);
        if (shouldUseChoiceDialog(rowData, fixedChoices) && choiceListDialog != null) {
            choiceListDialog.show(rowData.rawValue, "选择配置值 [" + rowData.leftText.trim() + "]",
                    fixedChoices,
                    selected -> NetworkManager.getChannel().sendToServer(
                            new RoomEditorUpdatePacket(room.roomId(), rowData.editKey, selected)), null);
            return;
        }
        textInputDialog.show(rowData.rawValue, buildEditorTitle(rowData),
                value -> NetworkManager.getChannel().sendToServer(
                        new RoomEditorUpdatePacket(room.roomId(), rowData.editKey, value)),
                buildEditorValidator(rowData));
    }

    private void openGroupParentEditor(ClientRoomEditorState.RoomState room, RightRow rowData) {
        if (room == null || rowData == null || textInputDialog == null) return;
        textInputDialog.show(rowData.rawValue, "编辑数据组 [" + rowData.leftText.trim() + "]",
                value -> NetworkManager.getChannel().sendToServer(
                        new RoomEditorUpdatePacket(room.roomId(), rowData.editKey, value)),
                buildGroupJsonValidator());
    }

    private void openGroupQuickEditor(ClientRoomEditorState.RoomState room, RightRow rowData) {
        if (room == null || rowData == null || choiceListDialog == null) return;
        openGroupQuickEditor(room, rowData, new ArrayList<>(rowData.groupValues));
    }

    private void openGroupQuickEditor(ClientRoomEditorState.RoomState room, RightRow rowData, List<String> enabledValues) {
        if (room == null || rowData == null || choiceListDialog == null) return;
        List<String> effectiveEnabled = enabledValues == null ? new ArrayList<>() : enabledValues;
        List<FixedChoiceConfigEntry.Choice> choices = buildQuickGroupChoices(rowData, effectiveEnabled);
        if (choices.isEmpty()) return;
        String selected = effectiveEnabled.isEmpty() ? choices.get(0).value() : effectiveEnabled.get(0);
        choiceListDialog.show(selected, "快速编辑数据组 [" + rowData.leftText.trim() + "]", choices,
                sel -> enableGroupChoice(room, rowData, effectiveEnabled, sel),
                null,
                choice -> containsIgnoreCase(effectiveEnabled, choice.value()),
                choice -> disableGroupChoice(room, rowData, effectiveEnabled, choice.value()),
                "禁用", GuiConstants.COLOR_RED, 30,
                null, null,
                choice -> !containsIgnoreCase(effectiveEnabled, choice.value()),
                this::resolveGroupChoiceIcon,
                null,
                choice -> containsIgnoreCase(effectiveEnabled, choice.value()), "启用");
    }

    private void enableGroupChoice(ClientRoomEditorState.RoomState room, RightRow rowData,
                                   List<String> enabledValues, String selected) {
        if (!containsIgnoreCase(enabledValues, selected)) enabledValues.add(selected);
        NetworkManager.getChannel().sendToServer(RoomEditorUpdatePacket.groupAdd(room.roomId(), rowData.editKey, selected));
        openGroupQuickEditor(room, rowData, enabledValues);
    }

    private void disableGroupChoice(ClientRoomEditorState.RoomState room, RightRow rowData,
                                    List<String> enabledValues, String selected) {
        enabledValues.removeIf(v -> v.equalsIgnoreCase(selected));
        NetworkManager.getChannel().sendToServer(RoomEditorUpdatePacket.groupDelete(room.roomId(), rowData.editKey, selected));
        openGroupQuickEditor(room, rowData, enabledValues);
    }

    private void editGroupChildField(ClientRoomEditorState.RoomState room, RightRow rowData) {
        textInputDialog.show(stripJsonQuote(rowData.rawValue), "编辑数据组子项 [" + rowData.leftText.trim() + "]", value -> {
            String rebuilt = rebuildParentJsonValue(room, rowData.parentKeyPath, rowData.childPath, value);
            if (rebuilt != null)
                NetworkManager.getChannel().sendToServer(
                        new RoomEditorUpdatePacket(room.roomId(), rowData.parentEditKey, rebuilt));
        });
    }

    // ---------- State management ----------

    private void refreshFromState() {
        ClientRoomEditorState.Snapshot snapshot = ClientRoomEditorState.snapshot();
        roomEntries.clear();
        if (snapshot == null || !snapshot.admin()) { rightRows.clear(); return; }
        roomEntries.addAll(snapshot.sortedRooms());
        if (selectedRoomId == null || selectedRoomId.isBlank()) {
            if (!roomEntries.isEmpty()) selectedRoomId = roomEntries.get(0).roomId();
        } else if (getSelectedRoom() == null && !roomEntries.isEmpty()) {
            selectedRoomId = roomEntries.get(0).roomId();
        }
        rebuildRightRows();
    }

    private void rebuildRightRows() {
        rightRows.clear();
        ClientRoomEditorState.RoomState selected = getSelectedRoom();
        if (selected == null) return;
        List<String> scopes = List.of("COMMON", "CONQUEST", "BREAKTHROUGH", "DEATHMATCH", "GUNMASTER", "RUSH");
        for (String scope : scopes) {
            rightRows.add(RightRow.header(scope, scope));
            if (!isCategoryExpanded(scope)) continue;
            List<ClientRoomEditorState.RoomConfigEntry> scoped = selected.configEntries().stream()
                    .filter(e -> e != null && scope.equalsIgnoreCase(safe(e.scope()))).toList();
            for (ClientRoomEditorState.RoomConfigEntry entry : scoped) {
                boolean group = entry.supportsGroupOperations() || isJsonGroup(entry.value());
                rightRows.add(RightRow.config(entry.keyPath(), entry.editKey(),
                        "  [" + safe(entry.category()) + "] " + entry.displayName(),
                        group ? resolveGroupValueCount(entry) + "个值" : preview(entry.value()),
                        entry.value(), entry.configured(), entry.required(), entry.editable(), scope,
                        entry.applicable(), entry.highlighted(), group, entry.valueType(),
                        entry.suggestionValues(), entry.supportsQuotedValue(), entry.groupValues(), entry.groupChoices()));
                if (group && isGroupExpanded(entry.keyPath()))
                    rightRows.addAll(buildGroupChildren(entry, scope));
            }
        }
    }

    private List<RightRow> buildGroupChildren(ClientRoomEditorState.RoomConfigEntry entry, String category) {
        List<RightRow> children = new ArrayList<>();
        if (entry == null) return children;
        if (entry.supportsGroupOperations()) {
            List<String> values = entry.groupValues();
            for (int i = 0; i < values.size(); i++) {
                String v = safe(values.get(i));
                children.add(RightRow.groupChild(entry.keyPath(), entry.editKey(), "", category,
                        "    值 " + (i + 1), preview(v), v, !v.isBlank(), entry.required(), false, entry.applicable()));
            }
            return children;
        }
        JsonElement root = parseJson(entry.value());
        if (root == null) return children;
        if (root.isJsonObject()) {
            for (String key : root.getAsJsonObject().keySet()) {
                JsonElement child = root.getAsJsonObject().get(key);
                String raw = child == null ? "" : child.toString();
                children.add(RightRow.groupChild(entry.keyPath(), entry.editKey(), key, category, "    " + key,
                        preview(raw), raw, !isJsonNullOrEmpty(child), entry.required(), true, entry.applicable()));
            }
        } else if (root.isJsonArray()) {
            JsonArray arr = root.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement child = arr.get(i);
                String raw = child == null ? "" : child.toString();
                children.add(RightRow.groupChild(entry.keyPath(), entry.editKey(), "[" + i + "]", category,
                        "    [" + i + "]", preview(raw), raw, !isJsonNullOrEmpty(child), entry.required(), true, entry.applicable()));
            }
        }
        return children;
    }

    private String rebuildParentJsonValue(ClientRoomEditorState.RoomState room, String parentKeyPath,
                                          String childPath, String inputValue) {
        if (room == null) return null;
        ClientRoomEditorState.RoomConfigEntry parent = room.configEntries().stream()
                .filter(e -> e.keyPath().equals(parentKeyPath)).findFirst().orElse(null);
        if (parent == null) return null;
        JsonElement root = parseJson(parent.value());
        if (root == null) return null;
        JsonElement valueElement = parseSmartValue(inputValue);
        if (root.isJsonObject()) { root.getAsJsonObject().add(childPath, valueElement); return root.toString(); }
        if (root.isJsonArray() && childPath.startsWith("[") && childPath.endsWith("]")) {
            try {
                int idx = Integer.parseInt(childPath.substring(1, childPath.length() - 1));
                JsonArray arr = root.getAsJsonArray();
                if (idx >= 0 && idx < arr.size()) { arr.set(idx, valueElement); return arr.toString(); }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ---------- Helpers ----------

    private boolean isCategoryExpanded(String cat) {
        return !collapsedCategories.contains(cat.toLowerCase(Locale.ROOT));
    }
    private void toggleCategory(String cat) {
        if (cat == null || cat.isBlank()) return;
        String key = cat.toLowerCase(Locale.ROOT);
        if (collapsedCategories.contains(key)) collapsedCategories.remove(key);
        else collapsedCategories.add(key);
        rebuildRightRows();
    }
    private void toggleGroup(String keyPath) {
        if (keyPath == null || keyPath.isBlank()) return;
        String key = keyPath.toLowerCase(Locale.ROOT);
        if (expandedGroupKeys.contains(key)) expandedGroupKeys.remove(key);
        else expandedGroupKeys.add(key);
        rebuildRightRows();
    }
    private boolean isGroupExpanded(String keyPath) {
        return keyPath != null && expandedGroupKeys.contains(keyPath.toLowerCase(Locale.ROOT));
    }

    private ClientRoomEditorState.RoomState getSelectedRoom() {
        if (selectedRoomId == null || selectedRoomId.isBlank()) return null;
        return roomEntries.stream().filter(r -> selectedRoomId.equalsIgnoreCase(r.roomId())).findFirst().orElse(null);
    }

    private void requestSnapshot(boolean force) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
        if (!force && ClientRoomEditorState.hasFreshSnapshot(SNAPSHOT_STALE_MS)) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRequestAtMs < REQUEST_INTERVAL_MS) return;
        lastRequestAtMs = now;
        NetworkManager.getChannel().sendToServer(new RoomEditorRequestPacket(force));
    }

    // ---------- Static helpers ----------

    private static String resolveRoomStateDisplayName(RoomLifecycleState state) {
        return switch (state) {
            case DISABLED -> "禁用"; case MAINTENANCE -> "维护中"; case IDLE -> "空闲";
            case MATCHING -> "匹配中"; case RUNNING -> "进行中"; case ENDING -> "结算中";
            case SAVING -> "保存中"; case CLEANING -> "清理中";
        };
    }

    private static String safe(String v) { return v == null ? "" : v; }
    private static String preview(String v) {
        String t = safe(v);
        return t.length() <= 80 ? t : t.substring(0, 80) + "...";
    }
    private static boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) return false;
        return values.stream().anyMatch(v -> v != null && v.equalsIgnoreCase(target));
    }

    private boolean isJsonGroup(String rawValue) {
        JsonElement e = parseJson(rawValue);
        return e != null && (e.isJsonObject() || e.isJsonArray());
    }
    private int countGroupValues(String rawValue) {
        JsonElement e = parseJson(rawValue);
        if (e == null) return 0;
        if (e.isJsonObject()) return e.getAsJsonObject().entrySet().size();
        if (e.isJsonArray()) return e.getAsJsonArray().size();
        return 0;
    }
    private int resolveGroupValueCount(ClientRoomEditorState.RoomConfigEntry entry) {
        if (entry == null) return 0;
        return entry.supportsGroupOperations() ? entry.groupValues().size() : countGroupValues(entry.value());
    }

    private static JsonElement parseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        if (!(t.startsWith("{") || t.startsWith("["))) return null;
        try { return JsonParser.parseString(t); } catch (Exception ignored) { return null; }
    }
    private static boolean isJsonNullOrEmpty(JsonElement e) {
        if (e == null || e.isJsonNull()) return true;
        if (e.isJsonPrimitive()) { String t = e.getAsString(); return t == null || t.isBlank(); }
        return false;
    }
    private static JsonElement parseSmartValue(String input) {
        String t = input == null ? "" : input.trim();
        if (t.isEmpty()) return JsonNull.INSTANCE;
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("\"")) {
            try { return JsonParser.parseString(t); } catch (Exception ignored) {}
        }
        if ("true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t)) return new JsonPrimitive(Boolean.parseBoolean(t));
        try {
            if (t.contains(".")) return new JsonPrimitive(Double.parseDouble(t));
            return new JsonPrimitive(Long.parseLong(t));
        } catch (Exception ignored) {}
        return new JsonPrimitive(t);
    }
    private static String stripJsonQuote(String raw) {
        if (raw == null) return "";
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) return raw.substring(1, raw.length() - 1);
        return raw;
    }

    private List<FixedChoiceConfigEntry.Choice> resolveFixedChoices(RightRow rowData) {
        List<FixedChoiceConfigEntry.Choice> c = new ArrayList<>();
        if (rowData == null) return c;
        if (rowData.suggestionValues != null && !rowData.suggestionValues.isEmpty()) {
            for (String sv : rowData.suggestionValues) c.add(new FixedChoiceConfigEntry.Choice(sv, sv));
            return c;
        }
        String key = rowData.keyPath == null ? "" : rowData.keyPath.toLowerCase(Locale.ROOT);
        if (isBooleanField(rowData.valueType, key, rowData.rawValue)) {
            c.add(new FixedChoiceConfigEntry.Choice("true", "是 / true"));
            c.add(new FixedChoiceConfigEntry.Choice("false", "否 / false"));
        }
        return c;
    }

    private List<FixedChoiceConfigEntry.Choice> buildQuickGroupChoices(RightRow rowData, List<String> enabledValues) {
        List<FixedChoiceConfigEntry.Choice> c = new ArrayList<>();
        if (rowData == null) return c;
        if (rowData.groupChoices != null && !rowData.groupChoices.isEmpty()) {
            for (ClientRoomEditorState.GroupChoice gc : rowData.groupChoices) {
                if (gc == null || gc.value() == null || gc.value().isBlank()) continue;
                c.add(new FixedChoiceConfigEntry.Choice(gc.value(),
                        gc.label() == null || gc.label().isBlank() ? gc.value() : gc.label(), gc.secondaryLabel()));
            }
        } else c.addAll(resolveFixedChoices(rowData));
        return c;
    }

    private ChoiceListDialog.RowIcon resolveGroupChoiceIcon(FixedChoiceConfigEntry.Choice choice) {
        if (choice == null) return null;
        ClientRoomEditorState.RoomState room = getSelectedRoom();
        if (room == null) return null;
        for (ClientRoomEditorState.RoomConfigEntry e : room.configEntries()) {
            if (e == null || e.groupChoices() == null) continue;
            for (ClientRoomEditorState.GroupChoice gc : e.groupChoices()) {
                if (gc != null && choice.value().equalsIgnoreCase(gc.value())) return buildItemIcon(gc.itemSnbt());
            }
        }
        return null;
    }

    private ChoiceListDialog.RowIcon buildItemIcon(String snbt) {
        if (snbt == null || snbt.isBlank()) return null;
        ItemStack stack = ClientLoadoutUiState.parseAndCacheStack(snbt);
        return stack.isEmpty() ? null : ChoiceListDialog.RowIcon.item(stack);
    }

    private boolean supportsQuickGroupEditor(RightRow rowData) {
        return rowData != null && rowData.groupParent && rowData.quickEditableGroup();
    }

    private boolean isValueColumnHovered(int mX, int mY, int rx1, int rx2, int ry1, int ry2) {
        if (mY < ry1 || mY > ry2) return false;
        int cs = rx2 - RIGHT_CONFIGURED_W, vs = cs - RIGHT_VALUE_W;
        return mX >= vs && mX < cs;
    }

    private boolean shouldUseChoiceDialog(RightRow rowData, List<FixedChoiceConfigEntry.Choice> fixedChoices) {
        if (rowData == null || fixedChoices == null || fixedChoices.isEmpty()) return false;
        String vt = rowData.valueType == null ? "" : rowData.valueType.trim().toUpperCase(Locale.ROOT);
        return "BOOLEAN".equals(vt) || "STRING".equals(vt);
    }

    private String buildEditorTitle(RightRow rowData) {
        String base = "编辑配置 [" + rowData.leftText.trim() + "]";
        if (rowData == null) return base;
        String vt = rowData.valueType == null ? "" : rowData.valueType.trim().toUpperCase(Locale.ROOT);
        return switch (vt) {
            case "NUMBER" -> base + " (数字)"; case "BOOLEAN" -> base + " (布尔值 true/false)";
            case "POSITION" -> base + " (坐标 JSON)"; case "VIEW_POSITION" -> base + " (朝向坐标 JSON)";
            case "POSITION_GROUP" -> base + " (坐标组 JSON)"; case "DATA_GROUP" -> base + " (数据组 JSON)";
            case "STRING" -> rowData.supportsQuotedValue ? base + " (字符串，支持空格)" : base + " (字符串)";
            default -> base;
        };
    }

    private java.util.function.Predicate<String> buildEditorValidator(RightRow rowData) {
        if (rowData == null) return null;
        String vt = rowData.valueType == null ? "" : rowData.valueType.trim().toUpperCase(Locale.ROOT);
        return switch (vt) {
            case "NUMBER" -> v -> { try { Double.parseDouble(v.trim()); return true; } catch (Exception ignored) { return false; }};
            case "BOOLEAN" -> v -> { String t = v.trim(); return "true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t); };
            default -> null;
        };
    }

    private java.util.function.Predicate<String> buildGroupJsonValidator() {
        return v -> {
            if (v == null || v.trim().isBlank()) return true;
            String t = v.trim();
            if (!(t.startsWith("{") || t.startsWith("["))) return false;
            try { JsonParser.parseString(t); return true; } catch (Exception ignored) { return false; }
        };
    }

    private boolean isBooleanField(String valueType, String key, String rawValue) {
        if ("BOOLEAN".equalsIgnoreCase(valueType)) return true;
        if (key.contains(".bool") || key.endsWith(".enabled") || key.contains(".enable_")) return true;
        if (rawValue == null) return false;
        return "true".equalsIgnoreCase(rawValue.trim()) || "false".equalsIgnoreCase(rawValue.trim());
    }

    private int resolveTypeColor(RightRow rowData) {
        if (rowData == null) return 0xFF9FD3FF;
        if (!rowData.applicable) return 0xFF7E8794;
        if (rowData.highlighted) return GuiConstants.COLOR_DATA_TEXT;
        if (rowData.groupParent || rowData.groupChild) return 0xFF8FD7A8;
        if ("BOOLEAN".equalsIgnoreCase(rowData.valueType)) return 0xFF9FD3FF;
        if ("NUMBER".equalsIgnoreCase(rowData.valueType)) return 0xFFBDF5BD;
        if ("POSITION".equalsIgnoreCase(rowData.valueType) || "VIEW_POSITION".equalsIgnoreCase(rowData.valueType)) return GuiConstants.COLOR_DATA_TEXT;
        if (!rowData.suggestionValues.isEmpty() || "room.state".equalsIgnoreCase(rowData.keyPath)) return GuiConstants.COLOR_DATA_TEXT;
        String v = rowData.rawValue == null ? "" : rowData.rawValue.trim();
        if ("POSITION_GROUP".equalsIgnoreCase(rowData.valueType) || "DATA_GROUP".equalsIgnoreCase(rowData.valueType) || isJsonGroup(v))
            return 0xFF8FD7A8;
        return rowData.required ? 0xFFB0C0F0 : 0xFFEDEDED;
    }

    // ---------- Event handlers ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        initDialogsIfNeeded();
        if (confirmDialog.isVisible() && confirmDialog.mouseClicked(mouseX, mouseY, button)) return true;
        if (choiceListDialog.isVisible() && choiceListDialog.mouseClicked(mouseX, mouseY, button)) return true;
        if (textInputDialog.isVisible() && textInputDialog.mouseClicked(mouseX, mouseY, button)) return true;
        return false;
    }

    public boolean mouseClickedContent(double mouseX, double mouseY, int button,
                                        int screenWidth, int screenHeight, int contentTop) {
        if (button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int top = contentTop + 6;
            int split = (screenWidth - PADDING * 3) / 2;
            int lx1 = PADDING, lx2 = lx1 + split;
            int rx1 = lx2 + PADDING, rx2 = screenWidth - PADDING;
            int cy1 = top + ROW_H + 1, cy2 = screenHeight - PADDING;
            if (mouseX >= lx1 && mouseX <= lx2 && mouseY >= cy1 && mouseY <= cy2) {
                for (int i = 0; i < leftRows.size() && i < roomEntries.size(); i++) {
                    int rt = cy1 + i * (ROW_H + ROW_GAP), rb = rt + ROW_H;
                    double ay = mouseY + leftScrollState.scrollY();
                    if (ay >= rt && ay <= rb && leftRows.get(i).mouseClicked(mouseX, ay, button)) return true;
                }
                leftScrollState.startDragging(mouseX, mouseY, lx1, cy1, lx2, cy2, roomEntries.size() * (ROW_H + ROW_GAP));
                return true;
            }
            if (mouseX >= rx1 && mouseX <= rx2 && mouseY >= cy1 && mouseY <= cy2) {
                for (int i = 0; i < rightRowRenderers.size() && i < rightRows.size(); i++) {
                    int rt = cy1 + i * (ROW_H + ROW_GAP), rb = rt + ROW_H;
                    double ay = mouseY + rightScrollState.scrollY();
                    if (ay >= rt && ay <= rb && rightRowRenderers.get(i).mouseClicked(mouseX, ay, button)) return true;
                }
                rightScrollState.startDragging(mouseX, mouseY, rx1, cy1, rx2, cy2, rightRows.size() * (ROW_H + ROW_GAP));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        leftScrollState.stopDragging();
        rightScrollState.stopDragging();
        return false;
    }

    public boolean mouseScrolledContent(double mouseX, double mouseY, double delta,
                                         int screenWidth, int screenHeight, int contentTop) {
        if (confirmDialog != null && confirmDialog.isVisible()) return confirmDialog.mouseScrolled(mouseX, mouseY, delta);
        if (choiceListDialog != null && choiceListDialog.isVisible()) return choiceListDialog.mouseScrolled(mouseX, mouseY, delta);
        if (textInputDialog != null && textInputDialog.isVisible()) return textInputDialog.mouseScrolled(mouseX, mouseY, delta);
        int top = contentTop + 6;
        int split = (screenWidth - PADDING * 3) / 2;
        int lx1 = PADDING, lx2 = lx1 + split;
        int rx1 = lx2 + PADDING, rx2 = screenWidth - PADDING;
        int cy1 = top + ROW_H + 1, cy2 = screenHeight - PADDING;
        if (mouseX >= lx1 && mouseX <= lx2 && mouseY >= cy1 && mouseY <= cy2)
            return leftScrollState.mouseScrolled(delta, cy2 - cy1, roomEntries.size() * (ROW_H + ROW_GAP));
        if (mouseX >= rx1 && mouseX <= rx2 && mouseY >= cy1 && mouseY <= cy2)
            return rightScrollState.mouseScrolled(delta, cy2 - cy1, rightRows.size() * (ROW_H + ROW_GAP));
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) { return false; }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmDialog != null && confirmDialog.isVisible()) return confirmDialog.keyPressed(keyCode, scanCode, modifiers);
        if (choiceListDialog != null && choiceListDialog.isVisible()) return choiceListDialog.keyPressed(keyCode, scanCode, modifiers);
        if (textInputDialog != null && textInputDialog.isVisible()) return textInputDialog.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (confirmDialog != null && confirmDialog.isVisible()) return confirmDialog.charTyped(codePoint, modifiers);
        if (choiceListDialog != null && choiceListDialog.isVisible()) return choiceListDialog.charTyped(codePoint, modifiers);
        if (textInputDialog != null && textInputDialog.isVisible()) return textInputDialog.charTyped(codePoint, modifiers);
        return false;
    }

    // ---------- RightRow data class ----------

    private record RightRow(
            boolean header, String categoryKey,
            String keyPath, String editKey,
            String leftText, String middleText, String rawValue,
            boolean configured, boolean required, boolean editable,
            boolean applicable, boolean highlighted,
            boolean groupParent, boolean groupChild,
            String parentKeyPath, String parentEditKey, String childPath,
            String valueType,
            List<String> suggestionValues, boolean supportsQuotedValue,
            List<String> groupValues,
            List<ClientRoomEditorState.GroupChoice> groupChoices
    ) {
        static RightRow header(String categoryKey, String text) {
            return new RightRow(true, categoryKey, "", "", text, "", "", true, false, false, true, false, false, false, "", "", "", "", List.of(), false, List.of(), List.of());
        }

        static RightRow config(String kp, String ek, String lt, String mt, String rv,
                               boolean cf, boolean rq, boolean ed, String cat, boolean ap,
                               boolean hl, boolean gp, String vt, List<String> sv,
                               boolean sqv, List<String> gv, List<ClientRoomEditorState.GroupChoice> gc) {
            String eEk = ek == null || ek.isBlank() ? kp : ek;
            return new RightRow(false, cat, kp, eEk, lt, mt, rv, cf, rq, ed, ap, hl, gp, false, kp, eEk, "",
                    vt, sv == null ? List.of() : List.copyOf(sv), sqv,
                    gv == null ? List.of() : List.copyOf(gv),
                    gc == null ? List.of() : List.copyOf(gc));
        }

        static RightRow groupChild(String ppk, String pek, String cp, String cat,
                                   String lt, String mt, String rv, boolean cf, boolean rq,
                                   boolean ed, boolean ap) {
            String eEk = pek == null || pek.isBlank() ? ppk : pek;
            return new RightRow(false, cat, ppk, eEk, lt, mt, rv, cf, rq, ed, ap, false, false, true,
                    ppk, eEk, cp, "", List.of(), false, List.of(), List.of());
        }

        boolean quickEditableGroup() {
            if (!groupParent || groupChoices == null || groupChoices.isEmpty()) return false;
            String nk = editKey == null ? "" : editKey.trim().toLowerCase(Locale.ROOT);
            return nk.startsWith("campa") || nk.startsWith("campb")
                    || "supportcampaherokits".equals(nk) || "supportcampbherokits".equals(nk)
                    || "supportcampashellings".equals(nk) || "supportcampbshellings".equals(nk)
                    || "gminitialweaponpool".equals(nk) || "gmmiddleweaponpool".equals(nk)
                    || "gmfinalweaponpool".equals(nk);
        }
    }
}
