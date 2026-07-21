package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.network.packet.RoomEditorSnapshotPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class RoomGunmasterWeaponPoolRules {
    private RoomGunmasterWeaponPoolRules() {
    }

    public static boolean isWeaponPoolConfigId(String configId) {
        if (configId == null) {
            return false;
        }
        return switch (configId.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "gminitialweaponpool", "gmmiddleweaponpool", "gmfinalweaponpool" -> true;
            default -> false;
        };
    }

    public static List<String> suggestionIds(EquipmentDataModel data) {
        if (data == null) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        allEntries(data).stream()
            .map(EquipmentDataModel.EquipmentEntry::getId)
            .map(CustomLoadoutService::normalizeEquipmentId)
            .filter(id -> !id.isBlank())
            .forEach(ids::add);
        return new ArrayList<>(ids);
    }

    public static List<RoomEditorSnapshotPacket.GroupChoicePayload> buildChoices(EquipmentDataModel data) {
        if (data == null) {
            return List.of();
        }
        List<RoomEditorSnapshotPacket.GroupChoicePayload> payloads = new ArrayList<>();
        for (EquipmentDataModel.EquipmentEntry entry : allEntries(data)) {
            if (entry == null) {
                continue;
            }
            String id = CustomLoadoutService.normalizeEquipmentId(entry.getId());
            if (id.isBlank()) {
                continue;
            }
            payloads.add(new RoomEditorSnapshotPacket.GroupChoicePayload(
                id,
                safeString(entry.getDisplayName(), id),
                id,
                safeString(entry.getItemSnbt(), "")
            ));
        }
        return payloads;
    }

    public static EquipmentDataModel.EquipmentEntry resolveEntry(EquipmentDataModel data, String equipmentId) {
        if (data == null) {
            return null;
        }
        String normalized = CustomLoadoutService.normalizeEquipmentId(equipmentId);
        return normalized.isBlank() ? null : data.getEquipmentById().get(normalized);
    }

    private static List<EquipmentDataModel.EquipmentEntry> allEntries(EquipmentDataModel data) {
        if (data == null) {
            return List.of();
        }
        List<EquipmentDataModel.EquipmentEntry> entries = new ArrayList<>(data.getEquipmentById().values());
        entries.removeIf(entry -> entry == null || CustomLoadoutService.isReservedEmptyEquipmentId(entry.getId()));
        entries.sort(Comparator.comparingInt(EquipmentDataModel.EquipmentEntry::getUnlockLevel)
            .thenComparingInt(EquipmentDataModel.EquipmentEntry::getSlot)
            .thenComparing(entry -> safeString(entry.getId(), ""), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static String safeString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
