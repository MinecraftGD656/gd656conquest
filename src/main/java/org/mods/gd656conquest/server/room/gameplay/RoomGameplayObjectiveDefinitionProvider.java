package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.List;

public interface RoomGameplayObjectiveDefinitionProvider {
    default List<RoomCapturePointDefinition> activeObjectivePoints(RoomDefinition room) {
        return List.of();
    }

    default RoomCapturePointDefinition findObjectivePoint(RoomDefinition room, String objectiveCode) {
        return null;
    }

    static RoomGameplayObjectiveDefinitionProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayObjectiveDefinitionProvider.class::isInstance)
            .map(RoomGameplayObjectiveDefinitionProvider.class::cast)
            .orElse(null);
    }
}
