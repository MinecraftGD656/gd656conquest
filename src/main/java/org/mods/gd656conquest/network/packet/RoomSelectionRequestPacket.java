package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.loadout.state.ClientRoomSelectionState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class RoomSelectionRequestPacket {
    public static void encode(RoomSelectionRequestPacket ignored, FriendlyByteBuf buffer) {
    }

    public static RoomSelectionRequestPacket decode(FriendlyByteBuf buffer) {
        return new RoomSelectionRequestPacket();
    }

    public static void handle(RoomSelectionRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
            List<ClientRoomSelectionState.Entry> entries = new ArrayList<>();
            for (int i = 1; i <= 24; i++) {
                String roomId = "room_" + String.format("%02d", i);
                Optional<RoomDefinition> opt = manager.findRoom(roomId);
                if (opt.isEmpty()) continue;
                RoomDefinition def = opt.get();
                String mapName = def.commonConfig().mapName();
                String roomDescription = def.commonConfig().roomDescription();
                int currentPlayers = def.lifecycleState() == RoomLifecycleState.MATCHING
                    ? manager.findMatchingState(roomId).map(state -> state.getQueuePlayers().size()).orElse(0)
                    : manager.countRoomPlayers(roomId);
                entries.add(new ClientRoomSelectionState.Entry(
                    def.roomId(),
                    def.displayName(),
                    mapName != null ? mapName : "",
                    roomDescription != null ? roomDescription : "",
                    def.gameplayId().value(),
                    def.lifecycleState().name(),
                    currentPlayers,
                    def.commonConfig().matchingConfig().totalMaxPlayers()
                ));
            }
            NetworkManager.sendToPlayer(player, new RoomSelectionSnapshotPacket(entries));
        });
        context.setPacketHandled(true);
    }
}
