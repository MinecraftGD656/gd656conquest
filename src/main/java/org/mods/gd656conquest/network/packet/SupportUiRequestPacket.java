package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.support.SupportUiService;

import java.util.function.Supplier;

public final class SupportUiRequestPacket {
    public static void encode(SupportUiRequestPacket packet, FriendlyByteBuf buffer) {
    }

    public static SupportUiRequestPacket decode(FriendlyByteBuf buffer) {
        return new SupportUiRequestPacket();
    }

    public static void handle(SupportUiRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            SupportUiService.sendSnapshot(sender);
        });
        context.setPacketHandled(true);
    }
}
