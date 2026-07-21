package org.mods.gd656conquest.server.room.bootstrap.defaults;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepositorySource;
import org.mods.gd656conquest.server.room.bootstrap.RoomCoreBootstrap;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.room.gameplay.breakthrough.BreakthroughGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.conquest.ConquestGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.deathmatch.DeathmatchGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.gunmaster.GunmasterGameplayEntry;

import java.util.List;
import java.util.Objects;

public final class DefaultRoomCoreBootstrap {
    private DefaultRoomCoreBootstrap() {
    }

    public static RoomCoreService bootstrap(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return RoomCoreBootstrap.bootstrap(
            server,
            new RoomDefinitionRepositorySource(ConquestDataManager.of(server).roomDefinitions()),
            List.of(
                new ConquestGameplayEntry(server),
                new BreakthroughGameplayEntry(server),
                new DeathmatchGameplayEntry(server),
                new GunmasterGameplayEntry(server)
            )
        );
    }
}
