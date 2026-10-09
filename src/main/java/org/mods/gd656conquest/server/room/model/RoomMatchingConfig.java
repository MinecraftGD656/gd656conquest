package org.mods.gd656conquest.server.room.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record RoomMatchingConfig(
    int minStartPlayers,
    int campAMaxPlayers,
    int campBMaxPlayers,
    int waitAfterMinSeconds,
    int waitAfterMaxSeconds,
    Set<RoomTeamId> enabledTeams
) {
    public RoomMatchingConfig {
        minStartPlayers = Math.max(1, minStartPlayers);
        campAMaxPlayers = Math.max(0, campAMaxPlayers);
        campBMaxPlayers = Math.max(0, campBMaxPlayers);
        waitAfterMinSeconds = Math.max(0, waitAfterMinSeconds);
        waitAfterMaxSeconds = Math.max(0, waitAfterMaxSeconds);
        Objects.requireNonNull(enabledTeams, "enabledTeams");
        enabledTeams = Set.copyOf(enabledTeams);
        if (enabledTeams.isEmpty()) {
            enabledTeams = Set.of(RoomTeamId.CAMP_A, RoomTeamId.CAMP_B);
        }
        if (enabledTeams.contains(RoomTeamId.CAMP_A)) {
            campAMaxPlayers = Math.max(1, campAMaxPlayers);
        }
        if (enabledTeams.contains(RoomTeamId.CAMP_B)) {
            campBMaxPlayers = Math.max(1, campBMaxPlayers);
        }
        int totalMaxPlayers = campAMaxPlayers + campBMaxPlayers;
        if (totalMaxPlayers <= 0) {
            if (enabledTeams.contains(RoomTeamId.CAMP_A)) {
                campAMaxPlayers = 1;
            }
            if (enabledTeams.contains(RoomTeamId.CAMP_B)) {
                campBMaxPlayers = 1;
            }
            totalMaxPlayers = campAMaxPlayers + campBMaxPlayers;
        }
        if (totalMaxPlayers < minStartPlayers) {
            int shortage = minStartPlayers - totalMaxPlayers;
            if (enabledTeams.contains(RoomTeamId.CAMP_A) && enabledTeams.contains(RoomTeamId.CAMP_B)) {
                campAMaxPlayers += (shortage + 1) / 2;
                campBMaxPlayers += shortage / 2;
            } else if (enabledTeams.contains(RoomTeamId.CAMP_A)) {
                campAMaxPlayers += shortage;
            } else if (enabledTeams.contains(RoomTeamId.CAMP_B)) {
                campBMaxPlayers += shortage;
            }
        }
    }

    public static RoomMatchingConfig defaults() {
        return new RoomMatchingConfig(1, 1, 1, 0, 0, EnumSet.of(RoomTeamId.CAMP_A, RoomTeamId.CAMP_B));
    }

    public int requiredReadyTeams() {
        return enabledTeams.size();
    }

    public int totalMaxPlayers() {
        return campAMaxPlayers + campBMaxPlayers;
    }

    public int maxPlayersForTeam(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return campAMaxPlayers;
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return campBMaxPlayers;
        }
        return 0;
    }
}
