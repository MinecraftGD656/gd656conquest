package org.mods.gd656conquest.server.room.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum RoomVoicePack {
    ENG("ENG"),
    FRA("FRA"),
    GER("GER"),
    ITA("ITA"),
    RUS("RUS"),
    TKR("TKR"),
    USA("USA");

    private final String id;
    private final String downedHelpSoundId;

    RoomVoicePack(String id) {
        this.id = id;
        this.downedHelpSoundId = "gd656frontvoice:tp." + id.toLowerCase(Locale.ROOT) + ".downed_help";
    }

    public String id() {
        return id;
    }

    public String downedHelpSoundId() {
        return downedHelpSoundId;
    }

    public static List<String> suggestions() {
        return Arrays.stream(values()).map(RoomVoicePack::id).toList();
    }

    public static String normalizeForStorage(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
        // Preserve the aliases used by FrontVoice 0.0.11-meow1 when loading old rooms.
        // This BF1 voice pack has no Chinese audio; its legacy CN alias selects ENG.
        return switch (normalized) {
            case "US" -> USA.id;
            case "CN" -> ENG.id;
            case "RU" -> RUS.id;
            default -> normalized;
        };
    }

    public static boolean isSupported(String rawValue) {
        return tryParse(rawValue) != null;
    }

    public static String normalizeOrThrow(String rawValue) {
        RoomVoicePack resolved = tryParse(rawValue);
        if (resolved == null) {
            throw new IllegalArgumentException("语音包必须为 " + String.join(" / ", suggestions()) + ": " + rawValue);
        }
        return resolved.id();
    }

    public static RoomVoicePack resolveOrDefault(String rawValue) {
        RoomVoicePack resolved = tryParse(rawValue);
        return resolved == null ? USA : resolved;
    }

    private static RoomVoicePack tryParse(String rawValue) {
        String normalized = normalizeForStorage(rawValue);
        if (normalized.isEmpty()) {
            return null;
        }
        for (RoomVoicePack value : values()) {
            if (value.id.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
