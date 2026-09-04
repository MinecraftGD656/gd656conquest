package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBakeState;

import java.util.function.Supplier;

public final class CaptureBoundaryBakeMetaPacket {
    private final String roomId;
    private final int version;
    private final String payloadHash;
    private final boolean available;

    public CaptureBoundaryBakeMetaPacket(String roomId, int version, String payloadHash, boolean available) {
        this.roomId = roomId == null ? "" : roomId;
        this.version = version;
        this.payloadHash = payloadHash == null ? "" : payloadHash;
        this.available = available;
    }

    public static void encode(CaptureBoundaryBakeMetaPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
        buffer.writeInt(packet.version);
        buffer.writeUtf(packet.payloadHash);
        buffer.writeBoolean(packet.available);
    }

    public static CaptureBoundaryBakeMetaPacket decode(FriendlyByteBuf buffer) {
        return new CaptureBoundaryBakeMetaPacket(buffer.readUtf(), buffer.readInt(), buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(CaptureBoundaryBakeMetaPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientCaptureBakeState.handleMeta(packet.roomId, packet.version, packet.payloadHash, packet.available)
        ));
        context.setPacketHandled(true);
    }
}
