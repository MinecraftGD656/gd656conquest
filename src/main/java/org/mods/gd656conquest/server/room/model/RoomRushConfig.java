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
    RoomSpawnGroupConfig bombCampASpawnGroupA,
    RoomSpawnGroupConfig bombCampASpawnGroupA1,
    RoomSpawnGroupConfig bombCampASpawnGroupA2,
    RoomSpawnGroupConfig bombCampASpawnGroupA3,
    RoomSpawnGroupConfig bombCampASpawnGroupB,
    RoomSpawnGroupConfig bombCampASpawnGroupB1,
    RoomSpawnGroupConfig bombCampASpawnGroupB2,
    RoomSpawnGroupConfig bombCampASpawnGroupB3,
    RoomSpawnGroupConfig bombCampASpawnGroupC,
    RoomSpawnGroupConfig bombCampASpawnGroupC1,
    RoomSpawnGroupConfig bombCampASpawnGroupC2,
    RoomSpawnGroupConfig bombCampASpawnGroupC3,
    RoomSpawnGroupConfig bombCampASpawnGroupD,
    RoomSpawnGroupConfig bombCampASpawnGroupD1,
    RoomSpawnGroupConfig bombCampASpawnGroupD2,
    RoomSpawnGroupConfig bombCampASpawnGroupD3,
    RoomSpawnGroupConfig bombCampASpawnGroupE,
    RoomSpawnGroupConfig bombCampASpawnGroupE1,
    RoomSpawnGroupConfig bombCampASpawnGroupE2,
    RoomSpawnGroupConfig bombCampASpawnGroupE3,
    RoomSpawnGroupConfig bombCampASpawnGroupF,
    RoomSpawnGroupConfig bombCampASpawnGroupF1,
    RoomSpawnGroupConfig bombCampASpawnGroupF2,
    RoomSpawnGroupConfig bombCampASpawnGroupF3,
    RoomSpawnGroupConfig bombCampBSpawnGroupA,
    RoomSpawnGroupConfig bombCampBSpawnGroupA1,
    RoomSpawnGroupConfig bombCampBSpawnGroupA2,
    RoomSpawnGroupConfig bombCampBSpawnGroupA3,
    RoomSpawnGroupConfig bombCampBSpawnGroupB,
    RoomSpawnGroupConfig bombCampBSpawnGroupB1,
    RoomSpawnGroupConfig bombCampBSpawnGroupB2,
    RoomSpawnGroupConfig bombCampBSpawnGroupB3,
    RoomSpawnGroupConfig bombCampBSpawnGroupC,
    RoomSpawnGroupConfig bombCampBSpawnGroupC1,
    RoomSpawnGroupConfig bombCampBSpawnGroupC2,
    RoomSpawnGroupConfig bombCampBSpawnGroupC3,
    RoomSpawnGroupConfig bombCampBSpawnGroupD,
    RoomSpawnGroupConfig bombCampBSpawnGroupD1,
    RoomSpawnGroupConfig bombCampBSpawnGroupD2,
    RoomSpawnGroupConfig bombCampBSpawnGroupD3,
    RoomSpawnGroupConfig bombCampBSpawnGroupE,
    RoomSpawnGroupConfig bombCampBSpawnGroupE1,
    RoomSpawnGroupConfig bombCampBSpawnGroupE2,
    RoomSpawnGroupConfig bombCampBSpawnGroupE3,
    RoomSpawnGroupConfig bombCampBSpawnGroupF,
    RoomSpawnGroupConfig bombCampBSpawnGroupF1,
    RoomSpawnGroupConfig bombCampBSpawnGroupF2,
    RoomSpawnGroupConfig bombCampBSpawnGroupF3,
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
        bombCampASpawnGroupA = normalizeSpawnGroup(bombCampASpawnGroupA);
        bombCampASpawnGroupA1 = normalizeSpawnGroup(bombCampASpawnGroupA1);
        bombCampASpawnGroupA2 = normalizeSpawnGroup(bombCampASpawnGroupA2);
        bombCampASpawnGroupA3 = normalizeSpawnGroup(bombCampASpawnGroupA3);
        bombCampASpawnGroupB = normalizeSpawnGroup(bombCampASpawnGroupB);
        bombCampASpawnGroupB1 = normalizeSpawnGroup(bombCampASpawnGroupB1);
        bombCampASpawnGroupB2 = normalizeSpawnGroup(bombCampASpawnGroupB2);
        bombCampASpawnGroupB3 = normalizeSpawnGroup(bombCampASpawnGroupB3);
        bombCampASpawnGroupC = normalizeSpawnGroup(bombCampASpawnGroupC);
        bombCampASpawnGroupC1 = normalizeSpawnGroup(bombCampASpawnGroupC1);
        bombCampASpawnGroupC2 = normalizeSpawnGroup(bombCampASpawnGroupC2);
        bombCampASpawnGroupC3 = normalizeSpawnGroup(bombCampASpawnGroupC3);
        bombCampASpawnGroupD = normalizeSpawnGroup(bombCampASpawnGroupD);
        bombCampASpawnGroupD1 = normalizeSpawnGroup(bombCampASpawnGroupD1);
        bombCampASpawnGroupD2 = normalizeSpawnGroup(bombCampASpawnGroupD2);
        bombCampASpawnGroupD3 = normalizeSpawnGroup(bombCampASpawnGroupD3);
        bombCampASpawnGroupE = normalizeSpawnGroup(bombCampASpawnGroupE);
        bombCampASpawnGroupE1 = normalizeSpawnGroup(bombCampASpawnGroupE1);
        bombCampASpawnGroupE2 = normalizeSpawnGroup(bombCampASpawnGroupE2);
        bombCampASpawnGroupE3 = normalizeSpawnGroup(bombCampASpawnGroupE3);
        bombCampASpawnGroupF = normalizeSpawnGroup(bombCampASpawnGroupF);
        bombCampASpawnGroupF1 = normalizeSpawnGroup(bombCampASpawnGroupF1);
        bombCampASpawnGroupF2 = normalizeSpawnGroup(bombCampASpawnGroupF2);
        bombCampASpawnGroupF3 = normalizeSpawnGroup(bombCampASpawnGroupF3);
        bombCampBSpawnGroupA = normalizeSpawnGroup(bombCampBSpawnGroupA);
        bombCampBSpawnGroupA1 = normalizeSpawnGroup(bombCampBSpawnGroupA1);
        bombCampBSpawnGroupA2 = normalizeSpawnGroup(bombCampBSpawnGroupA2);
        bombCampBSpawnGroupA3 = normalizeSpawnGroup(bombCampBSpawnGroupA3);
        bombCampBSpawnGroupB = normalizeSpawnGroup(bombCampBSpawnGroupB);
        bombCampBSpawnGroupB1 = normalizeSpawnGroup(bombCampBSpawnGroupB1);
        bombCampBSpawnGroupB2 = normalizeSpawnGroup(bombCampBSpawnGroupB2);
        bombCampBSpawnGroupB3 = normalizeSpawnGroup(bombCampBSpawnGroupB3);
        bombCampBSpawnGroupC = normalizeSpawnGroup(bombCampBSpawnGroupC);
        bombCampBSpawnGroupC1 = normalizeSpawnGroup(bombCampBSpawnGroupC1);
        bombCampBSpawnGroupC2 = normalizeSpawnGroup(bombCampBSpawnGroupC2);
        bombCampBSpawnGroupC3 = normalizeSpawnGroup(bombCampBSpawnGroupC3);
        bombCampBSpawnGroupD = normalizeSpawnGroup(bombCampBSpawnGroupD);
        bombCampBSpawnGroupD1 = normalizeSpawnGroup(bombCampBSpawnGroupD1);
        bombCampBSpawnGroupD2 = normalizeSpawnGroup(bombCampBSpawnGroupD2);
        bombCampBSpawnGroupD3 = normalizeSpawnGroup(bombCampBSpawnGroupD3);
        bombCampBSpawnGroupE = normalizeSpawnGroup(bombCampBSpawnGroupE);
        bombCampBSpawnGroupE1 = normalizeSpawnGroup(bombCampBSpawnGroupE1);
        bombCampBSpawnGroupE2 = normalizeSpawnGroup(bombCampBSpawnGroupE2);
        bombCampBSpawnGroupE3 = normalizeSpawnGroup(bombCampBSpawnGroupE3);
        bombCampBSpawnGroupF = normalizeSpawnGroup(bombCampBSpawnGroupF);
        bombCampBSpawnGroupF1 = normalizeSpawnGroup(bombCampBSpawnGroupF1);
        bombCampBSpawnGroupF2 = normalizeSpawnGroup(bombCampBSpawnGroupF2);
        bombCampBSpawnGroupF3 = normalizeSpawnGroup(bombCampBSpawnGroupF3);
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

    public RoomSpawnGroupConfig bombCampASpawnGroup(int stageIndex, RoomTeamId teamId) {
        return bombSpawnGroup(stageCode(stageIndex), teamId);
    }

    public RoomSpawnGroupConfig bombSpawnGroup(String objectiveCode, RoomTeamId teamId) {
        String code = normalizeObjectiveCode(objectiveCode);
        if (teamId == RoomTeamId.CAMP_B) {
            return switch (code) {
                case "A" -> bombCampBSpawnGroupA;
                case "A1" -> bombCampBSpawnGroupA1;
                case "A2" -> bombCampBSpawnGroupA2;
                case "A3" -> bombCampBSpawnGroupA3;
                case "B" -> bombCampBSpawnGroupB;
                case "B1" -> bombCampBSpawnGroupB1;
                case "B2" -> bombCampBSpawnGroupB2;
                case "B3" -> bombCampBSpawnGroupB3;
                case "C" -> bombCampBSpawnGroupC;
                case "C1" -> bombCampBSpawnGroupC1;
                case "C2" -> bombCampBSpawnGroupC2;
                case "C3" -> bombCampBSpawnGroupC3;
                case "D" -> bombCampBSpawnGroupD;
                case "D1" -> bombCampBSpawnGroupD1;
                case "D2" -> bombCampBSpawnGroupD2;
                case "D3" -> bombCampBSpawnGroupD3;
                case "E" -> bombCampBSpawnGroupE;
                case "E1" -> bombCampBSpawnGroupE1;
                case "E2" -> bombCampBSpawnGroupE2;
                case "E3" -> bombCampBSpawnGroupE3;
                case "F" -> bombCampBSpawnGroupF;
                case "F1" -> bombCampBSpawnGroupF1;
                case "F2" -> bombCampBSpawnGroupF2;
                case "F3" -> bombCampBSpawnGroupF3;
                default -> RoomSpawnGroupConfig.defaults();
            };
        }
        return switch (code) {
            case "A" -> bombCampASpawnGroupA;
            case "A1" -> bombCampASpawnGroupA1;
            case "A2" -> bombCampASpawnGroupA2;
            case "A3" -> bombCampASpawnGroupA3;
            case "B" -> bombCampASpawnGroupB;
            case "B1" -> bombCampASpawnGroupB1;
            case "B2" -> bombCampASpawnGroupB2;
            case "B3" -> bombCampASpawnGroupB3;
            case "C" -> bombCampASpawnGroupC;
            case "C1" -> bombCampASpawnGroupC1;
            case "C2" -> bombCampASpawnGroupC2;
            case "C3" -> bombCampASpawnGroupC3;
            case "D" -> bombCampASpawnGroupD;
            case "D1" -> bombCampASpawnGroupD1;
            case "D2" -> bombCampASpawnGroupD2;
            case "D3" -> bombCampASpawnGroupD3;
            case "E" -> bombCampASpawnGroupE;
            case "E1" -> bombCampASpawnGroupE1;
            case "E2" -> bombCampASpawnGroupE2;
            case "E3" -> bombCampASpawnGroupE3;
            case "F" -> bombCampASpawnGroupF;
            case "F1" -> bombCampASpawnGroupF1;
            case "F2" -> bombCampASpawnGroupF2;
            case "F3" -> bombCampASpawnGroupF3;
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
            bombCampASpawnGroupA,
            bombCampASpawnGroupA1,
            bombCampASpawnGroupA2,
            bombCampASpawnGroupA3,
            bombCampASpawnGroupB,
            bombCampASpawnGroupB1,
            bombCampASpawnGroupB2,
            bombCampASpawnGroupB3,
            bombCampASpawnGroupC,
            bombCampASpawnGroupC1,
            bombCampASpawnGroupC2,
            bombCampASpawnGroupC3,
            bombCampASpawnGroupD,
            bombCampASpawnGroupD1,
            bombCampASpawnGroupD2,
            bombCampASpawnGroupD3,
            bombCampASpawnGroupE,
            bombCampASpawnGroupE1,
            bombCampASpawnGroupE2,
            bombCampASpawnGroupE3,
            bombCampASpawnGroupF,
            bombCampASpawnGroupF1,
            bombCampASpawnGroupF2,
            bombCampASpawnGroupF3,
            bombCampBSpawnGroupA,
            bombCampBSpawnGroupA1,
            bombCampBSpawnGroupA2,
            bombCampBSpawnGroupA3,
            bombCampBSpawnGroupB,
            bombCampBSpawnGroupB1,
            bombCampBSpawnGroupB2,
            bombCampBSpawnGroupB3,
            bombCampBSpawnGroupC,
            bombCampBSpawnGroupC1,
            bombCampBSpawnGroupC2,
            bombCampBSpawnGroupC3,
            bombCampBSpawnGroupD,
            bombCampBSpawnGroupD1,
            bombCampBSpawnGroupD2,
            bombCampBSpawnGroupD3,
            bombCampBSpawnGroupE,
            bombCampBSpawnGroupE1,
            bombCampBSpawnGroupE2,
            bombCampBSpawnGroupE3,
            bombCampBSpawnGroupF,
            bombCampBSpawnGroupF1,
            bombCampBSpawnGroupF2,
            bombCampBSpawnGroupF3,
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
                    "rush.bombCampASpawnGroup." + code,
                    "rsBombCampASpawnGroup_" + code,
                    rush.bombSpawnGroup(code, RoomTeamId.CAMP_A),
                    true
                );
                org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(
                    result,
                    "rush.bombCampBSpawnGroup." + code,
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
