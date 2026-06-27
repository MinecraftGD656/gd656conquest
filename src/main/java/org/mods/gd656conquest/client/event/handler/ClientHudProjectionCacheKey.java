package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class ClientHudProjectionCacheKey {
    private static final double POSITION_STEP = 0.02D;
    private static final double ROTATION_STEP = 0.05D;
    private static final double FOV_STEP = 0.05D;

    private ClientHudProjectionCacheKey() {
    }

    static long capture(Minecraft minecraft, Matrix4f viewProjectionMatrix) {
        if (minecraft == null || minecraft.gameRenderer == null || minecraft.options == null) {
            return Long.MIN_VALUE;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return Long.MIN_VALUE;
        }
        Vec3 position = camera.getPosition();
        if (position == null) {
            return Long.MIN_VALUE;
        }
        long hash = 1469598103934665603L;
        hash = mix(hash, quantize(position.x, POSITION_STEP));
        hash = mix(hash, quantize(position.y, POSITION_STEP));
        hash = mix(hash, quantize(position.z, POSITION_STEP));
        hash = mix(hash, quantize(camera.getXRot(), ROTATION_STEP));
        hash = mix(hash, quantize(camera.getYRot(), ROTATION_STEP));
        hash = mix(hash, quantize(resolveCurrentFov(minecraft), FOV_STEP));
        hash = mix(hash, quantize(resolveProjectionComponent(viewProjectionMatrix, Matrix4f::m00), 1.0E-4D));
        hash = mix(hash, quantize(resolveProjectionComponent(viewProjectionMatrix, Matrix4f::m11), 1.0E-4D));
        hash = mix(hash, quantize(resolveProjectionComponent(viewProjectionMatrix, Matrix4f::m22), 1.0E-4D));
        hash = mix(hash, quantize(resolveProjectionComponent(viewProjectionMatrix, Matrix4f::m23), 1.0E-4D));
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 1099511628211L;
    }

    private static long quantize(double value, double step) {
        return Math.round(value / step);
    }

    private static double resolveCurrentFov(Minecraft minecraft) {
        Object value = minecraft.options.fov().get();
        return value instanceof Number number ? number.doubleValue() : 70.0D;
    }

    private static double resolveProjectionComponent(Matrix4f matrix, MatrixComponentResolver resolver) {
        if (matrix == null || resolver == null) {
            return 0.0D;
        }
        return resolver.resolve(matrix);
    }

    @FunctionalInterface
    private interface MatrixComponentResolver {
        float resolve(Matrix4f matrix);
    }
}
