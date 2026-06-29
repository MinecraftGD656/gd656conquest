package org.mods.gd656conquest.server.loadout.application;

import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.loadout.TaczAttachmentCompatService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WeaponAttachmentService {
    public static final String TYPE_SCOPE = "scope";
    public static final String TYPE_MUZZLE = "muzzle";
    public static final String TYPE_STOCK = "stock";
    public static final String TYPE_GRIP = "grip";
    public static final String TYPE_LASER = "laser";
    public static final String TYPE_EXTENDED_MAG = "extended_mag";
    public static final List<String> ORDERED_ATTACHMENT_TYPES = List.of(
        TYPE_SCOPE,
        TYPE_MUZZLE,
        TYPE_STOCK,
        TYPE_GRIP,
        TYPE_LASER,
        TYPE_EXTENDED_MAG
    );

    private static final Map<String, String> LABELS = Map.of(
        TYPE_SCOPE, "瞄准镜",
        TYPE_MUZZLE, "枪口配件",
        TYPE_STOCK, "枪托",
        TYPE_GRIP, "前握把",
        TYPE_LASER, "激光指示器",
        TYPE_EXTENDED_MAG, "扩容弹匣"
    );

    private WeaponAttachmentService() {
    }

    public static boolean supportsSlot(CustomLoadoutSlot slot) {
        return slot == CustomLoadoutSlot.PRIMARY_WEAPON || slot == CustomLoadoutSlot.SECONDARY_WEAPON;
    }

    public static String normalizeAttachmentId(String raw) {
        return CustomLoadoutService.normalizeEquipmentId(raw);
    }

    public static boolean isValidAttachmentId(String raw) {
        return CustomLoadoutService.isValidEquipmentId(normalizeAttachmentId(raw));
    }

    public static String normalizeAttachmentType(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return ORDERED_ATTACHMENT_TYPES.contains(normalized) ? normalized : "";
    }

    public static boolean isSupportedAttachmentType(String raw) {
        return !normalizeAttachmentType(raw).isBlank();
    }

    public static String getAttachmentTypeLabel(String typeKey) {
        return LABELS.getOrDefault(normalizeAttachmentType(typeKey), normalizeAttachmentType(typeKey));
    }

    public static List<WeaponAttachmentDataModel.WeaponAttachmentEntry> listEntriesByType(
        WeaponAttachmentDataModel data,
        String attachmentType
    ) {
        String normalizedType = normalizeAttachmentType(attachmentType);
        if (data == null || normalizedType.isBlank()) {
            return List.of();
        }
        List<WeaponAttachmentDataModel.WeaponAttachmentEntry> result = new ArrayList<>();
        for (WeaponAttachmentDataModel.WeaponAttachmentEntry entry : data.getAttachmentById().values()) {
            if (entry == null || !normalizedType.equals(normalizeAttachmentType(entry.getAttachmentType()))) {
                continue;
            }
            result.add(entry);
        }
        result.sort(Comparator.comparingInt(WeaponAttachmentDataModel.WeaponAttachmentEntry::getUnlockLevel)
            .thenComparing(WeaponAttachmentDataModel.WeaponAttachmentEntry::getId, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static boolean isEntryUnlocked(WeaponAttachmentDataModel.WeaponAttachmentEntry entry, int playerLevel) {
        return entry != null && playerLevel >= Math.max(0, entry.getUnlockLevel());
    }

    public static Map<String, Map<String, String>> getSelectionByLoadoutEntryId(PlayerDataModel playerData) {
        return playerData == null ? Map.of() : playerData.getAttachmentSelectionByLoadoutEntryId();
    }

    public static Map<String, String> getSelectionForLoadoutEntry(PlayerDataModel playerData, String loadoutEntryId) {
        if (playerData == null) {
            return Map.of();
        }
        String normalizedLoadoutEntryId = normalizeAttachmentId(loadoutEntryId);
        if (normalizedLoadoutEntryId.isBlank()) {
            return Map.of();
        }
        return playerData.getAttachmentSelectionByLoadoutEntryId()
            .computeIfAbsent(normalizedLoadoutEntryId, ignored -> new LinkedHashMap<>());
    }

    public static String getSelectedAttachmentId(PlayerDataModel playerData, String loadoutEntryId, String attachmentType) {
        if (playerData == null) {
            return "";
        }
        String normalizedLoadoutEntryId = normalizeAttachmentId(loadoutEntryId);
        String normalizedType = normalizeAttachmentType(attachmentType);
        if (normalizedLoadoutEntryId.isBlank() || normalizedType.isBlank()) {
            return "";
        }
        Map<String, String> selection = playerData.getAttachmentSelectionByLoadoutEntryId().get(normalizedLoadoutEntryId);
        if (selection == null) {
            return "";
        }
        return normalizeAttachmentId(selection.get(normalizedType));
    }

    public static boolean hasStoredSelection(PlayerDataModel playerData, String loadoutEntryId, String attachmentType) {
        if (playerData == null) {
            return false;
        }
        String normalizedLoadoutEntryId = normalizeAttachmentId(loadoutEntryId);
        String normalizedType = normalizeAttachmentType(attachmentType);
        if (normalizedLoadoutEntryId.isBlank() || normalizedType.isBlank()) {
            return false;
        }
        Map<String, String> selection = playerData.getAttachmentSelectionByLoadoutEntryId().get(normalizedLoadoutEntryId);
        return selection != null && selection.containsKey(normalizedType);
    }

    public static void setSelectedAttachmentId(
        PlayerDataModel playerData,
        String loadoutEntryId,
        String attachmentType,
        String attachmentEntryId,
        boolean explicitNone
    ) {
        if (playerData == null) {
            return;
        }
        String normalizedLoadoutEntryId = normalizeAttachmentId(loadoutEntryId);
        String normalizedType = normalizeAttachmentType(attachmentType);
        String normalizedAttachmentId = normalizeAttachmentId(attachmentEntryId);
        if (normalizedLoadoutEntryId.isBlank() || normalizedType.isBlank()) {
            return;
        }
        Map<String, Map<String, String>> selectionByLoadoutEntry = playerData.getAttachmentSelectionByLoadoutEntryId();
        if (!explicitNone && normalizedAttachmentId.isBlank()) {
            Map<String, String> existing = selectionByLoadoutEntry.get(normalizedLoadoutEntryId);
            if (existing == null) {
                return;
            }
            existing.remove(normalizedType);
            if (existing.isEmpty()) {
                selectionByLoadoutEntry.remove(normalizedLoadoutEntryId);
            }
            return;
        }
        Map<String, String> selection = selectionByLoadoutEntry.computeIfAbsent(normalizedLoadoutEntryId, ignored -> new LinkedHashMap<>());
        selection.put(normalizedType, normalizedAttachmentId);
    }

    public static boolean sanitizeSelectionForWeapon(
        PlayerDataModel playerData,
        String loadoutEntryId,
        ItemStack weaponStack,
        WeaponAttachmentDataModel attachmentData,
        int playerLevel,
        TaczAttachmentCompatService compatService
    ) {
        if (playerData == null || attachmentData == null || compatService == null) {
            return false;
        }
        String normalizedLoadoutEntryId = normalizeAttachmentId(loadoutEntryId);
        if (normalizedLoadoutEntryId.isBlank()) {
            return false;
        }
        Map<String, String> selection = playerData.getAttachmentSelectionByLoadoutEntryId().get(normalizedLoadoutEntryId);
        if (selection == null || selection.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Set<String> keys = new LinkedHashSet<>(selection.keySet());
        for (String rawType : keys) {
            String normalizedType = normalizeAttachmentType(rawType);
            if (normalizedType.isBlank()) {
                selection.remove(rawType);
                changed = true;
                continue;
            }
            String selectedId = normalizeAttachmentId(selection.get(rawType));
            if (selectedId.isBlank()) {
                if (!compatService.allowAttachmentType(weaponStack, normalizedType)) {
                    selection.remove(rawType);
                    changed = true;
                }
                continue;
            }
            WeaponAttachmentDataModel.WeaponAttachmentEntry entry = attachmentData.getAttachmentById().get(selectedId);
            if (entry == null
                || !normalizedType.equals(normalizeAttachmentType(entry.getAttachmentType()))
                || !isEntryUnlocked(entry, playerLevel)
                || !compatService.isOfficialAttachmentSnbt(entry.getItemSnbt())
                || !compatService.allowAttachment(weaponStack, compatService.deserializeStack(entry.getItemSnbt()))) {
                selection.remove(rawType);
                changed = true;
            }
        }
        if (selection.isEmpty()) {
            playerData.getAttachmentSelectionByLoadoutEntryId().remove(normalizedLoadoutEntryId);
            changed = true;
        }
        return changed;
    }

    public static void renameLoadoutEntryId(PlayerDataModel playerData, String oldId, String newId) {
        if (playerData == null) {
            return;
        }
        String normalizedOldId = normalizeAttachmentId(oldId);
        String normalizedNewId = normalizeAttachmentId(newId);
        if (normalizedOldId.isBlank() || Objects.equals(normalizedOldId, normalizedNewId)) {
            return;
        }
        Map<String, Map<String, String>> selection = playerData.getAttachmentSelectionByLoadoutEntryId();
        Map<String, String> oldSelection = selection.remove(normalizedOldId);
        if (oldSelection == null) {
            return;
        }
        if (normalizedNewId.isBlank()) {
            return;
        }
        Map<String, String> target = selection.computeIfAbsent(normalizedNewId, ignored -> new LinkedHashMap<>());
        target.putAll(oldSelection);
    }

    public static boolean replaceAttachmentEntryId(PlayerDataModel playerData, String oldId, String newId) {
        if (playerData == null) {
            return false;
        }
        String normalizedOldId = normalizeAttachmentId(oldId);
        String normalizedNewId = normalizeAttachmentId(newId);
        if (normalizedOldId.isBlank() || Objects.equals(normalizedOldId, normalizedNewId)) {
            return false;
        }
        boolean changed = false;
        for (Map<String, String> selection : playerData.getAttachmentSelectionByLoadoutEntryId().values()) {
            if (selection == null || selection.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String> entry : new ArrayList<>(selection.entrySet())) {
                if (!normalizedOldId.equals(normalizeAttachmentId(entry.getValue()))) {
                    continue;
                }
                if (normalizedNewId.isBlank()) {
                    selection.remove(entry.getKey());
                } else {
                    selection.put(entry.getKey(), normalizedNewId);
                }
                changed = true;
            }
        }
        cleanupEmptySelectionGroups(playerData);
        return changed;
    }

    public static void cleanupEmptySelectionGroups(PlayerDataModel playerData) {
        if (playerData == null) {
            return;
        }
        playerData.getAttachmentSelectionByLoadoutEntryId().entrySet().removeIf(entry -> entry == null
            || normalizeAttachmentId(entry.getKey()).isBlank()
            || entry.getValue() == null
            || entry.getValue().isEmpty());
    }
}
