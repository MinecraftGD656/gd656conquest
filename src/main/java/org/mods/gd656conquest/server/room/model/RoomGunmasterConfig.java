package org.mods.gd656conquest.server.room.model;

import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult;
import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator;

import java.util.List;

public record RoomGunmasterConfig(
    String mapId,
    int matchDurationMinutes,
    int targetScore,
    int startCountdownSeconds,
    int respawnSeconds,
    RoomViewPosition deployViewPosition,
    List<RoomPosition> campABoundary,
    List<RoomPosition> campBBoundary,
    RoomSpawnGroupConfig campASpawnGroup,
    RoomSpawnGroupConfig campBSpawnGroup,
    List<RoomPosition> ammoBoxPositions,
    List<RoomPosition> medicalBoxPositions,
    RoomGunmasterGroupingMode groupingMode,
    List<RoomPosition> soloSpawnPositions,
    List<RoomPosition> soloBoundary,
    List<String> initialWeaponPool,
    List<String> middleWeaponPool,
    List<String> finalWeaponPool,
    int enabledMiddleWeaponCount,
    int killsPerWeaponSwitch
) implements IRoomGameplayConfig {
    public RoomGunmasterConfig {
        mapId = mapId == null ? "" : mapId.trim();
        matchDurationMinutes = Math.max(0, matchDurationMinutes);
        targetScore = Math.max(0, targetScore);
        startCountdownSeconds = Math.max(0, startCountdownSeconds);
        respawnSeconds = Math.max(0, respawnSeconds);
        deployViewPosition = deployViewPosition == null ? defaultDeployViewPosition() : deployViewPosition;
        campABoundary = copyBoundary(campABoundary);
        campBBoundary = copyBoundary(campBBoundary);
        campASpawnGroup = normalizeSpawnGroup(campASpawnGroup);
        campBSpawnGroup = normalizeSpawnGroup(campBSpawnGroup);
        ammoBoxPositions = copyBoundary(ammoBoxPositions);
        medicalBoxPositions = copyBoundary(medicalBoxPositions);
        groupingMode = groupingMode == null ? RoomGunmasterGroupingMode.TEAM : groupingMode;
        soloSpawnPositions = copyBoundary(soloSpawnPositions);
        soloBoundary = copyBoundary(soloBoundary);
        initialWeaponPool = copyStringList(initialWeaponPool);
        middleWeaponPool = copyStringList(middleWeaponPool);
        finalWeaponPool = copyStringList(finalWeaponPool);
        enabledMiddleWeaponCount = Math.max(0, enabledMiddleWeaponCount);
        killsPerWeaponSwitch = Math.max(0, killsPerWeaponSwitch);
    }

    public static RoomGunmasterConfig defaults() {
        return new RoomGunmasterConfig(
            "",
            0,
            0,
            10,
            5,
            defaultDeployViewPosition(),
            List.of(),
            List.of(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            List.of(),
            List.of(),
            RoomGunmasterGroupingMode.TEAM,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            10,
            1
        );
    }

    @Override
    public String mapId() {
        return mapId;
    }

    @Override
    public String campADisplayName(RoomCommonConfig commonConfig) {
        return commonConfig == null ? "CampA" : commonConfig.campADisplayName();
    }

    @Override
    public String campBDisplayName(RoomCommonConfig commonConfig) {
        return commonConfig == null ? "CampB" : commonConfig.campBDisplayName();
    }

    @Override
    public List<RoomCapturePointDefinition> definedObjectivePoints() {
        return List.of();
    }

    public RoomDeathmatchConfig asArenaConfig() {
        return new RoomDeathmatchConfig(
            mapId,
            matchDurationMinutes,
            targetScore,
            startCountdownSeconds,
            respawnSeconds,
            deployViewPosition,
            campABoundary,
            campBBoundary,
            campASpawnGroup,
            campBSpawnGroup,
            ammoBoxPositions,
            medicalBoxPositions,
            RoomDeathmatchMode.TEAM,
            List.of(),
            List.of(),
            null
        );
    }

    public boolean isSoloGrouping() {
        return groupingMode == RoomGunmasterGroupingMode.SOLO;
    }

    @Override
    public void validate(RoomConfigValidationResult result, RoomDefinition definition) {
        if (mapId == null || mapId.isBlank() || !RoomConfigValidator.MAP_ID_PATTERN.matcher(mapId).matches()) {
            result.addMissing("gunmaster.mapId", "gmMapID");
        }
        if (matchDurationMinutes <= 0) {
            result.addMissing("gunmaster.matchDurationMinutes", "gmMatchDurationMinutes");
        }
        if (targetScore <= 0) {
            result.addMissing("gunmaster.targetScore", "gmTargetScore");
        }
        if (startCountdownSeconds <= 0) {
            result.addMissing("gunmaster.startCountdownSeconds", "gmStartCountdownSeconds");
        }
        if (respawnSeconds <= 0) {
            result.addMissing("gunmaster.respawnSeconds", "gmRespawnSeconds");
        }
        if (groupingMode == null) {
            result.addMissing("gunmaster.groupingMode", "gmPlayerGroupingMode");
        }
        if (isSoloGrouping()) {
            if (soloSpawnPositions == null || soloSpawnPositions.isEmpty()) {
                result.addMissing("gunmaster.soloSpawnPositions", "gmSoloSpawnPositions");
            }
            if (soloBoundary == null || soloBoundary.isEmpty()) {
                result.addMissing("gunmaster.soloBoundary", "gmSoloBoundary");
            }
        } else {
            RoomConfigValidator.validateRoomSpawnGroup(result, "gunmaster.campASpawnGroup", "gmCampASpawnGroup", campASpawnGroup, true);
            RoomConfigValidator.validateRoomSpawnGroup(result, "gunmaster.campBSpawnGroup", "gmCampBSpawnGroup", campBSpawnGroup, true);
        }
        if (initialWeaponPool.isEmpty()) {
            result.addMissing("gunmaster.initialWeaponPool", "gmInitialWeaponPool");
        }
        if (finalWeaponPool.isEmpty()) {
            result.addMissing("gunmaster.finalWeaponPool", "gmFinalWeaponPool");
        }
        if (enabledMiddleWeaponCount > 0 && middleWeaponPool.isEmpty()) {
            result.addMissing("gunmaster.middleWeaponPool", "gmMiddleWeaponPool");
        }
        if (killsPerWeaponSwitch <= 0) {
            result.addMissing("gunmaster.killsPerWeaponSwitch", "gmKillsPerWeaponSwitch");
        }
    }

    private static List<RoomPosition> copyBoundary(List<RoomPosition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static RoomSpawnGroupConfig normalizeSpawnGroup(RoomSpawnGroupConfig value) {
        return value == null ? RoomSpawnGroupConfig.defaults() : value;
    }

    private static List<String> copyStringList(List<String> values) {
        return values == null ? List.of() : values.stream()
            .map(value -> value == null ? "" : value.trim())
            .filter(value -> !value.isBlank())
            .toList();
    }

    private static RoomViewPosition defaultDeployViewPosition() {
        return new RoomViewPosition(0, 100.0D, 0, 0.0F, 0.0F);
    }

}
