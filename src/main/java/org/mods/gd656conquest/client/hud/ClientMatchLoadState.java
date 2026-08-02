package org.mods.gd656conquest.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;

/**
 * 对局加载界面的客户端状态。
 */
public final class ClientMatchLoadState {

    public static final int LOAD_DURATION_TICKS = 140; // 7 seconds
    public static final int POST_FADE_DURATION_TICKS = 20; // 1 second
    public static final int TOTAL_DURATION_TICKS = LOAD_DURATION_TICKS + POST_FADE_DURATION_TICKS;
    private static final int STANDARD_INSET = 12;
    private static final int LINE_GAP_AFTER_SUBTITLE_2 = 5;
    private static final int TIP_START_GAP = 3;
    private static final int TIP_LINE_GAP = 2;
    private static final int BOX5_WIDTH = 30;
    private static final int BOX6_WIDTH = 160;
    private static final int BOX6_HEIGHT = 100;
    private static final int BACKGROUND_30_BLACK = 0x4D000000;

    private static final float PHASE_1_END = 40.0F;
    private static final float PHASE_3_DURATION = 10.0F;
    private static final float PHASE2_DARKNESS = 0.20F;

    private static boolean active;
    private static boolean ending;
    private static int elapsedTicks;
    private static float loadDuration = LOAD_DURATION_TICKS;
    private static LoadingCopy loadingCopy = LoadingCopy.empty();

    private ClientMatchLoadState() {
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public static void start(
        int serverLoadDurationTicks,
        String roomDisplayName,
        String mapName,
        String dimensionId,
        String roomDescription,
        String tipLine1,
        String tipLine2,
        String tipLine3
    ) {
        active = true;
        ending = false;
        elapsedTicks = 0;
        loadDuration = Math.max(1, serverLoadDurationTicks);
        loadingCopy = new LoadingCopy(
            safe(roomDisplayName),
            safe(mapName),
            safe(dimensionId),
            safe(roomDescription),
            safe(tipLine1),
            safe(tipLine2),
            safe(tipLine3)
        );
    }

    public static void completeLoad() {
        if (!active) {
            return;
        }
        ending = true;
    }

    public static void clear() {
        active = false;
        ending = false;
        elapsedTicks = 0;
        loadDuration = LOAD_DURATION_TICKS;
        loadingCopy = LoadingCopy.empty();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean shouldLockInput() {
        return active && !ending;
    }

    /**
     * Whether to hide all other HUD elements during the loading phase.
     * True during Phases 1-3; false during Phase 4 (post-fade) to let normal HUD return.
     */
    public static boolean shouldHideAllHud() {
        return active && !ending;
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    public static void tick() {
        if (!active) {
            return;
        }
        elapsedTicks++;
        if (elapsedTicks > loadDuration + POST_FADE_DURATION_TICKS) {
            clear();
        }
    }

    // -----------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------

    public static void render(GuiGraphics gui, float partialTick) {
        if (!active) {
            return;
        }
        float darkness = computeDarkness(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (darkness > 0.0F) {
            int color = ((int) (darkness * 255.0F)) << 24;
            gui.fill(0, 0, width, height, color);
        }
        if (!ending) {
            renderCopy(gui, partialTick, width, height);
        }
    }

    // -----------------------------------------------------------------------
    // Per-frame darkness computation
    // -----------------------------------------------------------------------

    private static float computeDarkness(float partialTick) {
        float t = Math.max(0.0F, (elapsedTicks - 1.0F) + Mth.clamp(partialTick, 0.0F, 1.0F));
        float ld = loadDuration;

        // Phase 4: post-fade from full black to normal
        if (ending && t > ld) {
            float progress = Mth.clamp((t - ld) / POST_FADE_DURATION_TICKS, 0.0F, 1.0F);
            return 1.0F - smoothStep01(progress);
        }

        // Phase 1: fade from full black to 20% darkness (0→2s)
        if (t <= PHASE_1_END) {
            float progress = Mth.clamp(t / PHASE_1_END, 0.0F, 1.0F);
            return 1.0F - (1.0F - PHASE2_DARKNESS) * smoothStep01(progress);
        }

        float phase2End = ld - PHASE_3_DURATION;

        // Phase 2: hold at 20% darkness (2s→6.5s)
        if (t <= phase2End) {
            return PHASE2_DARKNESS;
        }

        // Phase 3: fade back to full black (6.5s→7s)
        float progress = Mth.clamp((t - phase2End) / PHASE_3_DURATION, 0.0F, 1.0F);
        return PHASE2_DARKNESS + (1.0F - PHASE2_DARKNESS) * smoothStep01(progress);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static float smoothStep01(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static void renderCopy(GuiGraphics gui, float partialTick, int screenWidth, int screenHeight) {
        int areaLeft = STANDARD_INSET;
        int areaTop = STANDARD_INSET;
        int areaRight = Math.max(areaLeft + 1, screenWidth - STANDARD_INSET);
        int areaBottom = Math.max(areaTop + 1, screenHeight - STANDARD_INSET);

        int currentTop = areaTop;

        int subtitle1Height = scaledLineHeight(2.5F);
        renderTextBlock(
            gui,
            partialTick,
            loadingCopy.roomDisplayName,
            areaLeft,
            currentTop,
            areaRight,
            currentTop + subtitle1Height,
            2.5F,
            GuiConstants.COLOR_WHITE,
            false,
            BACKGROUND_30_BLACK
        );
        currentTop += subtitle1Height;

        int subtitle2Height = scaledLineHeight(1.5F);
        renderTextBlock(
            gui,
            partialTick,
            loadingCopy.mapName,
            areaLeft,
            currentTop,
            areaRight,
            currentTop + subtitle2Height,
            1.5F,
            GuiConstants.COLOR_WHITE,
            false,
            BACKGROUND_30_BLACK
        );
        currentTop += subtitle2Height + LINE_GAP_AFTER_SUBTITLE_2;

        int subtitle3Height = scaledLineHeight(1.0F);
        renderTextBlock(
            gui,
            partialTick,
            loadingCopy.dimensionId,
            areaLeft,
            currentTop,
            areaRight,
            currentTop + subtitle3Height,
            1.0F,
            GuiConstants.COLOR_WHITE,
            false,
            null
        );
        currentTop += subtitle3Height + TIP_START_GAP;

        currentTop = renderTipLine(gui, partialTick, loadingCopy.tipLine1, areaLeft, currentTop, areaRight);
        currentTop = renderTipLine(gui, partialTick, loadingCopy.tipLine2, areaLeft, currentTop + TIP_LINE_GAP, areaRight);
        renderTipLine(gui, partialTick, loadingCopy.tipLine3, areaLeft, currentTop + TIP_LINE_GAP, areaRight);

        int bottomTextHeight = scaledLineHeight(1.0F);
        renderTextBlock(
            gui,
            partialTick,
            "\u2622 20HZ",
            areaLeft,
            areaBottom - bottomTextHeight,
            areaLeft + BOX5_WIDTH,
            areaBottom,
            1.0F,
            GuiConstants.COLOR_WHITE,
            false,
            null
        );

        renderTextBlock(
            gui,
            partialTick,
            loadingCopy.roomDescription,
            areaRight - BOX6_WIDTH,
            areaBottom - BOX6_HEIGHT,
            areaRight,
            areaBottom,
            1.0F,
            GuiConstants.COLOR_WHITE,
            true,
            null
        );
    }

    private static int renderTipLine(GuiGraphics gui, float partialTick, String content, int x1, int y1, int x2) {
        int height = scaledLineHeight(1.0F);
        renderTextBlock(
            gui,
            partialTick,
            prefixTip(content),
            x1,
            y1,
            x2,
            y1 + height,
            1.0F,
            GuiConstants.COLOR_WHITE,
            false,
            null
        );
        return y1 + height;
    }

    private static void renderTextBlock(
        GuiGraphics gui,
        float partialTick,
        String text,
        int x1,
        int y1,
        int x2,
        int y2,
        float fontSize,
        int color,
        boolean autoWrap,
        Integer backgroundColor
    ) {
        GDTextRenderer renderer = new GDTextRenderer(safe(text), x1, y1, x2, y2, fontSize, color, autoWrap);
        renderer.setCentered(false);
        renderer.setBackgroundColor(backgroundColor);
        renderer.render(gui, partialTick);
    }

    private static int scaledLineHeight(float scale) {
        return Math.max(1, Math.round(9.0F * scale));
    }

    private static String prefixTip(String content) {
        String safeContent = safe(content);
        return safeContent.isBlank() ? "" : "\u25b6  " + safeContent;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record LoadingCopy(
        String roomDisplayName,
        String mapName,
        String dimensionId,
        String roomDescription,
        String tipLine1,
        String tipLine2,
        String tipLine3
    ) {
        private static LoadingCopy empty() {
            return new LoadingCopy("", "", "", "", "", "", "");
        }
    }
}
