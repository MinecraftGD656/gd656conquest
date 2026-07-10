package org.mods.gd656conquest.server.data.shelling;

import java.util.List;
import java.util.Locale;

public enum ShellingImpactType {
    EMPTY("empty", "普通"),
    LIGHTSHELL("lightshell", "轻型炮击"),
    MEDIUMSHELL("mediumshell", "中型炮击"),
    HEAVYSHELL("heavyshell", "重型炮击"),
    MISSILE("missile", "导弹"),
    SMOKESHELL("smokeshell", "烟雾弹"),
    FLAMESHELL("flameshell", "燃烧弹");

    private final String id;
    private final String displayName;

    ShellingImpactType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public static ShellingImpactType findById(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        for (ShellingImpactType value : values()) {
            if (value.id.equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    public static ShellingImpactType require(String rawValue) {
        ShellingImpactType resolved = findById(rawValue);
        if (resolved == null) {
            throw new IllegalArgumentException("Unsupported shelling impact type: " + rawValue);
        }
        return resolved;
    }

    public static List<String> suggestionIds() {
        return List.of(
            EMPTY.id,
            LIGHTSHELL.id,
            MEDIUMSHELL.id,
            HEAVYSHELL.id,
            MISSILE.id,
            SMOKESHELL.id,
            FLAMESHELL.id
        );
    }
}
