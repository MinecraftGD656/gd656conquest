package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudBuildContext;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudElement;

import java.util.List;

public interface RoomGameplayBoundaryHudProvider {
    void collectBoundaryElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context);

    static RoomGameplayBoundaryHudProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayBoundaryHudProvider.class::isInstance)
            .map(RoomGameplayBoundaryHudProvider.class::cast)
            .orElse(null);
    }
}
