package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBakeState;

import java.util.function.Supplier;

public final class CaptureBoundaryBakeChunkPacket {
    private final String roomId;
    private final int version;
    private final String payloadHash;
    private final int batchIndex;
    private final int batchCount;
    private final byte[] payload;

    public CaptureBoundaryBakeChunkPacket(String roomId, int version, String payloadHash, int batchIndex, int batchCount, byte[] payload) {
        this.roomId = roomId == null ? "" : roomId;
        this.version = version;
        this.payloadHash = payloadHash == null ? "" : payloadHash;
        this.batchIndex = batchIndex;
        this.batchCount = batchCount;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public static void encode(CaptureBoundaryBakeChunkPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
        buffer.writeInt(packet.version);
        buffer.writeUtf(packet.payloadHash);
        buffer.writeInt(packet.batchIndex);
        buffer.writeInt(packet.batchCount);
        buffer.writeByteArray(packet.payload);
    }

    public static CaptureBoundaryBakeChunkPacket decode(FriendlyByteBuf buffer) {
        return new CaptureBoundaryBakeChunkPacket(
            buffer.readUtf(),
            buffer.readInt(),
            buffer.readUtf(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readByteArray()
        );
    }

    public static void handle(CaptureBoundaryBakeChunkPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientCaptureBakeState.handleChunk(packet.roomId, packet.version, packet.payloadHash, packet.batchIndex, packet.batchCount, packet.payload)
        ));
        context.setPacketHandled(true);
    }
}
