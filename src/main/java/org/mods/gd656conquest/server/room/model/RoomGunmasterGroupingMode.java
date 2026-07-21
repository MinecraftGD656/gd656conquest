package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Locale;

public enum RoomGunmasterGroupingMode {
    TEAM,
    SOLO;

    public static RoomGunmasterGroupingMode of(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TEAM", "TEAM_BATTLE", "TEAMBASED" -> TEAM;
            case "SOLO", "INDIVIDUAL", "FFA", "FREE_FOR_ALL", "FREEFORALL" -> SOLO;
            default -> throw new IllegalArgumentException("不支持的枪神玩家编组模式: " + raw);
        };
    }

    public static List<String> suggestions() {
        return List.of("TEAM", "SOLO");
    }
}
