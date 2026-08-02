package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.hud.ClientMatchLoadState;

import java.util.function.Supplier;

public final class RoomMatchLoadPacket {
    private final boolean active;
    private final int totalDurationTicks;
    private final String roomDisplayName;
    private final String mapName;
    private final String dimensionId;
    private final String roomDescription;
    private final String tipLine1;
    private final String tipLine2;
    private final String tipLine3;

    public RoomMatchLoadPacket(
        boolean active,
        int totalDurationTicks,
        String roomDisplayName,
        String mapName,
        String dimensionId,
        String roomDescription,
        String tipLine1,
        String tipLine2,
        String tipLine3
    ) {
        this.active = active;
        this.totalDurationTicks = totalDurationTicks;
        this.roomDisplayName = roomDisplayName == null ? "" : roomDisplayName;
        this.mapName = mapName == null ? "" : mapName;
        this.dimensionId = dimensionId == null ? "" : dimensionId;
        this.roomDescription = roomDescription == null ? "" : roomDescription;
        this.tipLine1 = tipLine1 == null ? "" : tipLine1;
        this.tipLine2 = tipLine2 == null ? "" : tipLine2;
        this.tipLine3 = tipLine3 == null ? "" : tipLine3;
    }

    public static void encode(RoomMatchLoadPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.active);
        buffer.writeInt(message.totalDurationTicks);
        buffer.writeUtf(message.roomDisplayName);
        buffer.writeUtf(message.mapName);
        buffer.writeUtf(message.dimensionId);
        buffer.writeUtf(message.roomDescription);
        buffer.writeUtf(message.tipLine1);
        buffer.writeUtf(message.tipLine2);
        buffer.writeUtf(message.tipLine3);
    }

    public static RoomMatchLoadPacket decode(FriendlyByteBuf buffer) {
        return new RoomMatchLoadPacket(
            buffer.readBoolean(),
            buffer.readInt(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf()
        );
    }

    public static void handle(RoomMatchLoadPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (message.active) {
                ClientMatchLoadState.start(
                    message.totalDurationTicks,
                    message.roomDisplayName,
                    message.mapName,
                    message.dimensionId,
                    message.roomDescription,
                    message.tipLine1,
                    message.tipLine2,
                    message.tipLine3
                );
            } else {
                ClientMatchLoadState.completeLoad();
            }
        }));
        context.setPacketHandled(true);
    }
}
