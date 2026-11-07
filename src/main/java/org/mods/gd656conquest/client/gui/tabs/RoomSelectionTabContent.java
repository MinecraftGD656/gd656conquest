/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.tabs;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;
import org.mods.gd656conquest.client.loadout.state.ClientRoomSelectionState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomSelectionRequestPacket;

public class RoomSelectionTabContent
implements ConfigScreenHeader.TabContent {
    private static final int PADDING = 8;
    private static final int ROW_H = 17;
    private static final int ROW_GAP = 1;
    private static final int BTN_H = 20;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private static final List<GameplayGroup> GAMEPLAY_GROUPS = List.of(new GameplayGroup("conquest", "\u5f81\u670d-Conquest"), new GameplayGroup("breakthrough", "\u7a81\u7834-Breakthrough"), new GameplayGroup("gunmaster", "\u67aa\u795e-Gunmaster"), new GameplayGroup("deathmatch", "\u56e2\u961f\u6b7b\u6597-TDM"), new GameplayGroup("rush", "\u7a81\u88ad-Rush"));
    private final Component title = Component.literal("\u623f\u95f4\u9009\u62e9");
    private final Minecraft minecraft;
    private final Set<String> collapsedGroups = new HashSet<String>();
    private String selectedRoomId;
    private final GDScrollState leftScrollState = new GDScrollState();
    private final List<GDRowRenderer> leftRowRenderers = new ArrayList<GDRowRenderer>();
    private GDButton joinButton;
    private GDTextRenderer titleRenderer;
    private GDTextRenderer infoRenderer;
    private GDRowRenderer leftHeader;
    private long lastRequestAt;
    private final List<LeftRowEntry> leftRowEntries = new ArrayList<LeftRowEntry>();
    private int listLeft, listTop, listRight, listBottom;
    private int storedViewportH;
    private int storedContentH;

    public RoomSelectionTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public void onTabOpen() {
        this.requestSnapshot();
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - this.lastRequestAt >= 1200L) {
            this.requestSnapshot();
        }
    }

    private void requestSnapshot() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.lastRequestAt = System.currentTimeMillis();
        NetworkManager.getChannel().sendToServer(new RoomSelectionRequestPacket());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int contentTop) {
        ClientRoomSelectionState.Snapshot snapshot = ClientRoomSelectionState.snapshot();
        this.rebuildLeftRows(snapshot);
        int split = (screenWidth - 24) * (BattlefieldUiTheme.current() == org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme.BF2042 ? 42 : 50) / 100;
        int leftX1 = 8;
        int leftX2 = leftX1 + split;
        int rightX1 = leftX2 + 8;
        int rightX2 = screenWidth - 8;
        int top = contentTop + 6;
        BattlefieldUiTheme.drawText(guiGraphics, "\u623f\u95f4\u9009\u62e9", 8, top - 10, -6237968);
        this.renderLeftPanel(guiGraphics, mouseX, mouseY, partialTick, leftX1, leftX2, top, screenHeight);
        this.renderRightPanel(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, top, screenHeight);
        if (this.joinButton != null) {
            this.joinButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void rebuildLeftRows(ClientRoomSelectionState.Snapshot snapshot) {
        this.leftRowEntries.clear();
        boolean isAdmin = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
        LinkedHashMap grouped = new LinkedHashMap();
        for (GameplayGroup g : GAMEPLAY_GROUPS) {
            grouped.put(g.gameplayId, new ArrayList());
        }
        if (snapshot != null) {
            for (ClientRoomSelectionState.Entry e2 : snapshot.entries()) {
                List list = (List)grouped.get(e2.gameplayId());
                if (list == null) continue;
                list.add(e2);
            }
        }
        for (GameplayGroup g : GAMEPLAY_GROUPS) {
            List rooms;
            this.leftRowEntries.add(new LeftRowEntry(g.gameplayId, g.displayName));
            if (this.collapsedGroups.contains(g.gameplayId) || (rooms = (List)grouped.get(g.gameplayId)) == null) continue;
            ArrayList<ClientRoomSelectionState.Entry> sorted = new ArrayList<ClientRoomSelectionState.Entry>(rooms);
            sorted.sort(Comparator.comparingInt(e -> {
                boolean joinable = "RUNNING".equals(e.lifecycleState()) || "MATCHING".equals(e.lifecycleState()) || "IDLE".equals(e.lifecycleState()) || "MAINTENANCE".equals(e.lifecycleState()) && isAdmin;
                return joinable ? 0 : 1;
            }));
            for (ClientRoomSelectionState.Entry e3 : sorted) {
                this.leftRowEntries.add(new LeftRowEntry(e3.roomId(), e3.displayName(), e3.currentPlayers(), e3.maxPlayers(), g.gameplayId, e3.lifecycleState(), isAdmin));
            }
        }
    }

    private void renderLeftPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int x2, int top, int screenHeight) {
        int contentY1 = top + 17 + 1;
        int contentY2 = screenHeight - 8;
        if (this.leftHeader == null) {
            this.leftHeader = new GDRowRenderer(x1, top, x2, top + 17, 0x8A9AA8, 0.76f, true);
        }
        this.leftHeader.setBounds(x1, top, x2, top + 17);
        this.leftHeader.resetColumnConfig();
        this.leftHeader.addColumn("\u623f\u95f4", -1, -6302721, true, false, null);
        this.leftHeader.addColumn("\u4eba\u6570", 30, -4328003, false, true, null);
        this.leftHeader.render(guiGraphics, mouseX, mouseY, partialTick);
        int viewH = Math.max(1, contentY2 - contentY1);
        int contentH = this.leftRowEntries.size() * 18;
        this.listLeft = x1; this.listTop = contentY1; this.listRight = x2; this.listBottom = contentY2;
        this.storedViewportH = viewH;
        this.storedContentH = contentH;
        this.leftScrollState.update(mouseY, viewH, contentH);
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,x1, contentY1, x2, contentY2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0, -this.leftScrollState.scrollY(), 0.0);
        for (int i = 0; i < this.leftRowEntries.size(); ++i) {
            LeftRowEntry entry = this.leftRowEntries.get(i);
            while (this.leftRowRenderers.size() <= i) {
                this.leftRowRenderers.add(new GDRowRenderer(x1, contentY1, x2, contentY1 + 17, 5267568, 0.26f, false));
            }
            int rowTop = contentY1 + i * 18;
            int rowBottom = rowTop + 17;
            if ((double)rowBottom - this.leftScrollState.scrollY() <= (double)contentY1 || (double)rowTop - this.leftScrollState.scrollY() >= (double)contentY2) continue;
            GDRowRenderer row = this.leftRowRenderers.get(i);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            if (entry.isGroupHeader) {
                boolean expanded = !this.collapsedGroups.contains(entry.gameplayId);
                row.setBackgroundColor(2503754);
                row.setBackgroundAlpha(0.55f);
                String arrow = expanded ? "\u25bc " : "\u25b6 ";
                row.addColumn(arrow + entry.displayName, -1, -999328, true, false, btn -> this.toggleGroup(entry.gameplayId));
                row.addColumn("", 30, -999328, false, true, null);
            } else {
                boolean selected = entry.roomId.equals(this.selectedRoomId);
                row.setBackgroundColor((i % 2 == 0 ? 1449772 : 1054751) & 0xFFFFFF);
                row.setBackgroundAlpha(i % 2 == 0 ? 0.18f : 0.32f);
                String label = entry.roomId + " " + entry.roomDisplayName;
                int nameColor = selected ? BattlefieldUiTheme.palette().accent() : (entry.isJoinable ? -7876885 : -7829368);
                row.addColumn(label, -1, nameColor, true, false, btn -> this.selectRoom(entry.roomId));
                String playerStr = entry.currentPlayers + "/" + entry.maxPlayers;
                int playerColor = RoomSelectionTabContent.getStateColor(entry.lifecycleState);
                row.addColumn(playerStr, 30, playerColor, false, true, null);
            }
            row.render(guiGraphics, mouseX, (int)((double)mouseY + this.leftScrollState.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        this.leftScrollState.renderScrollbar(guiGraphics, x1, contentY1, x2, contentY2, contentH);
    }

    private void toggleGroup(String gameplayId) {
        if (this.collapsedGroups.contains(gameplayId)) {
            this.collapsedGroups.remove(gameplayId);
        } else {
            this.collapsedGroups.add(gameplayId);
        }
    }

    private void selectRoom(String roomId) {
        this.selectedRoomId = roomId.equals(this.selectedRoomId) ? null : roomId;
    }

    private ClientRoomSelectionState.Entry findSelectedEntry() {
        ClientRoomSelectionState.Snapshot snapshot = ClientRoomSelectionState.snapshot();
        if (snapshot == null || this.selectedRoomId == null) {
            return null;
        }
        for (ClientRoomSelectionState.Entry e : snapshot.entries()) {
            if (!e.roomId().equals(this.selectedRoomId)) continue;
            return e;
        }
        return null;
    }

    private void renderRightPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int x2, int top, int screenHeight) {
        int borderColor = 0x8A9AA8;
        int borderAlpha = 153;
        int btnAreaH = 24;
        int bottomY = screenHeight - 8;
        int innerX1 = x1 + 1;
        int innerX2 = x2 - 1;
        int innerY1 = top + 1;
        int innerY2 = bottomY - btnAreaH - 1;
        int border = BattlefieldUiTheme.palette().line();
        guiGraphics.fill(x1, top, x2, innerY1, border);
        guiGraphics.fill(x1, innerY2, x2, bottomY - btnAreaH, border);
        guiGraphics.fill(x1, innerY1, innerX1, innerY2, border);
        guiGraphics.fill(innerX2, innerY1, x2, innerY2, border);
        ClientRoomSelectionState.Entry selected = this.findSelectedEntry();
        if (selected == null) {
            this.joinButton = null;
            BattlefieldUiTheme.drawText(guiGraphics, "\u70b9\u51fb\u5de6\u4fa7\u623f\u95f4\u67e5\u770b\u8be6\u60c5", x1 + 8, top + 8, -6641992);
            return;
        }
        int panelW = innerX2 - innerX1;
        int textX = x1 + 6;
        int titleTop = top + 2;
        int titleBottom = top + 28;
        int titleH = titleBottom - titleTop;
        float titleScale = 2.0f;
        String stateLabel = RoomSelectionTabContent.getStateDisplayName(selected.lifecycleState());
        int stateColor = RoomSelectionTabContent.getStateColor(selected.lifecycleState());
        String badgeText = "\u25c6 " + stateLabel;
        int badgeW = BattlefieldUiTheme.textWidth(badgeText);
        int badgeX = innerX2 - badgeW - 4;
        int badgeY = titleTop + (titleH - this.minecraft.font.lineHeight) / 2;
        BattlefieldUiTheme.drawText(guiGraphics, badgeText, badgeX, badgeY, stateColor);
        String displayName = RoomSelectionTabContent.safe(selected.displayName());
        if (displayName.isEmpty()) {
            displayName = selected.roomId();
        }
        int titleMaxX = badgeX - 6;
        if (this.titleRenderer == null) {
            this.titleRenderer = new GDTextRenderer(displayName, textX, titleTop, titleMaxX, titleBottom, titleScale, -1, false);
        } else {
            this.titleRenderer.setText(displayName);
            this.titleRenderer.setColor(-1);
            this.titleRenderer.setX1(textX);
            this.titleRenderer.setY1(titleTop);
            this.titleRenderer.setX2(titleMaxX);
            this.titleRenderer.setY2(titleBottom);
            this.titleRenderer.setFontSize(titleScale);
        }
        this.titleRenderer.render(guiGraphics, partialTick);
        int sepY = titleBottom;
        int sepColor = BattlefieldUiTheme.palette().line();
        guiGraphics.fill(innerX1, sepY, innerX2, sepY + 1, sepColor);
        int currentY = titleBottom + 3;
        String roomInfo = selected.roomId() + "  \u00b7  " + selected.currentPlayers() + "/" + selected.maxPlayers() + "\u4eba";
        String mapName = RoomSelectionTabContent.safe(selected.mapName());
        if (!mapName.isEmpty() && !mapName.equals(displayName)) {
            roomInfo = roomInfo + "  \u00b7  " + mapName;
        }
        int infoLineH = this.minecraft.font.lineHeight + 2;
        if (this.infoRenderer == null) {
            this.infoRenderer = new GDTextRenderer(roomInfo, textX, currentY, innerX2 - 4, currentY + infoLineH, 1.0f, -7298896, false);
        } else {
            this.infoRenderer.setText(roomInfo);
            this.infoRenderer.setColor(-7298896);
            this.infoRenderer.setX1(textX);
            this.infoRenderer.setY1(currentY);
            this.infoRenderer.setX2(innerX2 - 4);
            this.infoRenderer.setY2(currentY + infoLineH);
            this.infoRenderer.setFontSize(1.0f);
        }
        this.infoRenderer.render(guiGraphics, partialTick);
        currentY += infoLineH;
        String desc = RoomSelectionTabContent.safe(selected.roomDescription());
        if (!desc.isEmpty()) {
            int availableWidth = panelW - 12;
            int descColor = -6242088;
            for (String line : this.wrapTextToWidth(desc, availableWidth)) {
                if (currentY + this.minecraft.font.lineHeight > innerY2) break;
                BattlefieldUiTheme.drawText(guiGraphics, line, textX, currentY, descColor);
                currentY += this.minecraft.font.lineHeight;
            }
        }
        if (this.joinButton == null) {
            this.joinButton = new GDButton(0, 0, 0, 20, Component.empty(), btn -> this.joinSelectedRoom());
            this.joinButton.setAutoScaleTextToFit(true);
        }
        int joinBtnY = bottomY - 20 - 2;
        this.joinButton.setX(innerX1);
        this.joinButton.setY(joinBtnY);
        this.joinButton.setWidth(panelW);
        this.joinButton.setHeight(20);
        this.updateJoinButton(selected);
    }

    private void updateJoinButton(ClientRoomSelectionState.Entry selected) {
        String state = selected.lifecycleState();
        boolean isAdmin = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
        switch (state) {
            case "RUNNING": {
                this.joinButton.setMessage(Component.literal("\u52a0\u5165\u5bf9\u5c40(" + selected.currentPlayers() + "/" + selected.maxPlayers() + ")"));
                this.joinButton.active = true;
                break;
            }
            case "MATCHING": {
                this.joinButton.setMessage(Component.literal("\u8fdb\u5165\u5339\u914d(" + selected.currentPlayers() + ")"));
                this.joinButton.active = true;
                break;
            }
            case "IDLE": {
                this.joinButton.setMessage(Component.literal("\u5f00\u59cb\u5339\u914d"));
                this.joinButton.active = true;
                break;
            }
            case "MAINTENANCE": {
                this.joinButton.setMessage(Component.literal(isAdmin ? "\u8fdb\u5165\u623f\u95f4" : "\u65e0\u6cd5\u8fdb\u5165"));
                this.joinButton.active = isAdmin;
                break;
            }
            default: {
                this.joinButton.setMessage(Component.literal("\u65e0\u6cd5\u8fdb\u5165"));
                this.joinButton.active = false;
            }
        }
    }

    private void joinSelectedRoom() {
        if (this.selectedRoomId == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.connection.sendCommand("gdc room " + this.selectedRoomId + " join");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.leftHeader != null && this.leftHeader.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int scrolledMouseY = (int)(mouseY + this.leftScrollState.scrollY());
        if (mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= this.listTop && mouseY < this.listBottom) for (int i = 0; i < this.leftRowEntries.size(); i++) {
            GDRowRenderer row = this.leftRowRenderers.get(i);
            if (!row.mouseClicked(mouseX, scrolledMouseY, button)) continue;
            return true;
        }
        return this.joinButton != null && this.joinButton.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseX >= this.listLeft && mouseX < this.listRight && mouseY >= this.listTop && mouseY < this.listBottom && this.leftScrollState.mouseScrolled(delta, this.storedViewportH, this.storedContentH);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.leftScrollState.stopDragging();
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

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private List<String> wrapTextToWidth(String text, int maxWidth) {
        ArrayList<String> lines = new ArrayList<String>();
        if (text.isEmpty()) {
            return lines;
        }
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String testLine;
            String string = testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            if (BattlefieldUiTheme.textWidth(testLine) <= maxWidth) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
                continue;
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
            }
            if (BattlefieldUiTheme.textWidth(word) <= maxWidth) {
                currentLine = new StringBuilder(word);
                continue;
            }
            StringBuilder part = new StringBuilder();
            for (char c : word.toCharArray()) {
                if (BattlefieldUiTheme.textWidth(part.toString() + c) <= maxWidth) {
                    part.append(c);
                    continue;
                }
                lines.add(part.toString());
                part = new StringBuilder(String.valueOf(c));
            }
            if (part.length() <= 0) continue;
            currentLine = part;
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static String getStateDisplayName(String state) {
        return switch (state) {
            case "IDLE" -> "\u7a7a\u95f2";
            case "MATCHING" -> "\u5339\u914d\u4e2d";
            case "RUNNING" -> "\u5bf9\u5c40\u4e2d";
            case "ENDING" -> "\u7ed3\u7b97\u4e2d";
            case "MAINTENANCE" -> "\u7ef4\u62a4\u4e2d";
            case "DISABLED" -> "\u5df2\u7981\u7528";
            case "SAVING" -> "\u4fdd\u5b58\u4e2d";
            case "CLEANING" -> "\u6e05\u7406\u4e2d";
            case "LOCKED" -> "\u5df2\u9501\u5b9a";
            default -> state;
        };
    }

    private static int getStateColor(String state) {
        return switch (state) {
            case "IDLE" -> -5583617;
            case "MATCHING" -> -11149995;
            case "RUNNING" -> -999328;
            case "ENDING" -> -40864;
            case "MAINTENANCE" -> -4161281;
            case "DISABLED" -> -10066330;
            case "LOCKED" -> BattlefieldUiTheme.palette().accent();
            default -> -7829368;
        };
    }

    record GameplayGroup(String gameplayId, String displayName) {
    }

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
            this.isJoinable = "RUNNING".equals(lifecycleState) || "MATCHING".equals(lifecycleState) || "IDLE".equals(lifecycleState) || "MAINTENANCE".equals(lifecycleState) && isAdmin;
        }
    }
}

