package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.marker.ClientRoomMarkerState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class RoomMarkerSyncPacket {
    private final List<MarkerEntry> markers;

    public RoomMarkerSyncPacket(List<MarkerEntry> markers) {
        this.markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public static RoomMarkerSyncPacket clear() {
        return new RoomMarkerSyncPacket(List.of());
    }

    public static void encode(RoomMarkerSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.markers.size());
        for (MarkerEntry marker : packet.markers) {
            buffer.writeUUID(marker.ownerId());
            buffer.writeUtf(marker.ownerName());
            buffer.writeUtf(marker.kind());
            buffer.writeInt(marker.blockX());
            buffer.writeInt(marker.blockY());
            buffer.writeInt(marker.blockZ());
            buffer.writeBoolean(marker.targetPlayerId() != null);
            if (marker.targetPlayerId() != null) {
                buffer.writeUUID(marker.targetPlayerId());
            }
            buffer.writeVarInt(marker.ageTicks());
            buffer.writeVarInt(marker.remainingTicks());
        }
    }

    public static RoomMarkerSyncPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<MarkerEntry> markers = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            markers.add(new MarkerEntry(
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readVarInt(),
                buffer.readVarInt()
            ));
        }
        return new RoomMarkerSyncPacket(markers);
    }

    public static void handle(RoomMarkerSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRoomMarkerState.update(message.markers)));
        context.setPacketHandled(true);
    }

    public record MarkerEntry(
        UUID ownerId,
        String ownerName,
        String kind,
        int blockX,
        int blockY,
        int blockZ,
        UUID targetPlayerId,
        int ageTicks,
        int remainingTicks
    ) {
        public MarkerEntry {
            ownerId = ownerId == null ? new UUID(0L, 0L) : ownerId;
            ownerName = ownerName == null ? "" : ownerName;
            kind = kind == null ? "NORMAL" : kind;
            targetPlayerId = targetPlayerId != null && targetPlayerId.getLeastSignificantBits() == 0L && targetPlayerId.getMostSignificantBits() == 0L
                ? null
                : targetPlayerId;
            ageTicks = Math.max(0, ageTicks);
            remainingTicks = Math.max(0, remainingTicks);
        }
    }
}
