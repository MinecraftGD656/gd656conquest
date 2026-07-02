package org.mods.gd656conquest.server.room.model;

import java.util.Locale;

public record RoomPlayerStateConfig(String gameMode, double invulnerableSeconds, double revivedInvulnerableSeconds) {
    public RoomPlayerStateConfig {
        gameMode = normalizeGameMode(gameMode);
        invulnerableSeconds = Double.isFinite(invulnerableSeconds) ? invulnerableSeconds : 0.0D;
        revivedInvulnerableSeconds = Double.isFinite(revivedInvulnerableSeconds) ? revivedInvulnerableSeconds : 2.0D;
    }

    public static RoomPlayerStateConfig defaults() {
        return new RoomPlayerStateConfig("adventure", 0.0D, 2.0D);
    }

    private static String normalizeGameMode(String gameMode) {
        String normalized = gameMode == null ? "" : gameMode.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "adventure" : normalized;
    }
}
