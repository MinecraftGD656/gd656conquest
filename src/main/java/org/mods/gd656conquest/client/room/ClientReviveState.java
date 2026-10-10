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
        boolean shouldHold = minecraft.screen == null
            && !ClientDownedState.isActive()
            && ClientKeyMappings.COMMON_USE.isDown()
            && candidateId != null;
        if (shouldHold) {
            if (!reviveHeld || !candidateId.equals(activeTargetId)) {
                NetworkManager.getChannel().sendToServer(RoomDownedActionPacket.reviveStart(candidateId));
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
        return reviveHeld || resolveCandidateTarget(minecraft, self) != null;
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
            if (!DownedRagdollClientHooks.isDowned(other.getUUID())) {
                continue;
            }
            double distanceSqr = DownedRagdollClientHooks.distanceSquared(self, other);
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
