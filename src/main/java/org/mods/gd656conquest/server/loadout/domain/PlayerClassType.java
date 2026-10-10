package org.mods.gd656conquest.server.loadout.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum PlayerClassType {
    ASSAULT(1, "assault", "医护兵"),
    SUPPORT(2, "support", "支援兵"),
    ENGINEER(3, "engineer", "突击兵"),
    RECON(4, "recon", "侦察兵");

    private final int digit;
    private final String key;
    private final String displayName;

    PlayerClassType(int digit, String key, String displayName) {
        this.digit = digit;
        this.key = key;
        this.displayName = displayName;
    }

    public int getDigit() {
        return digit;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PlayerClassType fromInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (PlayerClassType type : values()) {
            if (type.key.equals(normalized) || String.valueOf(type.digit).equals(normalized)) {
                return type;
            }
            if ("突击兵".equals(normalized) && type == ASSAULT) {
                return type;
            }
            if (("support".equals(normalized) || "medic".equals(normalized) || "支援兵".equals(normalized) || "医疗兵".equals(normalized))
                && type == SUPPORT) {
                return type;
            }
            if ("工程兵".equals(normalized) && type == ENGINEER) {
                return type;
            }
            if ("侦察兵".equals(normalized) && type == RECON) {
                return type;
            }
        }
        return null;
    }

    public static PlayerClassType defaultType() {
        return ASSAULT;
    }

    public static List<String> suggestions() {
        List<String> list = new ArrayList<>();
        for (PlayerClassType type : values()) {
            list.add(type.key);
            list.add(type.displayName);
        }
        return list;
    }

    public static Set<Integer> parseAvailableDigits(int rawDigits) {
        Set<Integer> digits = new LinkedHashSet<>();
        if (rawDigits <= 0) {
            return digits;
        }
        String text = String.valueOf(rawDigits);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isDigit(ch)) {
                continue;
            }
            int digit = ch - '0';
            if (digit >= 1 && digit <= 4) {
                digits.add(digit);
            }
        }
        return digits;
    }
}
