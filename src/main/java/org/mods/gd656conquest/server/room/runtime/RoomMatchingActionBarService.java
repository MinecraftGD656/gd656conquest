package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

public final class RoomMatchingActionBarService {
    public static final String ACTIONBAR_SOURCE_QUEUE_STATUS = "room_queue_status";
    public static final String ACTIONBAR_SOURCE_QUEUE_WAIT = "room_queue_wait";

    private RoomMatchingActionBarService() {
    }

    public static void showQueueStatus(ServerPlayer player, RoomMatchingConfig config, MatchingRuntimeState state) {
        if (player == null || config == null || state == null) {
            return;
        }

        int queuedPlayers = state.getQueuePlayers().size();
        if (queuedPlayers < config.minStartPlayers()) {
            int missingPlayers = config.minStartPlayers() - queuedPlayers;
            ServerActionBarService.showText(
                player,
                ACTIONBAR_SOURCE_QUEUE_STATUS,
                "§7还需玩家 §b" + missingPlayers
            );
            return;
        }

        RoomTeamId missingTeam = findMissingTeam(config, state);
        if (missingTeam != null) {
            ServerActionBarService.showText(
                player,
                ACTIONBAR_SOURCE_QUEUE_STATUS,
                "§7还需等待 §b" + toDisplayName(missingTeam) + "§7阵营"
            );
            return;
        }

        ServerActionBarService.showText(
            player,
            ACTIONBAR_SOURCE_QUEUE_STATUS,
            "§7CampA §b" + countPlayers(state, RoomTeamId.CAMP_A) + "§7/§8" + config.campAMaxPlayers()
                + "  §7CampB §b" + countPlayers(state, RoomTeamId.CAMP_B) + "§7/§8" + config.campBMaxPlayers()
        );
    }

    public static void showWaitingStatus(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerActionBarService.showText(player, ACTIONBAR_SOURCE_QUEUE_WAIT, "§7等待玩家");
    }

    public static void showCountdownStatus(ServerPlayer player, int remainSeconds) {
        if (player == null) {
            return;
        }
        ServerActionBarService.showText(
            player,
            ACTIONBAR_SOURCE_QUEUE_WAIT,
            "§7等待对局开始 " + resolveCountdownColor(remainSeconds) + remainSeconds + "秒"
        );
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_QUEUE_STATUS);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_QUEUE_WAIT);
    }

    private static RoomTeamId findMissingTeam(RoomMatchingConfig config, MatchingRuntimeState state) {
        for (RoomTeamId teamId : config.enabledTeams()) {
            if (!state.getQueueTeams().containsValue(teamId)) {
                return teamId;
            }
        }
        return null;
    }

    private static String toDisplayName(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "CampA";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "CampB";
        }
        return "未知";
    }

    private static String resolveCountdownColor(int remainSeconds) {
        if (remainSeconds <= 1) {
            return "§c";
        }
        if (remainSeconds <= 2) {
            return "§6";
        }
        if (remainSeconds <= 3) {
            return "§e";
        }
        if (remainSeconds <= 5) {
            return "§a";
        }
        return "§b";
    }

    private static int countPlayers(MatchingRuntimeState state, RoomTeamId teamId) {
        int count = 0;
        for (RoomTeamId assigned : state.getQueueTeams().values()) {
            if (assigned == teamId) {
                count++;
            }
        }
        return count;
    }
}
