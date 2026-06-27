package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

public final class RoomJoinAccessPolicy {
    private static final String ROOM_SERVICE_NOT_INITIALIZED = "房间服务尚未初始化";
    private static final String MAINTENANCE_ADMIN_ONLY = "加入失败，维护房间仅管理员可进入";

    private RoomJoinAccessPolicy() {
    }

    public static JoinCheckResult evaluate(ServerPlayer player, RoomDefinition room, RoomCoreRuntimeManager manager) {
        if (player == null || player.server == null) {
            return JoinCheckResult.denied("", false);
        }
        if (manager == null || !manager.isInitialized()) {
            return JoinCheckResult.denied(ROOM_SERVICE_NOT_INITIALIZED, false);
        }
        if (room == null) {
            return JoinCheckResult.denied("未找到房间", false);
        }
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.DISABLED
            || lifecycleState == RoomLifecycleState.CLEANING
            || lifecycleState == RoomLifecycleState.SAVING) {
            return JoinCheckResult.denied("加入失败，当前房间状态为: " + lifecycleState.name(), false);
        }
        boolean maintenance = lifecycleState == RoomLifecycleState.MAINTENANCE;
        if (maintenance && !player.hasPermissions(2)) {
            return JoinCheckResult.denied(MAINTENANCE_ADMIN_ONLY, true);
        }
        return JoinCheckResult.allowed(maintenance);
    }

    public record JoinCheckResult(boolean allowed, String deniedMessage, boolean maintenance) {
        private static JoinCheckResult allowed(boolean maintenance) {
            return new JoinCheckResult(true, "", maintenance);
        }

        private static JoinCheckResult denied(String deniedMessage, boolean maintenance) {
            return new JoinCheckResult(false, deniedMessage == null ? "" : deniedMessage, maintenance);
        }
    }
}
