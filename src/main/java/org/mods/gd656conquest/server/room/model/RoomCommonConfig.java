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
    List<String> roomPrimaryWeaponIds,
    List<String> roomSecondaryWeaponIds,
    List<String> roomMeleeWeaponIds,
    List<String> roomGadget1Ids,
    List<String> roomGadget2Ids,
    List<String> roomThrowableIds,
    String campAEquipmentSetId,
    String campBEquipmentSetId
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
        roomPrimaryWeaponIds = normalizeStringIds(roomPrimaryWeaponIds, true);
        roomSecondaryWeaponIds = normalizeStringIds(roomSecondaryWeaponIds, true);
        roomMeleeWeaponIds = normalizeStringIds(roomMeleeWeaponIds, true);
        roomGadget1Ids = normalizeStringIds(roomGadget1Ids, true);
        roomGadget2Ids = normalizeStringIds(roomGadget2Ids, true);
        roomThrowableIds = normalizeStringIds(roomThrowableIds, true);
        campAEquipmentSetId = normalizeEquipmentSetId(campAEquipmentSetId);
        campBEquipmentSetId = normalizeEquipmentSetId(campBEquipmentSetId);
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
            "",
            ""
        );
    }

    public static RoomPosition defaultQueueSpawn() {
        return new RoomPosition(0, 100, 0);
    }

    public List<String> fixedLoadoutIds(CustomLoadoutSlot slot) {
        if (slot == null) {
            return List.of();
        }
        return switch (slot) {
            case PRIMARY_WEAPON -> roomPrimaryWeaponIds;
            case SECONDARY_WEAPON -> roomSecondaryWeaponIds;
            case MELEE -> roomMeleeWeaponIds;
            case GADGET_1 -> roomGadget1Ids;
            case GADGET_2 -> roomGadget2Ids;
            case THROWABLE -> roomThrowableIds;
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
