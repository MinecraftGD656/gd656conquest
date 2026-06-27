package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.marker.ClientMarkerDebugPreviewState;

import java.util.function.Supplier;

public final class MarkerDebugPreviewPacket {
    private final int durationMs;

    public MarkerDebugPreviewPacket(int durationMs) {
        this.durationMs = Math.max(1000, durationMs);
    }

    public static void encode(MarkerDebugPreviewPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.durationMs);
    }

    public static MarkerDebugPreviewPacket decode(FriendlyByteBuf buffer) {
        return new MarkerDebugPreviewPacket(buffer.readVarInt());
    }

    public static void handle(MarkerDebugPreviewPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMarkerDebugPreviewState.startPreview(message.durationMs)));
        context.setPacketHandled(true);
    }
}
