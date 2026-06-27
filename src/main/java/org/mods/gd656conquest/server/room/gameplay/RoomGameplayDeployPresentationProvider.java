package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;

import java.util.List;

public interface RoomGameplayDeployPresentationProvider {
    default RoomPosition resolveRespawnAnchorPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        return null;
    }

    default RoomPosition resolveRespawnHoldingPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        return null;
    }

    default RoomViewPosition resolveDeployViewPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomGameplayDeployViewRequest request
    ) {
        return null;
    }

    default RoomPosition resolveTeamDeployIconPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId
    ) {
        return null;
    }

    default List<RoomDeployHudIcon> resolveAdditionalDeployIcons(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId selfTeam
    ) {
        return List.of();
    }

    static RoomGameplayDeployPresentationProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayDeployPresentationProvider.class::isInstance)
            .map(RoomGameplayDeployPresentationProvider.class::cast)
            .orElse(null);
    }
}
