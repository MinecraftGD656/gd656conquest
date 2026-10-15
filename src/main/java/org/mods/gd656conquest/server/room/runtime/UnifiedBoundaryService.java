package org.mods.gd656conquest.server.room.runtime;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.compat.vehicle.BoundaryVehicleDestruction;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveBlockAreaResolver;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.RoomDeathEventDispatcher;

/** One player-based rule for horizontal bounds, forbidden blocks and the existing height reports. */
public final class UnifiedBoundaryService {
    private static final RoomObjectiveBlockAreaResolver BLOCKS = new RoomObjectiveBlockAreaResolver();
    private UnifiedBoundaryService() {}

    public static void process(MinecraftServer server, RoomDefinition room, RunningRuntimeState state, long tick) {
        RoomCommonConfig config = room.commonConfig();
        // This map keeps the existing room/session cleanup lifecycle, but now stores player deadlines.
        Map<UUID, Long> deadlines = state.getOutsideBoundarySinceTicks();
        state.getAircraftOutsideBoundarySinceTicks().clear();
        for (UUID id : new ArrayList<>(deadlines.keySet())) {
            if (!state.getRoomPlayers().contains(id)) clear(server.getPlayerList().getPlayer(id), state, id);
        }
        for (UUID id : new ArrayList<>(state.getRoomPlayers())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || state.isInRespawnFlow(id)
                || (player.hasPermissions(2) && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE)) {
                clear(player, state, id); continue;
            }
            Entity vehicle = player.getVehicle();
            boolean supported = VehicleCompatFacade.isVehicleEntity(vehicle);
            boolean aircraft = supported && VehicleCompatFacade.isAircraftEntity(vehicle);
            int kind = aircraft ? 2 : supported ? 1 : 0;
            int seconds = kind == 2 ? config.flightVehicleOutsideBoundaryDeathSeconds()
                : kind == 1 ? config.aircraftOutsideBoundaryCleanupSeconds() : config.outsideBoundaryDeathSeconds();
            boolean outside = isOutside(room, state, player, tick);
            if (!outside || seconds <= 0) { clear(player, state, id); continue; }
            long deadline = BoundaryCountdown.tighten(deadlines.get(id), tick, seconds);
            deadlines.put(id, deadline);
            int remaining = BoundaryCountdown.remainingTicks(deadline, tick);
            if (remaining == 0) {
                // The native destruction callback captures the passengers at the actual explosion.
                if (supported && BoundaryVehicleDestruction.destroy(vehicle, room, state)) continue;
                markDeserter(player);
                killDeserter(player);
                continue;
            }
            state.getBoundaryPenaltyVisualApplied().add(id);
            state.getBoundaryPenaltyLastShownSeconds().put(id, BoundaryCountdown.displaySeconds(remaining));
            // Authoritative remaining ticks also cover tightening and changes of mounted state.
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.boundaryUpdate(remaining, kind));
        }
    }

    /** Geometry is independent of penalty duration and evaluated again at the fatal event. */
    public static boolean isOutside(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long tick) {
        if (room == null || state == null || player == null) return false;
        RoomCommonConfig config = room.commonConfig();
        RoomGameplayBoundaryPolicyProvider policy = RoomGameplayBoundaryPolicyProvider.resolve(state);
        RoomTeamId team = state.getRoomTeams().get(player.getUUID());
        Entity vehicle = player.getVehicle();
        boolean aircraft = VehicleCompatFacade.isVehicleEntity(vehicle) && VehicleCompatFacade.isAircraftEntity(vehicle);
        List<List<RoomPosition>> allowed = policy == null ? room.conquestConfig().allowedBoundariesForTeam(team)
            : aircraft ? policy.resolveFlightAllowedBoundariesForPlayer(room, state, player, team)
            : policy.resolveAllowedBoundariesForPlayer(room, state, player, team);
        boolean horizontal = aircraft
            ? FlightBoundaryGeometry.outside(player.getX(), player.getZ(), allowed, config.flightBoundaryPaddingBlocks())
            : policy == null ? RoomBoundaryRuleEvaluator.isOutsideAllBoundaries(player, allowed)
            : policy.isOutsideAllowedBoundary(room, state, player, team);
        return horizontal || BLOCKS.isPlayerInsideOutOfBoundsBlock(player)
            || aboveHeight(config, state, player, aircraft, tick);
    }

    public static boolean isOutside(ServerPlayer player) {
        if (player == null || player.server == null) return false;
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        String id = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (id == null) return false;
        return isOutside(manager.findRoom(id).orElse(null), manager.findRunningState(id).orElse(null), player, player.server.getTickCount());
    }

    public static void markDeserter(ServerPlayer player) {
        if (player == null || player.server == null) return;
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        RunningRuntimeState state = manager.findPlayerRoomId(player.getUUID()).flatMap(manager::findRunningState).orElse(null);
        if (state != null) {
            state.getBoundaryDeathPendingPlayers().add(player.getUUID());
            clear(player, state, player.getUUID());
        }
    }

    /** A death listener may heal the room proxy after already entering deployment. */
    public static void killDeserter(ServerPlayer player) {
        if (player != null && RoomDeathEventDispatcher.isStillAliveOutsideDeployment(player)) {
            markDeserter(player);
            player.kill();
        }
    }

    private static boolean aboveHeight(RoomCommonConfig config, RunningRuntimeState state, ServerPlayer player, boolean aircraft, long tick) {
        UUID id = player.getUUID();
        Long report = state.getBoundaryHeightReportTicks().get(id);
        if (report == null || tick - report > 20L) {
            state.getBoundaryPlayerMaxYReportedPlayers().remove(id);
            state.getBoundaryAircraftMaxYReportedPlayers().remove(id);
            return false;
        }
        Entity vehicle = player.getVehicle();
        double y = aircraft && vehicle != null ? Math.max(player.getY(), vehicle.getY()) : player.getY();
        int max = aircraft ? config.aircraftPassengerMaxY() : config.playerMaxY();
        return max > 0 && y > max && (aircraft ? state.getBoundaryAircraftMaxYReportedPlayers()
            : state.getBoundaryPlayerMaxYReportedPlayers()).contains(id);
    }

    public static void clear(ServerPlayer player, RunningRuntimeState state, UUID id) {
        boolean applied = state == null;
        if (state != null && id != null) {
            applied = state.getBoundaryPenaltyVisualApplied().remove(id);
            state.getOutsideBoundarySinceTicks().remove(id);
            state.getBoundaryPenaltyLastShownSeconds().remove(id);
        }
        if (player != null && applied) NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
    }
}
