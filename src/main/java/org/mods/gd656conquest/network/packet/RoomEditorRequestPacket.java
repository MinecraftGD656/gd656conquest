package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class RoomEditorRequestPacket {
    private final boolean openPanel;

    public RoomEditorRequestPacket(boolean openPanel) {
        this.openPanel = openPanel;
    }

    public boolean openPanel() {
        return openPanel;
    }

    public static void encode(RoomEditorRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.openPanel);
    }

    public static RoomEditorRequestPacket decode(FriendlyByteBuf buffer) {
        return new RoomEditorRequestPacket(buffer.readBoolean());
    }

    public static void handle(RoomEditorRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.sendAdminRoomSnapshot(sender, message.openPanel());
        });
        context.setPacketHandled(true);
    }
}
