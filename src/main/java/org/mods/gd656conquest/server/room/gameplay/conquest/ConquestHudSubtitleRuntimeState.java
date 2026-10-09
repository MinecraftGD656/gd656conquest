package org.mods.gd656conquest.server.room.gameplay.conquest;

import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveHudSubtitleRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.EnumMap;
import java.util.Map;

public final class ConquestHudSubtitleRuntimeState extends RoomObjectiveHudSubtitleRuntimeState {
    private final Map<RoomTeamId, Integer> lastScoreStageByTeam = new EnumMap<>(RoomTeamId.class);
    private final Map<RoomTeamId, Integer> lastTroopStageByTeam = new EnumMap<>(RoomTeamId.class);

    public Map<RoomTeamId, Integer> getLastScoreStageByTeam() {
        return lastScoreStageByTeam;
    }

    public Map<RoomTeamId, Integer> getLastTroopStageByTeam() {
        return lastTroopStageByTeam;
    }

    public void clear() {
        super.clear();
        lastScoreStageByTeam.clear();
        lastTroopStageByTeam.clear();
    }
}
