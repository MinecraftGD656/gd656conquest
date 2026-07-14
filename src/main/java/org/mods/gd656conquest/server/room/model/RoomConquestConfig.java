package org.mods.gd656conquest.server.room.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public record RoomConquestConfig(
    RoomEquipmentConfig equipmentConfig,
    Map<RoomTeamId, RoomSpawnGroupConfig> teamSpawnGroups,
    Map<RoomTeamId, RoomPosition> teamDeployIcons,
    RoomViewPosition deployViewPosition,
    List<RoomPosition> sharedBoundary,
    List<RoomPosition> campABoundary,
    List<RoomPosition> campBBoundary,
    RoomCaptureConfig captureConfig
) implements IRoomGameplayConfig {
    public RoomConquestConfig {
        equipmentConfig = equipmentConfig == null ? RoomEquipmentConfig.defaults() : equipmentConfig;
        captureConfig = captureConfig == null ? RoomCaptureConfig.defaults() : captureConfig;
        teamSpawnGroups = normalizeSpawnGroups(teamSpawnGroups);
        teamDeployIcons = normalizeDeployIcons(teamDeployIcons);
        sharedBoundary = copyBoundary(sharedBoundary);
        campABoundary = copyBoundary(campABoundary);
        campBBoundary = copyBoundary(campBBoundary);
    }

    public static RoomConquestConfig defaults() {
        return new RoomConquestConfig(
            RoomEquipmentConfig.defaults(),
            Map.of(),
            Map.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            RoomCaptureConfig.defaults()
        );
    }

    @Override
    public String mapId() {
        return captureConfig.mapId();
    }

    @Override
    public String campADisplayName(RoomCommonConfig commonConfig) {
        return commonConfig.campADisplayName();
    }

    @Override
    public String campBDisplayName(RoomCommonConfig commonConfig) {
        return commonConfig.campBDisplayName();
    }

    @Override
    public List<RoomCapturePointDefinition> definedObjectivePoints() {
        return new java.util.ArrayList<>(captureConfig.points().values()).stream()
            .filter(Objects::nonNull)
            .toList();
    }

    public Optional<RoomPosition> findTeamSpawn(RoomTeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        RoomSpawnGroupConfig group = teamSpawnGroups.get(teamId);
        if (group != null) {
            for (RoomPosition position : group.spawns()) {
                if (position != null) {
                    return Optional.of(position);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<RoomPosition> pickRandomTeamSpawn(RoomTeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        RoomSpawnGroupConfig group = teamSpawnGroups.get(teamId);
        if (group != null && group.enabled() && !group.spawns().isEmpty()) {
            List<RoomPosition> candidates = group.spawns().stream()
                .filter(Objects::nonNull)
                .toList();
            if (!candidates.isEmpty()) {
                return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
            }
        }
        return findTeamSpawn(teamId);
    }

    public Optional<RoomPosition> findTeamDeployIcon(RoomTeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(teamDeployIcons.get(teamId));
    }

    public List<RoomPosition> boundaryForTeam(RoomTeamId teamId) {
        if (hasDedicatedTeamBoundariesConfigured() && teamId == RoomTeamId.CAMP_A) {
            return campABoundary;
        }
        if (hasDedicatedTeamBoundariesConfigured() && teamId == RoomTeamId.CAMP_B) {
            return campBBoundary;
        }
        return sharedBoundary;
    }

    public List<List<RoomPosition>> allowedBoundariesForTeam(RoomTeamId teamId) {
        if (hasDedicatedTeamBoundariesConfigured()) {
            if (teamId == RoomTeamId.CAMP_A) {
                return List.of(campABoundary);
            }
            if (teamId == RoomTeamId.CAMP_B) {
                return List.of(campBBoundary);
            }
            return List.of(campABoundary, campBBoundary);
        }
        return hasSharedBoundaryConfigured() ? List.of(sharedBoundary) : List.of();
    }

    public boolean hasSharedBoundaryConfigured() {
        return sharedBoundary.size() >= 3;
    }

    public boolean hasDedicatedTeamBoundariesConfigured() {
        return campABoundary.size() >= 3 && campBBoundary.size() >= 3;
    }

    public boolean hasAnyBoundaryConfigured() {
        return hasSharedBoundaryConfigured() || hasDedicatedTeamBoundariesConfigured();
    }

    public RoomConquestConfig withCaptureConfig(RoomCaptureConfig value) {
        return new RoomConquestConfig(
            equipmentConfig,
            teamSpawnGroups,
            teamDeployIcons,
            deployViewPosition,
            sharedBoundary,
            campABoundary,
            campBBoundary,
            value
        );
    }

    private static Map<RoomTeamId, RoomSpawnGroupConfig> normalizeSpawnGroups(Map<RoomTeamId, RoomSpawnGroupConfig> values) {
        Map<RoomTeamId, RoomSpawnGroupConfig> normalized = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<RoomTeamId, RoomSpawnGroupConfig> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Map.copyOf(normalized);
    }

    private static Map<RoomTeamId, RoomPosition> normalizeDeployIcons(Map<RoomTeamId, RoomPosition> values) {
        Map<RoomTeamId, RoomPosition> normalized = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<RoomTeamId, RoomPosition> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Map.copyOf(normalized);
    }

    private static List<RoomPosition> copyBoundary(List<RoomPosition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
