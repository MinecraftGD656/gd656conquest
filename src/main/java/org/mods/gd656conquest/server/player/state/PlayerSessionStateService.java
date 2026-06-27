package org.mods.gd656conquest.server.player.state;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.mods.gd656conquest.common.match.MatchResultType;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomHudSyncPacket;
import org.mods.gd656conquest.network.packet.RoomMatchResultPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessInventoryPolicyService;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.RoomResultRuntimeService;

import java.util.Locale;

public final class PlayerSessionStateService {
    private static final int LOBBY_SATURATION_DURATION_TICKS = 220;

    private PlayerSessionStateService() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        refillLobbyHealthIfNeeded(player);
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        roomManager.tryRestoreInventorySnapshot(player);
        sanitizeLoadoutSelection(player);
        if (roomManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            roomManager.resyncPlayerPresentation(player, player.server.getTickCount());
            return;
        }
        clearStandaloneSessionArtifacts(player);
        cleanupStandaloneConquestScoreboardTeam(player);
        QuickAccessInventoryPolicyService.refreshForCurrentContext(player);
    }

    public static void onStandaloneTransition(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        sanitizeLoadoutSelection(player);
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        if (roomManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            roomManager.resyncPlayerPresentation(player, player.server.getTickCount());
            return;
        }
        refillLobbyHealthIfNeeded(player);
        clearStandaloneSessionArtifacts(player);
        cleanupStandaloneConquestScoreboardTeam(player);
        QuickAccessInventoryPolicyService.refreshForCurrentContext(player);
    }

    public static void maintainLobbyHealth(ServerPlayer player) {
        refillLobbyHealthIfNeeded(player);
    }

    private static void sanitizeLoadoutSelection(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        CustomLoadoutService.sanitizePlayerSelection(
            player,
            ConquestDataManager.of(player.server).equipmentData().getData(),
            null,
            false
        );
    }

    private static void clearStandaloneSessionArtifacts(ServerPlayer player) {
        if (player == null) {
            return;
        }
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setMatchEndViewLock(false));
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(
            false,
            MatchResultType.DRAW,
            RoomResultRuntimeService.MATCH_RESULT_TOTAL_TICKS,
            RoomResultRuntimeService.MATCH_RESULT_BLACK_START_TICKS,
            RoomResultRuntimeService.MATCH_RESULT_RESTORE_TICKS
        ));
        NetworkManager.sendToPlayer(player, new RoomHudSyncPacket(
            "-",
            "未加入房间",
            "-",
            "-",
            "-",
            0,
            0,
            0,
            "SCORE_TARGET",
            0,
            0,
            0,
            "-",
            "-",
            "-",
            "-"
        ));
    }

    private static void cleanupStandaloneConquestScoreboardTeam(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        Scoreboard scoreboard = player.server.getScoreboard();
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (currentTeam == null || currentTeam.getName() == null) {
            return;
        }
        String normalized = currentTeam.getName().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("_camp_a") || normalized.endsWith("_camp_b")) {
            scoreboard.removePlayerFromTeam(player.getScoreboardName());
        }
    }

    private static void refillLobbyHealthIfNeeded(ServerPlayer player) {
        if (!isStandaloneHubPlayer(player)) {
            return;
        }
        float maxHealth = player.getMaxHealth();
        if (maxHealth > 0.0F && player.getHealth() < maxHealth) {
            player.setHealth(maxHealth);
        }
        if (player.isAlive() && !player.isSpectator()) {
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, LOBBY_SATURATION_DURATION_TICKS, 0, true, false, false));
        }
    }

    private static boolean isStandaloneHubPlayer(ServerPlayer player) {
        return player != null
            && player.server != null
            && HubDimensionService.isHubDimension(player.level())
            && RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).isEmpty();
    }
}
