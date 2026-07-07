package org.mods.gd656conquest.server.room.bootstrap;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RoomCoreComponents;
import org.mods.gd656conquest.server.room.core.RoomPlayerSessionService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeRepository;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayEntryManager;
import org.mods.gd656conquest.server.room.matching.RoomMatchingCoordinator;
import org.mods.gd656conquest.server.room.runtime.RoomRuntimeServices;

import java.util.Collection;
import java.util.Objects;

public final class RoomCoreComponentsFactory {
    private RoomCoreComponentsFactory() {
    }

    public static RoomCoreComponents create(MinecraftServer server, Collection<? extends IRoomGameplayEntry> gameplayEntries) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(gameplayEntries, "gameplayEntries");

        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository = RoomRuntimeRepository.createInMemory();
        RoomPlayerSessionService playerSessionService = new RoomPlayerSessionService();
        RoomRuntimeServices runtimeServices = RoomRuntimeServicesFactory.create(server, runtimeRepository, playerSessionService);
        return new RoomCoreComponents(
            server,
            runtimeRepository,
            playerSessionService,
            new RoomMatchingCoordinator(),
            new RoomGameplayEntryManager(gameplayEntries),
            runtimeServices.playerLaunchService(),
            runtimeServices.playerStateService(),
            runtimeServices.playerTransferQueueService(),
            runtimeServices.deploySelectionService(),
            runtimeServices.playerRespawnService(),
            runtimeServices.playerLoadoutService(),
            runtimeServices.itemReplenishService(),
            runtimeServices.deathSupportDropService(),
            runtimeServices.supportBoxService(),
            runtimeServices.markerRuntimeService(),
            runtimeServices.hudSyncService(),
            runtimeServices.inventorySnapshotService(),
            runtimeServices.playerRejoinService(),
            runtimeServices.squadAssignmentService(),
            runtimeServices.squadBeaconService(),
            runtimeServices.nametagVisibilityService(),
            runtimeServices.teamSwitchService(),
            runtimeServices.killiconService(),
            runtimeServices.adminDebugOverlayService(),
            runtimeServices.lobbyTransferService(),
            runtimeServices.audioRuntimeService(),
            runtimeServices.vehicleRuntimeService(),
            runtimeServices.ruleRuntimeService(),
            runtimeServices.resultRuntimeService()
        );
    }
}
