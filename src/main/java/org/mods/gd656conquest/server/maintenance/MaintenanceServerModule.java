package org.mods.gd656conquest.server.maintenance;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import org.mods.gd656conquest.server.data.ConquestDataManager;

public final class MaintenanceServerModule implements ServerModule {

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        if (!ConquestDataManager.of(player.server).serverConfig().isMaintenanceModeEnabled()) {
            return;
        }
        if (player.hasPermissions(2)) {
            return;
        }
        player.connection.disconnect(Component.literal("服务器目前处于维护状态，请持续关注群内消息直到维护完成"));
    }
}
