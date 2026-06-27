package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.function.Supplier;

public final class ServerboundAmmoBoxInteractPacket {
    public static void encode(ServerboundAmmoBoxInteractPacket message, FriendlyByteBuf buffer) {
    }

    public static ServerboundAmmoBoxInteractPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundAmmoBoxInteractPacket();
    }

    public static void handle(ServerboundAmmoBoxInteractPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }
            RoomCoreRuntimeManager.of(sender.server).tryUseNearbyAmmoBox(sender, sender.server.getTickCount());
        });
        context.setPacketHandled(true);
    }
}
