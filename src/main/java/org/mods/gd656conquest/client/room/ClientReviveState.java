package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.input.ClientKeyMappings;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDownedActionPacket;

import java.util.UUID;

public final class ClientReviveState {
    private static final double MAX_RESCUE_DISTANCE_SQR = 3.5D * 3.5D;

    private static boolean reviveHeld;
    private static UUID activeTargetId;
    private static boolean forceCrouchMovementLock;

    private ClientReviveState() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        if (self == null || minecraft.level == null) {
            stopLocalRevive();
            return;
        }
        RescueCandidate candidate = resolveCandidateTarget(minecraft, self);
        UUID candidateId = candidate == null ? null : candidate.targetId();
        // GD656Mods: 拆弹/装弹(Rush)优先于救援 —— 附近有可拆/可装炸弹时按 F 不发起救援
        boolean bombPriority = hasBombPriority();
        boolean shouldHold = minecraft.screen == null
            && !ClientDownedState.isActive()
            && ClientKeyMappings.COMMON_USE.isDown()
            && candidateId != null
            && !bombPriority;
        if (shouldHold) {
            if (!reviveHeld || !candidateId.equals(activeTargetId)) {
                NetworkManager.getChannel().sendToServer(RoomDownedActionPacket.reviveStart());
                self.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                reviveHeld = true;
                activeTargetId = candidateId;
            }
            forceCrouchMovementLock = candidate != null && candidate.forceCrouchLock();
            return;
        }
        if (reviveHeld) {
            NetworkManager.getChannel().sendToServer(RoomDownedActionPacket.reviveStop());
        }
        stopLocalRevive();
    }

    public static void reset() {
        stopLocalRevive();
    }

    static boolean shouldLockMovement() {
        return reviveHeld && forceCrouchMovementLock;
    }

    static boolean shouldForceCrouch() {
        return reviveHeld && forceCrouchMovementLock;
    }

    public static boolean isReviving() {
        return reviveHeld;
    }

    public static boolean isCommonUseReserved(Minecraft minecraft) {
        LocalPlayer self = minecraft == null ? null : minecraft.player;
        if (self == null || minecraft.level == null) {
            return false;
        }
        // GD656Mods: 拆弹/装弹(Rush)优先于救援 —— 附近有可拆/可装炸弹时不把通用使用键保留给救援
        return reviveHeld || (resolveCandidateTarget(minecraft, self) != null && !hasBombPriority());
    }

    /**
     * 服务端 Rush 运行时通过 HUD extraPayload 告知本玩家当前处于可拆弹/可装弹位置,
     * 此时通用使用键(F)优先执行拆弹/装弹, 不再发起救援。
     */
    private static boolean hasBombPriority() {
        String payload = ClientHudDebugState.getExtraPayload();
        return payload != null && payload.contains("RUSH_BOMB_ACTION|1");
    }

    private static RescueCandidate resolveCandidateTarget(Minecraft minecraft, LocalPlayer self) {
        if (minecraft == null || minecraft.level == null || self == null) {
            return null;
        }
        boolean selfIsAssault = "assault".equalsIgnoreCase(ClientHudDebugState.getSelfClass());
        Player bestTarget = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (Player other : minecraft.level.players()) {
            if (other == null || other == self || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (!ClientRoomTeamMirror.isTeammate(other)) {
                continue;
            }
            boolean squadMate = ClientRoomTeamMirror.isSquadMate(other);
            if (!selfIsAssault && !squadMate) {
                continue;
            }
            if (!ClientPlayerAnimatorDebugState.isPreviewActive(other.getUUID())) {
                continue;
            }
            double distanceSqr = self.distanceToSqr(other);
            if (distanceSqr > MAX_RESCUE_DISTANCE_SQR || distanceSqr >= bestDistanceSqr) {
                continue;
            }
            bestTarget = other;
            bestDistanceSqr = distanceSqr;
        }
        if (bestTarget == null) {
            return null;
        }
        boolean forceCrouchLock = !selfIsAssault && ClientRoomTeamMirror.isSquadMate(bestTarget);
        return new RescueCandidate(bestTarget.getUUID(), forceCrouchLock);
    }

    private static void stopLocalRevive() {
        reviveHeld = false;
        activeTargetId = null;
        forceCrouchMovementLock = false;
    }

    private record RescueCandidate(UUID targetId, boolean forceCrouchLock) {
    }
}
