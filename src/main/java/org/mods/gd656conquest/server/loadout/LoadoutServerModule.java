package org.mods.gd656conquest.server.loadout;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class LoadoutServerModule implements ServerModule {
    @Override
    public void onPlayerLogout(ServerPlayer player) {
        PlayerItemNameUploadService.clearPlayerCache(player);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        PlayerItemNameUploadService.clearPlayerCache(player);
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        PlayerItemNameUploadService.clearPlayerCache(player);
    }
}
