package org.mods.gd656conquest.server.room.objective;

public interface RoomObjectiveRuntimeProvider {
    RoomObjectiveRuntimeView objectiveRuntimeView();

    RoomObjectiveSpawnPolicy objectiveSpawnPolicy();
}
