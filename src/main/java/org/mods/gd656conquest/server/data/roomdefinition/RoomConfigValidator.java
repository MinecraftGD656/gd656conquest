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
    public static final java.util.regex.Pattern MAP_ID_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_]+$");

    private RoomConfigValidator() {
    }

    public static RoomConfigValidationResult validate(RoomDefinition definition) {
        RoomConfigValidationResult result = new RoomConfigValidationResult();
        validateCommon(result, definition);
        if (definition == null || definition.gameplayId() == null) {
            return result;
        }
        
        definition.gameplayConfig().validate(result, definition);
        
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
        if (common.playerMaxY() <= 0) {
            result.addMissing("common.playerMaxY", "playerMaxY");
        }
        if (common.aircraftPassengerMaxY() <= 0) {
            result.addMissing("common.aircraftPassengerMaxY", "aircraftPassengerMaxY");
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
        validateOptionalRoomViewPosition(result, "common.matchLoadViewPosition", "matchLoadViewPosition", common.matchLoadViewPosition());
    }


    private static void validateGameplayPlayerState(RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var playerState = definition.commonConfig().playerStateConfig();
        if (!Double.isFinite(playerState.invulnerableSeconds()) || playerState.invulnerableSeconds() < -1.0D) {
            result.addMissing("common.respawnInvulnerableSeconds", "invulnerableSeconds");
        }
        if (!Double.isFinite(playerState.revivedInvulnerableSeconds()) || playerState.revivedInvulnerableSeconds() < -1.0D) {
            result.addMissing("common.revivedInvulnerableSeconds", "revivedInvulnerableSeconds");
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

    public static void validateRoomPosition(RoomConfigValidationResult result, String fieldName, String configId, RoomPosition position) {
        if (position == null) {
            result.addMissing(fieldName, configId);
        }
    }

    public static void validateRoomViewPosition(RoomConfigValidationResult result, String fieldName, String configId, RoomViewPosition position) {
        if (position == null) {
            result.addMissing(fieldName, configId);
        }
    }

    /** Validates a {@link RoomViewPosition} that is allowed to be null (null = not configured / disabled). */
    public static void validateOptionalRoomViewPosition(RoomConfigValidationResult result, String fieldName, String configId, RoomViewPosition position) {
        if (position == null) {
            return; // null is valid for optional fields
        }
        // Basic sanity: all coordinates should be finite
        if (!Double.isFinite(position.x()) || !Double.isFinite(position.y()) || !Double.isFinite(position.z())) {
            result.addMissing(fieldName, configId);
        }
    }

    public static void validateRoomSpawnGroup(RoomConfigValidationResult result, String fieldName, String configId, RoomSpawnGroupConfig spawnGroup, boolean checkEmpty) {
        if (spawnGroup == null || (checkEmpty && (spawnGroup.spawns() == null || spawnGroup.spawns().isEmpty()))) {
            result.addMissing(fieldName, configId);
        }
    }

    public static void validateBoundaryGroup(RoomConfigValidationResult result, String fieldName, String configId, java.util.List<RoomPosition> boundary) {
        if (boundary == null || boundary.isEmpty()) {
            result.addMissing(fieldName, configId);
        }
    }

    public static void validateVoicePack(RoomConfigValidationResult result, String fieldName, String configId, RoomVoicePack voicePack) {
        if (voicePack == null || voicePack.id() == null || voicePack.id().isBlank()) {
            // Optional, don't fail validation
        }
    }
    
    public static void validateVoicePack(RoomConfigValidationResult result, String fieldName, String configId, String voicePack) {
        if (voicePack == null || voicePack.isBlank()) {
            // Optional, don't fail validation
        }
    }
}
