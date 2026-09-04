package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBakeState;

import java.util.function.Supplier;

public final class CaptureBoundaryDebugScanPacket {
    private final boolean enabled;

    public CaptureBoundaryDebugScanPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(CaptureBoundaryDebugScanPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
    }

    public static CaptureBoundaryDebugScanPacket decode(FriendlyByteBuf buffer) {
        return new CaptureBoundaryDebugScanPacket(buffer.readBoolean());
    }

    public static void handle(CaptureBoundaryDebugScanPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientCaptureBakeState.setDebugScanEnabled(packet.enabled)
        ));
        context.setPacketHandled(true);
    }
}
