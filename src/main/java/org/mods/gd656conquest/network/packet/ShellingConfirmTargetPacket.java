package org.mods.gd656conquest.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.support.ShellingSupportService;

import java.util.function.Supplier;

public record ShellingConfirmTargetPacket(
    String shellingId,
    int targetX,
    int targetY,
    int targetZ
) {
    public ShellingConfirmTargetPacket {
        shellingId = shellingId == null ? "" : shellingId;
    }

    public static ShellingConfirmTargetPacket of(String shellingId, BlockPos targetPos) {
        BlockPos safePos = targetPos == null ? BlockPos.ZERO : targetPos;
        return new ShellingConfirmTargetPacket(shellingId, safePos.getX(), safePos.getY(), safePos.getZ());
    }

    public static void encode(ShellingConfirmTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shellingId);
        buffer.writeInt(packet.targetX);
        buffer.writeInt(packet.targetY);
        buffer.writeInt(packet.targetZ);
    }

    public static ShellingConfirmTargetPacket decode(FriendlyByteBuf buffer) {
        return new ShellingConfirmTargetPacket(
            buffer.readUtf(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt()
        );
    }

    public static void handle(ShellingConfirmTargetPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ShellingSupportService.handleConfirmTarget(
                sender,
                message.shellingId,
                new BlockPos(message.targetX, message.targetY, message.targetZ)
            );
        });
        context.setPacketHandled(true);
    }
}
