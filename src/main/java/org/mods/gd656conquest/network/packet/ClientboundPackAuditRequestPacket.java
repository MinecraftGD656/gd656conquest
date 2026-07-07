package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.pack.ClientPackAuditService;

import java.util.function.Supplier;

public final class ClientboundPackAuditRequestPacket {
    private final String requestId;
    private final String reason;

    public ClientboundPackAuditRequestPacket(String requestId, String reason) {
        this.requestId = requestId == null ? "" : requestId;
        this.reason = reason == null ? "" : reason;
    }

    public static void encode(ClientboundPackAuditRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.requestId);
        buffer.writeUtf(packet.reason);
    }

    public static ClientboundPackAuditRequestPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundPackAuditRequestPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(ClientboundPackAuditRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientPackAuditService.requestUpload(packet.requestId, packet.reason)
        ));
        context.setPacketHandled(true);
    }
}
