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
    String roomDescription
) {
    public RoomCommonConfig {
        matchingConfig = matchingConfig == null ? RoomMatchingConfig.defaults() : matchingConfig;
        vehicleBackend = vehicleBackend == null ? RoomVehicleBackend.defaultValue() : vehicleBackend;
        playerStateConfig = playerStateConfig == null ? RoomPlayerStateConfig.defaults() : playerStateConfig;
        resultConfig = resultConfig == null ? RoomResultConfig.defaults() : resultConfig;
        dimensionId = normalizeDimensionId(dimensionId);
        respawnDelaySeconds = Math.max(0, respawnDelaySeconds);
        outsideBoundaryDeathSeconds = Math.max(0, outsideBoundaryDeathSeconds);
        aircraftOutsideBoundaryCleanupSeconds = Math.max(0, aircraftOutsideBoundaryCleanupSeconds);
        playerMaxY = Math.max(0, playerMaxY);
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
            RoomResultConfig.defaults(),
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
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
            ""
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
