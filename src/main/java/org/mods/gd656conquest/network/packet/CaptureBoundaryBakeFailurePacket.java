package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBakeState;

import java.util.function.Supplier;

public final class CaptureBoundaryBakeFailurePacket {
    private final String roomId;
    private final String message;

    public CaptureBoundaryBakeFailurePacket(String roomId, String message) {
        this.roomId = roomId == null ? "" : roomId;
        this.message = message == null ? "" : message;
    }

    public static void encode(CaptureBoundaryBakeFailurePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
        buffer.writeUtf(packet.message);
    }

    public static CaptureBoundaryBakeFailurePacket decode(FriendlyByteBuf buffer) {
        return new CaptureBoundaryBakeFailurePacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(CaptureBoundaryBakeFailurePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientCaptureBakeState.handleFailure(packet.roomId, packet.message)
        ));
        context.setPacketHandled(true);
    }
}
