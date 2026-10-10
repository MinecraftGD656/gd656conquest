package org.mods.gd656conquest.server.room.entry;

import org.mods.gd656conquest.common.player.DeathCause;

/** One deterministic result. Ownership has already been resolved by the room death entrypoint. */
public final class DeathCauseClassifier {
    public enum SystemDeath { NONE, COMMAND, BOUNDARY, REDEPLOY }
    public enum Ownership { ENEMY, SELF, OTHER, NONE }
    public record Context(SystemDeath system, Ownership ownership, String damageType, boolean fire) {}
    private DeathCauseClassifier() {}

    public static DeathCause classify(Context context) {
        if (context == null) return DeathCause.KILLED_IN_ACTION;
        if (context.system() == SystemDeath.COMMAND) return DeathCause.COMMAND_KILL;
        if (context.system() == SystemDeath.BOUNDARY) return DeathCause.DESERTION;
        if (context.system() == SystemDeath.REDEPLOY) return DeathCause.REDEPLOY;
        // Deliberately includes the existing recent-attacker fallback, as requested.
        if (context.ownership() == Ownership.ENEMY) return DeathCause.PLAYER_KILL;
        if (context.ownership() == Ownership.SELF) return DeathCause.ACCIDENT;
        String type = context.damageType() == null ? "" : context.damageType();
        return switch (type) {
            case "minecraft:fall" -> DeathCause.FALL;
            case "minecraft:drown" -> DeathCause.DROWNED;
            case "minecraft:falling_block", "minecraft:falling_anvil", "minecraft:falling_stalactite",
                 "minecraft:cramming", "ywzj_vehicle:vehicle_collision" -> DeathCause.CRUSHED;
            default -> context.fire() ? DeathCause.BURNED : DeathCause.KILLED_IN_ACTION;
        };
    }
}
