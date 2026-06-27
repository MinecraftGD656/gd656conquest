package org.mods.gd656conquest.server.room.vehicle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSnapshot;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class VehicleBoundaryPenaltyService {
    private final RoomVehicleRuntimeService vehicleRuntimeService;

    public VehicleBoundaryPenaltyService(RoomVehicleRuntimeService vehicleRuntimeService) {
        this.vehicleRuntimeService = Objects.requireNonNull(vehicleRuntimeService, "vehicleRuntimeService");
    }

    public boolean handleBoundaryTimeout(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        if (room == null || state == null || player == null) {
            return false;
        }
        Entity vehicle = player.getVehicle();
        if (!VehicleCompatFacade.isManagedVehicleEntity(vehicle)) {
            return false;
        }
        if (VehicleCompatFacade.isAircraftEntity(vehicle)) {
            state.getAircraftOutsideBoundarySinceTicks().putIfAbsent(vehicle.getUUID(), gameTick);
            return false;
        }
        if (!shouldCurrentPlayerDestroyVehicle(state, player, vehicle)) {
            return false;
        }
        UUID vehicleUuid = vehicle.getUUID();
        state.getAircraftOutsideBoundarySinceTicks().remove(vehicleUuid);
        boolean destroyed = VehicleCompatFacade.destroyManagedVehicleNow(vehicle);
        vehicleRuntimeService.markVehicleDestroyed(room, state, vehicleUuid, gameTick);
        return destroyed;
    }

    public void updateAircraftBoundaryState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId playerTeam,
        boolean outsideAllowedBoundary,
        long gameTick
    ) {
        if (room == null || state == null || player == null) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (!VehicleCompatFacade.isManagedVehicleEntity(vehicle) || !VehicleCompatFacade.isAircraftEntity(vehicle)) {
            clearTrackedAircraft(state, vehicle);
            return;
        }
        if (playerTeam == null || !outsideAllowedBoundary) {
            clearTrackedAircraft(state, vehicle);
            return;
        }
        state.getAircraftOutsideBoundarySinceTicks().putIfAbsent(vehicle.getUUID(), gameTick);
    }

    public void processAircraftBoundaryTimeouts(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        int cleanupSeconds = room.gameplayAircraftBoundaryCleanupSeconds();
        if (cleanupSeconds <= 0) {
            state.getAircraftOutsideBoundarySinceTicks().clear();
            return;
        }
        RoomGameplayBoundaryPolicyProvider boundaryPolicyProvider = RoomGameplayBoundaryPolicyProvider.resolve(state);
        boolean hasConfiguredBoundary = boundaryPolicyProvider == null
            ? room.conquestConfig().hasAnyBoundaryConfigured()
            : boundaryPolicyProvider.hasConfiguredBoundary(room, state);
        if (!hasConfiguredBoundary) {
            state.getAircraftOutsideBoundarySinceTicks().clear();
            return;
        }
        long cleanupTicks = cleanupSeconds * 20L;
        for (UUID vehicleUuid : new ArrayList<>(state.getAircraftOutsideBoundarySinceTicks().keySet())) {
            Entity vehicle = vehicleRuntimeService.findEntity(vehicleUuid).orElse(null);
            if (!VehicleCompatFacade.isManagedVehicleEntity(vehicle) || !VehicleCompatFacade.isAircraftEntity(vehicle)) {
                state.getAircraftOutsideBoundarySinceTicks().remove(vehicleUuid);
                continue;
            }
            RoomTeamId vehicleTeam = resolveBoundaryTeam(state, vehicle);
            if (vehicleTeam == null) {
                state.getAircraftOutsideBoundarySinceTicks().remove(vehicleUuid);
                continue;
            }
            List<List<RoomPosition>> allowedBoundaries = boundaryPolicyProvider == null
                ? room.conquestConfig().allowedBoundariesForTeam(vehicleTeam)
                : boundaryPolicyProvider.resolveAllowedBoundaries(room, state, vehicleTeam);
            if (!RoomBoundaryRuleEvaluator.isOutsideAllBoundaries(vehicle.getX(), vehicle.getZ(), allowedBoundaries)) {
                state.getAircraftOutsideBoundarySinceTicks().remove(vehicleUuid);
                continue;
            }
            long outsideSince = state.getAircraftOutsideBoundarySinceTicks().getOrDefault(vehicleUuid, gameTick);
            if (gameTick - outsideSince < cleanupTicks) {
                continue;
            }
            boolean destroyed = VehicleCompatFacade.destroyManagedVehicleNow(vehicle);
            vehicleRuntimeService.markVehicleDestroyed(room, state, vehicleUuid, gameTick);
            state.getAircraftOutsideBoundarySinceTicks().remove(vehicleUuid);
            if (!destroyed && !vehicle.isRemoved()) {
                vehicle.discard();
            }
        }
    }

    private boolean shouldCurrentPlayerDestroyVehicle(RunningRuntimeState state, ServerPlayer player, Entity vehicle) {
        if (state == null || player == null || vehicle == null) {
            return false;
        }
        VehicleCompatSnapshot snapshot = VehicleCompatFacade.snapshot(vehicle).orElse(null);
        if (snapshot == null || snapshot.driverUuid() == null) {
            return true;
        }
        UUID currentPlayerId = player.getUUID();
        UUID driverId = snapshot.driverUuid();
        if (currentPlayerId.equals(driverId)) {
            return true;
        }
        if (!state.getRoomPlayers().contains(driverId)) {
            return true;
        }
        ServerPlayer driver = player.server == null ? null : player.server.getPlayerList().getPlayer(driverId);
        return driver == null || !driver.isAlive();
    }

    private void clearTrackedAircraft(RunningRuntimeState state, Entity vehicle) {
        if (state == null || vehicle == null) {
            return;
        }
        state.getAircraftOutsideBoundarySinceTicks().remove(vehicle.getUUID());
    }

    private RoomTeamId resolveBoundaryTeam(RunningRuntimeState state, Entity vehicle) {
        if (state == null || vehicle == null) {
            return null;
        }
        VehicleCompatSnapshot snapshot = VehicleCompatFacade.snapshot(vehicle).orElse(null);
        if (snapshot != null && snapshot.driverUuid() != null) {
            RoomTeamId driverTeam = state.getRoomTeams().get(snapshot.driverUuid());
            if (driverTeam != null) {
                return driverTeam;
            }
        }
        VehicleRuntimeRecord record = vehicleRuntimeService.findManagedRecord(vehicle).orElse(null);
        if (record == null) {
            return null;
        }
        RoomTeamId configuredTeam = VehicleRuntimeTeamResolver.resolveConfiguredTeam(record.getConfiguredTeam());
        if (configuredTeam == null) {
            return null;
        }
        if (record.getLogicalState() == VehicleLogicalState.ENEMY_CONTROLLED
            || record.getOwnershipState() == VehicleOwnershipState.ENEMY) {
            return configuredTeam == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        }
        return configuredTeam;
    }
}
