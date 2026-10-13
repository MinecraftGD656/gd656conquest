package org.mods.gd656conquest.server.room.runtime.audio;

import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomVoicePack;

public final class RoomTeamVoicePackResolver {
    private RoomTeamVoicePackResolver() {
    }

    public static RoomVoicePack resolve(RoomDefinition room, RoomTeamId teamId) {
        if (room == null) {
            return RoomVoicePack.USA;
        }
        if (teamId == RoomTeamId.CAMP_A) {
            return RoomVoicePack.resolveOrDefault(room.commonConfig().campAVoicePack());
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return RoomVoicePack.resolveOrDefault(room.commonConfig().campBVoicePack());
        }
        return RoomVoicePack.USA;
    }

    public static String resolveDownedHelpSoundId(RoomDefinition room, RoomTeamId teamId) {
        return resolve(room, teamId).downedHelpSoundId();
    }
}
