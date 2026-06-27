package org.mods.gd656conquest.server.room.bootstrap;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayEntry;

import java.util.Collection;
import java.util.Objects;

public final class RoomCoreServiceFactory {
    private RoomCoreServiceFactory() {
    }

    public static RoomCoreService create(MinecraftServer server, Collection<? extends IRoomGameplayEntry> gameplayEntries) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(gameplayEntries, "gameplayEntries");
        return new RoomCoreService(RoomCoreComponentsFactory.create(server, gameplayEntries));
    }
}
