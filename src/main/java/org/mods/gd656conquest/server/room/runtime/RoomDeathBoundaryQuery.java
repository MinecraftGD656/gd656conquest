package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveBlockAreaResolver;

/** Read-only fatal-time geometry using the current original room boundary mechanisms. */
public final class RoomDeathBoundaryQuery {
    private static final RoomObjectiveBlockAreaResolver BLOCKS = new RoomObjectiveBlockAreaResolver();
    private RoomDeathBoundaryQuery() {}

    public static boolean isOutside(ServerPlayer player) {
        if (player == null || player.server == null) return false;
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null) return false;
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        RunningRuntimeState state = manager.findRunningState(roomId).orElse(null);
        if (room == null || state == null) return false;
        var policy = RoomGameplayBoundaryPolicyProvider.resolve(state);
        var team = state.getRoomTeams().get(player.getUUID());
        boolean horizontal = policy == null
            ? RoomBoundaryRuleEvaluator.isOutsideAllBoundaries(player, room.conquestConfig().allowedBoundariesForTeam(team))
            : policy.isOutsideAllowedBoundary(room, state, player, team);
        return horizontal || BLOCKS.isPlayerInsideOutOfBoundsBlock(player) || aboveHeight(room, state, player);
    }

    private static boolean aboveHeight(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        Long report = state.getBoundaryHeightReportTicks().get(player.getUUID());
        if (report == null || player.server.getTickCount() - report > 20L) return false;
        Entity vehicle = player.getVehicle();
        boolean aircraft = VehicleCompatFacade.isManagedVehicleEntity(vehicle) && VehicleCompatFacade.isAircraftEntity(vehicle);
        double height = aircraft ? Math.max(player.getY(), vehicle.getY()) : player.getY();
        int maximum = aircraft ? room.commonConfig().aircraftPassengerMaxY() : room.commonConfig().playerMaxY();
        return maximum > 0 && height > maximum && (aircraft ? state.getBoundaryAircraftMaxYReportedPlayers()
            : state.getBoundaryPlayerMaxYReportedPlayers()).contains(player.getUUID());
    }
}
