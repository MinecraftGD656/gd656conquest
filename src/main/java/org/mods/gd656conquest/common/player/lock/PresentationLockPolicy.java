package org.mods.gd656conquest.common.player.lock;

/** Cinematics freeze combat while retaining the cursor and ordinary screen input. */
public final class PresentationLockPolicy {
    private PresentationLockPolicy() {}

    public static PlayerLockSnapshot resolve(boolean ending, boolean combatPaused, boolean preparation) {
        if (!ending && (!combatPaused || preparation)) return null;
        return new PlayerLockSnapshot(PlayerLockStrength.UI_CURSOR,
            ending ? "match_end" : "battalion_intermission",
            true, true, true, true, true, true, true,
            !ending, !ending, !ending, false, false, ending);
    }

    public static boolean requiresCombatFreeze(PlayerLockSnapshot snapshot) {
        return snapshot != null && ("battalion_intermission".equals(snapshot.reason()) || "match_end".equals(snapshot.reason()));
    }

    public static boolean blocksPointerMovement(boolean lookLocked, boolean releasedForUi, boolean screenOpen, boolean mouseGrabbed) {
        return lookLocked && mouseGrabbed && !releasedForUi && !screenOpen;
    }

    public static boolean blocksLoadoutAction(String action, boolean combatPaused, boolean preparation) {
        return combatPaused && !preparation && "switch_camp".equals(action);
    }

    public static boolean blocksDownedAction(String action, boolean combatPaused) {
        if (!combatPaused || action == null) return false;
        return "surrender".equals(action) || "redeploy".equals(action) || "revive_start".equals(action)
            || action.startsWith("death_deploy:");
    }

    public static boolean blocksMouseCombatInput(boolean locked, boolean screenOpen, boolean releasedForUi, boolean combatBinding) {
        return locked && !screenOpen && !releasedForUi && combatBinding;
    }

    public static boolean blocksPlayerAction(String action, boolean locked) {
        if (!locked || action == null) return false;
        return switch (action) {
            case "START_DESTROY_BLOCK", "STOP_DESTROY_BLOCK", "DROP_ALL_ITEMS", "DROP_ITEM", "SWAP_ITEM_WITH_OFFHAND" -> true;
            default -> false;
        };
    }
}
