package org.mods.gd656conquest.server.room.bootstrap;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayEntry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class RoomCoreBootstrap {
    private RoomCoreBootstrap() {
    }

    public static RoomCoreService bootstrap(MinecraftServer server, RoomDefinitionSource definitionSource) {
        return bootstrap(server, definitionSource, List.of());
    }

    public static RoomCoreService bootstrap(
        MinecraftServer server,
        RoomDefinitionSource definitionSource,
        Collection<? extends IRoomGameplayEntry> gameplayEntries
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(definitionSource, "definitionSource");
        Objects.requireNonNull(gameplayEntries, "gameplayEntries");
        RoomCoreService coreService = RoomCoreServiceFactory.create(server, gameplayEntries);

        coreService.registerRooms(definitionSource.loadAll());
        return coreService;
    }
}
