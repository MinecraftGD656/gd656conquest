package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.PlayerItemNameUploadService;

import java.util.function.Supplier;

public final class MainHandItemNameUploadPacket {
    private final String itemId;
    private final String displayName;

    public MainHandItemNameUploadPacket(String itemId, String displayName) {
        this.itemId = itemId == null ? "" : itemId;
        this.displayName = displayName == null ? "" : displayName;
    }

    public static void encode(MainHandItemNameUploadPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.itemId);
        buffer.writeUtf(packet.displayName);
    }

    public static MainHandItemNameUploadPacket decode(FriendlyByteBuf buffer) {
        return new MainHandItemNameUploadPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(MainHandItemNameUploadPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            PlayerItemNameUploadService.updateUploadedMainHandName(sender, message.itemId, message.displayName);
        });
        context.setPacketHandled(true);
    }
}
