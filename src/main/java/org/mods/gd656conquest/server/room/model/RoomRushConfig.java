package org.mods.gd656conquest.server.room.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public record RoomRushConfig(
    Map<String, RoomPosition> bombPositions,
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
    RoomSpawnGroupConfig bombInnerSpawnGroupA,
    RoomSpawnGroupConfig bombInnerSpawnGroupA1,
    RoomSpawnGroupConfig bombInnerSpawnGroupA2,
    RoomSpawnGroupConfig bombInnerSpawnGroupA3,
    RoomSpawnGroupConfig bombInnerSpawnGroupB,
    RoomSpawnGroupConfig bombInnerSpawnGroupB1,
    RoomSpawnGroupConfig bombInnerSpawnGroupB2,
    RoomSpawnGroupConfig bombInnerSpawnGroupB3,
    RoomSpawnGroupConfig bombInnerSpawnGroupC,
    RoomSpawnGroupConfig bombInnerSpawnGroupC1,
    RoomSpawnGroupConfig bombInnerSpawnGroupC2,
    RoomSpawnGroupConfig bombInnerSpawnGroupC3,
    RoomSpawnGroupConfig bombInnerSpawnGroupD,
    RoomSpawnGroupConfig bombInnerSpawnGroupD1,
    RoomSpawnGroupConfig bombInnerSpawnGroupD2,
    RoomSpawnGroupConfig bombInnerSpawnGroupD3,
    RoomSpawnGroupConfig bombInnerSpawnGroupE,
    RoomSpawnGroupConfig bombInnerSpawnGroupE1,
    RoomSpawnGroupConfig bombInnerSpawnGroupE2,
    RoomSpawnGroupConfig bombInnerSpawnGroupE3,
    RoomSpawnGroupConfig bombInnerSpawnGroupF,
    RoomSpawnGroupConfig bombInnerSpawnGroupF1,
    RoomSpawnGroupConfig bombInnerSpawnGroupF2,
    RoomSpawnGroupConfig bombInnerSpawnGroupF3,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupA,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupA1,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupA2,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupA3,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupB,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupB1,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupB2,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupB3,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupC,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupC1,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupC2,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupC3,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupD,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupD1,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupD2,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupD3,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupE,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupE1,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupE2,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupE3,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupF,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupF1,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupF2,
    RoomSpawnGroupConfig bombDefenderInnerSpawnGroupF3,
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
    List<RoomPosition> medicalBoxPositions,
    int bombPlantTimeSeconds,
    int bombDefuseTimeSeconds,
    int bombFuseTimeSeconds,
    double bombExplosionRadius
) implements IRoomGameplayConfig {
    private static final List<String> RUSH_OBJECTIVE_CODES = List.of(
        "A", "A1", "A2", "A3",
        "B", "B1", "B2", "B3",
        "C", "C1", "C2", "C3",
        "D", "D1", "D2", "D3",
        "E", "E1", "E2", "E3",
        "F", "F1", "F2", "F3"
    );

    public RoomRushConfig {
        Objects.requireNonNull(bombPositions, "bombPositions");
        bombPositions = Map.copyOf(new LinkedHashMap<>(bombPositions));
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
        campABaseSpawnGroup = normalizeSpawnGroup(campABaseSpawnGroup);
        campBBaseSpawnGroup = normalizeSpawnGroup(campBBaseSpawnGroup);
        bombInnerSpawnGroupA = normalizeSpawnGroup(bombInnerSpawnGroupA);
        bombInnerSpawnGroupA1 = normalizeSpawnGroup(bombInnerSpawnGroupA1);
        bombInnerSpawnGroupA2 = normalizeSpawnGroup(bombInnerSpawnGroupA2);
        bombInnerSpawnGroupA3 = normalizeSpawnGroup(bombInnerSpawnGroupA3);
        bombInnerSpawnGroupB = normalizeSpawnGroup(bombInnerSpawnGroupB);
        bombInnerSpawnGroupB1 = normalizeSpawnGroup(bombInnerSpawnGroupB1);
        bombInnerSpawnGroupB2 = normalizeSpawnGroup(bombInnerSpawnGroupB2);
        bombInnerSpawnGroupB3 = normalizeSpawnGroup(bombInnerSpawnGroupB3);
        bombInnerSpawnGroupC = normalizeSpawnGroup(bombInnerSpawnGroupC);
        bombInnerSpawnGroupC1 = normalizeSpawnGroup(bombInnerSpawnGroupC1);
        bombInnerSpawnGroupC2 = normalizeSpawnGroup(bombInnerSpawnGroupC2);
        bombInnerSpawnGroupC3 = normalizeSpawnGroup(bombInnerSpawnGroupC3);
        bombInnerSpawnGroupD = normalizeSpawnGroup(bombInnerSpawnGroupD);
        bombInnerSpawnGroupD1 = normalizeSpawnGroup(bombInnerSpawnGroupD1);
        bombInnerSpawnGroupD2 = normalizeSpawnGroup(bombInnerSpawnGroupD2);
        bombInnerSpawnGroupD3 = normalizeSpawnGroup(bombInnerSpawnGroupD3);
        bombInnerSpawnGroupE = normalizeSpawnGroup(bombInnerSpawnGroupE);
        bombInnerSpawnGroupE1 = normalizeSpawnGroup(bombInnerSpawnGroupE1);
        bombInnerSpawnGroupE2 = normalizeSpawnGroup(bombInnerSpawnGroupE2);
        bombInnerSpawnGroupE3 = normalizeSpawnGroup(bombInnerSpawnGroupE3);
        bombInnerSpawnGroupF = normalizeSpawnGroup(bombInnerSpawnGroupF);
        bombInnerSpawnGroupF1 = normalizeSpawnGroup(bombInnerSpawnGroupF1);
        bombInnerSpawnGroupF2 = normalizeSpawnGroup(bombInnerSpawnGroupF2);
        bombInnerSpawnGroupF3 = normalizeSpawnGroup(bombInnerSpawnGroupF3);
        bombDefenderInnerSpawnGroupA = normalizeSpawnGroup(bombDefenderInnerSpawnGroupA);
        bombDefenderInnerSpawnGroupA1 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupA1);
        bombDefenderInnerSpawnGroupA2 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupA2);
        bombDefenderInnerSpawnGroupA3 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupA3);
        bombDefenderInnerSpawnGroupB = normalizeSpawnGroup(bombDefenderInnerSpawnGroupB);
        bombDefenderInnerSpawnGroupB1 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupB1);
        bombDefenderInnerSpawnGroupB2 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupB2);
        bombDefenderInnerSpawnGroupB3 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupB3);
        bombDefenderInnerSpawnGroupC = normalizeSpawnGroup(bombDefenderInnerSpawnGroupC);
        bombDefenderInnerSpawnGroupC1 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupC1);
        bombDefenderInnerSpawnGroupC2 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupC2);
        bombDefenderInnerSpawnGroupC3 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupC3);
        bombDefenderInnerSpawnGroupD = normalizeSpawnGroup(bombDefenderInnerSpawnGroupD);
        bombDefenderInnerSpawnGroupD1 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupD1);
        bombDefenderInnerSpawnGroupD2 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupD2);
        bombDefenderInnerSpawnGroupD3 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupD3);
        bombDefenderInnerSpawnGroupE = normalizeSpawnGroup(bombDefenderInnerSpawnGroupE);
        bombDefenderInnerSpawnGroupE1 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupE1);
        bombDefenderInnerSpawnGroupE2 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupE2);
        bombDefenderInnerSpawnGroupE3 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupE3);
        bombDefenderInnerSpawnGroupF = normalizeSpawnGroup(bombDefenderInnerSpawnGroupF);
        bombDefenderInnerSpawnGroupF1 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupF1);
        bombDefenderInnerSpawnGroupF2 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupF2);
        bombDefenderInnerSpawnGroupF3 = normalizeSpawnGroup(bombDefenderInnerSpawnGroupF3);
        campABaseDeployIconPosition = copyPosition(campABaseDeployIconPosition);
        campBBaseDeployIconPosition = copyPosition(campBBaseDeployIconPosition);
        stageDurationMinutes = Math.max(0, stageDurationMinutes);
        defenderRetreatSeconds = Math.max(0, defenderRetreatSeconds);
        troopGainPerDefenderKill = Math.max(0, troopGainPerDefenderKill);
        maxAttackTroopsAt12Players = Math.max(0, maxAttackTroopsAt12Players);
        troopBalanceReference = troopBalanceReference == null ? RoomTroopBalanceReference.defaultValue() : troopBalanceReference;
        ammoBoxPositions = copyPositions(ammoBoxPositions);
        medicalBoxPositions = copyPositions(medicalBoxPositions);
        bombPlantTimeSeconds = Math.max(0, bombPlantTimeSeconds);
        bombDefuseTimeSeconds = Math.max(0, bombDefuseTimeSeconds);
        bombFuseTimeSeconds = Math.max(0, bombFuseTimeSeconds);
        bombExplosionRadius = Math.max(0.0D, bombExplosionRadius);
    }

    public static RoomRushConfig defaults() {
        return new RoomRushConfig(
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
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
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
            0,
            RoomTroopBalanceReference.defaultValue(),
            List.of(),
            List.of(),
            5,
            5,
            30,
            4.0D
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
        java.util.ArrayList<RoomCapturePointDefinition> points = new java.util.ArrayList<>();
        for (String code : RUSH_OBJECTIVE_CODES) {
            RoomPosition center = bombPositions.get(code);
            int stageIndex = stageIndexOfCode(code);
            if (center == null || stageIndex <= 0 || stageIndex > stageCount) {
                continue;
            }
            RoomSpawnGroupConfig campASpawns = bombSpawnGroup(code, RoomTeamId.CAMP_A);
            RoomSpawnGroupConfig campBSpawns = bombSpawnGroup(code, RoomTeamId.CAMP_B);
            points.add(new RoomCapturePointDefinition(
                code,
                code,
                center,
                campASpawns,
                campBSpawns,
                Map.of(
                    RoomTeamId.CAMP_A, campASpawns,
                    RoomTeamId.CAMP_B, campBSpawns
                ),
                stageIndex
            ));
        }
        return List.copyOf(points);
    }

    public List<String> stageBombCodes(int stageIndex) {
        if (stageIndex <= 0 || stageIndex > stageCount) {
            return List.of();
        }
        java.util.ArrayList<String> codes = new java.util.ArrayList<>();
        for (String code : RUSH_OBJECTIVE_CODES) {
            if (stageIndexOfCode(code) == stageIndex && bombPositions.containsKey(code)) {
                codes.add(code);
            }
        }
        return List.copyOf(codes);
    }

    public List<RoomCapturePointDefinition> stageObjectivePoints(int stageIndex) {
        if (stageIndex <= 0 || stageIndex > stageCount) {
            return List.of();
        }
        java.util.ArrayList<RoomCapturePointDefinition> points = new java.util.ArrayList<>();
        for (RoomCapturePointDefinition point : definedObjectivePoints()) {
            if (point != null && point.stageIndex() == stageIndex) {
                points.add(point);
            }
        }
        return List.copyOf(points);
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

    public RoomSpawnGroupConfig bombInnerSpawnGroup(int stageIndex, RoomTeamId teamId) {
        return bombSpawnGroup(stageCode(stageIndex), teamId);
    }

    public RoomSpawnGroupConfig bombSpawnGroup(String objectiveCode, RoomTeamId teamId) {
        String code = normalizeObjectiveCode(objectiveCode);
        if (teamId == RoomTeamId.CAMP_B) {
            return switch (code) {
                case "A" -> bombDefenderInnerSpawnGroupA;
                case "A1" -> bombDefenderInnerSpawnGroupA1;
                case "A2" -> bombDefenderInnerSpawnGroupA2;
                case "A3" -> bombDefenderInnerSpawnGroupA3;
                case "B" -> bombDefenderInnerSpawnGroupB;
                case "B1" -> bombDefenderInnerSpawnGroupB1;
                case "B2" -> bombDefenderInnerSpawnGroupB2;
                case "B3" -> bombDefenderInnerSpawnGroupB3;
                case "C" -> bombDefenderInnerSpawnGroupC;
                case "C1" -> bombDefenderInnerSpawnGroupC1;
                case "C2" -> bombDefenderInnerSpawnGroupC2;
                case "C3" -> bombDefenderInnerSpawnGroupC3;
                case "D" -> bombDefenderInnerSpawnGroupD;
                case "D1" -> bombDefenderInnerSpawnGroupD1;
                case "D2" -> bombDefenderInnerSpawnGroupD2;
                case "D3" -> bombDefenderInnerSpawnGroupD3;
                case "E" -> bombDefenderInnerSpawnGroupE;
                case "E1" -> bombDefenderInnerSpawnGroupE1;
                case "E2" -> bombDefenderInnerSpawnGroupE2;
                case "E3" -> bombDefenderInnerSpawnGroupE3;
                case "F" -> bombDefenderInnerSpawnGroupF;
                case "F1" -> bombDefenderInnerSpawnGroupF1;
                case "F2" -> bombDefenderInnerSpawnGroupF2;
                case "F3" -> bombDefenderInnerSpawnGroupF3;
                default -> RoomSpawnGroupConfig.defaults();
            };
        }
        return switch (code) {
            case "A" -> bombInnerSpawnGroupA;
            case "A1" -> bombInnerSpawnGroupA1;
            case "A2" -> bombInnerSpawnGroupA2;
            case "A3" -> bombInnerSpawnGroupA3;
            case "B" -> bombInnerSpawnGroupB;
            case "B1" -> bombInnerSpawnGroupB1;
            case "B2" -> bombInnerSpawnGroupB2;
            case "B3" -> bombInnerSpawnGroupB3;
            case "C" -> bombInnerSpawnGroupC;
            case "C1" -> bombInnerSpawnGroupC1;
            case "C2" -> bombInnerSpawnGroupC2;
            case "C3" -> bombInnerSpawnGroupC3;
            case "D" -> bombInnerSpawnGroupD;
            case "D1" -> bombInnerSpawnGroupD1;
            case "D2" -> bombInnerSpawnGroupD2;
            case "D3" -> bombInnerSpawnGroupD3;
            case "E" -> bombInnerSpawnGroupE;
            case "E1" -> bombInnerSpawnGroupE1;
            case "E2" -> bombInnerSpawnGroupE2;
            case "E3" -> bombInnerSpawnGroupE3;
            case "F" -> bombInnerSpawnGroupF;
            case "F1" -> bombInnerSpawnGroupF1;
            case "F2" -> bombInnerSpawnGroupF2;
            case "F3" -> bombInnerSpawnGroupF3;
            default -> RoomSpawnGroupConfig.defaults();
        };
    }

    public RoomRushConfig withBombPositions(Map<String, RoomPosition> updatedBombPositions) {
        return new RoomRushConfig(
            updatedBombPositions,
            mapId,
            campADisplayName,
            campBDisplayName,
            stageCount,
            campABaseBoundary,
            campBBaseBoundary,
            stageABoundary,
            stageBBoundary,
            stageCBoundary,
            stageDBoundary,
            stageEBoundary,
            stageFBoundary,
            campABaseSpawnGroup,
            campBBaseSpawnGroup,
            bombInnerSpawnGroupA,
            bombInnerSpawnGroupA1,
            bombInnerSpawnGroupA2,
            bombInnerSpawnGroupA3,
            bombInnerSpawnGroupB,
            bombInnerSpawnGroupB1,
            bombInnerSpawnGroupB2,
            bombInnerSpawnGroupB3,
            bombInnerSpawnGroupC,
            bombInnerSpawnGroupC1,
            bombInnerSpawnGroupC2,
            bombInnerSpawnGroupC3,
            bombInnerSpawnGroupD,
            bombInnerSpawnGroupD1,
            bombInnerSpawnGroupD2,
            bombInnerSpawnGroupD3,
            bombInnerSpawnGroupE,
            bombInnerSpawnGroupE1,
            bombInnerSpawnGroupE2,
            bombInnerSpawnGroupE3,
            bombInnerSpawnGroupF,
            bombInnerSpawnGroupF1,
            bombInnerSpawnGroupF2,
            bombInnerSpawnGroupF3,
            bombDefenderInnerSpawnGroupA,
            bombDefenderInnerSpawnGroupA1,
            bombDefenderInnerSpawnGroupA2,
            bombDefenderInnerSpawnGroupA3,
            bombDefenderInnerSpawnGroupB,
            bombDefenderInnerSpawnGroupB1,
            bombDefenderInnerSpawnGroupB2,
            bombDefenderInnerSpawnGroupB3,
            bombDefenderInnerSpawnGroupC,
            bombDefenderInnerSpawnGroupC1,
            bombDefenderInnerSpawnGroupC2,
            bombDefenderInnerSpawnGroupC3,
            bombDefenderInnerSpawnGroupD,
            bombDefenderInnerSpawnGroupD1,
            bombDefenderInnerSpawnGroupD2,
            bombDefenderInnerSpawnGroupD3,
            bombDefenderInnerSpawnGroupE,
            bombDefenderInnerSpawnGroupE1,
            bombDefenderInnerSpawnGroupE2,
            bombDefenderInnerSpawnGroupE3,
            bombDefenderInnerSpawnGroupF,
            bombDefenderInnerSpawnGroupF1,
            bombDefenderInnerSpawnGroupF2,
            bombDefenderInnerSpawnGroupF3,
            campABaseDeployIconPosition,
            campBBaseDeployIconPosition,
            stageADeployViewPosition,
            stageBDeployViewPosition,
            stageCDeployViewPosition,
            stageDDeployViewPosition,
            stageEDeployViewPosition,
            stageFDeployViewPosition,
            stageDurationMinutes,
            defenderRetreatSeconds,
            troopGainPerDefenderKill,
            maxAttackTroopsAt12Players,
            troopBalanceReference,
            ammoBoxPositions,
            medicalBoxPositions,
            bombPlantTimeSeconds,
            bombDefuseTimeSeconds,
            bombFuseTimeSeconds,
            bombExplosionRadius
        );
    }

    public RoomRushConfig copy() {
        return withBombPositions(bombPositions);
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

    @Override
    public void validate(org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var rush = this;
        if (rush.mapId() == null || rush.mapId().isBlank() || !org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.MAP_ID_PATTERN.matcher(rush.mapId()).matches()) {
            result.addMissing("rush.mapId", "rsMapID");
        }
        if (rush.campADisplayName() == null || rush.campADisplayName().isBlank()) {
            result.addMissing("rush.campADisplayName", "rsCampADisplayName");
        }
        if (rush.campBDisplayName() == null || rush.campBDisplayName().isBlank()) {
            result.addMissing("rush.campBDisplayName", "rsCampBDisplayName");
        }
        if (rush.stageCount() <= 0 || rush.stageCount() > 6) {
            result.addMissing("rush.stageCount", "rsStageCount");
        }
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateBoundaryGroup(result, "rush.campABaseBoundary", "rsCampABaseBoundary", rush.campABaseBoundary());
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateBoundaryGroup(result, "rush.campBBaseBoundary", "rsCampBBaseBoundary", rush.campBBaseBoundary());
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "rush.campABaseSpawnGroup", "rsCampABaseSpawnGroup", rush.campABaseSpawnGroup(), true);
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "rush.campBBaseSpawnGroup", "rsCampBBaseSpawnGroup", rush.campBBaseSpawnGroup(), true);
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(result, "rush.campABaseDeployIconPosition", "rsCampABaseDeployIconPosition", rush.campABaseDeployIconPosition());
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(result, "rush.campBBaseDeployIconPosition", "rsCampBBaseDeployIconPosition", rush.campBBaseDeployIconPosition());

        if (rush.stageDurationMinutes() <= 0) {
            result.addMissing("rush.stageDurationMinutes", "rsStageDurationMinutes");
        }
        if (rush.defenderRetreatSeconds() <= 0) {
            result.addMissing("rush.defenderRetreatSeconds", "rsDefenderRetreatSeconds");
        }
        if (rush.troopGainPerDefenderKill() <= 0) {
            result.addMissing("rush.troopGainPerDefenderKill", "rsTroopGainPerDefenderKill");
        }
        if (rush.maxAttackTroopsAt12Players() <= 0) {
            result.addMissing("rush.maxAttackTroopsAt12Players", "rsMaxAttackTroopsAt12Players");
        }
        if (rush.bombPlantTimeSeconds() <= 0) {
            result.addMissing("rush.bombPlantTimeSeconds", "rsBombPlantTimeSeconds");
        }
        if (rush.bombDefuseTimeSeconds() <= 0) {
            result.addMissing("rush.bombDefuseTimeSeconds", "rsBombDefuseTimeSeconds");
        }
        if (rush.bombFuseTimeSeconds() <= 0) {
            result.addMissing("rush.bombFuseTimeSeconds", "rsBombFuseTimeSeconds");
        }
        if (rush.bombExplosionRadius() <= 0.0D) {
            result.addMissing("rush.bombExplosionRadius", "rsBombExplosionRadius");
        }

        for (int stageIndex = 1; stageIndex <= rush.stageCount(); stageIndex++) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateBoundaryGroup(
                result,
                "rush.stage" + stageIndex + "Boundary",
                rushStageBoundaryConfigId(stageIndex),
                rush.stageBoundary(stageIndex)
            );
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomViewPosition(
                result,
                "rush.stage" + stageIndex + "DeployViewPosition",
                rushStageViewConfigId(stageIndex),
                rush.stageDeployViewPosition(stageIndex)
            );
            List<String> stageCodes = rush.stageBombCodes(stageIndex);
            if (stageCodes.isEmpty()) {
                String requiredCode = stageCode(stageIndex);
                result.addMissing("rush.bombPosition." + requiredCode, "rsBombPosition_" + requiredCode);
                continue;
            }
            for (String code : stageCodes) {
                org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(
                    result,
                    "rush.bombPosition." + code,
                    "rsBombPosition_" + code,
                    rush.bombPositions().get(code)
                );
                org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(
                    result,
                    "rush.bombInnerSpawnGroup." + code,
                    "rsBombCampASpawnGroup_" + code,
                    rush.bombSpawnGroup(code, RoomTeamId.CAMP_A),
                    true
                );
                org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(
                    result,
                    "rush.bombDefenderInnerSpawnGroup." + code,
                    "rsBombCampBSpawnGroup_" + code,
                    rush.bombSpawnGroup(code, RoomTeamId.CAMP_B),
                    true
                );
            }
        }
    }

    private static List<RoomPosition> copyPositions(List<RoomPosition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static RoomSpawnGroupConfig normalizeSpawnGroup(RoomSpawnGroupConfig value) {
        return value == null ? RoomSpawnGroupConfig.defaults() : value;
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

    private static String normalizeMapId(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeObjectiveCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static int stageIndexOfCode(String code) {
        String normalized = normalizeObjectiveCode(code);
        if (normalized.isBlank()) {
            return 0;
        }
        char stageChar = normalized.charAt(0);
        if (stageChar < 'A' || stageChar > 'F') {
            return 0;
        }
        return stageChar - 'A' + 1;
    }

    private static String stageCode(int stageIndex) {
        if (stageIndex < 1 || stageIndex > 6) {
            return "";
        }
        return String.valueOf((char) ('A' + stageIndex - 1));
    }

    private static String rushStageBoundaryConfigId(int stageIndex) {
        return switch (stageIndex) {
            case 1 -> "rsStageABoundary";
            case 2 -> "rsStageBBoundary";
            case 3 -> "rsStageCBoundary";
            case 4 -> "rsStageDBoundary";
            case 5 -> "rsStageEBoundary";
            case 6 -> "rsStageFBoundary";
            default -> "rsStageBoundary";
        };
    }

    private static String rushStageViewConfigId(int stageIndex) {
        return switch (stageIndex) {
            case 1 -> "rsStageDeployViewA";
            case 2 -> "rsStageDeployViewB";
            case 3 -> "rsStageDeployViewC";
            case 4 -> "rsStageDeployViewD";
            case 5 -> "rsStageDeployViewE";
            case 6 -> "rsStageDeployViewF";
            default -> "rsStageDeployView";
        };
    }
}
