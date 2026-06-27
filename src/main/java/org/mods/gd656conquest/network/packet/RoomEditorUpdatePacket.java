package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class RoomEditorUpdatePacket {
    private final String roomId;
    private final String keyPath;
    private final String value;

    public RoomEditorUpdatePacket(String roomId, String keyPath, String value) {
        this.roomId = roomId == null ? "" : roomId;
        this.keyPath = keyPath == null ? "" : keyPath;
        this.value = value == null ? "" : value;
    }

    public static void encode(RoomEditorUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
        buffer.writeUtf(packet.keyPath);
        buffer.writeUtf(packet.value);
    }

    public static RoomEditorUpdatePacket decode(FriendlyByteBuf buffer) {
        return new RoomEditorUpdatePacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(RoomEditorUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.handleAdminRoomConfigEdit(sender, message.roomId, message.keyPath, message.value);
        });
        context.setPacketHandled(true);
    }
}
