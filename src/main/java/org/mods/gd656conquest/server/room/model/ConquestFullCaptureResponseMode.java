package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Locale;

public enum ConquestFullCaptureResponseMode {
    NONE,
    COUNTDOWN_END,
    NUCLEAR_REDEPLOY;

    public static ConquestFullCaptureResponseMode defaultValue() {
        return COUNTDOWN_END;
    }

    public static List<String> suggestions() {
        return List.of("NONE", "COUNTDOWN_END", "NUCLEAR_REDEPLOY");
    }

    public static ConquestFullCaptureResponseMode parse(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "", "COUNTDOWN_END", "COUNTDOWN", "TIMER", "倒计时结束对局" -> COUNTDOWN_END;
            case "NONE", "DISABLED", "OFF", "有无", "无" -> NONE;
            case "NUCLEAR_REDEPLOY", "NUKE", "NUCLEAR", "核弹" -> NUCLEAR_REDEPLOY;
            default -> throw new IllegalArgumentException("不支持的对局面对压家的处理方式: " + rawValue);
        };
    }
}
