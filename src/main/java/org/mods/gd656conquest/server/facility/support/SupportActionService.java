package org.mods.gd656conquest.server.facility.support;

import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class SupportActionService {
    private SupportActionService() {
    }

    public static void handleAction(ServerPlayer player, String action, String targetId) {
        if (player == null || player.server == null) {
            return;
        }
        String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        switch (normalizedAction) {
            case "deploy_hero_kit" -> HeroKitSupportService.handleDeployRequest(player, targetId);
            case "start_shelling" -> ShellingSupportService.handleStartRequest(player, targetId);
            default -> SupportUiService.sendSnapshot(player);
        }
    }
}
