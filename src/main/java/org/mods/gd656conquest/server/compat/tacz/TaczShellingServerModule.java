package org.mods.gd656conquest.server.compat.tacz;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
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

    @Override
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event == null || event.getProjectile() == null) {
            return;
        }
        if (event.getProjectile().level() != null && event.getProjectile().level().getServer() != null) {
            TaczShellingTestService.of(event.getProjectile().level().getServer()).onProjectileImpact(event);
        }
    }
}
