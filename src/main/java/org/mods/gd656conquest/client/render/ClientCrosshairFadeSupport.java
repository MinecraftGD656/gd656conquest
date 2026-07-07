package org.mods.gd656conquest.client.render;

import net.minecraft.util.Mth;

public final class ClientCrosshairFadeSupport {
    private ClientCrosshairFadeSupport() {
    }

    public static float resolveWeightedAlpha(
        int width,
        int height,
        double screenX,
        double screenY,
        float nearAlpha,
        float farAlpha,
        float radiusPixels,
        float curve
    ) {
        float crosshairX = width * 0.5F;
        float crosshairY = height * 0.5F;
        float distance = Mth.sqrt((float) (Math.pow(screenX - crosshairX, 2) + Math.pow(screenY - crosshairY, 2)));
        float normalizedDistance = Mth.clamp(distance / radiusPixels, 0.0F, 1.0F);
        float proximity = 1.0F - normalizedDistance;
        float factor = (float) Math.pow(proximity, curve);
        return Mth.lerp(factor, farAlpha, nearAlpha);
    }
}
