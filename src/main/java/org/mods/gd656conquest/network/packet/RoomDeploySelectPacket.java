package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.function.Supplier;

public final class RoomDeploySelectPacket {
    private final String action;
    private final String kind;
    private final String targetId;

    public RoomDeploySelectPacket(String action, String kind, String targetId) {
        this.action = action == null ? "" : action;
        this.kind = kind == null ? "" : kind;
        this.targetId = targetId == null ? "" : targetId;
    }

    public static RoomDeploySelectPacket selectCapture(String code) {
        return new RoomDeploySelectPacket("select", "capture", code);
    }

    public static RoomDeploySelectPacket selectTeam(String teamToken) {
        return new RoomDeploySelectPacket("select", "team", teamToken);
    }

    public static RoomDeploySelectPacket selectTeammate(String playerUuid) {
        return new RoomDeploySelectPacket("select", "teammate", playerUuid);
    }

    public static RoomDeploySelectPacket selectBeacon(String beaconUuid) {
        return new RoomDeploySelectPacket("select", "beacon", beaconUuid);
    }

    public static RoomDeploySelectPacket selectVehicle(String entryId) {
        return new RoomDeploySelectPacket("select", "vehicle", entryId);
    }

    public static RoomDeploySelectPacket confirmCapture(String code) {
        return new RoomDeploySelectPacket("confirm", "capture", code);
    }

    public static RoomDeploySelectPacket confirmTeam(String teamToken) {
        return new RoomDeploySelectPacket("confirm", "team", teamToken);
    }

    public static RoomDeploySelectPacket confirmTeammate(String playerUuid) {
        return new RoomDeploySelectPacket("confirm", "teammate", playerUuid);
    }

    public static RoomDeploySelectPacket confirmBeacon(String beaconUuid) {
        return new RoomDeploySelectPacket("confirm", "beacon", beaconUuid);
    }

    public static RoomDeploySelectPacket confirmVehicle(String entryId) {
        return new RoomDeploySelectPacket("confirm", "vehicle", entryId);
    }

    public static void encode(RoomDeploySelectPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.kind);
        buffer.writeUtf(packet.targetId);
    }

    public static RoomDeploySelectPacket decode(FriendlyByteBuf buffer) {
        return new RoomDeploySelectPacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(RoomDeploySelectPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (RoomCoreRuntimeManager.of(sender.server).handleDeploySelection(
                sender,
                message.action,
                message.kind,
                message.targetId,
                sender.server.getTickCount()
            )) {
                return;
            }
            sender.displayClientMessage(Component.literal("当前不在可部署状态"), true);
        });
        context.setPacketHandled(true);
    }
}
