package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.room.model.RoomCommonConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class RoomFixedLoadoutRules {
    private RoomFixedLoadoutRules() {
    }

    public static List<String> idsForTeamSlot(RoomDefinition room, RoomTeamId teamId, CustomLoadoutSlot slot) {
        RoomCommonConfig common = room == null ? null : room.commonConfig();
        return common == null || teamId == null || slot == null ? List.of() : common.fixedLoadoutIds(teamId, slot);
    }

    public static boolean hasRestriction(RoomDefinition room, RoomTeamId teamId, CustomLoadoutSlot slot) {
        return !idsForTeamSlot(room, teamId, slot).isEmpty();
    }

    public static boolean allowsEmpty(RoomDefinition room, RoomTeamId teamId, CustomLoadoutSlot slot) {
        for (String id : idsForTeamSlot(room, teamId, slot)) {
            if (CustomLoadoutService.isReservedEmptyEquipmentId(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean allowsEntryId(RoomDefinition room, RoomTeamId teamId, CustomLoadoutSlot slot, String equipmentId) {
        if (!hasRestriction(room, teamId, slot)) {
            return true;
        }
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(equipmentId);
        if (normalizedId.isBlank()) {
            return false;
        }
        for (String allowedId : idsForTeamSlot(room, teamId, slot)) {
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
            case "campaprimaryweaponids", "campbprimaryweaponids" -> CustomLoadoutSlot.PRIMARY_WEAPON;
            case "campasecondaryweaponids", "campbsecondaryweaponids" -> CustomLoadoutSlot.SECONDARY_WEAPON;
            case "campameleeweaponids", "campbmeleeweaponids" -> CustomLoadoutSlot.MELEE;
            case "campagadget1ids", "campbgadget1ids" -> CustomLoadoutSlot.GADGET_1;
            case "campagadget2ids", "campbgadget2ids" -> CustomLoadoutSlot.GADGET_2;
            case "campathrowableids", "campbthrowableids" -> CustomLoadoutSlot.THROWABLE;
            default -> null;
        };
    }

    public static RoomTeamId resolveTeamByConfigId(String configId) {
        if (configId == null) {
            return null;
        }
        return configId.trim().toLowerCase(java.util.Locale.ROOT).startsWith("campa")
            ? RoomTeamId.CAMP_A
            : configId.trim().toLowerCase(java.util.Locale.ROOT).startsWith("campb")
            ? RoomTeamId.CAMP_B
            : null;
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

    public static EquipmentDataModel.EquipmentEntry resolveAllowedEntry(RoomDefinition room, EquipmentDataModel equipmentData, RoomTeamId teamId, CustomLoadoutSlot slot, String equipmentId) {
        if (room == null || equipmentData == null || teamId == null || slot == null) {
            return null;
        }
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(equipmentId);
        if (!allowsEntryId(room, teamId, slot, normalizedId)) {
            return null;
        }
        EquipmentDataModel.EquipmentEntry entry = equipmentData.getEquipmentById().get(normalizedId);
        if (entry == null || entry.getSlot() != slot.getIndex()) {
            return null;
        }
        return entry;
    }

    public static EquipmentDataModel.EquipmentEntry resolveFallbackEntry(
        RoomDefinition room,
        EquipmentDataModel equipmentData,
        RoomTeamId teamId,
        CustomLoadoutSlot slot,
        int playerLevel,
        boolean ignoreUnlockLevel
    ) {
        if (room == null || equipmentData == null || teamId == null || slot == null || !hasRestriction(room, teamId, slot)) {
            return null;
        }
        return idsForTeamSlot(room, teamId, slot).stream()
            .filter(id -> !CustomLoadoutService.isReservedEmptyEquipmentId(id))
            .map(id -> equipmentData.getEquipmentById().get(CustomLoadoutService.normalizeEquipmentId(id)))
            .filter(entry -> entry != null && entry.getSlot() == slot.getIndex())
            .filter(entry -> ignoreUnlockLevel || CustomLoadoutService.isEntryUnlocked(entry, playerLevel))
            .sorted(Comparator.comparingInt(EquipmentDataModel.EquipmentEntry::getUnlockLevel)
                .thenComparing(EquipmentDataModel.EquipmentEntry::getId, String.CASE_INSENSITIVE_ORDER))
            .findFirst()
            .orElse(null);
    }
}
