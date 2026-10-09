package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.config.ClientPacketBypassConfig;
import org.mods.gd656conquest.client.audio.ClientMusicTriggerService;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.common.audio.AudioManager;

import java.util.function.Supplier;

public final class RoomDeathFlowPacket {
    private static final String SOUND_DEPLOY_SELECT_READY = "gd656conquest:sfx.deploy_select_ready";
    private static final int ACTION_START_DEATH = 0;
    private static final int ACTION_OPEN_DEPLOY = 1;
    private static final int ACTION_START_RESPAWN = 2;
    private static final int ACTION_CLEAR = 3;
    private static final int ACTION_BOUNDARY_ON = 4;
    private static final int ACTION_BOUNDARY_OFF = 5;
    private static final int ACTION_MATCH_END_VIEW_LOCK_ON = 6;
    private static final int ACTION_MATCH_END_VIEW_LOCK_OFF = 7;
    private static final int ACTION_MATCH_END_TRAVEL = 8;
    private static final int ACTION_OPEN_DEPLOY_INSTANT = 9;
    private static final int ACTION_OPEN_RESPAWN_WAIT = 10;
    private final int action;
    private final double sourceX;
    private final double sourceY;
    private final double sourceZ;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final float targetYaw;
    private final float targetPitch;
    private final int killerEntityId;
    private final int deployCountdownTicks;
    private final int transitionTicks;

    public RoomDeathFlowPacket(int action, double sourceX, double sourceY, double sourceZ, double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int killerEntityId, int deployCountdownTicks, int transitionTicks) {
        this.action = action;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.killerEntityId = killerEntityId;
        this.deployCountdownTicks = deployCountdownTicks;
        this.transitionTicks = transitionTicks;
    }

    public static RoomDeathFlowPacket startDeath(double deathX, double deathY, double deathZ, int killerEntityId) {
        return new RoomDeathFlowPacket(ACTION_START_DEATH, deathX, deathY, deathZ, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, killerEntityId, 0, 0);
    }

    public static RoomDeathFlowPacket openDeploy(double deployX, double deployY, double deployZ, float deployYaw, float deployPitch, int deployCountdownTicks) {
        return openDeployFrom(deployX, deployY, deployZ, deployX, deployY, deployZ, deployYaw, deployPitch, deployCountdownTicks);
    }

    public static RoomDeathFlowPacket openDeployFrom(double sourceX, double sourceY, double sourceZ, double deployX, double deployY, double deployZ, float deployYaw, float deployPitch, int deployCountdownTicks) {
        return new RoomDeathFlowPacket(ACTION_OPEN_DEPLOY, sourceX, sourceY, sourceZ, deployX, deployY, deployZ, deployYaw, deployPitch, -1, deployCountdownTicks, 0);
    }

    public static RoomDeathFlowPacket openDeployInstant(double deployX, double deployY, double deployZ, float deployYaw, float deployPitch, int deployCountdownTicks) {
        return new RoomDeathFlowPacket(ACTION_OPEN_DEPLOY_INSTANT, deployX, deployY, deployZ, deployX, deployY, deployZ, deployYaw, deployPitch, -1, deployCountdownTicks, 0);
    }

    public static RoomDeathFlowPacket startRespawnTransition(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int transitionTicks) {
        return new RoomDeathFlowPacket(ACTION_START_RESPAWN, 0.0D, 0.0D, 0.0D, targetX, targetY, targetZ, targetYaw, targetPitch, -1, 0, transitionTicks);
    }

    public static RoomDeathFlowPacket openRespawnWaitInstant(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int countdownTicks, int killerEntityId) {
        return new RoomDeathFlowPacket(ACTION_OPEN_RESPAWN_WAIT, targetX, targetY, targetZ, targetX, targetY, targetZ, targetYaw, targetPitch, killerEntityId, countdownTicks, 0);
    }

    public static RoomDeathFlowPacket clear() {
        return new RoomDeathFlowPacket(ACTION_CLEAR, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, -1, 0, 0);
    }

    public static RoomDeathFlowPacket setBoundaryPenalty(boolean active) {
        return new RoomDeathFlowPacket(active ? ACTION_BOUNDARY_ON : ACTION_BOUNDARY_OFF, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, -1, 0, 0);
    }

    public static RoomDeathFlowPacket setMatchEndViewLock(boolean active) {
        return new RoomDeathFlowPacket(active ? ACTION_MATCH_END_VIEW_LOCK_ON : ACTION_MATCH_END_VIEW_LOCK_OFF, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, -1, 0, 0);
    }

    public static RoomDeathFlowPacket startMatchEndTransition(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int transitionTicks) {
        return new RoomDeathFlowPacket(ACTION_MATCH_END_TRAVEL, 0.0D, 0.0D, 0.0D, targetX, targetY, targetZ, targetYaw, targetPitch, -1, 0, transitionTicks);
    }

    public static void encode(RoomDeathFlowPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.action);
        buffer.writeDouble(packet.sourceX);
        buffer.writeDouble(packet.sourceY);
        buffer.writeDouble(packet.sourceZ);
        buffer.writeDouble(packet.targetX);
        buffer.writeDouble(packet.targetY);
        buffer.writeDouble(packet.targetZ);
        buffer.writeFloat(packet.targetYaw);
        buffer.writeFloat(packet.targetPitch);
        buffer.writeInt(packet.killerEntityId);
        buffer.writeInt(packet.deployCountdownTicks);
        buffer.writeInt(packet.transitionTicks);
    }

    public static RoomDeathFlowPacket decode(FriendlyByteBuf buffer) {
        return new RoomDeathFlowPacket(
                buffer.readInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt()
        );
    }

    public static void handle(RoomDeathFlowPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (ClientPacketBypassConfig.bypassDeathFlowPackets()) {
                ClientDeathFlowState.clear();
                return;
            }
            if (message.action == ACTION_START_DEATH) {
                ClientMusicTriggerService.onDeathStarted();
                ClientDeathFlowState.startDeathSequence(message.sourceX, message.sourceY, message.sourceZ, message.killerEntityId);
                return;
            }
            if (message.action == ACTION_OPEN_DEPLOY) {
                ClientMusicTriggerService.onDeployTravelStarted();
                ClientDeathFlowState.openDeploySequence(message.sourceX, message.sourceY, message.sourceZ, message.targetX, message.targetY, message.targetZ, message.targetYaw, message.targetPitch, message.deployCountdownTicks);
                return;
            }
            if (message.action == ACTION_OPEN_DEPLOY_INSTANT) {
                ClientMusicTriggerService.onDeployOpened();
                ClientDeathFlowState.openDeployInstant(message.targetX, message.targetY, message.targetZ, message.targetYaw, message.targetPitch, message.deployCountdownTicks);
                return;
            }
            if (message.action == ACTION_START_RESPAWN) {
                AudioManager.getInstance().playSound(SOUND_DEPLOY_SELECT_READY, 1.0F, 1.0F);
                ClientMusicTriggerService.onRespawnTransitionStarted();
                ClientDeathFlowState.startRespawnTransition(message.targetX, message.targetY, message.targetZ, message.targetYaw, message.targetPitch, message.transitionTicks);
                return;
            }
            if (message.action == ACTION_OPEN_RESPAWN_WAIT) {
                ClientDeathFlowState.openRespawnWaitInstant(
                    message.targetX,
                    message.targetY,
                    message.targetZ,
                    message.targetYaw,
                    message.targetPitch,
                    message.deployCountdownTicks,
                    message.killerEntityId
                );
                return;
            }
            if (message.action == ACTION_BOUNDARY_ON) {
                ClientDeathFlowState.setBoundaryPenaltyActive(true);
                return;
            }
            if (message.action == ACTION_BOUNDARY_OFF) {
                ClientDeathFlowState.setBoundaryPenaltyActive(false);
                return;
            }
            if (message.action == ACTION_MATCH_END_VIEW_LOCK_ON) {
                ClientDeathFlowState.setMatchEndViewLocked(true);
                return;
            }
            if (message.action == ACTION_MATCH_END_VIEW_LOCK_OFF) {
                ClientDeathFlowState.setMatchEndViewLocked(false);
                return;
            }
            if (message.action == ACTION_MATCH_END_TRAVEL) {
                ClientMusicTriggerService.onMatchEndTransitionStarted();
                ClientDeathFlowState.startMatchEndTransition(message.targetX, message.targetY, message.targetZ, message.targetYaw, message.targetPitch, message.transitionTicks);
                return;
            }
            ClientDeathFlowState.clear();
        }));
        context.setPacketHandled(true);
    }
}
