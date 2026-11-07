/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements;

import net.minecraft.client.gui.GuiGraphics;

public final class GDScrollState {
    private static final int SCROLLBAR_TRACK_WIDTH = 3;
    private double scrollY;
    private double targetScrollY;
    private boolean dragging;
    private boolean draggingScrollbar;
    private double lastMouseY;
    private long lastFrameNanos = System.nanoTime();

    public void reset() {
        this.scrollY = 0.0;
        this.targetScrollY = 0.0;
        this.dragging = false;
        this.draggingScrollbar = false;
        this.lastMouseY = 0.0;
        this.lastFrameNanos = System.nanoTime();
    }

    public void setScrollInstant(double value, int viewportHeight, int contentHeight) {
        double clamped;
        this.scrollY = clamped = this.clamp(value, viewportHeight, contentHeight);
        this.targetScrollY = clamped;
    }

    public void update(double mouseY, int viewportHeight, int contentHeight) {
        double diff;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (float)(now - this.lastFrameNanos) / 1.0E9f);
        this.lastFrameNanos = now;
        if (this.dragging) {
            diff = mouseY - this.lastMouseY;
            if (this.draggingScrollbar) {
                int travel = Math.max(0, viewportHeight - thumbHeight(viewportHeight, contentHeight));
                this.targetScrollY += travel > 0 ? diff * this.getMaxScroll(viewportHeight, contentHeight) / travel : 0.0;
            } else {
                this.targetScrollY -= diff;
            }
            this.lastMouseY = mouseY;
        }
        this.targetScrollY = this.clamp(this.targetScrollY, viewportHeight, contentHeight);
        this.scrollY = this.clamp(this.scrollY, viewportHeight, contentHeight);
        diff = this.targetScrollY - this.scrollY;
        if (Math.abs(diff) < 0.01) {
            this.scrollY = this.targetScrollY;
            return;
        }
        this.scrollY += diff * 15.0 * (double)dt;
        this.scrollY = this.clamp(this.scrollY, viewportHeight, contentHeight);
    }

    public boolean mouseScrolled(double delta, int viewportHeight, int contentHeight) {
        if (this.getMaxScroll(viewportHeight, contentHeight) <= 0.0) {
            return false;
        }
        this.targetScrollY = this.clamp(this.targetScrollY - delta * 40.0, viewportHeight, contentHeight);
        return true;
    }

    public void startDragging(double mouseX, double mouseY, int x1, int y1, int x2, int y2, int contentHeight) {
        this.dragging = true;
        this.draggingScrollbar = this.isMouseOverScrollbar(mouseX, mouseY, x1, y1, x2, y2, contentHeight);
        this.lastMouseY = mouseY;
    }

    public void stopDragging() {
        this.dragging = false;
        this.draggingScrollbar = false;
    }

    public double scrollY() {
        return this.scrollY;
    }

    public double getMaxScroll(int viewportHeight, int contentHeight) {
        return Math.max(0.0, (double)(contentHeight - Math.max(1, viewportHeight)));
    }

    public boolean isMouseOverScrollbar(double mouseX, double mouseY, int x1, int y1, int x2, int y2, int contentHeight) {
        int viewportHeight = Math.max(0, y2 - y1);
        double maxScroll = this.getMaxScroll(viewportHeight, contentHeight);
        if (viewportHeight <= 0 || maxScroll <= 0.0) {
            return false;
        }
        int trackLeft = x2 - 3;
        int trackRight = x2 - 1;
        return mouseX >= (double)trackLeft && mouseX <= (double)trackRight && mouseY >= (double)y1 && mouseY <= (double)y2;
    }

    public void renderScrollbar(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int contentHeight) {
        int viewportHeight = Math.max(0, y2 - y1);
        double maxScroll = this.getMaxScroll(viewportHeight, contentHeight);
        if (viewportHeight <= 0 || maxScroll <= 0.0) {
            return;
        }
        int trackLeft = x2 - 3;
        int trackRight = x2 - 1;
        guiGraphics.fill(trackLeft, y1, trackRight, y2, 1144208194);
        int thumbHeight = thumbHeight(viewportHeight, contentHeight);
        int thumbTravel = Math.max(0, viewportHeight - thumbHeight);
        int thumbTop = y1 + (int)Math.round(this.scrollY / maxScroll * (double)thumbTravel);
        guiGraphics.fill(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, -2001156394);
    }

    private static int thumbHeight(int viewportHeight, int contentHeight) {
        return Math.min(Math.max(0, viewportHeight), Math.max(18,
            (int)Math.round((double)viewportHeight * viewportHeight / Math.max(1, contentHeight))));
    }

    private double clamp(double value, int viewportHeight, int contentHeight) {
        double maxScroll = this.getMaxScroll(viewportHeight, contentHeight);
        return Math.max(0.0, Math.min(maxScroll, value));
    }
}

