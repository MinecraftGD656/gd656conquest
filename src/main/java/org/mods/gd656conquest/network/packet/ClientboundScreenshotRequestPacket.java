package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.network.clientbridge.ClientPacketBridgeHolder;

import java.util.function.Supplier;

public class ClientboundScreenshotRequestPacket {
    private final String sessionToken;

    public ClientboundScreenshotRequestPacket(String sessionToken) {
        this.sessionToken = sessionToken == null ? "" : sessionToken;
    }

    public static void encode(ClientboundScreenshotRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.sessionToken);
    }

    public static ClientboundScreenshotRequestPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundScreenshotRequestPacket(buffer.readUtf());
    }

    public static void handle(ClientboundScreenshotRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketBridgeHolder.get().takeAndSendScreenshot(packet.sessionToken)));
        context.setPacketHandled(true);
    }
}
