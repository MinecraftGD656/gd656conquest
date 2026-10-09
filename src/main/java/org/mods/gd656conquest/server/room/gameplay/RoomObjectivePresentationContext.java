package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;

import java.util.List;

public record RoomObjectivePresentationContext(
    List<RoomCapturePointDefinition> activePoints,
    RoomObjectiveRuntimeView runtimeView,
    String focusCode,
    RoomTeamId selfTeam
) {
}
