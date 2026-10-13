package org.mods.gd656conquest.server.loadout.application;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.log.ClientMessageType;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomFixedLoadoutRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CustomLoadoutService {
    public static final String EMPTY_EQUIPMENT_ID = "EMPTY";
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private CustomLoadoutService() {
    }

    public static String normalizeEquipmentId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    public static boolean isValidEquipmentId(String normalizedId) {
        return normalizedId != null && !normalizedId.isBlank() && ID_PATTERN.matcher(normalizedId).matches();
    }

    public static boolean isReservedEmptyEquipmentId(String normalizedId) {
        return EMPTY_EQUIPMENT_ID.equalsIgnoreCase(normalizedId == null ? "" : normalizedId.trim());
    }

    public static EquipmentDataModel.EquipmentEntry getEntryById(EquipmentDataModel data, String normalizedId) {
        if (data == null || normalizedId == null) {
            return null;
        }
        return data.getEquipmentById().get(normalizedId);
    }

    public static List<EquipmentDataModel.EquipmentEntry> listEntriesBySlot(EquipmentDataModel data, CustomLoadoutSlot slot) {
        if (data == null || slot == null) {
            return List.of();
        }
        List<EquipmentDataModel.EquipmentEntry> list = new ArrayList<>();
        for (EquipmentDataModel.EquipmentEntry entry : data.getEquipmentById().values()) {
            if (entry == null || entry.getSlot() != slot.getIndex()) {
                continue;
            }
            list.add(entry);
        }
        list.sort(Comparator.comparingInt(EquipmentDataModel.EquipmentEntry::getUnlockLevel)
            .thenComparing(EquipmentDataModel.EquipmentEntry::getId, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public static EquipmentDataModel.EquipmentEntry createReservedEmptyEntry(CustomLoadoutSlot slot) {
        if (slot == null) {
            return null;
        }
        EquipmentDataModel.EquipmentEntry entry = new EquipmentDataModel.EquipmentEntry();
        entry.setId(EMPTY_EQUIPMENT_ID);
        entry.setSlot(slot.getIndex());
        entry.setSlotName(slot.getKey());
        entry.setDisplayName("空");
        entry.setUnlockLevel(101);
        entry.setAvailableClasses(1234);
        entry.setItemSnbt("");
        entry.setReplenishSeconds(0);
        entry.setAmmoBoxRefillEnabled(false);
        return entry;
    }

    public static List<EquipmentDataModel.EquipmentEntry> listEntriesByClassAndSlot(
        EquipmentDataModel data,
        PlayerClassType classType,
        CustomLoadoutSlot slot
    ) {
        List<EquipmentDataModel.EquipmentEntry> bySlot = listEntriesBySlot(data, slot);
        if (bySlot.isEmpty()) {
            return bySlot;
        }
        List<EquipmentDataModel.EquipmentEntry> filtered = new ArrayList<>();
        for (EquipmentDataModel.EquipmentEntry entry : bySlot) {
            if (isEntryAvailableForClass(entry, classType)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public static boolean isEntryUnlocked(EquipmentDataModel.EquipmentEntry entry, int playerLevel) {
        return entry != null && playerLevel >= Math.max(0, entry.getUnlockLevel());
    }

    public static boolean isEntryAvailableForClass(EquipmentDataModel.EquipmentEntry entry, PlayerClassType classType) {
        if (entry == null || classType == null) {
            return false;
        }
        Set<Integer> digits = PlayerClassType.parseAvailableDigits(entry.getAvailableClasses());
        if (digits.isEmpty()) {
            return true;
        }
        return digits.contains(classType.getDigit());
    }

    public static String makeClassSlotKey(PlayerClassType classType, CustomLoadoutSlot slot) {
        PlayerClassType safeClassType = classType == null ? PlayerClassType.defaultType() : classType;
        return safeClassType.getKey() + "." + slot.getKey();
    }

    public static String makeCampClassKey(RoomTeamId teamId, PlayerClassType classType) {
        RoomTeamId safeTeamId = teamId == null ? RoomTeamId.CAMP_A : teamId;
        PlayerClassType safeClassType = classType == null ? PlayerClassType.defaultType() : classType;
        return safeTeamId.name().toLowerCase(Locale.ROOT) + "." + safeClassType.getKey();
    }

    public static PlayerClassType resolveCurrentClass(PlayerDataModel playerData) {
        if (playerData == null) {
            return PlayerClassType.defaultType();
        }
        PlayerClassType classType = PlayerClassType.fromInput(playerData.getCurrentClassType());
        return classType == null ? PlayerClassType.defaultType() : classType;
    }

    public static PlayerClassType resolveCurrentClass(PlayerDataModel playerData, RunningRuntimeState state, UUID playerId) {
        if (state != null && playerId != null) {
            PlayerClassType active = PlayerClassType.fromInput(state.getActiveLoadoutClassKeysByPlayer().get(playerId));
            if (active != null) return active;
        }
        return resolveSelectedClass(playerData, state, playerId);
    }

    /** The editor shows the next deployment preference, while live abilities read the granted class. */
    public static PlayerClassType resolveSelectedClass(PlayerDataModel playerData, RunningRuntimeState state, UUID playerId) {
        if (state != null && playerId != null) {
            PlayerClassType runtimeClassType = PlayerClassType.fromInput(state.getRandomizedLoadoutClassKeysByPlayer().get(playerId));
            if (runtimeClassType != null) {
                return runtimeClassType;
            }
        }
        return resolveCurrentClass(playerData);
    }

    public static String getSelectedIdForClassSlot(PlayerDataModel playerData, PlayerClassType classType, CustomLoadoutSlot slot) {
        if (playerData == null || slot == null) {
            return "";
        }
        Map<String, String> selection = playerData.getCustomLoadoutSelectionBySlot();
        String classSlotKey = makeClassSlotKey(classType, slot);
        String selected = normalizeEquipmentId(selection.get(classSlotKey));
        if (!selected.isBlank()) {
            return selected;
        }
        String legacy = normalizeEquipmentId(selection.get(slot.getKey()));
        if (!legacy.isBlank()) {
            selection.put(classSlotKey, legacy);
            selection.remove(slot.getKey());
            return legacy;
        }
        for (String legacyClassKey : getLegacyClassKeys(classType)) {
            String legacyClassSlotKey = legacyClassKey + "." + slot.getKey();
            String legacyClassSelected = normalizeEquipmentId(selection.get(legacyClassSlotKey));
            if (!legacyClassSelected.isBlank()) {
                selection.put(classSlotKey, legacyClassSelected);
                selection.remove(legacyClassSlotKey);
                return legacyClassSelected;
            }
        }
        return "";
    }

    public static String getSelectedIdForRoomSlot(RunningRuntimeState state, UUID playerId, PlayerClassType classType, CustomLoadoutSlot slot) {
        if (state == null || playerId == null || classType == null || slot == null) {
            return "";
        }
        Map<String, String> selection = state.getFixedRoomLoadoutSelectionIdsByPlayerSlot().get(playerId);
        if (selection == null || selection.isEmpty()) {
            return "";
        }
        return normalizeEquipmentId(selection.get(makeClassSlotKey(classType, slot)));
    }

    public static void setSelectedIdForRoomSlot(
        RunningRuntimeState state,
        UUID playerId,
        PlayerClassType classType,
        CustomLoadoutSlot slot,
        String normalizedId
    ) {
        if (state == null || playerId == null || classType == null || slot == null) {
            return;
        }
        Map<String, String> selection = state.getFixedRoomLoadoutSelectionIdsByPlayerSlot()
            .computeIfAbsent(playerId, ignored -> new java.util.LinkedHashMap<>());
        String classSlotKey = makeClassSlotKey(classType, slot);
        if (normalizedId == null || normalizedId.isBlank()) {
            selection.remove(classSlotKey);
        } else {
            selection.put(classSlotKey, normalizeEquipmentId(normalizedId));
        }
        if (selection.isEmpty()) {
            state.getFixedRoomLoadoutSelectionIdsByPlayerSlot().remove(playerId);
        }
    }

    public static String resolveLowestLevelAllowedIdForSlot(
        EquipmentDataModel data,
        CustomLoadoutSlot slot,
        int playerLevel,
        RoomDefinition room
    ) {
        return resolveLowestLevelAllowedIdForSlot(data, slot, playerLevel, room, null);
    }

    public static String resolveLowestLevelAllowedIdForSlot(
        EquipmentDataModel data,
        CustomLoadoutSlot slot,
        int playerLevel,
        RoomDefinition room,
        RoomTeamId teamId
    ) {
        if (room != null && RoomFixedLoadoutRules.hasRestriction(room, teamId, slot)) {
            EquipmentDataModel.EquipmentEntry entry = RoomFixedLoadoutRules.resolveFallbackEntry(room, data, teamId, slot, playerLevel, false);
            if (entry != null) {
                return normalizeEquipmentId(entry.getId());
            }
            return RoomFixedLoadoutRules.allowsEmpty(room, teamId, slot) ? EMPTY_EQUIPMENT_ID : null;
        }
        for (EquipmentDataModel.EquipmentEntry entry : listEntriesBySlot(data, slot)) {
            if (isEntryUnlocked(entry, playerLevel)) {
                return normalizeEquipmentId(entry.getId());
            }
        }
        return null;
    }

    public static void setSelectedIdForClassSlot(
        PlayerDataModel playerData,
        PlayerClassType classType,
        CustomLoadoutSlot slot,
        String normalizedId
    ) {
        if (playerData == null || slot == null) {
            return;
        }
        Map<String, String> selection = playerData.getCustomLoadoutSelectionBySlot();
        String classSlotKey = makeClassSlotKey(classType, slot);
        if (normalizedId == null || normalizedId.isBlank()) {
            selection.remove(classSlotKey);
            return;
        }
        selection.put(classSlotKey, normalizeEquipmentId(normalizedId));
    }

    public static String resolveFallbackIdForClassSlot(
        EquipmentDataModel data,
        PlayerClassType classType,
        CustomLoadoutSlot slot,
        int playerLevel
    ) {
        if (data == null || slot == null || classType == null) {
            return null;
        }
        String defaultId = normalizeEquipmentId(data.getDefaultIdBySlot().get(makeClassSlotKey(classType, slot)));
        if (defaultId.isBlank()) {
            for (String legacyClassKey : getLegacyClassKeys(classType)) {
                String legacyClassSlotKey = legacyClassKey + "." + slot.getKey();
                defaultId = normalizeEquipmentId(data.getDefaultIdBySlot().get(legacyClassSlotKey));
                if (!defaultId.isBlank()) {
                    data.getDefaultIdBySlot().put(makeClassSlotKey(classType, slot), defaultId);
                    data.getDefaultIdBySlot().remove(legacyClassSlotKey);
                    break;
                }
            }
        }
        if (defaultId.isBlank()) {
            defaultId = normalizeEquipmentId(data.getDefaultIdBySlot().get(slot.getKey()));
        }
        if (!defaultId.isBlank()) {
            EquipmentDataModel.EquipmentEntry defaultEntry = data.getEquipmentById().get(defaultId);
            if (defaultEntry != null
                && defaultEntry.getSlot() == slot.getIndex()
                && isEntryUnlocked(defaultEntry, playerLevel)
                && isEntryAvailableForClass(defaultEntry, classType)) {
                return defaultId;
            }
        }
        for (EquipmentDataModel.EquipmentEntry entry : listEntriesByClassAndSlot(data, classType, slot)) {
            if (isEntryUnlocked(entry, playerLevel)) {
                return normalizeEquipmentId(entry.getId());
            }
        }
        return null;
    }

    public static EquipmentDataModel.ArmorEntry resolveArmorByCampClass(
        EquipmentDataModel data,
        RoomTeamId teamId,
        PlayerClassType classType
    ) {
        if (data == null || classType == null) {
            return null;
        }
        String key = makeCampClassKey(teamId, classType);
        EquipmentDataModel.ArmorEntry entry = data.getArmorByCampClass().get(key);
        if (entry != null) {
            return entry;
        }
        for (String legacyClassKey : getLegacyClassKeys(classType)) {
            String legacyKey = teamKey(teamId) + "." + legacyClassKey;
            EquipmentDataModel.ArmorEntry legacyEntry = data.getArmorByCampClass().get(legacyKey);
            if (legacyEntry != null) {
                data.getArmorByCampClass().put(key, legacyEntry);
                data.getArmorByCampClass().remove(legacyKey);
                return legacyEntry;
            }
        }
        return null;
    }

    public static boolean sanitizePlayerSelection(ServerPlayer player, EquipmentDataModel data, String deletedId, boolean notifyPlayer) {
        if (player == null || player.server == null || data == null) {
            return false;
        }
        PlayerDataStore playerDataHandler = ConquestDataManager.of(player.server).playerData();
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        PlayerClassType currentClass = resolveCurrentClass(playerData);
        int playerLevel = PlayerLevelService.getEffectiveLevel(player);
        boolean changed = false;
        String normalizedDeletedId = normalizeEquipmentId(deletedId);
        for (PlayerClassType classType : PlayerClassType.values()) {
            for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
                String selectedId = getSelectedIdForClassSlot(playerData, classType, slot);
                String fallbackId = resolveFallbackIdForClassSlot(data, classType, slot, playerLevel);
                if (selectedId.isBlank()) {
                    if (fallbackId != null) {
                        setSelectedIdForClassSlot(playerData, classType, slot, fallbackId);
                        changed = true;
                    }
                    continue;
                }
                EquipmentDataModel.EquipmentEntry selectedEntry = data.getEquipmentById().get(selectedId);
                boolean invalid = selectedEntry == null
                    || selectedEntry.getSlot() != slot.getIndex()
                    || !isEntryUnlocked(selectedEntry, playerLevel)
                    || !isEntryAvailableForClass(selectedEntry, classType);
                if (!invalid) {
                    continue;
                }
                changed = true;
                if (fallbackId == null) {
                    setSelectedIdForClassSlot(playerData, classType, slot, "");
                    if (notifyPlayer && classType == currentClass) {
                        ServerLogger.sendToClient(
                            player,
                            ClientMessageType.WARNING,
                            "浣犲綋鍓嶅叺绉嶇殑 %s 閰嶈宸插け鏁堜笖鏆傛棤鍙敤榛樿椤癸紝宸叉竻绌鸿妲戒綅",
                            slot.getKey()
                        );
                    }
                    continue;
                }
                setSelectedIdForClassSlot(playerData, classType, slot, fallbackId);
                if (notifyPlayer && classType == currentClass) {
                    EquipmentDataModel.EquipmentEntry fallbackEntry = data.getEquipmentById().get(fallbackId);
                    String fallbackDisplay = fallbackEntry == null ? fallbackId : fallbackEntry.getDisplayName();
                    if (!normalizedDeletedId.isBlank() && Objects.equals(selectedId, normalizedDeletedId)) {
                        ServerLogger.sendToClient(
                            player,
                            ClientMessageType.WARNING,
                            "你选择的装备 %s 已被服务器删除，%s 已自动切换为默认：%s",
                            selectedId,
                            slot.getKey(),
                            fallbackDisplay
                        );
                    } else {
                        ServerLogger.sendToClient(
                            player,
                            ClientMessageType.WARNING,
                            "你当前兵种的 %s 配装已失效，已自动切换为默认：%s",
                            slot.getKey(),
                            fallbackDisplay
                        );
                    }
                }
            }
        }
        if (changed) {
            playerDataHandler.savePlayerData(player.getUUID(), playerData);
        }
        return changed;
    }

    private static List<String> getLegacyClassKeys(PlayerClassType classType) {
        if (classType == PlayerClassType.SUPPORT) {
            return List.of("medic");
        }
        return List.of();
    }

    private static String teamKey(RoomTeamId teamId) {
        RoomTeamId safeTeamId = teamId == null ? RoomTeamId.CAMP_A : teamId;
        return safeTeamId.name().toLowerCase(Locale.ROOT);
    }
}


