package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;
import org.mods.gd656conquest.client.marker.ClientRoomMarkerState;

import java.util.UUID;
import java.util.function.Supplier;

public final class PlayerAnimatorHelpSyncPacket {
    private final UUID playerId;
    private final int durationMs;

    public PlayerAnimatorHelpSyncPacket(UUID playerId, int durationMs) {
        this.playerId = playerId == null ? new UUID(0L, 0L) : playerId;
        this.durationMs = Math.max(250, durationMs);
    }

    public static void encode(PlayerAnimatorHelpSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.playerId);
        buffer.writeVarInt(message.durationMs);
    }

    public static PlayerAnimatorHelpSyncPacket decode(FriendlyByteBuf buffer) {
        return new PlayerAnimatorHelpSyncPacket(buffer.readUUID(), buffer.readVarInt());
    }

    public static void handle(PlayerAnimatorHelpSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> {
                ClientPlayerAnimatorDebugState.startHelpPreview(message.playerId, message.durationMs);
                ClientRoomMarkerState.triggerHelpPulse(message.playerId);
            }
        ));
        context.setPacketHandled(true);
    }
}
