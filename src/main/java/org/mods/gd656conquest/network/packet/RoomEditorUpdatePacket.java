package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;

import java.util.function.Supplier;

public final class RoomEditorUpdatePacket {
    private static final String OPERATION_SET = "set";
    private static final String OPERATION_GROUP_ADD = "group_add";
    private static final String OPERATION_GROUP_DELETE = "group_delete";

    private final String roomId;
    private final String keyPath;
    private final String operation;
    private final String value;

    public RoomEditorUpdatePacket(String roomId, String keyPath, String value) {
        this(roomId, keyPath, OPERATION_SET, value);
    }

    public RoomEditorUpdatePacket(String roomId, String keyPath, String operation, String value) {
        this.roomId = roomId == null ? "" : roomId;
        this.keyPath = keyPath == null ? "" : keyPath;
        this.operation = operation == null || operation.isBlank() ? OPERATION_SET : operation;
        this.value = value == null ? "" : value;
    }

    public static RoomEditorUpdatePacket groupAdd(String roomId, String keyPath, String value) {
        return new RoomEditorUpdatePacket(roomId, keyPath, OPERATION_GROUP_ADD, value);
    }

    public static RoomEditorUpdatePacket groupDelete(String roomId, String keyPath, String value) {
        return new RoomEditorUpdatePacket(roomId, keyPath, OPERATION_GROUP_DELETE, value);
    }

    public static void encode(RoomEditorUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
        buffer.writeUtf(packet.keyPath);
        buffer.writeUtf(packet.operation);
        buffer.writeUtf(packet.value);
    }

    public static RoomEditorUpdatePacket decode(FriendlyByteBuf buffer) {
        return new RoomEditorUpdatePacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(RoomEditorUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            LoadoutUiService.handleAdminRoomConfigEdit(sender, message.roomId, message.keyPath, message.operation, message.value);
        });
        context.setPacketHandled(true);
    }
}
