package org.mods.gd656conquest.common.entity;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PlayerAnimatorDebugDimensionsState {
    private static final int MIN_DURATION_MS = 1_000;
    private static final Map<UUID, Long> ACTIVE_UNTIL_MS = new HashMap<>();
    private static final Set<UUID> APPLIED_PLAYERS = new HashSet<>();
    private static final String TACZ_HITBOX_HELPER = "com.tacz.guns.util.HitboxHelper";
    private static boolean taczReflectionResolved;
    private static Field taczPlayerPositionField;
    private static Field taczPlayerHitboxesField;
    private static Field taczPlayerVelocityField;

    private PlayerAnimatorDebugDimensionsState() {
    }

    public static void startPreview(UUID playerId, int durationMs) {
        if (playerId == null) {
            return;
        }
        ACTIVE_UNTIL_MS.put(playerId, System.currentTimeMillis() + Math.max(MIN_DURATION_MS, durationMs));
    }

    public static void stopPreview(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ACTIVE_UNTIL_MS.remove(playerId);
    }

    public static void startPreview(Player player, int durationMs) {
        if (player == null) {
            return;
        }
        startPreview(player.getUUID(), durationMs);
        clearTaczHitboxCache(player);
        player.setForcedPose(Pose.SWIMMING);
        APPLIED_PLAYERS.add(player.getUUID());
        player.refreshDimensions();
    }

    public static void stopPreview(Player player) {
        if (player == null) {
            return;
        }
        clearPlayer(player);
    }

    public static void tickPlayer(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();
        boolean shouldApply = isPreviewActive(playerId, now);
        boolean applied = APPLIED_PLAYERS.contains(playerId);
        if (shouldApply) {
            player.setForcedPose(Pose.SWIMMING);
        } else if (applied) {
            player.setForcedPose(null);
            if (!player.isSwimming() && !player.isFallFlying()) {
                player.setPose(Pose.STANDING);
            }
        }
        if (shouldApply != applied) {
            if (shouldApply) {
                clearTaczHitboxCache(player);
                APPLIED_PLAYERS.add(playerId);
            } else {
                APPLIED_PLAYERS.remove(playerId);
                clearTaczHitboxCache(player);
            }
            player.refreshDimensions();
        }
        clearExpiredState(now);
    }

    public static void handleEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isPreviewActive(player.getUUID(), System.currentTimeMillis())) {
            return;
        }
        event.setNewSize(player.getDimensions(Pose.SWIMMING));
    }

    public static void clearPlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        ACTIVE_UNTIL_MS.remove(playerId);
        if (APPLIED_PLAYERS.remove(playerId)) {
            player.setForcedPose(null);
            if (!player.isSwimming() && !player.isFallFlying()) {
                player.setPose(Pose.STANDING);
            }
            clearTaczHitboxCache(player);
            player.refreshDimensions();
        }
    }

    public static void resetAll() {
        ACTIVE_UNTIL_MS.clear();
        APPLIED_PLAYERS.clear();
    }

    public static boolean isPreviewActive(UUID playerId, long now) {
        return playerId != null && ACTIVE_UNTIL_MS.getOrDefault(playerId, 0L) > now;
    }

    private static void clearExpiredState(long now) {
        ACTIVE_UNTIL_MS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    @SuppressWarnings("unchecked")
    private static void clearTaczHitboxCache(Player player) {
        if (player == null || !resolveTaczFields()) {
            return;
        }
        removeFromWeakMap(taczPlayerPositionField, player);
        removeFromWeakMap(taczPlayerHitboxesField, player);
        removeFromWeakMap(taczPlayerVelocityField, player);
    }

    @SuppressWarnings("unchecked")
    private static void removeFromWeakMap(Field field, Player player) {
        if (field == null || player == null) {
            return;
        }
        try {
            Object value = field.get(null);
            if (value instanceof WeakHashMap<?, ?> rawMap) {
                ((WeakHashMap<Player, LinkedList<?>>) rawMap).remove(player);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static boolean resolveTaczFields() {
        if (taczReflectionResolved) {
            return taczPlayerPositionField != null && taczPlayerHitboxesField != null && taczPlayerVelocityField != null;
        }
        taczReflectionResolved = true;
        try {
            Class<?> helperClass = Class.forName(TACZ_HITBOX_HELPER);
            taczPlayerPositionField = helperClass.getDeclaredField("PLAYER_POSITION");
            taczPlayerHitboxesField = helperClass.getDeclaredField("PLAYER_HITBOXES");
            taczPlayerVelocityField = helperClass.getDeclaredField("PLAYER_VELOCITY");
            taczPlayerPositionField.setAccessible(true);
            taczPlayerHitboxesField.setAccessible(true);
            taczPlayerVelocityField.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
            taczPlayerPositionField = null;
            taczPlayerHitboxesField = null;
            taczPlayerVelocityField = null;
        }
        return taczPlayerPositionField != null && taczPlayerHitboxesField != null && taczPlayerVelocityField != null;
    }
}
