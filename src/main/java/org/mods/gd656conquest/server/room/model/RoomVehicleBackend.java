package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Locale;

public enum RoomVehicleBackend {
    YWZJ("YWZJ"),
    SUPERBWARFARE("SUPERBWARFARE");

    private final String value;

    RoomVehicleBackend(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RoomVehicleBackend defaultValue() {
        return YWZJ;
    }

    public static List<String> suggestions() {
        return List.of(YWZJ.value(), SUPERBWARFARE.value());
    }

    public static RoomVehicleBackend of(String raw) {
        String normalized = normalize(raw);
        return switch (normalized) {
            case "YWZJ", "YWZJ_VEHICLE", "永无止境" -> YWZJ;
            case "SUPERBWARFARE", "SBW", "卓越前线" -> SUPERBWARFARE;
            default -> throw new IllegalArgumentException("不支持的载具后端: " + raw);
        };
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
