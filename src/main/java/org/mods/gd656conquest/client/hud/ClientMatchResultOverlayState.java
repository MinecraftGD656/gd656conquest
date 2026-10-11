package org.mods.gd656conquest.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.common.match.MatchResultType;

public final class ClientMatchResultOverlayState {
    private static boolean operations;
    private static boolean active;
    private static MatchResultType resultType = MatchResultType.DEFEAT;
    private static int elapsedTicks;
    private static int totalTicks = 200;
    private static int blackStartTicks = 100;
    private static int restoreTicks = 80;
    private static String customSubtitle = "";

    private ClientMatchResultOverlayState() {
    }

    public static void start(MatchResultType matchResultType, int totalDurationTicks, int blackStartDurationTicks, int restoreDurationTicks, String subtitleOverride) {
        operations = false;
        active = true;
        resultType = matchResultType == null ? MatchResultType.DRAW : matchResultType;
        elapsedTicks = 0;
        totalTicks = Math.max(1, totalDurationTicks);
        blackStartTicks = Math.max(0, Math.min(totalTicks, blackStartDurationTicks));
        restoreTicks = Math.max(1, restoreDurationTicks);
        customSubtitle = subtitleOverride == null ? "" : subtitleOverride;
    }

    public static void startOperations(MatchResultType type, int remainingTicks) {
        start(type, remainingTicks, 0, 1, "");
        operations = true;
    }
    public static boolean isOperationsActive() { return active && operations; }

    public static void clear() {
        operations = false;
        active = false;
        elapsedTicks = 0;
        customSubtitle = "";
    }

    public static void tick() {
        if (!active) {
            return;
        }
        elapsedTicks++;
        if (elapsedTicks > totalTicks) {
            clear();
        }
    }

    public static boolean shouldHideConquestHud() {
        if (!active) {
            return false;
        }
        return operations || elapsedTicks >= blackStartTicks;
    }

    public static void render(GuiGraphics gui) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (operations) {
            String outcome = switch (resultType) { case VICTORY -> "胜利"; case DEFEAT -> "失败"; case DRAW -> "平局"; };
            String text = "行动结算 · " + outcome;
            gui.drawString(font, text, width-font.width(text)-14, 36, 0xFFF4F4F4, true);
            return;
        }
        String title = switch (resultType) {
            case VICTORY -> "大获全胜";
            case DEFEAT -> "败北";
            case DRAW -> "势均力敌";
        };
        String subtitle = switch (resultType) {
            case VICTORY -> "我方取得胜利";
            case DEFEAT -> "我方未能取得胜利";
            case DRAW -> "双方未能分出胜负";
        };
        if (customSubtitle != null && !customSubtitle.isBlank()) {
            subtitle = customSubtitle;
        }

        float titleProgress = Mth.clamp((elapsedTicks - 1.0F) / 24.0F, 0.0F, 1.0F);
        float titleAlpha = smoothStep01(titleProgress);
        int titleColor = packFontColorWithAlpha(0x00FFFFFF, titleAlpha);
        int subColor = packFontColorWithAlpha(0x00E8E8E8, titleAlpha * 0.88F);

        if (titleColor != 0) {
            gui.pose().pushPose();
            gui.pose().scale(2.4F, 2.4F, 1.0F);
            int titleW = font.width(title);
            int tx = (int) ((width / 2.0F - titleW * 1.2F) / 2.4F);
            int ty = (int) ((height / 2.0F - 26.0F) / 2.4F);
            gui.drawString(font, title, tx, ty, titleColor, true);
            gui.pose().popPose();
        }

        if (subColor != 0) {
            int subW = font.width(subtitle);
            gui.drawString(font, subtitle, width / 2 - subW / 2, height / 2 + 10, subColor, true);
        }

        float blackAlpha;
        if (elapsedTicks < blackStartTicks) {
            blackAlpha = Mth.clamp(elapsedTicks / (float) Math.max(1, blackStartTicks), 0.0F, 1.0F);
        } else if (elapsedTicks < blackStartTicks + restoreTicks) {
            float t = (elapsedTicks - blackStartTicks) / (float) Math.max(1, restoreTicks);
            blackAlpha = Mth.clamp(1.0F - t, 0.0F, 1.0F);
        } else {
            blackAlpha = 0.0F;
        }
        if (blackAlpha > 0.001F) {
            int color = (((int) (blackAlpha * 255.0F)) << 24);
            gui.fill(0, 0, width, height, color);
        }
    }

    private static int packFontColorWithAlpha(int rgb, float alpha) {
        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        if (alphaByte <= 0) {
            return 0;
        }
        // Font 渲染在 alpha 极低时会视为“无 alpha”并强制不透明，这里保底到 4 避免首帧闪白。
        alphaByte = Math.max(4, alphaByte);
        return (alphaByte << 24) | (rgb & 0x00FFFFFF);
    }

    private static float smoothStep01(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }
}
