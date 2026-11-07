/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.loadout.screen;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.parkour.ClientParkourLeaderboardState;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ParkourActionPacket;
import org.mods.gd656conquest.network.packet.ParkourLeaderboardRequestPacket;

public class LoadoutParkourLeaderboardScreen
extends Screen {
    private static final int PADDING = 18;
    private static final int TOP = 20;
    private static final int ROW_H = 17;
    private static final int ROW_GAP = 1;
    private static final int LEFT_WIDTH = 208;
    private static final long REQUEST_INTERVAL_MS = 1200L;
    private final Screen parent;
    private final List<GDRowRenderer> rightRows = new ArrayList<GDRowRenderer>();
    private final GDScrollState rightScroll = new GDScrollState();
    private GDButton backButton;
    private GDButton refreshButton;
    private GDButton quickJoinButton;
    private long lastRequestAtMs;

    public LoadoutParkourLeaderboardScreen(Screen parent) {
        super(Component.literal("\u8dd1\u9177\u6392\u884c\u699c"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.backButton = new GDButton(this.width - 86, this.height - 28, 72, 20, Component.literal("\u8fd4\u56de"), btn -> this.closeToParent());
        this.refreshButton = new GDButton(this.width - 166, this.height - 28, 72, 20, Component.literal("\u5237\u65b0"), btn -> this.requestSnapshot(true));
        this.quickJoinButton = new GDButton(18, this.height - 28, 96, 20, Component.literal("\u5feb\u901f\u52a0\u5165\u8dd1\u9177"), btn -> this.quickJoin());
        this.quickJoinButton.setAutoScaleTextToFit(true);
        this.quickJoinButton.setMinAutoScale(0.58f);
        this.rightScroll.reset();
        this.requestSnapshot(true);
    }

    @Override
    public void tick() {
        super.tick();
        this.requestSnapshot(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        BattlefieldUiTheme.background(guiGraphics, this.width, this.height);
        int leftX1 = 18;
        int leftX2 = Math.min(this.width / 2 - 8, leftX1 + 208);
        int rightX1 = leftX2 + 18;
        int rightX2 = this.width - 18;
        int headerY = 20;
        int contentY1 = 38;
        int contentY2 = this.height - 36;
        BattlefieldUiTheme.drawText(guiGraphics, "\u8dd1\u9177\u6392\u884c\u699c", 18, 6, -6237968);
        this.renderLeftPanel(guiGraphics, leftX1, leftX2, headerY, contentY1, contentY2);
        this.renderRightPanel(guiGraphics, mouseX, mouseY, partialTick, rightX1, rightX2, headerY, contentY1, contentY2);
        if (this.quickJoinButton != null) {
            boolean active;
            ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
            this.quickJoinButton.active = active = snapshot != null && snapshot.quickJoinAvailable();
            this.quickJoinButton.setTextColor(active ? -1 : -5592406);
            this.quickJoinButton.setY(this.height - 28);
            this.quickJoinButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.refreshButton != null) {
            this.refreshButton.setX(this.width - 166);
            this.refreshButton.setY(this.height - 28);
            this.refreshButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.backButton != null) {
            this.backButton.setX(this.width - 86);
            this.backButton.setY(this.height - 28);
            this.backButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.backButton != null && this.backButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.refreshButton != null && this.refreshButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.quickJoinButton != null && this.quickJoinButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.isMouseOverRightPanel(mouseX, mouseY)) {
                int left = Math.min(this.width / 2 - 8, 226) + 18;
                int right = this.width - 18;
                int top = 38;
                int bottom = this.height - 36;
                this.rightScroll.startDragging(mouseX, mouseY, left, top, right, bottom, this.getLeaderboardContentHeight());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.rightScroll.stopDragging();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.isMouseOverRightPanel(mouseX, mouseY)) {
            return this.rightScroll.mouseScrolled(delta, Math.max(1, this.height - 38 - 36), this.getLeaderboardContentHeight());
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 295) { BattlefieldUiTheme.cycle(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.closeToParent();
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

    private void quickJoin() {
        ClientParkourLeaderboardState.Snapshot snapshot = ClientParkourLeaderboardState.snapshot();
        if (snapshot == null || !snapshot.quickJoinAvailable()) {
            return;
        }
        NetworkManager.getChannel().sendToServer(ParkourActionPacket.quickJoin());
        this.requestSnapshot(true);
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

    private boolean isMouseOverRightPanel(double mouseX, double mouseY) {
        int left = Math.min(this.width / 2 - 8, 226) + 18;
        int right = this.width - 18;
        int top = 38;
        int bottom = this.height - 36;
        return mouseX >= (double)left && mouseX <= (double)right && mouseY >= (double)top && mouseY <= (double)bottom;
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
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
}

