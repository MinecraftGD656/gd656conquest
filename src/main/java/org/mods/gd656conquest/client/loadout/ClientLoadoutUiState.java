package org.mods.gd656conquest.client.loadout;

import org.mods.gd656conquest.network.packet.LoadoutUiOpenPacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

public final class ClientLoadoutUiState {
    private static Snapshot snapshot;
    private static boolean requestOpenPanel;
    private static long snapshotUpdatedAtMs;
    
    private static final int MAX_CACHE_SIZE = 500;
    private static final Map<String, ItemStack> ITEM_STACK_CACHE = new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private ClientLoadoutUiState() {
    }

    public static synchronized ItemStack parseAndCacheStack(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        return ITEM_STACK_CACHE.computeIfAbsent(snbt, s -> {
            try {
                CompoundTag tag = TagParser.parseTag(s);
                return ItemStack.of(tag);
            } catch (Exception ignored) {
                return ItemStack.EMPTY;
            }
        });
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
        Map<String, AdminAttachmentState> adminAttachments = new LinkedHashMap<>();
        for (LoadoutUiOpenPacket.AdminAttachmentPayload payload : packet.getAdminAttachments()) {
            String id = payload.id() == null ? "" : payload.id();
            if (id.isBlank()) {
                continue;
            }
            adminAttachments.put(id, new AdminAttachmentState(
                id,
                payload.displayName() == null ? "" : payload.displayName(),
                payload.attachmentType() == null ? "" : payload.attachmentType(),
                Math.max(0, payload.unlockLevel()),
                payload.itemSnbt() == null ? "" : payload.itemSnbt(),
                payload.futureRequirement() == null ? "" : payload.futureRequirement()
            ));
        }
        Map<Integer, WeaponAttachmentPanelState> attachmentPanels = new LinkedHashMap<>();
        for (LoadoutUiOpenPacket.WeaponAttachmentPanelPayload payload : packet.getAttachmentPanels()) {
            Map<String, AttachmentTypeState> typeStates = new LinkedHashMap<>();
            for (LoadoutUiOpenPacket.AttachmentTypePayload typePayload : payload.attachmentTypes()) {
                Map<String, AttachmentChoiceState> choices = new LinkedHashMap<>();
                for (LoadoutUiOpenPacket.AttachmentChoicePayload choicePayload : typePayload.choices()) {
                    String id = choicePayload.id() == null ? "" : choicePayload.id();
                    choices.put(id, new AttachmentChoiceState(
                        id,
                        choicePayload.displayName() == null ? "" : choicePayload.displayName(),
                        Math.max(0, choicePayload.unlockLevel()),
                        choicePayload.unlocked(),
                        choicePayload.itemSnbt() == null ? "" : choicePayload.itemSnbt()
                    ));
                }
                String typeKey = typePayload.attachmentType() == null ? "" : typePayload.attachmentType();
                typeStates.put(typeKey, new AttachmentTypeState(
                    typeKey,
                    typePayload.attachmentLabel() == null ? "" : typePayload.attachmentLabel(),
                    typePayload.available(),
                    typePayload.hasStoredSelection(),
                    typePayload.selectedAttachmentId() == null ? "" : typePayload.selectedAttachmentId(),
                    typePayload.selectedDisplayName() == null ? "" : typePayload.selectedDisplayName(),
                    typePayload.maxScopeMagnification(),
                    choices
                ));
            }
            attachmentPanels.put(payload.slotIndex(), new WeaponAttachmentPanelState(
                payload.slotIndex(),
                payload.slotKey() == null ? "" : payload.slotKey(),
                payload.slotLabel() == null ? "" : payload.slotLabel(),
                payload.loadoutEntryId() == null ? "" : payload.loadoutEntryId(),
                payload.weaponDisplayName() == null ? "" : payload.weaponDisplayName(),
                payload.weaponItemSnbt() == null ? "" : payload.weaponItemSnbt(),
                payload.available(),
                payload.taczGun(),
                typeStates
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
                packet.getPlayerTitleText(),
                packet.getCurrentClassKey(),
                packet.isAdmin(),
                packet.isVehiclePageAvailable(),
                classes,
                adminEquipment,
                adminAttachments,
                attachmentPanels,
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
        ITEM_STACK_CACHE.clear();
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
            String playerTitleText,
            String currentClassKey,
            boolean admin,
            boolean vehiclePageAvailable,
            Map<String, ClassState> classes,
            Map<String, AdminEquipmentState> adminEquipment,
            Map<String, AdminAttachmentState> adminAttachments,
            Map<Integer, WeaponAttachmentPanelState> attachmentPanels,
            List<VehicleEntryState> vehicleEntries
    ) {
        public List<AdminEquipmentState> sortedAdminEquipment() {
            return adminEquipment.values().stream()
                    .sorted((left, right) -> left.id().compareToIgnoreCase(right.id()))
                    .toList();
        }

        public List<AdminAttachmentState> sortedAdminAttachments() {
            return adminAttachments.values().stream()
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

    public record AdminAttachmentState(
        String id,
        String displayName,
        String attachmentType,
        int unlockLevel,
        String itemSnbt,
        String futureRequirement
    ) {
    }

    public record WeaponAttachmentPanelState(
        int slotIndex,
        String slotKey,
        String slotLabel,
        String loadoutEntryId,
        String weaponDisplayName,
        String weaponItemSnbt,
        boolean available,
        boolean taczGun,
        Map<String, AttachmentTypeState> attachmentTypes
    ) {
    }

    public record AttachmentTypeState(
        String attachmentType,
        String attachmentLabel,
        boolean available,
        boolean hasStoredSelection,
        String selectedAttachmentId,
        String selectedDisplayName,
        double maxScopeMagnification,
        Map<String, AttachmentChoiceState> choices
    ) {
        public List<AttachmentChoiceState> sortedChoices() {
            return choices.values().stream()
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

    public record AttachmentChoiceState(
        String id,
        String displayName,
        int unlockLevel,
        boolean unlocked,
        String itemSnbt
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
