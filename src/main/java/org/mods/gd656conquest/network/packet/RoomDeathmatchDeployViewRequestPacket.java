package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.function.Supplier;

public final class RoomDeathmatchDeployViewRequestPacket {
    public static void encode(RoomDeathmatchDeployViewRequestPacket packet, FriendlyByteBuf buffer) {
    }

    public static RoomDeathmatchDeployViewRequestPacket decode(FriendlyByteBuf buffer) {
        return new RoomDeathmatchDeployViewRequestPacket();
    }

    public static void handle(RoomDeathmatchDeployViewRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            RoomCoreRuntimeManager.of(sender.server).requestDeathmatchDeployView(sender, sender.server.getTickCount());
        });
        context.setPacketHandled(true);
    }
}
