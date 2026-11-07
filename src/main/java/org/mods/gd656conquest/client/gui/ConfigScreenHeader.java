/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
    private final List<Tab> tabs = new ArrayList<Tab>();
    private Tab selectedTab;

    public ConfigScreenHeader() {
        this.minecraft = Minecraft.getInstance();
        this.openTime = System.currentTimeMillis();
        this.lastRenderTime = System.currentTimeMillis();
    }

    public void addTab(TabContent content) {
        this.tabs.add(new Tab(content));
        if (this.tabs.size() == 1) {
            this.selectedTab = this.tabs.get(0);
            this.selectedTab.isSelected = true;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX > (double)this.splitPoint && mouseY <= 30.0) {
            double localMouseX = mouseX - (double)this.splitPoint + this.scrollX;
            for (Tab tab : this.tabs) {
                if (!(localMouseX >= (double)tab.x) || !(localMouseX <= (double)(tab.x + tab.width)) || !(mouseY >= (double)tab.finalY) || !(mouseY <= (double)(tab.finalY + tab.height))) continue;
                if (this.selectedTab != tab) {
                    this.selectedTab.isSelected = false;
                    this.selectedTab = tab;
                    this.selectedTab.isSelected = true;
                    this.selectedTab.content.onTabOpen();
                }
                this.isDragging = false;
                this.isPressed = true;
                this.lastMouseX = mouseX;
                return true;
            }
            this.isDragging = false;
            this.isPressed = true;
            this.lastMouseX = mouseX;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        this.isPressed = false;
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging) {
            this.targetScrollX -= dragX;
            this.scrollX = this.targetScrollX;
            this.clampScroll();
            return true;
        }
        if (this.isPressed && mouseY <= 45.0 && mouseX > (double)this.splitPoint) {
            this.isDragging = true;
            this.targetScrollX -= dragX;
            this.scrollX = this.targetScrollX;
            this.clampScroll();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX > (double)this.splitPoint && mouseY <= 45.0) {
            this.targetScrollX -= delta * 40.0;
            this.clampScroll();
            return true;
        }
        return false;
    }

    private void clampScroll() {
        this.targetScrollX = Mth.clamp(this.targetScrollX, 0.0, this.maxScroll);
        if (this.isDragging) {
            this.scrollX = this.targetScrollX;
        }
    }

    public void render(GuiGraphics guiGraphics, int screenWidth, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastRenderTime) / 1000.0f;
        this.lastRenderTime = now;
        this.calculateLayout(screenWidth);
        this.updateScroll(dt);
        this.renderPart2(guiGraphics, screenWidth, mouseX, mouseY, now, dt);
        this.renderPart1(guiGraphics, now);
        
    }

    private void updateScroll(float dt) {
        if (!this.isDragging) {
            double diff = this.targetScrollX - this.scrollX;
            this.scrollX = Math.abs(diff) < 0.1 ? this.targetScrollX : (this.scrollX += diff * Math.min(1.0, 15.0 * dt));
        }
    }

    private void calculateLayout(int screenWidth) {
        String title = "\u516d\u4e94\u516d\u5927\u6218\u573a";
        this.splitPoint = 33;
        if (this.minecraft.font != null) {
            this.splitPoint = BattlefieldUiTheme.textWidth(title) + 16 + 25;
        }
        float currentX = 0.0f;
        for (Tab tab : this.tabs) {
            tab.width = BattlefieldUiTheme.textWidth(tab.content.getTitle()) + 10;
            tab.x = (int)currentX;
            tab.finalY = 2;
            tab.height = 30;
            currentX += (float)(tab.width + 2);
        }
        float totalContentWidth = currentX;
        float visibleWidth = screenWidth - this.splitPoint;
        this.maxScroll = Math.max(0.0f, totalContentWidth - visibleWidth);
        if (this.targetScrollX > this.maxScroll) {
            this.targetScrollX = this.maxScroll;
        }
        if (this.scrollX > this.maxScroll) {
            this.scrollX = this.maxScroll;
        }
    }

    private void renderPart1(GuiGraphics guiGraphics, long now) {
        var p = BattlefieldUiTheme.palette();
        guiGraphics.fill(0, 0, this.splitPoint, 26, p.header());
        BattlefieldUiTheme.drawText(guiGraphics, "六五六大战场", 8, 10, p.accent());
        guiGraphics.fill(0, 25, this.splitPoint, 26, p.line());
    }

    private void renderPart2(GuiGraphics guiGraphics, int screenWidth, int mouseX, int mouseY, long now, float dt) {
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,this.splitPoint, 0, screenWidth, 35);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float)((double)this.splitPoint - this.scrollX), 0.0f, 0.0f);
        double localMouseX = (double)(mouseX - this.splitPoint) + this.scrollX;
        this.renderPart2Background(guiGraphics, screenWidth);
        this.renderTabs(guiGraphics, localMouseX, mouseY, now, dt);
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    private void renderPart2Background(GuiGraphics guiGraphics, int screenWidth) {
        guiGraphics.fill(0, 0, (int)Math.max(screenWidth + this.maxScroll, 5000), 26, BattlefieldUiTheme.palette().header());
        guiGraphics.fill(0, 25, (int)Math.max(screenWidth + this.maxScroll, 5000), 26, BattlefieldUiTheme.palette().line());
    }

    private void renderPart2Glow(GuiGraphics guiGraphics, int startX, int endX) {
        int gradientStartY = 25;
        int gradientEndY = 20;
        guiGraphics.fillGradient(startX, gradientEndY, endX, gradientStartY, 0, -1720680304);
    }

    private void renderTabs(GuiGraphics guiGraphics, double localMouseX, int mouseY, long now, float dt) {
        var p = BattlefieldUiTheme.palette();
        for (Tab tab : this.tabs) {
            boolean selected = tab == this.selectedTab;
            boolean hovered = localMouseX >= tab.x && localMouseX < tab.x + tab.width && mouseY >= 2 && mouseY < 30;
            BattlefieldUiTheme.tab(guiGraphics, tab.x, 2, tab.width, 24, selected, hovered);
            int color = selected && BattlefieldUiTheme.current() != org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme.BF6 ? p.onSelection() : selected ? p.text() : p.muted();
            BattlefieldUiTheme.centered(guiGraphics, tab.content.getTitle(), tab.x + tab.width / 2, 9, color);
        }
    }

    private void renderIntroSlice(GuiGraphics guiGraphics, int screenWidth, long now) {
        long elapsed = now - this.openTime;
        if (elapsed > 800L) {
            return;
        }
        float t = (float)elapsed / 800.0f;
        float ease = this.getEaseOutCubic(t);
        float sliceWidth = 8.333333f;
        float currentX = (float)screenWidth * ease;
        float alpha = 1.0f - Math.min(1.0f, t * 3.0f);
        if (alpha <= 0.01f) {
            return;
        }
        int color = (int)(alpha * 255.0f) << 24 | 0xAAAAAA;
        RenderSystem.enableBlend();
        guiGraphics.fill((int)currentX, 0, (int)(currentX + sliceWidth), 25, color);
        RenderSystem.disableBlend();
    }

    private float getEaseOutCubic(float t) {
        return 1.0f - (float)Math.pow(1.0f - t, 3.0);
    }

    public void setSelectedTab(int index) {
        Tab tab;
        if (index >= 0 && index < this.tabs.size() && this.selectedTab != (tab = this.tabs.get(index))) {
            if (this.selectedTab != null) {
                this.selectedTab.isSelected = false;
            }
            this.selectedTab = tab;
            this.selectedTab.isSelected = true;
            this.selectedTab.content.onTabOpen();
        }
    }

    public TabContent getSelectedTabContent() {
        return this.selectedTab != null ? this.selectedTab.content : null;
    }

    public int getSelectedTabIndex() {
        return this.tabs.indexOf(this.selectedTab);
    }

    public int getTabCount() {
        return this.tabs.size();
    }

    public int getHeaderHeight() {
        return 26;
    }

    public int getContentTop() {
        return 34;
    }

    private static class Tab {
        TabContent content;
        int x;
        int width;
        int height;
        int finalY;
        boolean isSelected = false;
        float selectionProgress = 0.0f;
        float hoverProgress = 0.0f;

        Tab(TabContent content) {
            this.content = content;
        }
    }

    public static interface TabContent {
        public Component getTitle();

        public void onTabOpen();

        public void tick();

        public void render(GuiGraphics var1, int var2, int var3, float var4, int var5, int var6, int var7);

        public boolean mouseClicked(double var1, double var3, int var5);

        public boolean mouseReleased(double var1, double var3, int var5);

        public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8);

        public boolean mouseScrolled(double var1, double var3, double var5);

        public boolean keyPressed(int var1, int var2, int var3);

        public boolean charTyped(char var1, int var2);
    }
}

