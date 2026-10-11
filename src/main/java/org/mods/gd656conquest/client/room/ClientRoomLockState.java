package org.mods.gd656conquest.client.room;

import org.mods.gd656conquest.common.player.lock.PlayerLockSnapshot;

public final class ClientRoomLockState {
    private static PlayerLockSnapshot snapshot = PlayerLockSnapshot.none();

    private ClientRoomLockState() {
    }

    public static void update(PlayerLockSnapshot nextSnapshot) {
        snapshot = nextSnapshot == null ? PlayerLockSnapshot.none() : nextSnapshot;
    }

    public static void reset() {
        snapshot = PlayerLockSnapshot.none();
    }

    public static PlayerLockSnapshot snapshot() {
        return snapshot;
    }

    public static boolean shouldLockInventory() {
        return ClientBattalionBriefingState.blocksAllInput() || snapshot.lockInventory();
    }

    public static boolean isDebugInventoryLockActive() {
        return snapshot.lockInventory() && snapshot.reason().startsWith("debug_");
    }

    public static boolean shouldLockMovement() {
        return ClientBattalionBriefingState.blocksAllInput() || snapshot.lockMovement() || ClientReviveState.shouldLockMovement();
    }

    public static boolean shouldForceCrouch() {
        return snapshot.forceCrouch() || ClientReviveState.shouldForceCrouch();
    }

    public static boolean shouldRestrictGameplayInteraction() {
        return ClientBattalionBriefingState.blocksAllInput() || snapshot.lockGameplayInteraction();
    }

    public static boolean shouldRestrictHeldItemUse() {
        return ClientBattalionBriefingState.blocksAllInput() || snapshot.lockGameplayInteraction() || ClientReviveState.isReviving();
    }

    public static boolean shouldLockHotbarSelection() {
        if (ClientBattalionBriefingState.blocksAllInput() || "battalion_intermission".equals(snapshot.reason())) return true;
        if (!snapshot.lockHotbar()) {
            return false;
        }
        String reason = snapshot.reason();
        return "downed".equals(reason)
            || "being_revived".equals(reason)
            || "initial_movement_lock".equals(reason);
    }

    public static boolean shouldBlockLookInput() {
        return ClientBattalionBriefingState.blocksAllInput() || ClientDownedCamera.blocksLook() || (snapshot.lockLook() && !snapshot.releaseMouse());
    }

    public static boolean shouldReleaseMouse() {
        return snapshot.releaseMouse();
    }

    public static boolean shouldReleaseMouseForUi() {
        return snapshot.releaseMouse();
    }

    public static boolean shouldHideHeldItems() {
        return snapshot.hideHeldItems();
    }

    public static boolean shouldForceHudOnly() {
        return snapshot.forceHudOnly();
    }

    public static boolean shouldForceVisibleHud() {
        return snapshot.forceVisibleHud();
    }

    public static boolean isDeployUiActive() {
        return snapshot.deployUiActive();
    }

    public static boolean isEndingState() {
        return snapshot.endingState();
    }
}
