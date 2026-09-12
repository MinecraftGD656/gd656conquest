package org.mods.gd656conquest.client.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.ConfigScreenHeader;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RoomStatisticTabContent implements ConfigScreenHeader.TabContent {
    private static final int LEFT = GuiConstants.SCREEN_EDGE_DISTANCE;
    private static final int ROW_H = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ROW_GAP = 2;

    private final Component title = Component.literal("房间统计数据");
    private final Minecraft minecraft;
    private final List<GDRowRenderer> rowRenderers = new ArrayList<>();
    private final List<ClientLoadoutUiState.RoomStatisticState> entries = new ArrayList<>();
    private final GDScrollState scrollState = new GDScrollState();
    private GDRowRenderer headerRenderer;

    public RoomStatisticTabContent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public void onTabOpen() {
        scrollState.reset();
        refreshFromSnapshot();
    }

    @Override
    public void tick() {
        refreshFromSnapshot();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                       int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int x1 = LEFT;
        int x2 = screenWidth - LEFT;
        ColumnLayout layout = computeColumnLayout(Math.max(1, x2 - x1));
        int y1 = top + ROW_H + ROW_GAP;
        int y2 = screenHeight - 17;
        int viewH = Math.max(1, y2 - y1);
        int contentHeight = entries.size() * (ROW_H + ROW_GAP);
        scrollState.update(mouseY, viewH, contentHeight);

        guiGraphics.drawString(minecraft.font, "房间统计数据", LEFT, top - 10, GuiConstants.COLOR_TITLE_TEXT, false);

        if (headerRenderer == null) {
            headerRenderer = new GDRowRenderer(x1, top, x2, top + ROW_H,
                GuiConstants.COLOR_GOLD_ORANGE & 0x00FFFFFF, 0.76f, true);
        }
        headerRenderer.setBounds(x1, top, x2, top + ROW_H);
        headerRenderer.resetColumnConfig();
        headerRenderer.addColumn("", layout.edgeWidth(), GuiConstants.COLOR_WHITE, true, true, null);
        headerRenderer.addColumn("房间ID", layout.roomIdWidth(), 0xFF9FD3FF, false, true, null);
        headerRenderer.addColumn("房间名称", layout.roomNameWidth(), 0xFFEDEDED, true, false, null);
        headerRenderer.addColumn("地图ID", layout.mapIdWidth(), 0xFFFFE59A, true, false, null);
        headerRenderer.addColumn("游玩人次", layout.totalPlayCountWidth(), 0xFFC8F5C8, false, true, null);
        headerRenderer.addColumn("总局次", layout.totalMatchesWidth(), 0xFFB8D8FF, true, true, null);
        headerRenderer.addColumn("campA胜场次", layout.campAWinsWidth(), 0xFFBDF5BD, false, true, null);
        headerRenderer.addColumn("campB胜场次", layout.campBWinsWidth(), 0xFFFFBABA, false, true, null);
        headerRenderer.addColumn("平局", layout.drawWidth(), 0xFFFFE596, true, true, null);
        headerRenderer.addColumn("平均时长", layout.averageMinutesWidth(), 0xFFD6D9FF, true, true, null);
        headerRenderer.addColumn("房间评分", layout.ratingWidth(), 0xFF7EDEFF, true, true, null);
        headerRenderer.addColumn("", layout.edgeWidth(), GuiConstants.COLOR_GRAY, true, true, null);
        headerRenderer.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.enableScissor(x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -scrollState.scrollY(), 0);
        for (int i = 0; i < entries.size(); i++) {
            int rowTop = y1 + i * (ROW_H + ROW_GAP);
            int rowBottom = rowTop + ROW_H;
            float screenTop = (float) (rowTop - scrollState.scrollY());
            float screenBottom = (float) (rowBottom - scrollState.scrollY());
            if (screenBottom <= y1 || screenTop >= y2) {
                continue;
            }
            renderEntryRow(guiGraphics, mouseX, (int) (mouseY + scrollState.scrollY()),
                partialTick, i, entries.get(i), x1, rowTop, x2, rowBottom, layout);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        scrollState.renderScrollbar(guiGraphics, x1, y1, x2, y2, contentHeight);

        guiGraphics.drawString(minecraft.font, "总数: " + entries.size(), LEFT, screenHeight - 17, 0xFFB8C6D6, false);
    }

    private void renderEntryRow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                int index, ClientLoadoutUiState.RoomStatisticState entry,
                                int x1, int y1, int x2, int y2, ColumnLayout layout) {
        while (rowRenderers.size() <= index) {
            rowRenderers.add(new GDRowRenderer(x1, y1, x2, y2,
                GuiConstants.COLOR_BLACK & 0xFFFFFF, 0.26f, false));
        }
        GDRowRenderer row = rowRenderers.get(index);
        row.setBounds(x1, y1, x2, y2);
        row.setBackgroundColor((index % 2 == 0 ? 0x00141B24 : 0x0010181F) & 0xFFFFFF);
        row.setBackgroundAlpha(index % 2 == 0 ? 0.18f : 0.34f);
        row.resetColumnConfig();
        row.addColumn("", layout.edgeWidth(), GuiConstants.COLOR_GRAY, true, true, null);
        row.addColumn(entry.roomId(), layout.roomIdWidth(), GuiConstants.COLOR_SKY_BLUE, false, true, null);
        row.addColumn(safe(entry.roomDisplayName()), layout.roomNameWidth(), 0xFFEDEDED, true, false, null);
        row.addColumn(safe(entry.mapId()), layout.mapIdWidth(), 0xFFFFE59A, true, false, null);
        row.addColumn(String.valueOf(entry.totalPlayCount()), layout.totalPlayCountWidth(), 0xFFC8F5C8, false, true, null);
        row.addColumn(String.valueOf(entry.totalMatches()), layout.totalMatchesWidth(), 0xFFB8D8FF, true, true, null);
        row.addColumn(String.valueOf(entry.campAWins()), layout.campAWinsWidth(), 0xFFBDF5BD, false, true, null);
        row.addColumn(String.valueOf(entry.campBWins()), layout.campBWinsWidth(), 0xFFFFBABA, false, true, null);
        row.addColumn(String.valueOf(entry.drawMatches()), layout.drawWidth(), 0xFFFFE596, true, true, null);
        row.addColumn(formatAverageMinutes(entry), layout.averageMinutesWidth(), 0xFFD6D9FF, true, true, null);
        row.addColumn(formatRoomScore(entry), layout.ratingWidth(), 0xFF7EDEFF, true, true, null);
        row.addColumn("", layout.edgeWidth(), GuiConstants.COLOR_GRAY, true, true, null);
        row.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void refreshFromSnapshot() {
        ClientLoadoutUiState.Snapshot snapshot = ClientLoadoutUiState.snapshot();
        entries.clear();
        if (snapshot != null) {
            entries.addAll(snapshot.sortedRoomStatistics());
        }
    }

    public boolean mouseClickedContent(double mouseX, double mouseY, int button,
                                       int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int x1 = LEFT;
        int y1 = top + ROW_H + ROW_GAP;
        int x2 = screenWidth - LEFT;
        int y2 = screenHeight - 17;
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
            scrollState.startDragging(mouseX, mouseY, x1, y1, x2, y2, entries.size() * (ROW_H + ROW_GAP));
            return true;
        }
        return false;
    }

    public boolean mouseScrolledContent(double mouseX, double mouseY, double delta,
                                        int screenWidth, int screenHeight, int contentTop) {
        int top = contentTop + 6;
        int x1 = LEFT;
        int y1 = top + ROW_H + ROW_GAP;
        int x2 = screenWidth - LEFT;
        int y2 = screenHeight - 17;
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
            return scrollState.mouseScrolled(delta, y2 - y1, entries.size() * (ROW_H + ROW_GAP));
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollState.stopDragging();
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
        double minutes = entry.totalMatchDurationSeconds() / 60.0D / entry.totalMatches();
        return String.format(Locale.ROOT, "%.1f", minutes);
    }

    private String formatRoomScore(ClientLoadoutUiState.RoomStatisticState entry) {
        if (entry == null || entry.ratingVoteCount() <= 0) {
            return "-";
        }
        double score = entry.ratingScoreTotal() / (double) entry.ratingVoteCount();
        return String.format(Locale.ROOT, "%.1f", score);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ColumnLayout computeColumnLayout(int panelWidth) {
        int edgeWidth = ROW_H;
        int[] preferred = new int[] {60, 124, 100, 66, 58, 96, 96, 50, 72, 68};
        int[] minimum = new int[] {48, 76, 66, 50, 46, 72, 72, 40, 54, 52};
        int[] growWeights = new int[] {1, 5, 3, 1, 1, 2, 2, 1, 1, 1};
        int available = Math.max(0, panelWidth - edgeWidth * 2);
        int[] widths = fitWidths(available, preferred, minimum, growWeights);
        return new ColumnLayout(
            edgeWidth,
            widths[0],
            widths[1],
            widths[2],
            widths[3],
            widths[4],
            widths[5],
            widths[6],
            widths[7],
            widths[8],
            widths[9]
        );
    }

    private int[] fitWidths(int available, int[] preferred, int[] minimum, int[] growWeights) {
        int count = preferred.length;
        int[] widths = new int[count];
        int preferredTotal = 0;
        int minimumTotal = 0;
        for (int i = 0; i < count; i++) {
            preferredTotal += preferred[i];
            minimumTotal += minimum[i];
        }
        if (available >= preferredTotal) {
            System.arraycopy(preferred, 0, widths, 0, count);
            distributeRemainder(widths, available - preferredTotal, growWeights);
            return widths;
        }
        if (available >= minimumTotal) {
            System.arraycopy(minimum, 0, widths, 0, count);
            int[] shrinkWeights = new int[count];
            for (int i = 0; i < count; i++) {
                shrinkWeights[i] = Math.max(0, preferred[i] - minimum[i]);
            }
            distributeRemainder(widths, available - minimumTotal, shrinkWeights);
            return widths;
        }
        int safeAvailable = Math.max(0, available);
        int assigned = 0;
        for (int i = 0; i < count; i++) {
            widths[i] = safeAvailable <= 0 ? 0 : safeAvailable * minimum[i] / Math.max(1, minimumTotal);
            assigned += widths[i];
        }
        int diff = safeAvailable - assigned;
        for (int i = 0; diff > 0; i = (i + 1) % count) {
            widths[i]++;
            diff--;
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
            widths[widths.length - 1] += extra;
            return;
        }
        int assigned = 0;
        for (int i = 0; i < widths.length; i++) {
            int weight = Math.max(0, weights[i]);
            if (weight <= 0) {
                continue;
            }
            int share = extra * weight / weightTotal;
            widths[i] += share;
            assigned += share;
        }
        int remainder = extra - assigned;
        for (int i = 0; remainder > 0; i = (i + 1) % widths.length) {
            if (Math.max(0, weights[i]) <= 0) {
                continue;
            }
            widths[i]++;
            remainder--;
        }
    }

    private record ColumnLayout(
        int edgeWidth,
        int roomIdWidth,
        int roomNameWidth,
        int mapIdWidth,
        int totalPlayCountWidth,
        int totalMatchesWidth,
        int campAWinsWidth,
        int campBWinsWidth,
        int drawWidth,
        int averageMinutesWidth,
        int ratingWidth
    ) {
    }
}
