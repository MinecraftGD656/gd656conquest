package org.mods.gd656conquest.server.pack;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class PackValidationServerModule implements ServerModule {
    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        PackValidationService.of(server).onServerTick(gameTick);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        PackValidationService.forget(server);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        PackValidationService.of(player.server).onPlayerLogin(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        PackValidationService.of(player.server).onPlayerLogout(player);
    }
}
