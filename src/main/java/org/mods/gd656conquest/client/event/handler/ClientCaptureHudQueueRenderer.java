/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayState;
import org.mods.gd656conquest.client.render.CaptureDiamondRenderer;

final class ClientCaptureHudQueueRenderer {
    ClientCaptureHudQueueRenderer() {
    }

    void renderHudCaptureQueue(ClientCaptureOverlayState state, GuiGraphics gui, Minecraft minecraft, float deltaSeconds, ClientCaptureOverlayModel.TeamToken selfTeam) {
        if(!org.mods.gd656killicon.client.gui.theme.BattlefieldAccessibility.classic()){
            state.hudIconLayoutStates.clear();return;
        }
        List<ClientCaptureOverlayModel.CaptureIconEntry> entries=state.cachedObjectiveEntries;
        if(entries.isEmpty()){state.hudIconLayoutStates.clear();return;}
        String focusCode=state.currentCaptureFocus==null?null:state.currentCaptureFocus.code();
        var focus=focusCode==null?null:state.objectiveEntryByCode.get(focusCode);
        int count=entries.size()-(focus==null?0:1),index=0;float center=gui.guiWidth()/2f,start=center-(count-1)*12;
        var seen=new HashSet<String>();var textBuffer=minecraft.renderBuffers().bufferSource();
        for(var entry:entries){
            seen.add(entry.renderId());boolean focused=entry.code().equals(focusCode);
            updateHudLayoutState(state,entry,focused?center:start+index++*24,focused?67:29,
                focused?ClientCaptureOverlayModel.HUD_FOCUS_ICON_RADIUS:ClientCaptureOverlayModel.HUD_QUEUE_ICON_RADIUS,
                focused?ClientCaptureOverlayModel.HUD_FOCUS_ICON_ALPHA:ClientCaptureOverlayModel.HUD_QUEUE_ICON_ALPHA,
                focused?ClientCaptureOverlayModel.HUD_FOCUS_LABEL_SCALE:ClientCaptureOverlayModel.HUD_QUEUE_LABEL_SCALE,deltaSeconds);
        }
        state.hudIconLayoutStates.keySet().retainAll(seen);
        for(var entry:entries){
            var layout=state.hudIconLayoutStates.get(entry.renderId());if(layout==null||layout.alpha<=.01f)continue;
            CaptureDiamondRenderer.drawCaptureIconInHud(gui,minecraft.font,entry.code(),false,layout.x,layout.y,layout.radius,
                resolveDisplayProgress(state,entry,selfTeam),layout.alpha,toPerspectiveToken(entry.owner(),selfTeam),toPerspectiveToken(entry.driving(),selfTeam),
                layout.labelScale,entry.code().equals(focusCode)?0:1,false,false,textBuffer);
        }
        renderFocusDetails(state,gui,minecraft,focus,textBuffer);textBuffer.endBatch();
    }

    private void updateHudLayoutState(ClientCaptureOverlayState state, ClientCaptureOverlayModel.CaptureIconEntry entry, float targetX, float targetY, float targetRadius, float targetAlpha, float targetLabelScale, float deltaSeconds) {
        ClientCaptureOverlayModel.HudIconLayoutState layout = state.hudIconLayoutStates.get(entry.renderId());
        if (layout == null) {
            state.hudIconLayoutStates.put(entry.renderId(), new ClientCaptureOverlayModel.HudIconLayoutState(targetX, targetY, targetRadius, targetAlpha, targetLabelScale));
            return;
        }
        float step = 1.0f - (float)Math.exp(-Math.max(deltaSeconds, 0.004166667f) / 0.18f);
        layout.x = Mth.lerp(step, layout.x, targetX);
        layout.y = Mth.lerp(step, layout.y, targetY);
        layout.radius = Mth.lerp(step, layout.radius, targetRadius);
        layout.alpha = Mth.lerp(step, layout.alpha, targetAlpha);
        layout.labelScale = Mth.lerp(step, layout.labelScale, targetLabelScale);
    }

    private void renderFocusDetails(ClientCaptureOverlayState state, GuiGraphics gui, Minecraft minecraft, ClientCaptureOverlayModel.CaptureIconEntry focusEntry, MultiBufferSource.BufferSource textBufferSource) {
        ClientCaptureOverlayModel.CaptureFocusData focus = state.currentCaptureFocus;
        if (focusEntry == null || focus == null || !focus.code().equals(focusEntry.code())) {
            return;
        }
        ClientCaptureOverlayModel.HudIconLayoutState layout = state.hudIconLayoutStates.get(focusEntry.renderId());
        if (layout == null) {
            return;
        }
        float blend = Mth.clamp((layout.radius - 5.8f) / Math.max(0.01f, 5.8f), 0.0f, 1.0f);
        float alpha = layout.alpha * blend;
        if (alpha <= 0.01f) {
            return;
        }
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, focus.name(), layout.x, layout.y + layout.radius + 5.0f, 0xFFFFFF, alpha, true, 0.8f, textBufferSource);
        if (focus.enemyCount() <= 0) {
            return;
        }
        float barWidth = 40.0f;
        float barLeft = layout.x - barWidth / 2.0f;
        float barTop = layout.y + layout.radius + 17.0f;
        int total = Math.max(1, focus.allyCount() + focus.enemyCount());
        float allyWidth = Math.max(1.0f, barWidth * ((float)focus.allyCount() / (float)total));
        this.drawHudRect(gui, barLeft, barTop, barLeft + barWidth, barTop + 4.0f, this.withAlpha(0x101010, alpha * 0.48f));
        this.drawHudRect(gui, barLeft, barTop, barLeft + allyWidth, barTop + 4.0f, this.withAlpha(4888319, alpha));
        this.drawHudRect(gui, barLeft + allyWidth, barTop, barLeft + barWidth, barTop + 4.0f, this.withAlpha(0xFF4A4A, alpha));
        String allyText = String.valueOf(focus.allyCount());
        String enemyText = String.valueOf(focus.enemyCount());
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, allyText, barLeft - 5.0f - (float)minecraft.font.width(allyText) * 0.5f, barTop - 2.0f, 10207999, alpha, false, 1.0f, textBufferSource);
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, enemyText, barLeft + barWidth + 5.0f + (float)minecraft.font.width(enemyText) * 0.5f, barTop - 2.0f, 0xFF9B9B, alpha, false, 1.0f, textBufferSource);
    }

    private float resolveDisplayProgress(ClientCaptureOverlayState state, ClientCaptureOverlayModel.CaptureIconEntry entry, ClientCaptureOverlayModel.TeamToken selfTeam) {
        float progress = this.resolveProgress(state, entry);
        if (selfTeam == ClientCaptureOverlayModel.TeamToken.CAMP_A) {
            return 100.0f - progress;
        }
        return progress;
    }

    private float resolveProgress(ClientCaptureOverlayState state, ClientCaptureOverlayModel.CaptureIconEntry entry) {
        ClientCaptureOverlayModel.ProgressInterpolationState interpolationState = state.progressInterpolation.get(entry.renderId());
        return interpolationState == null ? Mth.clamp(entry.progress(), 0.0f, 100.0f) : interpolationState.current();
    }

    private int withAlpha(int rgb, float alpha) {
        int alphaByte = Mth.clamp((int)(alpha * 255.0f), 0, 255);
        return alphaByte << 24 | rgb & 0xFFFFFF;
    }

    private void drawHudRect(GuiGraphics gui, float left, float top, float right, float bottom, int color) {
        Matrix4f matrix = gui.pose().last().pose();
        float alpha = (float)(color >>> 24 & 0xFF) / 255.0f;
        float red = (float)(color >>> 16 & 0xFF) / 255.0f;
        float green = (float)(color >>> 8 & 0xFF) / 255.0f;
        float blue = (float)(color & 0xFF) / 255.0f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, left, bottom, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, right, bottom, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, right, top, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, left, top, 0.0f).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private String toPerspectiveToken(ClientCaptureOverlayModel.TeamToken token, ClientCaptureOverlayModel.TeamToken selfTeam) {
        if (token == ClientCaptureOverlayModel.TeamToken.NEUTRAL || selfTeam == ClientCaptureOverlayModel.TeamToken.NEUTRAL) {
            return "NEUTRAL";
        }
        return token == selfTeam ? "ALLY" : "ENEMY";
    }
}

