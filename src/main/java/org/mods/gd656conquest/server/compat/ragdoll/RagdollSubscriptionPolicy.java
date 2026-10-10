package org.mods.gd656conquest.server.compat.ragdoll;

/** Shared defaults; view subscriptions do not alter physics or corpse lifetime. */
public final class RagdollSubscriptionPolicy {
    public static final double NEAR_DISTANCE=32, NEAR_EXIT_DISTANCE=40;
    public static final int MOVING_DIRECTORY_TICKS=4, STATIC_DIRECTORY_TICKS=40;
    public static final int VIEW_CHECK_TICKS=4, SUBSCRIPTION_LEASE_TICKS=80;
    public static final int HIDDEN_GRACE_TICKS=10, CLIENT_CACHE_TICKS=40;
    public static final int MAX_REQUEST_ENTRIES=64, MAX_DIRECTORY_ENTRIES=1024;
    public static final int REQUEST_RETRY_TICKS=8, REQUEST_RENEW_TICKS=20;
    public static final int VISIBILITY_SEGMENTS_PER_TICK=16;
    public static final double VISIBILITY_SEGMENT_LENGTH=64;
    public static final long VISIBILITY_BUDGET_NANOS=1_000_000L;
    private RagdollSubscriptionPolicy() {}
    public static boolean heartbeatDue(int tick,long generation) { return Math.floorMod(tick,10)==Math.floorMod(generation,10); }
}
