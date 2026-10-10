package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.UUID;
import java.util.function.Supplier;

public final class RoomDownedActionPacket {
    private final String action;
    private final UUID targetId;

    public RoomDownedActionPacket(String action) {
        this(action, null);
    }

    public RoomDownedActionPacket(String action, UUID targetId) {
        this.action = action == null ? "" : action.trim();
        this.targetId = targetId;
    }

    public static RoomDownedActionPacket surrender() {
        return new RoomDownedActionPacket("surrender");
    }

    public static RoomDownedActionPacket reviveStart() {
        return reviveStart(null);
    }

    public static RoomDownedActionPacket reviveStart(UUID targetId) {
        return new RoomDownedActionPacket("revive_start", targetId);
    }

    public static RoomDownedActionPacket reviveStop() {
        return new RoomDownedActionPacket("revive_stop");
    }

    public static RoomDownedActionPacket redeploy() {
        return new RoomDownedActionPacket("redeploy");
    }

    public static void encode(RoomDownedActionPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.action);
        buffer.writeBoolean(message.targetId != null);
        if (message.targetId != null) {
            buffer.writeUUID(message.targetId);
        }
    }

    public static RoomDownedActionPacket decode(FriendlyByteBuf buffer) {
        String action = buffer.readUtf();
        UUID targetId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new RoomDownedActionPacket(action, targetId);
    }

    public static void handle(RoomDownedActionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (RoomCoreRuntimeManager.of(sender.server).handleDownedAction(sender, message.action, message.targetId, sender.server.getTickCount())) {
                return;
            }
            if ("surrender".equalsIgnoreCase(message.action)) {
                sender.displayClientMessage(Component.literal("当前不在可放弃的倒地状态"), true);
            }
        });
        context.setPacketHandled(true);
    }
}
