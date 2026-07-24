package org.mods.gd656conquest.client.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.parkour.ClientParkourLeaderboardState;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ParkourActionPacket;
import org.mods.gd656conquest.network.packet.ParkourLeaderboardRequestPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab content for the parkour leaderboard (跑酷排行榜).
 * Adapted from LoadoutParkourLeaderboardScreen.
 */
public class ParkourTabContent implements ConfigScreenHeader.TabContent {

    private static final int PADDING = GuiConstants.SCREEN_EDGE_DISTANCE;
    private static final int ROW_H = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ROW_GAP = 1;
    private static final int LEFT_WIDTH = 208;
    private static final long REQUEST_INTERVAL_MS = 1200L;

    private final Component title = Component.literal("跑酷排行榜");
    private final Minecraft minecraft;
    private final List<GDRowRenderer> rightRows = new ArrayList<>();
    private final GDScrollState rightScroll = new GDScrollState();
    private long lastRequestAtMs;

    public ParkourTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public void onTabOpen() {
        rightScroll.reset();
        requestSnapshot(true);
    }

    @Override
    public void tick() {
        requestSnapshot(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int leftX1 = PADDING;
        int leftX2 = Math.min(screenWidth / 2 - 8, leftX1 + LEFT_WIDTH);
        int rightX1 = leftX2 + PADDING;
        int rightX2 = screenWidth - PADDING;
        int headerY = top;
        int contentY1 = top + ROW_H + 1;
        int contentY2 = screenHeight - 30;

        guiGraphics.drawString(minecraft.font, "跑酷排行榜", PADDING, top - 10, 0xFFFFD78A, false);

        renderLeftPanel(guiGraphics, leftX1, leftX2, headerY, contentY1, contentY2);
        renderRightPanel(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, headerY, contentY1, contentY2);
    }

    private void renderLeftPanel(GuiGraphics guiGraphics, int x1, int x2, int headerY, int contentY1, int contentY2) {
        GDRowRenderer header = new GDRowRenderer(x1, headerY, x2, headerY + ROW_H,
                GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        header.resetColumnConfig();
        header.addColumn("当前信息", -1, GuiConstants.COLOR_WHITE, true, false, null);
        header.render(guiGraphics, 0, 0, 0.0F);

        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        int textY = contentY1 + 6;
        int lineGap = 14;
        drawInfoLine(guiGraphics, x1 + 6, textY, "当前排名",
                snapshot == null || snapshot.currentRank() <= 0 ? "-" : String.valueOf(snapshot.currentRank()),
                resolveRankColor(snapshot == null ? 0 : snapshot.currentRank()));
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "最佳成绩",
                formatTime(snapshot == null ? 0L : snapshot.bestTimeMillis()), GuiConstants.COLOR_WHITE);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "上次成绩",
                formatTime(snapshot == null ? 0L : snapshot.lastTimeMillis()), 0xFFFFD78A);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "全服纪录",
                snapshot == null || snapshot.recordHolderName().isBlank() ? "-" : snapshot.recordHolderName(), 0xFFEDEDED);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "纪录用时",
                formatTime(snapshot == null ? 0L : snapshot.recordHolderTimeMillis()), 0xFFFF7070);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "当前状态",
                snapshot != null && snapshot.inParkour() ? "已加入跑酷" : "未加入",
                snapshot != null && snapshot.inParkour() ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
        if (snapshot == null) {
            guiGraphics.drawString(minecraft.font, "正在同步排行榜...", x1 + 6, contentY2 - 16, 0xFFFFD78A, false);
        }
    }

    private void renderRightPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                  int x1, int x2, int headerY, int contentY1, int contentY2) {
        GDRowRenderer header = new GDRowRenderer(x1, headerY, x2, headerY + ROW_H,
                GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        header.resetColumnConfig();
        header.addColumn("排名", 40, 0xFFFFE39F, true, true, null);
        header.addColumn("玩家", -1, GuiConstants.COLOR_WHITE, true, false, null);
        header.addColumn("用时", 84, 0xFFFFE39F, true, false, null);
        header.render(guiGraphics, mouseX, mouseY, partialTick);

        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        List<ClientParkourLeaderboardState.EntryState> entries = snapshot == null ? List.of() : snapshot.entries();
        int viewH = Math.max(1, contentY2 - contentY1);
        int contentH = getLeaderboardContentHeight();
        rightScroll.update(mouseY, viewH, contentH);
        guiGraphics.enableScissor(x1, contentY1, x2, contentY2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0D, -rightScroll.scrollY(), 0.0D);
        for (int index = 0; index < entries.size(); index++) {
            while (rightRows.size() <= index) {
                rightRows.add(new GDRowRenderer(x1, contentY1, x2, contentY1 + ROW_H,
                        GuiConstants.COLOR_BLACK & 0x00FFFFFF, 0.26f, false));
            }
            ClientParkourLeaderboardState.EntryState entry = entries.get(index);
            int rowTop = contentY1 + index * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            if (rowBottom - rightScroll.scrollY() <= contentY1 || rowTop - rightScroll.scrollY() >= contentY2) continue;
            GDRowRenderer row = rightRows.get(index);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            row.setBackgroundColor((index % 2 == 0 ? 0x002C2C2C : 0x001F1F1F) & 0x00FFFFFF);
            row.setBackgroundAlpha(entry.self() ? 0.42f : (index % 2 == 0 ? 0.22f : 0.32f));
            row.addColumn(String.valueOf(entry.rank()), 40, resolveRankColor(entry.rank()), true, true, null);
            row.addColumn(entry.self() ? entry.playerName() + "  [你]" : entry.playerName(), -1, 0xFFEDEDED, true, false, null);
            row.addColumn(formatTime(entry.timeMillis()), 84, resolveRankColor(entry.rank()), true, false, null);
            row.render(guiGraphics, mouseX, (int) (mouseY + rightScroll.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        rightScroll.renderScrollbar(guiGraphics, x1, contentY1, x2, contentY2, contentH);
        if (entries.isEmpty()) {
            guiGraphics.drawString(minecraft.font, "暂无跑酷纪录", x1 + 6, contentY1 + 8, 0xFF9AA6B8, false);
        }
    }

    private void requestSnapshot(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastRequestAtMs < REQUEST_INTERVAL_MS) return;
        lastRequestAtMs = now;
        NetworkManager.getChannel().sendToServer(new ParkourLeaderboardRequestPacket(false));
    }

    private int getLeaderboardContentHeight() {
        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        int size = snapshot == null ? 0 : snapshot.entries().size();
        return Math.max(ROW_H, size * (ROW_H + ROW_GAP));
    }

    private void drawInfoLine(GuiGraphics guiGraphics, int x, int y, String label, String value, int valueColor) {
        guiGraphics.drawString(minecraft.font, label + ": ", x, y, 0xFFD7D7D7, false);
        guiGraphics.drawString(minecraft.font, value, x + 58, y, valueColor, false);
    }

    private String formatTime(long millis) {
        return millis <= 0L ? "-" : ClientParkourRuntimeState.formatElapsed(millis);
    }

    private int resolveRankColor(int rank) {
        return switch (rank) {
            case 1 -> 0xFFFF4A4A;
            case 2 -> 0xFFFF9A3D;
            case 3 -> 0xFFFFD84D;
            default -> 0xFF5DA8FF;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseClickedContent(double mouseX, double mouseY, int button,
                                        int screenWidth, int screenHeight, int contentTop) {
        if (button == 0) {
            int top = contentTop + 6;
            int leftX2 = Math.min(screenWidth / 2 - 8, PADDING + LEFT_WIDTH);
            int rightX1 = leftX2 + PADDING;
            int rightX2 = screenWidth - PADDING;
            int contentY1 = top + ROW_H + 1;
            int contentY2 = screenHeight - 30;
            if (mouseX >= rightX1 && mouseX <= rightX2 && mouseY >= contentY1 && mouseY <= contentY2) {
                rightScroll.startDragging(mouseX, mouseY, rightX1, contentY1, rightX2, contentY2, getLeaderboardContentHeight());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        rightScroll.stopDragging();
        return false;
    }

    public boolean mouseScrolledContent(double mouseX, double mouseY, double delta,
                                         int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int leftX2 = Math.min(screenWidth / 2 - 8, PADDING + LEFT_WIDTH);
        int rightX1 = leftX2 + PADDING;
        int rightX2 = screenWidth - PADDING;
        int contentY1 = top + ROW_H + 1;
        int contentY2 = screenHeight - 30;
        if (mouseX >= rightX1 && mouseX <= rightX2 && mouseY >= contentY1 && mouseY <= contentY2) {
            return rightScroll.mouseScrolled(delta, Math.max(1, contentY2 - contentY1), getLeaderboardContentHeight());
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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
}
