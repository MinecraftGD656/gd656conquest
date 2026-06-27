package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Objects;

public record RoomSpawnGroupConfig(boolean enabled, List<RoomPosition> spawns) {
    public RoomSpawnGroupConfig {
        Objects.requireNonNull(spawns, "spawns");
        spawns = List.copyOf(spawns);
    }

    public static RoomSpawnGroupConfig defaults() {
        return new RoomSpawnGroupConfig(true, List.of());
    }
}
