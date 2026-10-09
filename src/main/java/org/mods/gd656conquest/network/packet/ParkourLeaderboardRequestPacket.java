package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;

import java.util.function.Supplier;

public final class ParkourLeaderboardRequestPacket {
    private final boolean openPanel;

    public ParkourLeaderboardRequestPacket(boolean openPanel) {
        this.openPanel = openPanel;
    }

    public boolean openPanel() {
        return openPanel;
    }

    public static void encode(ParkourLeaderboardRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.openPanel);
    }

    public static ParkourLeaderboardRequestPacket decode(FriendlyByteBuf buffer) {
        return new ParkourLeaderboardRequestPacket(buffer.readBoolean());
    }

    public static void handle(ParkourLeaderboardRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }
            ParkourRuntimeManager.of(sender.server).sendLeaderboardSnapshot(sender, message.openPanel());
        });
        context.setPacketHandled(true);
    }
}
