package org.mods.gd656conquest.server.room.bootstrap;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.core.RoomPlayerSessionService;
import org.mods.gd656conquest.server.room.core.RoomAdminDebugOverlayService;
import org.mods.gd656conquest.server.room.core.RoomPlayerRejoinService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeRepository;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.runtime.RoomAudioRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomDeathSupportDropService;
import org.mods.gd656conquest.server.room.runtime.RoomDeploySelectionService;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterLoadoutService;
import org.mods.gd656conquest.server.room.runtime.RoomHudSyncService;
import org.mods.gd656conquest.server.room.runtime.RoomItemReplenishService;
import org.mods.gd656conquest.server.room.runtime.RoomInventorySnapshotService;
import org.mods.gd656conquest.server.room.runtime.RoomKilliconService;
import org.mods.gd656conquest.server.room.runtime.RoomLobbyTransferService;
import org.mods.gd656conquest.server.room.runtime.RoomMarkerRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomNametagVisibilityService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLaunchService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerStateService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerTransferQueueService;
import org.mods.gd656conquest.server.room.runtime.RoomResultRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomRuleRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomRuntimeServices;
import org.mods.gd656conquest.server.room.runtime.RoomSquadAssignmentService;
import org.mods.gd656conquest.server.room.runtime.RoomSquadBeaconService;
import org.mods.gd656conquest.server.room.runtime.RoomSupportBoxService;
import org.mods.gd656conquest.server.room.runtime.RoomTeamSwitchService;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleDeployExecutionService;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleDeploySelectionService;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleRuntimeService;
import org.mods.gd656conquest.server.room.vehicle.VehicleBoundaryPenaltyService;

import java.util.Objects;

public final class RoomRuntimeServicesFactory {
    private RoomRuntimeServicesFactory() {
    }

    public static RoomRuntimeServices create(
        MinecraftServer server,
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        Objects.requireNonNull(playerSessionService, "playerSessionService");

        RoomPlayerLaunchService playerLaunchService = new RoomPlayerLaunchService(server);
        RoomPlayerStateService playerStateService = new RoomPlayerStateService(server);
        RoomSquadBeaconService squadBeaconService = new RoomSquadBeaconService(server, runtimeRepository, playerSessionService);
        RoomVehicleRuntimeService vehicleRuntimeService = new RoomVehicleRuntimeService(server, ConquestDataManager.of(server).vehicleConfigs());
        RoomVehicleDeploySelectionService vehicleDeploySelectionService = new RoomVehicleDeploySelectionService(server, vehicleRuntimeService);
        RoomVehicleDeployExecutionService vehicleDeployExecutionService = new RoomVehicleDeployExecutionService(server, vehicleRuntimeService);
        VehicleBoundaryPenaltyService vehicleBoundaryPenaltyService = new VehicleBoundaryPenaltyService(vehicleRuntimeService);
        RoomDeploySelectionService deploySelectionService = new RoomDeploySelectionService(server, squadBeaconService, vehicleDeploySelectionService);
        RoomPlayerLoadoutService playerLoadoutService = new RoomPlayerLoadoutService(server);
        RoomGunmasterLoadoutService gunmasterLoadoutService = new RoomGunmasterLoadoutService(server);
        RoomItemReplenishService itemReplenishService = new RoomItemReplenishService(server, playerLoadoutService, gunmasterLoadoutService);
        RoomDeathSupportDropService deathSupportDropService = new RoomDeathSupportDropService(server, runtimeRepository, playerSessionService);
        RoomSupportBoxService supportBoxService = new RoomSupportBoxService(server, runtimeRepository, playerSessionService, playerLoadoutService, gunmasterLoadoutService);
        RoomMarkerRuntimeService markerRuntimeService = new RoomMarkerRuntimeService(server);
        RoomInventorySnapshotService inventorySnapshotService = new RoomInventorySnapshotService();
        RoomPlayerRejoinService playerRejoinService = new RoomPlayerRejoinService(runtimeRepository);
        RoomSquadAssignmentService squadAssignmentService = new RoomSquadAssignmentService(server);
        RoomKilliconService killiconService = new RoomKilliconService(server);
        RoomNametagVisibilityService nametagVisibilityService = new RoomNametagVisibilityService(server);
        RoomHudSyncService hudSyncService = new RoomHudSyncService(server, squadBeaconService);
        RoomTeamSwitchService teamSwitchService = new RoomTeamSwitchService(
            deploySelectionService,
            squadAssignmentService,
            nametagVisibilityService,
            hudSyncService
        );
        RoomAdminDebugOverlayService adminDebugOverlayService = new RoomAdminDebugOverlayService(
            runtimeRepository,
            ConquestDataManager.of(server).roomDefinitions()
        );
        RoomLobbyTransferService lobbyTransferService = new RoomLobbyTransferService(server);
        RoomPlayerTransferQueueService playerTransferQueueService = new RoomPlayerTransferQueueService(server, playerLaunchService, lobbyTransferService);
        RoomAudioRuntimeService audioRuntimeService = new RoomAudioRuntimeService(server);
        RoomRuleRuntimeService ruleRuntimeService = new RoomRuleRuntimeService(server, vehicleBoundaryPenaltyService);
        RoomResultRuntimeService resultRuntimeService = new RoomResultRuntimeService(server, playerStateService, ruleRuntimeService, playerTransferQueueService);
        return new RoomRuntimeServices(
            playerLaunchService,
            playerStateService,
            playerTransferQueueService,
            deploySelectionService,
            new RoomPlayerRespawnService(
                server,
                playerLaunchService,
                playerStateService,
                playerTransferQueueService,
                deploySelectionService,
                playerLoadoutService,
                gunmasterLoadoutService,
                audioRuntimeService,
                vehicleDeployExecutionService,
                markerRuntimeService
            ),
            playerLoadoutService,
            gunmasterLoadoutService,
            itemReplenishService,
            deathSupportDropService,
            supportBoxService,
            markerRuntimeService,
            hudSyncService,
            inventorySnapshotService,
            playerRejoinService,
            squadAssignmentService,
            squadBeaconService,
            nametagVisibilityService,
            teamSwitchService,
            killiconService,
            adminDebugOverlayService,
            lobbyTransferService,
            audioRuntimeService,
            vehicleRuntimeService,
            ruleRuntimeService,
            resultRuntimeService
        );
    }
}
