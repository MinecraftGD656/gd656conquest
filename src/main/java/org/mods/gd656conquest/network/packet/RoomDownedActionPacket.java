package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.function.Supplier;

public final class RoomDownedActionPacket {
    private final String action;

    public RoomDownedActionPacket(String action) {
        this.action = action == null ? "" : action.trim();
    }

    public static RoomDownedActionPacket surrender() {
        return new RoomDownedActionPacket("surrender");
    }

    public static RoomDownedActionPacket reviveStart() {
        return new RoomDownedActionPacket("revive_start");
    }

    public static RoomDownedActionPacket reviveStop() {
        return new RoomDownedActionPacket("revive_stop");
    }

    public static RoomDownedActionPacket redeploy() {
        return new RoomDownedActionPacket("redeploy");
    }

    public static void encode(RoomDownedActionPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.action);
    }

    public static RoomDownedActionPacket decode(FriendlyByteBuf buffer) {
        return new RoomDownedActionPacket(buffer.readUtf());
    }

    public static void handle(RoomDownedActionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (RoomCoreRuntimeManager.of(sender.server).handleDownedAction(sender, message.action, sender.server.getTickCount())) {
                return;
            }
            if ("surrender".equalsIgnoreCase(message.action)) {
                sender.displayClientMessage(Component.literal("当前不在可放弃的倒地状态"), true);
            }
        });
        context.setPacketHandled(true);
    }
}
