package org.mods.gd656conquest.server.player.state;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class SleepRuleServerModule implements ServerModule {
    private static final int SLEEPING_PERCENTAGE = 33;
    private static final long REAPPLY_INTERVAL_TICKS = 200L;

    @Override
    public void onServerStarting(MinecraftServer server) {
        applySleepRule(server);
    }

    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        if (server == null || gameTick % REAPPLY_INTERVAL_TICKS != 0L) {
            return;
        }
        applySleepRule(server);
    }

    private void applySleepRule(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(SLEEPING_PERCENTAGE, server);
        }
    }
}
