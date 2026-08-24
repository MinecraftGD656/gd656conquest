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
    // GD656Mods: 弹药包单次补给数量(按武器类型) —— 自己用组: 拾取弹药包掉落物时使用
    int ammoBagTaczPistol,
    int ammoBagTaczSmg,
    int ammoBagTaczRifle,
    int ammoBagTaczSniper,
    int ammoBagTaczShotgun,
    int ammoBagTaczMg,
    int ammoBagTaczRpg,
    int ammoBagSbw,
    // GD656Mods: 弹药包单次补给数量(支援组) —— 支援兵自己使用/给予他人弹药包时使用
    int supportAmmoBagTaczPistol,
    int supportAmmoBagTaczSmg,
    int supportAmmoBagTaczRifle,
    int supportAmmoBagTaczSniper,
    int supportAmmoBagTaczShotgun,
    int supportAmmoBagTaczMg,
    int supportAmmoBagTaczRpg,
    int supportAmmoBagSbw
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
        // GD656Mods: 弹药包单次补给数量, 负数视为不补给(0)
        ammoBagTaczPistol = Math.max(0, ammoBagTaczPistol);
        ammoBagTaczSmg = Math.max(0, ammoBagTaczSmg);
        ammoBagTaczRifle = Math.max(0, ammoBagTaczRifle);
        ammoBagTaczSniper = Math.max(0, ammoBagTaczSniper);
        ammoBagTaczShotgun = Math.max(0, ammoBagTaczShotgun);
        ammoBagTaczMg = Math.max(0, ammoBagTaczMg);
        ammoBagTaczRpg = Math.max(0, ammoBagTaczRpg);
        ammoBagSbw = Math.max(0, ammoBagSbw);
        supportAmmoBagTaczPistol = Math.max(0, supportAmmoBagTaczPistol);
        supportAmmoBagTaczSmg = Math.max(0, supportAmmoBagTaczSmg);
        supportAmmoBagTaczRifle = Math.max(0, supportAmmoBagTaczRifle);
        supportAmmoBagTaczSniper = Math.max(0, supportAmmoBagTaczSniper);
        supportAmmoBagTaczShotgun = Math.max(0, supportAmmoBagTaczShotgun);
        supportAmmoBagTaczMg = Math.max(0, supportAmmoBagTaczMg);
        supportAmmoBagTaczRpg = Math.max(0, supportAmmoBagTaczRpg);
        supportAmmoBagSbw = Math.max(0, supportAmmoBagSbw);
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
            // GD656Mods: 弹药包单次补给数量默认值(自己用组: 拾取掉落物)
            6,
            15,
            10,
            3,
            4,
            20,
            1,
            1,
            // GD656Mods: 弹药包单次补给数量默认值(支援组: 自己使用/给予他人)
            6,
            15,
            10,
            3,
            4,
            20,
            1,
            1
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
