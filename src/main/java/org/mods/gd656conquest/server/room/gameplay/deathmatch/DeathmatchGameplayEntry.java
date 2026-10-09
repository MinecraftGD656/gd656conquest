package org.mods.gd656conquest.server.room.gameplay.deathmatch;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;

import java.util.Objects;

public final class DeathmatchGameplayEntry implements IRoomGameplayEntry {
    private final MinecraftServer server;

    public DeathmatchGameplayEntry(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public RoomGameplayId gameplayId() {
        return RoomGameplayId.DEATHMATCH;
    }

    @Override
    public IRoomGameplayRuntime createRuntime(RoomDefinition room, RunningRuntimeState state) {
        return new DeathmatchGameplayRuntime(server, state);
    }
}
