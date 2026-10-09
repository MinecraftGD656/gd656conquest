package org.mods.gd656conquest.common.player.lock;

public record PlayerLockSnapshot(
    PlayerLockStrength strength,
    String reason,
    boolean lockInventory,
    boolean lockHotbar,
    boolean lockGameplayInteraction,
    boolean lockMovement,
    boolean lockLook,
    boolean releaseMouse,
    boolean hideHeldItems,
    boolean forceHudOnly,
    boolean forceVisibleHud,
    boolean forceFirstPerson,
    boolean forceCrouch,
    boolean deployUiActive,
    boolean endingState
) {
    private static final PlayerLockSnapshot NONE = new PlayerLockSnapshot(
        PlayerLockStrength.NONE,
        "none",
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false
    );

    public PlayerLockSnapshot {
        strength = strength == null ? PlayerLockStrength.NONE : strength;
        reason = reason == null || reason.isBlank() ? "none" : reason;
    }

    public static PlayerLockSnapshot none() {
        return NONE;
    }

    public boolean active() {
        return strength != PlayerLockStrength.NONE
            || lockInventory
            || lockHotbar
            || lockGameplayInteraction
            || lockMovement
            || lockLook
            || releaseMouse
            || hideHeldItems
            || forceHudOnly
            || forceVisibleHud
            || forceFirstPerson
            || forceCrouch
            || deployUiActive
            || endingState;
    }
}
