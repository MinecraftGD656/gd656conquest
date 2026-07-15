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
    RoomSpawnGroupConfig campBSpawnGroup
) implements IRoomGameplayConfig {
    public RoomDeathmatchConfig {
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
    }

    public static RoomDeathmatchConfig defaults() {
        return new RoomDeathmatchConfig(
            "",
            0,
            0,
            10,
            5,
            defaultDeployViewPosition(),
            List.of(),
            List.of(),
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults()
        );
    }

    @Override
    public String mapId() {
        return mapId;
    }

    @Override
    public String campADisplayName(RoomCommonConfig commonConfig) {
        return "蓝队";
    }

    @Override
    public String campBDisplayName(RoomCommonConfig commonConfig) {
        return "红队";
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

    private static RoomViewPosition defaultDeployViewPosition() {
        return new RoomViewPosition(0, 100.0D, 0, 0.0F, 0.0F);
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
