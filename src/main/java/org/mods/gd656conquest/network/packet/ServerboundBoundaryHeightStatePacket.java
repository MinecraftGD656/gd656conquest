package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.function.Supplier;

public final class ServerboundBoundaryHeightStatePacket {
    private final boolean abovePlayerMaxY;
    private final boolean aboveAircraftPassengerMaxY;

    public ServerboundBoundaryHeightStatePacket(boolean abovePlayerMaxY, boolean aboveAircraftPassengerMaxY) {
        this.abovePlayerMaxY = abovePlayerMaxY;
        this.aboveAircraftPassengerMaxY = aboveAircraftPassengerMaxY;
    }

    public static void encode(ServerboundBoundaryHeightStatePacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.abovePlayerMaxY);
        buffer.writeBoolean(message.aboveAircraftPassengerMaxY);
    }

    public static ServerboundBoundaryHeightStatePacket decode(FriendlyByteBuf buffer) {
        return new ServerboundBoundaryHeightStatePacket(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(ServerboundBoundaryHeightStatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }
            RoomCoreRuntimeManager.of(sender.server).handleBoundaryHeightReport(
                sender,
                message.abovePlayerMaxY,
                message.aboveAircraftPassengerMaxY,
                sender.server.getTickCount()
            );
        });
        context.setPacketHandled(true);
    }
}
