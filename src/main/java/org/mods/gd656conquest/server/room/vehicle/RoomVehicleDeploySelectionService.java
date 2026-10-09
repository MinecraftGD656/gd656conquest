package org.mods.gd656conquest.server.room.vehicle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayVehicleDeployRuleProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class RoomVehicleDeploySelectionService {
    private final MinecraftServer server;
    private final RoomVehicleRuntimeService vehicleRuntimeService;

    public RoomVehicleDeploySelectionService(MinecraftServer server, RoomVehicleRuntimeService vehicleRuntimeService) {
        this.server = Objects.requireNonNull(server, "server");
        this.vehicleRuntimeService = Objects.requireNonNull(vehicleRuntimeService, "vehicleRuntimeService");
    }

    public VehicleDeployResolution resolve(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        String entryIdRaw
    ) {
        String entryId = normalizeEntryId(entryIdRaw);
        if (room == null || state == null || player == null || teamId == null || entryId.isBlank()) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.READY_TO_DEPLOY, "当前无法解析载具部署");
        }
        RoomGameplayVehicleDeployRuleProvider gameplayRules = RoomGameplayVehicleDeployRuleProvider.resolve(state);
        if (gameplayRules == null || !gameplayRules.supportsVehicleDeploy(room, state)) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.READY_TO_DEPLOY, "当前玩法暂不支持载具部署");
        }
        VehicleRuntimeRecord record = vehicleRuntimeService.getRecord(room.roomId(), entryId).orElse(null);
        if (record == null) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.READY_TO_DEPLOY, "未找到对应载具配置");
        }
        if (!gameplayRules.isVehicleDeployEntryActive(room, state, player, teamId, record)) {
            return VehicleDeployResolution.fail(
                entryId,
                record.getDisplayState(),
                gameplayRules.describeInactiveVehicleDeployEntry(room, state, player, teamId, record)
            );
        }
        RoomTeamId configuredTeam = VehicleRuntimeTeamResolver.resolveConfiguredTeam(record.getConfiguredTeam());
        if (configuredTeam != null && configuredTeam != teamId) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.ENEMY_CONTROLLED, "该载具不属于你的阵营");
        }
        int currentTeamSize = countCurrentTeamPlayers(state, teamId);
        if (currentTeamSize < record.getConfiguredRequiredTeamSize()) {
            return VehicleDeployResolution.fail(
                entryId,
                VehicleDisplayState.READY_TO_DEPLOY,
                String.format(Locale.ROOT, "当前阵营人数不足，需要 %d 人，当前 %d 人", record.getConfiguredRequiredTeamSize(), currentTeamSize)
            );
        }
        if (record.getLogicalState() == VehicleLogicalState.WAITING) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.WAITING, describeWaitingReason(record));
        }
        if (record.getLogicalState() == VehicleLogicalState.ENEMY_CONTROLLED
            || record.getOwnershipState() == VehicleOwnershipState.ENEMY) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.ENEMY_CONTROLLED, "该载具当前正被敌军控制");
        }
        UUID reservedDriverPlayerId = resolveReservedDriverPlayerId(state, player.getUUID(), entryId);
        if (reservedDriverPlayerId != null) {
            if (canBoardTeamControlledVehicle(state, player.getUUID(), reservedDriverPlayerId, record)) {
                return VehicleDeployResolution.success(
                    VehicleDeployActionType.BOARD_EXISTING_PASSENGER,
                    entryId,
                    null,
                    reservedDriverPlayerId,
                    resolvePassengerDisplayState(state, player.getUUID(), reservedDriverPlayerId, record),
                    record.isConfiguredTeamWideDeployEnabled()
                        ? "该载具已被阵营友军预留，生成后将进入乘客位"
                        : "该载具已被小队队友预留，生成后将进入乘客位"
                );
            }
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.TEAM_CONTROLLED, "该载具已被阵营友军预留驾驶位");
        }
        if (record.getSpawnedEntityUuid() == null || record.getLogicalState() == VehicleLogicalState.READY) {
            return VehicleDeployResolution.success(
                VehicleDeployActionType.SPAWN_AND_BOARD,
                entryId,
                null,
                null,
                VehicleDisplayState.READY_TO_DEPLOY,
                "可部署新载具并进入驾驶位"
            );
        }

        Entity entity = vehicleRuntimeService.findEntity(record.getSpawnedEntityUuid()).orElse(null);
        if (!VehicleCompatFacade.isVehicleEntity(entity)) {
            return VehicleDeployResolution.fail(entryId, record.getDisplayState(), "载具实体未同步完成，请稍后重试");
        }

        boolean hasAvailableSeat = VehicleCompatFacade.canAcceptPassenger(entity, player);
        UUID driverPlayerId = record.getDriverPlayerId();
        if (driverPlayerId == null) {
            if (!hasAvailableSeat) {
                return VehicleDeployResolution.fail(entryId, VehicleDisplayState.FULL, "该载具当前已满");
            }
            return VehicleDeployResolution.success(
                VehicleDeployActionType.BOARD_EXISTING_DRIVER,
                entryId,
                entity.getUUID(),
                null,
                VehicleDisplayState.FRIENDLY_EMPTY,
                "可部署到当前空载具驾驶位"
            );
        }

        RoomTeamId driverTeam = state.getRoomTeams().get(driverPlayerId);
        if (driverTeam != teamId) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.ENEMY_CONTROLLED, "该载具当前正被敌军控制");
        }
        if (!hasAvailableSeat) {
            return VehicleDeployResolution.fail(entryId, VehicleDisplayState.FULL, "该载具当前已满");
        }
        if (canBoardTeamControlledVehicle(state, player.getUUID(), driverPlayerId, record)) {
            return VehicleDeployResolution.success(
                VehicleDeployActionType.BOARD_EXISTING_PASSENGER,
                entryId,
                entity.getUUID(),
                null,
                resolvePassengerDisplayState(state, player.getUUID(), driverPlayerId, record),
                record.isConfiguredTeamWideDeployEnabled()
                    ? "可部署到阵营友军驾驶的载具乘客位"
                    : "可部署到小队队友驾驶的载具乘客位"
            );
        }
        return VehicleDeployResolution.fail(entryId, VehicleDisplayState.TEAM_CONTROLLED, "该载具正由阵营友军驾驶，无法部署");
    }

    private int countCurrentTeamPlayers(RunningRuntimeState state, RoomTeamId teamId) {
        if (state == null || teamId == null) {
            return 0;
        }
        int count = 0;
        for (UUID playerId : state.getRoomPlayers()) {
            if (state.getRoomTeams().get(playerId) == teamId && server.getPlayerList().getPlayer(playerId) != null) {
                count++;
            }
        }
        return count;
    }

    private boolean isSameSquad(RunningRuntimeState state, UUID firstPlayerId, UUID secondPlayerId) {
        if (state == null || firstPlayerId == null || secondPlayerId == null) {
            return false;
        }
        String firstLabel = state.getSquadLabelsByPlayer().get(firstPlayerId);
        String secondLabel = state.getSquadLabelsByPlayer().get(secondPlayerId);
        return firstLabel != null && !firstLabel.isBlank() && firstLabel.equalsIgnoreCase(secondLabel);
    }

    private boolean canBoardTeamControlledVehicle(
        RunningRuntimeState state,
        UUID playerId,
        UUID driverPlayerId,
        VehicleRuntimeRecord record
    ) {
        return record != null && record.isConfiguredTeamWideDeployEnabled()
            || isSameSquad(state, playerId, driverPlayerId);
    }

    private VehicleDisplayState resolvePassengerDisplayState(
        RunningRuntimeState state,
        UUID playerId,
        UUID driverPlayerId,
        VehicleRuntimeRecord record
    ) {
        return canBoardTeamControlledVehicle(state, playerId, driverPlayerId, record)
            && record != null
            && record.isConfiguredTeamWideDeployEnabled()
            && !isSameSquad(state, playerId, driverPlayerId)
            ? VehicleDisplayState.TEAM_CONTROLLED
            : VehicleDisplayState.SQUAD_CONTROLLED;
    }

    private String describeWaitingReason(VehicleRuntimeRecord record) {
        if (record == null) {
            return "该载具当前处于等待中";
        }
        if (record.getWaitingUntilTick() == Long.MAX_VALUE) {
            return "该载具被配置为摧毁后不再刷新";
        }
        long remainTicks = record.getWaitingUntilTick() - server.getTickCount();
        if (remainTicks > 0L) {
            long remainSeconds = Math.max(1L, (remainTicks + 19L) / 20L);
            return "该载具仍在等待刷新，剩余约 " + remainSeconds + " 秒";
        }
        return "该载具当前处于等待刷新状态";
    }

    private static String normalizeEntryId(String entryId) {
        return entryId == null ? "" : entryId.trim();
    }

    private UUID resolveReservedDriverPlayerId(RunningRuntimeState state, UUID selfPlayerId, String entryId) {
        if (state == null || entryId == null || entryId.isBlank()) {
            return null;
        }
        UUID winnerPlayerId = null;
        for (UUID candidateId : state.getRoomPlayers()) {
            if (candidateId == null) {
                continue;
            }
            String selectedKind = state.getDeploySelectionKinds().get(candidateId);
            String selectedTargetId = state.getDeploySelectionTargetIds().get(candidateId);
            if (!"vehicle".equalsIgnoreCase(selectedKind) || !entryId.equalsIgnoreCase(selectedTargetId)) {
                continue;
            }
            if (winnerPlayerId == null || compareReservationPriority(state, candidateId, winnerPlayerId) < 0) {
                winnerPlayerId = candidateId;
            }
        }
        if (winnerPlayerId == null || winnerPlayerId.equals(selfPlayerId)) {
            return null;
        }
        return winnerPlayerId;
    }

    private int compareReservationPriority(RunningRuntimeState state, UUID leftPlayerId, UUID rightPlayerId) {
        long leftTick = resolveSelectionChangedTick(state, leftPlayerId);
        long rightTick = resolveSelectionChangedTick(state, rightPlayerId);
        int tickCompare = Long.compare(leftTick, rightTick);
        if (tickCompare != 0) {
            return tickCompare;
        }
        String leftKey = leftPlayerId == null ? "" : leftPlayerId.toString();
        String rightKey = rightPlayerId == null ? "" : rightPlayerId.toString();
        return leftKey.compareToIgnoreCase(rightKey);
    }

    private long resolveSelectionChangedTick(RunningRuntimeState state, UUID playerId) {
        if (state == null || playerId == null) {
            return Long.MAX_VALUE;
        }
        Long changedTick = state.getDeploySelectionChangedTicks().get(playerId);
        return changedTick == null ? Long.MAX_VALUE : changedTick;
    }
}
