package org.mods.gd656conquest.client.render;

import net.minecraft.client.Minecraft;
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

    public record ProjectedPoint(double screenX, double screenY) {
    }
}
