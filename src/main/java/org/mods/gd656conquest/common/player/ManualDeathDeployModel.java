package org.mods.gd656conquest.common.player;

/** A confirmed death can wait indefinitely; only a fresh press requests deployment. */
public final class ManualDeathDeployModel {
    private long deathId, receivedTick, requestedTick;
    private int remainingTicks;
    private boolean active, jumpWasDown = true, requested;
    public void begin(long id) {
        if (id <= deathId) return;
        deathId = id; active = false; requested = false; jumpWasDown = true;
    }
    public boolean accept(long id, int ticks, long now, boolean jumpDown) {
        if (id != deathId || ticks < 0 || requested && now - requestedTick < 40) return false;
        // A continued authoritative waiting snapshot means the view did not open; allow a new press.
        requested = false;
        if (!active) jumpWasDown = jumpDown;
        active = true; remainingTicks = ticks; receivedTick = now; return true;
    }
    public boolean active() { return active; }
    public long deathId() { return deathId; }
    public int seconds(long now) { return (int)Math.ceil(Math.max(0, remainingTicks - Math.max(0, now - receivedTick)) / 20D); }
    public boolean press(boolean down, boolean allowed, long now) {
        boolean edge = down && !jumpWasDown; jumpWasDown = down;
        if (!active || requested || !allowed || !edge) return false;
        requested = true; requestedTick = now; active = false; return true;
    }
    public void close() { active = false; requested = true; requestedTick = Long.MAX_VALUE; jumpWasDown = true; }
    public void reset() { deathId = receivedTick = remainingTicks = 0; close(); }
    public static int remaining(long readyTick, long tick) { return (int)Math.min(Integer.MAX_VALUE, Math.max(0, readyTick - tick)); }
}
