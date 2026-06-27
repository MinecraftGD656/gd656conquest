package org.mods.gd656conquest.server.room.core;

import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

public record RoomGroupPlacementPlan(
    RoomLifecycleState lifecycleState,
    RoomTeamId teamId,
    String squadLabel
) {
}
