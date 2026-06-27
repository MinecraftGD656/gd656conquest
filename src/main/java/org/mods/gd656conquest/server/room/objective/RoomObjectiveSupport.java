package org.mods.gd656conquest.server.room.objective;

import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RoomObjectiveSupport {
    private RoomObjectiveSupport() {
    }

    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    public static RoomCapturePointDefinition fallbackPoint(String code) {
        String normalizedCode = normalizeCode(code);
        return new RoomCapturePointDefinition(
            normalizedCode,
            normalizedCode,
            null,
            RoomSpawnGroupConfig.defaults(),
            RoomSpawnGroupConfig.defaults()
        );
    }

    public static List<RoomCapturePointDefinition> activePoints(RoomCaptureConfig config) {
        List<RoomCapturePointDefinition> activePoints = new ArrayList<>();
        if (config == null || config.points() == null) {
            return activePoints;
        }
        for (RoomCapturePointDefinition point : config.points().values()) {
            if (point != null && point.isRuntimeActive()) {
                activePoints.add(point);
            }
        }
        return List.copyOf(activePoints);
    }

    public static Set<String> activeCodes(RoomCaptureConfig config) {
        Set<String> codes = new LinkedHashSet<>();
        for (RoomCapturePointDefinition point : activePoints(config)) {
            String code = normalizeCode(point.code());
            if (!code.isBlank()) {
                codes.add(code);
            }
        }
        return codes;
    }

    public static boolean hasActivePoints(RoomCaptureConfig config) {
        return !activePoints(config).isEmpty();
    }
}
