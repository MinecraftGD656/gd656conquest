package org.mods.gd656conquest.server.room.model;

/** Nullable components migrate existing room files without changing their other rules. */
public record RoomSuppressionConfig(Boolean enabled, Double radiusBlocks, Double threshold,
    Double damageScale, Double shotCap, Double decayDelaySeconds, Double decayPerSecond,
    Double recoverySeconds, Double vignetteOpacity) {
    public RoomSuppressionConfig {
        enabled = enabled == null ? true : enabled;
        radiusBlocks = number(radiusBlocks, 2, .1, 8);
        threshold = number(threshold, 60, 1, 100);
        damageScale = number(damageScale, 6, .01, 100);
        shotCap = number(shotCap, 40, .01, 100);
        decayDelaySeconds = number(decayDelaySeconds, 1, 0, 60);
        decayPerSecond = number(decayPerSecond, 40, .01, 1000);
        recoverySeconds = number(recoverySeconds, 2, 0, 60);
        vignetteOpacity = number(vignetteOpacity, .25, 0, .6);
    }
    private static double number(Double value, double fallback, double min, double max) {
        return value == null || !Double.isFinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }
    public static RoomSuppressionConfig defaults() {
        return new RoomSuppressionConfig(null,null,null,null,null,null,null,null,null);
    }
}
