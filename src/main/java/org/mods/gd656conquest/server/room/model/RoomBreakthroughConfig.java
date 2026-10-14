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
    Double troopsPerPlayer,
    Integer maxAttackTroops,
    Integer stageTroopReward,
    RoomTroopBalanceReference troopBalanceReference,
    List<RoomPosition> ammoBoxPositions,
    List<RoomPosition> medicalBoxPositions,
    RoomBattalionConfig battalionConfig,
    Boolean retreatSuccessRestore
) implements IRoomGameplayConfig {
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
        troopsPerPlayer = troopsPerPlayer == null ? 20.0D
            : Double.isFinite(troopsPerPlayer) ? Math.max(0.0D, troopsPerPlayer) : 0.0D;
        maxAttackTroops = maxAttackTroops == null ? 250 : Math.max(0, maxAttackTroops);
        stageTroopReward = stageTroopReward == null ? 50 : Math.max(0, stageTroopReward);
        troopBalanceReference = troopBalanceReference == null ? RoomTroopBalanceReference.defaultValue() : troopBalanceReference;
        ammoBoxPositions = copyPositions(ammoBoxPositions);
        medicalBoxPositions = copyPositions(medicalBoxPositions);
        battalionConfig = battalionConfig == null ? RoomBattalionConfig.defaults() : battalionConfig;
        retreatSuccessRestore = retreatSuccessRestore == null || retreatSuccessRestore;
    }

    public RoomBreakthroughConfig(
        Map<String, RoomCapturePointDefinition> points, String mapId, String campADisplayName, String campBDisplayName,
        int stageCount, List<RoomPosition> campABaseBoundary, List<RoomPosition> campBBaseBoundary,
        List<RoomPosition> stageABoundary, List<RoomPosition> stageBBoundary, List<RoomPosition> stageCBoundary,
        List<RoomPosition> stageDBoundary, List<RoomPosition> stageEBoundary, List<RoomPosition> stageFBoundary,
        RoomSpawnGroupConfig campABaseSpawnGroup, RoomSpawnGroupConfig campBBaseSpawnGroup,
        RoomPosition campABaseDeployIconPosition, RoomPosition campBBaseDeployIconPosition,
        RoomViewPosition stageADeployViewPosition, RoomViewPosition stageBDeployViewPosition,
        RoomViewPosition stageCDeployViewPosition, RoomViewPosition stageDDeployViewPosition,
        RoomViewPosition stageEDeployViewPosition, RoomViewPosition stageFDeployViewPosition,
        int stageDurationMinutes, int defenderRetreatSeconds, int troopGainPerDefenderKill,
        Double troopsPerPlayer, Integer maxAttackTroops, Integer stageTroopReward,
        RoomTroopBalanceReference troopBalanceReference, List<RoomPosition> ammoBoxPositions,
        List<RoomPosition> medicalBoxPositions, RoomBattalionConfig battalionConfig
    ) {
        this(points, mapId, campADisplayName, campBDisplayName, stageCount, campABaseBoundary, campBBaseBoundary,
            stageABoundary, stageBBoundary, stageCBoundary, stageDBoundary, stageEBoundary, stageFBoundary,
            campABaseSpawnGroup, campBBaseSpawnGroup, campABaseDeployIconPosition, campBBaseDeployIconPosition,
            stageADeployViewPosition, stageBDeployViewPosition, stageCDeployViewPosition, stageDDeployViewPosition,
            stageEDeployViewPosition, stageFDeployViewPosition, stageDurationMinutes, defenderRetreatSeconds,
            troopGainPerDefenderKill, troopsPerPlayer, maxAttackTroops, stageTroopReward, troopBalanceReference,
            ammoBoxPositions, medicalBoxPositions, battalionConfig, true);
    }

    /** Source compatibility for existing map builders. */
    public RoomBreakthroughConfig(
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
        int maxAttackTroopsAt12Players,
        RoomTroopBalanceReference troopBalanceReference,
        List<RoomPosition> ammoBoxPositions,
        List<RoomPosition> medicalBoxPositions
    ) {
        this(points, mapId, campADisplayName, campBDisplayName, stageCount,
            campABaseBoundary, campBBaseBoundary, stageABoundary, stageBBoundary, stageCBoundary,
            stageDBoundary, stageEBoundary, stageFBoundary, campABaseSpawnGroup, campBBaseSpawnGroup,
            campABaseDeployIconPosition, campBBaseDeployIconPosition, stageADeployViewPosition,
            stageBDeployViewPosition, stageCDeployViewPosition, stageDDeployViewPosition,
            stageEDeployViewPosition, stageFDeployViewPosition, stageDurationMinutes,
            defenderRetreatSeconds, troopGainPerDefenderKill, maxAttackTroopsAt12Players / 12.0D, 250, 50,
            troopBalanceReference, ammoBoxPositions, medicalBoxPositions, RoomBattalionConfig.defaults());
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
            3,
            20.0D,
            250,
            50,
            RoomTroopBalanceReference.defaultValue(),
            List.of(),
            List.of(),
            RoomBattalionConfig.defaults()
        );
    }

    @Override
    public String mapId() {
        return mapId;
    }

    @Override
    public String campADisplayName(RoomCommonConfig commonConfig) {
        return campADisplayName;
    }

    @Override
    public String campBDisplayName(RoomCommonConfig commonConfig) {
        return campBDisplayName;
    }

    @Override
    public List<RoomCapturePointDefinition> definedObjectivePoints() {
        return new java.util.ArrayList<>(points.values()).stream()
            .filter(Objects::nonNull)
            .toList();
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

    private static String breakthroughStageBoundaryConfigId(int stageIndex) {
        return switch (stageIndex) {
            case 1 -> "btStageABoundary";
            case 2 -> "btStageBBoundary";
            case 3 -> "btStageCBoundary";
            case 4 -> "btStageDBoundary";
            case 5 -> "btStageEBoundary";
            case 6 -> "btStageFBoundary";
            default -> "btStageBoundary";
        };
    }

    private static String breakthroughStageViewConfigId(int stageIndex) {
        return switch (stageIndex) {
            case 1 -> "btStageDeployViewA";
            case 2 -> "btStageDeployViewB";
            case 3 -> "btStageDeployViewC";
            case 4 -> "btStageDeployViewD";
            case 5 -> "btStageDeployViewE";
            case 6 -> "btStageDeployViewF";
            default -> "btStageDeployView";
        };
    }

    @Override
    public void validate(org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var breakthrough = this;
        if (breakthrough.mapId() == null || breakthrough.mapId().isBlank() || !org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.MAP_ID_PATTERN.matcher(breakthrough.mapId()).matches()) {
            result.addMissing("breakthrough.mapId", "btMapID");
        }
        if (breakthrough.campADisplayName() == null || breakthrough.campADisplayName().isBlank()) {
            result.addMissing("breakthrough.campADisplayName", "btCampADisplayName");
        }
        if (breakthrough.campBDisplayName() == null || breakthrough.campBDisplayName().isBlank()) {
            result.addMissing("breakthrough.campBDisplayName", "btCampBDisplayName");
        }
        if (breakthrough.stageCount() <= 0 || breakthrough.stageCount() > 6) {
            result.addMissing("breakthrough.stageCount", "btStageCount");
        }
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateBoundaryGroup(result, "breakthrough.campABaseBoundary", "btCampABaseBoundary", breakthrough.campABaseBoundary());
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateBoundaryGroup(result, "breakthrough.campBBaseBoundary", "btCampBBaseBoundary", breakthrough.campBBaseBoundary());
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "breakthrough.campABaseSpawnGroup", "btCampABaseSpawnGroup", breakthrough.campABaseSpawnGroup(), true);
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "breakthrough.campBBaseSpawnGroup", "btCampBBaseSpawnGroup", breakthrough.campBBaseSpawnGroup(), true);
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(result, "breakthrough.campABaseDeployIconPosition", "btCampABaseDeployIconPosition", breakthrough.campABaseDeployIconPosition());
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(result, "breakthrough.campBBaseDeployIconPosition", "btCampBBaseDeployIconPosition", breakthrough.campBBaseDeployIconPosition());
        if (breakthrough.stageDurationMinutes() < 0) {
            result.addMissing("breakthrough.stageDurationMinutes", "btStageDurationMinutes");
        }
        if (breakthrough.defenderRetreatSeconds() <= 0) {
            result.addMissing("breakthrough.defenderRetreatSeconds", "btDefenderRetreatSeconds");
        }
        if (breakthrough.troopGainPerDefenderKill() <= 0) {
            result.addMissing("breakthrough.troopGainPerDefenderKill", "btTroopGainPerDefenderKill");
        }
        if (breakthrough.troopsPerPlayer() <= 0) {
            result.addMissing("breakthrough.troopsPerPlayer", "btTroopsPerPlayer");
        }
        if (breakthrough.maxAttackTroops() <= 0) {
            result.addMissing("breakthrough.maxAttackTroops", "btMaxAttackTroops");
        }
        for (int stageIndex = 1; stageIndex <= breakthrough.stageCount(); stageIndex++) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateBoundaryGroup(
                result,
                "breakthrough.stage" + stageIndex + "Boundary",
                breakthroughStageBoundaryConfigId(stageIndex),
                breakthrough.stageBoundary(stageIndex)
            );
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomViewPosition(
                result,
                "breakthrough.stage" + stageIndex + "DeployViewPosition",
                breakthroughStageViewConfigId(stageIndex),
                breakthrough.stageDeployViewPosition(stageIndex)
            );
        }
        if (battalionConfig.count() > 1) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomViewPosition(
                result, "breakthrough.battalionConfig.briefingViewPosition", "btBriefingViewPosition", battalionConfig.briefingViewPosition());
        }
        boolean hasActivePoint = false;
        for (RoomCapturePointDefinition point : breakthrough.points().values()) {
            if (point == null) {
                continue;
            }
            String code = point.code();
            if (point.centerPosition() != null) {
                hasActivePoint = true;
            }
            if (point.displayName() == null || point.displayName().isBlank()) {
                result.addMissing("breakthrough.points[" + code + "].displayName", "btCaptureDisplayName_" + code);
            }
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(
                result,
                "breakthrough.points[" + code + "].centerPosition",
                "btCaptureCenterPosition_" + code,
                point.centerPosition()
            );
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(
                result,
                "breakthrough.points[" + code + "].innerSpawnGroup",
                "btCaptureInnerSpawnGroup_" + code,
                point.innerSpawnGroup(),
                true
            );
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(
                result,
                "breakthrough.points[" + code + "].teamOuterSpawnGroups[CAMP_A]",
                "btCaptureCampAOuterSpawnGroup_" + code,
                point.teamOuterSpawnGroups().get(RoomTeamId.CAMP_A),
                true
            );
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(
                result,
                "breakthrough.points[" + code + "].teamOuterSpawnGroups[CAMP_B]",
                "btCaptureCampBOuterSpawnGroup_" + code,
                point.teamOuterSpawnGroups().get(RoomTeamId.CAMP_B),
                true
            );
            if (point.stageIndex() <= 0 || point.stageIndex() > breakthrough.stageCount()) {
                result.addMissing("breakthrough.points[" + code + "].stageIndex");
            }
        }
        if (!hasActivePoint) {
            result.addMissing("breakthrough.activePoints");
        }
    }
}
