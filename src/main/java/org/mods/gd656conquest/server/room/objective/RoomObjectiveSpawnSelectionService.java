package org.mods.gd656conquest.server.room.objective;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveDefinitionProvider;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;

import java.util.List;
import java.util.Objects;

public final class RoomObjectiveSpawnSelectionService {
    private final RoomSpawnCandidateSelectionService spawnCandidateSelectionService;

    public RoomObjectiveSpawnSelectionService(MinecraftServer server) {
        this.spawnCandidateSelectionService = new RoomSpawnCandidateSelectionService(Objects.requireNonNull(server, "server"));
    }

    public RoomPosition resolveObjectiveSpawn(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        String objectiveCode
    ) {
        return assessObjectiveSpawn(room, state, player, teamId, objectiveCode).spawn();
    }

    public ObjectiveSpawnAssessment assessObjectiveSpawn(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        String objectiveCode
    ) {
        if (room == null || state == null || player == null || teamId == null) {
            return ObjectiveSpawnAssessment.failure("部署据点无效");
        }
        RoomGameplayObjectiveDefinitionProvider definitionProvider = state.getGameplayRuntime()
            .filter(RoomGameplayObjectiveDefinitionProvider.class::isInstance)
            .map(RoomGameplayObjectiveDefinitionProvider.class::cast)
            .orElse(null);
        RoomCapturePointDefinition point = definitionProvider == null
            ? room.findConfiguredObjectivePoint(objectiveCode).orElse(null)
            : definitionProvider.findObjectivePoint(room, objectiveCode);
        if (point == null) {
            return ObjectiveSpawnAssessment.failure("部署据点无效");
        }
        RoomObjectiveRuntimeProvider runtimeProvider = state.getGameplayRuntime()
            .filter(RoomObjectiveRuntimeProvider.class::isInstance)
            .map(RoomObjectiveRuntimeProvider.class::cast)
            .orElse(null);
        if (runtimeProvider == null) {
            return ObjectiveSpawnAssessment.failure("当前模式未启用据点部署");
        }
        RoomObjectiveRuntimeView runtimeView = runtimeProvider.objectiveRuntimeView();
        RoomObjectiveSpawnPolicy spawnPolicy = runtimeProvider.objectiveSpawnPolicy();
        if (runtimeView == null || spawnPolicy == null) {
            return ObjectiveSpawnAssessment.failure("当前模式未启用据点部署");
        }
        String normalizedCode = RoomObjectiveSupport.normalizeCode(objectiveCode);
        if (!spawnPolicy.canDeploy(runtimeView, point, teamId, normalizedCode)) {
            return ObjectiveSpawnAssessment.failure("该据点当前不属于我方");
        }
        boolean enemyNearby = hasEnemyNearby(state, player, teamId, point, spawnPolicy.objectiveThreatRadiusSq(point, teamId, normalizedCode));
        List<RoomPosition> candidates = spawnPolicy.resolveCandidateSpawns(runtimeView, point, teamId, normalizedCode, enemyNearby);
        if (candidates == null || candidates.isEmpty()) {
            return ObjectiveSpawnAssessment.failure(describeMissingSpawnFailure(point, teamId, enemyNearby));
        }
        RoomPosition spawn = spawnCandidateSelectionService.pickSpawn(candidates, state, teamId);
        if (spawn == null) {
            return ObjectiveSpawnAssessment.failure(enemyNearby
                ? "该据点附近有敌军，当前没有可用的外围出生点"
                : "该据点当前没有可用出生点");
        }
        return ObjectiveSpawnAssessment.success(spawn);
    }

    private boolean hasEnemyNearby(
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId selfTeam,
        RoomCapturePointDefinition point,
        double radiusSq
    ) {
        if (point.centerPosition() == null || radiusSq <= 0.0D) {
            return false;
        }
        return spawnCandidateSelectionService.hasThreatEnemyWithinRadius(state, selfTeam, point.centerPosition(), radiusSq);
    }

    private String describeMissingSpawnFailure(RoomCapturePointDefinition point, RoomTeamId teamId, boolean enemyNearby) {
        if (point == null) {
            return "部署据点无效";
        }
        if (!enemyNearby) {
            return point.innerSpawns().isEmpty()
                ? "该据点未配置可用出生点"
                : "该据点当前没有可用出生点";
        }
        boolean hasTeamOuter = !point.outerSpawns(teamId).isEmpty();
        boolean hasOuter = !point.outerSpawns().isEmpty();
        if (!hasTeamOuter && !hasOuter) {
            return "该据点附近有敌军，且未配置外围出生点";
        }
        return "该据点附近有敌军，当前没有可用的外围出生点";
    }

    public record ObjectiveSpawnAssessment(RoomPosition spawn, String failureMessage) {
        public static ObjectiveSpawnAssessment success(RoomPosition spawn) {
            return new ObjectiveSpawnAssessment(spawn, null);
        }

        public static ObjectiveSpawnAssessment failure(String failureMessage) {
            return new ObjectiveSpawnAssessment(null, failureMessage == null ? "" : failureMessage.trim());
        }

        public boolean success() {
            return spawn != null;
        }
    }
}
