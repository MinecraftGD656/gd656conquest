package org.mods.gd656conquest.client.loadout;

import org.mods.gd656conquest.network.packet.VehicleAdminEditorSnapshotPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientVehicleAdminEditorState {
    private static Snapshot snapshot;
    private static long snapshotUpdatedAtMs;

    private ClientVehicleAdminEditorState() {
    }

    public static synchronized void apply(VehicleAdminEditorSnapshotPacket packet) {
        if (packet == null) {
            return;
        }
        Map<String, RoomState> roomById = new LinkedHashMap<>();
        for (VehicleAdminEditorSnapshotPacket.RoomPayload roomPayload : packet.rooms()) {
            if (roomPayload == null || roomPayload.roomId() == null || roomPayload.roomId().isBlank()) {
                continue;
            }
            List<VehicleEntryState> vehicleEntries = new ArrayList<>();
            if (roomPayload.vehicleEntries() != null) {
                for (VehicleAdminEditorSnapshotPacket.VehicleEntryPayload entryPayload : roomPayload.vehicleEntries()) {
                    if (entryPayload == null || entryPayload.entryId() == null || entryPayload.entryId().isBlank()) {
                        continue;
                    }
                    List<VehicleFieldState> fields = new ArrayList<>();
                    if (entryPayload.fields() != null) {
                        for (VehicleAdminEditorSnapshotPacket.VehicleFieldPayload fieldPayload : entryPayload.fields()) {
                            if (fieldPayload == null || fieldPayload.fieldKey() == null || fieldPayload.fieldKey().isBlank()) {
                                continue;
                            }
                            fields.add(new VehicleFieldState(
                                fieldPayload.fieldKey(),
                                safe(fieldPayload.displayName(), fieldPayload.fieldKey()),
                                safe(fieldPayload.value(), ""),
                                fieldPayload.configured(),
                                fieldPayload.editable(),
                                safe(fieldPayload.valueType(), "STRING"),
                                fieldPayload.suggestionValues() == null ? List.of() : List.copyOf(fieldPayload.suggestionValues()),
                                fieldPayload.supportsQuotedValue()
                            ));
                        }
                    }
                    vehicleEntries.add(new VehicleEntryState(
                        entryPayload.entryId(),
                        safe(entryPayload.team(), ""),
                        safe(entryPayload.displayName(), ""),
                        safe(entryPayload.vehicleId(), ""),
                        entryPayload.enabled(),
                        List.copyOf(fields)
                    ));
                }
            }
            roomById.put(roomPayload.roomId(), new RoomState(
                roomPayload.roomId(),
                safe(roomPayload.displayName(), ""),
                safe(roomPayload.stateKey(), ""),
                safe(roomPayload.stateDisplayName(), ""),
                Math.max(0, roomPayload.currentPlayers()),
                Math.max(0, roomPayload.maxPlayers()),
                safe(roomPayload.gameplayId(), ""),
                safe(roomPayload.backendValue(), ""),
                safe(roomPayload.campADisplayName(), "CampA"),
                safe(roomPayload.campBDisplayName(), "CampB"),
                List.copyOf(vehicleEntries)
            ));
        }
        snapshot = new Snapshot(packet.admin(), roomById);
        snapshotUpdatedAtMs = System.currentTimeMillis();
    }

    public static synchronized Snapshot snapshot() {
        return snapshot;
    }

    public static synchronized boolean hasFreshSnapshot(long maxAgeMs) {
        if (snapshot == null) {
            return false;
        }
        if (maxAgeMs <= 0L) {
            return true;
        }
        long age = System.currentTimeMillis() - snapshotUpdatedAtMs;
        return age >= 0L && age <= maxAgeMs;
    }

    public static synchronized void clear() {
        snapshot = null;
        snapshotUpdatedAtMs = 0L;
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public record Snapshot(
        boolean admin,
        Map<String, RoomState> rooms
    ) {
        public List<RoomState> sortedRooms() {
            return rooms.values().stream()
                .sorted(Comparator.comparing(RoomState::roomId, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    public record RoomState(
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
        List<VehicleEntryState> vehicleEntries
    ) {
    }

    public record VehicleEntryState(
        String entryId,
        String team,
        String displayName,
        String vehicleId,
        boolean enabled,
        List<VehicleFieldState> fields
    ) {
    }

    public record VehicleFieldState(
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
