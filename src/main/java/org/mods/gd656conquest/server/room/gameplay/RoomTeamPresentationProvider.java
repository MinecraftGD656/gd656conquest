package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

public interface RoomTeamPresentationProvider {
    default String teamDisplayName(RoomDefinition room, RoomTeamId teamId) {
        return RoomTeamPresentationSupport.resolveConfiguredDisplayName(room, teamId);
    }
}
