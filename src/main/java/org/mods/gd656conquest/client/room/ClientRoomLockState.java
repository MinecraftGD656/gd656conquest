package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
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
        return snapshot.lockInventory();
    }

    /**
     * 管理员(权限>=2)是否应绕过全部禁用背包限制。
     * 调试锁(debug_)例外: 调试锁对管理员同样生效, 用于开发调试验证。
     */
    public static boolean shouldBypassForAdmin(Minecraft minecraft) {
        return minecraft != null
            && minecraft.player != null
            && minecraft.player.hasPermissions(2)
            && !isDebugInventoryLockActive();
    }

    public static boolean isDebugInventoryLockActive() {
        return snapshot.lockInventory() && snapshot.reason().startsWith("debug_");
    }

    public static boolean shouldLockMovement() {
        return snapshot.lockMovement() || ClientReviveState.shouldLockMovement();
    }

    public static boolean shouldForceCrouch() {
        return snapshot.forceCrouch() || ClientReviveState.shouldForceCrouch();
    }

    public static boolean shouldRestrictGameplayInteraction() {
        return snapshot.lockGameplayInteraction();
    }

    public static boolean shouldRestrictHeldItemUse() {
        return snapshot.lockGameplayInteraction() || ClientReviveState.isReviving();
    }

    public static boolean shouldLockHotbarSelection() {
        if (!snapshot.lockHotbar()) {
            return false;
        }
        String reason = snapshot.reason();
        return "downed".equals(reason)
            || "being_revived".equals(reason)
            || "initial_movement_lock".equals(reason);
    }

    public static boolean shouldBlockLookInput() {
        return snapshot.lockLook() && !snapshot.releaseMouse();
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
