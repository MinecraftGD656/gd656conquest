package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Locale;

public enum RoomTroopBalanceReference {
    ATTACKER("根据进攻方计算"),
    DEFENDER("根据防守方计算");

    private final String displayName;

    RoomTroopBalanceReference(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static RoomTroopBalanceReference defaultValue() {
        return DEFENDER;
    }

    public static List<String> suggestions() {
        return List.of(DEFENDER.displayName(), ATTACKER.displayName());
    }

    public static RoomTroopBalanceReference of(String raw) {
        String normalized = normalize(raw);
        return switch (normalized) {
            case "ATTACKER", "ATTACK", "OFFENSE", "根据进攻方计算", "进攻方", "ATTACKER_SIDE" -> ATTACKER;
            case "DEFENDER", "DEFENSE", "DEFEND", "根据防守方计算", "防守方", "DEFENDER_SIDE" -> DEFENDER;
            default -> throw new IllegalArgumentException("兵力计算方式必须为“根据进攻方计算”或“根据防守方计算”: " + raw);
        };
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
