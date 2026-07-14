package org.mods.gd656conquest.client.support;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDScrollState;
import org.mods.gd656conquest.common.support.SupportHeroKitTypeRegistry;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.SupportUiActionPacket;
import org.mods.gd656conquest.network.packet.SupportUiRequestPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SupportScreen extends Screen {
    private static final int LEFT = 18;
    private static final int TOP = 38;
    private static final int RIGHT = 18;
    private static final int BOTTOM = 34;
    private static final int PANEL_GAP = 12;
    private static final int LEFT_WIDTH = 248;
    private static final int OVERVIEW_MIN_WIDTH = 112;
    private static final int ROW_HEIGHT = 54;
    private static final int ROW_GAP = 6;
    private static final int BUTTON_WIDTH = 84;
    private static final int BUTTON_MIN_WIDTH = 15;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_INNER_PADDING = 10;
    private static final int ICON_SIZE = 34;
    private static final int ICON_GAP = 10;
    private static final int TEXT_BUTTON_GAP = 8;
    private static final int MIN_ENTRY_TEXT_WIDTH = 120;
    private static final int PREFERRED_LIST_WIDTH = 300;
    private static final long REQUEST_INTERVAL_MS = 1000L;

    private final Screen parent;
    private final GDScrollState listScrollState = new GDScrollState();
    private final List<SupportEntryView> entries = new ArrayList<>();
    private final List<GDButton> entryButtons = new ArrayList<>();
    private GDButton refreshButton;
    private GDButton closeButton;
    private long lastRequestAt;

    public SupportScreen(Screen parent) {
        super(Component.translatable("gd656conquest.client.gui.support.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        listScrollState.reset();
        rebuildStaticButtons();
        refreshFromState();
        requestSnapshot(true);
    }

    @Override
    public void tick() {
        super.tick();
        requestSnapshot(false);
        syncEntryButtons();
    }

    public void refreshFromState() {
        entries.clear();
        ClientSupportUiState.Snapshot snapshot = ClientSupportUiState.snapshot();
        if (snapshot != null) {
            for (ClientSupportUiState.SupportEntryState entry : snapshot.entries()) {
                entries.add(SupportEntryView.fromSnapshot(entry));
            }
        }
        syncEntryButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        ClientSupportUiState.Snapshot snapshot = ClientSupportUiState.snapshot();
        guiGraphics.drawString(this.font, this.title, LEFT, 12, GuiConstants.COLOR_WHITE, false);
        guiGraphics.drawString(
            this.font,
            snapshot == null ? "正在同步阵地支援数据..." : buildHeaderSubtitle(snapshot),
            LEFT,
            24,
            0xFFB8C6D6,
            false
        );

        SupportLayout layout = resolveLayout();
        renderOverviewPanel(guiGraphics, snapshot, layout);
        renderEntryList(guiGraphics, mouseX, mouseY, partialTick, snapshot, layout);

        if (refreshButton != null) {
            refreshButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (closeButton != null) {
            closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (refreshButton != null && refreshButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (int index = 0; index < entryButtons.size() && index < entries.size(); index++) {
                GDButton entryButton = entryButtons.get(index);
                if (entryButton.mouseClicked(mouseX, mouseY, button)) {
                    handleEntryClick(entries.get(index));
                    return true;
                }
            }
            if (isMouseOverList(mouseX, mouseY)) {
                listScrollState.startDragging(mouseX, mouseY, getListLeft(), getListTop(), getListRight(), getListBottom(), getListContentHeight());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        listScrollState.stopDragging();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOverList(mouseX, mouseY)) {
            return listScrollState.mouseScrolled(delta, getListViewportHeight(), getListContentHeight());
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        closeNow();
    }

    private void closeNow() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void rebuildStaticButtons() {
        int bottomY = this.height - 26;
        refreshButton = new GDButton(LEFT, bottomY, 88, BUTTON_HEIGHT, Component.literal("刷新"), btn -> requestSnapshot(true));
        closeButton = new GDButton(this.width - RIGHT - 72, bottomY, 72, BUTTON_HEIGHT, Component.literal("关闭"), btn -> closeNow());
    }

    private void syncEntryButtons() {
        while (entryButtons.size() < entries.size()) {
            GDButton button = new GDButton(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal("使用"), btn -> {
            });
            button.setAutoScaleTextToFit(true);
            button.setMinAutoScale(0.35F);
            entryButtons.add(button);
        }
        while (entryButtons.size() > entries.size()) {
            entryButtons.remove(entryButtons.size() - 1);
        }
    }

    private void renderOverviewPanel(GuiGraphics guiGraphics, ClientSupportUiState.Snapshot snapshot, SupportLayout layout) {
        int left = layout.overviewLeft();
        int top = layout.overviewTop();
        int right = layout.overviewRight();
        int bottom = layout.overviewBottom();
        guiGraphics.fill(left, top, right, bottom, 0x4410181F);
        guiGraphics.fill(left, top, right, top + 1, 0x66FFD78A);

        guiGraphics.drawString(this.font, "支援概览", left + 10, top + 8, 0xFFFFD78A, false);
        if (snapshot == null) {
            guiGraphics.drawString(this.font, "正在等待服务器返回数据", left + 10, top + 28, 0xFFB8C6D6, false);
            guiGraphics.drawString(this.font, "界面会自动刷新", left + 10, top + 42, 0xFF8E9BA9, false);
            return;
        }

        int lineY = top + 28;
        int valueMaxWidth = Math.max(16, right - left - 76);
        drawLabelValue(guiGraphics, "房间", safe(snapshot.roomDisplayName()), left + 10, lineY, valueMaxWidth, 0xFFFFFFFF);
        lineY += 16;
        drawLabelValue(guiGraphics, "阵营", safe(snapshot.teamDisplayName()), left + 10, lineY, valueMaxWidth, 0xFF8CC4FF);
        lineY += 16;
        drawLabelValue(guiGraphics, "当前分数", String.valueOf(snapshot.currentScore()), left + 10, lineY, valueMaxWidth, 0xFFEDEDED);
        lineY += 16;
        drawLabelValue(guiGraphics, "已消耗", String.valueOf(snapshot.spentScore()), left + 10, lineY, valueMaxWidth, 0xFFFFB870);
        lineY += 16;
        drawLabelValue(guiGraphics, "剩余分数", String.valueOf(snapshot.remainingScore()), left + 10, lineY, valueMaxWidth, 0xFF8FF0A4);

        if (!snapshot.statusText().isBlank()) {
            int statusTop = bottom - 44;
            guiGraphics.fill(left + 10, statusTop, right - 10, bottom - 10, 0x33151A20);
            guiGraphics.fill(left + 10, statusTop, left + 13, bottom - 10, 0xFF7EC8FF);
            drawWrappedLine(guiGraphics, snapshot.statusText(), left + 20, statusTop + 11, right - left - 34, 0xFFD7D7D7);
        }
    }

    private void renderEntryList(
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        float partialTick,
        ClientSupportUiState.Snapshot snapshot,
        SupportLayout layout
    ) {
        int left = layout.listLeft();
        int top = layout.listTop();
        int right = layout.listRight();
        int bottom = layout.listBottom();

        guiGraphics.fill(left, top, right, bottom, 0x2210181F);
        guiGraphics.drawString(this.font, "当前可用支援", left, top - 16, 0xFFFFFFFF, false);

        if (snapshot == null) {
            guiGraphics.drawString(this.font, "正在加载支援列表...", left + 10, top + 10, 0xFFB8C6D6, false);
            return;
        }
        if (entries.isEmpty()) {
            String message = snapshot.statusText().isBlank() ? "当前没有可显示的支援项" : snapshot.statusText();
            guiGraphics.drawString(this.font, message, left + 10, top + 10, 0xFFB8C6D6, false);
            return;
        }

        int viewportHeight = getListViewportHeight();
        int contentHeight = getListContentHeight();
        listScrollState.update(mouseY, viewportHeight, contentHeight);
        syncEntryButtons();
        guiGraphics.enableScissor(left, top, right, bottom);
        for (int index = 0; index < entries.size(); index++) {
            int rowY = top + index * (ROW_HEIGHT + ROW_GAP) - (int) Math.round(listScrollState.scrollY());
            if (rowY + ROW_HEIGHT <= top || rowY >= bottom) {
                continue;
            }
            renderEntryRow(guiGraphics, mouseX, mouseY, partialTick, entries.get(index), entryButtons.get(index), rowY, layout);
        }
        guiGraphics.disableScissor();
        listScrollState.renderScrollbar(guiGraphics, left, top, right, bottom, contentHeight);
    }

    private void renderEntryRow(
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        float partialTick,
        SupportEntryView entry,
        GDButton button,
        int rowY,
        SupportLayout layout
    ) {
        int left = layout.listLeft();
        int right = layout.listRight();
        int accentColor = entry.available()
            ? (entry.isShelling() ? 0xFFF0B35D : 0xFF6DE08B)
            : 0xFF9AA3AD;
        int backgroundColor = entry.available() ? 0x33151A20 : 0x2621262C;
        guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, backgroundColor);
        guiGraphics.fill(left, rowY, left + 4, rowY + ROW_HEIGHT, accentColor);
        guiGraphics.fill(left, rowY, right, rowY + 1, 0x22E6EEF8);

        int textLeft = left + LIST_INNER_PADDING;
        if (layout.renderIcons()) {
            renderEntryIcon(guiGraphics, entry, textLeft, rowY + 10, ICON_SIZE);
            textLeft += ICON_SIZE + ICON_GAP;
        }
        int buttonWidth = layout.buttonWidth();
        int buttonX = right - buttonWidth - LIST_INNER_PADDING;
        int textWidth = Math.max(16, buttonX - TEXT_BUTTON_GAP - textLeft);

        guiGraphics.drawString(this.font, fitText(entry.displayName(), textWidth), textLeft, rowY + 8, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font, fitText(entry.subtitleText(), textWidth), textLeft, rowY + 21, 0xFF8CC4FF, false);
        guiGraphics.drawString(this.font, fitText(entry.detailText(), textWidth), textLeft, rowY + 34, entry.available() ? 0xFFB8C6D6 : 0xFFFFA58C, false);

        if (button != null) {
            button.setX(buttonX);
            button.setY(rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2);
            button.setWidth(buttonWidth);
            button.setHeight(BUTTON_HEIGHT);
            button.setMessage(Component.literal(resolveCompactButtonText(entry.buttonText(), buttonWidth)));
            button.active = entry.available();
            button.setTextColor(entry.available() ? GuiConstants.COLOR_WHITE : GuiConstants.COLOR_GRAY);
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderEntryIcon(GuiGraphics guiGraphics, SupportEntryView entry, int x, int y, int size) {
        guiGraphics.fill(x, y, x + size, y + size, 0x22101010);
        ResourceLocation texture = entry.icon();
        if (texture != null) {
            int iconSize = Math.min(22, size - 8);
            int iconX = x + (size - iconSize) / 2;
            int iconY = y + (size - iconSize) / 2;
            guiGraphics.blit(texture, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            return;
        }
        guiGraphics.drawCenteredString(
            this.font,
            entry.isShelling() ? "炮" : "?",
            x + size / 2,
            y + 12,
            entry.isShelling() ? 0xFFFFD78A : 0xFFB8C6D6
        );
    }

    private void drawLabelValue(GuiGraphics guiGraphics, String label, String value, int x, int y, int valueMaxWidth, int valueColor) {
        guiGraphics.drawString(this.font, label + ":", x, y, 0xFF9AA6B8, false);
        guiGraphics.drawString(this.font, fitText(safe(value), valueMaxWidth), x + 56, y, valueColor, false);
    }

    private void drawWrappedLine(GuiGraphics guiGraphics, String text, int x, int y, int maxWidth, int color) {
        List<?> lines = this.font.split(Component.literal(safe(text)), maxWidth);
        for (int index = 0; index < Math.min(lines.size(), 2); index++) {
            guiGraphics.drawString(this.font, (net.minecraft.util.FormattedCharSequence) lines.get(index), x, y + index * 12, color);
        }
    }

    private String buildHeaderSubtitle(ClientSupportUiState.Snapshot snapshot) {
        return String.format(
            Locale.ROOT,
            "%s  |  %s  |  剩余分数 %d",
            safe(snapshot.roomDisplayName()),
            safe(snapshot.teamDisplayName()),
            snapshot.remainingScore()
        );
    }

    private void requestSnapshot(boolean force) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastRequestAt < REQUEST_INTERVAL_MS) {
            return;
        }
        lastRequestAt = now;
        NetworkManager.getChannel().sendToServer(new SupportUiRequestPacket());
    }

    private void handleEntryClick(SupportEntryView entry) {
        if (entry == null || !entry.available() || minecraft == null || minecraft.player == null) {
            return;
        }
        if (entry.isShelling()) {
            NetworkManager.getChannel().sendToServer(SupportUiActionPacket.startShelling(entry.id()));
        } else {
            NetworkManager.getChannel().sendToServer(SupportUiActionPacket.deployHeroKit(entry.id()));
        }
        closeNow();
    }

    private boolean isMouseOverList(double mouseX, double mouseY) {
        return mouseX >= getListLeft()
            && mouseX <= getListRight()
            && mouseY >= getListTop()
            && mouseY <= getListBottom();
    }

    private int getListLeft() {
        return resolveLayout().listLeft();
    }

    private int getListRight() {
        return resolveLayout().listRight();
    }

    private int getListTop() {
        return resolveLayout().listTop();
    }

    private int getListBottom() {
        return resolveLayout().listBottom();
    }

    private int getListViewportHeight() {
        return Math.max(1, getListBottom() - getListTop());
    }

    private int getListContentHeight() {
        return entries.isEmpty() ? 0 : entries.size() * (ROW_HEIGHT + ROW_GAP);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private SupportLayout resolveLayout() {
        int top = TOP;
        int bottom = this.height - BOTTOM;
        int availableWidth = Math.max(1, this.width - LEFT - RIGHT - PANEL_GAP);
        int overviewMinWidth = Math.min(OVERVIEW_MIN_WIDTH, Math.max(72, availableWidth / 3));
        int overviewWidth = Math.min(LEFT_WIDTH, availableWidth);
        if (availableWidth - overviewWidth < PREFERRED_LIST_WIDTH) {
            overviewWidth = Math.max(overviewMinWidth, availableWidth - PREFERRED_LIST_WIDTH);
        }
        overviewWidth = Math.max(overviewMinWidth, Math.min(overviewWidth, Math.max(overviewMinWidth, availableWidth - 1)));
        int listLeft = LEFT + overviewWidth + PANEL_GAP;
        int listRight = this.width - RIGHT;
        int listWidth = Math.max(1, listRight - listLeft);

        boolean renderIcons = computeEntryTextWidth(listWidth, true, BUTTON_WIDTH) >= MIN_ENTRY_TEXT_WIDTH;
        int buttonWidth = BUTTON_WIDTH;
        if (computeEntryTextWidth(listWidth, renderIcons, buttonWidth) < MIN_ENTRY_TEXT_WIDTH) {
            int iconWidth = renderIcons ? ICON_SIZE + ICON_GAP : 0;
            int computedButtonWidth = listWidth - LIST_INNER_PADDING * 2 - iconWidth - TEXT_BUTTON_GAP - MIN_ENTRY_TEXT_WIDTH;
            buttonWidth = Math.max(BUTTON_MIN_WIDTH, Math.min(BUTTON_WIDTH, computedButtonWidth));
        }

        return new SupportLayout(
            LEFT,
            top,
            LEFT + overviewWidth,
            bottom,
            listLeft,
            top,
            listRight,
            bottom,
            buttonWidth,
            renderIcons
        );
    }

    private int computeEntryTextWidth(int listWidth, boolean renderIcons, int buttonWidth) {
        int iconWidth = renderIcons ? ICON_SIZE + ICON_GAP : 0;
        return listWidth - LIST_INNER_PADDING * 2 - iconWidth - TEXT_BUTTON_GAP - buttonWidth;
    }

    private String fitText(String text, int maxWidth) {
        String normalized = safe(text);
        if (maxWidth <= 0 || normalized.isBlank()) {
            return maxWidth <= 0 ? "" : normalized;
        }
        if (this.font.width(normalized) <= maxWidth) {
            return normalized;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return this.font.plainSubstrByWidth(normalized, maxWidth);
        }
        return this.font.plainSubstrByWidth(normalized, maxWidth - ellipsisWidth) + ellipsis;
    }

    private String resolveCompactButtonText(String baseText, int width) {
        String normalized = safe(baseText);
        if (width >= 40 || normalized.isBlank()) {
            return normalized;
        }
        if (normalized.contains("锁")) {
            return "锁";
        }
        if (normalized.contains("选")) {
            return "选";
        }
        if (normalized.contains("用")) {
            return "用";
        }
        return normalized.substring(0, 1);
    }

    private record SupportEntryView(
        String kind,
        String id,
        String displayName,
        String typeKey,
        String subtitleText,
        String detailText,
        String buttonText,
        boolean available,
        ResourceLocation icon
    ) {
        private static SupportEntryView fromSnapshot(ClientSupportUiState.SupportEntryState entry) {
            String kind = entry == null ? "" : entry.kind();
            String id = entry == null ? "" : entry.id();
            String displayName = entry == null || entry.displayName().isBlank() ? id : entry.displayName();
            String typeKey = entry == null ? "" : entry.typeKey();
            String subtitleText = entry == null ? "" : entry.subtitleText();
            String detailText = entry == null ? "" : entry.detailText();
            String buttonText = entry == null ? "" : entry.buttonText();
            ResourceLocation icon = isHeroKitKind(kind) ? SupportHeroKitTypeRegistry.texture(typeKey) : null;
            boolean available = entry != null && entry.available();
            return new SupportEntryView(kind, id, displayName, typeKey, subtitleText, detailText, buttonText, available, icon);
        }

        private static boolean isHeroKitKind(String kind) {
            return "hero_kit".equalsIgnoreCase(kind);
        }

        private boolean isShelling() {
            return "shelling".equalsIgnoreCase(kind);
        }
    }

    private record SupportLayout(
        int overviewLeft,
        int overviewTop,
        int overviewRight,
        int overviewBottom,
        int listLeft,
        int listTop,
        int listRight,
        int listBottom,
        int buttonWidth,
        boolean renderIcons
    ) {
    }
}
