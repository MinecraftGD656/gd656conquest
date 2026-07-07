package org.mods.gd656conquest.client.marker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import net.minecraft.resources.ResourceLocation;

public final class TacticalMarkerRenderer {
    private static final ResourceLocation TEX_ASSAULT_TEAM = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_assault_team.png");
    private static final ResourceLocation TEX_ASSAULT_SQUAD = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_assault_squad.png");
    private static final ResourceLocation TEX_ENGINEER_TEAM = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_engineer_team.png");
    private static final ResourceLocation TEX_ENGINEER_SQUAD = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_engineer_squad.png");
    private static final ResourceLocation TEX_RECON_TEAM = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_recon_team.png");
    private static final ResourceLocation TEX_RECON_SQUAD = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_recon_squad.png");
    private static final ResourceLocation TEX_SUPPORT_TEAM = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_support_team.png");
    private static final ResourceLocation TEX_SUPPORT_SQUAD = ResourceLocation.parse("gd656conquest:textures/gui/marker_class_support_squad.png");
    private static final ResourceLocation TEX_AMMO_BOX = ResourceLocation.parse("gd656conquest:textures/gui/marker_ammo_box.png");
    private static final ResourceLocation TEX_MEDICAL_BOX = ResourceLocation.parse("gd656conquest:textures/gui/marker_med_box.png");

    private static final float MAX_MARKER_VISUAL_OPACITY = 1.0F;
    private static final float TRIANGLE_BASE_Y = 4F;
    private static final float TRIANGLE_HALF_WIDTH = 3F;
    private static final float TRIANGLE_HEIGHT = 5.5F;
    private static final float DIAMOND_HALF_DIAGONAL = 3.8F;
    private static final float ALLY_DOT_RADIUS = 2.55F;
    private static final float DOWNED_RADIUS = 4.15F;
    private static final float ALLY_CYAN_RED = 0.16F;
    private static final float ALLY_CYAN_GREEN = 0.88F;
    private static final float ALLY_CYAN_BLUE = 1.00F;
    private static final float SQUAD_GREEN_RED = 0.38F;
    private static final float SQUAD_GREEN_GREEN = 1.00F;
    private static final float SQUAD_GREEN_BLUE = 0.58F;
    private static final float CLASS_MARKER_HALF_SIZE = 5.0F;
    private static final float AMMO_BOX_MARKER_HALF_SIZE = 7.0F;

    private TacticalMarkerRenderer() {
    }

    public static void drawMarker(
        GuiGraphics gui,
        float centerX,
        float centerY,
        float alpha,
        float shapeOpacity,
        float pulse,
        boolean hovered,
        MarkerVisualType visualType,
        float entryScale,
        float helpPulseProgress,
        String targetClass,
        boolean isAmmoBoxEmpty
    ) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha)) * MAX_MARKER_VISUAL_OPACITY;
        float clampedShapeOpacity = Math.max(0.0F, Math.min(1.0F, shapeOpacity));
        if (clampedAlpha <= 0.01F && clampedShapeOpacity <= 0.01F) {
            return;
        }
        float scale = Math.max(0.01F, entryScale) * 0.82F;
        MarkerVisual visual = MarkerVisual.resolve(visualType == null ? MarkerVisualType.NORMAL : visualType);
        float red = visual.red();
        float green = visual.green();
        float blue = visual.blue();
        float finalAlpha = clampedShapeOpacity * visual.opacityMultiplier();
        if (visual.downed() && helpPulseProgress >= 0.0F && helpPulseProgress < 1.0F) {
            float emphasis = 1.0F - helpPulseProgress;
            finalAlpha = Math.min(1.0F, finalAlpha + emphasis * 0.22F);
        }
        gui.pose().pushPose();
        gui.pose().translate(centerX, centerY, 0.0F);
        Matrix4f matrix = gui.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (finalAlpha > 0.01F) {
            drawShape(gui, matrix, visual, scale, red, green, blue, finalAlpha, targetClass, isAmmoBoxEmpty);
            if (visual.hasCenterCross()) {
                drawCenterCross(matrix, visual.crossRadius() * scale, 0.72F + (hovered ? 0.08F : 0.0F), 1.0F, 1.0F, 1.0F, Math.min(1.0F, finalAlpha * 0.97F));
            }
        }
        if (visual.downed() && helpPulseProgress >= 0.0F && helpPulseProgress < 1.0F) {
            float pulseFade = 1.0F - helpPulseProgress;
            float pulseScale = 1.0F + helpPulseProgress * 2.55F;
            float pulseAlpha = pulseFade * Math.max(0.40F, visual.opacityMultiplier()) * clampedAlpha;
            drawShape(gui, matrix, visual, scale * pulseScale, red, green, blue, pulseAlpha, targetClass, isAmmoBoxEmpty);
            if (visual.hasCenterCross()) {
                drawCenterCross(matrix, visual.crossRadius() * scale * pulseScale, 0.84F, 1.0F, 1.0F, 1.0F, Math.min(1.0F, pulseAlpha));
            }
            float outerPulseScale = 1.28F + helpPulseProgress * 3.15F;
            float outerPulseAlpha = pulseFade * pulseFade * Math.max(0.20F, visual.opacityMultiplier() * 0.52F) * clampedAlpha;
            drawShape(gui, matrix, visual, scale * outerPulseScale, red, green, blue, outerPulseAlpha, targetClass, isAmmoBoxEmpty);
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        gui.pose().popPose();
    }

    private static void drawShape(GuiGraphics gui, Matrix4f matrix, MarkerVisual visual, float scale, float red, float green, float blue, float alpha, String targetClass, boolean isAmmoBoxEmpty) {
        if (visual.shape() == MarkerShape.AMMO_BOX) {
            float r = 1.0f, g = 1.0f, b = 1.0f;
            if (isAmmoBoxEmpty) {
                long time = System.currentTimeMillis();
                float flash = (float) (Math.sin(time / 150.0) * 0.5 + 0.5); // 0.0 to 1.0 smooth
                g = flash;
                b = flash;
            }
            drawTextureMarker(gui, matrix, TEX_AMMO_BOX, scale, r, g, b, alpha, 256.0f);
            return;
        }
        if (visual.shape() == MarkerShape.MEDICAL_BOX) {
            drawTextureMarker(gui, matrix, TEX_MEDICAL_BOX, scale, 1.0f, 1.0f, 1.0f, alpha, 256.0f);
            return;
        }
        
        if ((visual.shape() == MarkerShape.CIRCLE) && targetClass != null && !targetClass.isBlank()) {
            ResourceLocation tex = resolveClassTexture(targetClass, visual.isSquad());
            if (tex != null) {
                drawTextureMarker(gui, matrix, tex, scale, 1.0f, 1.0f, 1.0f, alpha, 16.0f);
                return;
            }
        }
        
        switch (visual.shape()) {
            case TRIANGLE -> {
                float triangleCenterY = TRIANGLE_BASE_Y * scale;
                drawInvertedTriangle(matrix, 0.0F, triangleCenterY, TRIANGLE_HALF_WIDTH * scale, TRIANGLE_HEIGHT * scale, red, green, blue, alpha);
            }
            case CIRCLE -> drawFilledCircle(matrix, visual.radius() * scale, red, green, blue, alpha, 28);
            case DIAMOND -> drawDiamond(matrix, 0.0F, 0.0F, DIAMOND_HALF_DIAGONAL * scale, red, green, blue, alpha);
        }
    }

    private static ResourceLocation resolveClassTexture(String targetClass, boolean isSquad) {
        String lowerClass = targetClass.toLowerCase(java.util.Locale.ROOT);
        return switch (lowerClass) {
            case "assault" -> isSquad ? TEX_ASSAULT_SQUAD : TEX_ASSAULT_TEAM;
            case "engineer" -> isSquad ? TEX_ENGINEER_SQUAD : TEX_ENGINEER_TEAM;
            case "recon" -> isSquad ? TEX_RECON_SQUAD : TEX_RECON_TEAM;
            case "support" -> isSquad ? TEX_SUPPORT_SQUAD : TEX_SUPPORT_TEAM;
            default -> null;
        };
    }

    private static void drawTextureMarker(GuiGraphics gui, Matrix4f matrix, ResourceLocation texture, float scale, float r, float g, float b, float alpha, float textureSize) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        
        float halfSize = (textureSize == 256.0f ? AMMO_BOX_MARKER_HALF_SIZE : CLASS_MARKER_HALF_SIZE) * scale;
        
        buffer.vertex(matrix, -halfSize, -halfSize, 0.0F).uv(0.0F, 0.0F).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, -halfSize, halfSize, 0.0F).uv(0.0F, 1.0F).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, halfSize, halfSize, 0.0F).uv(1.0F, 1.0F).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, halfSize, -halfSize, 0.0F).uv(1.0F, 0.0F).color(r, g, b, alpha).endVertex();
        
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawCenterCross(Matrix4f matrix, float radius, float lineWidth, float red, float green, float blue, float alpha) {
        float halfThickness = Math.max(0.35F, lineWidth * 0.5F);
        drawQuad(matrix, -radius, -halfThickness, radius, halfThickness, red, green, blue, alpha);
        drawQuad(matrix, -halfThickness, -radius, halfThickness, radius, red, green, blue, alpha);
    }

    private static void drawFilledCircle(Matrix4f matrix, float radius, float red, float green, float blue, float alpha, int segments) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).color(red, green, blue, alpha).endVertex();
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * (double) i / (double) Math.max(segments, 3);
            float x = (float) (Math.cos(angle) * radius);
            float y = (float) (Math.sin(angle) * radius);
            buffer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawInvertedTriangle(
        Matrix4f matrix,
        float centerX,
        float centerY,
        float halfWidth,
        float height,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, centerX - halfWidth, centerY - height * 0.5F, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, centerX + halfWidth, centerY - height * 0.5F, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, centerX, centerY + height * 0.5F, 0.0F).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawDiamond(
        Matrix4f matrix,
        float centerX,
        float centerY,
        float halfDiagonal,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, centerX, centerY - halfDiagonal, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, centerX + halfDiagonal, centerY, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, centerX, centerY + halfDiagonal, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, centerX, centerY - halfDiagonal, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, centerX, centerY + halfDiagonal, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, centerX - halfDiagonal, centerY, 0.0F).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawQuad(
        Matrix4f matrix,
        float minX,
        float minY,
        float maxX,
        float maxY,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, minX, minY, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, maxX, minY, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, maxX, maxY, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, minX, minY, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, maxX, maxY, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, minX, maxY, 0.0F).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    public enum MarkerVisualType {
        NORMAL,
        ENEMY_GROUND,
        ENEMY_TRACKED,
        AUTO_ENEMY,
        ALLY_PERSISTENT,
        SQUAD_PERSISTENT,
        DOWNED_ALLY,
        DOWNED_SQUAD,
        AMMO_BOX,
        MEDICAL_BOX
    }

    private enum MarkerShape {
        DIAMOND,
        TRIANGLE,
        CIRCLE,
        AMMO_BOX,
        MEDICAL_BOX
    }

    private record MarkerVisual(
        MarkerShape shape,
        float red,
        float green,
        float blue,
        float opacityMultiplier,
        float radius,
        boolean hasCenterCross,
        float crossRadius,
        boolean downed,
        boolean isSquad
    ) {
        private static MarkerVisual resolve(MarkerVisualType type) {
            return switch (type) {
                case ENEMY_GROUND -> new MarkerVisual(MarkerShape.DIAMOND, 0.84F, 0.20F, 0.20F, 0.90F, DIAMOND_HALF_DIAGONAL, false, 0.0F, false, false);
                case ENEMY_TRACKED -> new MarkerVisual(MarkerShape.TRIANGLE, 0.84F, 0.20F, 0.20F, 0.90F, TRIANGLE_HALF_WIDTH, false, 0.0F, false, false);
                case AUTO_ENEMY -> new MarkerVisual(MarkerShape.TRIANGLE, 0.84F, 0.20F, 0.20F, 0.60F, TRIANGLE_HALF_WIDTH, false, 0.0F, false, false);
                case ALLY_PERSISTENT -> new MarkerVisual(MarkerShape.CIRCLE, ALLY_CYAN_RED, ALLY_CYAN_GREEN, ALLY_CYAN_BLUE, 0.50F, ALLY_DOT_RADIUS, false, 0.0F, false, false);
                case SQUAD_PERSISTENT -> new MarkerVisual(MarkerShape.CIRCLE, SQUAD_GREEN_RED, SQUAD_GREEN_GREEN, SQUAD_GREEN_BLUE, 0.50F, ALLY_DOT_RADIUS, false, 0.0F, false, true);
                case DOWNED_ALLY -> new MarkerVisual(MarkerShape.CIRCLE, ALLY_CYAN_RED, ALLY_CYAN_GREEN, ALLY_CYAN_BLUE, 0.85F, DOWNED_RADIUS, true, 1.95F, true, false);
                case DOWNED_SQUAD -> new MarkerVisual(MarkerShape.CIRCLE, SQUAD_GREEN_RED, SQUAD_GREEN_GREEN, SQUAD_GREEN_BLUE, 0.85F, DOWNED_RADIUS, true, 1.95F, true, true);
                case AMMO_BOX -> new MarkerVisual(MarkerShape.AMMO_BOX, 1.0F, 1.0F, 1.0F, 0.85F, ALLY_DOT_RADIUS, false, 0.0F, false, false);
                case MEDICAL_BOX -> new MarkerVisual(MarkerShape.MEDICAL_BOX, 1.0F, 1.0F, 1.0F, 0.85F, ALLY_DOT_RADIUS, false, 0.0F, false, false);
                case NORMAL -> new MarkerVisual(MarkerShape.DIAMOND, 0.95F, 0.82F, 0.20F, 0.90F, DIAMOND_HALF_DIAGONAL, false, 0.0F, false, false);
            };
        }
    }
}
