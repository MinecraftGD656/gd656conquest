package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;

import java.util.function.Supplier;

public final class AdminDebugOverlayPacket {
    private final String payload;

    public AdminDebugOverlayPacket(String payload) {
        this.payload = payload == null || payload.isBlank() ? "-" : payload;
    }

    public static void encode(AdminDebugOverlayPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.payload);
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
