package org.mods.gd656conquest.server.loadout;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.packet.LoadoutUiOpenPacket;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayVehicleDeployRuleProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.vehicle.VehicleDisplayStateTokenResolver;
import org.mods.gd656conquest.server.room.vehicle.VehicleDeployResolution;
import org.mods.gd656conquest.server.room.vehicle.VehicleDisplayState;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeRecord;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeTeamResolver;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class LoadoutVehiclePageSnapshotBuilder {
    private static final float LOW_HEALTH_RATIO = 0.35F;

    private LoadoutVehiclePageSnapshotBuilder() {
    }

    static Snapshot build(ServerPlayer player) {
        if (player == null || player.server == null) {
            return new Snapshot(false, List.of());
        }
        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(player.server);
        if (!runtimeManager.isInitialized()) {
            return new Snapshot(false, List.of());
        }
        String roomId = runtimeManager.findPlayerRoomId(player.getUUID()).orElse("");
        if (roomId.isBlank()) {
            return new Snapshot(false, List.of());
        }
        RoomDefinition room = runtimeManager.findRoom(roomId).orElse(null);
        RunningRuntimeState runningState = runtimeManager.findRunningState(roomId).orElse(null);
        if (room == null || runningState == null) {
            return new Snapshot(false, List.of());
        }
        RoomTeamId selfTeam = runningState.getRoomTeams().get(player.getUUID());
        if (selfTeam == null) {
            return new Snapshot(false, List.of());
        }
        RoomGameplayVehicleDeployRuleProvider gameplayRules = RoomGameplayVehicleDeployRuleProvider.resolve(runningState);
        boolean vehiclePageAvailable = gameplayRules == null || gameplayRules.supportsVehicleDeploy(room, runningState);
        List<LoadoutUiOpenPacket.VehicleEntryPayload> entries = runtimeManager.coreService()
            .vehicleRuntimeService()
            .getRecords(roomId)
            .stream()
            .filter(record -> VehicleRuntimeTeamResolver.resolveConfiguredTeam(record) == selfTeam)
            .filter(record -> gameplayRules == null || gameplayRules.shouldDisplayVehicleDeployEntry(room, runningState, player, selfTeam, record))
            .sorted(Comparator.comparing(LoadoutVehiclePageSnapshotBuilder::sortKey, String.CASE_INSENSITIVE_ORDER))
            .map(record -> toPayload(runtimeManager, room, runningState, player, selfTeam, record))
            .toList();
        return new Snapshot(vehiclePageAvailable, entries);
    }

    private static LoadoutUiOpenPacket.VehicleEntryPayload toPayload(
        RoomCoreRuntimeManager runtimeManager,
        RoomDefinition room,
        RunningRuntimeState runningState,
        ServerPlayer player,
        RoomTeamId selfTeam,
        VehicleRuntimeRecord record
    ) {
        VehicleDeployResolution resolution = runtimeManager.coreService()
            .deploySelectionService()
            .resolveVehicleSelection(room, runningState, player.getUUID(), selfTeam, record.getEntryId());
        long gameTick = player.server == null ? 0L : player.server.getTickCount();
        boolean selected = "vehicle".equalsIgnoreCase(runningState.getDeploySelectionKinds().get(player.getUUID()))
            && record.getEntryId().equalsIgnoreCase(runningState.getDeploySelectionTargetIds().get(player.getUUID()));
        long deployReadyTick = runningState.getDeployReadyTicks().getOrDefault(player.getUUID(), -1L);
        boolean deployBlockedByCountdown = deployReadyTick > gameTick;
        boolean waitingForReady = selected && deployBlockedByCountdown;
        int readyRemainSeconds = waitingForReady ? Math.max(1, (int) Math.ceil((deployReadyTick - gameTick) / 20.0D)) : 0;
        boolean lowHealth = isLowHealth(record);
        int healthPercent = resolveHealthPercent(record);
        int occupiedSeatCount = Math.max(record.getPassengerCount(), record.getOccupantPlayerIds().size());
        int seatCount = 0;
        int healthCurrent = resolveHealthCurrent(record);
        int healthMax = resolveHealthMax(record);
        return new LoadoutUiOpenPacket.VehicleEntryPayload(
            record.getEntryId(),
            resolveDisplayName(record),
            VehicleDisplayStateTokenResolver.toToken(resolution.displayState()),
            resolveStatusText(resolution, lowHealth, waitingForReady, readyRemainSeconds),
            resolveActionText(resolution, waitingForReady),
            resolution.success() && (!deployBlockedByCountdown || !selected),
            selected,
            waitingForReady,
            resolution.success() && !deployBlockedByCountdown,
            lowHealth,
            readyRemainSeconds,
            healthPercent
            ,
            healthCurrent,
            healthMax,
            occupiedSeatCount,
            seatCount,
            resolveDetailText(record, occupiedSeatCount, seatCount)
        );
    }

    private static String sortKey(VehicleRuntimeRecord record) {
        String displayName = resolveDisplayName(record);
        if (!displayName.isBlank()) {
            return displayName;
        }
        return record == null ? "" : record.getEntryId();
    }

    private static String resolveDisplayName(VehicleRuntimeRecord record) {
        if (record == null) {
            return "";
        }
        String displayName = record.getConfiguredDisplayId();
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        String vehicleId = record.getConfiguredVehicleId();
        if (vehicleId != null && !vehicleId.isBlank()) {
            return vehicleId.trim();
        }
        return record.getEntryId();
    }

    private static boolean isLowHealth(VehicleRuntimeRecord record) {
        if (record == null || record.getSpawnedEntityUuid() == null || record.getMaxHealth() <= 0.0F) {
            return false;
        }
        return record.getHealth() / record.getMaxHealth() <= LOW_HEALTH_RATIO;
    }

    private static int resolveHealthPercent(VehicleRuntimeRecord record) {
        if (record == null || record.getSpawnedEntityUuid() == null || record.getMaxHealth() <= 0.0F) {
            return -1;
        }
        return Math.max(0, Math.min(100, Math.round(record.getHealth() / record.getMaxHealth() * 100.0F)));
    }

    private static int resolveHealthCurrent(VehicleRuntimeRecord record) {
        if (record == null || record.getSpawnedEntityUuid() == null || record.getMaxHealth() <= 0.0F) {
            return -1;
        }
        return Math.max(0, Math.round(record.getHealth()));
    }

    private static int resolveHealthMax(VehicleRuntimeRecord record) {
        if (record == null || record.getSpawnedEntityUuid() == null || record.getMaxHealth() <= 0.0F) {
            return -1;
        }
        return Math.max(1, Math.round(record.getMaxHealth()));
    }

    private static String resolveStatusText(VehicleDeployResolution resolution, boolean lowHealth, boolean waitingForReady, int readyRemainSeconds) {
        if (waitingForReady) {
            return "已选定部署，等待倒计时结束" + (readyRemainSeconds > 0 ? "（约 " + readyRemainSeconds + " 秒）" : "");
        }
        VehicleDisplayState displayState = resolution == null ? VehicleDisplayState.READY_TO_DEPLOY : resolution.displayState();
        String base = switch (displayState) {
            case READY_TO_DEPLOY -> "载具已完成待命，可立即部署";
            case FRIENDLY_EMPTY -> "己方空载具，可直接接管驾驶位";
            case SQUAD_CONTROLLED -> "小队成员正在驾驶，可补位搭乘";
            case TEAM_CONTROLLED -> resolution != null && resolution.success()
                ? "阵营友军正在驾驶，可补位搭乘"
                : "阵营友军正在驾驶，当前不开放部署";
            case FULL -> "当前座位已满，暂不可部署";
            case WAITING -> "载具正在重整，暂不可部署";
            case ENEMY_CONTROLLED -> "载具处于敌军控制状态";
        };
        if (displayState == VehicleDisplayState.WAITING && resolution != null && resolution.message() != null && !resolution.message().isBlank()) {
            base = resolution.message();
        }
        if (!lowHealth) {
            return base;
        }
        return "低耐久预警 | " + base;
    }

    private static String resolveActionText(VehicleDeployResolution resolution, boolean waitingForReady) {
        if (waitingForReady) {
            return "等待部署";
        }
        if (resolution == null || !resolution.success()) {
            return "不可部署";
        }
        return switch (resolution.actionType()) {
            case SPAWN_AND_BOARD -> "部署";
            case BOARD_EXISTING_DRIVER, BOARD_EXISTING_PASSENGER -> "乘坐";
            case FAIL -> "不可部署";
        };
    }

    private static String resolveDetailText(VehicleRuntimeRecord record, int occupiedSeatCount, int seatCount) {
        if (record == null) {
            return "";
        }
        String vehicleCode = resolveVehicleCode(record.getConfiguredVehicleId());
        String entryCode = record.getEntryId() == null ? "-" : record.getEntryId();
        if (record.getDriverName() != null && !record.getDriverName().isBlank()) {
            return "型号 " + vehicleCode + " | 驾驶员 " + record.getDriverName() + " | 乘员 " + occupiedSeatCount;
        }
        return "型号 " + vehicleCode + " | 乘员 " + occupiedSeatCount + " | 条目 " + entryCode;
    }

    private static String resolveVehicleCode(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            return "-";
        }
        int separator = vehicleId.indexOf(':');
        String code = separator >= 0 && separator + 1 < vehicleId.length()
            ? vehicleId.substring(separator + 1)
            : vehicleId;
        return code.trim().toUpperCase(Locale.ROOT);
    }

    record Snapshot(boolean available, List<LoadoutUiOpenPacket.VehicleEntryPayload> entries) {
        Snapshot {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
