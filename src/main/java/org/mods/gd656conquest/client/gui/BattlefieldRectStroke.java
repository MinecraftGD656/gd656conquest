package org.mods.gd656conquest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Screen-pixel rectangle strokes shared by the 656 menus.
 *
 * <p>The legacy 1px frames were integer GUI pixels drawn through {@code GuiGraphics.fill},
 * so every boundary was rounded on its own. Inside a scaled pose (embedded weapon editor,
 * HUD module zoom, lobby embedding) a hairline is thinner than one GUI pixel and the four
 * roundings collapse unevenly: one or two sides end up with a zero-width quad and vanish.
 *
 * <p>This helper keeps the geometry in floats and expresses the thickness in screen pixels,
 * so a frame is exactly one physical pixel wide at every GUI scale and zoom, instead of one
 * GUI pixel (which is 2-3 physical pixels at the usual scales). Thickness is clamped to one
 * screen pixel on the low end: an opaque quad thinner than a pixel is sampled by its centre
 * and would flicker away again, so a smaller request fades the colour instead of shrinking
 * the geometry further.
 */
public final class BattlefieldRectStroke {
    /** One physical pixel: the thin frame used by every 656 panel, button and slot. */
    public static final float HAIRLINE_SCREEN_PX = 1f;
    public static final int EDGE_TOP = 1;
    public static final int EDGE_BOTTOM = 2;
    public static final int EDGE_LEFT = 4;
    public static final int EDGE_RIGHT = 8;
    public static final int EDGE_ALL = EDGE_TOP | EDGE_BOTTOM | EDGE_LEFT | EDGE_RIGHT;
    /** Below one physical pixel the colour is faded so the request stays visible but lighter. */
    private static final float MIN_SOLID_SCREEN_PX = 1f;
    private static final float MIN_ALPHA = 0.35f;
    private BattlefieldRectStroke() {}

    /** One physical pixel frame, drawn inside {@code (x,y,w,h)} so neighbours cannot clip it. */
    public static void hairline(GuiGraphics g, float x, float y, float w, float h, int color) {
        stroke(g, x, y, w, h, color, HAIRLINE_SCREEN_PX);
    }

    /** Frame with an explicit thickness in physical pixels; never thinner than one pixel. */
    public static void stroke(GuiGraphics g, float x, float y, float w, float h, int color, float screenPx) {
        stroke(g, x, y, w, h, color, screenPx, EDGE_ALL);
    }

    /** Frame with a subset of the four edges, e.g. {@code EDGE_ALL & ~EDGE_TOP}. */
    public static void stroke(GuiGraphics g, float x, float y, float w, float h, int color, float screenPx, int edges) {
        stroke(g, x, y, w, h, color, screenPx, edges, 0f);
    }

    /** Same as above with an explicit vertex depth, for canvases that layer their widgets. */
    public static void stroke(GuiGraphics g, float x, float y, float w, float h, int color, float screenPx, int edges, float z) {
        if (g == null || w <= 0f || h <= 0f || (color >>> 24) == 0) {
            return;
        }
        Matrix4f matrix = g.pose().last().pose();
        float guiScale = Math.max(0.5f, (float)Minecraft.getInstance().getWindow().getGuiScale());
        // One physical pixel expressed in the current GUI/pose space.
        float pxX = 1f / (guiScale * Math.max(1.0E-4f, axisLength(matrix, true)));
        float pxY = 1f / (guiScale * Math.max(1.0E-4f, axisLength(matrix, false)));
        float requested = Math.max(0.05f, screenPx);
        float alpha = requested >= MIN_SOLID_SCREEN_PX
            ? 1f
            : Math.max(MIN_ALPHA, requested / MIN_SOLID_SCREEN_PX);
        float thickX = Math.min(Math.max(pxX, requested * pxX), w * 0.5f);
        float thickY = Math.min(Math.max(pxY, requested * pxY), h * 0.5f);
        int argb = fade(color, alpha);
        float left = x;
        float right = x + w;
        float top = y;
        float bottom = y + h;
        float[][] quads = {
            {left, top, right, top + thickY},
            {left, bottom - thickY, right, bottom},
            {left, top + thickY, left + thickX, bottom - thickY},
            {right - thickX, top + thickY, right, bottom - thickY}
        };
        int[] edgeBits = {EDGE_TOP, EDGE_BOTTOM, EDGE_LEFT, EDGE_RIGHT};
        float[][] selected = new float[quads.length][];
        int count = 0;
        for (int index = 0; index < quads.length; index++) {
            if ((edges & edgeBits[index]) != 0) {
                selected[count++] = quads[index];
            }
        }
        if (count == 0) {
            return;
        }
        draw(g, matrix, java.util.Arrays.copyOf(selected, count), argb, z);
    }

    private static float axisLength(Matrix4f matrix, boolean xAxis) {
        float a = xAxis ? matrix.m00() : matrix.m01();
        float b = xAxis ? matrix.m10() : matrix.m11();
        return (float)Math.sqrt(a * a + b * b);
    }

    private static int fade(int color, float alpha) {
        int source = (color >>> 24) & 0xFF;
        int scaled = Math.round(source * Math.min(1f, Math.max(0f, alpha)));
        return (scaled << 24) | (color & 0xFFFFFF);
    }

    /** Same state contract as {@code GuiGraphics.fill}, so callers can mix both freely. */
    private static void draw(GuiGraphics g, Matrix4f matrix, float[][] quads, int color, float z) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int alpha = (color >>> 24) & 0xFF;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] quad : quads) {
            if (quad[2] <= quad[0] || quad[3] <= quad[1]) {
                continue;
            }
            buffer.vertex(matrix, quad[0], quad[1], z).color(r, green, blue, alpha).endVertex();
            buffer.vertex(matrix, quad[0], quad[3], z).color(r, green, blue, alpha).endVertex();
            buffer.vertex(matrix, quad[2], quad[3], z).color(r, green, blue, alpha).endVertex();
            buffer.vertex(matrix, quad[2], quad[1], z).color(r, green, blue, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
