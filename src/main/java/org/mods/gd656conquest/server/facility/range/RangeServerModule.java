package org.mods.gd656conquest.server.facility.range;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class RangeServerModule implements ServerModule {
    @Override
    public void onServerStarting(MinecraftServer server) {
        RangeRuntimeManager.of(server).initialize();
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        RangeRuntimeManager.of(server).shutdown();
    }

    @Override
    public void onPlayerRespawn(ServerPlayer player) {
        RangeRuntimeManager.of(player.server).onPlayerRespawn(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        RangeRuntimeManager.of(player.server).onPlayerLogout(player);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        RangeRuntimeManager.of(player.server).onPlayerLogin(player);
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        RangeRuntimeManager.of(player.server).onPlayerChangedDimension(player);
    }
}
