/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;
import org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout;
import org.mods.gd656killicon.client.gui.theme.BattlefieldPovOverlay;
import org.mods.gd656killicon.client.gui.theme.BattlefieldTextRenderer;
import static org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.*;

public final class CaptureDiamondRenderer {
    private static final float LABEL_SCALE = 0.72f;
    private static final float LABEL_X_OFFSET = 0.3f;
    private static final float LABEL_Y_OFFSET = -2.8f;
    private static final int LABEL_PACKED_LIGHT = 0xF000F0;

    public static float hudRadius(float radius){return Math.max(2.7f,radius);}

    private CaptureDiamondRenderer() {
    }

    public static void drawCaptureDiamond(Matrix4f matrix, float radius, float progress, float alpha, String ownerToken, String drivingToken) {
        float iconAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
        RenderSystem.disableCull();
        float[] baseColor = CaptureDiamondRenderer.resolveBaseColor(progress, ownerToken);
        float baseBlend = 0.45f;
        baseColor[0] = Mth.lerp(baseBlend, baseColor[0], 0.22f);
        baseColor[1] = Mth.lerp(baseBlend, baseColor[1], 0.22f);
        baseColor[2] = Mth.lerp(baseBlend, baseColor[2], 0.22f);
        CaptureDiamondRenderer.drawDiamond(matrix, radius, baseColor[0], baseColor[1], baseColor[2], iconAlpha);
        float sectorProgress = Mth.clamp(Math.abs(progress - 50.0f) / 50.0f, 0.0f, 1.0f);
        String sectorToken = CaptureDiamondRenderer.resolveSectorPerspectiveToken(progress, ownerToken, drivingToken);
        if (sectorProgress > 0.0f && "ALLY".equalsIgnoreCase(sectorToken)) {
            CaptureDiamondRenderer.drawDiamondSector(matrix, radius, sectorProgress, true, 0.14f, 0.52f, 1.0f, Math.min(1.0f, iconAlpha * 1.25f));
        } else if (sectorProgress > 0.0f && "ENEMY".equalsIgnoreCase(sectorToken)) {
            CaptureDiamondRenderer.drawDiamondSector(matrix, radius, sectorProgress, false, 1.0f, 0.18f, 0.18f, Math.min(1.0f, iconAlpha * 1.25f));
        }
        CaptureDiamondRenderer.drawDiamondOutline(matrix, radius, 1.0f, 1.0f, 1.0f, iconAlpha);
        CaptureDiamondRenderer.drawDiamondOutline(matrix, radius + 0.35f, 1.0f, 1.0f, 1.0f, iconAlpha);
        CaptureDiamondRenderer.drawDiamondOutline(matrix, Math.max(1.0f, radius - 0.35f), 1.0f, 1.0f, 1.0f, iconAlpha);
        RenderSystem.enableCull();
    }

    public static void drawSpawnDiamond(Matrix4f matrix, float radius, float alpha, String ownerToken) {
        float iconAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
        RenderSystem.disableCull();
        float[] baseColor = CaptureDiamondRenderer.resolveSpawnBaseColor(ownerToken);
        CaptureDiamondRenderer.drawDiamond(matrix, radius, baseColor[0], baseColor[1], baseColor[2], iconAlpha);
        CaptureDiamondRenderer.drawDiamondOutline(matrix, radius, 1.0f, 1.0f, 1.0f, iconAlpha);
        CaptureDiamondRenderer.drawDiamondOutline(matrix, radius + 0.35f, 1.0f, 1.0f, 1.0f, iconAlpha);
        CaptureDiamondRenderer.drawDiamondOutline(matrix, Math.max(1.0f, radius - 0.35f), 1.0f, 1.0f, 1.0f, iconAlpha);
        RenderSystem.enableCull();
    }

    public static void drawCaptureIconInHud(GuiGraphics gui, Font font, String text, boolean spawnPoint, float centerX, float centerY, float radius, float progress, float alpha, String ownerToken, String drivingToken, float labelScale, float labelXOffset, boolean selected, boolean hovered) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        CaptureDiamondRenderer.drawCaptureIconInHud(gui, font, text, spawnPoint, centerX, centerY, radius, progress, alpha, ownerToken, drivingToken, labelScale, labelXOffset, selected, hovered, bufferSource);
        bufferSource.endBatch();
    }

    public static void drawCaptureIconInHud(GuiGraphics gui, Font font, String text, boolean spawnPoint, float centerX, float centerY, float radius, float progress, float alpha, String ownerToken, String drivingToken, float labelScale, float labelXOffset, boolean selected, boolean hovered, MultiBufferSource.BufferSource bufferSource) {
        var theme=BattlefieldUiTheme.current();
        boolean friendly=ownerToken.startsWith("ALLY")||ownerToken.equals("DBG_CAMP_A_SPAWN");
        int rgb=friendly?BattlefieldPovLayout.ally(theme):ownerToken.equals("ENEMY")?BattlefieldPovLayout.enemy(theme):0xffd9dedb;
        int color=((int)(Mth.clamp(alpha,0,1)*255)<<24)|(rgb&0xffffff);
        gui.pose().pushPose();gui.pose().translate(centerX,centerY,0);float factor=hudRadius(radius)/18f;gui.pose().scale(factor,factor,1);
        var canvas=new BattlefieldPovOverlay.GameCanvas(gui,theme);
        BattlefieldPovLayout.marker(canvas,theme,0,0,18,text,color,friendly,spawnPoint?-1:Math.abs(progress-50)/50,org.mods.gd656conquest.client.room.ClientDeathFlowState.shouldRenderDeployBoundaries());
        if(selected||hovered){int highlight=((int)(Mth.clamp(alpha,0,1)*255)<<24)|(BattlefieldPovLayout.ink(theme)&0xffffff);canvas.line(-24,-18,-24,-24,1.5f,highlight);canvas.line(-24,-24,-18,-24,1.5f,highlight);canvas.line(24,18,24,24,1.5f,highlight);canvas.line(24,24,18,24,1.5f,highlight);}
        gui.pose().popPose();
    }

    public static void drawVehicleIconInHud(GuiGraphics gui, Font font, String text, float centerX, float centerY, float radius, float alpha, int fillRgb, int outlineRgb, int glyphRgb, float labelScale, boolean selected, boolean hovered) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        CaptureDiamondRenderer.drawVehicleIconInHud(gui, font, text, centerX, centerY, radius, alpha, fillRgb, outlineRgb, glyphRgb, labelScale, selected, hovered, bufferSource);
        bufferSource.endBatch();
    }

    public static void drawVehicleIconInHud(GuiGraphics gui, Font font, String text, float centerX, float centerY, float radius, float alpha, int fillRgb, int outlineRgb, int glyphRgb, float labelScale, boolean selected, boolean hovered, MultiBufferSource.BufferSource bufferSource) {
        gui.pose().pushPose();
        gui.pose().translate(centerX, centerY, 0.0f);
        Matrix4f matrix = gui.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float[] fill = CaptureDiamondRenderer.rgb(fillRgb);
        float[] outline = CaptureDiamondRenderer.rgb(outlineRgb);
        CaptureDiamondRenderer.drawDiamond(matrix, radius, fill[0], fill[1], fill[2], Mth.clamp(alpha, 0.0f, 1.0f));
        CaptureDiamondRenderer.drawDiamondOutline(matrix, radius, outline[0], outline[1], outline[2], alpha);
        CaptureDiamondRenderer.drawDiamondOutline(matrix, radius + 0.35f, outline[0], outline[1], outline[2], alpha);
        CaptureDiamondRenderer.drawDiamondOutline(matrix, Math.max(1.0f, radius - 0.35f), outline[0], outline[1], outline[2], alpha);
        if (selected) {
            CaptureDiamondRenderer.drawDiamondOutline(matrix, radius + 1.0f, 1.0f, 0.9f, 0.2f, alpha);
            CaptureDiamondRenderer.drawDiamondOutline(matrix, radius + 1.45f, 1.0f, 0.9f, 0.2f, alpha);
        } else if (hovered) {
            CaptureDiamondRenderer.drawDiamondOutline(matrix, radius + 1.0f, 1.0f, 1.0f, 1.0f, alpha);
            CaptureDiamondRenderer.drawDiamondOutline(matrix, radius + 1.45f, 1.0f, 1.0f, 1.0f, alpha);
        }
        CaptureDiamondRenderer.drawIconGlyphInHud(gui, font, text, alpha, labelScale, glyphRgb, bufferSource);
        RenderSystem.enableDepthTest();
        gui.pose().popPose();
    }

    public static void drawDiamondOutline(Matrix4f matrix, float radius, float red, float green, float blue, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(1.0f);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0.0f, radius, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, radius, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, -radius, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, -radius, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, radius, 0.0f).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    public static void drawIconLabelInWorld(Font font, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, String text, float alpha) {
        float textAlpha = alpha <= 0.01f ? 0.0f : Math.max(0.11f, alpha);
        int alphaByte = (int)(Mth.clamp(textAlpha, 0.0f, 1.0f) * 255.0f) << 24;
        int color = alphaByte | 0xFFFFFF;
        float textWidth = font.width(BattlefieldUiTheme.styled(text));
        poseStack.pushPose();
        poseStack.scale(0.72f, 0.72f, 1.0f);
        Matrix4f textMatrix = poseStack.last().pose();
        font.drawInBatch(BattlefieldUiTheme.styled(text).getVisualOrderText(), (-textWidth * 0.5f + 0.3f) / 0.72f, -3.8888886f, color, true, textMatrix, (MultiBufferSource)bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        poseStack.popPose();
    }

    public static void drawIconLabelInHud(GuiGraphics gui, Font font, String text, int centerX, int centerY, float alpha) {
        CaptureDiamondRenderer.drawIconLabelInHud(gui, font, text, centerX, centerY, alpha, 0.72f);
    }

    public static void drawIconLabelInHud(GuiGraphics gui, Font font, String text, int centerX, int centerY, float alpha, float labelScale) {
        CaptureDiamondRenderer.drawIconLabelInHud(gui, font, text, centerX, centerY, alpha, labelScale, 0.3f);
    }

    public static void drawIconLabelInHud(GuiGraphics gui, Font font, String text, float centerX, float centerY, float alpha, float labelScale) {
        CaptureDiamondRenderer.drawIconLabelInHud(gui, font, text, centerX, centerY, alpha, labelScale, 0.3f);
    }

    public static void drawIconLabelInHud(GuiGraphics gui, Font font, String text, float centerX, float centerY, float alpha, float labelScale, float labelXOffset) {
        int color=((int)(Mth.clamp(alpha,0,1)*255)<<24)|0xffffff;
        BattlefieldTextRenderer.centered(gui,BattlefieldUiTheme.current(),Face.TEXT,9*Math.max(.1f,labelScale),text,centerX,centerY,color);
    }

    public static void drawCenteredHudText(GuiGraphics gui, Font font, String text, float centerX, float topY, int rgb, float alpha, boolean shadow) {
        CaptureDiamondRenderer.drawCenteredHudText(gui, font, text, centerX, topY, rgb, alpha, shadow, 1.0f);
    }

    public static void drawCenteredHudText(GuiGraphics gui, Font font, String text, float centerX, float topY, int rgb, float alpha, boolean shadow, float scale) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        CaptureDiamondRenderer.drawCenteredHudText(gui, font, text, centerX, topY, rgb, alpha, shadow, scale, bufferSource);
        bufferSource.endBatch();
    }

    public static void drawCenteredHudText(GuiGraphics gui, Font font, String text, float centerX, float topY, int rgb, float alpha, boolean shadow, float scale, MultiBufferSource.BufferSource bufferSource) {
        int color=((int)(Mth.clamp(alpha,0,1)*255)<<24)|(rgb&0xffffff);
        float size=Math.max(.1f,scale)*9f;
        if(shadow){float pixel=(float)gui.guiWidth()/Minecraft.getInstance().getWindow().getWidth();
            BattlefieldTextRenderer.draw(gui,BattlefieldUiTheme.current(),Face.TEXT,size,text,centerX,topY+pixel,((int)(Mth.clamp(alpha,0,1)*190)<<24)|0x061219,Align.CENTER);}
        BattlefieldTextRenderer.draw(gui,BattlefieldUiTheme.current(),Face.TEXT,size,text,centerX,topY,color,Align.CENTER);
    }

    public static void drawScreenCrosshair(GuiGraphics gui, float centerX, float centerY, int screenWidth, int screenHeight, float horizontalGap, float verticalGap, float horizontalFeather, float verticalFeather, float thickness, int rgb, float alpha) {
        int color=((int)(Mth.clamp(alpha,0,1)*255)<<24)|(rgb&0xffffff);
        var canvas=new BattlefieldPovOverlay.GameCanvas(gui,BattlefieldUiTheme.current());
        float gap=5,length=7;
        canvas.line(centerX-gap-length,centerY,centerX-gap,centerY,0.7f,color);
        canvas.line(centerX+gap,centerY,centerX+gap+length,centerY,0.7f,color);
        canvas.line(centerX,centerY-gap-length,centerX,centerY-gap,0.7f,color);
        canvas.line(centerX,centerY+gap,centerX,centerY+gap+length,0.7f,color);
    }

    private static void drawIconGlyphInHud(GuiGraphics gui, Font font, String text, float alpha, float labelScale, MultiBufferSource.BufferSource bufferSource) {
        CaptureDiamondRenderer.drawIconGlyphInHud(gui, font, text, alpha, labelScale, 0xFFFFFF, bufferSource);
    }

    private static void drawIconGlyphInHud(GuiGraphics gui, Font font, String text, float alpha, float labelScale, int rgb, MultiBufferSource.BufferSource bufferSource) {
        float textAlpha = alpha <= 0.01f ? 0.0f : Math.max(0.11f, alpha);
        int alphaByte = (int)(Mth.clamp(textAlpha, 0.0f, 1.0f) * 255.0f) << 24;
        int color = alphaByte | rgb & 0xFFFFFF;
        float usingScale = Math.max(0.1f, labelScale) * 9f / 16f;
        gui.pose().pushPose();
        gui.pose().scale(usingScale, usingScale, 1.0f);
        Matrix4f textMatrix = gui.pose().last().pose();
        float drawX = (float)(-font.width(BattlefieldUiTheme.styled(text))) * 0.5f;
        float drawY = -8.0f;
        font.drawInBatch(BattlefieldUiTheme.styled(text).getVisualOrderText(), drawX, drawY, color, true, textMatrix, (MultiBufferSource)bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        gui.pose().popPose();
    }

    private static void addHorizontalCrosshairArm(BufferBuilder buffer, Matrix4f matrix, float startX, float endX, float top, float bottom, boolean extendsRight, float feather, int rgb, float alpha) {
        if (endX <= startX || alpha <= 0.0f) {
            return;
        }
        float clampedFeather = Math.max(0.0f, feather);
        if (clampedFeather <= 0.001f) {
            CaptureDiamondRenderer.addSolidQuad(buffer, matrix, startX, top, endX, bottom, rgb, alpha);
            return;
        }
        if (extendsRight) {
            float fadeEnd = Math.min(endX, startX + clampedFeather);
            CaptureDiamondRenderer.addHorizontalGradientQuad(buffer, matrix, startX, top, fadeEnd, bottom, rgb, 0.0f, alpha);
            if (endX > fadeEnd) {
                CaptureDiamondRenderer.addSolidQuad(buffer, matrix, fadeEnd, top, endX, bottom, rgb, alpha);
            }
            return;
        }
        float fadeStart = Math.max(startX, endX - clampedFeather);
        if (fadeStart > startX) {
            CaptureDiamondRenderer.addSolidQuad(buffer, matrix, startX, top, fadeStart, bottom, rgb, alpha);
        }
        CaptureDiamondRenderer.addHorizontalGradientQuad(buffer, matrix, fadeStart, top, endX, bottom, rgb, alpha, 0.0f);
    }

    private static void addVerticalCrosshairArm(BufferBuilder buffer, Matrix4f matrix, float startY, float endY, float left, float right, boolean extendsDown, float feather, int rgb, float alpha) {
        if (endY <= startY || alpha <= 0.0f) {
            return;
        }
        float clampedFeather = Math.max(0.0f, feather);
        if (clampedFeather <= 0.001f) {
            CaptureDiamondRenderer.addSolidQuad(buffer, matrix, left, startY, right, endY, rgb, alpha);
            return;
        }
        if (extendsDown) {
            float fadeEnd = Math.min(endY, startY + clampedFeather);
            CaptureDiamondRenderer.addVerticalGradientQuad(buffer, matrix, left, startY, right, fadeEnd, rgb, 0.0f, alpha);
            if (endY > fadeEnd) {
                CaptureDiamondRenderer.addSolidQuad(buffer, matrix, left, fadeEnd, right, endY, rgb, alpha);
            }
            return;
        }
        float fadeStart = Math.max(startY, endY - clampedFeather);
        if (fadeStart > startY) {
            CaptureDiamondRenderer.addSolidQuad(buffer, matrix, left, startY, right, fadeStart, rgb, alpha);
        }
        CaptureDiamondRenderer.addVerticalGradientQuad(buffer, matrix, left, fadeStart, right, endY, rgb, alpha, 0.0f);
    }

    private static void addSolidQuad(BufferBuilder buffer, Matrix4f matrix, float left, float top, float right, float bottom, int rgb, float alpha) {
        CaptureDiamondRenderer.addGradientQuad(buffer, matrix, left, top, right, bottom, rgb, alpha, alpha, alpha, alpha);
    }

    private static void addHorizontalGradientQuad(BufferBuilder buffer, Matrix4f matrix, float left, float top, float right, float bottom, int rgb, float leftAlpha, float rightAlpha) {
        CaptureDiamondRenderer.addGradientQuad(buffer, matrix, left, top, right, bottom, rgb, leftAlpha, rightAlpha, rightAlpha, leftAlpha);
    }

    private static void addVerticalGradientQuad(BufferBuilder buffer, Matrix4f matrix, float left, float top, float right, float bottom, int rgb, float topAlpha, float bottomAlpha) {
        CaptureDiamondRenderer.addGradientQuad(buffer, matrix, left, top, right, bottom, rgb, topAlpha, topAlpha, bottomAlpha, bottomAlpha);
    }

    private static void addGradientQuad(BufferBuilder buffer, Matrix4f matrix, float left, float top, float right, float bottom, int rgb, float topLeftAlpha, float topRightAlpha, float bottomRightAlpha, float bottomLeftAlpha) {
        if (right <= left || bottom <= top) {
            return;
        }
        float red = (float)(rgb >>> 16 & 0xFF) / 255.0f;
        float green = (float)(rgb >>> 8 & 0xFF) / 255.0f;
        float blue = (float)(rgb & 0xFF) / 255.0f;
        buffer.vertex(matrix, left, bottom, 0.0f).color(red, green, blue, Mth.clamp(bottomLeftAlpha, 0.0f, 1.0f)).endVertex();
        buffer.vertex(matrix, right, bottom, 0.0f).color(red, green, blue, Mth.clamp(bottomRightAlpha, 0.0f, 1.0f)).endVertex();
        buffer.vertex(matrix, right, top, 0.0f).color(red, green, blue, Mth.clamp(topRightAlpha, 0.0f, 1.0f)).endVertex();
        buffer.vertex(matrix, left, top, 0.0f).color(red, green, blue, Mth.clamp(topLeftAlpha, 0.0f, 1.0f)).endVertex();
    }

    private static float[] rgb(int rgb) {
        return new float[]{(float)(rgb >>> 16 & 0xFF) / 255.0f, (float)(rgb >>> 8 & 0xFF) / 255.0f, (float)(rgb & 0xFF) / 255.0f};
    }

    private static float[] resolveBaseColor(float progress, String ownerToken) {
        float intensity = Mth.clamp(Math.abs(progress - 50.0f) / 50.0f, 0.0f, 1.0f);
        if ("ALLY".equalsIgnoreCase(ownerToken)) {
            return new float[]{Mth.lerp(intensity, 0.45f, 0.2f), Mth.lerp(intensity, 0.45f, 0.33f), Mth.lerp(intensity, 0.45f, 0.7f)};
        }
        if ("ENEMY".equalsIgnoreCase(ownerToken)) {
            return new float[]{Mth.lerp(intensity, 0.45f, 0.72f), Mth.lerp(intensity, 0.45f, 0.18f), Mth.lerp(intensity, 0.45f, 0.18f)};
        }
        return new float[]{0.45f, 0.45f, 0.45f};
    }

    private static float[] resolveSpawnBaseColor(String ownerToken) {
        if ("DBG_CAMP_A_SPAWN".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.12f, 0.24f, 0.58f};
        }
        if ("DBG_CAMP_B_SPAWN".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.58f, 0.14f, 0.14f};
        }
        if ("DBG_QUEUE".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.72f, 0.74f, 0.78f};
        }
        if ("DBG_DEPLOY".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.2f, 0.62f, 0.32f};
        }
        if ("DBG_END".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.8f, 0.68f, 0.16f};
        }
        if ("DBG_OUTER_CAMP_A".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.56f, 0.7f, 0.96f};
        }
        if ("DBG_OUTER_CAMP_B".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.95f, 0.56f, 0.56f};
        }
        if ("DBG_INNER_CAMP_A".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.43f, 0.6f, 0.96f};
        }
        if ("DBG_INNER_CAMP_B".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.96f, 0.48f, 0.48f};
        }
        if ("DBG_INNER_NEUTRAL".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.7f, 0.73f, 0.78f};
        }
        if ("DBG_OUTER_NEUTRAL".equalsIgnoreCase(ownerToken) || "DBG_NEUTRAL".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.78f, 0.8f, 0.84f};
        }
        if ("ALLY".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.2f, 0.33f, 0.7f};
        }
        if ("ALLY_STAGE_PROXY".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.2f, 0.33f, 0.7f};
        }
        if ("ENEMY".equalsIgnoreCase(ownerToken)) {
            return new float[]{0.72f, 0.18f, 0.18f};
        }
        return new float[]{0.45f, 0.45f, 0.45f};
    }

    private static String resolveSectorPerspectiveToken(float progress, String ownerToken, String drivingToken) {
        if ("ALLY".equalsIgnoreCase(ownerToken) || "ENEMY".equalsIgnoreCase(ownerToken)) {
            return ownerToken;
        }
        if (progress < 50.0f) {
            return "ALLY";
        }
        if (progress > 50.0f) {
            return "ENEMY";
        }
        return "NEUTRAL";
    }

    private static void drawDiamond(Matrix4f matrix, float radius, float red, float green, float blue, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0.0f, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, radius, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, radius, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, radius, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, -radius, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, -radius, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, -radius, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, -radius, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, 0.0f, radius, 0.0f).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawDiamondSector(Matrix4f matrix, float radius, float progress, boolean clockwise, float red, float green, float blue, float alpha) {
        if (progress <= 0.0f) {
            return;
        }
        List<float[]> perimeter = CaptureDiamondRenderer.buildSectorPerimeter(radius, progress, clockwise);
        if (perimeter.size() < 2) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < perimeter.size() - 1; ++i) {
            float[] p0 = perimeter.get(i);
            float[] p1 = perimeter.get(i + 1);
            buffer.vertex(matrix, 0.0f, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
            buffer.vertex(matrix, p0[0], p0[1], 0.0f).color(red, green, blue, alpha).endVertex();
            buffer.vertex(matrix, p1[0], p1[1], 0.0f).color(red, green, blue, alpha).endVertex();
            buffer.vertex(matrix, 0.0f, 0.0f, 0.0f).color(red, green, blue, alpha).endVertex();
            buffer.vertex(matrix, p1[0], p1[1], 0.0f).color(red, green, blue, alpha).endVertex();
            buffer.vertex(matrix, p0[0], p0[1], 0.0f).color(red, green, blue, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static List<float[]> buildSectorPerimeter(float radius, float progress, boolean clockwise) {
        double len = (double)Mth.clamp(progress, 0.0f, 1.0f) * 4.0;
        ArrayList<float[]> points = new ArrayList<float[]>();
        points.add(CaptureDiamondRenderer.flipSectorY(new float[]{0.0f, radius}));
        int fullSegments = (int)Math.floor(len);
        for (int segment = 1; segment <= fullSegments; ++segment) {
            points.add(CaptureDiamondRenderer.flipSectorY(CaptureDiamondRenderer.pointOnDiamondPerimeter(radius, segment, clockwise)));
        }
        double remain = len - (double)fullSegments;
        if (remain > 1.0E-6 && fullSegments < 4) {
            points.add(CaptureDiamondRenderer.flipSectorY(CaptureDiamondRenderer.pointOnDiamondPerimeter(radius, (double)fullSegments + remain, clockwise)));
        }
        if (len >= 3.999) {
            points.add(CaptureDiamondRenderer.flipSectorY(new float[]{0.0f, radius}));
        }
        return points;
    }

    private static float[] flipSectorY(float[] point) {
        return new float[]{point[0], -point[1]};
    }

    private static float[] pointOnDiamondPerimeter(float radius, double s, boolean clockwise) {
        double clamped = Mth.clamp(s, 0.0, 4.0);
        if (clockwise) {
            if (clamped <= 1.0) {
                return new float[]{(float)((double)radius * clamped), (float)((double)radius * (1.0 - clamped))};
            }
            if (clamped <= 2.0) {
                double t = clamped - 1.0;
                return new float[]{(float)((double)radius * (1.0 - t)), (float)((double)(-radius) * t)};
            }
            if (clamped <= 3.0) {
                double t = clamped - 2.0;
                return new float[]{(float)((double)(-radius) * t), (float)((double)(-radius) * (1.0 - t))};
            }
            double t = clamped - 3.0;
            return new float[]{(float)((double)(-radius) * (1.0 - t)), (float)((double)radius * t)};
        }
        if (clamped <= 1.0) {
            return new float[]{(float)((double)(-radius) * clamped), (float)((double)radius * (1.0 - clamped))};
        }
        if (clamped <= 2.0) {
            double t = clamped - 1.0;
            return new float[]{(float)((double)(-radius) * (1.0 - t)), (float)((double)(-radius) * t)};
        }
        if (clamped <= 3.0) {
            double t = clamped - 2.0;
            return new float[]{(float)((double)radius * t), (float)((double)(-radius) * (1.0 - t))};
        }
        double t = clamped - 3.0;
        return new float[]{(float)((double)radius * (1.0 - t)), (float)((double)radius * t)};
    }
}

