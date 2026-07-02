package org.mods.gd656conquest.client.loadout.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.parkour.ClientParkourLeaderboardState;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ParkourActionPacket;
import org.mods.gd656conquest.network.packet.ParkourLeaderboardRequestPacket;

import java.util.ArrayList;
import java.util.List;

public class LoadoutParkourLeaderboardScreen extends Screen {
    private static final int PADDING = 18;
    private static final int TOP = 20;
    private static final int ROW_H = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ROW_GAP = 1;
    private static final int LEFT_WIDTH = 208;
    private static final long REQUEST_INTERVAL_MS = 1200L;

    private final Screen parent;
    private final List<GDRowRenderer> rightRows = new ArrayList<>();
    private final GDScrollState rightScroll = new GDScrollState();
    private GDButton backButton;
    private GDButton refreshButton;
    private GDButton quickJoinButton;
    private long lastRequestAtMs;

    public LoadoutParkourLeaderboardScreen(Screen parent) {
        super(Component.literal("跑酷排行榜"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.backButton = new GDButton(this.width - 86, this.height - 26, 72, 20, Component.literal("返回"), btn -> closeToParent());
        this.refreshButton = new GDButton(this.width - 166, this.height - 26, 72, 20, Component.literal("刷新"), btn -> requestSnapshot(true));
        this.quickJoinButton = new GDButton(PADDING, this.height - 26, 96, 20, Component.literal("快速加入跑酷"), btn -> quickJoin());
        this.rightScroll.reset();
        requestSnapshot(true);
    }

    @Override
    public void tick() {
        super.tick();
        requestSnapshot(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        int leftX1 = PADDING;
        int leftX2 = Math.min(this.width / 2 - 8, leftX1 + LEFT_WIDTH);
        int rightX1 = leftX2 + PADDING;
        int rightX2 = this.width - PADDING;
        int headerY = TOP;
        int contentY1 = TOP + ROW_H + 1;
        int contentY2 = this.height - 36;

        guiGraphics.drawString(this.font, "跑酷排行榜", PADDING, 6, 0xFFFFD78A, false);
        guiGraphics.drawString(this.font, "仅主城可加入，挑战过程中切换玩法会自动作废当前成绩", PADDING + 86, 6, 0xFFB8C6D6, false);

        renderLeftPanel(guiGraphics, leftX1, leftX2, headerY, contentY1, contentY2);
        renderRightPanel(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, headerY, contentY1, contentY2);

        if (quickJoinButton != null) {
            ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
            boolean active = snapshot != null && snapshot.quickJoinAvailable();
            quickJoinButton.active = active;
            quickJoinButton.setTextColor(active ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            quickJoinButton.setY(this.height - 26);
            quickJoinButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (refreshButton != null) {
            refreshButton.setX(this.width - 166);
            refreshButton.setY(this.height - 26);
            refreshButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (backButton != null) {
            backButton.setX(this.width - 86);
            backButton.setY(this.height - 26);
            backButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (refreshButton != null && refreshButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (quickJoinButton != null && quickJoinButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (isMouseOverRightPanel(mouseX, mouseY)) {
                rightScroll.startDragging(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        rightScroll.stopDragging();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOverRightPanel(mouseX, mouseY)) {
            return rightScroll.mouseScrolled(delta, Math.max(1, this.height - (TOP + ROW_H + 1) - 36), getLeaderboardContentHeight());
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void renderLeftPanel(GuiGraphics guiGraphics, int x1, int x2, int headerY, int contentY1, int contentY2) {
        GDRowRenderer header = new GDRowRenderer(x1, headerY, x2, headerY + ROW_H, GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        header.resetColumnConfig();
        header.addColumn("当前信息", -1, 0xFF9FD3FF, true, false, null);
        header.render(guiGraphics, 0, 0, 0.0F);

        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        int textY = contentY1 + 6;
        int lineGap = 14;
        drawInfoLine(guiGraphics, x1 + 6, textY, "当前排名", snapshot == null || snapshot.currentRank() <= 0 ? "-" : String.valueOf(snapshot.currentRank()), resolveRankColor(snapshot == null ? 0 : snapshot.currentRank()));
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "最佳成绩", formatTime(snapshot == null ? 0L : snapshot.bestTimeMillis()), 0xFF9FD3FF);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "上次成绩", formatTime(snapshot == null ? 0L : snapshot.lastTimeMillis()), 0xFFFFD78A);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "全服纪录", snapshot == null || snapshot.recordHolderName().isBlank() ? "-" : snapshot.recordHolderName(), 0xFFEDEDED);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "纪录用时", formatTime(snapshot == null ? 0L : snapshot.recordHolderTimeMillis()), 0xFFFF7070);
        textY += lineGap;
        drawInfoLine(guiGraphics, x1 + 6, textY, "当前状态", snapshot != null && snapshot.inParkour() ? "已加入跑酷" : "未加入", snapshot != null && snapshot.inParkour() ? 0xFF8EF7A2 : 0xFF9AA6B8);
        textY += lineGap * 2;
        guiGraphics.drawString(this.font, "挑战计时会显示在 actionbar 上方", x1 + 6, textY, 0xFFB8C6D6, false);
        textY += lineGap;
        guiGraphics.drawString(this.font, "格式: " + ClientParkourRuntimeState.formatElapsed(62_345L), x1 + 6, textY, 0xFFB8C6D6, false);
        if (snapshot == null) {
            guiGraphics.drawString(this.font, "正在同步排行榜...", x1 + 6, contentY2 - 16, 0xFFFFD78A, false);
        }
    }

    private void renderRightPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int x2, int headerY, int contentY1, int contentY2) {
        GDRowRenderer header = new GDRowRenderer(x1, headerY, x2, headerY + ROW_H, GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        header.resetColumnConfig();
        header.addColumn("排名", 40, 0xFFFFE39F, true, true, null);
        header.addColumn("玩家", -1, 0xFF9FD3FF, true, false, null);
        header.addColumn("用时", 84, 0xFFBDF5BD, true, false, null);
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
                rightRows.add(new GDRowRenderer(x1, contentY1, x2, contentY1 + ROW_H, GuiConstants.COLOR_BLACK & 0x00FFFFFF, 0.26f, false));
            }
            ClientParkourLeaderboardState.EntryState entry = entries.get(index);
            int rowTop = contentY1 + index * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            if (rowBottom - rightScroll.scrollY() <= contentY1 || rowTop - rightScroll.scrollY() >= contentY2) {
                continue;
            }
            GDRowRenderer row = rightRows.get(index);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            row.setBackgroundColor((index % 2 == 0 ? 0x00161F2C : 0x0010181F) & 0x00FFFFFF);
            row.setBackgroundAlpha(entry.self() ? 0.42f : (index % 2 == 0 ? 0.18f : 0.30f));
            row.addColumn(String.valueOf(entry.rank()), 40, resolveRankColor(entry.rank()), true, true, null);
            row.addColumn(entry.self() ? entry.playerName() + "  [你]" : entry.playerName(), -1, 0xFFEDEDED, true, false, null);
            row.addColumn(formatTime(entry.timeMillis()), 84, resolveRankColor(entry.rank()), true, false, null);
            row.render(guiGraphics, mouseX, (int) (mouseY + rightScroll.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        rightScroll.renderScrollbar(guiGraphics, x1, contentY1, x2, contentY2, contentH);
        if (entries.isEmpty()) {
            guiGraphics.drawString(this.font, "暂无跑酷纪录", x1 + 6, contentY1 + 8, 0xFF9AA6B8, false);
        }
    }

    private void quickJoin() {
        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        if (snapshot == null || !snapshot.quickJoinAvailable()) {
            return;
        }
        NetworkManager.getChannel().sendToServer(ParkourActionPacket.quickJoin());
        requestSnapshot(true);
    }

    private void requestSnapshot(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastRequestAtMs < REQUEST_INTERVAL_MS) {
            return;
        }
        lastRequestAtMs = now;
        NetworkManager.getChannel().sendToServer(new ParkourLeaderboardRequestPacket(false));
    }

    private int getLeaderboardContentHeight() {
        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        int size = snapshot == null ? 0 : snapshot.entries().size();
        return Math.max(ROW_H, size * (ROW_H + ROW_GAP));
    }

    private boolean isMouseOverRightPanel(double mouseX, double mouseY) {
        int left = Math.min(this.width / 2 - 8, PADDING + LEFT_WIDTH) + PADDING;
        int right = this.width - PADDING;
        int top = TOP + ROW_H + 1;
        int bottom = this.height - 36;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void drawInfoLine(GuiGraphics guiGraphics, int x, int y, String label, String value, int valueColor) {
        guiGraphics.drawString(this.font, label + ": ", x, y, 0xFFD7D7D7, false);
        guiGraphics.drawString(this.font, value, x + 58, y, valueColor, false);
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
}
