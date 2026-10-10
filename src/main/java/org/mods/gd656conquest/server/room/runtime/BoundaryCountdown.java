package org.mods.gd656conquest.server.room.runtime;

/** Deadlines belong to players. A change of vehicle can only shorten the remaining time. */
public final class BoundaryCountdown {
    private BoundaryCountdown() {}
    public static long tighten(Long deadline, long tick, int seconds) {
        long limit = tick + Math.max(0, seconds) * 20L;
        return deadline == null ? limit : Math.min(deadline, limit);
    }
    public static int remainingTicks(long deadline, long tick) {
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, deadline - tick));
    }
    public static int displaySeconds(int ticks) { return (int)((Math.max(0, ticks) + 19L) / 20L); }
}
