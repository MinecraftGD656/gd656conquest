package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public final class AdminDebugOverlayPacket {
    private static final int MAX_PAYLOAD_BYTES = 32767;
    private final String payload;

    public AdminDebugOverlayPacket(String payload) {
        this.payload = payload == null || payload.isBlank() ? "-" : payload;
    }

    public static void encode(AdminDebugOverlayPacket message, FriendlyByteBuf buffer) {
        String safePayload = message.payload;
        if (!"-".equals(safePayload) && safePayload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            int lastValidSemi = safePayload.lastIndexOf(';', MAX_PAYLOAD_BYTES);
            if (lastValidSemi > 0) {
                safePayload = safePayload.substring(0, lastValidSemi);
            } else {
                safePayload = safePayload.substring(0, MAX_PAYLOAD_BYTES);
            }
        }
        buffer.writeUtf(safePayload);
    }

    public static AdminDebugOverlayPacket decode(FriendlyByteBuf buffer) {
        return new AdminDebugOverlayPacket(buffer.readUtf());
    }

    public static void handle(AdminDebugOverlayPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHudDebugState.setAdminDebugOverlay(message.payload)));
        context.setPacketHandled(true);
    }
}
