package org.mods.gd656conquest.client.room;

/** Frame-rate-independent filter transition and voice cooldown, separate from warning text. */
public final class BoundaryPresentation {
    public static final float FADE_SECONDS = .10F;
    public static final long VOICE_DURATION_NANOS = 1_405_333_334L;
    public static final long VOICE_COOLDOWN_NANOS = VOICE_DURATION_NANOS + 500_000_000L;
    private float strength;
    private long nextVoice;
    public float strength() { return strength; }
    public void advance(boolean active, float dt) {
        float delta = Math.max(0, dt) / FADE_SECONDS;
        strength = active ? Math.min(1, strength + delta) : Math.max(0, strength - delta);
    }
    public boolean tryVoice(long now) {
        if (now < nextVoice) return false;
        nextVoice = now + VOICE_COOLDOWN_NANOS;
        return true;
    }
    public void reset() { strength = 0; nextVoice = 0; }
}
