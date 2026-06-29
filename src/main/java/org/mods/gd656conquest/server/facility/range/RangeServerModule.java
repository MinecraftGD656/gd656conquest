package org.mods.gd656conquest.server.facility.range;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

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

    @Override
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        RangeRuntimeManager.of(player.server).onPlayerDeath(player);
    }
}
