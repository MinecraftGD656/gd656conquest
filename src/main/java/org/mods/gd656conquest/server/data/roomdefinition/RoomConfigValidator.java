package org.mods.gd656conquest.server.data.roomdefinition;

import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.model.RoomVoicePack;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

public final class RoomConfigValidator {
    private static final java.util.regex.Pattern MAP_ID_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_]+$");

    private RoomConfigValidator() {
    }

    public static RoomConfigValidationResult validate(RoomDefinition definition) {
        RoomConfigValidationResult result = new RoomConfigValidationResult();
        validateCommon(result, definition);
        if (definition == null || definition.gameplayId() == null) {
            return result;
        }
        if (RoomGameplayId.CONQUEST.equals(definition.gameplayId())) {
            validateConquest(result, definition);
        } else if (RoomGameplayId.BREAKTHROUGH.equals(definition.gameplayId())) {
            validateBreakthrough(result, definition);
        }
        return result;
    }

    private static void validateCommon(RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            result.addMissing("definition");
            return;
        }
        if (definition.roomId() == null || definition.roomId().isBlank()) {
            result.addMissing("roomId");
        }
        if (definition.displayName() == null || definition.displayName().isBlank()) {
            result.addMissing("displayName");
        }
        if (definition.gameplayId() == null) {
            result.addMissing("gameplayId");
        }
        if (definition.commonConfig() == null || definition.commonConfig().dimensionId() == null || definition.commonConfig().dimensionId().isBlank()) {
            result.addMissing("dimensionId");
        }

        var common = definition.commonConfig();
        var matching = common.matchingConfig();
        var resultConfig = common.resultConfig();

        if (matching.minStartPlayers() <= 0) {
            result.addMissing("common.queueMinStartPlayers", "minStartPlayers");
        }
        if (matching.totalMaxPlayers() <= 0 || matching.totalMaxPlayers() < matching.minStartPlayers()) {
            result.addMissing("common.queueMaxPlayers", "queueMaxPlayers");
        }
        if (matching.waitAfterMinSeconds() <= 0) {
            result.addMissing("common.queueWaitAfterMinSeconds", "waitAfterMinSeconds");
        }
        if (matching.waitAfterMaxSeconds() <= 0) {
            result.addMissing("common.queueWaitAfterMaxSeconds", "waitAfterMaxSeconds");
        }
        validateRoomPosition(result, "common.queueSpawn", "queueSpawn", common.queueSpawn());
        if (common.respawnDelaySeconds() <= 0) {
            result.addMissing("common.respawnDelaySeconds", "respawnDelaySeconds");
        }
        if (common.progressSpeed() <= 0.0D) {
            result.addMissing("common.progressSpeed", "progressSpeed");
        }
        if (common.outsideBoundaryDeathSeconds() <= 0) {
            result.addMissing("common.outsideBoundaryDeathSeconds", "outsideBoundaryDeathSeconds");
        }
        if (common.aircraftOutsideBoundaryCleanupSeconds() <= 0) {
            result.addMissing("common.aircraftOutsideBoundaryCleanupSeconds", "aircraftOutsideBoundaryCleanupSeconds");
        }
        if (common.campADisplayName() == null || common.campADisplayName().isBlank()) {
            result.addMissing("common.campADisplayName", "campADisplayName");
        }
        if (common.campBDisplayName() == null || common.campBDisplayName().isBlank()) {
            result.addMissing("common.campBDisplayName", "campBDisplayName");
        }
        validateVoicePack(result, "common.campAVoicePack", "campAVoicePack", common.campAVoicePack());
        validateVoicePack(result, "common.campBVoicePack", "campBVoicePack", common.campBVoicePack());
        validateGameplayPlayerState(result, definition);
        validateRoomViewPosition(result, "common.endViewPosition", "endViewPosition", resultConfig.endViewPosition());
    }

    private static void validateConquest(RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var conquest = definition.conquestConfig();
        var equipment = conquest.equipmentConfig();
        var capture = conquest.captureConfig();
        validateRoomViewPosition(result, "conquest.deployViewPosition", "deployViewPosition", conquest.deployViewPosition());
        validateRoomPosition(result, "conquest.teamDeployIconCampA", "teamDeployIconCampA", conquest.teamDeployIcons().get(RoomTeamId.CAMP_A));
        validateRoomPosition(result, "conquest.teamDeployIconCampB", "teamDeployIconCampB", conquest.teamDeployIcons().get(RoomTeamId.CAMP_B));
        validateRoomSpawnGroup(result, "conquest.campASpawnGroup", "campASpawnGroup", conquest.teamSpawnGroups().get(RoomTeamId.CAMP_A), true);
        validateRoomSpawnGroup(result, "conquest.campBSpawnGroup", "campBSpawnGroup", conquest.teamSpawnGroups().get(RoomTeamId.CAMP_B), true);
        if (conquest.sharedBoundary() == null) {
            result.addMissing("capture.gameBoundary", "sharedBoundary");
        }
        if (conquest.campABoundary() == null) {
            result.addMissing("capture.campAGameBoundary", "campABoundary");
        }
        if (conquest.campBBoundary() == null) {
            result.addMissing("capture.campBGameBoundary", "campBBoundary");
        }
        if (equipment.campAEquipmentSlots() == null) {
            result.addMissing("conquest.campAEquipmentSlots", "campAEquipmentSlots");
        }
        if (equipment.campBEquipmentSlots() == null) {
            result.addMissing("conquest.campBEquipmentSlots", "campBEquipmentSlots");
        }
        if (capture.mapId() == null || capture.mapId().isBlank() || !MAP_ID_PATTERN.matcher(capture.mapId()).matches()) {
            result.addMissing("capture.mapId", "mapId");
        }
        if (capture.drawDurationMinutes() <= 0) {
            result.addMissing("capture.drawDurationMinutes", "drawDurationMinutes");
        }
        if (capture.fullCaptureEndSeconds() < -1) {
            result.addMissing("capture.fullCaptureEndSeconds", "fullCaptureEndSeconds");
        }
        if (capture.captureScoreIntervalSeconds() <= 0) {
            result.addMissing("capture.captureScoreIntervalSeconds", "captureScoreIntervalSeconds");
        }
        if (capture.scoreMethod() == null || capture.scoreMethod().isBlank()) {
            result.addMissing("capture.scoreMethod", "scoreMethod");
        } else if ("SCORE_TARGET".equals(capture.scoreMethod())) {
            if (capture.targetScore() <= 0) {
                result.addMissing("capture.targetScore", "targetScore");
            }
        } else if ("TROOP_DEPLETION".equals(capture.scoreMethod())) {
            if (capture.campAInitialTroops() <= 0) {
                result.addMissing("capture.campAInitialTroops", "campAInitialTroops");
            }
            if (capture.campBInitialTroops() <= 0) {
                result.addMissing("capture.campBInitialTroops", "campBInitialTroops");
            }
        } else {
            result.addMissing("capture.scoreMethod", "scoreMethod");
        }
        if (capture.initialCaptureOwnership() == null || capture.initialCaptureOwnership().isBlank()) {
            result.addMissing("capture.initialCaptureOwnership", "initialCaptureOwnership");
        }
        if (!RoomObjectiveSupport.hasActivePoints(capture)) {
            result.addMissing("capture.activePoints", "capturePoints");
        }
    }

    private static void validateBreakthrough(RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var breakthrough = definition.breakthroughConfig();
        if (breakthrough.mapId() == null || breakthrough.mapId().isBlank() || !MAP_ID_PATTERN.matcher(breakthrough.mapId()).matches()) {
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
        validateBoundaryGroup(result, "breakthrough.campABaseBoundary", "btCampABaseBoundary", breakthrough.campABaseBoundary());
        validateBoundaryGroup(result, "breakthrough.campBBaseBoundary", "btCampBBaseBoundary", breakthrough.campBBaseBoundary());
        validateRoomSpawnGroup(result, "breakthrough.campABaseSpawnGroup", "btCampABaseSpawnGroup", breakthrough.campABaseSpawnGroup(), true);
        validateRoomSpawnGroup(result, "breakthrough.campBBaseSpawnGroup", "btCampBBaseSpawnGroup", breakthrough.campBBaseSpawnGroup(), true);
        validateRoomPosition(result, "breakthrough.campABaseDeployIconPosition", "btCampABaseDeployIconPosition", breakthrough.campABaseDeployIconPosition());
        validateRoomPosition(result, "breakthrough.campBBaseDeployIconPosition", "btCampBBaseDeployIconPosition", breakthrough.campBBaseDeployIconPosition());
        if (breakthrough.stageDurationMinutes() <= 0) {
            result.addMissing("breakthrough.stageDurationMinutes", "btStageDurationMinutes");
        }
        if (breakthrough.defenderRetreatSeconds() <= 0) {
            result.addMissing("breakthrough.defenderRetreatSeconds", "btDefenderRetreatSeconds");
        }
        if (breakthrough.troopGainPerDefenderKill() <= 0) {
            result.addMissing("breakthrough.troopGainPerDefenderKill", "btTroopGainPerDefenderKill");
        }
        if (breakthrough.maxAttackTroopsAt12Players() <= 0) {
            result.addMissing("breakthrough.maxAttackTroopsAt12Players", "btMaxAttackTroopsAt12Players");
        }
        for (int stageIndex = 1; stageIndex <= breakthrough.stageCount(); stageIndex++) {
            validateBoundaryGroup(
                result,
                "breakthrough.stage" + stageIndex + "Boundary",
                breakthroughStageBoundaryConfigId(stageIndex),
                breakthrough.stageBoundary(stageIndex)
            );
            validateRoomViewPosition(
                result,
                "breakthrough.stage" + stageIndex + "DeployViewPosition",
                breakthroughStageViewConfigId(stageIndex),
                breakthrough.stageDeployViewPosition(stageIndex)
            );
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
            validateRoomPosition(
                result,
                "breakthrough.points[" + code + "].centerPosition",
                "btCaptureCenterPosition_" + code,
                point.centerPosition()
            );
            validateRoomSpawnGroup(
                result,
                "breakthrough.points[" + code + "].innerSpawnGroup",
                "btCaptureInnerSpawnGroup_" + code,
                point.innerSpawnGroup(),
                true
            );
            validateRoomSpawnGroup(
                result,
                "breakthrough.points[" + code + "].teamOuterSpawnGroups[CAMP_A]",
                "btCaptureCampAOuterSpawnGroup_" + code,
                point.teamOuterSpawnGroups().get(RoomTeamId.CAMP_A),
                true
            );
            validateRoomSpawnGroup(
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

    private static void validateGameplayPlayerState(RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var playerState = definition.commonConfig().playerStateConfig();
        if (!Double.isFinite(playerState.invulnerableSeconds()) || playerState.invulnerableSeconds() < -1.0D) {
            result.addMissing("common.respawnInvulnerableSeconds", "invulnerableSeconds");
        }
        if (playerState.gameMode() == null || playerState.gameMode().isBlank()) {
            result.addMissing("common.playerGameMode", "gameMode");
            return;
        }
        try {
            GameType.valueOf(playerState.gameMode().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception exception) {
            result.addMissing("common.playerGameMode", "gameMode");
        }
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

    private static void validateRoomPosition(RoomConfigValidationResult result, String fieldName, String configId, RoomPosition position) {
        if (position == null) {
            result.addMissing(fieldName, configId);
        }
    }

    private static void validateRoomViewPosition(RoomConfigValidationResult result, String fieldName, String configId, RoomViewPosition position) {
        if (position == null) {
            result.addMissing(fieldName, configId);
        }
    }

    private static void validateVoicePack(RoomConfigValidationResult result, String fieldName, String configId, String voicePack) {
        if (!RoomVoicePack.isSupported(voicePack)) {
            result.addMissing(fieldName, configId);
        }
    }

    private static void validateBoundaryGroup(RoomConfigValidationResult result, String fieldName, String configId, java.util.List<RoomPosition> values) {
        if (values == null || values.size() < 3) {
            result.addMissing(fieldName, configId);
        }
    }

    private static void validateRoomSpawnGroup(RoomConfigValidationResult result, String fieldName, String configId, RoomSpawnGroupConfig group, boolean mustEnable) {
        if (group == null) {
            result.addMissing(fieldName, configId);
            return;
        }
        if (mustEnable && !group.enabled()) {
            result.addMissing(fieldName + ".enabled", configId);
        }
        if (group.spawns() == null || group.spawns().isEmpty()) {
            result.addMissing(fieldName + ".spawns", configId);
            return;
        }
        boolean hasValidPosition = false;
        for (int i = 0; i < group.spawns().size(); i++) {
            RoomPosition position = group.spawns().get(i);
            if (position == null) {
                result.addMissing(fieldName + ".spawn" + (i + 1), configId);
                continue;
            }
            hasValidPosition = true;
        }
        if (!hasValidPosition) {
            result.addMissing(fieldName + ".spawns", configId);
        }
    }
}
