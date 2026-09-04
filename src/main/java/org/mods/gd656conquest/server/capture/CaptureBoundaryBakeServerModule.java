package org.mods.gd656conquest.server.capture;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class CaptureBoundaryBakeServerModule implements ServerModule {
    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        CaptureBoundaryBakeService.of(server).onServerTick(gameTick);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        CaptureBoundaryBakeService.forget(server);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        if (player != null && player.server != null) {
            CaptureBoundaryBakeService.of(player.server).onPlayerLogin(player);
        }
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        if (player != null && player.server != null) {
            CaptureBoundaryBakeService.of(player.server).onPlayerChangedDimension(player);
        }
    }
}
