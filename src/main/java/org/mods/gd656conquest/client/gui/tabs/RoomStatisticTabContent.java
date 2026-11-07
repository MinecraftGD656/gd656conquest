/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.tabs;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;

public final class RoomStatisticTabContent
implements ConfigScreenHeader.TabContent {
    private static final int LEFT = 8;
    private static final int ROW_H = 17;
    private static final int ROW_GAP = 2;
    private final Component title = Component.literal("\u623f\u95f4\u7edf\u8ba1\u6570\u636e");
    private final Minecraft minecraft;
    private final List<GDRowRenderer> rowRenderers = new ArrayList<GDRowRenderer>();
    private final List<ClientLoadoutUiState.RoomStatisticState> entries = new ArrayList<ClientLoadoutUiState.RoomStatisticState>();
    private final GDScrollState scrollState = new GDScrollState();
    private GDRowRenderer headerRenderer;

    public RoomStatisticTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public void onTabOpen() {
        this.scrollState.reset();
        this.refreshFromSnapshot();
    }

    @Override
    public void tick() {
        this.refreshFromSnapshot();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int x1 = 8;
        int x2 = screenWidth - 8;
        ColumnLayout layout = this.computeColumnLayout(Math.max(1, x2 - x1));
        int y1 = top + 17 + 2;
        int y2 = screenHeight - 17;
        int viewH = Math.max(1, y2 - y1);
        int contentHeight = this.entries.size() * 19;
        this.scrollState.update(mouseY, viewH, contentHeight);
        BattlefieldUiTheme.drawText(guiGraphics, "\u623f\u95f4\u7edf\u8ba1\u6570\u636e", 8, top - 10, -6237968);
        if (this.headerRenderer == null) {
            this.headerRenderer = new GDRowRenderer(x1, top, x2, top + 17, 0x8A9AA8, 0.76f, true);
        }
        this.headerRenderer.setBounds(x1, top, x2, top + 17);
        this.headerRenderer.resetColumnConfig();
        this.headerRenderer.addColumn("", layout.edgeWidth(), -1, true, true, null);
        this.headerRenderer.addColumn("\u623f\u95f4ID", layout.roomIdWidth(), -6302721, false, true, null);
        this.headerRenderer.addColumn("\u623f\u95f4\u540d\u79f0", layout.roomNameWidth(), -1184275, true, false, null);
        this.headerRenderer.addColumn("\u5730\u56feID", layout.mapIdWidth(), -6758, true, false, null);
        this.headerRenderer.addColumn("\u6e38\u73a9\u4eba\u6b21", layout.totalPlayCountWidth(), -3607096, false, true, null);
        this.headerRenderer.addColumn("\u603b\u5c40\u6b21", layout.totalMatchesWidth(), -4663041, true, true, null);
        this.headerRenderer.addColumn("campA\u80dc\u573a\u6b21", layout.campAWinsWidth(), -4328003, false, true, null);
        this.headerRenderer.addColumn("campB\u80dc\u573a\u6b21", layout.campBWinsWidth(), -17734, false, true, null);
        this.headerRenderer.addColumn("\u5e73\u5c40", layout.drawWidth(), -6762, true, true, null);
        this.headerRenderer.addColumn("\u5e73\u5747\u65f6\u957f", layout.averageMinutesWidth(), -2696705, true, true, null);
        this.headerRenderer.addColumn("\u623f\u95f4\u8bc4\u5206", layout.ratingWidth(), -8462593, true, true, null);
        this.headerRenderer.addColumn("", layout.edgeWidth(), -5592406, true, true, null);
        this.headerRenderer.render(guiGraphics, mouseX, mouseY, partialTick);
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0, -this.scrollState.scrollY(), 0.0);
        for (int i = 0; i < this.entries.size(); ++i) {
            int rowTop = y1 + i * 19;
            int rowBottom = rowTop + 17;
            float screenTop = (float)((double)rowTop - this.scrollState.scrollY());
            float screenBottom = (float)((double)rowBottom - this.scrollState.scrollY());
            if (screenBottom <= (float)y1 || screenTop >= (float)y2) continue;
            this.renderEntryRow(guiGraphics, mouseX, (int)((double)mouseY + this.scrollState.scrollY()), partialTick, i, this.entries.get(i), x1, rowTop, x2, rowBottom, layout);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        this.scrollState.renderScrollbar(guiGraphics, x1, y1, x2, y2, contentHeight);
        BattlefieldUiTheme.drawText(guiGraphics, "\u603b\u6570: " + this.entries.size(), 8, screenHeight - 17, -4667690);
    }

    private void renderEntryRow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int index, ClientLoadoutUiState.RoomStatisticState entry, int x1, int y1, int x2, int y2, ColumnLayout layout) {
        while (this.rowRenderers.size() <= index) {
            this.rowRenderers.add(new GDRowRenderer(x1, y1, x2, y2, 5267568, 0.26f, false));
        }
        GDRowRenderer row = this.rowRenderers.get(index);
        row.setBounds(x1, y1, x2, y2);
        row.setBackgroundColor((index % 2 == 0 ? 1317668 : 1054751) & 0xFFFFFF);
        row.setBackgroundAlpha(index % 2 == 0 ? 0.18f : 0.34f);
        row.resetColumnConfig();
        row.addColumn("", layout.edgeWidth(), -5592406, true, true, null);
        row.addColumn(entry.roomId(), layout.roomIdWidth(), -7876885, false, true, null);
        row.addColumn(this.safe(entry.roomDisplayName()), layout.roomNameWidth(), -1184275, true, false, null);
        row.addColumn(this.safe(entry.mapId()), layout.mapIdWidth(), -6758, true, false, null);
        row.addColumn(String.valueOf(entry.totalPlayCount()), layout.totalPlayCountWidth(), -3607096, false, true, null);
        row.addColumn(String.valueOf(entry.totalMatches()), layout.totalMatchesWidth(), -4663041, true, true, null);
        row.addColumn(String.valueOf(entry.campAWins()), layout.campAWinsWidth(), -4328003, false, true, null);
        row.addColumn(String.valueOf(entry.campBWins()), layout.campBWinsWidth(), -17734, false, true, null);
        row.addColumn(String.valueOf(entry.drawMatches()), layout.drawWidth(), -6762, true, true, null);
        row.addColumn(this.formatAverageMinutes(entry), layout.averageMinutesWidth(), -2696705, true, true, null);
        row.addColumn(this.formatRoomScore(entry), layout.ratingWidth(), -8462593, true, true, null);
        row.addColumn("", layout.edgeWidth(), -5592406, true, true, null);
        row.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void refreshFromSnapshot() {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        this.entries.clear();
        if (snapshot != null) {
            this.entries.addAll(snapshot.sortedRoomStatistics());
        }
    }

    public boolean mouseClickedContent(double mouseX, double mouseY, int button, int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int x1 = 8;
        int y1 = top + 17 + 2;
        int x2 = screenWidth - 8;
        int y2 = screenHeight - 17;
        if (mouseX >= (double)x1 && mouseX <= (double)x2 && mouseY >= (double)y1 && mouseY <= (double)y2) {
            this.scrollState.startDragging(mouseX, mouseY, x1, y1, x2, y2, this.entries.size() * 19);
            return true;
        }
        return false;
    }

    public boolean mouseScrolledContent(double mouseX, double mouseY, double delta, int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int x1 = 8;
        int y1 = top + 17 + 2;
        int x2 = screenWidth - 8;
        int y2 = screenHeight - 17;
        if (mouseX >= (double)x1 && mouseX <= (double)x2 && mouseY >= (double)y1 && mouseY <= (double)y2) {
            return this.scrollState.mouseScrolled(delta, y2 - y1, this.entries.size() * 19);
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollState.stopDragging();
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

    private String formatAverageMinutes(ClientLoadoutUiState.RoomStatisticState entry) {
        if (entry == null || entry.totalMatches() <= 0) {
            return "-";
        }
        double minutes = (double)entry.totalMatchDurationSeconds() / 60.0 / (double)entry.totalMatches();
        return String.format(Locale.ROOT, "%.1f", minutes);
    }

    private String formatRoomScore(ClientLoadoutUiState.RoomStatisticState entry) {
        if (entry == null || entry.ratingVoteCount() <= 0) {
            return "-";
        }
        double score = (double)entry.ratingScoreTotal() / (double)entry.ratingVoteCount();
        return String.format(Locale.ROOT, "%.1f", score);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ColumnLayout computeColumnLayout(int panelWidth) {
        int edgeWidth = 17;
        int[] preferred = new int[]{60, 124, 100, 66, 58, 96, 96, 50, 72, 68};
        int[] minimum = new int[]{48, 76, 66, 50, 46, 72, 72, 40, 54, 52};
        int[] growWeights = new int[]{1, 5, 3, 1, 1, 2, 2, 1, 1, 1};
        int available = Math.max(0, panelWidth - edgeWidth * 2);
        int[] widths = this.fitWidths(available, preferred, minimum, growWeights);
        return new ColumnLayout(edgeWidth, widths[0], widths[1], widths[2], widths[3], widths[4], widths[5], widths[6], widths[7], widths[8], widths[9]);
    }

    private int[] fitWidths(int available, int[] preferred, int[] minimum, int[] growWeights) {
        int count = preferred.length;
        int[] widths = new int[count];
        int preferredTotal = 0;
        int minimumTotal = 0;
        for (int i = 0; i < count; ++i) {
            preferredTotal += preferred[i];
            minimumTotal += minimum[i];
        }
        if (available >= preferredTotal) {
            System.arraycopy(preferred, 0, widths, 0, count);
            this.distributeRemainder(widths, available - preferredTotal, growWeights);
            return widths;
        }
        if (available >= minimumTotal) {
            System.arraycopy(minimum, 0, widths, 0, count);
            int[] shrinkWeights = new int[count];
            for (int i = 0; i < count; ++i) {
                shrinkWeights[i] = Math.max(0, preferred[i] - minimum[i]);
            }
            this.distributeRemainder(widths, available - minimumTotal, shrinkWeights);
            return widths;
        }
        int safeAvailable = Math.max(0, available);
        int assigned = 0;
        for (int i = 0; i < count; ++i) {
            widths[i] = safeAvailable <= 0 ? 0 : safeAvailable * minimum[i] / Math.max(1, minimumTotal);
            assigned += widths[i];
        }
        int i = 0;
        for (int diff = safeAvailable - assigned; diff > 0; --diff) {
            int n = i;
            widths[n] = widths[n] + 1;
            i = (i + 1) % count;
        }
        return widths;
    }

    private void distributeRemainder(int[] widths, int extra, int[] weights) {
        if (extra <= 0) {
            return;
        }
        int weightTotal = 0;
        for (int weight : weights) {
            weightTotal += Math.max(0, weight);
        }
        if (weightTotal <= 0) {
            int n = widths.length - 1;
            widths[n] = widths[n] + extra;
            return;
        }
        int assigned = 0;
        for (int i = 0; i < widths.length; ++i) {
            int weight = Math.max(0, weights[i]);
            if (weight <= 0) continue;
            int share = extra * weight / weightTotal;
            int n = i;
            widths[n] = widths[n] + share;
            assigned += share;
        }
        int remainder = extra - assigned;
        int i = 0;
        while (remainder > 0) {
            if (Math.max(0, weights[i]) > 0) {
                int n = i;
                widths[n] = widths[n] + 1;
                --remainder;
            }
            i = (i + 1) % widths.length;
        }
    }

    private record ColumnLayout(int edgeWidth, int roomIdWidth, int roomNameWidth, int mapIdWidth, int totalPlayCountWidth, int totalMatchesWidth, int campAWinsWidth, int campBWinsWidth, int drawWidth, int averageMinutesWidth, int ratingWidth) {
    }
}

