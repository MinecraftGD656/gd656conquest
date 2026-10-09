package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.support.ShellingSupportService;

import java.util.function.Supplier;

public final class ShellingCancelRequestPacket {
    public static ShellingCancelRequestPacket of() {
        return new ShellingCancelRequestPacket();
    }

    public static void encode(ShellingCancelRequestPacket packet, FriendlyByteBuf buffer) {
    }

    public static ShellingCancelRequestPacket decode(FriendlyByteBuf buffer) {
        return new ShellingCancelRequestPacket();
    }

    public static void handle(ShellingCancelRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ShellingSupportService.handleIndicatorCancelRequest(sender);
        });
        context.setPacketHandled(true);
    }
}
