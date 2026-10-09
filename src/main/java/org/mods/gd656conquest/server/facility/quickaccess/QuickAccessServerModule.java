package org.mods.gd656conquest.server.facility.quickaccess;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class QuickAccessServerModule implements ServerModule {
    @Override
    public void onPlayerTick(ServerPlayer player) {
        QuickAccessItemService.onPlayerTick(player);
    }
}
