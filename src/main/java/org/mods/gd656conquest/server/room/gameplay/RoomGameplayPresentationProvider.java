package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

public interface RoomGameplayPresentationProvider extends RoomTeamPresentationProvider {
    RoomGameplayHudState buildHudState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        boolean inDeployFlow,
        long gameTick
    );

    default void syncEndingHudSubtitles(RoomDefinition room, long gameTick) {
    }

    default int matchDurationSeconds(long gameTick) {
        return 0;
    }

    static RoomGameplayPresentationProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayPresentationProvider.class::isInstance)
            .map(RoomGameplayPresentationProvider.class::cast)
            .orElse(null);
    }
}
