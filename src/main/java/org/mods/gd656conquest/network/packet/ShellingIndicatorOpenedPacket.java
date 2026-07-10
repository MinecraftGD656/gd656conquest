package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.support.ShellingSupportService;

import java.util.function.Supplier;

public record ShellingIndicatorOpenedPacket(String shellingId) {
    public ShellingIndicatorOpenedPacket {
        shellingId = shellingId == null ? "" : shellingId;
    }

    public static ShellingIndicatorOpenedPacket of(String shellingId) {
        return new ShellingIndicatorOpenedPacket(shellingId);
    }

    public static void encode(ShellingIndicatorOpenedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shellingId);
    }

    public static ShellingIndicatorOpenedPacket decode(FriendlyByteBuf buffer) {
        return new ShellingIndicatorOpenedPacket(buffer.readUtf());
    }

    public static void handle(ShellingIndicatorOpenedPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ShellingSupportService.handleIndicatorOpened(sender, message.shellingId);
        });
        context.setPacketHandled(true);
    }
}
