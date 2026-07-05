package org.mods.gd656conquest.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayDeque;
import java.util.Deque;

public final class ClientHudSubtitleManager {
    private static final long ONCE_DISPLAY_NANOS = 4_000_000_000L;
    private static final long ONCE_FADE_NANOS = 450_000_000L;
    private static final long QUEUE_REFRESH_NANOS = 2_000_000_000L;
    private static final float SUBTITLE_SCALE = 0.72F;
    private static final int SUBTITLE_RGB = 0x00E9EEF4;

    private static final Deque<SubtitleEntry> queue = new ArrayDeque<>();
    private static SubtitleEntry activeOnce;
    private static long activeOnceStartNanos = -1L;
    private static String persistentKey = "";
    private static String persistentText = "";
    private static DisplayState currentDisplayState;

    private ClientHudSubtitleManager() {
    }

    public static void showOnce(String key, String text) {
        String normalizedKey = normalizeKey(key);
        String normalizedText = normalizeText(text);
        if (normalizedText.isEmpty()) {
            return;
        }
        if (activeOnce != null && activeOnce.key().equals(normalizedKey)) {
            return;
        }
        for (SubtitleEntry entry : queue) {
            if (entry.key().equals(normalizedKey)) {
                return;
            }
        }
        queue.addLast(new SubtitleEntry(normalizedKey, normalizedText));
    }

    public static void setPersistent(String key, String text) {
        String normalizedText = normalizeText(text);
        if (normalizedText.isEmpty()) {
            clearPersistent(key);
            return;
        }
        persistentKey = normalizeKey(key);
        persistentText = normalizedText;
    }

    public static void clearPersistent(String key) {
        String normalizedKey = normalizeKey(key);
        if (!normalizedKey.isEmpty() && !normalizedKey.equals(persistentKey)) {
            return;
        }
        persistentKey = "";
        persistentText = "";
    }

    public static void tick() {
        long now = System.nanoTime();
        if (activeOnce != null) {
            long elapsed = Math.max(0L, now - activeOnceStartNanos);
            if (elapsed >= QUEUE_REFRESH_NANOS && !queue.isEmpty()) {
                activeOnce = queue.removeFirst();
                activeOnceStartNanos = now;
                currentDisplayState = new DisplayState(activeOnce.text(), 1.0F);
                return;
            }
            if (elapsed >= ONCE_DISPLAY_NANOS + ONCE_FADE_NANOS) {
                activeOnce = null;
                activeOnceStartNanos = -1L;
            }
        }
        if (activeOnce == null && !queue.isEmpty()) {
            activeOnce = queue.removeFirst();
            activeOnceStartNanos = now;
        }
        currentDisplayState = resolveDisplayState(now);
    }

    public static void render(GuiGraphics gui, Font font, int centerX, int topY) {
        DisplayState state = currentDisplayState;
        if (state == null) {
            return;
        }
        if (state.alpha() <= 0.05F) {
            return;
        }
        drawHudText(gui, font, state.text(), centerX, topY, state.alpha());
    }

    static String currentText() {
        DisplayState state = currentDisplayState;
        return state == null ? "" : state.text();
    }

    static float currentAlpha() {
        DisplayState state = currentDisplayState;
        return state == null ? 0.0F : state.alpha();
    }

    public static void reset() {
        queue.clear();
        activeOnce = null;
        activeOnceStartNanos = -1L;
        persistentKey = "";
        persistentText = "";
        currentDisplayState = null;
    }

    private static DisplayState resolveDisplayState(long now) {
        if (activeOnce != null) {
            long elapsed = Math.max(0L, now - activeOnceStartNanos);
            float alpha = 1.0F;
            if (elapsed > ONCE_DISPLAY_NANOS) {
                float fadeProgress = Math.min(1.0F, (elapsed - ONCE_DISPLAY_NANOS) / (float) ONCE_FADE_NANOS);
                alpha = 1.0F - fadeProgress;
            }
            if (alpha > 0.0F) {
                return new DisplayState(activeOnce.text(), alpha);
            }
        }
        if (!persistentText.isEmpty()) {
            return new DisplayState(persistentText, 1.0F);
        }
        return null;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() || "-".equals(trimmed) ? "" : trimmed;
    }

    private static void drawHudText(GuiGraphics gui, Font font, String text, int centerX, int topY, float alpha) {
        ClientHudStyledText.Descriptor styled = ClientHudStyledText.parse(text, SUBTITLE_RGB | 0xFF000000);
        if (!styled.visible()) {
            return;
        }
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F))) << 24;
        int color = alphaByte | (styled.currentColor() & 0x00FFFFFF);
        float usingScale = Math.max(0.1F, SUBTITLE_SCALE);
        int drawX = Math.round(centerX / usingScale) - font.width(styled.text()) / 2;
        int drawY = Math.round(topY / usingScale);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.pose().pushPose();
        gui.pose().scale(usingScale, usingScale, 1.0F);
        gui.drawString(font, styled.text(), drawX, drawY, color, true);
        gui.pose().popPose();
        gui.flush();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static void renderPreviewText(GuiGraphics gui, Font font, int centerX, int topY, String text) {
        drawHudText(gui, font, text, centerX, topY, 1.0F);
    }

    private record SubtitleEntry(String key, String text) {
    }

    private record DisplayState(String text, float alpha) {
    }
}
