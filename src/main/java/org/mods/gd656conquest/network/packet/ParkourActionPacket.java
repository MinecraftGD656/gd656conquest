package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;

import java.util.Locale;
import java.util.function.Supplier;

public final class ParkourActionPacket {
    private final String action;

    public ParkourActionPacket(String action) {
        this.action = action == null ? "" : action;
    }

    public String action() {
        return action;
    }

    public static ParkourActionPacket quickJoin() {
        return new ParkourActionPacket("quick_join");
    }

    public static ParkourActionPacket fellBelowStart() {
        return new ParkourActionPacket("fell_below_start");
    }

    public static void encode(ParkourActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action);
    }

    public static ParkourActionPacket decode(FriendlyByteBuf buffer) {
        return new ParkourActionPacket(buffer.readUtf());
    }

    public static void handle(ParkourActionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }
            String action = message.action().trim().toLowerCase(Locale.ROOT);
            if ("quick_join".equals(action)) {
                ParkourRuntimeManager.of(sender.server).handleQuickJoinRequest(sender);
                return;
            }
            if ("fell_below_start".equals(action)) {
                ParkourRuntimeManager.of(sender.server).handleFallBelowStartRequest(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
