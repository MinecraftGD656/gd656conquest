package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;

import java.util.List;

public interface RoomGameplayBoundaryPolicyProvider {
    default int boundaryPenaltySeconds(RoomDefinition room) {
        return 0;
    }

    default boolean hasConfiguredBoundary(RoomDefinition room, RunningRuntimeState state) {
        return false;
    }

    default List<List<RoomPosition>> resolveAllowedBoundaries(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        return List.of();
    }

    default List<List<RoomPosition>> resolveAllowedBoundariesForPlayer(
        RoomDefinition room, RunningRuntimeState state, ServerPlayer player, RoomTeamId teamId
    ) {
        return resolveAllowedBoundaries(room, state, teamId);
    }

    default List<List<RoomPosition>> resolveFlightAllowedBoundariesForPlayer(
        RoomDefinition room, RunningRuntimeState state, ServerPlayer player, RoomTeamId teamId
    ) {
        return resolveAllowedBoundariesForPlayer(room, state, player, teamId);
    }

    default boolean isDeploymentPositionAllowed(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId, double x, double z) {
        return true;
    }

    default boolean isInsideAllowedBoundary(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, RoomTeamId teamId) {
        return RoomBoundaryRuleEvaluator.isInsideAnyBoundary(player, resolveAllowedBoundariesForPlayer(room, state, player, teamId));
    }

    default boolean isOutsideAllowedBoundary(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, RoomTeamId teamId) {
        return RoomBoundaryRuleEvaluator.isOutsideAllBoundaries(player, resolveAllowedBoundariesForPlayer(room, state, player, teamId));
    }

    static RoomGameplayBoundaryPolicyProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayBoundaryPolicyProvider.class::isInstance)
            .map(RoomGameplayBoundaryPolicyProvider.class::cast)
            .orElse(null);
    }
}
