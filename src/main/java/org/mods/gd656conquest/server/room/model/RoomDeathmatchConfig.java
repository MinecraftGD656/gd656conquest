package org.mods.gd656conquest.server.room.model;

import java.util.List;

public record RoomDeathmatchConfig(
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
    List<RoomPosition> medicalBoxPositions
) implements IRoomGameplayConfig {
    public RoomDeathmatchConfig {
        mapId = mapId == null ? "" : mapId.trim();
        matchDurationMinutes = Math.max(0, matchDurationMinutes);
        targetScore = Math.max(0, targetScore);
        startCountdownSeconds = Math.max(0, startCountdownSeconds);
        respawnSeconds = Math.max(0, respawnSeconds);
        campABoundary = copyBoundary(campABoundary);
        campBBoundary = copyBoundary(campBBoundary);
        campASpawnGroup = normalizeSpawnGroup(campASpawnGroup);
        campBSpawnGroup = normalizeSpawnGroup(campBSpawnGroup);
        ammoBoxPositions = copyBoundary(ammoBoxPositions);
        medicalBoxPositions = copyBoundary(medicalBoxPositions);
    }

    public static RoomDeathmatchConfig defaults() {
        return new RoomDeathmatchConfig(
            "",
            0,
            0,
            10,
            5,
            null,
            List.of(),
            List.of(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults(),
            List.of(),
            List.of()
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

    private static List<RoomPosition> copyBoundary(List<RoomPosition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static RoomSpawnGroupConfig normalizeSpawnGroup(RoomSpawnGroupConfig value) {
        return value == null ? RoomSpawnGroupConfig.defaults() : value;
    }

    @Override
    public void validate(org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var deathmatch = this;
        if (deathmatch.mapId() == null || deathmatch.mapId().isBlank() || !org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.MAP_ID_PATTERN.matcher(deathmatch.mapId()).matches()) {
            result.addMissing("deathmatch.mapId", "dmMapID");
        }
        if (deathmatch.matchDurationMinutes() <= 0) {
            result.addMissing("deathmatch.matchDurationMinutes", "dmMatchDurationMinutes");
        }
        if (deathmatch.targetScore() <= 0) {
            result.addMissing("deathmatch.targetScore", "dmTargetScore");
        }
        if (deathmatch.startCountdownSeconds() <= 0) {
            result.addMissing("deathmatch.startCountdownSeconds", "dmStartCountdownSeconds");
        }
        if (deathmatch.respawnSeconds() <= 0) {
            result.addMissing("deathmatch.respawnSeconds", "dmRespawnSeconds");
        }
        if (deathmatch.deployViewPosition() == null) {
            result.addMissing("deathmatch.deployViewPosition", "dmDeployViewPosition");
        }
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campASpawnGroup", "dmCampASpawnGroup", deathmatch.campASpawnGroup(), true);
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campBSpawnGroup", "dmCampBSpawnGroup", deathmatch.campBSpawnGroup(), true);
    }
}
