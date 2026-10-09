package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.UUID;

public record RoomMatchEndRequest(RoomTeamId winnerTeam, UUID winnerPlayerId, String reason) {
    public RoomMatchEndRequest(RoomTeamId winnerTeam, String reason) {
        this(winnerTeam, null, reason);
    }

    public RoomMatchEndRequest(UUID winnerPlayerId, String reason) {
        this(null, winnerPlayerId, reason);
    }

    public RoomMatchEndRequest {
        reason = reason == null ? "-" : reason.trim();
        if (reason.isEmpty()) {
            reason = "-";
        }
    }
}
