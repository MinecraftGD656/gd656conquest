package org.mods.gd656conquest.server.compat.tacz;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class TaczShellingServerModule implements ServerModule {
    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        TaczShellingTestService.of(server).onServerTick(gameTick);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        TaczShellingTestService.forget(server);
    }
}
