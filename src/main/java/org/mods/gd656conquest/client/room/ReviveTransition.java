package org.mods.gd656conquest.client.room;

/** One camera-owned timeline for successful revival; no spawn/death trigger. */
public final class ReviveTransition {
    public static final double CAMERA_SECONDS = .28;
    public static final double PULSE_HOLD_SECONDS = .10;
    public static final double PULSE_SECONDS = 1.40;
    public static final double COLOR_RECOVERY_SECONDS = .08;
    public static final float EXPOSURE_EV = 3.1F;
    public static final float BLOOM_GAIN = 1.40F;
    private ReviveTransition() {}

    public static double easeOut(double t) {
        if (!Double.isFinite(t)) return 0;
        t = Math.max(0, Math.min(1, t));
        return 1 - (1-t)*(1-t)*(1-t);
    }
    public static double cameraProgress(double seconds) { return easeOut(seconds / CAMERA_SECONDS); }
    public static double colorProgress(double seconds) { return easeOut(seconds / COLOR_RECOVERY_SECONDS); }
    public static float pulse(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds >= PULSE_SECONDS) return 0;
        // Hold the immediate peak, then keep a stronger early pulse with a smooth finish.
        double t = Math.max(0, (seconds - PULSE_HOLD_SECONDS) / (PULSE_SECONDS - PULSE_HOLD_SECONDS));
        return (float)(1 - t * t * (3 - 2 * t));
    }
}
