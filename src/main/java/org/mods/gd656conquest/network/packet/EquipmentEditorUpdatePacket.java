package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class EquipmentEditorUpdatePacket {
    private final String equipmentId;
    private final String fieldKey;
    private final String value;

    public EquipmentEditorUpdatePacket(String equipmentId, String fieldKey, String value) {
        this.equipmentId = equipmentId == null ? "" : equipmentId;
        this.fieldKey = fieldKey == null ? "" : fieldKey;
        this.value = value == null ? "" : value;
    }

    public static void encode(EquipmentEditorUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.equipmentId);
        buffer.writeUtf(packet.fieldKey);
        buffer.writeUtf(packet.value);
    }

    public static EquipmentEditorUpdatePacket decode(FriendlyByteBuf buffer) {
        return new EquipmentEditorUpdatePacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(EquipmentEditorUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.handleAdminEquipmentEdit(sender, message.equipmentId, message.fieldKey, message.value);
        });
        context.setPacketHandled(true);
    }
}
