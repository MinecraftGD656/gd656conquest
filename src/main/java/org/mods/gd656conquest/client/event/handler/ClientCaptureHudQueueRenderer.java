package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.CaptureFocusData;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.CaptureIconEntry;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.HudIconLayoutState;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.render.CaptureDiamondRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClientCaptureHudQueueRenderer {
    void renderHudCaptureQueue(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        Minecraft minecraft,
        float deltaSeconds,
        TeamToken selfTeam
    ) {
        List<CaptureIconEntry> captureEntries = new ArrayList<>();
        for (CaptureIconEntry entry : state.cachedCaptureIcons) {
            if (!entry.isSpawnPoint()
                && !entry.isDebugMarker()
                && !entry.isTeammateMarker()
                && !entry.isBeaconMarker()
                && !entry.isVehicleMarker()) {
                captureEntries.add(entry);
            }
        }
        if (captureEntries.isEmpty()) {
            state.hudIconLayoutStates.clear();
            return;
        }
        captureEntries.sort(Comparator.comparing(CaptureIconEntry::code));
        int centerX = gui.guiWidth() / 2;
        float queueY = 29.0F;
        float focusX = centerX;
        float focusY = 67.0F;
        String focusCode = state.currentCaptureFocus == null ? null : state.currentCaptureFocus.code();
        List<CaptureIconEntry> queueEntries = new ArrayList<>();
        for (CaptureIconEntry entry : captureEntries) {
            if (!entry.code().equals(focusCode)) {
                queueEntries.add(entry);
            }
        }
        int spacing = 24;
        float queueStartX = centerX - (queueEntries.size() - 1) * spacing / 2.0F;
        MultiBufferSource.BufferSource textBufferSource = minecraft.renderBuffers().bufferSource();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < queueEntries.size(); i++) {
            CaptureIconEntry entry = queueEntries.get(i);
            seen.add(entry.renderId());
            updateHudLayoutState(
                state,
                entry,
                queueStartX + i * spacing,
                queueY,
                ClientCaptureOverlayModel.HUD_QUEUE_ICON_RADIUS,
                ClientCaptureOverlayModel.HUD_QUEUE_ICON_ALPHA,
                ClientCaptureOverlayModel.HUD_QUEUE_LABEL_SCALE,
                deltaSeconds
            );
        }
        if (focusCode != null) {
            for (CaptureIconEntry entry : captureEntries) {
                if (!entry.code().equals(focusCode)) {
                    continue;
                }
                seen.add(entry.renderId());
                updateHudLayoutState(
                    state,
                    entry,
                    focusX,
                    focusY,
                    ClientCaptureOverlayModel.HUD_FOCUS_ICON_RADIUS,
                    ClientCaptureOverlayModel.HUD_FOCUS_ICON_ALPHA,
                    ClientCaptureOverlayModel.HUD_FOCUS_LABEL_SCALE,
                    deltaSeconds
                );
                break;
            }
        }
        state.hudIconLayoutStates.keySet().retainAll(seen);
        for (CaptureIconEntry entry : captureEntries) {
            HudIconLayoutState layout = state.hudIconLayoutStates.get(entry.renderId());
            if (layout == null || layout.alpha <= 0.01F) {
                continue;
            }
            CaptureDiamondRenderer.drawCaptureIconInHud(
                gui,
                minecraft.font,
                entry.code(),
                false,
                layout.x,
                layout.y,
                layout.radius,
                resolveDisplayProgress(state, entry, selfTeam),
                layout.alpha,
                toPerspectiveToken(entry.owner(), selfTeam),
                toPerspectiveToken(entry.driving(), selfTeam),
                layout.labelScale,
                entry.code().equals(focusCode) ? 0.0F : 1.0F,
                false,
                false,
                textBufferSource
            );
        }
        renderFocusDetails(state, gui, minecraft, focusCode, textBufferSource);
        textBufferSource.endBatch();
    }

    private void updateHudLayoutState(
        ClientCaptureOverlayState state,
        CaptureIconEntry entry,
        float targetX,
        float targetY,
        float targetRadius,
        float targetAlpha,
        float targetLabelScale,
        float deltaSeconds
    ) {
        HudIconLayoutState layout = state.hudIconLayoutStates.get(entry.renderId());
        if (layout == null) {
            state.hudIconLayoutStates.put(entry.renderId(), new HudIconLayoutState(targetX, targetY, targetRadius, targetAlpha, targetLabelScale));
            return;
        }
        float step = 1.0F - (float) Math.exp(-Math.max(deltaSeconds, 1.0F / 240.0F) / ClientCaptureOverlayModel.ICON_LAYOUT_APPROACH_SECONDS);
        layout.x = Mth.lerp(step, layout.x, targetX);
        layout.y = Mth.lerp(step, layout.y, targetY);
        layout.radius = Mth.lerp(step, layout.radius, targetRadius);
        layout.alpha = Mth.lerp(step, layout.alpha, targetAlpha);
        layout.labelScale = Mth.lerp(step, layout.labelScale, targetLabelScale);
    }

    private void renderFocusDetails(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        Minecraft minecraft,
        String focusCode,
        MultiBufferSource.BufferSource textBufferSource
    ) {
        CaptureFocusData focus = state.currentCaptureFocus;
        if (focusCode == null || focus == null || !focus.code().equals(focusCode)) {
            return;
        }
        CaptureIconEntry focusEntry = null;
        for (CaptureIconEntry entry : state.cachedCaptureIcons) {
            if (!entry.isSpawnPoint() && entry.code().equals(focusCode)) {
                focusEntry = entry;
                break;
            }
        }
        if (focusEntry == null) {
            return;
        }
        HudIconLayoutState layout = state.hudIconLayoutStates.get(focusEntry.renderId());
        if (layout == null) {
            return;
        }
        float blend = Mth.clamp(
            (layout.radius - ClientCaptureOverlayModel.HUD_QUEUE_ICON_RADIUS)
                / Math.max(0.01F, ClientCaptureOverlayModel.HUD_FOCUS_ICON_RADIUS - ClientCaptureOverlayModel.HUD_QUEUE_ICON_RADIUS),
            0.0F,
            1.0F
        );
        float alpha = layout.alpha * blend;
        if (alpha <= 0.01F) {
            return;
        }
        CaptureDiamondRenderer.drawCenteredHudText(
            gui,
            minecraft.font,
            focus.name(),
            layout.x,
            layout.y + layout.radius + 5.0F,
            0x00FFFFFF,
            alpha,
            true,
            0.8F,
            textBufferSource
        );
        if (focus.enemyCount() <= 0) {
            return;
        }
        float barWidth = 40.0F;
        float barLeft = layout.x - barWidth / 2.0F;
        float barTop = layout.y + layout.radius + 17.0F;
        int total = Math.max(1, focus.allyCount() + focus.enemyCount());
        float allyWidth = Math.max(1.0F, barWidth * (focus.allyCount() / (float) total));
        drawHudRect(gui, barLeft, barTop, barLeft + barWidth, barTop + 4.0F, withAlpha(0x00101010, alpha * 0.48F));
        drawHudRect(gui, barLeft, barTop, barLeft + allyWidth, barTop + 4.0F, withAlpha(0x004A96FF, alpha));
        drawHudRect(gui, barLeft + allyWidth, barTop, barLeft + barWidth, barTop + 4.0F, withAlpha(0x00FF4A4A, alpha));
        String allyText = String.valueOf(focus.allyCount());
        String enemyText = String.valueOf(focus.enemyCount());
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, allyText, barLeft - 5.0F - minecraft.font.width(allyText) * 0.5F, barTop - 2.0F, 0x009BC2FF, alpha, false, 1.0F, textBufferSource);
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, enemyText, barLeft + barWidth + 5.0F + minecraft.font.width(enemyText) * 0.5F, barTop - 2.0F, 0x00FF9B9B, alpha, false, 1.0F, textBufferSource);
    }

    private float resolveDisplayProgress(ClientCaptureOverlayState state, CaptureIconEntry entry, TeamToken selfTeam) {
        float progress = resolveProgress(state, entry);
        if (selfTeam == TeamToken.CAMP_A) {
            return 100.0F - progress;
        }
        return progress;
    }

    private float resolveProgress(ClientCaptureOverlayState state, CaptureIconEntry entry) {
        ClientCaptureOverlayModel.ProgressInterpolationState interpolationState = state.progressInterpolation.get(entry.renderId());
        return interpolationState == null ? Mth.clamp(entry.progress(), 0.0F, 100.0F) : interpolationState.current();
    }

    private int withAlpha(int rgb, float alpha) {
        int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (alphaByte << 24) | (rgb & 0x00FFFFFF);
    }

    private void drawHudRect(GuiGraphics gui, float left, float top, float right, float bottom, int color) {
        Matrix4f matrix = gui.pose().last().pose();
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, left, bottom, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, right, bottom, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, right, top, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, left, top, 0.0F).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private String toPerspectiveToken(TeamToken token, TeamToken selfTeam) {
        if (token == TeamToken.NEUTRAL || selfTeam == TeamToken.NEUTRAL) {
            return "NEUTRAL";
        }
        return token == selfTeam ? "ALLY" : "ENEMY";
    }
}
