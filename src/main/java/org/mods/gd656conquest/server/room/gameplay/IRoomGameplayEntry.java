package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;

public interface IRoomGameplayEntry {
    RoomGameplayId gameplayId();

    IRoomGameplayRuntime createRuntime(RoomDefinition room, RunningRuntimeState state);
}
