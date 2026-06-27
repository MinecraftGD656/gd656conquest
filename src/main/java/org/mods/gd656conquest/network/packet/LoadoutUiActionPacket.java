package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class LoadoutUiActionPacket {
    private final String action;
    private final String classKey;
    private final int slotIndex;
    private final String itemId;

    public LoadoutUiActionPacket(String action, String classKey, int slotIndex, String itemId) {
        this.action = action == null ? "" : action;
        this.classKey = classKey == null ? "" : classKey;
        this.slotIndex = slotIndex;
        this.itemId = itemId == null ? "" : itemId;
    }

    public static LoadoutUiActionPacket selectClass(String classKey) {
        return new LoadoutUiActionPacket("select_class", classKey, 0, "");
    }

    public static LoadoutUiActionPacket selectSlot(String classKey, int slotIndex, String itemId) {
        return new LoadoutUiActionPacket("select_slot", classKey, slotIndex, itemId);
    }

    public static LoadoutUiActionPacket leaveSession() {
        return new LoadoutUiActionPacket("leave_session", "", 0, "");
    }

    public static void encode(LoadoutUiActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.classKey);
        buffer.writeInt(packet.slotIndex);
        buffer.writeUtf(packet.itemId);
    }

    public static LoadoutUiActionPacket decode(FriendlyByteBuf buffer) {
        return new LoadoutUiActionPacket(buffer.readUtf(), buffer.readUtf(), buffer.readInt(), buffer.readUtf());
    }

    public static void handle(LoadoutUiActionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.handleAction(sender, message.action, message.classKey, message.slotIndex, message.itemId);
        });
        context.setPacketHandled(true);
    }
}
