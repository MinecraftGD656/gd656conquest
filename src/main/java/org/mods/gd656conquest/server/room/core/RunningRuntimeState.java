package org.mods.gd656conquest.server.room.core;

import org.mods.gd656conquest.server.room.gameplay.RoomGameplayIntermissionProvider;

import net.minecraft.core.BlockPos;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.vehicle.VehicleDeployResolution;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RunningRuntimeState {
    private static final int NO_KILLER_ENTITY_ID = -1;

    private final Set<UUID> roomPlayers = new LinkedHashSet<>();
    private final Map<UUID, RoomTeamId> roomTeams = new LinkedHashMap<>();
    private final Map<UUID, RoomPosition> deathPositions = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingDownedStartTicks = new LinkedHashMap<>();
    private final Map<UUID, Long> downedStartTicks = new LinkedHashMap<>();
    private final Map<UUID, Long> downedExpireTicks = new LinkedHashMap<>();
    private final Map<UUID, UUID> reviveTargetByReviver = new LinkedHashMap<>();
    private final Map<UUID, UUID> reviveReviverByTarget = new LinkedHashMap<>();
    private final Map<UUID, Long> reviveStartTicksByReviver = new LinkedHashMap<>();
    private final Map<UUID, Long> respawnCountdownStartTicks = new LinkedHashMap<>();
    private final Map<UUID, Long> deployReadyTicks = new LinkedHashMap<>();
    private final Map<UUID, Long> respawnFinalizeTicks = new LinkedHashMap<>();
    private final Map<UUID, RoomPosition> respawnTargetPositions = new LinkedHashMap<>();
    private final Map<UUID, Float> respawnTargetYRot = new LinkedHashMap<>();
    private final Map<UUID, Float> respawnTargetXRot = new LinkedHashMap<>();
    private final Map<UUID, net.minecraft.world.entity.Pose> respawnTargetPose = new LinkedHashMap<>();
    private final Map<UUID, VehicleDeployResolution> pendingVehicleDeployResolutions = new LinkedHashMap<>();
    private final Map<UUID, String> deploySelectionKinds = new LinkedHashMap<>();
    private final Map<UUID, String> deploySelectionTargetIds = new LinkedHashMap<>();
    private final Map<UUID, Long> deploySelectionChangedTicks = new LinkedHashMap<>();
    private final Set<UUID> deployAutoStartOnReadyPlayers = new LinkedHashSet<>();
    private final Set<UUID> deployAllowFallbackOnMissingSelectionPlayers = new LinkedHashSet<>();
    private final Set<UUID> deployExplicitConfirmRequiredPlayers = new LinkedHashSet<>();
    private final Set<UUID> deployViewActivePlayers = new LinkedHashSet<>();
    private final Map<UUID, Integer> deathKillerEntityIds = new LinkedHashMap<>();
    private final Set<UUID> initialMovementLockedPlayers = new LinkedHashSet<>();
    private final Map<UUID, Integer> initialMovementLockLastShownSeconds = new LinkedHashMap<>();
    private final Map<UUID, Integer> respawnCountdownLastShownSeconds = new LinkedHashMap<>();
    private final Map<UUID, String> squadLabelsByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Long> combatUntilTicks = new LinkedHashMap<>();
    private final Set<UUID> doubledRespawnPending = new LinkedHashSet<>();
    private final Map<UUID, Long> teammateDeployKillWindowUntilTicks = new LinkedHashMap<>();
    private final Map<UUID, UUID> beaconOwnerByDeployer = new LinkedHashMap<>();
    private final Map<UUID, Long> beaconOwnerKillWindowUntilTicksByDeployer = new LinkedHashMap<>();
    private final Map<UUID, Map<String, SquadWipeProgress>> squadWipeProgressByKiller = new LinkedHashMap<>();
    private final Map<UUID, RoomTeamId> pendingTeamChanges = new LinkedHashMap<>();
    private final Map<UUID, RoomTeamId> autoTeamSwitchOfferTargetTeams = new LinkedHashMap<>();
    private final Map<UUID, Long> autoTeamSwitchOfferExpireTicks = new LinkedHashMap<>();
    private final Map<UUID, KilliconMatchStats> killiconMatchStatsByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Integer> supportSpentScoreByPlayer = new LinkedHashMap<>();
    private final Map<UUID, String> activeHeroKitIdsByPlayer = new LinkedHashMap<>();
    private final Map<UUID, String> activeHeroKitTypesByPlayer = new LinkedHashMap<>();
    private final Map<String, Long> heroKitCooldownExpireTicksByScopedKey = new LinkedHashMap<>();
    private final Set<UUID> heroKitLoadoutResetPendingPlayers = new LinkedHashSet<>();
    private final Map<UUID, ShellingRequestState> activeShellingsByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Map<Integer, Long>> loadoutItemLastReplenishTickByPlayerSlot = new LinkedHashMap<>();
    private final Map<UUID, String> randomizedLoadoutClassKeysByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Map<String, String>> randomizedLoadoutSelectionIdsByPlayerSlot = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Map<String, String>>> randomizedAttachmentSelectionIdsByPlayerLoadoutEntry = new LinkedHashMap<>();
    private final Map<UUID, Map<String, String>> fixedRoomLoadoutSelectionIdsByPlayerSlot = new LinkedHashMap<>();
    private final Map<UUID, Integer> gunmasterStageIndexByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Integer> gunmasterStageKillsByPlayer = new LinkedHashMap<>();
    private final java.util.List<String> gunmasterMatchWeaponSequenceIds = new java.util.ArrayList<>();
    private long autoTeamBalanceImbalanceSinceTick = -1L;
    private long autoTeamBalanceLastPromptTick = -1L;
    private int autoTeamBalanceLastDelta;
    private final Map<UUID, Long> outsideBoundarySinceTicks = new LinkedHashMap<>();
    private final Map<UUID, Long> aircraftOutsideBoundarySinceTicks = new LinkedHashMap<>();
    private final Map<UUID, Integer> boundaryPenaltyLastShownSeconds = new LinkedHashMap<>();
    private final Set<UUID> boundaryPenaltyVisualApplied = new LinkedHashSet<>();
    private final Set<UUID> boundaryDeathPendingPlayers = new LinkedHashSet<>();
    private final Map<UUID, Long> boundaryHeightReportTicks = new LinkedHashMap<>();
    private final Set<UUID> boundaryPlayerMaxYReportedPlayers = new LinkedHashSet<>();
    private final Set<UUID> boundaryAircraftMaxYReportedPlayers = new LinkedHashSet<>();
    private final Map<UUID, TacticalMarker> tacticalMarkersByOwner = new LinkedHashMap<>();
    private final Map<UUID, TacticalMarker> autoTacticalMarkersByOwner = new LinkedHashMap<>();
    private final Map<String, TeamScopedTacticalMarker> gameplayTacticalMarkersByKey = new LinkedHashMap<>();
    private final Map<UUID, RejoinTicket> rejoinTickets = new LinkedHashMap<>();
    private long operationsPresentationEndTick = -1L;
    private RoomTeamId resultWinnerTeam;
    private UUID resultWinnerPlayerId;
    private String resultReason = "-";
    private long resultBlackStartTick = -1L;
    private long resultSequenceEndTick = -1L;
    private int resultCloseRemainAnnouncedSecond = -1;
    private boolean resultViewApplied;
    private long resultLastViewTransferTick = -1L;
    private final Set<UUID> resultViewTransferPending = new LinkedHashSet<>();
    private final Set<UUID> resultAwardedPlayers = new LinkedHashSet<>();
    private long activeMatchInstanceId = -1L;
    private long initialMovementUnlockTick = -1L;
    private long lastHudSyncTick = -1L;
    private long lastNametagSyncTick = -1L;
    private IRoomGameplayRuntime gameplayRuntime;

    public Optional<IRoomGameplayRuntime> getGameplayRuntime() {
        return Optional.ofNullable(gameplayRuntime);
    }

    public void setGameplayRuntime(IRoomGameplayRuntime gameplayRuntime) {
        this.gameplayRuntime = gameplayRuntime;
    }

    public void clearGameplayRuntime() {
        this.gameplayRuntime = null;
    }

    public Set<UUID> getRoomPlayers() {
        return roomPlayers;
    }

    public Map<UUID, RoomTeamId> getRoomTeams() {
        return roomTeams;
    }

    public Map<UUID, RoomPosition> getDeathPositions() {
        return deathPositions;
    }

    public Map<UUID, Long> getDownedStartTicks() {
        return downedStartTicks;
    }

    public Map<UUID, Long> getPendingDownedStartTicks() {
        return pendingDownedStartTicks;
    }

    public Map<UUID, Long> getDownedExpireTicks() {
        return downedExpireTicks;
    }

    public Map<UUID, UUID> getReviveTargetByReviver() {
        return reviveTargetByReviver;
    }

    public Map<UUID, UUID> getReviveReviverByTarget() {
        return reviveReviverByTarget;
    }

    public Map<UUID, Long> getReviveStartTicksByReviver() {
        return reviveStartTicksByReviver;
    }

    public Map<UUID, Long> getRespawnCountdownStartTicks() {
        return respawnCountdownStartTicks;
    }

    public Map<UUID, Long> getDeployReadyTicks() {
        return deployReadyTicks;
    }

    public Map<UUID, Long> getRespawnFinalizeTicks() {
        return respawnFinalizeTicks;
    }

    public Map<UUID, RoomPosition> getRespawnTargetPositions() {
        return respawnTargetPositions;
    }

    public Map<UUID, Float> getRespawnTargetYRot() {
        return respawnTargetYRot;
    }

    public Map<UUID, Float> getRespawnTargetXRot() {
        return respawnTargetXRot;
    }

    public Map<UUID, net.minecraft.world.entity.Pose> getRespawnTargetPose() {
        return respawnTargetPose;
    }

    public Map<UUID, VehicleDeployResolution> getPendingVehicleDeployResolutions() {
        return pendingVehicleDeployResolutions;
    }

    public Map<UUID, String> getDeploySelectionKinds() {
        return deploySelectionKinds;
    }

    public Map<UUID, String> getDeploySelectionTargetIds() {
        return deploySelectionTargetIds;
    }

    public Map<UUID, Long> getDeploySelectionChangedTicks() {
        return deploySelectionChangedTicks;
    }

    public Set<UUID> getDeployAutoStartOnReadyPlayers() {
        return deployAutoStartOnReadyPlayers;
    }

    public Set<UUID> getDeployAllowFallbackOnMissingSelectionPlayers() {
        return deployAllowFallbackOnMissingSelectionPlayers;
    }

    public Set<UUID> getDeployExplicitConfirmRequiredPlayers() {
        return deployExplicitConfirmRequiredPlayers;
    }

    public Set<UUID> getDeployViewActivePlayers() {
        return deployViewActivePlayers;
    }

    public Map<UUID, Integer> getDeathKillerEntityIds() {
        return deathKillerEntityIds;
    }

    public Set<UUID> getInitialMovementLockedPlayers() {
        return initialMovementLockedPlayers;
    }

    public Map<UUID, Integer> getInitialMovementLockLastShownSeconds() {
        return initialMovementLockLastShownSeconds;
    }

    public Map<UUID, Integer> getRespawnCountdownLastShownSeconds() {
        return respawnCountdownLastShownSeconds;
    }

    public long getInitialMovementUnlockTick() {
        return initialMovementUnlockTick;
    }

    public void setInitialMovementUnlockTick(long initialMovementUnlockTick) {
        this.initialMovementUnlockTick = initialMovementUnlockTick;
    }

    public long getLastHudSyncTick() {
        return lastHudSyncTick;
    }

    public void setLastHudSyncTick(long lastHudSyncTick) {
        this.lastHudSyncTick = lastHudSyncTick;
    }

    public long getLastNametagSyncTick() {
        return lastNametagSyncTick;
    }

    public void setLastNametagSyncTick(long lastNametagSyncTick) {
        this.lastNametagSyncTick = lastNametagSyncTick;
    }

    public boolean isInitialMovementLockActive(long gameTick) {
        return initialMovementUnlockTick > 0L
            && gameTick < initialMovementUnlockTick
            && !initialMovementLockedPlayers.isEmpty();
    }

    public boolean isInitialMovementLockActive(UUID playerId, long gameTick) {
        return playerId != null
            && initialMovementLockedPlayers.contains(playerId)
            && isInitialMovementLockActive(gameTick);
    }

    public Map<UUID, String> getSquadLabelsByPlayer() {
        return squadLabelsByPlayer;
    }

    public Map<UUID, Long> getCombatUntilTicks() {
        return combatUntilTicks;
    }

    public Set<UUID> getDoubledRespawnPending() {
        return doubledRespawnPending;
    }

    public Map<UUID, Long> getTeammateDeployKillWindowUntilTicks() {
        return teammateDeployKillWindowUntilTicks;
    }

    public Map<UUID, UUID> getBeaconOwnerByDeployer() {
        return beaconOwnerByDeployer;
    }

    public Map<UUID, Long> getBeaconOwnerKillWindowUntilTicksByDeployer() {
        return beaconOwnerKillWindowUntilTicksByDeployer;
    }

    public Map<UUID, Map<String, SquadWipeProgress>> getSquadWipeProgressByKiller() {
        return squadWipeProgressByKiller;
    }

    public Map<UUID, RoomTeamId> getPendingTeamChanges() {
        return pendingTeamChanges;
    }

    public Map<UUID, RoomTeamId> getAutoTeamSwitchOfferTargetTeams() {
        return autoTeamSwitchOfferTargetTeams;
    }

    public Map<UUID, Long> getAutoTeamSwitchOfferExpireTicks() {
        return autoTeamSwitchOfferExpireTicks;
    }

    public Map<UUID, KilliconMatchStats> getKilliconMatchStatsByPlayer() {
        return killiconMatchStatsByPlayer;
    }

    public Map<UUID, Integer> getSupportSpentScoreByPlayer() {
        return supportSpentScoreByPlayer;
    }

    public Map<UUID, String> getActiveHeroKitIdsByPlayer() {
        return activeHeroKitIdsByPlayer;
    }

    public Map<UUID, String> getActiveHeroKitTypesByPlayer() {
        return activeHeroKitTypesByPlayer;
    }

    public Map<String, Long> getHeroKitCooldownExpireTicksByScopedKey() {
        return heroKitCooldownExpireTicksByScopedKey;
    }

    public Set<UUID> getHeroKitLoadoutResetPendingPlayers() {
        return heroKitLoadoutResetPendingPlayers;
    }

    public Map<UUID, ShellingRequestState> getActiveShellingsByPlayer() {
        return activeShellingsByPlayer;
    }

    public Map<UUID, Map<Integer, Long>> getLoadoutItemLastReplenishTickByPlayerSlot() {
        return loadoutItemLastReplenishTickByPlayerSlot;
    }

    public Map<UUID, String> getRandomizedLoadoutClassKeysByPlayer() {
        return randomizedLoadoutClassKeysByPlayer;
    }

    public Map<UUID, Map<String, String>> getRandomizedLoadoutSelectionIdsByPlayerSlot() {
        return randomizedLoadoutSelectionIdsByPlayerSlot;
    }

    public Map<UUID, Map<String, String>> getFixedRoomLoadoutSelectionIdsByPlayerSlot() {
        return fixedRoomLoadoutSelectionIdsByPlayerSlot;
    }

    public Map<UUID, Integer> getGunmasterStageIndexByPlayer() {
        return gunmasterStageIndexByPlayer;
    }

    public Map<UUID, Integer> getGunmasterStageKillsByPlayer() {
        return gunmasterStageKillsByPlayer;
    }

    public List<String> getGunmasterMatchWeaponSequenceIds() {
        return gunmasterMatchWeaponSequenceIds;
    }

    public Map<UUID, Map<String, Map<String, String>>> getRandomizedAttachmentSelectionIdsByPlayerLoadoutEntry() {
        return randomizedAttachmentSelectionIdsByPlayerLoadoutEntry;
    }

    public long getAutoTeamBalanceImbalanceSinceTick() {
        return autoTeamBalanceImbalanceSinceTick;
    }

    public void setAutoTeamBalanceImbalanceSinceTick(long autoTeamBalanceImbalanceSinceTick) {
        this.autoTeamBalanceImbalanceSinceTick = autoTeamBalanceImbalanceSinceTick;
    }

    public long getAutoTeamBalanceLastPromptTick() {
        return autoTeamBalanceLastPromptTick;
    }

    public void setAutoTeamBalanceLastPromptTick(long autoTeamBalanceLastPromptTick) {
        this.autoTeamBalanceLastPromptTick = autoTeamBalanceLastPromptTick;
    }

    public int getAutoTeamBalanceLastDelta() {
        return autoTeamBalanceLastDelta;
    }

    public void setAutoTeamBalanceLastDelta(int autoTeamBalanceLastDelta) {
        this.autoTeamBalanceLastDelta = autoTeamBalanceLastDelta;
    }

    public Map<UUID, Long> getOutsideBoundarySinceTicks() {
        return outsideBoundarySinceTicks;
    }

    public Map<UUID, Long> getAircraftOutsideBoundarySinceTicks() {
        return aircraftOutsideBoundarySinceTicks;
    }

    public Map<UUID, Integer> getBoundaryPenaltyLastShownSeconds() {
        return boundaryPenaltyLastShownSeconds;
    }

    public Set<UUID> getBoundaryPenaltyVisualApplied() {
        return boundaryPenaltyVisualApplied;
    }

    public Set<UUID> getBoundaryDeathPendingPlayers() {
        return boundaryDeathPendingPlayers;
    }

    public Map<UUID, Long> getBoundaryHeightReportTicks() {
        return boundaryHeightReportTicks;
    }

    public Set<UUID> getBoundaryPlayerMaxYReportedPlayers() {
        return boundaryPlayerMaxYReportedPlayers;
    }

    public Set<UUID> getBoundaryAircraftMaxYReportedPlayers() {
        return boundaryAircraftMaxYReportedPlayers;
    }

    public Map<UUID, TacticalMarker> getTacticalMarkersByOwner() {
        return tacticalMarkersByOwner;
    }

    public Map<UUID, TacticalMarker> getAutoTacticalMarkersByOwner() {
        return autoTacticalMarkersByOwner;
    }

    public Map<String, TeamScopedTacticalMarker> getGameplayTacticalMarkersByKey() {
        return gameplayTacticalMarkersByKey;
    }

    public Map<UUID, RejoinTicket> getRejoinTickets() {
        return rejoinTickets;
    }

    public RoomTeamId getResultWinnerTeam() {
        return resultWinnerTeam;
    }

    public void setResultWinnerTeam(RoomTeamId resultWinnerTeam) {
        this.resultWinnerTeam = resultWinnerTeam;
    }

    public UUID getResultWinnerPlayerId() {
        return resultWinnerPlayerId;
    }

    public void setResultWinnerPlayerId(UUID resultWinnerPlayerId) {
        this.resultWinnerPlayerId = resultWinnerPlayerId;
    }

    public String getResultReason() {
        return resultReason;
    }

    public void setResultReason(String resultReason) {
        this.resultReason = resultReason == null || resultReason.isBlank() ? "-" : resultReason;
    }

    public long getResultBlackStartTick() {
        return resultBlackStartTick;
    }

    public void setResultBlackStartTick(long resultBlackStartTick) {
        this.resultBlackStartTick = resultBlackStartTick;
    }

    public boolean isOperationsResult() { return operationsPresentationEndTick >= 0; }
    public long getOperationsPresentationEndTick() { return operationsPresentationEndTick; }
    public void setOperationsPresentationEndTick(long tick) { operationsPresentationEndTick = tick; }

    public long getResultSequenceEndTick() {
        return resultSequenceEndTick;
    }

    public void setResultSequenceEndTick(long resultSequenceEndTick) {
        this.resultSequenceEndTick = resultSequenceEndTick;
    }

    public boolean isResultViewApplied() {
        return resultViewApplied;
    }

    public void setResultViewApplied(boolean resultViewApplied) {
        this.resultViewApplied = resultViewApplied;
    }

    public long getResultLastViewTransferTick() {
        return resultLastViewTransferTick;
    }

    public void setResultLastViewTransferTick(long resultLastViewTransferTick) {
        this.resultLastViewTransferTick = resultLastViewTransferTick;
    }

    public Set<UUID> getResultViewTransferPending() {
        return resultViewTransferPending;
    }

    public Set<UUID> getResultAwardedPlayers() {
        return resultAwardedPlayers;
    }

    public int getResultCloseRemainAnnouncedSecond() {
        return resultCloseRemainAnnouncedSecond;
    }

    public void setResultCloseRemainAnnouncedSecond(int resultCloseRemainAnnouncedSecond) {
        this.resultCloseRemainAnnouncedSecond = resultCloseRemainAnnouncedSecond;
    }

    public long getActiveMatchInstanceId() {
        return activeMatchInstanceId;
    }

    public void beginNewMatchInstance(long gameTick) {
        long candidate = gameTick > 0L ? gameTick : 1L;
        if (candidate <= activeMatchInstanceId) {
            candidate = activeMatchInstanceId + 1L;
        }
        activeMatchInstanceId = candidate;
        killiconMatchStatsByPlayer.clear();
        supportSpentScoreByPlayer.clear();
        activeHeroKitIdsByPlayer.clear();
        activeHeroKitTypesByPlayer.clear();
        heroKitCooldownExpireTicksByScopedKey.clear();
        heroKitLoadoutResetPendingPlayers.clear();
        clearAllShellingRequests();
        loadoutItemLastReplenishTickByPlayerSlot.clear();
        gunmasterStageIndexByPlayer.clear();
        gunmasterStageKillsByPlayer.clear();
        gunmasterMatchWeaponSequenceIds.clear();
        rejoinTickets.clear();
        randomizedAttachmentSelectionIdsByPlayerLoadoutEntry.clear();
        resultAwardedPlayers.clear();
        initialMovementLockedPlayers.clear();
        initialMovementLockLastShownSeconds.clear();
        initialMovementUnlockTick = -1L;
        lastHudSyncTick = -1L;
        lastNametagSyncTick = -1L;
    }

    public void clearActiveMatchInstance() {
        activeMatchInstanceId = -1L;
        supportSpentScoreByPlayer.clear();
        activeHeroKitIdsByPlayer.clear();
        activeHeroKitTypesByPlayer.clear();
        heroKitCooldownExpireTicksByScopedKey.clear();
        heroKitLoadoutResetPendingPlayers.clear();
        clearAllShellingRequests();
        loadoutItemLastReplenishTickByPlayerSlot.clear();
        gunmasterStageIndexByPlayer.clear();
        gunmasterStageKillsByPlayer.clear();
        gunmasterMatchWeaponSequenceIds.clear();
        rejoinTickets.clear();
        randomizedAttachmentSelectionIdsByPlayerLoadoutEntry.clear();
        resultAwardedPlayers.clear();
        initialMovementLockedPlayers.clear();
        initialMovementLockLastShownSeconds.clear();
        initialMovementUnlockTick = -1L;
        lastHudSyncTick = -1L;
        lastNametagSyncTick = -1L;
    }

    public void addRoomPlayer(UUID playerId, RoomTeamId teamId) {
        if (playerId == null) {
            return;
        }
        roomPlayers.add(playerId);
        if (teamId != null) {
            roomTeams.put(playerId, teamId);
        }
    }

    public void removeRoomPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        clearRespawnFlow(playerId);
        resultViewTransferPending.remove(playerId);
        roomPlayers.remove(playerId);
        roomTeams.remove(playerId);
        squadLabelsByPlayer.remove(playerId);
        combatUntilTicks.remove(playerId);
        doubledRespawnPending.remove(playerId);
        teammateDeployKillWindowUntilTicks.remove(playerId);
        beaconOwnerByDeployer.remove(playerId);
        beaconOwnerKillWindowUntilTicksByDeployer.remove(playerId);
        initialMovementLockedPlayers.remove(playerId);
        initialMovementLockLastShownSeconds.remove(playerId);
        beaconOwnerByDeployer.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
        squadWipeProgressByKiller.remove(playerId);
        squadWipeProgressByKiller.values().forEach(progressBySquad ->
            progressBySquad.values().forEach(progress -> progress.getKilledMembers().remove(playerId))
        );
        pendingTeamChanges.remove(playerId);
        autoTeamSwitchOfferTargetTeams.remove(playerId);
        autoTeamSwitchOfferExpireTicks.remove(playerId);
        activeHeroKitIdsByPlayer.remove(playerId);
        activeHeroKitTypesByPlayer.remove(playerId);
        heroKitLoadoutResetPendingPlayers.remove(playerId);
        clearShellingRequest(playerId);
        loadoutItemLastReplenishTickByPlayerSlot.remove(playerId);
        randomizedLoadoutClassKeysByPlayer.remove(playerId);
        randomizedLoadoutSelectionIdsByPlayerSlot.remove(playerId);
        randomizedAttachmentSelectionIdsByPlayerLoadoutEntry.remove(playerId);
        fixedRoomLoadoutSelectionIdsByPlayerSlot.remove(playerId);
        outsideBoundarySinceTicks.remove(playerId);
        respawnCountdownLastShownSeconds.remove(playerId);
        boundaryPenaltyLastShownSeconds.remove(playerId);
        boundaryPenaltyVisualApplied.remove(playerId);
        boundaryDeathPendingPlayers.remove(playerId);
        boundaryHeightReportTicks.remove(playerId);
        boundaryPlayerMaxYReportedPlayers.remove(playerId);
        boundaryAircraftMaxYReportedPlayers.remove(playerId);
        tacticalMarkersByOwner.remove(playerId);
        autoTacticalMarkersByOwner.remove(playerId);
        gameplayTacticalMarkersByKey.entrySet().removeIf(entry -> {
            TeamScopedTacticalMarker marker = entry.getValue();
            return marker != null && playerId.equals(marker.marker().targetPlayerId());
        });
    }

    public void clearRoomPlayers() {
        deathPositions.clear();
        pendingDownedStartTicks.clear();
        downedStartTicks.clear();
        downedExpireTicks.clear();
        reviveTargetByReviver.clear();
        reviveReviverByTarget.clear();
        reviveStartTicksByReviver.clear();
        respawnCountdownStartTicks.clear();
        deployReadyTicks.clear();
        respawnFinalizeTicks.clear();
        respawnTargetPositions.clear();
        respawnTargetYRot.clear();
        respawnTargetXRot.clear();
        respawnTargetPose.clear();
        pendingVehicleDeployResolutions.clear();
        deploySelectionKinds.clear();
        deploySelectionTargetIds.clear();
        deploySelectionChangedTicks.clear();
        deployAutoStartOnReadyPlayers.clear();
        deployAllowFallbackOnMissingSelectionPlayers.clear();
        deployExplicitConfirmRequiredPlayers.clear();
        deathKillerEntityIds.clear();
        initialMovementLockedPlayers.clear();
        initialMovementLockLastShownSeconds.clear();
        respawnCountdownLastShownSeconds.clear();
        squadLabelsByPlayer.clear();
        combatUntilTicks.clear();
        doubledRespawnPending.clear();
        teammateDeployKillWindowUntilTicks.clear();
        beaconOwnerByDeployer.clear();
        beaconOwnerKillWindowUntilTicksByDeployer.clear();
        squadWipeProgressByKiller.clear();
        pendingTeamChanges.clear();
        autoTeamSwitchOfferTargetTeams.clear();
        autoTeamSwitchOfferExpireTicks.clear();
        killiconMatchStatsByPlayer.clear();
        supportSpentScoreByPlayer.clear();
        activeHeroKitIdsByPlayer.clear();
        activeHeroKitTypesByPlayer.clear();
        heroKitCooldownExpireTicksByScopedKey.clear();
        heroKitLoadoutResetPendingPlayers.clear();
        clearAllShellingRequests();
        loadoutItemLastReplenishTickByPlayerSlot.clear();
        randomizedLoadoutClassKeysByPlayer.clear();
        randomizedLoadoutSelectionIdsByPlayerSlot.clear();
        randomizedAttachmentSelectionIdsByPlayerLoadoutEntry.clear();
        fixedRoomLoadoutSelectionIdsByPlayerSlot.clear();
        gunmasterStageIndexByPlayer.clear();
        gunmasterStageKillsByPlayer.clear();
        gunmasterMatchWeaponSequenceIds.clear();
        autoTeamBalanceImbalanceSinceTick = -1L;
        autoTeamBalanceLastPromptTick = -1L;
        autoTeamBalanceLastDelta = 0;
        outsideBoundarySinceTicks.clear();
        boundaryPenaltyLastShownSeconds.clear();
        boundaryPenaltyVisualApplied.clear();
        boundaryDeathPendingPlayers.clear();
        aircraftOutsideBoundarySinceTicks.clear();
        boundaryHeightReportTicks.clear();
        boundaryPlayerMaxYReportedPlayers.clear();
        boundaryAircraftMaxYReportedPlayers.clear();
        tacticalMarkersByOwner.clear();
        autoTacticalMarkersByOwner.clear();
        gameplayTacticalMarkersByKey.clear();
        clearResultState();
        clearActiveMatchInstance();
        roomPlayers.clear();
        roomTeams.clear();
        initialMovementUnlockTick = -1L;
        lastHudSyncTick = -1L;
        lastNametagSyncTick = -1L;
    }

    public void markPlayerDead(UUID playerId, RoomPosition deathPosition, long countdownStartTick, int killerEntityId) {
        if (playerId == null) {
            return;
        }
        deathPositions.put(playerId, deathPosition);
        pendingDownedStartTicks.remove(playerId);
        downedStartTicks.remove(playerId);
        downedExpireTicks.remove(playerId);
        clearReviveState(playerId);
        respawnCountdownStartTicks.put(playerId, countdownStartTick);
        deployReadyTicks.remove(playerId);
        respawnFinalizeTicks.remove(playerId);
        respawnTargetPositions.remove(playerId);
        respawnTargetYRot.remove(playerId);
        respawnTargetXRot.remove(playerId);
        respawnTargetPose.remove(playerId);
        pendingVehicleDeployResolutions.remove(playerId);
        deploySelectionKinds.remove(playerId);
        deploySelectionKinds.remove(playerId);
        deploySelectionTargetIds.remove(playerId);
        deploySelectionChangedTicks.remove(playerId);
        deployReadyTicks.remove(playerId);
        deployAutoStartOnReadyPlayers.remove(playerId);
        deployAllowFallbackOnMissingSelectionPlayers.remove(playerId);
        deployExplicitConfirmRequiredPlayers.remove(playerId);
        respawnCountdownLastShownSeconds.remove(playerId);
        if (killerEntityId >= 0) {
            deathKillerEntityIds.put(playerId, killerEntityId);
        } else {
            deathKillerEntityIds.remove(playerId);
        }
    }

    public void markPlayerPendingDowned(UUID playerId, RoomPosition deathPosition, long startTick, int killerEntityId) {
        if (playerId == null) {
            return;
        }
        deathPositions.put(playerId, deathPosition);
        pendingDownedStartTicks.put(playerId, startTick);
        downedStartTicks.remove(playerId);
        downedExpireTicks.remove(playerId);
        clearReviveState(playerId);
        respawnCountdownStartTicks.remove(playerId);
        deployReadyTicks.remove(playerId);
        respawnFinalizeTicks.remove(playerId);
        respawnTargetPositions.remove(playerId);
        respawnTargetYRot.remove(playerId);
        respawnTargetXRot.remove(playerId);
        respawnTargetPose.remove(playerId);
        pendingVehicleDeployResolutions.remove(playerId);
        deploySelectionKinds.remove(playerId);
        deploySelectionTargetIds.remove(playerId);
        deploySelectionChangedTicks.remove(playerId);
        deployAutoStartOnReadyPlayers.remove(playerId);
        deployAllowFallbackOnMissingSelectionPlayers.remove(playerId);
        deployExplicitConfirmRequiredPlayers.remove(playerId);
        respawnCountdownLastShownSeconds.remove(playerId);
        if (killerEntityId >= 0) {
            deathKillerEntityIds.put(playerId, killerEntityId);
        } else {
            deathKillerEntityIds.remove(playerId);
        }
    }

    public void markPlayerDowned(UUID playerId, RoomPosition deathPosition, long startTick, long expireTick, int killerEntityId) {
        if (playerId == null) {
            return;
        }
        deathPositions.put(playerId, deathPosition);
        pendingDownedStartTicks.remove(playerId);
        downedStartTicks.put(playerId, startTick);
        downedExpireTicks.put(playerId, expireTick);
        clearReviveState(playerId);
        respawnCountdownStartTicks.remove(playerId);
        deployReadyTicks.remove(playerId);
        respawnFinalizeTicks.remove(playerId);
        respawnTargetPositions.remove(playerId);
        respawnTargetYRot.remove(playerId);
        respawnTargetXRot.remove(playerId);
        respawnTargetPose.remove(playerId);
        pendingVehicleDeployResolutions.remove(playerId);
        deploySelectionKinds.remove(playerId);
        deploySelectionTargetIds.remove(playerId);
        deploySelectionChangedTicks.remove(playerId);
        deployAutoStartOnReadyPlayers.remove(playerId);
        deployAllowFallbackOnMissingSelectionPlayers.remove(playerId);
        deployExplicitConfirmRequiredPlayers.remove(playerId);
        respawnCountdownLastShownSeconds.remove(playerId);
        if (killerEntityId >= 0) {
            deathKillerEntityIds.put(playerId, killerEntityId);
        } else {
            deathKillerEntityIds.remove(playerId);
        }
    }

    public boolean isPlayerDowned(UUID playerId) {
        return playerId != null && downedExpireTicks.containsKey(playerId);
    }

    public boolean isPlayerPendingDowned(UUID playerId) {
        return playerId != null && pendingDownedStartTicks.containsKey(playerId);
    }

    public boolean isPlayerBeingRevived(UUID playerId) {
        return playerId != null && reviveReviverByTarget.containsKey(playerId);
    }

    public boolean isGameplayCombatPaused() {
        return gameplayRuntime instanceof RoomGameplayIntermissionProvider provider
            && provider.isCombatPaused();
    }

    public boolean isGameplayDeploymentPreparation() {
        return gameplayRuntime instanceof RoomGameplayIntermissionProvider provider
            && provider.isDeploymentPreparation();
    }

    public boolean isInRespawnFlow(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return deathPositions.containsKey(playerId)
            || pendingDownedStartTicks.containsKey(playerId)
            || downedExpireTicks.containsKey(playerId)
            || respawnCountdownStartTicks.containsKey(playerId)
            || deployReadyTicks.containsKey(playerId)
            || respawnFinalizeTicks.containsKey(playerId);
    }

    public boolean isInDeployFlow(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return deployReadyTicks.containsKey(playerId)
            || respawnFinalizeTicks.containsKey(playerId);
    }

    public int getDeathKillerEntityIdOrDefault(UUID playerId) {
        if (playerId == null) {
            return NO_KILLER_ENTITY_ID;
        }
        return deathKillerEntityIds.getOrDefault(playerId, NO_KILLER_ENTITY_ID);
    }

    public void markPlayerInCombat(UUID playerId, long untilTick) {
        if (playerId == null || untilTick <= 0L) {
            return;
        }
        combatUntilTicks.put(playerId, untilTick);
    }

    public boolean isPlayerInCombat(UUID playerId, long gameTick) {
        if (playerId == null) {
            return false;
        }
        Long untilTick = combatUntilTicks.get(playerId);
        if (untilTick == null) {
            return false;
        }
        if (untilTick < gameTick) {
            combatUntilTicks.remove(playerId);
            return false;
        }
        return true;
    }

    public void clearRespawnFlow(UUID playerId) {
        if (playerId == null) {
            return;
        }
        deathPositions.remove(playerId);
        pendingDownedStartTicks.remove(playerId);
        downedStartTicks.remove(playerId);
        downedExpireTicks.remove(playerId);
        clearReviveState(playerId);
        respawnCountdownStartTicks.remove(playerId);
        deployReadyTicks.remove(playerId);
        respawnFinalizeTicks.remove(playerId);
        respawnTargetPositions.remove(playerId);
        respawnTargetYRot.remove(playerId);
        respawnTargetXRot.remove(playerId);
        respawnTargetPose.remove(playerId);
        pendingVehicleDeployResolutions.remove(playerId);
        deploySelectionKinds.remove(playerId);
        deploySelectionTargetIds.remove(playerId);
        deploySelectionChangedTicks.remove(playerId);
        deployAutoStartOnReadyPlayers.remove(playerId);
        deployAllowFallbackOnMissingSelectionPlayers.remove(playerId);
        deployExplicitConfirmRequiredPlayers.remove(playerId);
        deployViewActivePlayers.remove(playerId);
        deathKillerEntityIds.remove(playerId);
        respawnCountdownLastShownSeconds.remove(playerId);
        doubledRespawnPending.remove(playerId);
        boundaryDeathPendingPlayers.remove(playerId);
    }

    public void clearTeamSensitiveStateForTeamChange(UUID playerId) {
        if (playerId == null) {
            return;
        }
        respawnTargetPositions.remove(playerId);
        respawnTargetYRot.remove(playerId);
        respawnTargetXRot.remove(playerId);
        respawnTargetPose.remove(playerId);
        pendingVehicleDeployResolutions.remove(playerId);
        deploySelectionKinds.remove(playerId);
        deploySelectionTargetIds.remove(playerId);
        deploySelectionChangedTicks.remove(playerId);
        deployAutoStartOnReadyPlayers.remove(playerId);
        deployAllowFallbackOnMissingSelectionPlayers.remove(playerId);
        deployExplicitConfirmRequiredPlayers.remove(playerId);
        deployViewActivePlayers.remove(playerId);
        deathKillerEntityIds.remove(playerId);
        teammateDeployKillWindowUntilTicks.remove(playerId);
        beaconOwnerByDeployer.remove(playerId);
        beaconOwnerKillWindowUntilTicksByDeployer.remove(playerId);
        beaconOwnerByDeployer.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
        outsideBoundarySinceTicks.remove(playerId);
        aircraftOutsideBoundarySinceTicks.remove(playerId);
        boundaryPenaltyLastShownSeconds.remove(playerId);
        boundaryPenaltyVisualApplied.remove(playerId);
        boundaryDeathPendingPlayers.remove(playerId);
        boundaryHeightReportTicks.remove(playerId);
        boundaryPlayerMaxYReportedPlayers.remove(playerId);
        boundaryAircraftMaxYReportedPlayers.remove(playerId);
        combatUntilTicks.remove(playerId);
    }

    public void startRevive(UUID reviverId, UUID targetId, long startTick) {
        if (reviverId == null || targetId == null || reviverId.equals(targetId)) {
            return;
        }
        clearReviveState(reviverId);
        clearReviveState(targetId);
        reviveTargetByReviver.put(reviverId, targetId);
        reviveReviverByTarget.put(targetId, reviverId);
        reviveStartTicksByReviver.put(reviverId, startTick);
    }

    public void clearReviveState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        UUID targetId = reviveTargetByReviver.remove(playerId);
        if (targetId != null) {
            reviveReviverByTarget.remove(targetId, playerId);
        }
        reviveStartTicksByReviver.remove(playerId);

        UUID reviverId = reviveReviverByTarget.remove(playerId);
        if (reviverId != null) {
            reviveTargetByReviver.remove(reviverId, playerId);
            reviveStartTicksByReviver.remove(reviverId);
        }
    }

    public void clearResultState() {
        operationsPresentationEndTick = -1L;
        resultWinnerTeam = null;
        resultWinnerPlayerId = null;
        resultReason = "-";
        resultBlackStartTick = -1L;
        resultSequenceEndTick = -1L;
        resultCloseRemainAnnouncedSecond = -1;
        resultViewApplied = false;
        resultLastViewTransferTick = -1L;
        resultViewTransferPending.clear();
        resultAwardedPlayers.clear();
    }

    public boolean isPlayerInShellingFlow(UUID playerId) {
        return playerId != null && activeShellingsByPlayer.containsKey(playerId);
    }

    public ShellingRequestState getActiveShellingRequest(UUID playerId) {
        return playerId == null ? null : activeShellingsByPlayer.get(playerId);
    }

    public ShellingRequestState findActiveShellingRequest(RoomTeamId teamId, String shellingId) {
        if (teamId == null || shellingId == null || shellingId.isBlank()) {
            return null;
        }
        for (ShellingRequestState request : activeShellingsByPlayer.values()) {
            if (request == null) {
                continue;
            }
            if (teamId == request.teamId() && shellingId.equals(request.shellingId())) {
                return request;
            }
        }
        return null;
    }

    public void putShellingRequest(ShellingRequestState request) {
        if (request == null || request.playerId() == null || request.teamId() == null) {
            return;
        }
        clearShellingRequest(request.playerId());
        activeShellingsByPlayer.put(request.playerId(), request);
    }

    public ShellingRequestState clearShellingRequest(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return activeShellingsByPlayer.remove(playerId);
    }

    public void clearAllShellingRequests() {
        activeShellingsByPlayer.clear();
    }

    public record RejoinTicket(
        long matchInstanceId,
        RoomTeamId teamId,
        String squadLabel,
        long leftTick
    ) {
    }

    public record KilliconMatchStats(
        float score,
        int kill,
        int death,
        int assist,
        int revive
    ) {
    }

    public record ShellingRequestState(
        UUID playerId,
        RoomTeamId teamId,
        String roomId,
        String shellingId,
        int reservedCost,
        String originalSlot9ItemSnbt,
        long indicatorIssuedTick,
        long timeoutTick,
        long confirmedTick,
        long returnTick,
        BlockPos targetBlockPos,
        boolean confirmed
    ) {
        public ShellingRequestState {
            roomId = roomId == null ? "" : roomId;
            shellingId = shellingId == null ? "" : shellingId;
            reservedCost = Math.max(0, reservedCost);
            originalSlot9ItemSnbt = originalSlot9ItemSnbt == null ? "" : originalSlot9ItemSnbt;
            targetBlockPos = targetBlockPos == null ? null : targetBlockPos.immutable();
        }
    }

    public record TacticalMarker(
        BlockPos blockPos,
        MarkerKind kind,
        UUID targetPlayerId,
        long placedTick,
        long expireTick,
        long directSpotExpireTick
    ) {
    }

    public record TeamScopedTacticalMarker(
        UUID markerId,
        String ownerName,
        RoomTeamId visibleTeam,
        TacticalMarker marker
    ) {
        public TeamScopedTacticalMarker {
            markerId = markerId == null ? new UUID(0L, 0L) : markerId;
            ownerName = ownerName == null ? "" : ownerName;
        }
    }

    public enum MarkerKind {
        NORMAL,
        OBSERVE_GROUND,
        REQUEST_STRIKE,
        ENEMY,
        DIRECT_ENEMY,
        AUTO_ENEMY,
        ALLY_PERSISTENT,
        SQUAD_PERSISTENT,
        DOWNED_ALLY,
        DOWNED_SQUAD
    }

    public static final class SquadWipeProgress {
        private final long firstKillTick;
        private long lastKillTick;
        private final Set<UUID> killedMembers = new LinkedHashSet<>();

        public SquadWipeProgress(long firstKillTick, long lastKillTick) {
            this.firstKillTick = firstKillTick;
            this.lastKillTick = lastKillTick;
        }

        public long getFirstKillTick() {
            return firstKillTick;
        }

        public long getLastKillTick() {
            return lastKillTick;
        }

        public void setLastKillTick(long lastKillTick) {
            this.lastKillTick = lastKillTick;
        }

        public Set<UUID> getKilledMembers() {
            return killedMembers;
        }
    }
}
