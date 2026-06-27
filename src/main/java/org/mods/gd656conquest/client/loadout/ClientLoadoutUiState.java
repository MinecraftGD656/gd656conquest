package org.mods.gd656conquest.client.loadout;

import org.mods.gd656conquest.network.packet.LoadoutUiOpenPacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientLoadoutUiState {
    private static Snapshot snapshot;
    private static boolean requestOpenPanel;
    private static long snapshotUpdatedAtMs;

    private ClientLoadoutUiState() {
    }

    public static synchronized void apply(LoadoutUiOpenPacket packet) {
        if (packet == null) {
            return;
        }
        Map<String, ClassState> classes = new LinkedHashMap<>();
        for (LoadoutUiOpenPacket.ClassPayload classPayload : packet.getClasses()) {
            Map<Integer, SlotState> slots = new LinkedHashMap<>();
            for (LoadoutUiOpenPacket.SlotPayload slotPayload : classPayload.slots()) {
                Map<String, EntryState> entries = new LinkedHashMap<>();
                for (LoadoutUiOpenPacket.EntryPayload entryPayload : slotPayload.entries()) {
                    String id = entryPayload.id() == null ? "" : entryPayload.id();
                    entries.put(id, new EntryState(
                            id,
                            entryPayload.displayName() == null ? "" : entryPayload.displayName(),
                            Math.max(0, entryPayload.unlockLevel()),
                            entryPayload.unlocked(),
                            entryPayload.itemSnbt() == null ? "" : entryPayload.itemSnbt()
                    ));
                }
                slots.put(slotPayload.slotIndex(), new SlotState(
                        slotPayload.slotIndex(),
                        slotPayload.slotKey() == null ? "" : slotPayload.slotKey(),
                        slotPayload.slotLabel() == null ? "" : slotPayload.slotLabel(),
                        slotPayload.selectedId() == null ? "" : slotPayload.selectedId(),
                        entries
                ));
            }
            classes.put(classPayload.classKey(), new ClassState(
                    classPayload.classKey() == null ? "" : classPayload.classKey(),
                    classPayload.className() == null ? "" : classPayload.className(),
                    slots
            ));
        }
        Map<String, AdminEquipmentState> adminEquipment = new LinkedHashMap<>();
        for (LoadoutUiOpenPacket.AdminEquipmentPayload payload : packet.getAdminEquipment()) {
            String id = payload.id() == null ? "" : payload.id();
            if (id.isBlank()) {
                continue;
            }
            adminEquipment.put(id, new AdminEquipmentState(
                    id,
                    payload.slot(),
                    payload.slotName() == null ? "" : payload.slotName(),
                    payload.displayName() == null ? "" : payload.displayName(),
                    payload.extraData(),
                    Math.max(0, payload.unlockLevel()),
                    payload.availableClasses(),
                    payload.itemSnbt() == null ? "" : payload.itemSnbt(),
                    Math.max(0, payload.replenishSeconds()),
                    payload.ammoBoxRefillEnabled()
            ));
        }
        List<VehicleEntryState> vehicleEntries = packet.getVehicleEntries().stream()
                .map(payload -> new VehicleEntryState(
                        payload.entryId() == null ? "" : payload.entryId(),
                        payload.displayName() == null ? "" : payload.displayName(),
                        payload.stateToken() == null ? "" : payload.stateToken(),
                        payload.statusText() == null ? "" : payload.statusText(),
                        payload.actionText() == null ? "" : payload.actionText(),
                        payload.actionable(),
                        payload.selected(),
                        payload.waitingForReady(),
                        payload.immediateDeploy(),
                        payload.lowHealth(),
                        payload.readyRemainSeconds(),
                        payload.healthPercent(),
                        payload.healthCurrent(),
                        payload.healthMax(),
                        payload.occupiedSeatCount(),
                        payload.seatCount(),
                        payload.detailText() == null ? "" : payload.detailText()
                ))
                .toList();
        snapshot = new Snapshot(
                Math.max(0, packet.getPlayerLevel()),
                packet.getCurrentClassKey(),
                packet.isAdmin(),
                packet.isVehiclePageAvailable(),
                classes,
                adminEquipment,
                vehicleEntries
        );
        snapshotUpdatedAtMs = System.currentTimeMillis();
        if (packet.isOpenPanel()) {
            requestOpenPanel = true;
        }
    }

    public static synchronized Snapshot snapshot() {
        return snapshot;
    }

    public static synchronized boolean consumeOpenPanelRequest() {
        boolean current = requestOpenPanel;
        requestOpenPanel = false;
        return current;
    }

    public static synchronized void clear() {
        snapshot = null;
        requestOpenPanel = false;
        snapshotUpdatedAtMs = 0L;
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

    public static synchronized long snapshotAgeMs() {
        if (snapshot == null || snapshotUpdatedAtMs <= 0L) {
            return Long.MAX_VALUE;
        }
        long age = System.currentTimeMillis() - snapshotUpdatedAtMs;
        return Math.max(0L, age);
    }

    public static synchronized long snapshotUpdatedAtMs() {
        return snapshotUpdatedAtMs;
    }

    public record Snapshot(
            int playerLevel,
            String currentClassKey,
            boolean admin,
            boolean vehiclePageAvailable,
            Map<String, ClassState> classes,
            Map<String, AdminEquipmentState> adminEquipment,
            List<VehicleEntryState> vehicleEntries
    ) {
        public List<AdminEquipmentState> sortedAdminEquipment() {
            return adminEquipment.values().stream()
                    .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
                    .toList();
        }

        public List<VehicleEntryState> sortedVehicleEntries() {
            return vehicleEntries.stream()
                    .sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()))
                    .toList();
        }
    }

    public record ClassState(String classKey, String className, Map<Integer, SlotState> slots) {
    }

    public record SlotState(int slotIndex, String slotKey, String slotLabel, String selectedId, Map<String, EntryState> entries) {
        public List<EntryState> sortedEntries() {
            return entries.values().stream()
                    .sorted((left, right) -> {
                        int unlockCompare = Integer.compare(left.unlockLevel(), right.unlockLevel());
                        if (unlockCompare != 0) {
                            return unlockCompare;
                        }
                        return left.id().compareToIgnoreCase(right.id());
                    })
                    .toList();
        }
    }

    public record EntryState(String id, String displayName, int unlockLevel, boolean unlocked, String itemSnbt) {
    }

    public record AdminEquipmentState(
            String id,
            int slot,
            String slotName,
            String displayName,
            int extraData,
            int unlockLevel,
            int availableClasses,
            String itemSnbt,
            int replenishSeconds,
            boolean ammoBoxRefillEnabled
    ) {
    }

    public record VehicleEntryState(
            String entryId,
            String displayName,
            String stateToken,
            String statusText,
            String actionText,
            boolean actionable,
            boolean selected,
            boolean waitingForReady,
            boolean immediateDeploy,
            boolean lowHealth,
            int readyRemainSeconds,
            int healthPercent,
            int healthCurrent,
            int healthMax,
            int occupiedSeatCount,
            int seatCount,
            String detailText
    ) {
    }
}
