package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomTeamId;

public record RoomMatchEndRequest(RoomTeamId winnerTeam, String reason) {
    public RoomMatchEndRequest {
        reason = reason == null ? "-" : reason.trim();
        if (reason.isEmpty()) {
            reason = "-";
        }
    }
}
