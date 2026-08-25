package org.mods.gd656conquest.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GD656Mods: 武器开镜(ADS)状态判定(完全客户端)。
 * 覆盖 TACZ(IClientPlayerGunOperator.isAim) 与 SuperbWarfare(ClientEventHandler.zoom)。
 * 用于开镜时隐藏地图内据点图标: 提供 0.1 秒渐入/渐隐的淡化因子。
 * 反射失败/模组未安装时恒判定为"未开镜", 不会出现图标不显示的问题。
 */
public final class ClientWeaponAimCompat {
    private static final float AIM_FADE_SECONDS = 0.1F;
    private static float aimFadeAlpha = 1.0F;

    private ClientWeaponAimCompat() {
    }

    /** 每帧在渲染前调用: 根据开镜状态向 0(开镜)/1(取消开镜) 以 0.1 秒渐变 */
    public static void tickFade(Minecraft minecraft, float deltaSeconds) {
        boolean aiming = isAnyWeaponAiming(minecraft);
        float target = aiming ? 0.0F : 1.0F;
        float step = Mth.clamp(deltaSeconds / AIM_FADE_SECONDS, 0.0F, 1.0F);
        aimFadeAlpha = Mth.lerp(step, aimFadeAlpha, target);
    }

    /** 当前图标淡化因子: 1=完全显示, 0=完全隐藏 */
    public static float fadeAlpha() {
        return aimFadeAlpha;
    }

    /** TACZ 或 SuperbWarfare 武器是否正在开镜 */
    public static boolean isAnyWeaponAiming(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        if (isTaczAiming(minecraft.player)) {
            return true;
        }
        return isSuperbWarfareAiming();
    }

    private static boolean isTaczAiming(LocalPlayer player) {
        try {
            Class<?> operatorClass = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            Method fromLocalPlayer = operatorClass.getMethod("fromLocalPlayer", LocalPlayer.class);
            Object operator = fromLocalPlayer.invoke(null, player);
            if (operator == null) {
                return false;
            }
            Method isAim = operatorClass.getMethod("isAim");
            return Boolean.TRUE.equals(isAim.invoke(operator));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSuperbWarfareAiming() {
        try {
            Class<?> handlerClass = Class.forName("com.atsuishio.superbwarfare.event.ClientEventHandler");
            Field zoomField = handlerClass.getField("zoom");
            return Boolean.TRUE.equals(zoomField.get(null));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
