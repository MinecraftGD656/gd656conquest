package org.mods.gd656conquest.server.room.gameplay.conquest;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSpawnPolicy;

import java.util.List;

public final class ConquestObjectiveSpawnPolicy implements RoomObjectiveSpawnPolicy {
    private static final double OBJECTIVE_THREAT_RADIUS_SQ = 16.0D * 16.0D;

    @Override
    public boolean canDeploy(
        RoomObjectiveRuntimeView runtimeView,
        RoomCapturePointDefinition point,
        RoomTeamId teamId,
        String objectiveCode
    ) {
        return runtimeView != null && point != null && teamId != null && runtimeView.ownerOf(objectiveCode) == teamId;
    }

    @Override
    public List<RoomPosition> resolveCandidateSpawns(
        RoomObjectiveRuntimeView runtimeView,
        RoomCapturePointDefinition point,
        RoomTeamId teamId,
        String objectiveCode,
        boolean enemyNearby
    ) {
        if (point == null || teamId == null) {
            return List.of();
        }
        boolean fullyControlled = runtimeView != null && runtimeView.isFullyControlledBy(objectiveCode, teamId);
        if (fullyControlled && !enemyNearby) {
            return point.innerSpawns();
        }
        List<RoomPosition> teamOuterSpawns = point.outerSpawns(teamId);
        if (teamOuterSpawns != null && !teamOuterSpawns.isEmpty()) {
            return teamOuterSpawns;
        }
        return point.outerSpawns();
    }

    @Override
    public double objectiveThreatRadiusSq(RoomCapturePointDefinition point, RoomTeamId teamId, String objectiveCode) {
        return OBJECTIVE_THREAT_RADIUS_SQ;
    }
}
