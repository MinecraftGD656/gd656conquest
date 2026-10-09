package org.mods.gd656conquest.server.title;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class TitleServerModule implements ServerModule {
    @Override
    public void onServerStarting(MinecraftServer server) {
        if (server == null) {
            return;
        }
        PlayerTitleService.of(server);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        PlayerTitleService.forget(server);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        PlayerTitleService service = PlayerTitleService.of(player.server);
        service.initializePlayer(player);
        service.deliverPendingNotices(player);
    }
}
