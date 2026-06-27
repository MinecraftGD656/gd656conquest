package org.mods.gd656conquest.client.compat.tacz;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class TaczClientInputCompat {
    private static final String TACZ_MOD_ID = "tacz";
    private static boolean reflectionResolved;
    private static boolean available;
    private static Field shootKeyField;
    private static Field lastTimeShootSuccessField;
    private static Method shootControllerTickMethod;
    private static Method fromLocalPlayerMethod;
    private static Method chargeShootMethod;
    private static Method aimMethod;
    private static Method getDataHolderMethod;
    private static Method resetDataHolderMethod;
    private static Method crawlMethod;

    private TaczClientInputCompat() {
    }

    public static void clearTransientCombatState(Minecraft minecraft) {
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
        clearShootKeyState();
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
            Class<?> shootKeyClass = Class.forName("com.tacz.guns.client.input.ShootKey");
            shootKeyField = shootKeyClass.getDeclaredField("SHOOT_KEY");
            lastTimeShootSuccessField = shootKeyClass.getDeclaredField("lastTimeShootSuccess");
            shootControllerTickMethod = shootKeyClass.getDeclaredMethod("shootControllerTick", boolean.class);
            Class<?> operatorClass = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            fromLocalPlayerMethod = operatorClass.getDeclaredMethod("fromLocalPlayer", LocalPlayer.class);
            chargeShootMethod = operatorClass.getDeclaredMethod("chargeShoot", boolean.class);
            aimMethod = operatorClass.getDeclaredMethod("aim", boolean.class);
            getDataHolderMethod = operatorClass.getDeclaredMethod("getDataHolder");
            crawlMethod = operatorClass.getDeclaredMethod("crawl", boolean.class);
            Class<?> dataHolderClass = Class.forName("com.tacz.guns.client.gameplay.LocalPlayerDataHolder");
            resetDataHolderMethod = dataHolderClass.getDeclaredMethod("reset");
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    private static void clearShootKeyState() {
        try {
            KeyMapping shootKey = (KeyMapping) shootKeyField.get(null);
            if (shootKey != null) {
                shootKey.setDown(false);
                while (shootKey.consumeClick()) {
                }
            }
            if (lastTimeShootSuccessField != null) {
                lastTimeShootSuccessField.setBoolean(null, false);
            }
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
            Object dataHolder = getDataHolderMethod.invoke(operator);
            if (dataHolder != null) {
                resetDataHolderMethod.invoke(dataHolder);
            }
        } catch (Throwable ignored) {
        }
    }
}
