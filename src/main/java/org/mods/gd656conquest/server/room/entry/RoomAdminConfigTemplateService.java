package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigEntry;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigRepository;
import org.mods.gd656conquest.server.room.model.RoomCommonConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.room.runtime.RoomDimensionResetService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class RoomAdminConfigTemplateService {
    private static final List<RoomGameplayId> SUPPORTED_GAMEPLAY_IDS = List.of(
        RoomGameplayId.CONQUEST,
        RoomGameplayId.BREAKTHROUGH,
        RoomGameplayId.DEATHMATCH
    );

    private final MinecraftServer server;
    private final RoomCoreRuntimeManager manager;
    private final ConquestDataManager dataManager;
    private final RoomAdminLifecycleService lifecycleService;
    private final RoomDimensionResetService dimensionResetService;

    public RoomAdminConfigTemplateService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.manager = RoomCoreRuntimeManager.of(server);
        this.dataManager = ConquestDataManager.of(server);
        this.lifecycleService = new RoomAdminLifecycleService(server);
        this.dimensionResetService = new RoomDimensionResetService(server);
    }

    public RoomAdminLifecycleService.OperationResult copyRoomConfig(String templateRoomId, String targetRoomId, long gameTick) {
        String sourceId = normalizeRoomId(templateRoomId);
        String targetId = normalizeRoomId(targetRoomId);
        if (sourceId.isBlank() || targetId.isBlank()) {
            return RoomAdminLifecycleService.OperationResult.failure("模板房间或目标房间 ID 无效");
        }
        if (sourceId.equals(targetId)) {
            return RoomAdminLifecycleService.OperationResult.failure("模板房间和目标房间不能相同");
        }
        RoomDefinition template = findExistingRoom(sourceId);
        if (template == null) {
            return RoomAdminLifecycleService.OperationResult.failure("未找到模板房间: " + sourceId);
        }
        RoomDefinition target = findExistingRoom(targetId);
        if (target == null) {
            return RoomAdminLifecycleService.OperationResult.failure("未找到目标房间: " + targetId);
        }
        if (isTransientLifecycle(template.lifecycleState())) {
            return RoomAdminLifecycleService.OperationResult.failure("模板房间当前正在保存或清理中，无法复制: " + template.roomId());
        }
        if (isBlockedTargetLifecycle(target.lifecycleState())) {
            return RoomAdminLifecycleService.OperationResult.failure("目标房间当前状态不允许复制: " + target.roomId());
        }
        try {
            RoomDefinition copied = buildCopiedDefinition(template, target);
            RoomDefinition persisted = dataManager.roomDefinitions().saveDefinition(copied);
            manager.hotUpdateRoomDefinition(persisted, gameTick);
            copyVehicleConfigs(template.roomId(), target.roomId());
            RoomAdminLifecycleService.OperationResult cleanResult = lifecycleService.cleanRoom(target.roomId(), gameTick);
            if (!cleanResult.success()) {
                return RoomAdminLifecycleService.OperationResult.failure(
                    "房间配置已复制，但 cleanRoom 失败: " + cleanResult.message()
                );
            }
            return RoomAdminLifecycleService.OperationResult.success(
                "已复制房间配置: " + template.roomId() + " -> " + target.roomId() + " | " + cleanResult.message()
            );
        } catch (Exception exception) {
            return RoomAdminLifecycleService.OperationResult.failure("房间配置复制失败: " + detailOf(exception));
        }
    }

    public RoomAdminLifecycleService.OperationResult deleteRoom(String targetRoomId, long gameTick) {
        String roomId = normalizeRoomId(targetRoomId);
        if (roomId.isBlank()) {
            return RoomAdminLifecycleService.OperationResult.failure("目标房间 ID 无效");
        }
        RoomDefinition target = findExistingRoom(roomId);
        if (target == null) {
            return RoomAdminLifecycleService.OperationResult.failure("未找到目标房间: " + roomId);
        }
        if (isBlockedTargetLifecycle(target.lifecycleState())) {
            return RoomAdminLifecycleService.OperationResult.failure("目标房间当前状态不允许删除: " + target.roomId());
        }
        try {
            int evacuated = manager.coreService().evacuateRoom(roomId, gameTick);
            clearVehicleConfigs(roomId);
            clearDimension(target);
            RoomDefinition reset = dataManager.roomDefinitions().resetDefinition(roomId);
            manager.hotUpdateRoomDefinition(reset, gameTick);
            manager.coreService().updateLifecycleState(roomId, reset.lifecycleState());
            if (!sameDimension(target, reset)) {
                clearDimension(reset);
            }
            return RoomAdminLifecycleService.OperationResult.success(
                appendEvacuated("已将房间重置为默认干净状态: " + roomId, evacuated)
            );
        } catch (Exception exception) {
            try {
                manager.coreService().updateLifecycleState(roomId, RoomLifecycleState.MAINTENANCE);
            } catch (Exception ignored) {
            }
            return RoomAdminLifecycleService.OperationResult.failure(
                "房间删除失败，房间已转入维护状态: " + detailOf(exception)
            );
        }
    }

    private RoomDefinition findExistingRoom(String roomId) {
        return manager.findRoom(roomId)
            .or(() -> dataManager.roomDefinitions().findDefinition(roomId))
            .orElse(null);
    }

    private RoomDefinition buildCopiedDefinition(RoomDefinition template, RoomDefinition target) {
        RoomCommonConfig sourceCommon = template.commonConfig();
        RoomCommonConfig targetCommon = target.commonConfig();
        RoomCommonConfig copiedCommon = new RoomCommonConfig(
            sourceCommon.matchingConfig(),
            targetCommon.dimensionId(),
            sourceCommon.queueSpawn(),
            sourceCommon.respawnDelaySeconds(),
            sourceCommon.vehicleBackend(),
            sourceCommon.playerStateConfig(),
            sourceCommon.outsideBoundaryDeathSeconds(),
            sourceCommon.aircraftOutsideBoundaryCleanupSeconds(),
            sourceCommon.playerMaxY(),
            sourceCommon.aircraftPassengerMaxY(),
            sourceCommon.campADisplayName(),
            sourceCommon.campBDisplayName(),
            sourceCommon.campAVoicePack(),
            sourceCommon.campBVoicePack(),
            sourceCommon.progressSpeed(),
            sourceCommon.resultConfig(),
            sourceCommon.disableHunger(),
            sourceCommon.enableAutoTeamSwitchPrompt(),
            sourceCommon.disableInventoryChange(),
            sourceCommon.enableGoatHorn(),
            sourceCommon.enablePlayerDowned(),
            sourceCommon.enableRespawnCountdownWhileDowned(),
            sourceCommon.enableBackAgainstWall(),
            sourceCommon.enableRandomLoadout(),
            sourceCommon.enableRandomAttachments(),
            sourceCommon.enableParachute(),
            sourceCommon.enableDeathSupportDrop(),
            sourceCommon.disableUndyingTotem(),
            sourceCommon.allowPlayerCampSwitch(),
            sourceCommon.supportCampAHeroKits(),
            sourceCommon.supportCampBHeroKits(),
            sourceCommon.supportCampAShellings(),
            sourceCommon.supportCampBShellings(),
            sourceCommon.roomPrimaryWeaponIds(),
            sourceCommon.roomSecondaryWeaponIds(),
            sourceCommon.roomMeleeWeaponIds(),
            sourceCommon.roomGadget1Ids(),
            sourceCommon.roomGadget2Ids(),
            sourceCommon.roomThrowableIds(),
            sourceCommon.campAEquipmentSetId(),
            sourceCommon.campBEquipmentSetId()
        );
        return new RoomDefinition(
            target.roomId(),
            template.displayName(),
            template.gameplayId(),
            target.lifecycleState(),
            copiedCommon,
            template.gameplayConfig()
        );
    }

    private void copyVehicleConfigs(String sourceRoomId, String targetRoomId) {
        VehicleConfigRepository repository = dataManager.vehicleConfigs();
        for (RoomGameplayId gameplayId : SUPPORTED_GAMEPLAY_IDS) {
            for (RoomVehicleBackend backend : RoomVehicleBackend.values()) {
                List<VehicleConfigEntry> copiedEntries = cloneVehicleEntries(
                    repository.getEntries(sourceRoomId, gameplayId.value(), backend)
                );
                repository.saveEntries(targetRoomId, gameplayId.value(), backend, copiedEntries);
            }
        }
    }

    private void clearVehicleConfigs(String roomId) {
        VehicleConfigRepository repository = dataManager.vehicleConfigs();
        for (RoomGameplayId gameplayId : SUPPORTED_GAMEPLAY_IDS) {
            for (RoomVehicleBackend backend : RoomVehicleBackend.values()) {
                repository.saveEntries(roomId, gameplayId.value(), backend, List.of());
            }
        }
    }

    private List<VehicleConfigEntry> cloneVehicleEntries(List<VehicleConfigEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<VehicleConfigEntry> copied = new ArrayList<>();
        for (VehicleConfigEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            VehicleConfigEntry clone = new VehicleConfigEntry();
            clone.setEntryId(entry.getEntryId());
            clone.setGameplay(entry.getGameplay());
            clone.setTeam(entry.getTeam());
            clone.setVehicleId(entry.getVehicleId());
            clone.setYwzjVehicleId(entry.getYwzjVehicleId());
            clone.setDisplayId(entry.getDisplayId());
            clone.setRequiredTeamSize(entry.getRequiredTeamSize());
            clone.setRespawnSeconds(entry.getRespawnSeconds());
            clone.setSpawnX(entry.getSpawnX());
            clone.setSpawnY(entry.getSpawnY());
            clone.setSpawnZ(entry.getSpawnZ());
            clone.setSpawnYaw45(entry.getSpawnYaw45());
            clone.setStageIndex(entry.getStageIndex());
            clone.setTeamWideDeployEnabled(entry.isTeamWideDeployEnabled());
            clone.setEnabled(entry.isEnabled());
            copied.add(clone);
        }
        return copied;
    }

    private void clearDimension(RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        dimensionResetService.clearDimensionData(definition);
    }

    private boolean sameDimension(RoomDefinition left, RoomDefinition right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(
            normalizeNullable(left.commonConfig().dimensionId()),
            normalizeNullable(right.commonConfig().dimensionId())
        );
    }

    private boolean isTransientLifecycle(RoomLifecycleState lifecycleState) {
        return lifecycleState == RoomLifecycleState.SAVING || lifecycleState == RoomLifecycleState.CLEANING;
    }

    private boolean isBlockedTargetLifecycle(RoomLifecycleState lifecycleState) {
        return lifecycleState == RoomLifecycleState.RUNNING
            || lifecycleState == RoomLifecycleState.ENDING
            || lifecycleState == RoomLifecycleState.SAVING
            || lifecycleState == RoomLifecycleState.CLEANING;
    }

    private String appendEvacuated(String message, int evacuated) {
        if (evacuated <= 0) {
            return message;
        }
        return message + " | 已清理玩家: " + evacuated;
    }

    private String detailOf(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception == null ? "未知错误" : exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }

    private String normalizeRoomId(String roomId) {
        return normalizeNullable(roomId);
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
