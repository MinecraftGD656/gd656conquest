package org.mods.gd656conquest.client.hud;

public final class ClientHudDebugPreviewState {
    private static String mode = "-";
    private static long previewExpiresAtMs;

    private ClientHudDebugPreviewState() {
    }

    public static void startPreview(String previewMode, int durationMs) {
        mode = previewMode == null || previewMode.isBlank() ? "CONQUEST" : previewMode.trim().toUpperCase(java.util.Locale.ROOT);
        previewExpiresAtMs = System.currentTimeMillis() + Math.max(1000L, durationMs);
    }

    public static boolean isActive() {
        return previewExpiresAtMs > System.currentTimeMillis();
    }

    public static String mode() {
        return mode;
    }

    public static void reset() {
        mode = "-";
        previewExpiresAtMs = 0L;
    }
}
