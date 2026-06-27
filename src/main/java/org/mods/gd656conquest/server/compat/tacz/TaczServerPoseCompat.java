package org.mods.gd656conquest.server.compat.tacz;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class TaczServerPoseCompat {
    private static final String TACZ_MOD_ID = "tacz";
    private static boolean reflectionResolved;
    private static boolean available;
    private static Method fromLivingEntityMethod;
    private static Method crawlMethod;

    private TaczServerPoseCompat() {
    }

    public static void clearCrawlState(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded(TACZ_MOD_ID)) {
            return;
        }
        if (!resolveReflection()) {
            return;
        }
        try {
            Object operator = fromLivingEntityMethod.invoke(null, player);
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
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> operatorClass = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            fromLivingEntityMethod = operatorClass.getDeclaredMethod("fromLivingEntity", livingEntityClass);
            crawlMethod = operatorClass.getDeclaredMethod("crawl", boolean.class);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }
}
