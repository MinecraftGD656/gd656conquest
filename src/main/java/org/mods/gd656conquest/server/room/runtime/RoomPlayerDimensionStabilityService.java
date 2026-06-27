package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

public final class RoomPlayerDimensionStabilityService {
    private static final int ROOM_DIMENSION_VALIDATE_INTERVAL_TICKS = 20;

    private RoomPlayerDimensionStabilityService() {
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        reconcile(player);
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.tickCount % ROOM_DIMENSION_VALIDATE_INTERVAL_TICKS != 0) {
            return;
        }
        reconcile(player);
    }

    private static void reconcile(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (!requiresExpectedDimension(room)) {
            return;
        }
        RoomPlayerLaunchService playerLaunchService = new RoomPlayerLaunchService(player.server);
        boolean inExpectedLevel = playerLaunchService.findRoomLevel(room)
            .map(level -> player.serverLevel() == level)
            .orElse(false);
        if (inExpectedLevel) {
            return;
        }
        manager.sendPlayerToHub(player);
    }

    private static boolean requiresExpectedDimension(RoomDefinition room) {
        if (room == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = room.lifecycleState();
        return lifecycleState == RoomLifecycleState.MATCHING
            || lifecycleState == RoomLifecycleState.RUNNING
            || lifecycleState == RoomLifecycleState.ENDING
            || lifecycleState == RoomLifecycleState.MAINTENANCE;
    }
}
