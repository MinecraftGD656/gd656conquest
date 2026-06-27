package org.mods.gd656conquest.server.loadout.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum CustomLoadoutSlot {
    PRIMARY_WEAPON(1, "PrimaryWeapon"),
    SECONDARY_WEAPON(2, "SecondaryWeapon"),
    MELEE(3, "Melee"),
    GADGET_1(4, "Gadget1"),
    GADGET_2(5, "Gadget2"),
    THROWABLE(6, "Throwable");

    private final int index;
    private final String key;

    CustomLoadoutSlot(int index, String key) {
        this.index = index;
        this.key = key;
    }

    public int getIndex() {
        return index;
    }

    public String getKey() {
        return key;
    }

    public static CustomLoadoutSlot fromIndex(int index) {
        for (CustomLoadoutSlot slot : values()) {
            if (slot.index == index) {
                return slot;
            }
        }
        return null;
    }

    public static CustomLoadoutSlot fromInput(String input) {
        if (input == null) {
            return null;
        }
        String raw = stripWrappingQuotes(input.trim());
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return fromIndex(Integer.parseInt(raw));
        } catch (Exception ignored) {
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (CustomLoadoutSlot slot : values()) {
            if (slot.key.toLowerCase(Locale.ROOT).equals(normalized)) {
                return slot;
            }
        }
        return switch (normalized) {
            case "主武器", "主武", "主枪" -> PRIMARY_WEAPON;
            case "副武器", "副武", "副枪" -> SECONDARY_WEAPON;
            case "近战", "近战武器" -> MELEE;
            case "装备1", "道具1", "技能1", "gadget1" -> GADGET_1;
            case "装备2", "道具2", "技能2", "gadget2" -> GADGET_2;
            case "投掷", "投掷物" -> THROWABLE;
            default -> null;
        };
    }

    public static List<String> localizedNameSuggestions() {
        return List.of(
            "PrimaryWeapon", "SecondaryWeapon", "Melee", "Gadget1", "Gadget2", "Throwable",
            "主武器", "副武器", "近战武器", "装备1", "装备2", "投掷物"
        );
    }

    public static List<String> nameSuggestions() {
        return Arrays.stream(values()).map(CustomLoadoutSlot::getKey).toList();
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2) {
            return trimmed;
        }
        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'') || (first == '“' && last == '”')) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
