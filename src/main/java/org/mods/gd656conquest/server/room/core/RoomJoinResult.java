package org.mods.gd656conquest.server.room.core;

import org.mods.gd656conquest.server.room.model.RoomTeamId;

public record RoomJoinResult(
    Kind kind,
    String roomId,
    RoomTeamId teamId
) {
    public RoomJoinResult {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        roomId = roomId == null ? "" : roomId;
    }

    public boolean isRejoin() {
        return kind == Kind.REJOIN_RUNNING || kind == Kind.REJOIN_ENDING;
    }

    public enum Kind {
        MATCHING,
        MIDJOIN_RUNNING,
        MIDJOIN_ENDING,
        REJOIN_RUNNING,
        REJOIN_ENDING
    }
}
