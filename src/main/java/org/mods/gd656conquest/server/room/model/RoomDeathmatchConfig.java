package org.mods.gd656conquest.server.room.model;

import java.util.List;

public record RoomDeathmatchConfig(
    String mapId,
    int matchDurationMinutes,
    int targetScore,
    int enabledTeamCount,
    List<RoomPosition> campABoundary,
    List<RoomPosition> campBBoundary,
    List<RoomPosition> campCBoundary,
    List<RoomPosition> campDBoundary,
    List<RoomPosition> campEBoundary,
    List<RoomPosition> campFBoundary,
    List<RoomPosition> campGBoundary,
    List<RoomPosition> campHBoundary,
    RoomSpawnGroupConfig campASpawnGroup,
    RoomSpawnGroupConfig campBSpawnGroup,
    RoomSpawnGroupConfig campCSpawnGroup,
    RoomSpawnGroupConfig campDSpawnGroup,
    RoomSpawnGroupConfig campESpawnGroup,
    RoomSpawnGroupConfig campFSpawnGroup,
    RoomSpawnGroupConfig campGSpawnGroup,
    RoomSpawnGroupConfig campHSpawnGroup
) implements IRoomGameplayConfig {
    public RoomDeathmatchConfig {
        mapId = mapId == null ? "" : mapId.trim();
        matchDurationMinutes = Math.max(0, matchDurationMinutes);
        targetScore = Math.max(0, targetScore);
        enabledTeamCount = Math.max(0, Math.min(8, enabledTeamCount));
        campABoundary = copyBoundary(campABoundary);
        campBBoundary = copyBoundary(campBBoundary);
        campCBoundary = copyBoundary(campCBoundary);
        campDBoundary = copyBoundary(campDBoundary);
        campEBoundary = copyBoundary(campEBoundary);
        campFBoundary = copyBoundary(campFBoundary);
        campGBoundary = copyBoundary(campGBoundary);
        campHBoundary = copyBoundary(campHBoundary);
        campASpawnGroup = normalizeSpawnGroup(campASpawnGroup);
        campBSpawnGroup = normalizeSpawnGroup(campBSpawnGroup);
        campCSpawnGroup = normalizeSpawnGroup(campCSpawnGroup);
        campDSpawnGroup = normalizeSpawnGroup(campDSpawnGroup);
        campESpawnGroup = normalizeSpawnGroup(campESpawnGroup);
        campFSpawnGroup = normalizeSpawnGroup(campFSpawnGroup);
        campGSpawnGroup = normalizeSpawnGroup(campGSpawnGroup);
        campHSpawnGroup = normalizeSpawnGroup(campHSpawnGroup);
    }

    public static RoomDeathmatchConfig defaults() {
        return new RoomDeathmatchConfig(
            "",
            0,
            0,
            2,
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
            RoomSpawnGroupConfig.defaults()
        );
    }

    @Override
    public String mapId() {
        return mapId;
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
        if (deathmatch.enabledTeamCount() < 2 || deathmatch.enabledTeamCount() > 8) {
            result.addMissing("deathmatch.enabledTeamCount", "dmEnabledTeamCount");
        }
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campASpawnGroup", "dmCampASpawnGroup", deathmatch.campASpawnGroup(), true);
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campBSpawnGroup", "dmCampBSpawnGroup", deathmatch.campBSpawnGroup(), true);
        if (deathmatch.enabledTeamCount() >= 3) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campCSpawnGroup", "dmCampCSpawnGroup", deathmatch.campCSpawnGroup(), true);
        }
        if (deathmatch.enabledTeamCount() >= 4) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campDSpawnGroup", "dmCampDSpawnGroup", deathmatch.campDSpawnGroup(), true);
        }
        if (deathmatch.enabledTeamCount() >= 5) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campESpawnGroup", "dmCampESpawnGroup", deathmatch.campESpawnGroup(), true);
        }
        if (deathmatch.enabledTeamCount() >= 6) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campFSpawnGroup", "dmCampFSpawnGroup", deathmatch.campFSpawnGroup(), true);
        }
        if (deathmatch.enabledTeamCount() >= 7) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campGSpawnGroup", "dmCampGSpawnGroup", deathmatch.campGSpawnGroup(), true);
        }
        if (deathmatch.enabledTeamCount() >= 8) {
            org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "deathmatch.campHSpawnGroup", "dmCampHSpawnGroup", deathmatch.campHSpawnGroup(), true);
        }
    }
}
