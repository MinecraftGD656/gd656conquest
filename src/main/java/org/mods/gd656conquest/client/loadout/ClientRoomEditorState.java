package org.mods.gd656conquest.client.loadout;

import org.mods.gd656conquest.network.packet.RoomEditorSnapshotPacket;
import org.mods.gd656conquest.server.room.config.RoomValidationMessageFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class ClientRoomEditorState {
    private static Snapshot snapshot;
    private static long snapshotUpdatedAtMs;
    private static boolean requestOpenPanel;

    private ClientRoomEditorState() {
    }

    public static synchronized void apply(RoomEditorSnapshotPacket packet) {
        if (packet == null) {
            return;
        }
        Map<String, RoomState> roomById = new LinkedHashMap<>();
        for (RoomEditorSnapshotPacket.RoomPayload roomPayload : packet.rooms()) {
            if (roomPayload == null || roomPayload.roomId() == null || roomPayload.roomId().isBlank()) {
                continue;
            }
            List<RoomConfigEntry> configEntries = new ArrayList<>();
            if (roomPayload.configEntries() != null) {
                for (RoomEditorSnapshotPacket.ConfigEntryPayload configPayload : roomPayload.configEntries()) {
                    if (configPayload == null || configPayload.keyPath() == null || configPayload.keyPath().isBlank()) {
                        continue;
                    }
                    configEntries.add(new RoomConfigEntry(
                            configPayload.keyPath(),
                            configPayload.editKey() == null || configPayload.editKey().isBlank() ? configPayload.keyPath() : configPayload.editKey(),
                            configPayload.displayName() == null ? configPayload.keyPath() : configPayload.displayName(),
                            configPayload.scope() == null ? "" : configPayload.scope(),
                            configPayload.category() == null ? "" : configPayload.category(),
                            configPayload.value() == null ? "" : configPayload.value(),
                            configPayload.configured(),
                            configPayload.required(),
                            configPayload.editable(),
                            configPayload.applicable(),
                            configPayload.highlighted(),
                            configPayload.valueType() == null ? "" : configPayload.valueType(),
                            configPayload.suggestionValues() == null ? List.of() : List.copyOf(configPayload.suggestionValues()),
                            configPayload.supportsQuotedValue(),
                            configPayload.supportsGroupOperations(),
                            configPayload.groupValues() == null ? List.of() : List.copyOf(configPayload.groupValues())
                    ));
                }
            }
            List<String> missingItems = roomPayload.missingItems() == null ? List.of() : roomPayload.missingItems();
            List<String> missingConfigIds = roomPayload.missingConfigIds() == null ? List.of() : roomPayload.missingConfigIds();
            roomById.put(roomPayload.roomId(), new RoomState(
                    roomPayload.roomId(),
                    roomPayload.displayName() == null ? "" : roomPayload.displayName(),
                    roomPayload.stateKey() == null ? "" : roomPayload.stateKey(),
                    roomPayload.stateDisplayName() == null ? "" : roomPayload.stateDisplayName(),
                    Math.max(0, roomPayload.currentPlayers()),
                    Math.max(0, roomPayload.maxPlayers()),
                    List.copyOf(missingItems),
                    List.copyOf(missingConfigIds),
                    List.copyOf(configEntries)
            ));
        }
        snapshot = new Snapshot(packet.admin(), roomById);
        snapshotUpdatedAtMs = System.currentTimeMillis();
        if (packet.openPanel()) {
            requestOpenPanel = true;
        }
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

    public static synchronized boolean consumeOpenPanelRequest() {
        boolean current = requestOpenPanel;
        requestOpenPanel = false;
        return current;
    }

    public static synchronized void clear() {
        snapshot = null;
        snapshotUpdatedAtMs = 0L;
        requestOpenPanel = false;
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
            List<String> missingItems,
            List<String> missingConfigIds,
            List<RoomConfigEntry> configEntries
    ) {
        public boolean hasMissingRequirements() {
            return !(missingItems == null || missingItems.isEmpty())
                || !(missingConfigIds == null || missingConfigIds.isEmpty());
        }

        public int missingSummaryCount() {
            return missingSummaryLabels().size();
        }

        public List<String> missingSummaryLabels() {
            LinkedHashSet<String> labels = new LinkedHashSet<>();
            Map<String, String> labelsByConfigId = new LinkedHashMap<>();
            Map<String, String> labelsByKeyPath = new LinkedHashMap<>();
            if (configEntries != null) {
                for (RoomConfigEntry entry : configEntries) {
                    if (entry == null) {
                        continue;
                    }
                    String display = entry.displayName() == null || entry.displayName().isBlank()
                            ? fallbackLabel(entry.editKey(), entry.keyPath())
                            : entry.displayName();
                    String configKey = normalizeKey(entry.editKey());
                    if (configKey != null && !labelsByConfigId.containsKey(configKey)) {
                        labelsByConfigId.put(configKey, display);
                    }
                    String pathKey = normalizeKey(entry.keyPath());
                    if (pathKey != null && !labelsByKeyPath.containsKey(pathKey)) {
                        labelsByKeyPath.put(pathKey, display);
                    }
                }
            }
            List<String> fallbackLabels = RoomValidationMessageFormatter.labels(missingConfigIds, missingItems);
            if (missingConfigIds != null) {
                for (String configId : missingConfigIds) {
                    String key = normalizeKey(configId);
                    if (key == null) {
                        continue;
                    }
                    labels.add(labelsByConfigId.getOrDefault(key, RoomValidationMessageFormatter.labelForConfigId(configId)));
                }
            }
            if (missingItems != null) {
                for (String item : missingItems) {
                    String key = normalizeKey(item);
                    if (key == null) {
                        continue;
                    }
                    labels.add(labelsByKeyPath.getOrDefault(key, RoomValidationMessageFormatter.labelForMissingItem(item)));
                }
            }
            labels.addAll(fallbackLabels);
            return List.copyOf(labels);
        }

        private static String fallbackLabel(String editKey, String keyPath) {
            if (editKey != null && !editKey.isBlank()) {
                return editKey.trim();
            }
            if (keyPath != null && !keyPath.isBlank()) {
                return keyPath.trim();
            }
            return "";
        }

        private static String normalizeKey(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            return normalized.toLowerCase(Locale.ROOT);
        }

    }

    public record RoomConfigEntry(
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
