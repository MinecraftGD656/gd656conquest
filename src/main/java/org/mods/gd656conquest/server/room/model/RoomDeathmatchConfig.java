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
}
