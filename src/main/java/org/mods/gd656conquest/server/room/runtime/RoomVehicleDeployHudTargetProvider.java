package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayVehicleDeployRuleProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.vehicle.VehicleDisplayStateTokenResolver;
import org.mods.gd656conquest.server.room.vehicle.VehicleDeployResolution;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeRecord;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeTeamResolver;

import java.util.EnumMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoomVehicleDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    private static final float LOW_HEALTH_RATIO = 0.35F;
    private RunningRuntimeState preparedState;
    private String preparedRoomId = "";
    private Map<RoomTeamId, List<VehicleRuntimeRecord>> preparedRecordsByTeam = Map.of();

    @Override
    public void prepareRoomSnapshot(MinecraftServer server, RoomDefinition room, RunningRuntimeState state) {
        if (server == null || room == null || state == null) {
            clearPreparedSnapshot();
            return;
        }
        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(server);
        if (!runtimeManager.isInitialized()) {
            clearPreparedSnapshot();
            return;
        }
        Map<RoomTeamId, List<VehicleRuntimeRecord>> recordsByTeam = new EnumMap<>(RoomTeamId.class);
        for (RoomTeamId teamId : RoomTeamId.values()) {
            recordsByTeam.put(teamId, runtimeManager.coreService().vehicleRuntimeService().getRecords(room.roomId()).stream()
                .filter(record -> VehicleRuntimeTeamResolver.resolveConfiguredTeam(record) == teamId)
                .sorted(Comparator.comparing(VehicleRuntimeRecord::getEntryId, String.CASE_INSENSITIVE_ORDER))
                .toList());
        }
        preparedState = state;
        preparedRoomId = room.roomId();
        preparedRecordsByTeam = recordsByTeam;
    }

    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        RoomTeamId selfTeam = context.selfTeam();
        UUID playerId = context.playerId();
        if (selfTeam == null || playerId == null) {
            return;
        }
        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(context.server());
        if (!runtimeManager.isInitialized()) {
            return;
        }
        ServerPlayer viewer = context.server().getPlayerList().getPlayer(playerId);
        if (viewer == null) {
            return;
        }
        RoomGameplayVehicleDeployRuleProvider gameplayRules = RoomGameplayVehicleDeployRuleProvider.resolve(context.state());
        List<VehicleRuntimeRecord> records = resolvePreparedRecords(context, selfTeam, runtimeManager);
        for (VehicleRuntimeRecord record : records) {
            if (gameplayRules != null && !gameplayRules.shouldDisplayVehicleDeployEntry(context.room(), context.state(), viewer, selfTeam, record)) {
                continue;
            }
            VehicleDeployResolution resolution = runtimeManager.coreService()
                .deploySelectionService()
                .resolveVehicleSelection(context.room(), context.state(), playerId, selfTeam, record.getEntryId());
            elements.add(RoomDeployHudElement.vehicle(
                record.getEntryId(),
                resolveAnchorX(record),
                resolveAnchorY(record),
                resolveAnchorZ(record),
                selfTeam,
                resolveDrivingTeam(record, context),
                resolveHealthPercent(record),
                resolveDisplayName(record),
                VehicleDisplayStateTokenResolver.toToken(resolution.displayState()),
                isLowHealth(record),
                resolution.success()
            ));
        }
    }

    private List<VehicleRuntimeRecord> resolvePreparedRecords(
        RoomDeployHudBuildContext context,
        RoomTeamId selfTeam,
        RoomCoreRuntimeManager runtimeManager
    ) {
        if (context.state() == preparedState
            && context.room().roomId().equals(preparedRoomId)) {
            return preparedRecordsByTeam.getOrDefault(selfTeam, List.of());
        }
        return runtimeManager.coreService().vehicleRuntimeService().getRecords(context.room().roomId()).stream()
            .filter(record -> VehicleRuntimeTeamResolver.resolveConfiguredTeam(record) == selfTeam)
            .sorted(Comparator.comparing(VehicleRuntimeRecord::getEntryId, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private void clearPreparedSnapshot() {
        preparedState = null;
        preparedRoomId = "";
        preparedRecordsByTeam = Map.of();
    }

    private static double resolveAnchorX(VehicleRuntimeRecord record) {
        if (record == null) {
            return 0.0D;
        }
        return record.getSpawnedEntityUuid() != null ? record.getX() : record.getConfiguredSpawnX();
    }

    private static double resolveAnchorY(VehicleRuntimeRecord record) {
        if (record == null) {
            return 0.0D;
        }
        return record.getSpawnedEntityUuid() != null ? record.getY() : record.getConfiguredSpawnY();
    }

    private static double resolveAnchorZ(VehicleRuntimeRecord record) {
        if (record == null) {
            return 0.0D;
        }
        return record.getSpawnedEntityUuid() != null ? record.getZ() : record.getConfiguredSpawnZ();
    }

    private static RoomTeamId resolveDrivingTeam(VehicleRuntimeRecord record, RoomDeployHudBuildContext context) {
        if (record == null || context == null || record.getDriverPlayerId() == null) {
            return null;
        }
        return context.state().getRoomTeams().get(record.getDriverPlayerId());
    }

    private static float resolveHealthPercent(VehicleRuntimeRecord record) {
        if (record == null || record.getSpawnedEntityUuid() == null || record.getMaxHealth() <= 0.0F) {
            return 100.0F;
        }
        return Math.max(0.0F, Math.min(100.0F, record.getHealth() / record.getMaxHealth() * 100.0F));
    }

    private static boolean isLowHealth(VehicleRuntimeRecord record) {
        if (record == null || record.getSpawnedEntityUuid() == null || record.getMaxHealth() <= 0.0F) {
            return false;
        }
        return record.getHealth() / record.getMaxHealth() <= LOW_HEALTH_RATIO;
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

}
