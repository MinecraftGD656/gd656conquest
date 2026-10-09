package org.mods.gd656conquest.server.room.objective;

import org.mods.gd656conquest.server.room.model.RoomTeamId;

public interface RoomObjectiveRuntimeView {
    RoomTeamId ownerOf(String objectiveCode);

    RoomTeamId drivingTeamOf(String objectiveCode);

    double progressOf(String objectiveCode);

    int teamCount(String objectiveCode, RoomTeamId teamId);

    boolean isFullyControlledBy(String objectiveCode, RoomTeamId teamId);
}
