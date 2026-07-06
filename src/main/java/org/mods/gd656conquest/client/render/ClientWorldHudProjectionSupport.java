package org.mods.gd656conquest.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class ClientWorldHudProjectionSupport {
    private ClientWorldHudProjectionSupport() {
    }

    public static Matrix4f captureViewProjectionMatrix(RenderLevelStageEvent event) {
        if (event == null || event.getPoseStack() == null || event.getProjectionMatrix() == null || event.getCamera() == null) {
            return null;
        }
        Vec3 cameraPos = event.getCamera().getPosition();
        return new Matrix4f(event.getProjectionMatrix())
            .mul(event.getPoseStack().last().pose())
            .translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
    }

    public static ProjectedPoint projectToScreen(Minecraft minecraft, Matrix4f viewProjectionMatrix, Vec3 worldPos) {
        if (minecraft == null || viewProjectionMatrix == null || worldPos == null) {
            return null;
        }
        Vector4f clip = new Vector4f((float) worldPos.x, (float) worldPos.y, (float) worldPos.z, 1.0F);
        viewProjectionMatrix.transform(clip);
        if (clip.w() <= 1.0E-6F) {
            return null;
        }
        double ndcX = clip.x() / clip.w();
        double ndcY = clip.y() / clip.w();
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY)) {
            return null;
        }
        double screenX = minecraft.getWindow().getGuiScaledWidth() * 0.5D * (1.0D + ndcX);
        double screenY = minecraft.getWindow().getGuiScaledHeight() * 0.5D * (1.0D - ndcY);
        return new ProjectedPoint(screenX, screenY);
    }

    public static ProjectedHudPoint projectToHud(Minecraft minecraft, Matrix4f viewProjectionMatrix, Vec3 worldPos, double edgePadding) {
        if (minecraft == null || viewProjectionMatrix == null || worldPos == null) {
            return null;
        }
        Vector4f clip = new Vector4f((float) worldPos.x, (float) worldPos.y, (float) worldPos.z, 1.0F);
        viewProjectionMatrix.transform(clip);
        if (Math.abs(clip.w()) <= 1.0E-6F) {
            return null;
        }
        boolean inFront = clip.w() > 0.0F;
        double ndcX = clip.x() / clip.w();
        double ndcY = clip.y() / clip.w();
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY)) {
            return null;
        }
        if (!inFront) {
            ndcX = -ndcX;
            ndcY = -ndcY;
        }
        double width = minecraft.getWindow().getGuiScaledWidth();
        double height = minecraft.getWindow().getGuiScaledHeight();
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        double screenX = centerX * (1.0D + ndcX);
        double screenY = centerY * (1.0D - ndcY);
        boolean insideViewport = inFront
            && ndcX >= -1.0D && ndcX <= 1.0D
            && ndcY >= -1.0D && ndcY <= 1.0D;
        if (insideViewport) {
            return new ProjectedHudPoint(screenX, screenY, true, true);
        }
        double clampedPadding = Math.max(0.0D, edgePadding);
        double maxOffsetX = Math.max(1.0D, centerX - clampedPadding);
        double maxOffsetY = Math.max(1.0D, centerY - clampedPadding);
        double dx = screenX - centerX;
        double dy = screenY - centerY;
        if (Math.abs(dx) <= 1.0E-6D && Math.abs(dy) <= 1.0E-6D) {
            return new ProjectedHudPoint(centerX, centerY, inFront, false);
        }
        double scale = 1.0D / Math.max(Math.abs(dx) / maxOffsetX, Math.abs(dy) / maxOffsetY);
        double edgeX = Mth.clamp(centerX + dx * scale, clampedPadding, Math.max(clampedPadding, width - clampedPadding));
        double edgeY = Mth.clamp(centerY + dy * scale, clampedPadding, Math.max(clampedPadding, height - clampedPadding));
        return new ProjectedHudPoint(edgeX, edgeY, inFront, false);
    }

    public record ProjectedPoint(double screenX, double screenY) {
    }

    public record ProjectedHudPoint(double screenX, double screenY, boolean inFront, boolean insideViewport) {
    }
}
