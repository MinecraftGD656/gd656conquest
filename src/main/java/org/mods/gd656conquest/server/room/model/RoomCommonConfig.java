package org.mods.gd656conquest.server.room.model;

import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record RoomCommonConfig(
    RoomMatchingConfig matchingConfig,
    String dimensionId,
    RoomPosition queueSpawn,
    int respawnDelaySeconds,
    RoomVehicleBackend vehicleBackend,
    RoomPlayerStateConfig playerStateConfig,
    int outsideBoundaryDeathSeconds,
    int aircraftOutsideBoundaryCleanupSeconds,
    int playerMaxY,
    int aircraftPassengerMaxY,
    String campADisplayName,
    String campBDisplayName,
    String campAVoicePack,
    String campBVoicePack,
    double progressSpeed,
    RoomViewPosition matchLoadViewPosition,
    RoomResultConfig resultConfig,
    boolean disableHunger,
    boolean enableAutoTeamSwitchPrompt,
    boolean disableInventoryChange,
    boolean enableGoatHorn,
    boolean enablePlayerDowned,
    boolean enableRespawnCountdownWhileDowned,
    boolean enableBackAgainstWall,
    boolean enableRandomLoadout,
    boolean enableRandomAttachments,
    boolean enableParachute,
    boolean enableDeathSupportDrop,
    boolean enableGiveArrow,
    boolean disableUndyingTotem,
    boolean forceDisableGadgets,
    Boolean allowPlayerCampSwitch,
    List<String> supportCampAHeroKits,
    List<String> supportCampBHeroKits,
    List<String> supportCampAShellings,
    List<String> supportCampBShellings,
    List<String> campAPrimaryWeaponIds,
    List<String> campASecondaryWeaponIds,
    List<String> campAMeleeWeaponIds,
    List<String> campAGadget1Ids,
    List<String> campAGadget2Ids,
    List<String> campAThrowableIds,
    List<String> campBPrimaryWeaponIds,
    List<String> campBSecondaryWeaponIds,
    List<String> campBMeleeWeaponIds,
    List<String> campBGadget1Ids,
    List<String> campBGadget2Ids,
    List<String> campBThrowableIds,
    String campAEquipmentSetId,
    String campBEquipmentSetId,
    String mapName,
    String roomDescription,
    /**
     * GD656Mods: 限定槽位是否忽略兵种限制 —— 默认 true, 即保持历史行为:
     * 某槽位一旦配置了限定列表, 该槽位就无视兵种(availableClasses)限制, 全兵种可用(含跨兵种道具)。
     * 关掉它则限定列表内仍按兵种过滤。
     * 刻意使用包装类型: 老房间配置里没有该字段, 反序列化得到 null;
     * 若用基本类型 boolean, Gson 会填成 false, 导致升级后默认行为静默反转。
     */
    Boolean ignoreClassLimitOnRestriction,
    /**
     * GD656Mods: 房间所用武器是否忽略枪械等级 —— 默认 false(保持原行为)。
     * 开启后玩家在本房间可选用"未解锁"的武器槽装备(配装 GUI 里标作试用);
     * 一旦离开本房间(或登录时已不在本房间), 由 {@code CustomLoadoutService.sanitizePlayerSelection}
     * 统一把"选中了未解锁武器"回退成该兵种的初始默认武器。
     * 用基本类型即可: 默认值就是 false, 老配置缺失该字段时 Gson 也填 false, 与默认一致(不像默认 true 的项必须用包装类型)。
     */
    boolean ignoreGunLevel
) {
    public RoomCommonConfig {
        matchingConfig = matchingConfig == null ? RoomMatchingConfig.defaults() : matchingConfig;
        vehicleBackend = vehicleBackend == null ? RoomVehicleBackend.defaultValue() : vehicleBackend;
        playerStateConfig = playerStateConfig == null ? RoomPlayerStateConfig.defaults() : playerStateConfig;
        matchLoadViewPosition = matchLoadViewPosition; // null = not configured
        resultConfig = resultConfig == null ? RoomResultConfig.defaults() : resultConfig;
        dimensionId = normalizeDimensionId(dimensionId);
        respawnDelaySeconds = Math.max(0, respawnDelaySeconds);
        outsideBoundaryDeathSeconds = Math.max(0, outsideBoundaryDeathSeconds);
        aircraftOutsideBoundaryCleanupSeconds = Math.max(0, aircraftOutsideBoundaryCleanupSeconds);
        // GD656Mods: 玩家最大Y轴支持负数 —— 1.17 起世界最低 Y 为 -64, 地下房间可配置负数上限;
        // 0 仍表示未配置(不检查高度)。
        playerMaxY = Math.max(-64, playerMaxY);
        aircraftPassengerMaxY = Math.max(0, aircraftPassengerMaxY);
        campADisplayName = normalizeDisplayName(campADisplayName, "CampA");
        campBDisplayName = normalizeDisplayName(campBDisplayName, "CampB");
        campAVoicePack = normalizeVoicePack(campAVoicePack);
        campBVoicePack = normalizeVoicePack(campBVoicePack);
        progressSpeed = Double.isFinite(progressSpeed) && progressSpeed > 0.0D ? progressSpeed : 40.0D;
        allowPlayerCampSwitch = allowPlayerCampSwitch == null ? Boolean.TRUE : allowPlayerCampSwitch;
        supportCampAHeroKits = normalizeStringIds(supportCampAHeroKits, false);
        supportCampBHeroKits = normalizeStringIds(supportCampBHeroKits, false);
        supportCampAShellings = normalizeStringIds(supportCampAShellings, false);
        supportCampBShellings = normalizeStringIds(supportCampBShellings, false);
        campAPrimaryWeaponIds = normalizeStringIds(campAPrimaryWeaponIds, true);
        campASecondaryWeaponIds = normalizeStringIds(campASecondaryWeaponIds, true);
        campAMeleeWeaponIds = normalizeStringIds(campAMeleeWeaponIds, true);
        campAGadget1Ids = normalizeStringIds(campAGadget1Ids, true);
        campAGadget2Ids = normalizeStringIds(campAGadget2Ids, true);
        campAThrowableIds = normalizeStringIds(campAThrowableIds, true);
        campBPrimaryWeaponIds = normalizeStringIds(campBPrimaryWeaponIds, true);
        campBSecondaryWeaponIds = normalizeStringIds(campBSecondaryWeaponIds, true);
        campBMeleeWeaponIds = normalizeStringIds(campBMeleeWeaponIds, true);
        campBGadget1Ids = normalizeStringIds(campBGadget1Ids, true);
        campBGadget2Ids = normalizeStringIds(campBGadget2Ids, true);
        campBThrowableIds = normalizeStringIds(campBThrowableIds, true);
        campAEquipmentSetId = normalizeEquipmentSetId(campAEquipmentSetId);
        campBEquipmentSetId = normalizeEquipmentSetId(campBEquipmentSetId);
        mapName = normalizeMapName(mapName);
        roomDescription = normalizeRoomDescription(roomDescription);
        // GD656Mods: 默认 true = 保持"限定槽位无视兵种限制"的历史行为
        ignoreClassLimitOnRestriction = ignoreClassLimitOnRestriction == null ? Boolean.TRUE : ignoreClassLimitOnRestriction;
    }

    /** GD656Mods: 对外暴露为 boolean —— 未配置时按 true(保持历史行为)处理。 */
    public boolean ignoresClassLimitOnRestriction() {
        return !Boolean.FALSE.equals(ignoreClassLimitOnRestriction);
    }

    public static RoomCommonConfig defaults() {
        return new RoomCommonConfig(
            RoomMatchingConfig.defaults(),
            "",
            defaultQueueSpawn(),
            0,
            RoomVehicleBackend.defaultValue(),
            RoomPlayerStateConfig.defaults(),
            0,
            0,
            0,
            0,
            "CampA",
            "CampB",
            "",
            "",
            40.0D,
            null,
            RoomResultConfig.defaults(),
            false,
            false,
            true,  // disableInventoryChange: 默认禁用玩家背包
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            false,
            false,
            true,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "",
            "",
            "",
            "",
            // GD656Mods: ignoreClassLimitOnRestriction —— 默认 true(保持历史行为)
            true,
            // GD656Mods: ignoreGunLevel —— 默认 false
            false
        );
    }

    public static RoomPosition defaultQueueSpawn() {
        return new RoomPosition(0, 100, 0);
    }

    public List<String> fixedLoadoutIds(RoomTeamId teamId, CustomLoadoutSlot slot) {
        if (teamId == null || slot == null) {
            return List.of();
        }
        return switch (teamId) {
            case CAMP_A -> switch (slot) {
                case PRIMARY_WEAPON -> campAPrimaryWeaponIds;
                case SECONDARY_WEAPON -> campASecondaryWeaponIds;
                case MELEE -> campAMeleeWeaponIds;
                case GADGET_1 -> campAGadget1Ids;
                case GADGET_2 -> campAGadget2Ids;
                case THROWABLE -> campAThrowableIds;
            };
            case CAMP_B -> switch (slot) {
                case PRIMARY_WEAPON -> campBPrimaryWeaponIds;
                case SECONDARY_WEAPON -> campBSecondaryWeaponIds;
                case MELEE -> campBMeleeWeaponIds;
                case GADGET_1 -> campBGadget1Ids;
                case GADGET_2 -> campBGadget2Ids;
                case THROWABLE -> campBThrowableIds;
            };
        };
    }

    private static String normalizeDimensionId(String dimensionId) {
        return dimensionId == null ? "" : dimensionId.trim();
    }

    private static String normalizeDisplayName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeVoicePack(String voicePack) {
        return voicePack == null ? "" : voicePack.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeEquipmentSetId(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeMapName(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeRoomDescription(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeStringIds(List<String> rawValues, boolean normalizeEmptyToken) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null) {
                continue;
            }
            String trimmed = rawValue.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (normalizeEmptyToken && CustomLoadoutService.isReservedEmptyEquipmentId(trimmed)) {
                normalized.add(CustomLoadoutService.EMPTY_EQUIPMENT_ID);
            } else {
                normalized.add(trimmed);
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }
}
