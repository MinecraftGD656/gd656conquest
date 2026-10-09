package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

public record RoomCapturePointDefinition(
    String code,
    String displayName,
    RoomPosition centerPosition,
    RoomSpawnGroupConfig innerSpawnGroup,
    RoomSpawnGroupConfig outerSpawnGroup,
    Map<RoomTeamId, RoomSpawnGroupConfig> teamOuterSpawnGroups,
    int stageIndex
) {
    public RoomCapturePointDefinition {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(innerSpawnGroup, "innerSpawnGroup");
        Objects.requireNonNull(outerSpawnGroup, "outerSpawnGroup");
        teamOuterSpawnGroups = normalizeTeamOuterSpawnGroups(teamOuterSpawnGroups);
        stageIndex = Math.max(0, Math.min(6, stageIndex));
        code = code.trim().toUpperCase(Locale.ROOT);
        displayName = displayName.trim().isEmpty() ? code : displayName.trim();
    }

    public RoomCapturePointDefinition(
        String code,
        String displayName,
        RoomPosition centerPosition,
        RoomSpawnGroupConfig innerSpawnGroup,
        RoomSpawnGroupConfig outerSpawnGroup
    ) {
        this(code, displayName, centerPosition, innerSpawnGroup, outerSpawnGroup, Map.of(), 0);
    }

    public RoomCapturePointDefinition(
        String code,
        String displayName,
        RoomPosition centerPosition,
        RoomSpawnGroupConfig innerSpawnGroup,
        RoomSpawnGroupConfig outerSpawnGroup,
        Map<RoomTeamId, RoomSpawnGroupConfig> teamOuterSpawnGroups
    ) {
        this(code, displayName, centerPosition, innerSpawnGroup, outerSpawnGroup, teamOuterSpawnGroups, 0);
    }

    public List<RoomPosition> innerSpawns() {
        return innerSpawnGroup.spawns();
    }

    public List<RoomPosition> outerSpawns() {
        return outerSpawnGroup.spawns();
    }

    public RoomSpawnGroupConfig outerSpawnGroup(RoomTeamId teamId) {
        if (teamId == null) {
            return outerSpawnGroup;
        }
        return teamOuterSpawnGroups.getOrDefault(teamId, outerSpawnGroup);
    }

    public List<RoomPosition> outerSpawns(RoomTeamId teamId) {
        return outerSpawnGroup(teamId).spawns();
    }

    public boolean isRuntimeActive() {
        return centerPosition != null;
    }

    @Override
    public int stageIndex() {
        int inferred = inferStageIndexFromCode(code);
        return inferred > 0 ? inferred : stageIndex;
    }

    public RoomCapturePointDefinition withDisplayName(String value) {
        return new RoomCapturePointDefinition(code, value, centerPosition, innerSpawnGroup, outerSpawnGroup, teamOuterSpawnGroups, stageIndex);
    }

    public RoomCapturePointDefinition withCenterPosition(RoomPosition value) {
        return new RoomCapturePointDefinition(code, displayName, value, innerSpawnGroup, outerSpawnGroup, teamOuterSpawnGroups, stageIndex);
    }

    public RoomCapturePointDefinition withInnerSpawnGroup(RoomSpawnGroupConfig value) {
        return new RoomCapturePointDefinition(code, displayName, centerPosition, value, outerSpawnGroup, teamOuterSpawnGroups, stageIndex);
    }

    public RoomCapturePointDefinition withOuterSpawnGroup(RoomSpawnGroupConfig value) {
        return new RoomCapturePointDefinition(code, displayName, centerPosition, innerSpawnGroup, value, teamOuterSpawnGroups, stageIndex);
    }

    public RoomCapturePointDefinition withTeamOuterSpawnGroups(Map<RoomTeamId, RoomSpawnGroupConfig> value) {
        return new RoomCapturePointDefinition(code, displayName, centerPosition, innerSpawnGroup, outerSpawnGroup, value, stageIndex);
    }

    public RoomCapturePointDefinition withStageIndex(int value) {
        return new RoomCapturePointDefinition(code, displayName, centerPosition, innerSpawnGroup, outerSpawnGroup, teamOuterSpawnGroups, value);
    }

    private static int inferStageIndexFromCode(String code) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        char first = Character.toUpperCase(code.trim().charAt(0));
        if (first < 'A' || first > 'F') {
            return 0;
        }
        return first - 'A' + 1;
    }

    private static Map<RoomTeamId, RoomSpawnGroupConfig> normalizeTeamOuterSpawnGroups(Map<RoomTeamId, RoomSpawnGroupConfig> values) {
        Map<RoomTeamId, RoomSpawnGroupConfig> normalized = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<RoomTeamId, RoomSpawnGroupConfig> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Map.copyOf(normalized);
    }
}
