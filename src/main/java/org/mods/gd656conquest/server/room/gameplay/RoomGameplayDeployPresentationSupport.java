package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;

public final class RoomGameplayDeployPresentationSupport {
    private RoomGameplayDeployPresentationSupport() {
    }

    public static RoomPosition resolveRespawnAnchorPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        RoomTeamId teamId = state == null || player == null ? null : state.getRoomTeams().get(player.getUUID());
        return resolveRespawnAnchorPosition(room, state, player, teamId);
    }

    public static RoomPosition resolveRespawnAnchorPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId
    ) {
        RoomGameplayDeployPresentationProvider provider = RoomGameplayDeployPresentationProvider.resolve(state);
        if (provider != null) {
            RoomPosition anchor = provider.resolveRespawnAnchorPosition(room, state, player);
            if (room != null && org.mods.gd656conquest.server.room.model.RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) return anchor;
            if (anchor != null) {
                return anchor;
            }
        }
        if (room == null) {
            return null;
        }
        RoomPosition teamSpawn = player == null || player.server == null
            ? room.conquestConfig().findTeamSpawn(teamId).orElse(null)
            : new RoomSpawnCandidateSelectionService(player.server)
                .pickSpawn(room.conquestConfig().teamSpawnGroups().get(teamId), state, teamId);
        if (teamSpawn != null) {
            return teamSpawn;
        }
        RoomPosition deathPosition = state == null || player == null ? null : state.getDeathPositions().get(player.getUUID());
        if (deathPosition != null) {
            return deathPosition;
        }
        if (room.commonConfig().queueSpawn() != null) {
            return room.commonConfig().queueSpawn();
        }
        return null;
    }

    public static RoomPosition resolveTeamDeployIconPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId
    ) {
        RoomGameplayDeployPresentationProvider provider = RoomGameplayDeployPresentationProvider.resolve(state);
        if (provider != null) {
            RoomPosition iconPosition = provider.resolveTeamDeployIconPosition(room, state, teamId);
            if (room != null && org.mods.gd656conquest.server.room.model.RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) return iconPosition;
            if (iconPosition != null) {
                return iconPosition;
            }
        }
        if (room == null) {
            return null;
        }
        return room.conquestConfig().findTeamDeployIcon(teamId)
            .or(() -> room.conquestConfig().findTeamSpawn(teamId))
            .orElse(null);
    }

    public static RoomPosition resolveRespawnHoldingPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        RoomGameplayDeployPresentationProvider provider = RoomGameplayDeployPresentationProvider.resolve(state);
        if (provider != null) {
            RoomPosition holding = provider.resolveRespawnHoldingPosition(room, state, player);
            if (holding != null) {
                return holding;
            }
        }
        return state == null || player == null ? null : state.getDeathPositions().get(player.getUUID());
    }

    public static RoomViewPosition resolveDeployViewPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomGameplayDeployViewRequest request
    ) {
        RoomGameplayDeployPresentationProvider provider = RoomGameplayDeployPresentationProvider.resolve(state);
        if (provider != null) {
            RoomViewPosition view = provider.resolveDeployViewPosition(room, state, player, request);
            if (view != null) {
                return view;
            }
        }
        return room == null ? null : room.conquestConfig().deployViewPosition();
    }
}
