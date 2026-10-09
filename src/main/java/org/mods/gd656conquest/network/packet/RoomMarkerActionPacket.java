package org.mods.gd656conquest.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.UUID;
import java.util.function.Supplier;

public final class RoomMarkerActionPacket {
    private final String action;
    private final String kind;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final UUID targetPlayerId;

    public RoomMarkerActionPacket(String action, String kind, int blockX, int blockY, int blockZ, UUID targetPlayerId) {
        this.action = action == null ? "" : action;
        this.kind = kind == null ? "NORMAL" : kind;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.targetPlayerId = targetPlayerId;
    }

    public static RoomMarkerActionPacket place(BlockPos blockPos, String kind, UUID targetPlayerId) {
        return new RoomMarkerActionPacket(
            "place",
            kind,
            blockPos == null ? 0 : blockPos.getX(),
            blockPos == null ? 0 : blockPos.getY(),
            blockPos == null ? 0 : blockPos.getZ(),
            targetPlayerId
        );
    }

    public static RoomMarkerActionPacket cancel() {
        return new RoomMarkerActionPacket("cancel", "NORMAL", 0, 0, 0, null);
    }

    public static void encode(RoomMarkerActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.kind);
        buffer.writeInt(packet.blockX);
        buffer.writeInt(packet.blockY);
        buffer.writeInt(packet.blockZ);
        buffer.writeBoolean(packet.targetPlayerId != null);
        if (packet.targetPlayerId != null) {
            buffer.writeUUID(packet.targetPlayerId);
        }
    }

    public static RoomMarkerActionPacket decode(FriendlyByteBuf buffer) {
        String action = buffer.readUtf();
        String kind = buffer.readUtf();
        int blockX = buffer.readInt();
        int blockY = buffer.readInt();
        int blockZ = buffer.readInt();
        UUID targetPlayerId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new RoomMarkerActionPacket(action, kind, blockX, blockY, blockZ, targetPlayerId);
    }

    public static void handle(RoomMarkerActionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (RoomCoreRuntimeManager.of(sender.server).handleMarkerAction(
                sender,
                message.action,
                message.kind,
                new BlockPos(message.blockX, message.blockY, message.blockZ),
                message.targetPlayerId,
                sender.server.getTickCount()
            )) {
                return;
            }
        });
        context.setPacketHandled(true);
    }
}
