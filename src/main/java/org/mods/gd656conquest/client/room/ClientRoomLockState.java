package org.mods.gd656conquest.client.room;

import org.mods.gd656conquest.common.player.lock.PlayerLockSnapshot;
import org.mods.gd656conquest.common.player.lock.PresentationLockPolicy;

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
        return ClientBattalionBriefingState.isCinematicActive() || snapshot.lockInventory();
    }

    public static boolean isDebugInventoryLockActive() {
        return snapshot.lockInventory() && snapshot.reason().startsWith("debug_");
    }

    public static boolean requiresCombatFreeze() {
        return ClientBattalionBriefingState.isCinematicActive() || PresentationLockPolicy.requiresCombatFreeze(snapshot);
    }

    public static boolean shouldLockMovement() {
        return ClientBattalionBriefingState.isCinematicActive() || snapshot.lockMovement() || ClientReviveState.shouldLockMovement();
    }

    public static boolean shouldForceCrouch() {
        return snapshot.forceCrouch() || ClientReviveState.shouldForceCrouch();
    }

    public static boolean shouldRestrictGameplayInteraction() {
        return ClientBattalionBriefingState.isCinematicActive() || snapshot.lockGameplayInteraction();
    }

    public static boolean shouldRestrictHeldItemUse() {
        return ClientBattalionBriefingState.isCinematicActive() || snapshot.lockGameplayInteraction() || ClientReviveState.isReviving();
    }

    public static boolean shouldLockHotbarSelection() {
        if (requiresCombatFreeze()) return true;
        if (!snapshot.lockHotbar()) {
            return false;
        }
        String reason = snapshot.reason();
        return "downed".equals(reason)
            || "being_revived".equals(reason)
            || "initial_movement_lock".equals(reason);
    }

    public static boolean shouldBlockLookInput() {
        return requiresCombatFreeze() || ClientDownedCamera.blocksLook() || (snapshot.lockLook() && !snapshot.releaseMouse());
    }

    public static boolean shouldReleaseMouse() {
        return ClientBattalionBriefingState.isCinematicActive() || snapshot.releaseMouse();
    }

    public static boolean shouldReleaseMouseForUi() {
        return shouldReleaseMouse();
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
