package tests;

import org.mods.gd656conquest.client.room.RagdollVisibilityMath;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSubscriptionPolicy;

public final class BodyPolicyCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static boolean visible(double x, double y, double z, double radius) {
        return RagdollVisibilityMath.inView(x, y, z, radius, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1);
    }
    public static void main(String[] args) {
        check(visible(0, 0, 10_000, 1), "Visible distant body has no arbitrary distance cutoff");
        check(!visible(0, 0, -10, 1), "Body behind camera is excluded");
        check(!visible(20, 0, 10, 1), "Body beyond side plane is excluded");
        check(!visible(0, 20, 10, 1), "Body beyond vertical plane is excluded");
        check(visible(11, 0, 10, 1), "Body bounds intersecting side plane are retained");
        check(visible(0, 0, -0.5, 1), "Bounds surrounding camera are retained");
        check(RagdollSubscriptionPolicy.NEAR_EXIT_DISTANCE > RagdollSubscriptionPolicy.NEAR_DISTANCE,
            "Near subscriptions have exit hysteresis");
        check(RagdollSubscriptionPolicy.REQUEST_RENEW_TICKS < RagdollSubscriptionPolicy.SUBSCRIPTION_LEASE_TICKS,
            "Normal renewal occurs before lease expiry");
        for (long generation : new long[]{1, 2, 10, Long.MAX_VALUE}) {
            int heartbeats = 0;
            for (int tick = 0; tick < 100; tick++) if (RagdollSubscriptionPolicy.heartbeatDue(tick, generation)) heartbeats++;
            check(heartbeats == 10, "Every generation sends one heartbeat per ten ticks");
        }
        check(!RagdollSubscriptionPolicy.heartbeatDue(1, 2), "Different generations stagger heartbeats");
        System.out.println("PASS body visibility / subscription policy: " + checks);
    }
}
