package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class LoadoutUiRequestPacket {
    private final boolean openPanel;

    public LoadoutUiRequestPacket(boolean openPanel) {
        this.openPanel = openPanel;
    }

    public static void encode(LoadoutUiRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.openPanel);
    }

    public static LoadoutUiRequestPacket decode(FriendlyByteBuf buffer) {
        return new LoadoutUiRequestPacket(buffer.readBoolean());
    }

    public static void handle(LoadoutUiRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.sendSnapshot(sender, message.openPanel);
        });
        context.setPacketHandled(true);
    }
}
