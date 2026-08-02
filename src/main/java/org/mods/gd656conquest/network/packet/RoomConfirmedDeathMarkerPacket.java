package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientDeployDeathMarkerState;

import java.util.UUID;
import java.util.function.Supplier;

public final class RoomConfirmedDeathMarkerPacket {
    private final UUID playerId;
    private final double x;
    private final double y;
    private final double z;
    private final boolean squadMate;

    public RoomConfirmedDeathMarkerPacket(UUID playerId, double x, double y, double z, boolean squadMate) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.squadMate = squadMate;
    }

    public static void encode(RoomConfirmedDeathMarkerPacket message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.playerId);
        buffer.writeDouble(message.x);
        buffer.writeDouble(message.y);
        buffer.writeDouble(message.z);
        buffer.writeBoolean(message.squadMate);
    }

    public static RoomConfirmedDeathMarkerPacket decode(FriendlyByteBuf buffer) {
        return new RoomConfirmedDeathMarkerPacket(
            buffer.readUUID(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readBoolean()
        );
    }

    public static void handle(RoomConfirmedDeathMarkerPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientDeployDeathMarkerState.record(message.playerId, message.x, message.y, message.z, message.squadMate)
        ));
        context.setPacketHandled(true);
    }
}
