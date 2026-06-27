package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

public record RoomDeployHudIcon(
    String code,
    RoomPosition position,
    RoomTeamId ownerTeam,
    String note,
    boolean stageObjectiveProxy
) {
    public RoomDeployHudIcon(String code, RoomPosition position, RoomTeamId ownerTeam, String note) {
        this(code, position, ownerTeam, note, false);
    }
}
