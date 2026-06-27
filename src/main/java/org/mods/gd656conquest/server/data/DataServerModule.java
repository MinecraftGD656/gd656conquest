package org.mods.gd656conquest.server.data;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class DataServerModule implements ServerModule {
    @Override
    public void onServerStarting(MinecraftServer server) {
        ConquestDataManager.of(server).initialize();
    }

    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        ConquestDataManager.of(server).flushDelayedWrites(gameTick);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        ConquestDataManager.of(server).flushAllWrites();
        ConquestDataManager.forget(server);
    }
}
