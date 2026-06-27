package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientRoomTeamMirror;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RoomTeamMirrorSyncPacket {
    private final boolean active;
    private final String roomId;
    private final String campADisplayName;
    private final String campBDisplayName;
    private final List<String> campAPlayers;
    private final List<String> campBPlayers;
    private final List<PlayerSquadEntry> squadEntries;

    public RoomTeamMirrorSyncPacket(
        boolean active,
        String roomId,
        String campADisplayName,
        String campBDisplayName,
        List<String> campAPlayers,
        List<String> campBPlayers,
        List<PlayerSquadEntry> squadEntries
    ) {
        this.active = active;
        this.roomId = roomId == null ? "-" : roomId;
        this.campADisplayName = campADisplayName == null ? "CampA" : campADisplayName;
        this.campBDisplayName = campBDisplayName == null ? "CampB" : campBDisplayName;
        this.campAPlayers = campAPlayers == null ? List.of() : List.copyOf(campAPlayers);
        this.campBPlayers = campBPlayers == null ? List.of() : List.copyOf(campBPlayers);
        this.squadEntries = squadEntries == null ? List.of() : List.copyOf(squadEntries);
    }

    public static RoomTeamMirrorSyncPacket clear() {
        return new RoomTeamMirrorSyncPacket(false, "-", "CampA", "CampB", List.of(), List.of(), List.of());
    }

    public static void encode(RoomTeamMirrorSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.active);
        buffer.writeUtf(message.roomId);
        buffer.writeUtf(message.campADisplayName);
        buffer.writeUtf(message.campBDisplayName);
        writePlayers(buffer, message.campAPlayers);
        writePlayers(buffer, message.campBPlayers);
        buffer.writeInt(message.squadEntries.size());
        for (PlayerSquadEntry squadEntry : message.squadEntries) {
            buffer.writeUtf(squadEntry.playerName());
            buffer.writeUtf(squadEntry.squadLabel());
        }
    }

    public static RoomTeamMirrorSyncPacket decode(FriendlyByteBuf buffer) {
        return new RoomTeamMirrorSyncPacket(
            buffer.readBoolean(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readUtf(),
            readPlayers(buffer),
            readPlayers(buffer),
            readSquadEntries(buffer)
        );
    }

    public static void handle(RoomTeamMirrorSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientRoomTeamMirror.apply(
                message.active,
                message.roomId,
                message.campADisplayName,
                message.campBDisplayName,
                message.campAPlayers,
                message.campBPlayers,
                message.squadEntries
            )
        ));
        context.setPacketHandled(true);
    }

    private static void writePlayers(FriendlyByteBuf buffer, List<String> players) {
        buffer.writeInt(players.size());
        for (String playerName : players) {
            buffer.writeUtf(playerName == null ? "" : playerName);
        }
    }

    private static List<String> readPlayers(FriendlyByteBuf buffer) {
        int size = Math.max(0, buffer.readInt());
        List<String> players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            players.add(buffer.readUtf());
        }
        return players;
    }

    private static List<PlayerSquadEntry> readSquadEntries(FriendlyByteBuf buffer) {
        int size = Math.max(0, buffer.readInt());
        List<PlayerSquadEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new PlayerSquadEntry(buffer.readUtf(), buffer.readUtf()));
        }
        return entries;
    }

    public record PlayerSquadEntry(String playerName, String squadLabel) {
        public PlayerSquadEntry {
            playerName = playerName == null ? "" : playerName;
            squadLabel = squadLabel == null ? "" : squadLabel;
        }
    }
}
