package org.mods.gd656conquest.client.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;
import org.mods.gd656conquest.client.loadout.state.ClientRoomSelectionState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomSelectionRequestPacket;

import java.util.*;

/**
 * Tab content for room selection / server browser (房间选择).
 * Available to all players (not just admins).
 */
public class RoomSelectionTabContent implements ConfigScreenHeader.TabContent {

    private static final int PADDING = GuiConstants.SCREEN_EDGE_DISTANCE;
    private static final int ROW_H = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ROW_GAP = 1;
    private static final int BTN_H = 20;
    private static final long REQUEST_INTERVAL_MS = 1200L;

    private static final List<GameplayGroup> GAMEPLAY_GROUPS = List.of(
        new GameplayGroup("conquest", "征服-Conquest"),
        new GameplayGroup("breakthrough", "突破-Breakthrough"),
        new GameplayGroup("gunmaster", "枪神-Gunmaster"),
        new GameplayGroup("deathmatch", "团队死斗-TDM"),
        new GameplayGroup("rush", "突袭-Rush")
    );

    record GameplayGroup(String gameplayId, String displayName) {}

    private final Component title = Component.literal("房间选择");
    private final Minecraft minecraft;

    private final Set<String> collapsedGroups = new HashSet<>();
    private String selectedRoomId;
    private final GDScrollState leftScrollState = new GDScrollState();
    private final List<GDRowRenderer> leftRowRenderers = new ArrayList<>();
    private GDButton joinButton;
    private GDTextRenderer titleRenderer;
    private GDTextRenderer infoRenderer;
    private GDRowRenderer leftHeader;

    private long lastRequestAt;
    private final List<LeftRowEntry> leftRowEntries = new ArrayList<>();
    private int storedViewportH;
    private int storedContentH;

    private static final class LeftRowEntry {
        final boolean isGroupHeader;
        final String gameplayId;
        final String displayName;
        final String roomId;
        final String roomDisplayName;
        final int currentPlayers;
        final int maxPlayers;
        final String lifecycleState;
        final boolean isJoinable;

        private LeftRowEntry(String gameplayId, String displayName) {
            this.isGroupHeader = true;
            this.gameplayId = gameplayId;
            this.displayName = displayName;
            this.roomId = null;
            this.roomDisplayName = null;
            this.currentPlayers = 0;
            this.maxPlayers = 0;
            this.lifecycleState = null;
            this.isJoinable = false;
        }

        private LeftRowEntry(String roomId, String roomDisplayName, int currentPlayers, int maxPlayers, String gameplayId, String lifecycleState, boolean isAdmin) {
            this.isGroupHeader = false;
            this.gameplayId = gameplayId;
            this.displayName = null;
            this.roomId = roomId;
            this.roomDisplayName = roomDisplayName;
            this.currentPlayers = currentPlayers;
            this.maxPlayers = maxPlayers;
            this.lifecycleState = lifecycleState;
            this.isJoinable = "RUNNING".equals(lifecycleState) || "MATCHING".equals(lifecycleState) || "IDLE".equals(lifecycleState)
                || ("MAINTENANCE".equals(lifecycleState) && isAdmin);
        }
    }

    public RoomSelectionTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public void onTabOpen() {
        requestSnapshot();
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastRequestAt >= REQUEST_INTERVAL_MS) {
            requestSnapshot();
        }
    }

    private void requestSnapshot() {
        if (minecraft == null || minecraft.player == null) return;
        lastRequestAt = System.currentTimeMillis();
        NetworkManager.getChannel().sendToServer(new RoomSelectionRequestPacket());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, int contentTop) {
        ClientRoomSelectionState.Snapshot snapshot = ClientRoomSelectionState.snapshot();
        rebuildLeftRows(snapshot);

        int split = (screenWidth - PADDING * 3) / 2;
        int leftX1 = PADDING;
        int leftX2 = leftX1 + split;
        int rightX1 = leftX2 + PADDING;
        int rightX2 = screenWidth - PADDING;

        int top = contentTop + 6;
        guiGraphics.drawString(minecraft.font, "房间选择", PADDING, top - 10, GuiConstants.COLOR_TITLE_TEXT, false);

        renderLeftPanel(guiGraphics, mouseX, mouseY, partialTick, leftX1, leftX2, top, screenHeight);
        renderRightPanel(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, top, screenHeight);

        if (joinButton != null) {
            joinButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    // ==================== Left Panel ====================

    private void rebuildLeftRows(ClientRoomSelectionState.Snapshot snapshot) {
        leftRowEntries.clear();
        boolean isAdmin = minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
        Map<String, List<ClientRoomSelectionState.Entry>> grouped = new LinkedHashMap<>();
        for (GameplayGroup g : GAMEPLAY_GROUPS) {
            grouped.put(g.gameplayId, new ArrayList<>());
        }
        if (snapshot != null) {
            for (ClientRoomSelectionState.Entry e : snapshot.entries()) {
                List<ClientRoomSelectionState.Entry> list = grouped.get(e.gameplayId());
                if (list != null) {
                    list.add(e);
                }
            }
        }
        for (GameplayGroup g : GAMEPLAY_GROUPS) {
            leftRowEntries.add(new LeftRowEntry(g.gameplayId, g.displayName));
            if (!collapsedGroups.contains(g.gameplayId)) {
                List<ClientRoomSelectionState.Entry> rooms = grouped.get(g.gameplayId);
                if (rooms != null) {
                    // 排序：可进入的排在不可进入之前
                    List<ClientRoomSelectionState.Entry> sorted = new ArrayList<>(rooms);
                    sorted.sort(Comparator.comparingInt(e -> {
                        boolean joinable = "RUNNING".equals(e.lifecycleState())
                            || "MATCHING".equals(e.lifecycleState())
                            || "IDLE".equals(e.lifecycleState())
                            || ("MAINTENANCE".equals(e.lifecycleState()) && isAdmin);
                        return joinable ? 0 : 1;
                    }));
                    for (ClientRoomSelectionState.Entry e : sorted) {
                        leftRowEntries.add(new LeftRowEntry(e.roomId(), e.displayName(),
                            e.currentPlayers(), e.maxPlayers(), g.gameplayId, e.lifecycleState(), isAdmin));
                    }
                }
            }
        }
    }

    private void renderLeftPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                 int x1, int x2, int top, int screenHeight) {
        int contentY1 = top + ROW_H + 1;
        int contentY2 = screenHeight - PADDING;

        if (leftHeader == null)
            leftHeader = new GDRowRenderer(x1, top, x2, top + ROW_H,
                GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        leftHeader.setBounds(x1, top, x2, top + ROW_H);
        leftHeader.resetColumnConfig();
        leftHeader.addColumn("房间", -1, 0xFF9FD3FF, true, false, null);
        leftHeader.addColumn("人数", 30, 0xFFBDF5BD, false, true, null);
        leftHeader.render(guiGraphics, mouseX, mouseY, partialTick);

        int viewH = Math.max(1, contentY2 - contentY1);
        int contentH = leftRowEntries.size() * (ROW_H + ROW_GAP);
        storedViewportH = viewH;
        storedContentH = contentH;
        leftScrollState.update(mouseY, viewH, contentH);

        guiGraphics.enableScissor(x1, contentY1, x2, contentY2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -leftScrollState.scrollY(), 0);
        for (int i = 0; i < leftRowEntries.size(); i++) {
            LeftRowEntry entry = leftRowEntries.get(i);
            while (leftRowRenderers.size() <= i)
                leftRowRenderers.add(new GDRowRenderer(x1, contentY1, x2, contentY1 + ROW_H,
                    GuiConstants.COLOR_BLACK & 0xFFFFFF, 0.26f, false));
            int rowTop = contentY1 + i * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            if (rowBottom - leftScrollState.scrollY() <= contentY1 || rowTop - leftScrollState.scrollY() >= contentY2) continue;
            GDRowRenderer row = leftRowRenderers.get(i);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            if (entry.isGroupHeader) {
                boolean expanded = !collapsedGroups.contains(entry.gameplayId);
                row.setBackgroundColor(0x0026344A);
                row.setBackgroundAlpha(0.55f);
                String arrow = expanded ? "\u25bc " : "\u25b6 ";
                row.addColumn(arrow + entry.displayName, -1, 0xFFF0C060, true, false,
                    btn -> toggleGroup(entry.gameplayId));
                row.addColumn("", 30, 0xFFF0C060, false, true, null);
            } else {
                boolean selected = entry.roomId.equals(selectedRoomId);
                row.setBackgroundColor((i % 2 == 0 ? 0x00161F2C : 0x0010181F) & 0xFFFFFF);
                row.setBackgroundAlpha(i % 2 == 0 ? 0.18f : 0.32f);
                String label = entry.roomId + " " + entry.roomDisplayName;
                int nameColor = selected ? GuiConstants.COLOR_GOLD
                    : entry.isJoinable ? GuiConstants.COLOR_SKY_BLUE : 0xFF888888;
                row.addColumn(label, -1, nameColor, true, false, btn -> selectRoom(entry.roomId));
                String playerStr = entry.currentPlayers + "/" + entry.maxPlayers;
                int playerColor = getStateColor(entry.lifecycleState);
                row.addColumn(playerStr, 30, playerColor, false, true, null);
            }
            row.render(guiGraphics, mouseX, (int) (mouseY + leftScrollState.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        leftScrollState.renderScrollbar(guiGraphics, x1, contentY1, x2, contentY2, contentH);
    }

    private void toggleGroup(String gameplayId) {
        if (collapsedGroups.contains(gameplayId)) {
            collapsedGroups.remove(gameplayId);
        } else {
            collapsedGroups.add(gameplayId);
        }
    }

    private void selectRoom(String roomId) {
        if (roomId.equals(selectedRoomId)) {
            selectedRoomId = null;
        } else {
            selectedRoomId = roomId;
        }
    }

    // ==================== Right Panel ====================

    private ClientRoomSelectionState.Entry findSelectedEntry() {
        ClientRoomSelectionState.Snapshot snapshot = ClientRoomSelectionState.snapshot();
        if (snapshot == null || selectedRoomId == null) return null;
        for (ClientRoomSelectionState.Entry e : snapshot.entries()) {
            if (e.roomId().equals(selectedRoomId)) return e;
        }
        return null;
    }

    private void renderRightPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                  int x1, int x2, int top, int screenHeight) {
        // Border
        int borderColor = GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF;
        int borderAlpha = 0x99;
        int btnAreaH = BTN_H + 4;
        int bottomY = screenHeight - PADDING;
        int innerX1 = x1 + 1;
        int innerX2 = x2 - 1;
        int innerY1 = top + 1;
        int innerY2 = bottomY - btnAreaH - 1;

        int border = (borderAlpha << 24) | borderColor;
        guiGraphics.fill(x1, top, x2, innerY1, border);
        guiGraphics.fill(x1, innerY2, x2, bottomY - btnAreaH, border);
        guiGraphics.fill(x1, innerY1, innerX1, innerY2, border);
        guiGraphics.fill(innerX2, innerY1, x2, innerY2, border);

        ClientRoomSelectionState.Entry selected = findSelectedEntry();
        if (selected == null) {
            guiGraphics.drawString(minecraft.font, "点击左侧房间查看详情", x1 + 8, top + 8, 0xFF9AA6B8, false);
            return;
        }

        int panelW = innerX2 - innerX1;
        int textX = x1 + 6;

        // ── Title area ──
        int titleTop = top + 2;
        int titleBottom = top + 28;
        int titleH = titleBottom - titleTop;
        float titleScale = 2.0f;

        // ── 1. Status badge (top-right corner of title area) ──
        String stateLabel = getStateDisplayName(selected.lifecycleState());
        int stateColor = getStateColor(selected.lifecycleState());
        String badgeText = "◆ " + stateLabel;
        int badgeW = minecraft.font.width(badgeText);
        int badgeX = innerX2 - badgeW - 4;
        int badgeY = titleTop + (titleH - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, badgeText, badgeX, badgeY, stateColor, false);

        // ── 2. Large white display name (GDTextRenderer, scrolls if too long) ──
        String displayName = safe(selected.displayName());
        if (displayName.isEmpty()) displayName = selected.roomId();
        int titleMaxX = badgeX - 6; // stops before status badge
        if (titleRenderer == null) {
            titleRenderer = new GDTextRenderer(displayName, textX, titleTop, titleMaxX, titleBottom, titleScale, GuiConstants.COLOR_WHITE, false);
        } else {
            titleRenderer.setText(displayName);
            titleRenderer.setColor(GuiConstants.COLOR_WHITE);
            titleRenderer.setX1(textX);
            titleRenderer.setY1(titleTop);
            titleRenderer.setX2(titleMaxX);
            titleRenderer.setY2(titleBottom);
            titleRenderer.setFontSize(titleScale);
        }
        titleRenderer.render(guiGraphics, partialTick);

        // ── 3. Separator ──
        int sepY = titleBottom;
        int sepColor = (0x55 << 24) | (0xFF8A9AA8 & 0x00FFFFFF);
        guiGraphics.fill(innerX1, sepY, innerX2, sepY + 1, sepColor);

        int currentY = titleBottom + 3;

        // ── 4. Room ID (blue) + player count + map (GDTextRenderer, scrolls if too long) ──
        String roomInfo = selected.roomId() + "  ·  " + selected.currentPlayers() + "/" + selected.maxPlayers() + "人";
        String mapName = safe(selected.mapName());
        if (!mapName.isEmpty() && !mapName.equals(displayName)) {
            roomInfo = roomInfo + "  ·  " + mapName;
        }
        int infoLineH = minecraft.font.lineHeight + 2;
        if (infoRenderer == null) {
            infoRenderer = new GDTextRenderer(roomInfo, textX, currentY, innerX2 - 4, currentY + infoLineH, 1.0f, GuiConstants.COLOR_THEME_LIGHT, false);
        } else {
            infoRenderer.setText(roomInfo);
            infoRenderer.setColor(GuiConstants.COLOR_THEME_LIGHT);
            infoRenderer.setX1(textX);
            infoRenderer.setY1(currentY);
            infoRenderer.setX2(innerX2 - 4);
            infoRenderer.setY2(currentY + infoLineH);
            infoRenderer.setFontSize(1.0f);
        }
        infoRenderer.render(guiGraphics, partialTick);
        currentY += infoLineH;

        // ── 5. Description ──
        String desc = safe(selected.roomDescription());
        if (!desc.isEmpty()) {
            int availableWidth = panelW - 12;
            int descColor = 0xFFA0C0D8;
            for (String line : wrapTextToWidth(desc, availableWidth)) {
                if (currentY + minecraft.font.lineHeight > innerY2) break;
                guiGraphics.drawString(minecraft.font, line, textX, currentY, descColor, false);
                currentY += minecraft.font.lineHeight;
            }
        }

        // ── 6. Join button ──
        if (joinButton == null) {
            joinButton = new GDButton(0, 0, 0, BTN_H, Component.empty(), btn -> joinSelectedRoom());
            joinButton.setAutoScaleTextToFit(true);
        }
        int joinBtnY = bottomY - BTN_H - 2;
        joinButton.setX(innerX1);
        joinButton.setY(joinBtnY);
        joinButton.setWidth(panelW);
        joinButton.setHeight(BTN_H);
        updateJoinButton(selected);
    }

    private void updateJoinButton(ClientRoomSelectionState.Entry selected) {
        String state = selected.lifecycleState();
        boolean isAdmin = minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
        switch (state) {
            case "RUNNING" -> {
                joinButton.setMessage(Component.literal("加入对局(" + selected.currentPlayers() + "/" + selected.maxPlayers() + ")"));
                joinButton.active = true;
            }
            case "MATCHING" -> {
                joinButton.setMessage(Component.literal("进入匹配(" + selected.currentPlayers() + ")"));
                joinButton.active = true;
            }
            case "IDLE" -> {
                joinButton.setMessage(Component.literal("开始匹配"));
                joinButton.active = true;
            }
            case "MAINTENANCE" -> {
                joinButton.setMessage(Component.literal(isAdmin ? "进入房间" : "无法进入"));
                joinButton.active = isAdmin;
            }
            default -> {
                joinButton.setMessage(Component.literal("无法进入"));
                joinButton.active = false;
            }
        }
    }

    private void joinSelectedRoom() {
        if (selectedRoomId == null || minecraft == null || minecraft.player == null) return;
        minecraft.player.connection.sendCommand("gdc room " + selectedRoomId + " join");
    }

    // ==================== Input Events ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left panel click handling
        if (leftHeader != null && leftHeader.mouseClicked(mouseX, mouseY, button)) return true;
        int scrolledMouseY = (int) (mouseY + leftScrollState.scrollY());
        for (GDRowRenderer row : leftRowRenderers) {
            if (row.mouseClicked(mouseX, scrolledMouseY, button)) return true;
        }
        if (joinButton != null && joinButton.mouseClicked(mouseX, mouseY, button)) return true;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return leftScrollState.mouseScrolled(delta, storedViewportH, storedContentH);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        leftScrollState.stopDragging();
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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

    // ==================== Helpers ====================

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private List<String> wrapTextToWidth(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            if (minecraft.font.width(testLine) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                // Handle long words that exceed maxWidth
                if (minecraft.font.width(word) <= maxWidth) {
                    currentLine = new StringBuilder(word);
                } else {
                    // Break long word at character level
                    StringBuilder part = new StringBuilder();
                    for (char c : word.toCharArray()) {
                        if (minecraft.font.width(part.toString() + c) <= maxWidth) {
                            part.append(c);
                        } else {
                            lines.add(part.toString());
                            part = new StringBuilder(String.valueOf(c));
                        }
                    }
                    if (part.length() > 0) {
                        currentLine = part;
                    }
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static String getStateDisplayName(String state) {
        return switch (state) {
            case "IDLE" -> "空闲";
            case "MATCHING" -> "匹配中";
            case "RUNNING" -> "对局中";
            case "ENDING" -> "结算中";
            case "MAINTENANCE" -> "维护中";
            case "DISABLED" -> "已禁用";
            case "SAVING" -> "保存中";
            case "CLEANING" -> "清理中";
            default -> state;
        };
    }

    private static int getStateColor(String state) {
        return switch (state) {
            case "IDLE"       -> 0xFFAACCFF;
            case "MATCHING"   -> 0xFF55DD55;
            case "RUNNING"    -> 0xFFF0C060;
            case "ENDING"     -> 0xFFFF6060;
            case "MAINTENANCE" -> 0xFFC080FF;
            case "DISABLED"   -> 0xFF666666;
            default           -> 0xFF888888;
        };
    }
}
