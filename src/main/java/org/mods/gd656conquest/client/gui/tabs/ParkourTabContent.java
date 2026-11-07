/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.tabs;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.parkour.ClientParkourLeaderboardState;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ParkourLeaderboardRequestPacket;

public class ParkourTabContent
implements ConfigScreenHeader.TabContent {
    private static final int PADDING = 8;
    private static final int ROW_H = 17;
    private static final int ROW_GAP = 1;
    private static final int LEFT_WIDTH = 208;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private final Component title = Component.literal("\u8dd1\u9177\u6392\u884c\u699c");
    private final Minecraft minecraft;
    private final List<GDRowRenderer> rightRows = new ArrayList<GDRowRenderer>();
    private final GDScrollState rightScroll = new GDScrollState();
    private long lastRequestAtMs;

    public ParkourTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public void onTabOpen() {
        this.rightScroll.reset();
        this.requestSnapshot(true);
    }

    @Override
    public void tick() {
        this.requestSnapshot(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int leftX1 = 8;
        int leftX2 = Math.min(screenWidth / 2 - 8, leftX1 + 208);
        int rightX1 = leftX2 + 8;
        int rightX2 = screenWidth - 8;
        int headerY = top;
        int contentY1 = top + 17 + 1;
        int contentY2 = screenHeight - 8;
        BattlefieldUiTheme.drawText(guiGraphics, "\u8dd1\u9177\u6392\u884c\u699c", 8, top - 10, -6237968);
        this.renderLeftPanel(guiGraphics, leftX1, leftX2, headerY, contentY1, contentY2);
        this.renderRightPanel(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, headerY, contentY1, contentY2);
    }

    private void renderLeftPanel(GuiGraphics guiGraphics, int x1, int x2, int headerY, int contentY1, int contentY2) {
        GDRowRenderer header = new GDRowRenderer(x1, headerY, x2, headerY + 17, 0x8A9AA8, 0.76f, true);
        header.resetColumnConfig();
        header.addColumn("\u5f53\u524d\u4fe1\u606f", -1, -1, true, false, null);
        header.render(guiGraphics, 0, 0, 0.0f);
        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        int textY = contentY1 + 6;
        int lineGap = 14;
        this.drawInfoLine(guiGraphics, x1 + 6, textY, "\u5f53\u524d\u6392\u540d", snapshot == null || snapshot.currentRank() <= 0 ? "-" : String.valueOf(snapshot.currentRank()), this.resolveRankColor(snapshot == null ? 0 : snapshot.currentRank()));
        this.drawInfoLine(guiGraphics, x1 + 6, textY += lineGap, "\u6700\u4f73\u6210\u7ee9", this.formatTime(snapshot == null ? 0L : snapshot.bestTimeMillis()), -1);
        this.drawInfoLine(guiGraphics, x1 + 6, textY += lineGap, "\u4e0a\u6b21\u6210\u7ee9", this.formatTime(snapshot == null ? 0L : snapshot.lastTimeMillis()), -6237968);
        this.drawInfoLine(guiGraphics, x1 + 6, textY += lineGap, "\u5168\u670d\u7eaa\u5f55", snapshot == null || snapshot.recordHolderName().isBlank() ? "-" : snapshot.recordHolderName(), -1184275);
        this.drawInfoLine(guiGraphics, x1 + 6, textY += lineGap, "\u7eaa\u5f55\u7528\u65f6", this.formatTime(snapshot == null ? 0L : snapshot.recordHolderTimeMillis()), -36752);
        this.drawInfoLine(guiGraphics, x1 + 6, textY += lineGap, "\u5f53\u524d\u72b6\u6001", snapshot != null && snapshot.inParkour() ? "\u5df2\u52a0\u5165\u8dd1\u9177" : "\u672a\u52a0\u5165", snapshot != null && snapshot.inParkour() ? -1 : -5592406);
        if (snapshot == null) {
            BattlefieldUiTheme.drawText(guiGraphics, "\u6b63\u5728\u540c\u6b65\u6392\u884c\u699c...", x1 + 6, contentY2 - 16, -6237968);
        }
    }

    private void renderRightPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int x2, int headerY, int contentY1, int contentY2) {
        GDRowRenderer header = new GDRowRenderer(x1, headerY, x2, headerY + 17, 0x8A9AA8, 0.76f, true);
        header.resetColumnConfig();
        header.addColumn("\u6392\u540d", 40, -6569217, true, true, null);
        header.addColumn("\u73a9\u5bb6", -1, -1, true, false, null);
        header.addColumn("\u7528\u65f6", 84, -6569217, true, false, null);
        header.render(guiGraphics, mouseX, mouseY, partialTick);
        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        List<ClientParkourLeaderboardState.EntryState> entries = snapshot == null ? List.of() : snapshot.entries();
        int viewH = Math.max(1, contentY2 - contentY1);
        int contentH = this.getLeaderboardContentHeight();
        this.rightScroll.update(mouseY, viewH, contentH);
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,x1, contentY1, x2, contentY2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0, -this.rightScroll.scrollY(), 0.0);
        for (int index = 0; index < entries.size(); ++index) {
            while (this.rightRows.size() <= index) {
                this.rightRows.add(new GDRowRenderer(x1, contentY1, x2, contentY1 + 17, 5267568, 0.26f, false));
            }
            ClientParkourLeaderboardState.EntryState entry = (ClientParkourLeaderboardState.EntryState)entries.get(index);
            int rowTop = contentY1 + index * 18;
            int rowBottom = rowTop + 17;
            if ((double)rowBottom - this.rightScroll.scrollY() <= (double)contentY1 || (double)rowTop - this.rightScroll.scrollY() >= (double)contentY2) continue;
            GDRowRenderer row = this.rightRows.get(index);
            row.setBounds(x1, rowTop, x2, rowBottom);
            row.resetColumnConfig();
            row.setBackgroundColor((index % 2 == 0 ? 0x2C2C2C : 0x1F1F1F) & 0xFFFFFF);
            row.setBackgroundAlpha(entry.self() ? 0.42f : (index % 2 == 0 ? 0.22f : 0.32f));
            row.addColumn(String.valueOf(entry.rank()), 40, this.resolveRankColor(entry.rank()), true, true, null);
            row.addColumn((String)(entry.self() ? entry.playerName() + "  [\u4f60]" : entry.playerName()), -1, -1184275, true, false, null);
            row.addColumn(this.formatTime(entry.timeMillis()), 84, this.resolveRankColor(entry.rank()), true, false, null);
            row.render(guiGraphics, mouseX, (int)((double)mouseY + this.rightScroll.scrollY()), partialTick);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        this.rightScroll.renderScrollbar(guiGraphics, x1, contentY1, x2, contentY2, contentH);
        if (entries.isEmpty()) {
            BattlefieldUiTheme.drawText(guiGraphics, "\u6682\u65e0\u8dd1\u9177\u7eaa\u5f55", x1 + 6, contentY1 + 8, -6641992);
        }
    }

    private void requestSnapshot(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - this.lastRequestAtMs < 1200L) {
            return;
        }
        this.lastRequestAtMs = now;
        NetworkManager.getChannel().sendToServer(new ParkourLeaderboardRequestPacket(false));
    }

    private int getLeaderboardContentHeight() {
        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        int size = snapshot == null ? 0 : snapshot.entries().size();
        return Math.max(17, size * 18);
    }

    private void drawInfoLine(GuiGraphics guiGraphics, int x, int y, String label, String value, int valueColor) {
        BattlefieldUiTheme.drawText(guiGraphics, label + ": ", x, y, -2631721);
        BattlefieldUiTheme.drawText(guiGraphics, value, x + 58, y, valueColor);
    }

    private String formatTime(long millis) {
        return millis <= 0L ? "-" : ClientParkourRuntimeState.formatElapsed(millis);
    }

    private int resolveRankColor(int rank) {
        return switch (rank) {
            case 1 -> -46518;
            case 2 -> -26051;
            case 3 -> -6237968;
            default -> -10639105;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseClickedContent(double mouseX, double mouseY, int button, int screenWidth, int screenHeight, int contentTop) {
        if (button == 0) {
            int top = contentTop + 6;
            int leftX2 = Math.min(screenWidth / 2 - 8, 216);
            int rightX1 = leftX2 + 8;
            int rightX2 = screenWidth - 8;
            int contentY1 = top + 17 + 1;
            int contentY2 = screenHeight - 8;
            if (mouseX >= (double)rightX1 && mouseX <= (double)rightX2 && mouseY >= (double)contentY1 && mouseY <= (double)contentY2) {
                this.rightScroll.startDragging(mouseX, mouseY, rightX1, contentY1, rightX2, contentY2, this.getLeaderboardContentHeight());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.rightScroll.stopDragging();
        return false;
    }

    public boolean mouseScrolledContent(double mouseX, double mouseY, double delta, int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int leftX2 = Math.min(screenWidth / 2 - 8, 216);
        int rightX1 = leftX2 + 8;
        int rightX2 = screenWidth - 8;
        int contentY1 = top + 17 + 1;
        int contentY2 = screenHeight - 8;
        if (mouseX >= (double)rightX1 && mouseX <= (double)rightX2 && mouseY >= (double)contentY1 && mouseY <= (double)contentY2) {
            return this.rightScroll.mouseScrolled(delta, Math.max(1, contentY2 - contentY1), this.getLeaderboardContentHeight());
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

