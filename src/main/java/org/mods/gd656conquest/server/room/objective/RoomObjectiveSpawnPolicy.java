package org.mods.gd656conquest.server.room.objective;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;

public interface RoomObjectiveSpawnPolicy {
    boolean canDeploy(
        RoomObjectiveRuntimeView runtimeView,
        RoomCapturePointDefinition point,
        RoomTeamId teamId,
        String objectiveCode
    );

    List<RoomPosition> resolveCandidateSpawns(
        RoomObjectiveRuntimeView runtimeView,
        RoomCapturePointDefinition point,
        RoomTeamId teamId,
        String objectiveCode,
        boolean enemyNearby
    );

    double objectiveThreatRadiusSq(RoomCapturePointDefinition point, RoomTeamId teamId, String objectiveCode);
}
