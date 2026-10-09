package org.mods.gd656conquest.server.vehicle;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class VehicleServerModule implements ServerModule {
    private final VehicleMountedHotbarLockService mountedHotbarLockService = new VehicleMountedHotbarLockService();

    @Override
    public void onPlayerTick(ServerPlayer player) {
        mountedHotbarLockService.onPlayerTick(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        mountedHotbarLockService.onPlayerLogout(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayer player) {
        mountedHotbarLockService.onPlayerRespawn(player);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        mountedHotbarLockService.onPlayerLogin(player);
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        mountedHotbarLockService.onPlayerChangedDimension(player);
    }
}
