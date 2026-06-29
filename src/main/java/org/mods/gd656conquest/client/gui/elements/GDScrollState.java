package org.mods.gd656conquest.client.gui.elements;

import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.client.gui.GuiConstants;

public final class GDScrollState {
    private double scrollY;
    private double targetScrollY;
    private boolean dragging;
    private double lastMouseY;
    private long lastFrameNanos = System.nanoTime();

    public void reset() {
        scrollY = 0.0D;
        targetScrollY = 0.0D;
        dragging = false;
        lastMouseY = 0.0D;
        lastFrameNanos = System.nanoTime();
    }

    public void setScrollInstant(double value, int viewportHeight, int contentHeight) {
        double clamped = clamp(value, viewportHeight, contentHeight);
        scrollY = clamped;
        targetScrollY = clamped;
    }

    public void update(double mouseY, int viewportHeight, int contentHeight) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000.0f);
        lastFrameNanos = now;
        if (dragging) {
            double diff = mouseY - lastMouseY;
            targetScrollY -= diff;
            lastMouseY = mouseY;
        }
        targetScrollY = clamp(targetScrollY, viewportHeight, contentHeight);
        scrollY = clamp(scrollY, viewportHeight, contentHeight);
        double diff = targetScrollY - scrollY;
        if (Math.abs(diff) < 0.01D) {
            scrollY = targetScrollY;
            return;
        }
        scrollY += diff * GuiConstants.SCROLL_SMOOTHING * dt;
        scrollY = clamp(scrollY, viewportHeight, contentHeight);
    }

    public boolean mouseScrolled(double delta, int viewportHeight, int contentHeight) {
        if (getMaxScroll(viewportHeight, contentHeight) <= 0.0D) {
            return false;
        }
        targetScrollY = clamp(targetScrollY - delta * GuiConstants.SCROLL_AMOUNT, viewportHeight, contentHeight);
        return true;
    }

    public void startDragging(double mouseY) {
        dragging = true;
        lastMouseY = mouseY;
    }

    public void stopDragging() {
        dragging = false;
    }

    public double scrollY() {
        return scrollY;
    }

    public double getMaxScroll(int viewportHeight, int contentHeight) {
        return Math.max(0.0D, contentHeight - Math.max(1, viewportHeight));
    }

    public void renderScrollbar(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int contentHeight) {
        int viewportHeight = Math.max(0, y2 - y1);
        double maxScroll = getMaxScroll(viewportHeight, contentHeight);
        if (viewportHeight <= 0 || maxScroll <= 0.0D) {
            return;
        }
        int trackLeft = x2 - 3;
        int trackRight = x2 - 1;
        guiGraphics.fill(trackLeft, y1, trackRight, y2, 0x44333B42);
        int thumbHeight = Math.max(18, (int) Math.round(viewportHeight * (viewportHeight / (double) contentHeight)));
        int thumbTravel = Math.max(0, viewportHeight - thumbHeight);
        int thumbTop = y1 + (int) Math.round((scrollY / maxScroll) * thumbTravel);
        guiGraphics.fill(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, 0x88B8C6D6);
    }

    private double clamp(double value, int viewportHeight, int contentHeight) {
        double maxScroll = getMaxScroll(viewportHeight, contentHeight);
        return Math.max(0.0D, Math.min(maxScroll, value));
    }
}
