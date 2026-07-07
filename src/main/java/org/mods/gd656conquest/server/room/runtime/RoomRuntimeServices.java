package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.core.RoomPlayerRejoinService;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleRuntimeService;

import java.util.Objects;

public record RoomRuntimeServices(
    RoomPlayerLaunchService playerLaunchService,
    RoomPlayerStateService playerStateService,
    RoomPlayerTransferQueueService playerTransferQueueService,
    RoomDeploySelectionService deploySelectionService,
    RoomPlayerRespawnService playerRespawnService,
    RoomPlayerLoadoutService playerLoadoutService,
    RoomItemReplenishService itemReplenishService,
    RoomDeathSupportDropService deathSupportDropService,
    RoomSupportBoxService supportBoxService,
    RoomMarkerRuntimeService markerRuntimeService,
    RoomHudSyncService hudSyncService,
    RoomInventorySnapshotService inventorySnapshotService,
    RoomPlayerRejoinService playerRejoinService,
    RoomSquadAssignmentService squadAssignmentService,
    RoomSquadBeaconService squadBeaconService,
    RoomNametagVisibilityService nametagVisibilityService,
    RoomTeamSwitchService teamSwitchService,
    RoomKilliconService killiconService,
    org.mods.gd656conquest.server.room.core.RoomAdminDebugOverlayService adminDebugOverlayService,
    RoomLobbyTransferService lobbyTransferService,
    RoomAudioRuntimeService audioRuntimeService,
    RoomVehicleRuntimeService vehicleRuntimeService,
    RoomRuleRuntimeService ruleRuntimeService,
    RoomResultRuntimeService resultRuntimeService
) {
    public RoomRuntimeServices {
        Objects.requireNonNull(playerLaunchService, "playerLaunchService");
        Objects.requireNonNull(playerStateService, "playerStateService");
        Objects.requireNonNull(playerTransferQueueService, "playerTransferQueueService");
        Objects.requireNonNull(deploySelectionService, "deploySelectionService");
        Objects.requireNonNull(playerRespawnService, "playerRespawnService");
        Objects.requireNonNull(playerLoadoutService, "playerLoadoutService");
        Objects.requireNonNull(itemReplenishService, "itemReplenishService");
        Objects.requireNonNull(deathSupportDropService, "deathSupportDropService");
        Objects.requireNonNull(supportBoxService, "supportBoxService");
        Objects.requireNonNull(markerRuntimeService, "markerRuntimeService");
        Objects.requireNonNull(hudSyncService, "hudSyncService");
        Objects.requireNonNull(inventorySnapshotService, "inventorySnapshotService");
        Objects.requireNonNull(playerRejoinService, "playerRejoinService");
        Objects.requireNonNull(squadAssignmentService, "squadAssignmentService");
        Objects.requireNonNull(squadBeaconService, "squadBeaconService");
        Objects.requireNonNull(nametagVisibilityService, "nametagVisibilityService");
        Objects.requireNonNull(teamSwitchService, "teamSwitchService");
        Objects.requireNonNull(killiconService, "killiconService");
        Objects.requireNonNull(adminDebugOverlayService, "adminDebugOverlayService");
        Objects.requireNonNull(lobbyTransferService, "lobbyTransferService");
        Objects.requireNonNull(audioRuntimeService, "audioRuntimeService");
        Objects.requireNonNull(vehicleRuntimeService, "vehicleRuntimeService");
        Objects.requireNonNull(ruleRuntimeService, "ruleRuntimeService");
        Objects.requireNonNull(resultRuntimeService, "resultRuntimeService");
    }
}
