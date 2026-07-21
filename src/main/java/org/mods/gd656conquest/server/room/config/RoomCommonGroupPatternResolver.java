package org.mods.gd656conquest.server.room.config;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterWeaponPoolRules;
import org.mods.gd656conquest.server.room.runtime.RoomFixedLoadoutRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class RoomCommonGroupPatternResolver {
    private RoomCommonGroupPatternResolver() {
    }

    public static List<String> resolveAddValues(String canonical, String normalizedValue, ServerPlayer editor) {
        return resolveMatchedValues(canonical, normalizedValue, resolveCandidateValuesForAdd(canonical, editor), "导入");
    }

    public static List<String> resolveDeleteValues(String canonical, String normalizedValue, List<String> currentValues) {
        List<String> candidates = currentValues == null ? List.of() : currentValues;
        return resolveMatchedValues(canonical, normalizedValue, candidates, "删除");
    }

    private static List<String> resolveMatchedValues(String canonical, String normalizedValue, List<String> candidates, String actionName) {
        if (!supportsWildcard(canonical) || !isWildcardPattern(normalizedValue)) {
            return List.of(normalizedValue);
        }
        String prefix = normalizedValue.substring(0, normalizedValue.length() - 1);
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("批量" + actionName + "前缀不能为空");
        }
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String candidate : candidates) {
            String value = normalizeCandidateValue(candidate);
            if (!value.isBlank() && value.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matched.add(value);
            }
        }
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("未找到匹配前缀 " + normalizedValue + " 的配置值");
        }
        return new ArrayList<>(matched);
    }

    private static List<String> resolveCandidateValuesForAdd(String canonical, ServerPlayer editor) {
        if (editor == null || editor.server == null) {
            throw new IllegalArgumentException("批量导入配置值时需要玩家上下文");
        }
        ConquestDataManager dataManager = ConquestDataManager.of(editor.server);
        if (isHeroKitGroup(canonical)) {
            List<String> values = new ArrayList<>(dataManager.heroKits().get().getHeroKitsById().keySet());
            values.sort(String.CASE_INSENSITIVE_ORDER);
            return values;
        }
        if (isShellingGroup(canonical)) {
            List<String> values = new ArrayList<>(dataManager.shellings().get().getShellingsById().keySet());
            values.sort(String.CASE_INSENSITIVE_ORDER);
            return values;
        }
        if (RoomFixedLoadoutRules.resolveSlotByConfigId(canonical) != null) {
            CustomLoadoutSlot slot = RoomFixedLoadoutRules.resolveSlotByConfigId(canonical);
            return RoomFixedLoadoutRules.suggestionIdsForSlot(dataManager.equipmentData().getData(), slot);
        }
        if (RoomGunmasterWeaponPoolRules.isWeaponPoolConfigId(canonical)) {
            return RoomGunmasterWeaponPoolRules.suggestionIds(dataManager.equipmentData().getData());
        }
        return List.of();
    }

    private static boolean supportsWildcard(String canonical) {
        return isHeroKitGroup(canonical)
            || isShellingGroup(canonical)
            || RoomFixedLoadoutRules.resolveSlotByConfigId(canonical) != null
            || RoomGunmasterWeaponPoolRules.isWeaponPoolConfigId(canonical);
    }

    private static boolean isHeroKitGroup(String canonical) {
        return "supportcampaherokits".equals(canonical) || "supportcampbherokits".equals(canonical);
    }

    private static boolean isShellingGroup(String canonical) {
        return "supportcampashellings".equals(canonical) || "supportcampbshellings".equals(canonical);
    }

    private static boolean isWildcardPattern(String normalizedValue) {
        return normalizedValue != null
            && normalizedValue.endsWith("*")
            && normalizedValue.indexOf('*') == normalizedValue.length() - 1;
    }

    private static String normalizeCandidateValue(String value) {
        return value == null ? "" : value.trim();
    }
}
