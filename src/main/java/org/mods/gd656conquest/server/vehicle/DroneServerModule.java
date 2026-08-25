package org.mods.gd656conquest.server.vehicle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

public final class DroneServerModule implements ServerModule {
    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        DroneLifecycleService.of(server).onServerTick(gameTick);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        DroneLifecycleService.of(player.server).onPlayerLogout(player);
    }

    @Override
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        if (RoomCoreRuntimeManager.of(player.server).isPlayerDowned(player.getUUID())) {
            return;
        }
        DroneLifecycleService.of(player.server).onPlayerConfirmedDeath(player);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        DroneLifecycleService.forget(server);
    }
}
