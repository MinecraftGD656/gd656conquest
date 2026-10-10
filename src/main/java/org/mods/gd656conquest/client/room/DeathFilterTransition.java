package org.mods.gd656conquest.client.room;

/** Frame-rate independent smoothstep, retaining continuity if revive interrupts entry. */
public final class DeathFilterTransition {
    public static final float MAX_DESATURATION = .96F;
    // Match the existing impact + reveal movement (0.32 + 1.10 seconds).
    public static final float FADE_IN_SECONDS = (float) DownedCameraMath.DOWNED_REVEAL_SECONDS;
    // Ordinary deployment/state clears retain the existing fade. Successful revival uses its own timeline.
    public static final float FADE_OUT_SECONDS = .20F;
    public static final float DEATH_CONTRAST = 1.03F;
    private boolean dying;
    private float strength, start, elapsed, reviveStart;

    public void beginReviveRecovery() {
        dying = false; reviveStart = start = strength; elapsed = 0;
    }
    public void recoverRevive(double seconds) {
        strength = reviveStart * (float)(1 - ReviveTransition.colorProgress(seconds));
        // A canceled revival can continue the ordinary fade from the currently displayed color.
        start = strength; elapsed = 0;
    }

    public float advance(boolean death, float seconds) {
        if (death != dying) { dying = death; start = strength; elapsed = 0; }
        if (Float.isFinite(seconds) && seconds > 0) elapsed += seconds;
        float duration = dying ? FADE_IN_SECONDS : FADE_OUT_SECONDS;
        float t = Math.min(1, elapsed / duration);
        float smooth = t * t * (3 - 2 * t);
        strength = start + ((dying ? MAX_DESATURATION : 0) - start) * smooth;
        return strength;
    }
    public float strength() { return strength; }
    public void reset() { dying = false; strength = start = elapsed = reviveStart = 0; }
}
