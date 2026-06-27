package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;

import java.util.UUID;
import java.util.function.Supplier;

public final class PlayerAnimatorDebugPacket {
    private final UUID playerId;
    private final int durationMs;
    private final boolean active;

    public PlayerAnimatorDebugPacket(UUID playerId, int durationMs, boolean active) {
        this.playerId = playerId == null ? new UUID(0L, 0L) : playerId;
        this.durationMs = Math.max(0, durationMs);
        this.active = active;
    }

    public static PlayerAnimatorDebugPacket start(UUID playerId, int durationMs) {
        return new PlayerAnimatorDebugPacket(playerId, Math.max(1000, durationMs), true);
    }

    public static PlayerAnimatorDebugPacket stop(UUID playerId) {
        return new PlayerAnimatorDebugPacket(playerId, 0, false);
    }

    public static void encode(PlayerAnimatorDebugPacket message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.playerId);
        buffer.writeVarInt(message.durationMs);
        buffer.writeBoolean(message.active);
    }

    public static PlayerAnimatorDebugPacket decode(FriendlyByteBuf buffer) {
        return new PlayerAnimatorDebugPacket(buffer.readUUID(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(PlayerAnimatorDebugPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> {
                if (message.active) {
                    ClientPlayerAnimatorDebugState.startPreview(message.playerId, message.durationMs);
                    return;
                }
                ClientPlayerAnimatorDebugState.stopPreview(message.playerId);
            }
        ));
        context.setPacketHandled(true);
    }
}
