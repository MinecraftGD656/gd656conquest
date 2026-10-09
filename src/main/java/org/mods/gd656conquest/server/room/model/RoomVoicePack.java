package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Locale;

public enum RoomVoicePack {
    US("US", "gd656frontlinevoice:voice.downed_help_us"),
    CN("CN", "gd656frontlinevoice:voice.downed_help_cn"),
    RU("RU", "gd656frontlinevoice:voice.downed_help_ru");

    private final String id;
    private final String downedHelpSoundId;

    RoomVoicePack(String id, String downedHelpSoundId) {
        this.id = id;
        this.downedHelpSoundId = downedHelpSoundId;
    }

    public String id() {
        return id;
    }

    public String downedHelpSoundId() {
        return downedHelpSoundId;
    }

    public static List<String> suggestions() {
        return List.of(US.id, CN.id, RU.id);
    }

    public static boolean isSupported(String rawValue) {
        return tryParse(rawValue) != null;
    }

    public static String normalizeOrThrow(String rawValue) {
        RoomVoicePack resolved = tryParse(rawValue);
        if (resolved == null) {
            throw new IllegalArgumentException("语音包必须为 US / CN / RU: " + rawValue);
        }
        return resolved.id();
    }

    public static RoomVoicePack resolveOrDefault(String rawValue) {
        RoomVoicePack resolved = tryParse(rawValue);
        return resolved == null ? US : resolved;
    }

    private static RoomVoicePack tryParse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
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
