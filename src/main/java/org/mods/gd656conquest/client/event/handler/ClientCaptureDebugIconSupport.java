package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.CaptureIconEntry;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.ScreenProjection;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.render.CaptureDiamondRenderer;

import java.util.Locale;

final class ClientCaptureDebugIconSupport {
    void renderDebugText(GuiGraphics gui, Minecraft minecraft, ScreenProjection projection, CaptureIconEntry entry, float weightedAlpha) {
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        renderDebugText(gui, minecraft, projection, entry, weightedAlpha, bufferSource);
        bufferSource.endBatch();
    }

    void renderDebugText(
        GuiGraphics gui,
        Minecraft minecraft,
        ScreenProjection projection,
        CaptureIconEntry entry,
        float weightedAlpha,
        MultiBufferSource.BufferSource bufferSource
    ) {
        float textAlpha = weightedAlpha * 0.94F;
        String line1 = entry.note() == null || entry.note().isBlank() ? entry.code() : entry.note();
        String line2 = String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", entry.x(), entry.y(), entry.z());
        int labelColor = resolveDebugMarkerRgb(entry);
        CaptureDiamondRenderer.drawCenteredHudText(
            gui,
            minecraft.font,
            line1,
            (float) projection.screenX(),
            (float) projection.screenY() + ClientCaptureOverlayModel.CAPTURE_ICON_RADIUS + 6.0F,
            labelColor,
            textAlpha,
            true,
            0.64F,
            bufferSource
        );
        CaptureDiamondRenderer.drawCenteredHudText(
            gui,
            minecraft.font,
            line2,
            (float) projection.screenX(),
            (float) projection.screenY() + ClientCaptureOverlayModel.CAPTURE_ICON_RADIUS + 14.0F,
            0x00C8D0DA,
            textAlpha,
            false,
            0.56F,
            bufferSource
        );
    }

    String resolveDebugMarkerGlyph(CaptureIconEntry entry) {
        String code = entry.code() == null ? "" : entry.code().toUpperCase(Locale.ROOT);
        if (code.startsWith("DP_")) return "M";
        if (code.startsWith("AT_") || code.startsWith("BT_")) return "\u25C8";
        if (code.startsWith("EP_")) return "E";
        if (code.startsWith("QS_")) return "Q";
        if (code.startsWith("BTA_") || code.startsWith("BTB_")) return "\u2726";
        if (code.startsWith("BV")) return "V";
        if (code.startsWith("OSA_") || code.startsWith("OSB_")) return "\u25CC";
        if (code.startsWith("OS_")) return "\u25CB";
        if (code.startsWith("IS_")) return "\u25CF";
        return "\u25C6";
    }

    int resolveDebugMarkerRgb(CaptureIconEntry entry) {
        String code = entry.code() == null ? "" : entry.code().toUpperCase(Locale.ROOT);
        if (code.startsWith("TS_")) return 0x1F4FAF;
        if (code.startsWith("SS_")) return 0x9F2A2A;
        if (code.startsWith("AT_")) return 0x4E82FF;
        if (code.startsWith("BT_")) return 0xE05B5B;
        if (code.startsWith("QS_")) return 0xB8C0CC;
        if (code.startsWith("DP_")) return 0x34B261;
        if (code.startsWith("BV")) return 0x6EB9D6;
        if (code.startsWith("EP_")) return 0xD6B321;
        if (code.startsWith("BTA_")) return 0x4E82FF;
        if (code.startsWith("BTB_")) return 0xE05B5B;
        if (code.startsWith("OSA_")) return 0x7DA7FF;
        if (code.startsWith("OSB_")) return 0xFF8A8A;
        if (code.startsWith("OS_")) {
            if (entry.owner() == TeamToken.CAMP_A) return 0x8FB3FF;
            if (entry.owner() == TeamToken.CAMP_B) return 0xFF9B9B;
            return 0xC8CCD2;
        }
        if (code.startsWith("IS_")) {
            if (entry.owner() == TeamToken.CAMP_A) return 0x6E98FF;
            if (entry.owner() == TeamToken.CAMP_B) return 0xFF7F7F;
            return 0xB8C0CC;
        }
        return 0xDADADA;
    }

    String resolveDebugOwnerToken(CaptureIconEntry entry) {
        String code = entry.code() == null ? "" : entry.code().toUpperCase(Locale.ROOT);
        if (code.startsWith("TS_")) return "DBG_CAMP_A_SPAWN";
        if (code.startsWith("SS_")) return "DBG_CAMP_B_SPAWN";
        if (code.startsWith("QS_")) return "DBG_QUEUE";
        if (code.startsWith("DP_")) return "DBG_DEPLOY";
        if (code.startsWith("EP_")) return "DBG_END";
        if (code.startsWith("OS_")) {
            if (entry.owner() == TeamToken.CAMP_A) return "DBG_OUTER_CAMP_A";
            if (entry.owner() == TeamToken.CAMP_B) return "DBG_OUTER_CAMP_B";
            return "DBG_OUTER_NEUTRAL";
        }
        if (code.startsWith("IS_")) {
            if (entry.owner() == TeamToken.CAMP_A) return "DBG_INNER_CAMP_A";
            if (entry.owner() == TeamToken.CAMP_B) return "DBG_INNER_CAMP_B";
            return "DBG_INNER_NEUTRAL";
        }
        return "DBG_NEUTRAL";
    }
}
