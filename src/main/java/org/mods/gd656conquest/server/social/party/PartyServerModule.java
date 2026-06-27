package org.mods.gd656conquest.server.social.party;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class PartyServerModule implements ServerModule {
    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        PlayerPartyManager.of(server).onServerTick(gameTick);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        PlayerPartyManager.of(player.server).onPlayerLogout(player);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        PlayerPartyManager.of(player.server).onPlayerLogin(player);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        PlayerPartyManager.of(server).clearAll();
    }
}
