package org.mods.gd656conquest.client.event.handler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.render.ClientWorldHudProjectionSupport;
import org.mods.gd656conquest.client.room.ClientBattalionBriefingState;
import org.mods.gd656conquest.common.match.BattalionSequence;
import org.mods.gd656conquest.common.match.BattalionSequence.Phase;

/** GUI-projected polygon masks also support concave sector boundaries. */
public final class ClientBattalionBriefingRenderer {
    private static Matrix4f projection;
    private static final int ALLY = 0x4078FF;
    private static final int ENEMY = 0xFF3333;
    private ClientBattalionBriefingRenderer() {}

    public static void captureProjection(RenderLevelStageEvent event) {
        if (ClientBattalionBriefingState.showingBriefing())
            projection = ClientWorldHudProjectionSupport.captureViewProjectionMatrix(event);
        else projection = null;
    }

    public static void render(GuiGraphics gui, float partialTick) {
        var p = ClientBattalionBriefingState.snapshot();
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        double elapsed = ClientBattalionBriefingState.elapsed(partialTick);
        if (p.phase() == Phase.RESULT || p.phase() == Phase.TRAVEL_TO_BRIEFING) {
            boolean won = p.selfAttacker() == p.attackersWon();
            float alpha = BattalionSequence.resultAlpha(p.phase(), elapsed);
            String title = won ? "我们赢了" : "我们输了";
            String sub = p.attackersWon() ? (p.selfAttacker() ? "成功拿下全部区域" : "无法守住本区域")
                : p.selfAttacker() ? "无法拿下本区域" : "成功防守本区域";
            drawTrackedCentered(gui, mc.font, title, width * .5F, height * .18F,
                height * .070F / mc.font.lineHeight, 2.5F, alpha, width);
            drawTrackedCentered(gui, mc.font, sub, width * .5F, height * .268F,
                height * .034F / mc.font.lineHeight, 1.5F, BattalionSequence.resultSubtitleAlpha(p.phase(), elapsed), width);
        }
        if (p.phase() == Phase.NEXT_ASSAULT) {
            drawTrackedCentered(gui, mc.font, BattalionSequence.conclusionText(p.battalion(), p.totalBattalions(), p.selfAttacker(), p.attackersWon(), p.remainingDefenseLines()),
                width * .5F, height * .385F, height * .049F / mc.font.lineHeight, 2.5F,
                BattalionSequence.announcementAlpha(elapsed), width);
            return;
        }
        float boundaryFade = BattalionSequence.briefingBoundaryAlpha(p.phase(), elapsed);
        float maskFade = BattalionSequence.briefingMaskAlpha(p.phase(), elapsed);
        if (boundaryFade <= 0 || projection == null) return;
        for (var sector : p.sectors()) {
            if (!sector.visibleInBriefing()) continue;
            var polygon = sector.points().stream().map(world -> new Vec3(world.x() + .5, world.y(), world.z() + .5)).toList();
            List<Point> points = org.mods.gd656conquest.common.match.BriefingPolygonProjection.project(polygon, projection, width, height)
                .stream().map(point -> new Point(point.x(), point.y())).toList();
            if (points.size() < 3) continue;
            float blend = sector.attackerControl(p.phase(), p.capturedSectors(), elapsed);
            int from = p.selfAttacker() ? ENEMY : ALLY, to = p.selfAttacker() ? ALLY : ENEMY;
            int color = mix(from, to, blend);
            if (maskFade > 0) fillPolygon(gui, points, color | (Math.round(255 * .28F * maskFade) << 24), width, height);
            drawBorder(gui, points, color, boundaryFade);
        }
    }

    /** Size and placement follow the viewport, independently of the user's GUI scale. */
    private static void drawTrackedCentered(GuiGraphics gui, Font font, String text, float centerX, float y,
        float scale, float spacing, float alpha, int screenWidth) {
        int opacity = Math.round(255 * Mth.clamp(alpha, 0, 1));
        // Vanilla text rendering treats alpha values 0..3 as opaque.
        if (text.isEmpty() || opacity <= 3) return;
        List<String> glyphs = text.codePoints().mapToObj(Character::toString).toList();
        float trackedWidth = (glyphs.size() - 1) * spacing;
        for (String glyph : glyphs) trackedWidth += font.width(glyph);
        scale = Math.min(scale, screenWidth * .9F / Math.max(1, trackedWidth));
        gui.pose().pushPose();
        try {
            gui.pose().translate(centerX - trackedWidth * scale / 2, y, 0);
            gui.pose().scale(scale, scale, 1);
            for (String glyph : glyphs) {
                gui.drawString(font, glyph, 0, 0, (opacity << 24) | 0xF4F4F4, false);
                gui.pose().translate(font.width(glyph) + spacing, 0, 0);
            }
        } finally { gui.pose().popPose(); }
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255));
        int blue = Math.round(Mth.lerp(t, a & 255, b & 255));
        return (r << 16) | (g << 8) | blue;
    }

    private static void fillPolygon(GuiGraphics gui, List<Point> points, int color, int width, int height) {
        if ((color >>> 24) == 0) return;
        double min = points.stream().mapToDouble(Point::y).min().orElse(0);
        double max = points.stream().mapToDouble(Point::y).max().orElse(0);
        for (int y = Math.max(0, (int) Math.floor(min)); y < Math.min(height, Math.ceil(max)); y++) {
            double sample = y + .5;
            List<Double> xs = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                Point a = points.get(i), b = points.get((i + 1) % points.size());
                if ((a.y <= sample && b.y > sample) || (b.y <= sample && a.y > sample))
                    xs.add(a.x + (sample - a.y) * (b.x - a.x) / (b.y - a.y));
            }
            xs.sort(Comparator.naturalOrder());
            for (int i = 0; i + 1 < xs.size(); i += 2) {
                int left = Math.max(0, (int) Math.floor(xs.get(i))), right = Math.min(width, (int) Math.ceil(xs.get(i + 1)));
                if (left < right) gui.fill(left, y, right, y + 1, color);
            }
        }
    }

    private static void drawBorder(GuiGraphics gui, List<Point> points, int color, float alpha) {
        if (alpha <= 0) return;
        gui.flush();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest(); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = gui.pose().last().pose();
            for (int i = 0; i < points.size(); i++) {
                Point a = points.get(i), b = points.get((i + 1) % points.size());
                double dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy);
                if (len < .001) continue;
                double nx = -dy / len, ny = dx / len;
                vertex(buffer, matrix, a.x + nx, a.y + ny, color, alpha);
                vertex(buffer, matrix, b.x + nx, b.y + ny, color, alpha);
                vertex(buffer, matrix, b.x - nx, b.y - ny, color, alpha);
                vertex(buffer, matrix, a.x - nx, a.y - ny, color, alpha);
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally { RenderSystem.enableDepthTest(); RenderSystem.enableCull(); RenderSystem.disableBlend(); }
    }

    private static void vertex(BufferBuilder b, Matrix4f matrix, double x, double y, int color, float alpha) {
        b.vertex(matrix, (float) x, (float) y, 0).color((color >> 16) & 255, (color >> 8) & 255, color & 255, Math.round(255 * alpha)).endVertex();
    }
    private record Point(double x, double y) {}
}
