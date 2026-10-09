package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.parkour.ClientParkourLeaderboardState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ParkourLeaderboardSnapshotPacket {
    private final boolean openPanel;
    private final boolean quickJoinAvailable;
    private final boolean inParkour;
    private final int currentRank;
    private final long bestTimeMillis;
    private final long lastTimeMillis;
    private final String recordHolderName;
    private final long recordHolderTimeMillis;
    private final List<EntryPayload> entries;

    public ParkourLeaderboardSnapshotPacket(
        boolean openPanel,
        boolean quickJoinAvailable,
        boolean inParkour,
        int currentRank,
        long bestTimeMillis,
        long lastTimeMillis,
        String recordHolderName,
        long recordHolderTimeMillis,
        List<EntryPayload> entries
    ) {
        this.openPanel = openPanel;
        this.quickJoinAvailable = quickJoinAvailable;
        this.inParkour = inParkour;
        this.currentRank = currentRank;
        this.bestTimeMillis = bestTimeMillis;
        this.lastTimeMillis = lastTimeMillis;
        this.recordHolderName = recordHolderName == null ? "" : recordHolderName;
        this.recordHolderTimeMillis = recordHolderTimeMillis;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public boolean openPanel() {
        return openPanel;
    }

    public boolean quickJoinAvailable() {
        return quickJoinAvailable;
    }

    public boolean inParkour() {
        return inParkour;
    }

    public int currentRank() {
        return currentRank;
    }

    public long bestTimeMillis() {
        return bestTimeMillis;
    }

    public long lastTimeMillis() {
        return lastTimeMillis;
    }

    public String recordHolderName() {
        return recordHolderName;
    }

    public long recordHolderTimeMillis() {
        return recordHolderTimeMillis;
    }

    public List<EntryPayload> entries() {
        return entries;
    }

    public static void encode(ParkourLeaderboardSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.openPanel);
        buffer.writeBoolean(packet.quickJoinAvailable);
        buffer.writeBoolean(packet.inParkour);
        buffer.writeInt(packet.currentRank);
        buffer.writeLong(packet.bestTimeMillis);
        buffer.writeLong(packet.lastTimeMillis);
        buffer.writeUtf(packet.recordHolderName);
        buffer.writeLong(packet.recordHolderTimeMillis);
        buffer.writeInt(packet.entries.size());
        for (EntryPayload entry : packet.entries) {
            buffer.writeInt(entry.rank());
            buffer.writeUtf(entry.playerName());
            buffer.writeLong(entry.timeMillis());
            buffer.writeBoolean(entry.self());
        }
    }

    public static ParkourLeaderboardSnapshotPacket decode(FriendlyByteBuf buffer) {
        boolean openPanel = buffer.readBoolean();
        boolean quickJoinAvailable = buffer.readBoolean();
        boolean inParkour = buffer.readBoolean();
        int currentRank = buffer.readInt();
        long bestTimeMillis = buffer.readLong();
        long lastTimeMillis = buffer.readLong();
        String recordHolderName = buffer.readUtf();
        long recordHolderTimeMillis = buffer.readLong();
        int entryCount = Math.max(0, buffer.readInt());
        List<EntryPayload> entries = new ArrayList<>();
        for (int index = 0; index < entryCount; index++) {
            entries.add(new EntryPayload(
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readBoolean()
            ));
        }
        return new ParkourLeaderboardSnapshotPacket(
            openPanel,
            quickJoinAvailable,
            inParkour,
            currentRank,
            bestTimeMillis,
            lastTimeMillis,
            recordHolderName,
            recordHolderTimeMillis,
            entries
        );
    }

    public static void handle(ParkourLeaderboardSnapshotPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientParkourLeaderboardState.apply(message)));
        context.setPacketHandled(true);
    }

    public record EntryPayload(int rank, String playerName, long timeMillis, boolean self) {
    }
}
