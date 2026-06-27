package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

public final class RoomTeamPresentationSupport {
    private RoomTeamPresentationSupport() {
    }

    public static RoomTeamPresentationProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomTeamPresentationProvider.class::isInstance)
            .map(RoomTeamPresentationProvider.class::cast)
            .orElse(null);
    }

    public static String resolveTeamDisplayName(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        RoomTeamPresentationProvider provider = resolve(state);
        if (provider != null) {
            return provider.teamDisplayName(room, teamId);
        }
        return resolveConfiguredDisplayName(room, teamId);
    }

    public static String resolveConfiguredDisplayName(RoomDefinition room, RoomTeamId teamId) {
        if (room != null) {
            String configured = room.gameplayTeamDisplayName(teamId);
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
        }
        return defaultTeamDisplayName(teamId);
    }

    public static String defaultTeamDisplayName(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "CampA";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "CampB";
        }
        return "平局";
    }
}
