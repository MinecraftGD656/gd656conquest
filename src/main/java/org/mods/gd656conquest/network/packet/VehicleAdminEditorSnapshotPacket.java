package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.config.ClientPacketBypassConfig;
import org.mods.gd656conquest.client.loadout.ClientVehicleAdminEditorState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class VehicleAdminEditorSnapshotPacket {
    private final boolean admin;
    private final boolean openPanel;
    private final List<RoomPayload> rooms;

    public VehicleAdminEditorSnapshotPacket(boolean admin, boolean openPanel, List<RoomPayload> rooms) {
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

    public static void encode(VehicleAdminEditorSnapshotPacket packet, FriendlyByteBuf buffer) {
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
            buffer.writeUtf(room.gameplayId());
            buffer.writeUtf(room.backendValue());
            buffer.writeUtf(room.campADisplayName());
            buffer.writeUtf(room.campBDisplayName());
            buffer.writeInt(room.vehicleEntries().size());
            for (VehicleEntryPayload entry : room.vehicleEntries()) {
                buffer.writeUtf(entry.entryId());
                buffer.writeUtf(entry.team());
                buffer.writeUtf(entry.displayName());
                buffer.writeUtf(entry.vehicleId());
                buffer.writeBoolean(entry.enabled());
                buffer.writeInt(entry.fields().size());
                for (VehicleFieldPayload field : entry.fields()) {
                    buffer.writeUtf(field.fieldKey());
                    buffer.writeUtf(field.displayName());
                    buffer.writeUtf(field.value());
                    buffer.writeBoolean(field.configured());
                    buffer.writeBoolean(field.editable());
                    buffer.writeUtf(field.valueType());
                    writeUtfList(buffer, field.suggestionValues());
                    buffer.writeBoolean(field.supportsQuotedValue());
                }
            }
        }
    }

    public static VehicleAdminEditorSnapshotPacket decode(FriendlyByteBuf buffer) {
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
            String gameplayId = buffer.readUtf();
            String backendValue = buffer.readUtf();
            String campADisplayName = buffer.readUtf();
            String campBDisplayName = buffer.readUtf();
            int entryCount = Math.max(0, buffer.readInt());
            List<VehicleEntryPayload> entries = new ArrayList<>();
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                String entryId = buffer.readUtf();
                String team = buffer.readUtf();
                String entryDisplayName = buffer.readUtf();
                String vehicleId = buffer.readUtf();
                boolean enabled = buffer.readBoolean();
                int fieldCount = Math.max(0, buffer.readInt());
                List<VehicleFieldPayload> fields = new ArrayList<>();
                for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                    fields.add(new VehicleFieldPayload(
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUtf(),
                        readUtfList(buffer),
                        buffer.readBoolean()
                    ));
                }
                entries.add(new VehicleEntryPayload(entryId, team, entryDisplayName, vehicleId, enabled, fields));
            }
            rooms.add(new RoomPayload(
                roomId,
                displayName,
                stateKey,
                stateDisplayName,
                currentPlayers,
                maxPlayers,
                gameplayId,
                backendValue,
                campADisplayName,
                campBDisplayName,
                entries
            ));
        }
        return new VehicleAdminEditorSnapshotPacket(admin, openPanel, rooms);
    }

    public static void handle(VehicleAdminEditorSnapshotPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (ClientPacketBypassConfig.bypassVehicleAdminEditorPackets()) {
                ClientVehicleAdminEditorState.clear();
                return;
            }
            ClientVehicleAdminEditorState.apply(message);
        }));
        context.setPacketHandled(true);
    }

    private static void writeUtfList(FriendlyByteBuf buffer, List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        buffer.writeInt(safeValues.size());
        for (String value : safeValues) {
            buffer.writeUtf(value == null ? "" : value);
        }
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
        String gameplayId,
        String backendValue,
        String campADisplayName,
        String campBDisplayName,
        List<VehicleEntryPayload> vehicleEntries
    ) {
    }

    public record VehicleEntryPayload(
        String entryId,
        String team,
        String displayName,
        String vehicleId,
        boolean enabled,
        List<VehicleFieldPayload> fields
    ) {
    }

    public record VehicleFieldPayload(
        String fieldKey,
        String displayName,
        String value,
        boolean configured,
        boolean editable,
        String valueType,
        List<String> suggestionValues,
        boolean supportsQuotedValue
    ) {
    }
}
