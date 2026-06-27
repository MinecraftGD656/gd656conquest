package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RoomSpawnCandidateSelectionService {
    public static final double SPAWN_ENEMY_AVOID_RADIUS = 8.0D;
    public static final double SPAWN_ENEMY_AVOID_RADIUS_SQ = SPAWN_ENEMY_AVOID_RADIUS * SPAWN_ENEMY_AVOID_RADIUS;

    private final MinecraftServer server;

    public RoomSpawnCandidateSelectionService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public RoomPosition pickSpawn(RoomSpawnGroupConfig group, RunningRuntimeState state, RoomTeamId selfTeam) {
        return pickSpawn(collectCandidates(group), state, selfTeam);
    }

    public RoomPosition pickSpawn(List<RoomPosition> candidates, RunningRuntimeState state, RoomTeamId selfTeam) {
        List<SpawnCandidateScore> scored = scoreCandidates(candidates, state, selfTeam);
        if (scored.isEmpty()) {
            return null;
        }
        List<SpawnCandidateScore> safeCandidates = scored.stream()
            .filter(candidate -> candidate.nearestEnemyDistanceSq() > SPAWN_ENEMY_AVOID_RADIUS_SQ)
            .toList();
        List<SpawnCandidateScore> pool = safeCandidates.isEmpty()
            ? selectFarthestCandidates(scored)
            : safeCandidates;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size())).position();
    }

    public boolean hasThreatEnemyWithinRadius(RunningRuntimeState state, RoomTeamId selfTeam, RoomPosition position, double radiusSq) {
        if (position == null || radiusSq <= 0.0D) {
            return false;
        }
        return nearestThreatEnemyDistanceSq(state, selfTeam, position) <= radiusSq;
    }

    private List<RoomPosition> collectCandidates(RoomSpawnGroupConfig group) {
        if (group == null || !group.enabled() || group.spawns().isEmpty()) {
            return List.of();
        }
        return group.spawns().stream()
            .filter(Objects::nonNull)
            .toList();
    }

    private List<SpawnCandidateScore> scoreCandidates(List<RoomPosition> candidates, RunningRuntimeState state, RoomTeamId selfTeam) {
        List<SpawnCandidateScore> scored = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return scored;
        }
        for (RoomPosition candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            scored.add(new SpawnCandidateScore(candidate, nearestThreatEnemyDistanceSq(state, selfTeam, candidate)));
        }
        return scored;
    }

    private List<SpawnCandidateScore> selectFarthestCandidates(List<SpawnCandidateScore> scored) {
        double farthestDistanceSq = Double.NEGATIVE_INFINITY;
        for (SpawnCandidateScore candidate : scored) {
            farthestDistanceSq = Math.max(farthestDistanceSq, candidate.nearestEnemyDistanceSq());
        }
        List<SpawnCandidateScore> farthestCandidates = new ArrayList<>();
        for (SpawnCandidateScore candidate : scored) {
            if (Double.compare(candidate.nearestEnemyDistanceSq(), farthestDistanceSq) == 0) {
                farthestCandidates.add(candidate);
            }
        }
        return farthestCandidates.isEmpty() ? scored : farthestCandidates;
    }

    private double nearestThreatEnemyDistanceSq(RunningRuntimeState state, RoomTeamId selfTeam, RoomPosition position) {
        if (state == null || selfTeam == null || position == null) {
            return Double.POSITIVE_INFINITY;
        }
        double spawnX = position.x() + 0.5D;
        double spawnY = position.y();
        double spawnZ = position.z() + 0.5D;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        for (UUID playerId : state.getRoomPlayers()) {
            if (playerId == null) {
                continue;
            }
            RoomTeamId playerTeam = state.getRoomTeams().get(playerId);
            if (playerTeam == null || playerTeam == selfTeam || state.isInRespawnFlow(playerId)) {
                continue;
            }
            ServerPlayer enemy = server.getPlayerList().getPlayer(playerId);
            if (enemy == null || !enemy.isAlive() || enemy.isSpectator()) {
                continue;
            }
            double dx = enemy.getX() - spawnX;
            double dy = enemy.getY() - spawnY;
            double dz = enemy.getZ() - spawnZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
            }
        }
        return nearestDistanceSq;
    }

    private record SpawnCandidateScore(RoomPosition position, double nearestEnemyDistanceSq) {
    }
}
