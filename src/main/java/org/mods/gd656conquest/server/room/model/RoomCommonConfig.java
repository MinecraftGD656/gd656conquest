package org.mods.gd656conquest.server.room.model;

import java.util.Objects;

public record RoomCommonConfig(
    RoomMatchingConfig matchingConfig,
    String dimensionId,
    RoomPosition queueSpawn,
    int respawnDelaySeconds,
    RoomVehicleBackend vehicleBackend,
    RoomPlayerStateConfig playerStateConfig,
    int outsideBoundaryDeathSeconds,
    int aircraftOutsideBoundaryCleanupSeconds,
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
    boolean enableBackAgainstWall,
    boolean enableRandomLoadout,
    boolean enableRandomAttachments,
    boolean enableParachute
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
        campADisplayName = normalizeDisplayName(campADisplayName, "CampA");
        campBDisplayName = normalizeDisplayName(campBDisplayName, "CampB");
        campAVoicePack = normalizeVoicePack(campAVoicePack);
        campBVoicePack = normalizeVoicePack(campBVoicePack);
        progressSpeed = Double.isFinite(progressSpeed) && progressSpeed > 0.0D ? progressSpeed : 40.0D;
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
            true
        );
    }

    public static RoomPosition defaultQueueSpawn() {
        return new RoomPosition(0, 100, 0);
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
        return voicePack == null ? "" : voicePack.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public RoomCommonConfig(
        RoomMatchingConfig matchingConfig,
        String dimensionId,
        RoomPosition queueSpawn,
        int respawnDelaySeconds,
        RoomPlayerStateConfig playerStateConfig,
        int outsideBoundaryDeathSeconds,
        int aircraftOutsideBoundaryCleanupSeconds,
        String campADisplayName,
        String campBDisplayName,
        String campAVoicePack,
        String campBVoicePack,
        double progressSpeed,
        RoomResultConfig resultConfig,
        boolean disableHunger,
        boolean enableAutoTeamSwitchPrompt,
        boolean disableInventoryChange
    ) {
        this(
            matchingConfig,
            dimensionId,
            queueSpawn,
            respawnDelaySeconds,
            RoomVehicleBackend.defaultValue(),
            playerStateConfig,
            outsideBoundaryDeathSeconds,
            aircraftOutsideBoundaryCleanupSeconds,
            campADisplayName,
            campBDisplayName,
            campAVoicePack,
            campBVoicePack,
            progressSpeed,
            resultConfig,
            disableHunger,
            enableAutoTeamSwitchPrompt,
            disableInventoryChange,
            false,
            false,
            false,
            false,
            false,
            true
        );
    }

    public RoomCommonConfig(
        RoomMatchingConfig matchingConfig,
        String dimensionId,
        RoomPosition queueSpawn,
        int respawnDelaySeconds,
        RoomVehicleBackend vehicleBackend,
        RoomPlayerStateConfig playerStateConfig,
        int outsideBoundaryDeathSeconds,
        int aircraftOutsideBoundaryCleanupSeconds,
        String campADisplayName,
        String campBDisplayName,
        String campAVoicePack,
        String campBVoicePack,
        double progressSpeed,
        RoomResultConfig resultConfig,
        boolean disableHunger,
        boolean enableAutoTeamSwitchPrompt,
        boolean disableInventoryChange
    ) {
        this(
            matchingConfig,
            dimensionId,
            queueSpawn,
            respawnDelaySeconds,
            vehicleBackend,
            playerStateConfig,
            outsideBoundaryDeathSeconds,
            aircraftOutsideBoundaryCleanupSeconds,
            campADisplayName,
            campBDisplayName,
            campAVoicePack,
            campBVoicePack,
            progressSpeed,
            resultConfig,
            disableHunger,
            enableAutoTeamSwitchPrompt,
            disableInventoryChange,
            false,
            false,
            false,
            false,
            false,
            true
        );
    }

    public RoomCommonConfig(
        RoomMatchingConfig matchingConfig,
        String dimensionId,
        RoomPosition queueSpawn,
        int respawnDelaySeconds,
        RoomVehicleBackend vehicleBackend,
        RoomPlayerStateConfig playerStateConfig,
        int outsideBoundaryDeathSeconds,
        int aircraftOutsideBoundaryCleanupSeconds,
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
        boolean enableBackAgainstWall,
        boolean enableRandomLoadout
    ) {
        this(
            matchingConfig,
            dimensionId,
            queueSpawn,
            respawnDelaySeconds,
            vehicleBackend,
            playerStateConfig,
            outsideBoundaryDeathSeconds,
            aircraftOutsideBoundaryCleanupSeconds,
            campADisplayName,
            campBDisplayName,
            campAVoicePack,
            campBVoicePack,
            progressSpeed,
            resultConfig,
            disableHunger,
            enableAutoTeamSwitchPrompt,
            disableInventoryChange,
            enableGoatHorn,
            enablePlayerDowned,
            enableBackAgainstWall,
            enableRandomLoadout,
            false,
            true
        );
    }

    public RoomCommonConfig(
        RoomMatchingConfig matchingConfig,
        String dimensionId,
        RoomPosition queueSpawn,
        int respawnDelaySeconds,
        RoomVehicleBackend vehicleBackend,
        RoomPlayerStateConfig playerStateConfig,
        int outsideBoundaryDeathSeconds,
        int aircraftOutsideBoundaryCleanupSeconds,
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
        boolean enableBackAgainstWall,
        boolean enableRandomLoadout,
        boolean enableRandomAttachments
    ) {
        this(
            matchingConfig,
            dimensionId,
            queueSpawn,
            respawnDelaySeconds,
            vehicleBackend,
            playerStateConfig,
            outsideBoundaryDeathSeconds,
            aircraftOutsideBoundaryCleanupSeconds,
            campADisplayName,
            campBDisplayName,
            campAVoicePack,
            campBVoicePack,
            progressSpeed,
            resultConfig,
            disableHunger,
            enableAutoTeamSwitchPrompt,
            disableInventoryChange,
            enableGoatHorn,
            enablePlayerDowned,
            enableBackAgainstWall,
            enableRandomLoadout,
            enableRandomAttachments,
            true
        );
    }
}
