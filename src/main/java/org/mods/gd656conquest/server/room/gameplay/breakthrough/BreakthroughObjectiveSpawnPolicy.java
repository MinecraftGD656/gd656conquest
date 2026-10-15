package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSpawnPolicy;

import java.util.List;

public final class BreakthroughObjectiveSpawnPolicy implements RoomObjectiveSpawnPolicy {
    private static final double OBJECTIVE_THREAT_RADIUS_SQ = 16.0D * 16.0D;

    @Override
    public boolean canDeploy(
        RoomObjectiveRuntimeView runtimeView,
        RoomCapturePointDefinition point,
        RoomTeamId teamId,
        String objectiveCode
    ) {
        if (runtimeView == null || point == null || teamId == null) {
            return false;
        }
        int currentStage = resolveCurrentStage(runtimeView, teamId);
        if (currentStage <= 0) {
            return false;
        }
        int pointStage = point.stageIndex();
        if (teamId == RoomTeamId.CAMP_A) {
            return pointStage == currentStage || pointStage == currentStage - 1;
        }
        return pointStage == currentStage || pointStage == currentStage + 1;
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
        int currentStage = resolveCurrentStage(runtimeView, teamId);
        int pointStage = point.stageIndex();
        if (teamId == RoomTeamId.CAMP_A && pointStage == currentStage - 1) {
            return point.innerSpawns();
        }
        if (teamId == RoomTeamId.CAMP_B && pointStage == currentStage + 1) {
            return point.innerSpawns();
        }
        boolean fullyControlled = runtimeView != null && runtimeView.isFullyControlledBy(objectiveCode, teamId);
        List<RoomPosition> innerSpawns = point.innerSpawns();
        if (fullyControlled && !enemyNearby && innerSpawns != null && !innerSpawns.isEmpty()) {
            return innerSpawns;
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

    private int resolveCurrentStage(RoomObjectiveRuntimeView runtimeView, RoomTeamId teamId) {
        if (!(runtimeView instanceof BreakthroughObjectiveRuntimeView breakthroughRuntimeView)) {
            return 0;
        }
        int currentStage = breakthroughRuntimeView.deploymentStageIndex(teamId);
        if (currentStage <= 0 || currentStage > breakthroughRuntimeView.maxStageCount()) {
            return 0;
        }
        return currentStage;
    }
}
