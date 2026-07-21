package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.room.model.RoomCommonConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class RoomFixedLoadoutRules {
    private RoomFixedLoadoutRules() {
    }

    public static List<String> idsForSlot(RoomDefinition room, CustomLoadoutSlot slot) {
        RoomCommonConfig common = room == null ? null : room.commonConfig();
        return common == null || slot == null ? List.of() : common.fixedLoadoutIds(slot);
    }

    public static boolean hasRestriction(RoomDefinition room, CustomLoadoutSlot slot) {
        return !idsForSlot(room, slot).isEmpty();
    }

    public static boolean allowsEmpty(RoomDefinition room, CustomLoadoutSlot slot) {
        for (String id : idsForSlot(room, slot)) {
            if (CustomLoadoutService.isReservedEmptyEquipmentId(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean allowsEntryId(RoomDefinition room, CustomLoadoutSlot slot, String equipmentId) {
        if (!hasRestriction(room, slot)) {
            return true;
        }
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(equipmentId);
        if (normalizedId.isBlank()) {
            return false;
        }
        for (String allowedId : idsForSlot(room, slot)) {
            if (CustomLoadoutService.isReservedEmptyEquipmentId(allowedId)) {
                continue;
            }
            if (allowedId.equalsIgnoreCase(normalizedId)) {
                return true;
            }
        }
        return false;
    }

    public static CustomLoadoutSlot resolveSlotByConfigId(String configId) {
        if (configId == null) {
            return null;
        }
        return switch (configId.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "roomprimaryweaponids" -> CustomLoadoutSlot.PRIMARY_WEAPON;
            case "roomsecondaryweaponids" -> CustomLoadoutSlot.SECONDARY_WEAPON;
            case "roommeleeweaponids" -> CustomLoadoutSlot.MELEE;
            case "roomgadget1ids" -> CustomLoadoutSlot.GADGET_1;
            case "roomgadget2ids" -> CustomLoadoutSlot.GADGET_2;
            case "roomthrowableids" -> CustomLoadoutSlot.THROWABLE;
            default -> null;
        };
    }

    public static List<String> suggestionIdsForSlot(EquipmentDataModel equipmentData, CustomLoadoutSlot slot) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(CustomLoadoutService.EMPTY_EQUIPMENT_ID);
        for (EquipmentDataModel.EquipmentEntry entry : CustomLoadoutService.listEntriesBySlot(equipmentData, slot)) {
            if (entry == null) {
                continue;
            }
            String normalizedId = CustomLoadoutService.normalizeEquipmentId(entry.getId());
            if (!normalizedId.isBlank()) {
                ids.add(normalizedId);
            }
        }
        return new ArrayList<>(ids);
    }

    public static EquipmentDataModel.EquipmentEntry resolveAllowedEntry(RoomDefinition room, EquipmentDataModel equipmentData, CustomLoadoutSlot slot, String equipmentId) {
        if (room == null || equipmentData == null || slot == null) {
            return null;
        }
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(equipmentId);
        if (!allowsEntryId(room, slot, normalizedId)) {
            return null;
        }
        EquipmentDataModel.EquipmentEntry entry = equipmentData.getEquipmentById().get(normalizedId);
        if (entry == null || entry.getSlot() != slot.getIndex()) {
            return null;
        }
        return entry;
    }

    public static EquipmentDataModel.EquipmentEntry resolveFallbackEntry(RoomDefinition room, EquipmentDataModel equipmentData, CustomLoadoutSlot slot, int playerLevel) {
        if (room == null || equipmentData == null || slot == null || !hasRestriction(room, slot)) {
            return null;
        }
        return idsForSlot(room, slot).stream()
            .filter(id -> !CustomLoadoutService.isReservedEmptyEquipmentId(id))
            .map(id -> equipmentData.getEquipmentById().get(CustomLoadoutService.normalizeEquipmentId(id)))
            .filter(entry -> entry != null && entry.getSlot() == slot.getIndex())
            .filter(entry -> CustomLoadoutService.isEntryUnlocked(entry, playerLevel))
            .sorted(Comparator.comparingInt(EquipmentDataModel.EquipmentEntry::getUnlockLevel)
                .thenComparing(EquipmentDataModel.EquipmentEntry::getId, String.CASE_INSENSITIVE_ORDER))
            .findFirst()
            .orElse(null);
    }
}
