package org.mods.gd656conquest.server.room.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record RoomCaptureConfig(
    Map<String, RoomCapturePointDefinition> points,
    String mapId,
    String campADisplayName,
    String campBDisplayName,
    int drawDurationMinutes,
    int fullCaptureEndSeconds,
    ConquestFullCaptureResponseMode fullCaptureResponseMode,
    int antiBaseNukeTriggerSeconds,
    int antiBaseNukeRespawnDelaySeconds,
    int antiBaseNukeTriggerCooldownSeconds,
    int targetScore,
    int captureScoreIntervalSeconds,
    String scoreMethod,
    int campAInitialTroops,
    int campBInitialTroops,
    String initialCaptureOwnership
) {
    public RoomCaptureConfig {
        Objects.requireNonNull(points, "points");
        points = Map.copyOf(new LinkedHashMap<>(points));
        mapId = normalizeMapId(mapId);
        campADisplayName = normalizeDisplayName(campADisplayName, "CampA");
        campBDisplayName = normalizeDisplayName(campBDisplayName, "CampB");
        drawDurationMinutes = Math.max(0, drawDurationMinutes);
        fullCaptureEndSeconds = Math.max(-1, fullCaptureEndSeconds);
        fullCaptureResponseMode = fullCaptureResponseMode == null ? ConquestFullCaptureResponseMode.defaultValue() : fullCaptureResponseMode;
        antiBaseNukeTriggerSeconds = antiBaseNukeTriggerSeconds <= 0 ? 60 : antiBaseNukeTriggerSeconds;
        antiBaseNukeRespawnDelaySeconds = antiBaseNukeRespawnDelaySeconds <= 0 ? 30 : antiBaseNukeRespawnDelaySeconds;
        antiBaseNukeTriggerCooldownSeconds = antiBaseNukeTriggerCooldownSeconds <= 0 ? 60 : antiBaseNukeTriggerCooldownSeconds;
        targetScore = Math.max(0, targetScore);
        captureScoreIntervalSeconds = Math.max(0, captureScoreIntervalSeconds);
        scoreMethod = normalizeScoreMethod(scoreMethod);
        campAInitialTroops = Math.max(0, campAInitialTroops);
        campBInitialTroops = Math.max(0, campBInitialTroops);
        initialCaptureOwnership = normalizeInitialCaptureOwnership(initialCaptureOwnership);
    }

    public static RoomCaptureConfig defaults() {
        return new RoomCaptureConfig(
            Map.of(),
            "",
            "CampA",
            "CampB",
            0,
            -1,
            ConquestFullCaptureResponseMode.defaultValue(),
            60,
            30,
            60,
            0,
            0,
            "",
            0,
            0,
            ""
        );
    }

    public Optional<RoomCapturePointDefinition> findPoint(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(points.get(code.trim().toUpperCase(java.util.Locale.ROOT)));
    }

    private static String normalizeDisplayName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeMapId(String mapId) {
        return mapId == null ? "" : mapId.trim();
    }

    private static String normalizeScoreMethod(String scoreMethod) {
        return scoreMethod == null ? "" : scoreMethod.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizeInitialCaptureOwnership(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
