package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class AttachmentEditorUpdatePacket {
    private final String attachmentId;
    private final String fieldKey;
    private final String value;

    public AttachmentEditorUpdatePacket(String attachmentId, String fieldKey, String value) {
        this.attachmentId = attachmentId == null ? "" : attachmentId;
        this.fieldKey = fieldKey == null ? "" : fieldKey;
        this.value = value == null ? "" : value;
    }

    public static void encode(AttachmentEditorUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.attachmentId);
        buffer.writeUtf(packet.fieldKey);
        buffer.writeUtf(packet.value);
    }

    public static AttachmentEditorUpdatePacket decode(FriendlyByteBuf buffer) {
        return new AttachmentEditorUpdatePacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(AttachmentEditorUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.handleAdminAttachmentEdit(sender, message.attachmentId, message.fieldKey, message.value);
        });
        context.setPacketHandled(true);
    }
}
