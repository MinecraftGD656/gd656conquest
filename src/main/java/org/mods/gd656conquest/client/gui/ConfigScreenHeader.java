package org.mods.gd656conquest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static org.mods.gd656conquest.client.gui.GuiConstants.*;

/**
 * Handles the rendering and logic for the configuration screen header tab bar.
 * Adapted from GD656KilliconServer's ConfigScreenHeader for GD656Conquest.
 */
public class ConfigScreenHeader {

    private final Minecraft minecraft;

    private long openTime;
    private long lastRenderTime;

    private int splitPoint;
    private double scrollX;
    private double targetScrollX;
    private double maxScroll;
    private boolean isDragging;
    private boolean isPressed;
    private double lastMouseX;

    private final List<Tab> tabs = new ArrayList<>();
    private Tab selectedTab;

    public ConfigScreenHeader() {
        this.minecraft = Minecraft.getInstance();
        this.openTime = System.currentTimeMillis();
        this.lastRenderTime = System.currentTimeMillis();
    }

    public void addTab(TabContent content) {
        tabs.add(new Tab(content));
        if (tabs.size() == 1) {
            selectedTab = tabs.get(0);
            selectedTab.isSelected = true;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX > splitPoint && mouseY <= HEADER_HEIGHT + HEADER_CLICK_ZONE) {
            double localMouseX = mouseX - splitPoint + scrollX;

            for (Tab tab : tabs) {
                if (localMouseX >= tab.x && localMouseX <= tab.x + tab.width
                        && mouseY >= tab.finalY && mouseY <= tab.finalY + tab.height) {
                    if (selectedTab != tab) {
                        selectedTab.isSelected = false;
                        selectedTab = tab;
                        selectedTab.isSelected = true;
                        selectedTab.content.onTabOpen();
                    }
                    isDragging = false;
                    isPressed = true;
                    lastMouseX = mouseX;
                    return true;
                }
            }

            isDragging = false;
            isPressed = true;
            lastMouseX = mouseX;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        isPressed = false;
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging) {
            targetScrollX -= dragX;
            scrollX = targetScrollX;
            clampScroll();
            return true;
        }

        if (isPressed && mouseY <= HEADER_HEIGHT + HEADER_SCROLL_ZONE && mouseX > splitPoint) {
            isDragging = true;
            targetScrollX -= dragX;
            scrollX = targetScrollX;
            clampScroll();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX > splitPoint && mouseY <= HEADER_HEIGHT + HEADER_SCROLL_ZONE) {
            targetScrollX -= delta * SCROLL_AMOUNT;
            clampScroll();
            return true;
        }
        return false;
    }

    private void clampScroll() {
        targetScrollX = Mth.clamp(targetScrollX, 0, maxScroll);
        if (isDragging) {
            scrollX = targetScrollX;
        }
    }

    public void render(GuiGraphics guiGraphics, int screenWidth, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float dt = (now - lastRenderTime) / 1000.0f;
        lastRenderTime = now;

        calculateLayout(screenWidth);
        updateScroll(dt);

        renderPart2(guiGraphics, screenWidth, mouseX, mouseY, now, dt);
        renderPart1(guiGraphics, now);
        renderIntroSlice(guiGraphics, screenWidth, now);
    }

    private void updateScroll(float dt) {
        if (!isDragging) {
            double diff = targetScrollX - scrollX;
            if (Math.abs(diff) < 0.1) {
                scrollX = targetScrollX;
            } else {
                scrollX += diff * SCROLL_SMOOTHING * dt;
            }
        }
    }

    private void calculateLayout(int screenWidth) {
        String title = "六五六大战场";
        splitPoint = DEFAULT_PADDING + SPLIT_POINT_OFFSET;
        if (minecraft.font != null) {
            splitPoint = minecraft.font.width(title) + DEFAULT_PADDING * 2 + SPLIT_POINT_OFFSET;
        }

        float currentX = 0;
        for (Tab tab : tabs) {
            tab.width = minecraft.font.width(tab.content.getTitle()) + 10;
            tab.x = (int) currentX;
            tab.finalY = TAB_Y_OFFSET;
            tab.height = HEADER_HEIGHT + HEADER_CLICK_ZONE;
            currentX += tab.width + TAB_SPACING;
        }

        float totalContentWidth = currentX;
        float visibleWidth = screenWidth - splitPoint;
        maxScroll = Math.max(0, totalContentWidth - visibleWidth);

        if (targetScrollX > maxScroll) targetScrollX = maxScroll;
        if (scrollX > maxScroll) scrollX = maxScroll;
    }

    private void renderPart1(GuiGraphics guiGraphics, long now) {
        guiGraphics.fill(0, 0, splitPoint, HEADER_HEIGHT, COLOR_BG);
        guiGraphics.fill(0, HEADER_HEIGHT, splitPoint, HEADER_HEIGHT + GOLD_BAR_HEIGHT, COLOR_THEME);

        guiGraphics.fillGradient(0, HEADER_HEIGHT - 5, splitPoint, HEADER_HEIGHT,
                0x00000000, (0x99 << 24) | (COLOR_THEME & 0x00FFFFFF));

        long elapsed = now - openTime;
        float ease = getEaseOutCubic(Math.min(1.0f, elapsed / (float) INTRO_DURATION_MS));
        float animOffsetX = -TITLE_ANIM_OFFSET * (1.0f - ease);

        String title = "六五六大战场";
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(animOffsetX + DEFAULT_PADDING, (float) DEFAULT_PADDING, 0);
        guiGraphics.drawString(minecraft.font, title, 0, 0, COLOR_THEME, true);
        guiGraphics.pose().popPose();
    }

    private void renderPart2(GuiGraphics guiGraphics, int screenWidth, int mouseX, int mouseY, long now, float dt) {
        guiGraphics.enableScissor(splitPoint, 0, screenWidth, HEADER_HEIGHT + 10);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) (splitPoint - scrollX), 0, 0);

        double localMouseX = mouseX - splitPoint + scrollX;

        renderPart2Background(guiGraphics, screenWidth);
        renderTabs(guiGraphics, localMouseX, mouseY, now, dt);

        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    private void renderPart2Background(GuiGraphics guiGraphics, int screenWidth) {
        float drawEnd = Math.max(screenWidth + (float) maxScroll, 5000);

        if (selectedTab == null) {
            guiGraphics.fill(0, 0, (int) drawEnd, HEADER_HEIGHT, COLOR_BG);
            guiGraphics.fill(0, HEADER_HEIGHT, (int) drawEnd, HEADER_HEIGHT + GOLD_BAR_HEIGHT, COLOR_THEME);
            renderPart2Glow(guiGraphics, 0, (int) drawEnd);
            return;
        }

        if (selectedTab.x > 0) {
            guiGraphics.fill(0, 0, selectedTab.x, HEADER_HEIGHT, COLOR_BG);
            guiGraphics.fill(0, HEADER_HEIGHT, selectedTab.x, HEADER_HEIGHT + GOLD_BAR_HEIGHT, COLOR_THEME);
            renderPart2Glow(guiGraphics, 0, selectedTab.x);
        }

        int tabRight = selectedTab.x + selectedTab.width;
        if (tabRight < drawEnd) {
            guiGraphics.fill(tabRight, 0, (int) drawEnd, HEADER_HEIGHT, COLOR_BG);
            guiGraphics.fill(tabRight, HEADER_HEIGHT, (int) drawEnd, HEADER_HEIGHT + GOLD_BAR_HEIGHT, COLOR_THEME);
            renderPart2Glow(guiGraphics, tabRight, (int) drawEnd);
        }
    }

    private void renderPart2Glow(GuiGraphics guiGraphics, int startX, int endX) {
        int gradientStartY = HEADER_HEIGHT;
        int gradientEndY = HEADER_HEIGHT - 5;
        guiGraphics.fillGradient(startX, gradientEndY, endX, gradientStartY,
                0x00000000, (0x99 << 24) | (COLOR_THEME & 0x00FFFFFF));
    }

    private void renderTabs(GuiGraphics guiGraphics, double localMouseX, int mouseY, long now, float dt) {
        int selectedIndex = tabs.indexOf(selectedTab);

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            boolean isSelected = (tab == selectedTab);
            boolean hovered = localMouseX >= tab.x && localMouseX <= tab.x + tab.width
                    && mouseY >= tab.finalY && mouseY <= tab.finalY + tab.height
                    && mouseY <= HEADER_HEIGHT + HEADER_CLICK_ZONE;

            if (hovered) {
                tab.hoverProgress = Math.min(1.0f, tab.hoverProgress + ANIMATION_SPEED * dt);
            } else {
                tab.hoverProgress = Math.max(0.0f, tab.hoverProgress - ANIMATION_SPEED * dt);
            }

            if (isSelected) {
                tab.selectionProgress = Math.min(1.0f, tab.selectionProgress + ANIMATION_SPEED * dt);
            } else {
                tab.selectionProgress = Math.max(0.0f, tab.selectionProgress - ANIMATION_SPEED * dt);
            }

            int distance = Math.abs(i - selectedIndex);
            long delay = distance * (long) TAB_DELAY_MS;
            long tabIntroElapsed = now - (openTime + delay);
            float introEase = getEaseOutCubic(
                    tabIntroElapsed > 0 ? Math.min(1.0f, tabIntroElapsed / (float) INTRO_DURATION_MS) : 0.0f);

            float textCurrentY = HEADER_HEIGHT + (tab.finalY - HEADER_HEIGHT) * introEase;
            int alpha = Math.max(5, (int) (255 * introEase));

            int baseColor = isSelected ? COLOR_WHITE : (tab.hoverProgress > 0 ? COLOR_DARK_GRAY : COLOR_GRAY);
            int colorWithAlpha = (baseColor & 0x00FFFFFF) | (alpha << 24);

            int textX = tab.x + (tab.width - minecraft.font.width(tab.content.getTitle())) / 2;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(textX, textCurrentY + 8.0f, 0);
            guiGraphics.drawString(minecraft.font, tab.content.getTitle(), 0, 0, colorWithAlpha, true);
            guiGraphics.pose().popPose();

            if (tab.selectionProgress > 0.01f) {
                float borderEase = getEaseOutCubic(tab.selectionProgress);
                int borderCurrentY = (int) (HEADER_HEIGHT + (tab.finalY - HEADER_HEIGHT) * borderEase);

                guiGraphics.fill(tab.x, borderCurrentY, tab.x + tab.width, borderCurrentY + 1, COLOR_THEME);
                guiGraphics.fill(tab.x, borderCurrentY, tab.x + 1, HEADER_HEIGHT + GOLD_BAR_HEIGHT, COLOR_THEME);
                guiGraphics.fill(tab.x + tab.width - 1, borderCurrentY, tab.x + tab.width,
                        HEADER_HEIGHT + GOLD_BAR_HEIGHT, COLOR_THEME);
            }

            if (tab.hoverProgress > 0.01f) {
                float hoverEase = getEaseOutCubic(tab.hoverProgress);
                int hoverCurrentY = (int) (HEADER_HEIGHT + (tab.finalY - HEADER_HEIGHT) * hoverEase);

                RenderSystem.enableBlend();
                guiGraphics.fill(tab.x, hoverCurrentY, tab.x + tab.width, hoverCurrentY + 1, COLOR_HOVER_BORDER);
                guiGraphics.fill(tab.x, hoverCurrentY, tab.x + 1, HEADER_HEIGHT, COLOR_HOVER_BORDER);
                guiGraphics.fill(tab.x + tab.width - 1, hoverCurrentY, tab.x + tab.width, HEADER_HEIGHT, COLOR_HOVER_BORDER);
                RenderSystem.disableBlend();
            }
        }
    }

    private void renderIntroSlice(GuiGraphics guiGraphics, int screenWidth, long now) {
        long elapsed = now - openTime;
        if (elapsed > SLICE_DURATION_MS) return;

        float t = elapsed / (float) SLICE_DURATION_MS;
        float ease = getEaseOutCubic(t);

        float sliceWidth = HEADER_HEIGHT / 3.0f;
        float currentX = screenWidth * ease;

        float alpha = 1.0f - Math.min(1.0f, t * 3.0f);
        if (alpha <= 0.01f) return;

        int color = ((int) (alpha * 255) << 24) | (COLOR_GRAY & 0x00FFFFFF);

        RenderSystem.enableBlend();
        guiGraphics.fill((int) currentX, 0, (int) (currentX + sliceWidth), HEADER_HEIGHT, color);
        RenderSystem.disableBlend();
    }

    private float getEaseOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    public void setSelectedTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            Tab tab = tabs.get(index);
            if (selectedTab != tab) {
                if (selectedTab != null) selectedTab.isSelected = false;
                selectedTab = tab;
                selectedTab.isSelected = true;
                selectedTab.content.onTabOpen();
            }
        }
    }

    public TabContent getSelectedTabContent() {
        return selectedTab != null ? selectedTab.content : null;
    }

    public int getSelectedTabIndex() {
        return tabs.indexOf(selectedTab);
    }

    public int getTabCount() {
        return tabs.size();
    }

    public int getHeaderHeight() {
        return HEADER_HEIGHT + GOLD_BAR_HEIGHT;
    }

    public int getContentTop() {
        return HEADER_HEIGHT + GOLD_BAR_HEIGHT + DEFAULT_PADDING;
    }

    /**
     * Interface for tab content displayed below the header.
     */
    public interface TabContent {
        net.minecraft.network.chat.Component getTitle();
        void onTabOpen();
        void tick();
        void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                    int screenWidth, int screenHeight, int contentTop);
        boolean mouseClicked(double mouseX, double mouseY, int button);
        boolean mouseReleased(double mouseX, double mouseY, int button);
        boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);
        boolean mouseScrolled(double mouseX, double mouseY, double delta);
        boolean keyPressed(int keyCode, int scanCode, int modifiers);
        boolean charTyped(char codePoint, int modifiers);
    }

    private static class Tab {
        TabContent content;
        int x, width, height;
        int finalY;
        boolean isSelected = false;
        float selectionProgress = 0.0f;
        float hoverProgress = 0.0f;

        Tab(TabContent content) {
            this.content = content;
        }
    }
}
