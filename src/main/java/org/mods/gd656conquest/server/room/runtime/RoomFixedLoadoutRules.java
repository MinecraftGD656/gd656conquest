package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
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

    /**
     * GD656Mods: 限定槽位是否忽略"兵种可用性"(EquipmentEntry.availableClasses)。
     *
     * <p>历史行为: 某个槽位一旦配置了限定列表, 该槽位就无视兵种限制 —— 全兵种都能拿到该槽位的装备,
     * 包括本该属于特定兵种的道具。这被保留为默认行为, 由房间配置
     * {@code commonConfig.ignoreClassLimitOnRestriction} 控制(默认 true = 保持历史行为);
     * 关掉它则会在此后的限定分支里追加兵种过滤。
     */
    public static boolean ignoresClassLimitOnRestriction(RoomDefinition room) {
        if (room == null || room.commonConfig() == null) {
            return true;
        }
        return room.commonConfig().ignoresClassLimitOnRestriction();
    }

    /** GD656Mods: 房间是否开启"武器忽略枪械等级"(默认 false)。 */
    public static boolean ignoresGunLevel(RoomDefinition room) {
        return room != null && room.commonConfig() != null && room.commonConfig().ignoreGunLevel();
    }

    /**
     * GD656Mods: 限定分支里的兵种可见性判定 —— 统一入口, 避免每个调用点各写一遍 if。
     * 房间配置为"忽略"时恒为 true(保持历史行为)。
     */
    public static boolean allowsEntryForClass(
        RoomDefinition room,
        EquipmentDataModel.EquipmentEntry entry,
        PlayerClassType classType
    ) {
        if (entry == null) {
            return false;
        }
        if (ignoresClassLimitOnRestriction(room)) {
            return true;
        }
        return classType == null || CustomLoadoutService.isEntryAvailableForClass(entry, classType);
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
        // GD656Mods: 旧签名保留(等价于不按兵种过滤 = 历史行为); 需要兵种过滤的调用请用带 classType 的重载
        return resolveFallbackEntry(room, equipmentData, teamId, slot, playerLevel, ignoreUnlockLevel, null);
    }

    /**
     * GD656Mods: 带兵种信息的兜底项解析 —— 房间关掉"限定槽位忽略兵种限制"时,
     * 兜底项也只会挑该兵种可用的装备。
     */
    public static EquipmentDataModel.EquipmentEntry resolveFallbackEntry(
        RoomDefinition room,
        EquipmentDataModel equipmentData,
        RoomTeamId teamId,
        CustomLoadoutSlot slot,
        int playerLevel,
        boolean ignoreUnlockLevel,
        PlayerClassType classType
    ) {
        if (room == null || equipmentData == null || teamId == null || slot == null || !hasRestriction(room, teamId, slot)) {
            return null;
        }
        return idsForTeamSlot(room, teamId, slot).stream()
            .filter(id -> !CustomLoadoutService.isReservedEmptyEquipmentId(id))
            .map(id -> equipmentData.getEquipmentById().get(CustomLoadoutService.normalizeEquipmentId(id)))
            .filter(entry -> entry != null && entry.getSlot() == slot.getIndex())
            .filter(entry -> allowsEntryForClass(room, entry, classType))
            .filter(entry -> ignoreUnlockLevel || CustomLoadoutService.isEntryUnlocked(entry, playerLevel))
            .sorted(Comparator.comparingInt(EquipmentDataModel.EquipmentEntry::getUnlockLevel)
                .thenComparing(EquipmentDataModel.EquipmentEntry::getId, String.CASE_INSENSITIVE_ORDER))
            .findFirst()
            .orElse(null);
    }
}
