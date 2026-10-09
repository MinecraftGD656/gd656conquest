package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.loadout.state.ClientRoomSelectionState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RoomSelectionSnapshotPacket {
    private final List<ClientRoomSelectionState.Entry> entries;

    public RoomSelectionSnapshotPacket(List<ClientRoomSelectionState.Entry> entries) {
        this.entries = entries;
    }

    public static void encode(RoomSelectionSnapshotPacket message, FriendlyByteBuf buffer) {
        List<ClientRoomSelectionState.Entry> entries = message.entries;
        buffer.writeVarInt(entries.size());
        for (ClientRoomSelectionState.Entry e : entries) {
            buffer.writeUtf(e.roomId());
            buffer.writeUtf(e.displayName());
            buffer.writeUtf(e.mapName());
            buffer.writeUtf(e.roomDescription());
            buffer.writeUtf(e.gameplayId());
            buffer.writeUtf(e.lifecycleState());
            buffer.writeVarInt(e.currentPlayers());
            buffer.writeVarInt(e.maxPlayers());
        }
    }

    public static RoomSelectionSnapshotPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<ClientRoomSelectionState.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new ClientRoomSelectionState.Entry(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt()
            ));
        }
        return new RoomSelectionSnapshotPacket(entries);
    }

    public static void handle(RoomSelectionSnapshotPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ClientRoomSelectionState.apply(new ClientRoomSelectionState.Snapshot(message.entries))));
        context.setPacketHandled(true);
    }
}
