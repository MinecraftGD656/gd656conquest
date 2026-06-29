package org.mods.gd656conquest.client.compat.tacz;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class TaczClientInputCompat {
    private static final String TACZ_MOD_ID = "tacz";
    private static boolean suppressAttackUntilRelease;
    private static boolean suppressUseUntilRelease;
    private static boolean reflectionResolved;
    private static boolean available;
    private static Field aimKeyField;
    private static Field shootKeyField;
    private static Field zoomKeyField;
    private static Field lastTimeShootSuccessField;
    private static Method shootControllerTickMethod;
    private static Method fromLocalPlayerMethod;
    private static Method chargeShootMethod;
    private static Method aimMethod;
    private static Method crawlMethod;

    private TaczClientInputCompat() {
    }

    public static void clearTransientCombatState(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        forceCombatStateReleased(minecraft);
    }

    public static void suppressCombatInputUntilRelease(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        suppressAttackUntilRelease = suppressAttackUntilRelease || isPhysicalInputPressed(minecraft, minecraft.options == null ? null : minecraft.options.keyAttack);
        suppressUseUntilRelease = suppressUseUntilRelease || isPhysicalInputPressed(minecraft, minecraft.options == null ? null : minecraft.options.keyUse);
        forceCombatStateReleased(minecraft);
    }

    public static void tickCombatInputSuppression(Minecraft minecraft) {
        if (minecraft == null || (!suppressAttackUntilRelease && !suppressUseUntilRelease)) {
            return;
        }
        boolean attackPressed = isPhysicalInputPressed(minecraft, minecraft.options == null ? null : minecraft.options.keyAttack);
        boolean usePressed = isPhysicalInputPressed(minecraft, minecraft.options == null ? null : minecraft.options.keyUse);
        if (suppressAttackUntilRelease && !attackPressed) {
            suppressAttackUntilRelease = false;
        }
        if (suppressUseUntilRelease && !usePressed) {
            suppressUseUntilRelease = false;
        }
        if (!suppressAttackUntilRelease && !suppressUseUntilRelease) {
            return;
        }
        forceCombatStateReleased(minecraft);
    }

    public static boolean shouldBlockCombatInputUntilRelease(Minecraft minecraft, boolean attackInput, boolean useInput) {
        if (minecraft == null) {
            return false;
        }
        if (attackInput && suppressAttackUntilRelease) {
            return true;
        }
        return useInput && suppressUseUntilRelease;
    }

    public static void resetSuppression() {
        suppressAttackUntilRelease = false;
        suppressUseUntilRelease = false;
    }

    private static void clearVanillaCombatState(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        releaseKeyMapping(minecraft.options.keyAttack);
        releaseKeyMapping(minecraft.options.keyUse);
        releaseKeyMapping(minecraft.options.keyDrop);
        releaseKeyMapping(minecraft.options.keySwapOffhand);
        if (minecraft.player != null) {
            minecraft.player.stopUsingItem();
        }
    }

    private static void forceCombatStateReleased(Minecraft minecraft) {
        clearVanillaCombatState(minecraft);
        if (!ModList.get().isLoaded(TACZ_MOD_ID)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!resolveReflection()) {
            return;
        }
        clearAimKeyState();
        clearShootKeyState();
        clearZoomKeyState();
        clearControllerShootState();
        resetLocalPlayerGunState(player);
    }

    public static void clearCrawlState(Minecraft minecraft) {
        if (minecraft == null || !ModList.get().isLoaded(TACZ_MOD_ID)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!resolveReflection()) {
            return;
        }
        try {
            Object operator = fromLocalPlayerMethod.invoke(null, player);
            if (operator != null) {
                crawlMethod.invoke(operator, false);
            }
        } catch (Throwable ignored) {
        }
        if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
        if (!player.isSwimming() && !player.isFallFlying()) {
            player.setPose(Pose.STANDING);
        }
        player.refreshDimensions();
    }

    private static boolean resolveReflection() {
        if (reflectionResolved) {
            return available;
        }
        reflectionResolved = true;
        try {
            Class<?> aimKeyClass = Class.forName("com.tacz.guns.client.input.AimKey");
            aimKeyField = aimKeyClass.getDeclaredField("AIM_KEY");
            Class<?> shootKeyClass = Class.forName("com.tacz.guns.client.input.ShootKey");
            shootKeyField = shootKeyClass.getDeclaredField("SHOOT_KEY");
            lastTimeShootSuccessField = shootKeyClass.getDeclaredField("lastTimeShootSuccess");
            shootControllerTickMethod = shootKeyClass.getDeclaredMethod("shootControllerTick", boolean.class);
            Class<?> zoomKeyClass = Class.forName("com.tacz.guns.client.input.ZoomKey");
            zoomKeyField = zoomKeyClass.getDeclaredField("ZOOM_KEY");
            Class<?> operatorClass = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            fromLocalPlayerMethod = operatorClass.getDeclaredMethod("fromLocalPlayer", LocalPlayer.class);
            chargeShootMethod = operatorClass.getDeclaredMethod("chargeShoot", boolean.class);
            aimMethod = operatorClass.getDeclaredMethod("aim", boolean.class);
            crawlMethod = operatorClass.getDeclaredMethod("crawl", boolean.class);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    private static void clearAimKeyState() {
        try {
            KeyMapping aimKey = (KeyMapping) aimKeyField.get(null);
            releaseKeyMapping(aimKey);
        } catch (Throwable ignored) {
        }
    }

    private static void clearShootKeyState() {
        try {
            KeyMapping shootKey = (KeyMapping) shootKeyField.get(null);
            releaseKeyMapping(shootKey);
            if (lastTimeShootSuccessField != null) {
                lastTimeShootSuccessField.setBoolean(null, false);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearZoomKeyState() {
        try {
            KeyMapping zoomKey = (KeyMapping) zoomKeyField.get(null);
            releaseKeyMapping(zoomKey);
        } catch (Throwable ignored) {
        }
    }

    private static void clearControllerShootState() {
        try {
            shootControllerTickMethod.invoke(null, false);
        } catch (Throwable ignored) {
        }
    }

    private static void resetLocalPlayerGunState(LocalPlayer player) {
        try {
            Object operator = fromLocalPlayerMethod.invoke(null, player);
            if (operator == null) {
                return;
            }
            if (aimMethod != null) {
                aimMethod.invoke(operator, false);
            }
            chargeShootMethod.invoke(operator, false);
        } catch (Throwable ignored) {
        }
    }

    private static void releaseKeyMapping(KeyMapping keyMapping) {
        if (keyMapping == null) {
            return;
        }
        keyMapping.setDown(false);
        while (keyMapping.consumeClick()) {
        }
    }

    private static boolean isPhysicalInputPressed(Minecraft minecraft, KeyMapping keyMapping) {
        if (minecraft == null || minecraft.getWindow() == null || keyMapping == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        InputConstants.Key key = keyMapping.getKey();
        if (key == null) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
    }
}
