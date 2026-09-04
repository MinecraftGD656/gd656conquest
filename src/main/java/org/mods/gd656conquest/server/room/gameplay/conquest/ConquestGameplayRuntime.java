package org.mods.gd656conquest.server.room.gameplay.conquest;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveSnapshotProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayVehicleDeployRuleProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveEventTextService;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveFocusPayloadService;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectivePresentationContext;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveStatusTextService;
import org.mods.gd656conquest.server.room.model.ConquestFullCaptureResponseMode;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeProvider;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSpawnPolicy;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ConquestGameplayRuntime implements
    IRoomGameplayRuntime,
    RoomObjectiveRuntimeProvider,
    RoomGameplayPresentationProvider,
    RoomGameplayObjectiveSnapshotProvider,
    RoomGameplayDeployPresentationProvider,
    RoomGameplayVehicleDeployRuleProvider {
    private static final int SQUAD_WIPE_WINDOW_TICKS = 25 * 20;
    private long enteredTick = -1L;
    private final RunningRuntimeState runningState;
    private final ConquestCaptureRuntimeService captureRuntimeService;
    private final ConquestHudSubtitleRuntimeService hudSubtitleRuntimeService;
    private final RoomObjectiveFocusPayloadService objectiveFocusPayloadService = new RoomObjectiveFocusPayloadService();
    private final RoomObjectiveEventTextService objectiveEventTextService = new RoomObjectiveEventTextService();
    private final RoomObjectiveStatusTextService objectiveStatusTextService = new RoomObjectiveStatusTextService();
    private final ConquestObjectiveRuntimeView objectiveRuntimeView;
    private final ConquestObjectiveSpawnPolicy objectiveSpawnPolicy;
    private final ConquestCaptureRuntimeState captureState = new ConquestCaptureRuntimeState();
    private final ConquestScoreRuntimeState scoreState = new ConquestScoreRuntimeState();
    private final ConquestHudSubtitleRuntimeState hudSubtitleState = new ConquestHudSubtitleRuntimeState();
    private RoomMatchEndRequest pendingEndRequest;

    public ConquestGameplayRuntime(RunningRuntimeState runningState, ConquestCaptureRuntimeService captureRuntimeService) {
        this.runningState = Objects.requireNonNull(runningState, "runningState");
        this.captureRuntimeService = Objects.requireNonNull(captureRuntimeService, "captureRuntimeService");
        this.hudSubtitleRuntimeService = new ConquestHudSubtitleRuntimeService();
        this.objectiveRuntimeView = new ConquestObjectiveRuntimeView(captureState);
        this.objectiveSpawnPolicy = new ConquestObjectiveSpawnPolicy();
    }

    @Override
    public void onEnterMainPhase(RoomDefinition room, long gameTick) {
        enteredTick = gameTick + RoomPlayerRespawnService.initialCombatUnlockDelayTicks();
        captureState.initialize(captureConfig(room));
        scoreState.clearFullCaptureState();
        scoreState.setCampAScore(resolveInitialScore(room, org.mods.gd656conquest.server.room.model.RoomTeamId.CAMP_A));
        scoreState.setCampBScore(resolveInitialScore(room, org.mods.gd656conquest.server.room.model.RoomTeamId.CAMP_B));
        scoreState.clearTeamEvents();
        hudSubtitleRuntimeService.initialize(hudSubtitleState, room, captureState, scoreState);
    }

    @Override
    public void tick(RoomDefinition room, long gameTick) {
        if (pendingEndRequest != null) {
            return;
        }
        pendingEndRequest = checkDrawDurationEnd(room, gameTick).orElse(null);
        if (pendingEndRequest != null) {
            return;
        }
        pendingEndRequest = captureRuntimeService.tick(room, runningState, captureState, scoreState, gameTick).orElse(null);
        hudSubtitleRuntimeService.tickRunning(hudSubtitleState, room, runningState, captureState, scoreState, gameTick);
        scoreState.clearExpiredEvents(gameTick);
    }

    @Override
    public void onLeaveMainPhase(RoomDefinition room, long gameTick) {
        hudSubtitleRuntimeService.resetAllPlayers(hudSubtitleState, runningState);
        enteredTick = -1L;
        pendingEndRequest = null;
    }

    @Override
    public void onPlayerInvalidated(
        RoomDefinition room,
        java.util.UUID playerId,
        RoomTeamId teamId,
        RoomPlayerInvalidationReason reason,
        int killerEntityId,
        long gameTick
    ) {
        if ((reason != RoomPlayerInvalidationReason.DEATH && reason != RoomPlayerInvalidationReason.REDEPLOY) || pendingEndRequest != null) {
            return;
        }
        if (reason == RoomPlayerInvalidationReason.DEATH && "TROOP_DEPLETION".equalsIgnoreCase(captureConfig(room).scoreMethod())) {
            if (teamId == RoomTeamId.CAMP_A) {
                scoreState.setCampAScore(scoreState.getCampAScore() - 1);
            } else if (teamId == RoomTeamId.CAMP_B) {
                scoreState.setCampBScore(scoreState.getCampBScore() - 1);
            }
            pendingEndRequest = captureRuntimeService.checkTroopDepletionEnd(runningState, scoreState).orElse(null);
            if (pendingEndRequest != null) {
                return;
            }
        }

        if (reason == RoomPlayerInvalidationReason.REDEPLOY) {
            return;
        }

        ServerPlayer deadPlayer = runningState.getRoomPlayers().contains(playerId)
            ? captureRuntimeService.findOnlinePlayer(playerId)
            : null;
        ServerPlayer killerPlayer = captureRuntimeService.resolveKillerPlayer(deadPlayer, killerEntityId);
        if (killerPlayer == null) {
            return; // 自杀（包括非系统判定的重新部署）时，不执行任何加分或特殊连杀逻辑
        }
        if (killerPlayer.getUUID().equals(playerId)) {
            return;
        }
        RoomTeamId killerTeam = runningState.getRoomTeams().get(killerPlayer.getUUID());
        if (killerTeam == null || killerTeam == teamId) {
            return;
        }
        applyLastAliveSquadKillBonus(killerPlayer);
        applyTeammateDeployKillWindowBonus(killerPlayer, gameTick);
        applyBeaconOwnerKillWindowBonus(killerPlayer, gameTick);
        tryAwardSquadWipeBonus(killerPlayer, deadPlayer, gameTick);
        if ("SCORE_TARGET".equalsIgnoreCase(captureConfig(room).scoreMethod())) {
            if (killerTeam == RoomTeamId.CAMP_A) {
                scoreState.setCampAScore(scoreState.getCampAScore() + 1);
                if (scoreState.getCampAScore() >= captureConfig(room).targetScore()) {
                    pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_A, "达到目标得分");
                }
            } else if (killerTeam == RoomTeamId.CAMP_B) {
                scoreState.setCampBScore(scoreState.getCampBScore() + 1);
                if (scoreState.getCampBScore() >= captureConfig(room).targetScore()) {
                    pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_B, "达到目标得分");
                }
            }
        }
    }

    public long getEnteredTick() {
        return enteredTick;
    }

    public ConquestCaptureRuntimeState getCaptureState() {
        return captureState;
    }

    public ConquestScoreRuntimeState getScoreState() {
        return scoreState;
    }

    public void syncEndingHudSubtitles(RoomDefinition room, long gameTick) {
        hudSubtitleRuntimeService.syncEnding(hudSubtitleState, room, runningState, scoreState, gameTick);
    }

    public void clearHudSubtitlePlayer(UUID playerId, ServerPlayer player) {
        hudSubtitleRuntimeService.clearPlayer(hudSubtitleState, playerId, player);
    }

    public String buildCaptureFocusPayload(RoomDefinition room, net.minecraft.server.level.ServerPlayer player) {
        RoomObjectivePresentationContext context = buildObjectiveContext(room, player);
        return context == null ? "-" : objectiveFocusPayloadService.buildFocusPayload(context);
    }

    public String resolveActiveCaptureCode(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return null;
        }
        return captureRuntimeService.resolvePlayerInsideCaptureCode(player, captureConfig(room));
    }

    public String buildCaptureEventText(RoomDefinition room, net.minecraft.server.level.ServerPlayer player, long gameTick) {
        RoomTeamId selfTeam = player == null ? null : runningState.getRoomTeams().get(player.getUUID());
        if (room == null || player == null || selfTeam == null) {
            return "-";
        }
        if (scoreState.getFullCaptureResponseMode() == ConquestFullCaptureResponseMode.COUNTDOWN_END
            && scoreState.getFullCaptureEndTick() > 0
            && scoreState.getFullCaptureOwnerTeam() != null) {
            return selfTeam == scoreState.getFullCaptureOwnerTeam() ? "我方控制了全部据点" : "敌方控制了全部据点";
        }
        String teamEvent = scoreState.resolveTeamEvent(selfTeam, gameTick);
        if (!"-".equals(teamEvent)) {
            return teamEvent;
        }
        String nextEvent = objectiveEventTextService.buildFocusEventText(buildObjectiveContext(room, player));
        if ("-".equals(nextEvent)) {
            int target = Math.max(1, resolveGoal(room, selfTeam));
            int selfScore = selfTeam == RoomTeamId.CAMP_A ? scoreState.getCampAScore() : scoreState.getCampBScore();
            RoomTeamId enemy = selfTeam == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
            int enemyScore = enemy == RoomTeamId.CAMP_A ? scoreState.getCampAScore() : scoreState.getCampBScore();
            if ("TROOP_DEPLETION".equalsIgnoreCase(room.conquestConfig().captureConfig().scoreMethod())) {
                double selfDanger = 1.0D - Math.min(1.0D, selfScore / (double) target);
                double enemyDanger = 1.0D - Math.min(1.0D, enemyScore / (double) Math.max(1, resolveGoal(room, enemy)));
                if (enemyDanger >= 0.8D && enemyDanger > selfDanger) {
                    nextEvent = "敌军兵力即将耗尽";
                } else if (selfDanger >= 0.8D && selfDanger > enemyDanger) {
                    nextEvent = "我军兵力即将耗尽";
                }
            } else {
                double selfProgress = selfScore / (double) target;
                double enemyProgress = enemyScore / (double) Math.max(1, resolveGoal(room, enemy));
                if (selfProgress >= 0.8D && selfProgress > enemyProgress) {
                    nextEvent = "我军即将拿下胜利";
                } else if (enemyProgress >= 0.8D && enemyProgress > selfProgress) {
                    nextEvent = "敌军即将达到目标分数";
                }
            }
        }
        if (!"-".equals(nextEvent)) {
            scoreState.setTeamEvent(selfTeam, nextEvent, gameTick + 100L);
        }
        return nextEvent;
    }

    public String buildCaptureStatusText(RoomDefinition room, net.minecraft.server.level.ServerPlayer player, int remainSeconds) {
        if (room == null) {
            return "-";
        }
        if (scoreState.getFullCaptureResponseMode() == ConquestFullCaptureResponseMode.COUNTDOWN_END
            && scoreState.getFullCaptureEndTick() > 0
            && scoreState.getFullCaptureOwnerTeam() != null) {
            String teamName = room.gameplayTeamDisplayName(scoreState.getFullCaptureOwnerTeam());
            return teamName + " 控制全部据点，决战剩余 " + remainSeconds + "秒";
        }
        RoomObjectivePresentationContext context = buildObjectiveContext(room, player);
        if (context != null) {
            String status = objectiveStatusTextService.buildFocusStatusText(context);
            if (!"-".equals(status)) {
                return status;
            }
        }
        if ("TROOP_DEPLETION".equalsIgnoreCase(room.conquestConfig().captureConfig().scoreMethod())) {
            return "兵力消耗模式";
        }
        if (room.conquestConfig().captureConfig().targetScore() > 0) {
            return "目标分数 " + room.conquestConfig().captureConfig().targetScore();
        }
        return "-";
    }

    public String buildAudioState(RoomDefinition room, RoomTeamId selfTeam, long gameTick) {
        if (room == null) {
            return "-";
        }
        if (scoreState.isFullCapturePressureActive(gameTick)) {
            return resolveFinalObjectiveAudioState(selfTeam, scoreState.getFullCaptureOwnerTeam());
        }
        if ("TROOP_DEPLETION".equalsIgnoreCase(captureConfig(room).scoreMethod()) && isTroopRatioNearEnd(room)) {
            return resolveFinalObjectiveAudioState(selfTeam, resolveTroopPressureWinningTeam(room));
        }
        if ("SCORE_TARGET".equalsIgnoreCase(captureConfig(room).scoreMethod()) && isScoreTargetNearEnd(room)) {
            return resolveFinalObjectiveAudioState(selfTeam, resolveScorePressureWinningTeam(room));
        }
        return "RUNNING";
    }

    @Override
    public RoomPosition resolveRespawnAnchorPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        if (room == null || state == null || player == null) {
            return null;
        }
        RoomTeamId teamId = state.getRoomTeams().get(player.getUUID());
        return new RoomSpawnCandidateSelectionService(player.server)
            .pickSpawn(room.conquestConfig().teamSpawnGroups().get(teamId), state, teamId);
    }

    @Override
    public boolean supportsVehicleDeploy(RoomDefinition room, RunningRuntimeState state) {
        return room != null && room.lifecycleState() == org.mods.gd656conquest.server.room.model.RoomLifecycleState.RUNNING;
    }

    @Override
    public RoomViewPosition resolveDeployViewPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployViewRequest request
    ) {
        return room == null ? null : room.conquestConfig().deployViewPosition();
    }

    @Override
    public RoomPosition resolveTeamDeployIconPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId
    ) {
        if (room == null || teamId == null) {
            return null;
        }
        return room.conquestConfig().findTeamDeployIcon(teamId)
            .or(() -> room.conquestConfig().findTeamSpawn(teamId))
            .orElse(null);
    }

    @Override
    public RoomGameplayHudState buildHudState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        boolean inDeployFlow,
        long gameTick
    ) {
        if (room == null || player == null) {
            return RoomGameplayHudState.defaults();
        }
        int remainSeconds = resolveRemainSeconds(room, state, gameTick);
        String topStatus = resolveTopStatusText(room, player, inDeployFlow, remainSeconds);
        boolean initialLockActive = state != null && state.isInitialMovementLockActive(player.getUUID(), gameTick);
        RoomTeamId selfTeam = state == null ? null : state.getRoomTeams().get(player.getUUID());
        String audioState = room.lifecycleState() == org.mods.gd656conquest.server.room.model.RoomLifecycleState.ENDING
            ? "ENDING"
            : initialLockActive ? "INITIAL_STAGE_BEGIN" : buildAudioState(room, selfTeam, gameTick);
        return new RoomGameplayHudState(
            scoreState.getCampAScore(),
            scoreState.getCampBScore(),
            Math.max(0, (int) ((enteredTick < 0 ? 0L : gameTick - enteredTick) / 20L)),
            captureConfig(room).scoreMethod().isBlank() ? "SCORE_TARGET" : captureConfig(room).scoreMethod(),
            resolveGoal(room, RoomTeamId.CAMP_A),
            resolveGoal(room, RoomTeamId.CAMP_B),
            remainSeconds,
            topStatus,
            buildCaptureEventText(room, player, gameTick),
            buildCaptureFocusPayload(room, player),
            audioState,
            "-"
        );
    }

    @Override
    public int matchDurationSeconds(long gameTick) {
        if (enteredTick < 0L || gameTick <= enteredTick) {
            return 0;
        }
        return (int) ((gameTick - enteredTick) / 20L);
    }

    @Override
    public Map<String, Double> objectiveProgressSnapshot() {
        return new java.util.LinkedHashMap<>(captureState.getProgressByCode());
    }

    @Override
    public Map<String, RoomTeamId> objectiveOwnerSnapshot() {
        return new java.util.LinkedHashMap<>(captureState.getOwnerByCode());
    }

    @Override
    public Map<String, RoomTeamId> objectiveDrivingSnapshot() {
        return new java.util.LinkedHashMap<>(captureState.getDrivingTeamByCode());
    }

    @Override
    public String activeObjectiveCode(RoomDefinition room, ServerPlayer player) {
        return resolveActiveCaptureCode(room, player);
    }

    @Override
    public Optional<RoomMatchEndRequest> pollMatchEndRequest() {
        RoomMatchEndRequest request = pendingEndRequest;
        pendingEndRequest = null;
        return Optional.ofNullable(request);
    }

    @Override
    public RoomObjectiveRuntimeView objectiveRuntimeView() {
        return objectiveRuntimeView;
    }

    @Override
    public RoomObjectiveSpawnPolicy objectiveSpawnPolicy() {
        return objectiveSpawnPolicy;
    }

    private void applyBeaconOwnerKillWindowBonus(ServerPlayer killerPlayer, long gameTick) {
        if (killerPlayer == null) {
            return;
        }
        java.util.UUID killerId = killerPlayer.getUUID();
        Long windowUntilTick = runningState.getBeaconOwnerKillWindowUntilTicksByDeployer().get(killerId);
        java.util.UUID beaconOwnerId = runningState.getBeaconOwnerByDeployer().get(killerId);
        if (windowUntilTick == null || beaconOwnerId == null) {
            return;
        }
        if (windowUntilTick < gameTick) {
            runningState.getBeaconOwnerKillWindowUntilTicksByDeployer().remove(killerId);
            runningState.getBeaconOwnerByDeployer().remove(killerId);
            return;
        }
        if (beaconOwnerId.equals(killerId)) {
            return;
        }
        ServerPlayer beaconOwner = captureRuntimeService.findOnlinePlayer(beaconOwnerId);
        if (beaconOwner != null) {
            KilliconScoreboardBridge.tryAwardConquestBonus(beaconOwner, "EMERGENCY_REINFORCEMENT", 1.0F, "");
        }
    }

    private void applyTeammateDeployKillWindowBonus(ServerPlayer killerPlayer, long gameTick) {
        if (killerPlayer == null) {
            return;
        }
        UUID killerId = killerPlayer.getUUID();
        Long windowUntilTick = runningState.getTeammateDeployKillWindowUntilTicks().get(killerId);
        if (windowUntilTick == null) {
            return;
        }
        if (windowUntilTick < gameTick) {
            runningState.getTeammateDeployKillWindowUntilTicks().remove(killerId);
            return;
        }
        runningState.getTeammateDeployKillWindowUntilTicks().remove(killerId);
        KilliconScoreboardBridge.tryAwardConquestBonus(killerPlayer, "EMERGENCY_REINFORCEMENT", 1.0F, "");
    }

    private void applyLastAliveSquadKillBonus(ServerPlayer killerPlayer) {
        if (killerPlayer == null || !isLastAlivePlayerInSquad(killerPlayer.getUUID())) {
            return;
        }
        KilliconScoreboardBridge.tryAwardConquestBonus(killerPlayer, "SQUAD_LAST_MEMBER_KILL", 1.0F, "");
    }

    private boolean isLastAlivePlayerInSquad(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        String squadLabel = runningState.getSquadLabelsByPlayer().get(playerId);
        if (squadLabel == null || squadLabel.isBlank()) {
            return false;
        }
        RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            return false;
        }
        int aliveCount = 0;
        for (UUID roomPlayerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(roomPlayerId) != teamId) {
                continue;
            }
            if (!isSameSquadLabel(squadLabel, runningState.getSquadLabelsByPlayer().get(roomPlayerId))) {
                continue;
            }
            ServerPlayer roomPlayer = captureRuntimeService.findOnlinePlayer(roomPlayerId);
            if (roomPlayer == null || !roomPlayer.isAlive() || roomPlayer.isSpectator()) {
                continue;
            }
            aliveCount++;
            if (aliveCount > 1) {
                return false;
            }
        }
        return aliveCount == 1;
    }

    private void tryAwardSquadWipeBonus(ServerPlayer killerPlayer, ServerPlayer deadPlayer, long gameTick) {
        if (killerPlayer == null || deadPlayer == null) {
            return;
        }
        UUID killerId = killerPlayer.getUUID();
        UUID deadId = deadPlayer.getUUID();
        RoomTeamId deadTeam = runningState.getRoomTeams().get(deadId);
        if (deadTeam != RoomTeamId.CAMP_A && deadTeam != RoomTeamId.CAMP_B) {
            return;
        }
        String deadSquadLabel = runningState.getSquadLabelsByPlayer().get(deadId);
        if (deadSquadLabel == null || deadSquadLabel.isBlank()) {
            return;
        }
        Set<UUID> targetMembers = new HashSet<>();
        for (UUID roomPlayerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(roomPlayerId) != deadTeam) {
                continue;
            }
            if (!isSameSquadLabel(deadSquadLabel, runningState.getSquadLabelsByPlayer().get(roomPlayerId))) {
                continue;
            }
            targetMembers.add(roomPlayerId);
        }
        if (targetMembers.isEmpty()) {
            return;
        }
        pruneSquadWipeProgress(gameTick);
        String squadKey = buildSquadWipeKey(deadTeam, deadSquadLabel);
        Map<String, RunningRuntimeState.SquadWipeProgress> progressBySquad = runningState.getSquadWipeProgressByKiller()
            .computeIfAbsent(killerId, ignored -> new java.util.LinkedHashMap<>());
        RunningRuntimeState.SquadWipeProgress progress = progressBySquad.get(squadKey);
        if (progress == null || gameTick - progress.getFirstKillTick() > SQUAD_WIPE_WINDOW_TICKS) {
            progress = new RunningRuntimeState.SquadWipeProgress(gameTick, gameTick);
            progressBySquad.put(squadKey, progress);
        }
        progress.setLastKillTick(gameTick);
        progress.getKilledMembers().add(deadId);
        progress.getKilledMembers().retainAll(targetMembers);
        if (progress.getKilledMembers().containsAll(targetMembers)
            && gameTick - progress.getFirstKillTick() <= SQUAD_WIPE_WINDOW_TICKS) {
            progressBySquad.remove(squadKey);
            KilliconScoreboardBridge.tryAwardConquestBonus(killerPlayer, "SQUAD_WIPE_COMPLETION", 1.0F, "");
        }
        if (progressBySquad.isEmpty()) {
            runningState.getSquadWipeProgressByKiller().remove(killerId);
        }
    }

    private void pruneSquadWipeProgress(long gameTick) {
        runningState.getSquadWipeProgressByKiller().entrySet().removeIf(entry -> {
            Map<String, RunningRuntimeState.SquadWipeProgress> progressBySquad = entry.getValue();
            if (progressBySquad == null || progressBySquad.isEmpty()) {
                return true;
            }
            progressBySquad.entrySet().removeIf(progressEntry ->
                progressEntry.getValue() == null
                    || gameTick - progressEntry.getValue().getLastKillTick() > SQUAD_WIPE_WINDOW_TICKS
            );
            return progressBySquad.isEmpty();
        });
    }

    private String buildSquadWipeKey(RoomTeamId teamId, String squadLabel) {
        return (teamId == null ? "-" : teamId.name()) + "|" + (squadLabel == null ? "" : squadLabel.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private boolean isSameSquadLabel(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equalsIgnoreCase(right);
    }

    private int resolveInitialScore(RoomDefinition room, org.mods.gd656conquest.server.room.model.RoomTeamId teamId) {
        if ("TROOP_DEPLETION".equalsIgnoreCase(captureConfig(room).scoreMethod())) {
            return teamId == org.mods.gd656conquest.server.room.model.RoomTeamId.CAMP_A
                ? captureConfig(room).campAInitialTroops()
                : captureConfig(room).campBInitialTroops();
        }
        return 0;
    }

    private int resolveGoal(RoomDefinition room, RoomTeamId teamId) {
        if ("TROOP_DEPLETION".equalsIgnoreCase(captureConfig(room).scoreMethod())) {
            return teamId == RoomTeamId.CAMP_A ? captureConfig(room).campAInitialTroops() : captureConfig(room).campBInitialTroops();
        }
        return captureConfig(room).targetScore();
    }

    private int resolveRemainSeconds(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room != null && room.lifecycleState() == org.mods.gd656conquest.server.room.model.RoomLifecycleState.ENDING
            && state != null && state.getResultSequenceEndTick() > 0) {
            return (int) Math.max(0L, (state.getResultSequenceEndTick() - gameTick + 19L) / 20L);
        }
        if (scoreState.getFullCaptureResponseMode() == ConquestFullCaptureResponseMode.COUNTDOWN_END
            && scoreState.getFullCaptureEndTick() > 0) {
            return (int) Math.max(0L, (scoreState.getFullCaptureEndTick() - gameTick + 19L) / 20L);
        }
        long matchEndTick = resolveMatchEndTick(room);
        if (matchEndTick > 0L) {
            return (int) Math.max(0L, (matchEndTick - gameTick + 19L) / 20L);
        }
        return 0;
    }

    private long resolveMatchEndTick(RoomDefinition room) {
        if (room == null) {
            return -1L;
        }
        int drawDurationMinutes = captureConfig(room).drawDurationMinutes();
        if (drawDurationMinutes <= 0 || enteredTick < 0L) {
            return -1L;
        }
        return enteredTick + drawDurationMinutes * 60L * 20L;
    }

    private String resolveTopStatusText(RoomDefinition room, ServerPlayer player, boolean inDeployFlow, int remainSeconds) {
        if (room == null) {
            return "-";
        }
        if (room.lifecycleState() == org.mods.gd656conquest.server.room.model.RoomLifecycleState.ENDING) {
            return remainSeconds > 0 ? "结算中，房间将在 " + remainSeconds + " 秒后关闭" : "结算中";
        }
        if (inDeployFlow) {
            return "部署中";
        }
        if (player == null) {
            return "进行中";
        }
        String runtimeStatus = buildCaptureStatusText(room, player, remainSeconds);
        if (runtimeStatus != null && !runtimeStatus.isBlank() && !"-".equals(runtimeStatus)) {
            return runtimeStatus;
        }
        return "进行中";
    }

    private Optional<RoomMatchEndRequest> checkDrawDurationEnd(RoomDefinition room, long gameTick) {
        if (room == null || enteredTick < 0L) {
            return Optional.empty();
        }
        int drawDurationMinutes = captureConfig(room).drawDurationMinutes();
        if (drawDurationMinutes <= 0) {
            return Optional.empty();
        }
        long matchEndTick = enteredTick + drawDurationMinutes * 60L * 20L;
        if (gameTick < matchEndTick) {
            return Optional.empty();
        }
        // GD656Mods: 时间耗尽不再直接平局 —— 分数(SCORE_TARGET)/兵力剩余(TROOP_DEPLETION)最多的一方胜, 相同才平局
        RoomTeamId winner = null;
        if (scoreState.getCampAScore() > scoreState.getCampBScore()) {
            winner = RoomTeamId.CAMP_A;
        } else if (scoreState.getCampBScore() > scoreState.getCampAScore()) {
            winner = RoomTeamId.CAMP_B;
        }
        String reason = winner == null ? "达到时长，判定平局" : "达到时长，" + (winner == RoomTeamId.CAMP_A ? "CampA" : "CampB") + "分数领先";
        return Optional.of(RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), winner, reason));
    }

    private boolean isTroopRatioNearEnd(RoomDefinition room) {
        int campAGoal = Math.max(1, captureConfig(room).campAInitialTroops());
        int campBGoal = Math.max(1, captureConfig(room).campBInitialTroops());
        double campARemainRatio = scoreState.getCampAScore() / (double) campAGoal;
        double campBRemainRatio = scoreState.getCampBScore() / (double) campBGoal;
        return campARemainRatio <= 0.2D || campBRemainRatio <= 0.2D;
    }

    private boolean isScoreTargetNearEnd(RoomDefinition room) {
        int campAGoal = Math.max(1, captureConfig(room).targetScore());
        int campBGoal = Math.max(1, captureConfig(room).targetScore());
        double campAProgress = scoreState.getCampAScore() / (double) campAGoal;
        double campBProgress = scoreState.getCampBScore() / (double) campBGoal;
        return (campAProgress >= 0.8D && campAProgress > campBProgress)
            || (campBProgress >= 0.8D && campBProgress > campAProgress);
    }

    private RoomTeamId resolveTroopPressureWinningTeam(RoomDefinition room) {
        if (room == null) {
            return null;
        }
        int campAGoal = Math.max(1, captureConfig(room).campAInitialTroops());
        int campBGoal = Math.max(1, captureConfig(room).campBInitialTroops());
        double campARemainRatio = scoreState.getCampAScore() / (double) campAGoal;
        double campBRemainRatio = scoreState.getCampBScore() / (double) campBGoal;
        if (campARemainRatio > campBRemainRatio) {
            return RoomTeamId.CAMP_A;
        }
        if (campBRemainRatio > campARemainRatio) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private RoomTeamId resolveScorePressureWinningTeam(RoomDefinition room) {
        if (room == null) {
            return null;
        }
        int targetScore = Math.max(1, captureConfig(room).targetScore());
        double campAProgress = scoreState.getCampAScore() / (double) targetScore;
        double campBProgress = scoreState.getCampBScore() / (double) targetScore;
        if (campAProgress > campBProgress) {
            return RoomTeamId.CAMP_A;
        }
        if (campBProgress > campAProgress) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private String resolveFinalObjectiveAudioState(RoomTeamId selfTeam, RoomTeamId winningTeam) {
        if (winningTeam == null) {
            return "FINAL_OBJECTIVE";
        }
        if (selfTeam == winningTeam) {
            return "FINAL_OBJECTIVE_VICTORY";
        }
        if (selfTeam == oppositeTeam(winningTeam)) {
            return "FINAL_OBJECTIVE_DEFEAT";
        }
        return "FINAL_OBJECTIVE";
    }

    private RoomTeamId oppositeTeam(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return RoomTeamId.CAMP_B;
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return RoomTeamId.CAMP_A;
        }
        return null;
    }

    private RoomObjectivePresentationContext buildObjectiveContext(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return null;
        }
        RoomTeamId selfTeam = runningState.getRoomTeams().get(player.getUUID());
        if (selfTeam == null || runningState.isInRespawnFlow(player.getUUID()) || player.isSpectator()) {
            return null;
        }
        return new RoomObjectivePresentationContext(
            RoomObjectiveSupport.activePoints(captureConfig(room)),
            objectiveRuntimeView,
            captureRuntimeService.resolvePlayerInsideCaptureCode(player, captureConfig(room)),
            selfTeam
        );
    }

    private RoomCaptureConfig captureConfig(RoomDefinition room) {
        return room.conquestConfig().captureConfig();
    }
}
