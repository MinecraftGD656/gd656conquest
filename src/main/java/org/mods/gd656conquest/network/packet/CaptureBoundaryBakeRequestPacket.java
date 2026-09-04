package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.capture.CaptureBoundaryBakeService;

import java.util.function.Supplier;

public final class CaptureBoundaryBakeRequestPacket {
    private final String roomId;

    public CaptureBoundaryBakeRequestPacket(String roomId) {
        this.roomId = roomId == null ? "" : roomId;
    }

    public static void encode(CaptureBoundaryBakeRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
    }

    public static CaptureBoundaryBakeRequestPacket decode(FriendlyByteBuf buffer) {
        return new CaptureBoundaryBakeRequestPacket(buffer.readUtf());
    }

    public static void handle(CaptureBoundaryBakeRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> CaptureBoundaryBakeService.of(player.server).handleDownloadRequest(player, packet.roomId));
        }
        context.setPacketHandled(true);
    }
}
