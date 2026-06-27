package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.config.ClientPacketBypassConfig;
import org.mods.gd656conquest.client.loadout.ClientRoomEditorState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RoomEditorSnapshotPacket {
    private final boolean admin;
    private final boolean openPanel;
    private final List<RoomPayload> rooms;

    public RoomEditorSnapshotPacket(boolean admin, boolean openPanel, List<RoomPayload> rooms) {
        this.admin = admin;
        this.openPanel = openPanel;
        this.rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }

    public boolean admin() {
        return admin;
    }

    public boolean openPanel() {
        return openPanel;
    }

    public List<RoomPayload> rooms() {
        return rooms;
    }

    public static void encode(RoomEditorSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.admin);
        buffer.writeBoolean(packet.openPanel);
        buffer.writeInt(packet.rooms.size());
        for (RoomPayload room : packet.rooms) {
            buffer.writeUtf(room.roomId());
            buffer.writeUtf(room.displayName());
            buffer.writeUtf(room.stateKey());
            buffer.writeUtf(room.stateDisplayName());
            buffer.writeInt(room.currentPlayers());
            buffer.writeInt(room.maxPlayers());
            buffer.writeInt(room.missingItems().size());
            for (String item : room.missingItems()) {
                buffer.writeUtf(item == null ? "" : item);
            }
            buffer.writeInt(room.missingConfigIds().size());
            for (String configId : room.missingConfigIds()) {
                buffer.writeUtf(configId == null ? "" : configId);
            }
            buffer.writeInt(room.configEntries().size());
            for (ConfigEntryPayload configEntry : room.configEntries()) {
                buffer.writeUtf(configEntry.keyPath());
                buffer.writeUtf(configEntry.editKey());
                buffer.writeUtf(configEntry.displayName());
                buffer.writeUtf(configEntry.scope());
                buffer.writeUtf(configEntry.category());
                buffer.writeUtf(configEntry.value());
                buffer.writeBoolean(configEntry.configured());
                buffer.writeBoolean(configEntry.required());
                buffer.writeBoolean(configEntry.editable());
                buffer.writeBoolean(configEntry.applicable());
                buffer.writeBoolean(configEntry.highlighted());
                buffer.writeUtf(configEntry.valueType());
                buffer.writeInt(configEntry.suggestionValues().size());
                for (String suggestionValue : configEntry.suggestionValues()) {
                    buffer.writeUtf(suggestionValue == null ? "" : suggestionValue);
                }
                buffer.writeBoolean(configEntry.supportsQuotedValue());
                buffer.writeBoolean(configEntry.supportsGroupOperations());
                buffer.writeInt(configEntry.groupValues().size());
                for (String groupValue : configEntry.groupValues()) {
                    buffer.writeUtf(groupValue == null ? "" : groupValue);
                }
            }
        }
    }

    public static RoomEditorSnapshotPacket decode(FriendlyByteBuf buffer) {
        boolean admin = buffer.readBoolean();
        boolean openPanel = buffer.readBoolean();
        int roomCount = Math.max(0, buffer.readInt());
        List<RoomPayload> rooms = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            String roomId = buffer.readUtf();
            String displayName = buffer.readUtf();
            String stateKey = buffer.readUtf();
            String stateDisplayName = buffer.readUtf();
            int currentPlayers = buffer.readInt();
            int maxPlayers = buffer.readInt();
            int missingCount = Math.max(0, buffer.readInt());
            List<String> missingItems = new ArrayList<>();
            for (int m = 0; m < missingCount; m++) {
                missingItems.add(buffer.readUtf());
            }
            int missingConfigCount = Math.max(0, buffer.readInt());
            List<String> missingConfigIds = new ArrayList<>();
            for (int m = 0; m < missingConfigCount; m++) {
                missingConfigIds.add(buffer.readUtf());
            }
            int configCount = Math.max(0, buffer.readInt());
            List<ConfigEntryPayload> configEntries = new ArrayList<>();
            for (int c = 0; c < configCount; c++) {
                String keyPath = buffer.readUtf();
                String editKey = buffer.readUtf();
                String configDisplayName = buffer.readUtf();
                String scope = buffer.readUtf();
                String category = buffer.readUtf();
                String value = buffer.readUtf();
                boolean configured = buffer.readBoolean();
                boolean required = buffer.readBoolean();
                boolean editable = buffer.readBoolean();
                boolean applicable = buffer.readBoolean();
                boolean highlighted = buffer.readBoolean();
                String valueType = buffer.readUtf();
                int suggestionCount = Math.max(0, buffer.readInt());
                List<String> suggestionValues = new ArrayList<>();
                for (int s = 0; s < suggestionCount; s++) {
                    suggestionValues.add(buffer.readUtf());
                }
                configEntries.add(new ConfigEntryPayload(
                        keyPath,
                        editKey,
                        configDisplayName,
                        scope,
                        category,
                        value,
                        configured,
                        required,
                        editable,
                        applicable,
                        highlighted,
                        valueType,
                        suggestionValues,
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        readUtfList(buffer)
                ));
            }
            rooms.add(new RoomPayload(roomId, displayName, stateKey, stateDisplayName, currentPlayers, maxPlayers, missingItems, missingConfigIds, configEntries));
        }
        return new RoomEditorSnapshotPacket(admin, openPanel, rooms);
    }

    public static void handle(RoomEditorSnapshotPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (ClientPacketBypassConfig.bypassRoomEditorPackets()) {
                ClientRoomEditorState.clear();
                return;
            }
            ClientRoomEditorState.apply(message);
        }));
        context.setPacketHandled(true);
    }

    private static List<String> readUtfList(FriendlyByteBuf buffer) {
        int size = Math.max(0, buffer.readInt());
        List<String> values = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            values.add(buffer.readUtf());
        }
        return values;
    }

    public record RoomPayload(
            String roomId,
            String displayName,
            String stateKey,
            String stateDisplayName,
            int currentPlayers,
            int maxPlayers,
            List<String> missingItems,
            List<String> missingConfigIds,
            List<ConfigEntryPayload> configEntries
    ) {
    }

    public record ConfigEntryPayload(
            String keyPath,
            String editKey,
            String displayName,
            String scope,
            String category,
            String value,
            boolean configured,
            boolean required,
            boolean editable,
            boolean applicable,
            boolean highlighted,
            String valueType,
            List<String> suggestionValues,
            boolean supportsQuotedValue,
            boolean supportsGroupOperations,
            List<String> groupValues
    ) {
    }
}
