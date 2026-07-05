package org.mods.gd656conquest.client.marker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.client.render.CaptureDiamondRenderer;

import java.util.List;

public final class ClientMarkerDebugPreviewState {
    private static final float PREVIEW_Y_RATIO = 0.36F;
    private static final float ENTRY_DURATION_TICKS = 14.0F;
    private static final float MARKER_SHAPE_OPACITY = 0.90F;
    private static final float AUTO_ENEMY_SHAPE_OPACITY = 0.60F;

    private static long previewStartedAtMs;
    private static long previewExpiresAtMs;

    private ClientMarkerDebugPreviewState() {
    }

    public static void startPreview(int durationMs) {
        long now = System.currentTimeMillis();
        previewStartedAtMs = now;
        previewExpiresAtMs = now + Math.max(1000L, durationMs);
    }

    public static void reset() {
        previewStartedAtMs = 0L;
        previewExpiresAtMs = 0L;
    }

    public static boolean isActive() {
        return previewExpiresAtMs > System.currentTimeMillis();
    }

    public static void renderHudOverlay(GuiGraphics gui) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null || gui == null || !isActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        float elapsedTicks = (float) Math.max(0L, now - previewStartedAtMs) / 50.0F;
        float entryProgress = resolveEntryAnimationProgress(elapsedTicks);
        float pulse = 0.5F + 0.5F * Mth.sin(elapsedTicks * 0.16F);
        float helpPulse = (elapsedTicks % 18.0F) / 18.0F;
        float baseY = gui.guiHeight() * PREVIEW_Y_RATIO;
        float startX = gui.guiWidth() * 0.5F - 144.0F;
        float spacing = 96.0F;
        float rowSpacing = 88.0F;
        List<PreviewEntry> entries = List.of(
            new PreviewEntry("普通标记", TacticalMarkerRenderer.MarkerVisualType.NORMAL, false, MARKER_SHAPE_OPACITY, -1.0F, 0, ""),
            new PreviewEntry("敌军地面", TacticalMarkerRenderer.MarkerVisualType.ENEMY_GROUND, false, MARKER_SHAPE_OPACITY, -1.0F, 0, ""),
            new PreviewEntry("敌军实体", TacticalMarkerRenderer.MarkerVisualType.ENEMY_TRACKED, true, MARKER_SHAPE_OPACITY, -1.0F, 0, ""),
            new PreviewEntry("自动敌军", TacticalMarkerRenderer.MarkerVisualType.AUTO_ENEMY, false, AUTO_ENEMY_SHAPE_OPACITY, -1.0F, 0, ""),
            new PreviewEntry("友军永久", TacticalMarkerRenderer.MarkerVisualType.ALLY_PERSISTENT, false, 1.0F, -1.0F, 1, "assault"),
            new PreviewEntry("小队永久", TacticalMarkerRenderer.MarkerVisualType.SQUAD_PERSISTENT, false, 1.0F, -1.0F, 1, "engineer"),
            new PreviewEntry("倒地友军", TacticalMarkerRenderer.MarkerVisualType.DOWNED_ALLY, false, 1.0F, helpPulse, 1, "recon"),
            new PreviewEntry("倒地小队", TacticalMarkerRenderer.MarkerVisualType.DOWNED_SQUAD, false, 1.0F, helpPulse, 1, "support"),
            new PreviewEntry("弹药箱", TacticalMarkerRenderer.MarkerVisualType.AMMO_BOX, false, 1.0F, -1.0F, 2, "")
        );
        for (int i = 0; i < entries.size(); i++) {
            PreviewEntry entry = entries.get(i);
            int column = i % 4;
            int row = entry.row();
            float centerX = startX + spacing * column;
            float centerY = baseY + rowSpacing * row;
            boolean isAmmoBoxEmpty = entry.visualType() == TacticalMarkerRenderer.MarkerVisualType.AMMO_BOX && (elapsedTicks % 40) > 20;
            TacticalMarkerRenderer.drawMarker(
                gui,
                centerX,
                centerY - resolveEntryYOffset(entryProgress),
                1.0F,
                entry.shapeOpacity(),
                pulse,
                entry.hovered(),
                entry.visualType(),
                resolveEntryScale(entryProgress),
                entry.helpPulseProgress(),
                entry.targetClass(),
                isAmmoBoxEmpty
            );
            int textRgb = switch (entry.visualType()) {
                case ENEMY_GROUND, ENEMY_TRACKED, AUTO_ENEMY -> 0x00F1C1C1;
                case ALLY_PERSISTENT, DOWNED_ALLY -> 0x0057F2FF;
                case SQUAD_PERSISTENT, DOWNED_SQUAD -> 0x0079FFAF;
                default -> 0x00E6EEF8;
            };
            CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, entry.label(), centerX, centerY + 24.0F, textRgb, 0.46F, false, 0.62F);
        }
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, "/gdc debug preview marker", gui.guiWidth() * 0.5F, baseY - 34.0F, 0x00E7C75A, 0.34F, true, 0.64F);
        float remainingSeconds = Math.max(0.0F, (previewExpiresAtMs - now) / 1000.0F);
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, String.format(java.util.Locale.ROOT, "%.1fs", remainingSeconds), gui.guiWidth() * 0.5F, baseY - 22.0F, 0x00C8D0DA, 0.28F, false, 0.56F);
    }

    private static float resolveEntryAnimationProgress(float ageTicks) {
        return Mth.clamp(ageTicks / ENTRY_DURATION_TICKS, 0.0F, 1.0F);
    }

    private static float resolveEntryScale(float progress) {
        float x = Mth.clamp(progress, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - x, 3.0D);
    }

    private static float resolveEntryYOffset(float progress) {
        float eased = resolveEntryScale(progress);
        float inverse = 1.0F - eased;
        return inverse * inverse * 10.0F;
    }

    private record PreviewEntry(
        String label,
        TacticalMarkerRenderer.MarkerVisualType visualType,
        boolean hovered,
        float shapeOpacity,
        float helpPulseProgress,
        int row,
        String targetClass
    ) {
    }
}
