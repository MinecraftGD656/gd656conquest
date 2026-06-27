package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class VehicleAdminEditorUpdatePacket {
    private final String roomId;
    private final String entryId;
    private final String fieldKey;
    private final String value;

    public VehicleAdminEditorUpdatePacket(String roomId, String entryId, String fieldKey, String value) {
        this.roomId = roomId == null ? "" : roomId;
        this.entryId = entryId == null ? "" : entryId;
        this.fieldKey = fieldKey == null ? "" : fieldKey;
        this.value = value == null ? "" : value;
    }

    public static void encode(VehicleAdminEditorUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
        buffer.writeUtf(packet.entryId);
        buffer.writeUtf(packet.fieldKey);
        buffer.writeUtf(packet.value);
    }

    public static VehicleAdminEditorUpdatePacket decode(FriendlyByteBuf buffer) {
        return new VehicleAdminEditorUpdatePacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(VehicleAdminEditorUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.handleAdminVehicleConfigEdit(sender, message.roomId, message.entryId, message.fieldKey, message.value);
        });
        context.setPacketHandled(true);
    }
}
