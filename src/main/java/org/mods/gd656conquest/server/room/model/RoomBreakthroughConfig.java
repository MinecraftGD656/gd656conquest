package org.mods.gd656conquest.server.room.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public record RoomBreakthroughConfig(
    Map<String, RoomCapturePointDefinition> points,
    String mapId,
    String campADisplayName,
    String campBDisplayName,
    int stageCount,
    List<RoomPosition> campABaseBoundary,
    List<RoomPosition> campBBaseBoundary,
    List<RoomPosition> stageABoundary,
    List<RoomPosition> stageBBoundary,
    List<RoomPosition> stageCBoundary,
    List<RoomPosition> stageDBoundary,
    List<RoomPosition> stageEBoundary,
    List<RoomPosition> stageFBoundary,
    RoomSpawnGroupConfig campABaseSpawnGroup,
    RoomSpawnGroupConfig campBBaseSpawnGroup,
    RoomPosition campABaseDeployIconPosition,
    RoomPosition campBBaseDeployIconPosition,
    RoomViewPosition stageADeployViewPosition,
    RoomViewPosition stageBDeployViewPosition,
    RoomViewPosition stageCDeployViewPosition,
    RoomViewPosition stageDDeployViewPosition,
    RoomViewPosition stageEDeployViewPosition,
    RoomViewPosition stageFDeployViewPosition,
    int stageDurationMinutes,
    int defenderRetreatSeconds,
    int troopGainPerDefenderKill,
    int maxAttackTroopsAt12Players
) {
    public RoomBreakthroughConfig {
        Objects.requireNonNull(points, "points");
        points = Map.copyOf(new LinkedHashMap<>(points));
        mapId = normalizeMapId(mapId);
        campADisplayName = normalizeDisplayName(campADisplayName, "CampA");
        campBDisplayName = normalizeDisplayName(campBDisplayName, "CampB");
        stageCount = Math.max(0, Math.min(6, stageCount));
        campABaseBoundary = copyPositions(campABaseBoundary);
        campBBaseBoundary = copyPositions(campBBaseBoundary);
        stageABoundary = copyPositions(stageABoundary);
        stageBBoundary = copyPositions(stageBBoundary);
        stageCBoundary = copyPositions(stageCBoundary);
        stageDBoundary = copyPositions(stageDBoundary);
        stageEBoundary = copyPositions(stageEBoundary);
        stageFBoundary = copyPositions(stageFBoundary);
        campABaseSpawnGroup = campABaseSpawnGroup == null ? RoomSpawnGroupConfig.defaults() : campABaseSpawnGroup;
        campBBaseSpawnGroup = campBBaseSpawnGroup == null ? RoomSpawnGroupConfig.defaults() : campBBaseSpawnGroup;
        campABaseDeployIconPosition = copyPosition(campABaseDeployIconPosition);
        campBBaseDeployIconPosition = copyPosition(campBBaseDeployIconPosition);
        stageDurationMinutes = Math.max(0, stageDurationMinutes);
        defenderRetreatSeconds = Math.max(0, defenderRetreatSeconds);
        troopGainPerDefenderKill = Math.max(0, troopGainPerDefenderKill);
        maxAttackTroopsAt12Players = Math.max(0, maxAttackTroopsAt12Players);
    }

    public static RoomBreakthroughConfig defaults() {
        return new RoomBreakthroughConfig(
            Map.of(),
            "",
            "CampA",
            "CampB",
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            0,
            0
        );
    }

    public Optional<RoomCapturePointDefinition> findPoint(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(points.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public List<RoomCapturePointDefinition> allActivePoints() {
        return points.values().stream()
            .filter(Objects::nonNull)
            .filter(RoomCapturePointDefinition::isRuntimeActive)
            .sorted(java.util.Comparator.comparing(RoomCapturePointDefinition::code, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public List<RoomCapturePointDefinition> stageActivePoints(int stageIndex) {
        if (stageIndex <= 0) {
            return List.of();
        }
        return points.values().stream()
            .filter(Objects::nonNull)
            .filter(RoomCapturePointDefinition::isRuntimeActive)
            .filter(point -> point.stageIndex() == stageIndex)
            .sorted(java.util.Comparator.comparing(RoomCapturePointDefinition::code, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public boolean hasStageActivePoints(int stageIndex) {
        return !stageActivePoints(stageIndex).isEmpty();
    }

    public List<RoomPosition> stageBoundary(int stageIndex) {
        return switch (stageIndex) {
            case 1 -> stageABoundary;
            case 2 -> stageBBoundary;
            case 3 -> stageCBoundary;
            case 4 -> stageDBoundary;
            case 5 -> stageEBoundary;
            case 6 -> stageFBoundary;
            default -> List.of();
        };
    }

    public RoomViewPosition stageDeployViewPosition(int stageIndex) {
        return switch (stageIndex) {
            case 1 -> stageADeployViewPosition;
            case 2 -> stageBDeployViewPosition;
            case 3 -> stageCDeployViewPosition;
            case 4 -> stageDDeployViewPosition;
            case 5 -> stageEDeployViewPosition;
            case 6 -> stageFDeployViewPosition;
            default -> null;
        };
    }

    public RoomPosition baseDeployIconPosition(RoomTeamId teamId) {
        return switch (teamId) {
            case CAMP_A -> campABaseDeployIconPosition;
            case CAMP_B -> campBBaseDeployIconPosition;
        };
    }

    public Optional<RoomPosition> pickRandomBaseSpawn(RoomTeamId teamId) {
        RoomSpawnGroupConfig group = teamId == RoomTeamId.CAMP_A ? campABaseSpawnGroup : teamId == RoomTeamId.CAMP_B ? campBBaseSpawnGroup : null;
        if (group != null && group.enabled() && !group.spawns().isEmpty()) {
            List<RoomPosition> candidates = group.spawns().stream()
                .filter(Objects::nonNull)
                .toList();
            if (!candidates.isEmpty()) {
                return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
            }
        }
        return Optional.empty();
    }

    private static List<RoomPosition> copyPositions(List<RoomPosition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static RoomPosition copyPosition(RoomPosition value) {
        return value;
    }

    private static String normalizeDisplayName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeMapId(String mapId) {
        return mapId == null ? "" : mapId.trim();
    }
}
