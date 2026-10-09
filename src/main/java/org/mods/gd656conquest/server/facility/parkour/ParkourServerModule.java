package org.mods.gd656conquest.server.facility.parkour;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class ParkourServerModule implements ServerModule {
    @Override
    public void onServerStarting(MinecraftServer server) {
        ParkourRuntimeManager.of(server).initialize();
    }

    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        ParkourRuntimeManager.of(server).onServerTick(gameTick);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        ParkourRuntimeManager.of(server).shutdown();
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        if (player != null && player.server != null) {
            ParkourRuntimeManager.of(player.server).onPlayerTick(player);
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayer player) {
        if (player != null && player.server != null) {
            ParkourRuntimeManager.of(player.server).onPlayerRespawn(player);
        }
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        if (player != null && player.server != null) {
            ParkourRuntimeManager.of(player.server).onPlayerLogout(player);
        }
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        if (player != null && player.server != null) {
            ParkourRuntimeManager.of(player.server).onPlayerLogin(player);
        }
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        if (player != null && player.server != null) {
            ParkourRuntimeManager.of(player.server).onPlayerChangedDimension(player);
        }
    }

    @Override
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        ParkourRuntimeManager.of(player.server).onPlayerDeath(player);
    }
}
