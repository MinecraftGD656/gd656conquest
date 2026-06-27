package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Map;

public interface RoomGameplayObjectiveSnapshotProvider {
    default Map<String, Double> objectiveProgressSnapshot() {
        return Map.of();
    }

    default Map<String, RoomTeamId> objectiveOwnerSnapshot() {
        return Map.of();
    }

    default Map<String, RoomTeamId> objectiveDrivingSnapshot() {
        return Map.of();
    }

    default String activeObjectiveCode(RoomDefinition room, ServerPlayer player) {
        return null;
    }

    static RoomGameplayObjectiveSnapshotProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayObjectiveSnapshotProvider.class::isInstance)
            .map(RoomGameplayObjectiveSnapshotProvider.class::cast)
            .orElse(null);
    }
}
