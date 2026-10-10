package org.mods.gd656conquest.client.room;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;

public final class DownedRagdollClientHooks {
    private DownedRagdollClientHooks() {}

    public static void clearForcedPose(UUID playerId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || playerId == null) return;
        Player player = mc.level.getPlayerByUUID(playerId);
        if (player == null) return;
        if (org.mods.gd656conquest.common.player.RevivedPronePose.active(player)) {
            org.mods.gd656conquest.common.player.RevivedPronePose.apply(player);
            return;
        }
        PlayerAnimatorDebugDimensionsState.clearPlayer(player);
        // On an integrated server the shared APPLIED_PLAYERS set may already have been cleared
        // by the server entity. The client entity still needs its own forced pose released.
        if (player.getForcedPose() == Pose.SWIMMING) player.setForcedPose(null);
        if (!player.isSwimming() && !player.isFallFlying()) player.setPose(Pose.STANDING);
        player.refreshDimensions();
        if (mc.player == player) TaczClientInputCompat.clearCrawlState(mc);
    }

    public static void onDownedCleared() {
        ClientKillerCardState.hide();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID playerId = mc.player.getUUID();
        ClientDownedRagdollState.finishLocal();
        ClientPlayerAnimatorDebugState.stopPreview(playerId);
        clearForcedPose(playerId);
    }

    public static void onDeployTransition() {
        if (ClientDownedState.isActive()) ClientDownedState.clear();
        else onDownedCleared();
    }

    public static boolean isDowned(UUID playerId) {
        return ClientDownedRagdollState.isDowned(playerId)
            || ClientPlayerAnimatorDebugState.isPreviewActive(playerId);
    }

    public static double distanceSquared(Entity viewer, Entity target) {
        if (viewer instanceof Player player && target instanceof Player downed) {
            return ClientDownedRagdollState.distanceSquared(player, downed);
        }
        return viewer.distanceToSqr(target);
    }
}
