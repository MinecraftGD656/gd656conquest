package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult;
import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator;
import org.mods.gd656conquest.server.room.config.RoomValidationMessageFormatter;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

import java.nio.file.Path;
import java.util.Objects;

public final class RoomAdminLifecycleService {
    private final MinecraftServer server;
    private final RoomCoreRuntimeManager manager;
    private final ConquestDataManager dataManager;

    public RoomAdminLifecycleService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.manager = RoomCoreRuntimeManager.of(server);
        this.dataManager = ConquestDataManager.of(server);
    }

    public OperationResult startRoom(String roomId, long gameTick) {
        RoomDefinition room = repairTransientLifecycleIfNeeded(requireRoom(roomId), gameTick);
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            return OperationResult.failure("房间当前正在对局流程中，无法执行 startRoom: " + room.roomId());
        }
        if (lifecycleState == RoomLifecycleState.SAVING || lifecycleState == RoomLifecycleState.CLEANING) {
            return OperationResult.failure("房间当前正在保存或清理中，无法执行 startRoom: " + room.roomId());
        }
        if (lifecycleState == RoomLifecycleState.MATCHING) {
            return OperationResult.success("房间当前已经处于开放状态: " + room.roomId());
        }

        int evacuated = 0;
        if (lifecycleState == RoomLifecycleState.MAINTENANCE) {
            evacuated = manager.evacuateRoom(room.roomId(), gameTick);
            room = reloadRoom(room.roomId());
        }

        RoomConfigValidationResult validation = RoomConfigValidator.validate(room);
        if (!validation.isComplete()) {
            persistLifecycle(room, RoomLifecycleState.DISABLED, gameTick);
            String message = RoomValidationMessageFormatter.buildMissingConfigMessage(
                "房间配置不完整，startRoom 失败，房间已设为禁用",
                validation
            );
            return OperationResult.failure(appendEvacuated(message, evacuated));
        }

        persistLifecycle(room, RoomLifecycleState.IDLE, gameTick);
        return OperationResult.success(appendEvacuated("已开启房间: " + room.roomId(), evacuated));
    }

    public OperationResult maintenanceRoom(String roomId, long gameTick) {
        RoomDefinition room = repairTransientLifecycleIfNeeded(requireRoom(roomId), gameTick);
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            return OperationResult.failure("仅可在非运行中状态执行 maintenanceRoom: " + room.roomId());
        }
        if (lifecycleState == RoomLifecycleState.SAVING || lifecycleState == RoomLifecycleState.CLEANING) {
            return OperationResult.failure("房间当前正在保存或清理中，无法执行 maintenanceRoom: " + room.roomId());
        }
        if (lifecycleState == RoomLifecycleState.MAINTENANCE) {
            return OperationResult.success("房间已处于维护状态: " + room.roomId());
        }
        int evacuated = manager.evacuateRoom(room.roomId(), gameTick);
        persistLifecycle(reloadRoom(room.roomId()), RoomLifecycleState.MAINTENANCE, gameTick);
        return OperationResult.success(appendEvacuated("已将房间切换为维护状态: " + room.roomId(), evacuated));
    }

    public OperationResult cleanRoom(String roomId, long gameTick) {
        return cleanRoom(roomId, gameTick, RoomLifecycleState.IDLE);
    }

    public OperationResult cleanRoom(String roomId, long gameTick, RoomLifecycleState reopenState) {
        RoomDefinition room = repairTransientLifecycleIfNeeded(requireRoom(roomId), gameTick);
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.SAVING || lifecycleState == RoomLifecycleState.CLEANING) {
            return OperationResult.failure("房间当前正在保存或清理中，无法执行 cleanRoom: " + room.roomId());
        }
        try {
            RoomResetRecoveryService.of(server).markRoomForRecovery(room.roomId());
            persistLifecycle(room, RoomLifecycleState.CLEANING, gameTick);
            int evacuated = manager.evacuateRoom(room.roomId(), gameTick);
            RoomDefinition current = reloadRoom(room.roomId());
            dataManager.roomTemplates().resetRoomDimensionFromTemplate(current);
            OperationResult result = reopenAfterReset(
                current.roomId(),
                gameTick,
                evacuated,
                "已完成 cleanRoom",
                normalizeReopenLifecycle(reopenState)
            );
            if (result.success()) {
                RoomResetRecoveryService.of(server).clearRoomRecovery(room.roomId());
            }
            return result;
        } catch (Exception exception) {
            forceMaintenance(room.roomId(), gameTick);
            return OperationResult.failure("cleanRoom 失败，房间已转入维护状态: " + detailOf(exception));
        }
    }

    public OperationResult saveRoom(String roomId, long gameTick) {
        RoomDefinition room = repairTransientLifecycleIfNeeded(requireRoom(roomId), gameTick);
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            return OperationResult.failure("saveRoom 禁止在运行中或结算中执行: " + room.roomId());
        }
        if (lifecycleState == RoomLifecycleState.SAVING || lifecycleState == RoomLifecycleState.CLEANING) {
            return OperationResult.failure("房间当前正在保存或清理中，无法执行 saveRoom: " + room.roomId());
        }
        try {
            RoomResetRecoveryService.of(server).markRoomForRecovery(room.roomId());
            persistLifecycle(room, RoomLifecycleState.SAVING, gameTick);
            int evacuated = manager.evacuateRoom(room.roomId(), gameTick);
            RoomDefinition current = reloadRoom(room.roomId());
            Path templatePath = dataManager.roomTemplates().saveCurrentDimensionAsTemplate(current);
            persistLifecycle(reloadRoom(room.roomId()), RoomLifecycleState.CLEANING, gameTick);
            OperationResult reopened = reopenAfterReset(
                current.roomId(),
                gameTick,
                evacuated,
                "已保存维度模板到 " + templatePath.getFileName(),
                lifecycleState
            );
            if (reopened.success()) {
                RoomResetRecoveryService.of(server).clearRoomRecovery(room.roomId());
                return OperationResult.success(reopened.message());
            }
            return OperationResult.failure(reopened.message());
        } catch (Exception exception) {
            forceMaintenance(room.roomId(), gameTick);
            return OperationResult.failure("saveRoom 失败，房间已转入维护状态: " + detailOf(exception));
        }
    }

    public OperationResult recoverInterruptedRoom(String roomId, long gameTick) {
        RoomDefinition room = requireRoom(roomId);
        try {
            RoomResetRecoveryService.of(server).markRoomForRecovery(room.roomId());
            persistLifecycle(room, RoomLifecycleState.CLEANING, gameTick);
            int evacuated = manager.evacuateRoom(room.roomId(), gameTick);
            RoomDefinition current = reloadRoom(room.roomId());
            dataManager.roomTemplates().resetRoomDimensionFromTemplate(current);
            OperationResult result = reopenAfterReset(current.roomId(), gameTick, evacuated, "已恢复停服中断的房间关闭", RoomLifecycleState.IDLE);
            if (result.success()) {
                RoomResetRecoveryService.of(server).clearRoomRecovery(room.roomId());
            }
            return result;
        } catch (Exception exception) {
            forceMaintenance(room.roomId(), gameTick);
            return OperationResult.failure("恢复房间关闭失败，房间已转入维护状态: " + detailOf(exception));
        }
    }

    private OperationResult reopenAfterReset(String roomId, long gameTick, int evacuated, String prefix, RoomLifecycleState reopenState) {
        RoomDefinition refreshed = reloadRoom(roomId);
        RoomConfigValidationResult validation = RoomConfigValidator.validate(refreshed);
        if (!validation.isComplete()) {
            persistLifecycle(refreshed, RoomLifecycleState.DISABLED, gameTick);
            String message = RoomValidationMessageFormatter.buildMissingConfigMessage(
                prefix + "，但房间配置不完整，已设为禁用",
                validation
            );
            return OperationResult.failure(appendEvacuated(message, evacuated));
        }
        RoomLifecycleState targetState = normalizeReopenLifecycle(reopenState);
        persistLifecycle(refreshed, targetState, gameTick);
        if (targetState == RoomLifecycleState.DISABLED) {
            return OperationResult.success(appendEvacuated(prefix + "，房间已恢复为禁用状态: " + roomId, evacuated));
        }
        if (targetState == RoomLifecycleState.MAINTENANCE) {
            return OperationResult.success(appendEvacuated(prefix + "，房间已恢复为维护状态: " + roomId, evacuated));
        }
        return OperationResult.success(appendEvacuated(prefix + "，房间已重新开启: " + roomId, evacuated));
    }

    private RoomDefinition persistLifecycle(RoomDefinition room, RoomLifecycleState targetState, long gameTick) {
        RoomDefinition persisted = dataManager.roomDefinitions().saveDefinition(room.withLifecycleState(targetState));
        manager.coreService().hotUpdateRoomDefinition(persisted, gameTick);
        return manager.coreService().updateLifecycleState(persisted.roomId(), persisted.lifecycleState());
    }

    private RoomDefinition repairTransientLifecycleIfNeeded(RoomDefinition room, long gameTick) {
        if (room == null) {
            return null;
        }
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState != RoomLifecycleState.SAVING && lifecycleState != RoomLifecycleState.CLEANING) {
            return room;
        }
        OperationResult repaired = recoverInterruptedRoom(room.roomId(), gameTick);
        if (!repaired.success()) {
            return requireRoom(room.roomId());
        }
        return requireRoom(room.roomId());
    }

    private void forceMaintenance(String roomId, long gameTick) {
        try {
            persistLifecycle(reloadRoom(roomId), RoomLifecycleState.MAINTENANCE, gameTick);
        } catch (Exception ignored) {
        }
    }

    private RoomLifecycleState normalizeReopenLifecycle(RoomLifecycleState reopenState) {
        if (reopenState == RoomLifecycleState.DISABLED || reopenState == RoomLifecycleState.MAINTENANCE || reopenState == RoomLifecycleState.IDLE) {
            return reopenState;
        }
        return RoomLifecycleState.IDLE;
    }

    private RoomDefinition requireRoom(String roomId) {
        return manager.findRoom(roomId).orElseGet(() -> dataManager.roomDefinitions().readDefinition(roomId));
    }

    private RoomDefinition reloadRoom(String roomId) {
        return dataManager.roomDefinitions().readDefinition(roomId);
    }

    private String appendEvacuated(String message, int evacuated) {
        if (evacuated <= 0) {
            return message;
        }
        return message + " | 已清理玩家: " + evacuated;
    }

    private String detailOf(Exception exception) {
        if (exception == null) {
            return "未知错误";
        }
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return detail;
    }

    public record OperationResult(boolean success, String message) {
        public static OperationResult success(String message) {
            return new OperationResult(true, message);
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
    }
}
