package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomHudSubtitlePacket;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryHudProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveDefinitionProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployViewRequest;
import org.mods.gd656conquest.server.room.gameplay.RoomHudSubtitleBroadcastService;
import org.mods.gd656conquest.server.room.gameplay.RoomDeployHudIcon;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveEventTextService;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveFocusPayloadService;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectivePresentationContext;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveSnapshotProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayVehicleDeployRuleProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveHudSubtitleRuntimeService;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveHudSubtitleRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveHudSubtitleRuntimeService.RoomObjectiveSubtitleEntry;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomTroopBalanceReference;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeProvider;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSpawnPolicy;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryPolygonSupport;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudBuildContext;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudElement;
import org.mods.gd656conquest.server.room.runtime.RoomMarkerRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeRecord;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BreakthroughGameplayRuntime implements IRoomGameplayRuntime,
    RoomGameplayPresentationProvider,
    RoomGameplayObjectiveSnapshotProvider,
    RoomGameplayObjectiveDefinitionProvider,
    RoomObjectiveRuntimeProvider,
    RoomGameplayDeployPresentationProvider,
    RoomGameplayBoundaryPolicyProvider,
    RoomGameplayBoundaryHudProvider,
    RoomGameplayVehicleDeployRuleProvider {
    private static final int STAGE_ADVANCE_COUNTDOWN_SECONDS = 5;
    private static final int ATTACK_TROOP_REFERENCE_PLAYERS = 12;
    private static final int STAGE_REWARD_DIVISOR = 2;
    private static final long STAGE_REWARD_EVENT_TICKS = 5L * 20L;
    private static final int BACK_TO_WALL_DURATION_SECONDS = 180;
    private static final int BURN_BOATS_DURATION_SECONDS = 60;
    private static final long DESPERATE_TRIGGER_EVENT_TICKS = 5L * 20L;
    private static final String RETREAT_MARKER_ATTACKER_PREFIX = "bt_retreat_attackers_";
    private static final String RETREAT_MARKER_DEFENDER_PREFIX = "bt_retreat_defenders_";
    private static final String RETREAT_MARKER_OWNER_NAME = "撤离标记";
    private static final long RETREAT_MARKER_REFRESH_TICKS = 3L * 20L;

    private final RunningRuntimeState runningState;
    private final BreakthroughCaptureRuntimeService captureRuntimeService;
    private final BreakthroughStageTransitionTitleService titleService;
    private final RoomMarkerRuntimeService markerRuntimeService;
    private final BreakthroughRuntimeState runtimeState = new BreakthroughRuntimeState();
    private final BreakthroughObjectiveRuntimeView objectiveRuntimeView = new BreakthroughObjectiveRuntimeView(runtimeState);
    private final BreakthroughObjectiveSpawnPolicy objectiveSpawnPolicy = new BreakthroughObjectiveSpawnPolicy();
    private final RoomObjectiveEventTextService objectiveEventTextService = new RoomObjectiveEventTextService();
    private final RoomObjectiveFocusPayloadService objectiveFocusPayloadService = new RoomObjectiveFocusPayloadService();
    private final RoomObjectiveHudSubtitleRuntimeService objectiveSubtitleRuntimeService = new RoomObjectiveHudSubtitleRuntimeService();
    private final RoomObjectiveHudSubtitleRuntimeState objectiveSubtitleState = new RoomObjectiveHudSubtitleRuntimeState();
    private final RoomHudSubtitleBroadcastService subtitleBroadcastService = new RoomHudSubtitleBroadcastService();
    private final Map<java.util.UUID, String> endingSubtitleByPlayer = new LinkedHashMap<>();
    private final Map<java.util.UUID, String> retreatSubtitleByPlayer = new LinkedHashMap<>();
    private final Map<java.util.UUID, String> finalSegmentSubtitleByPlayer = new LinkedHashMap<>();
    private final Map<java.util.UUID, String> desperateSubtitleByPlayer = new LinkedHashMap<>();
    private final Set<java.util.UUID> retreatMarkedDefenders = new LinkedHashSet<>();

    private long enteredTick = -1L;
    private RoomMatchEndRequest pendingEndRequest;
    private boolean initialCombatSubtitleDispatched;
    private boolean attackTroopHalfSubtitleDispatched;
    private boolean attackTroopCriticalSubtitleDispatched;

    public BreakthroughGameplayRuntime(
        RunningRuntimeState runningState,
        BreakthroughCaptureRuntimeService captureRuntimeService,
        BreakthroughStageTransitionTitleService titleService,
        RoomMarkerRuntimeService markerRuntimeService
    ) {
        this.runningState = runningState;
        this.captureRuntimeService = captureRuntimeService;
        this.titleService = titleService;
        this.markerRuntimeService = markerRuntimeService;
    }

    @Override
    public void onEnterMainPhase(RoomDefinition room, long gameTick) {
        enteredTick = gameTick + RoomPlayerRespawnService.initialCombatUnlockDelayTicks();
        pendingEndRequest = null;
        initialCombatSubtitleDispatched = false;
        attackTroopHalfSubtitleDispatched = false;
        attackTroopCriticalSubtitleDispatched = false;
        runtimeState.initialize(room == null ? null : room.breakthroughConfig(), enteredTick);
        initializeAttackTroopCapForOpeningStage(room);
        applyStageReward(room);
        initializeObjectiveSubtitles(room);
        clearRetreatDefenderMarkers(room, gameTick);
        clearRetreatPersistentSubtitles();
        clearDesperatePersistentSubtitles();
    }

    @Override
    public void tick(RoomDefinition room, long gameTick) {
        if (room == null || pendingEndRequest != null) {
            return;
        }
        syncCombatNarrativeSubtitles(room, gameTick);
        syncDesperatePersistentSubtitles();
        syncFinalSegmentPersistentSubtitles();
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.COMBAT) {
            if (gameTick >= runtimeState.getStageDeadlineTick()) {
                broadcastOutcomeSubtitle(
                    "bt_match_end_timeout_" + gameTick,
                    "战斗时间结束，本局平局",
                    "战斗时间结束，本局平局"
                );
                pendingEndRequest = new RoomMatchEndRequest((RoomTeamId) null, "达到时长，判定平局");
                return;
            }
            captureRuntimeService.tick(room, runningState, runtimeState, gameTick);
            tickObjectiveSubtitles(room);
            if (runtimeState.areAllActiveObjectivesFullyCapturedByAttackers(room.breakthroughConfig())) {
                if (runtimeState.getCurrentStageIndex() >= runtimeState.getMaxStageCount()) {
                    broadcastOutcomeSubtitle(
                        "bt_match_end_capture_" + gameTick,
                        "我方突破了全部防线",
                        "敌军突破了全部防线"
                    );
                    pendingEndRequest = new RoomMatchEndRequest(RoomTeamId.CAMP_A, "进攻方占领全部阶段");
                    return;
                }
                int defenders = captureRuntimeService.countDefendersInStageBoundary(room, runningState, runtimeState.getCurrentStageIndex());
                if (room.breakthroughConfig().defenderRetreatSeconds() <= 0 || defenders <= 0) {
                    startAdvanceCountdown(room, gameTick);
                } else {
                    broadcastStageTransitionSubtitle(
                        "bt_stage_captured_" + runtimeState.getCurrentStageIndex() + "_" + gameTick,
                        "我方已夺得阶段 " + stageLabel(runtimeState.getCurrentStageIndex()),
                        "我方未能守住阶段 " + stageLabel(runtimeState.getCurrentStageIndex())
                    );
                    runtimeState.startRetreat(gameTick, room.breakthroughConfig().defenderRetreatSeconds());
                    syncRetreatPersistentSubtitles(room);
                    titleService.syncRetreatTitles(room, runningState, runtimeState, captureRuntimeService, gameTick);
                    syncRetreatDefenderMarkers(room, gameTick);
                }
            }
            if (tickDesperateModeState(room, gameTick)) {
                return;
            }
            return;
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.RETREAT) {
            syncRetreatPersistentSubtitles(room);
            titleService.syncRetreatTitles(room, runningState, runtimeState, captureRuntimeService, gameTick);
            syncRetreatDefenderMarkers(room, gameTick);
            int defenders = captureRuntimeService.countDefendersInStageBoundary(room, runningState, runtimeState.getRetreatStageIndex());
            if (defenders <= 0 || gameTick >= runtimeState.getRetreatDeadlineTick()) {
                startAdvanceCountdown(room, gameTick);
            }
            return;
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.ADVANCE_COUNTDOWN) {
            titleService.syncCountdownTitles(runningState, runtimeState, gameTick);
            if (gameTick >= runtimeState.getAdvanceCountdownEndTick()) {
                titleService.clearCountdownTitles(runningState, runtimeState);
                clearRetreatDefenderMarkers(room, gameTick);
                int previousStageIndex = runtimeState.getCurrentStageIndex();
                runtimeState.advanceToNextStage(room.breakthroughConfig(), gameTick);
                refreshAttackTroopCapForNextStage(room);
                int rewardTroops = applyStageReward(room);
                initializeObjectiveSubtitles(room);
                runtimeState.markStageReward(gameTick, rewardTroops, runtimeState.getCurrentStageIndex());
                broadcastStageStartSubtitles(gameTick);
                playStageAdvanceVoiceForAttackers();
                refreshDeployViewForRespawnPlayers(room, gameTick, previousStageIndex, runtimeState.getCurrentStageIndex());
            }
        }
    }

    @Override
    public void onLeaveMainPhase(RoomDefinition room, long gameTick) {
        titleService.clearCountdownTitles(runningState, runtimeState);
        titleService.clearRetreatTitles(runningState, runtimeState);
        objectiveSubtitleRuntimeService.resetAllPlayers(objectiveSubtitleState, runningState);
        clearRetreatDefenderMarkers(room, gameTick);
        clearRetreatPersistentSubtitles();
        clearDesperatePersistentSubtitles();
        clearFinalSegmentPersistentSubtitles();
        endingSubtitleByPlayer.clear();
        enteredTick = -1L;
        pendingEndRequest = null;
        initialCombatSubtitleDispatched = false;
        attackTroopHalfSubtitleDispatched = false;
        attackTroopCriticalSubtitleDispatched = false;
        runtimeState.initialize(null, 0L);
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
        if (reason == RoomPlayerInvalidationReason.DEATH && teamId == RoomTeamId.CAMP_A && runtimeState.getPhase() == BreakthroughRuntimeState.Phase.COMBAT) {
            if (runtimeState.isDesperateModeActive()) {
                return;
            }
            runtimeState.consumeAttackTroop(1);
            if (runtimeState.getAttackTroops() <= 0) {
                if (tryActivateBackToWall(room, gameTick)) {
                    return;
                }
                broadcastOutcomeSubtitle(
                    "bt_match_end_troops_" + gameTick,
                    "我方兵力已耗尽",
                    "敌军兵力已耗尽"
                );
                pendingEndRequest = new RoomMatchEndRequest(RoomTeamId.CAMP_B, "进攻方兵力耗尽");
                return;
            }
        }
        if (reason == RoomPlayerInvalidationReason.REDEPLOY) {
            return;
        }
        if (teamId != RoomTeamId.CAMP_B || room == null || runtimeState.getPhase() != BreakthroughRuntimeState.Phase.RETREAT) {
            return;
        }
        ServerPlayer deadPlayer = captureRuntimeService.findOnlinePlayer(playerId);
        if (!captureRuntimeService.isInsideStageBoundary(room, deadPlayer, runtimeState.getRetreatStageIndex())) {
            return;
        }
        ServerPlayer killerPlayer = captureRuntimeService.resolveKillerPlayer(deadPlayer, killerEntityId);
        if (killerPlayer == null) {
            return;
        }
        if (runningState.getRoomTeams().get(killerPlayer.getUUID()) == RoomTeamId.CAMP_A) {
            runtimeState.addAttackTroops(room.breakthroughConfig().troopGainPerDefenderKill());
        }
    }

    @Override
    public RoomGameplayHudState buildHudState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        boolean inDeployFlow,
        long gameTick
    ) {
        int elapsedSeconds = enteredTick < 0L || gameTick <= enteredTick ? 0 : (int) ((gameTick - enteredTick) / 20L);
        boolean initialLockActive = state != null && player != null && state.isInitialMovementLockActive(player.getUUID(), gameTick);
        RoomTeamId selfTeam = state == null || player == null ? null : state.getRoomTeams().get(player.getUUID());
        String audioState = room != null && room.lifecycleState() == RoomLifecycleState.ENDING
            ? "ENDING"
            : initialLockActive ? "INITIAL_STAGE_BEGIN" : buildAudioState(selfTeam);
        return new RoomGameplayHudState(
            runtimeState.isDesperateModeActive() ? -1 : runtimeState.getAttackTroops(),
            runtimeState.getCurrentStageIndex(),
            elapsedSeconds,
            "BREAKTHROUGH",
            runtimeState.getAttackTroopCap(),
            runtimeState.getMaxStageCount(),
            resolveRemainSeconds(gameTick),
            room != null && room.lifecycleState() == RoomLifecycleState.ENDING ? "结算中" : resolveTopStatus(inDeployFlow, gameTick),
            buildEventText(room, player, state == null || player == null ? null : state.getRoomTeams().get(player.getUUID()), gameTick),
            buildFocusPayload(room, player),
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
        return new LinkedHashMap<>(runtimeState.getProgressByCode());
    }

    @Override
    public Map<String, RoomTeamId> objectiveOwnerSnapshot() {
        return new LinkedHashMap<>(runtimeState.getOwnerByCode());
    }

    @Override
    public Map<String, RoomTeamId> objectiveDrivingSnapshot() {
        return new LinkedHashMap<>(runtimeState.getDrivingTeamByCode());
    }

    @Override
    public String activeObjectiveCode(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return null;
        }
        return captureRuntimeService.resolvePlayerInsideActiveObjectiveCode(room, runtimeState, player);
    }

    @Override
    public List<RoomCapturePointDefinition> activeObjectivePoints(RoomDefinition room) {
        if (room == null) {
            return List.of();
        }
        int currentStageIndex = runtimeState.getCurrentStageIndex();
        if (currentStageIndex <= 0) {
            return room.breakthroughConfig().allActivePoints();
        }
        return room.breakthroughConfig().stageActivePoints(currentStageIndex);
    }

    @Override
    public RoomCapturePointDefinition findObjectivePoint(RoomDefinition room, String objectiveCode) {
        if (room == null || objectiveCode == null || objectiveCode.isBlank()) {
            return null;
        }
        return room.breakthroughConfig().points().values().stream()
            .filter(point -> point.code().equalsIgnoreCase(objectiveCode))
            .findFirst()
            .orElse(null);
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

    @Override
    public Integer resolveRespawnDelaySeconds(RoomDefinition room, RunningRuntimeState state) {
        return runtimeState.isDesperateModeActive() ? 0 : null;
    }

    @Override
    public int boundaryPenaltySeconds(RoomDefinition room) {
        return room == null ? 0 : room.gameplayBoundaryPenaltySeconds();
    }

    @Override
    public boolean hasConfiguredBoundary(RoomDefinition room, RunningRuntimeState state) {
        if (room == null) {
            return false;
        }
        java.util.ArrayList<List<RoomPosition>> configured = new java.util.ArrayList<>();
        addBoundaryIfValid(configured, room.breakthroughConfig().campABaseBoundary());
        addBoundaryIfValid(configured, room.breakthroughConfig().campBBaseBoundary());
        for (int stageIndex = 1; stageIndex <= runtimeState.getMaxStageCount(); stageIndex++) {
            addBoundaryIfValid(configured, room.breakthroughConfig().stageBoundary(stageIndex));
        }
        return RoomBoundaryRuleEvaluator.hasConfiguredBoundary(configured);
    }

    @Override
    public List<List<RoomPosition>> resolveAllowedBoundaries(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId
    ) {
        return room == null || teamId == null ? List.of() : allowedBoundaries(room, teamId);
    }

    @Override
    public void collectBoundaryElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        if (context == null || context.room() == null) {
            return;
        }
        appendBoundaryRows(elements, context.room().breakthroughConfig().campABaseBoundary(), "T", "bt_camp_a_base");
        appendBoundaryRows(elements, context.room().breakthroughConfig().campBBaseBoundary(), "S", "bt_camp_b_base");
        for (int stageIndex = 1; stageIndex <= runtimeState.getMaxStageCount(); stageIndex++) {
            String teamToken = stageBoundaryToken(stageIndex);
            appendBoundaryRows(
                elements,
                context.room().breakthroughConfig().stageBoundary(stageIndex),
                teamToken,
                "bt_stage_" + stageIndex
            );
        }
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
        RoomPosition spawn = new RoomSpawnCandidateSelectionService(player.server).pickSpawn(
            teamId == RoomTeamId.CAMP_A ? room.breakthroughConfig().campABaseSpawnGroup() : room.breakthroughConfig().campBBaseSpawnGroup(),
            state,
            teamId
        );
        return spawn != null ? spawn : room.commonConfig().queueSpawn();
    }

    @Override
    public boolean supportsVehicleDeploy(RoomDefinition room, RunningRuntimeState state) {
        return room != null && room.lifecycleState() == RoomLifecycleState.RUNNING && runtimeState.getCurrentStageIndex() > 0;
    }

    @Override
    public boolean isVehicleDeployEntryActive(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        VehicleRuntimeRecord record
    ) {
        return supportsVehicleDeploy(room, state) && shouldDisplayVehicleDeployEntry(room, state, player, teamId, record);
    }

    @Override
    public String describeInactiveVehicleDeployEntry(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        VehicleRuntimeRecord record
    ) {
        if (record == null) {
            return "该载具当前不在本阶段部署范围内";
        }
        Integer stageIndex = normalizeVehicleStageIndex(record);
        int currentStageIndex = runtimeState.getCurrentStageIndex();
        if (stageIndex == null) {
            return "该载具未配置阶段，当前无法在突破模式部署";
        }
        if (currentStageIndex <= 0) {
            return "突破模式阶段尚未开始，当前无法部署载具";
        }
        if (stageIndex > currentStageIndex) {
            return "该载具将在后续阶段开放";
        }
        if (stageIndex < currentStageIndex) {
            return "历史阶段载具已损失，当前无法恢复";
        }
        return "该载具当前不在本阶段部署范围内";
    }

    @Override
    public boolean shouldDisplayVehicleDeployEntry(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        VehicleRuntimeRecord record
    ) {
        if (!supportsVehicleDeploy(room, state) || record == null) {
            return false;
        }
        Integer stageIndex = normalizeVehicleStageIndex(record);
        if (stageIndex == null) {
            return false;
        }
        int currentStageIndex = runtimeState.getCurrentStageIndex();
        if (stageIndex > currentStageIndex) {
            return false;
        }
        if (stageIndex == currentStageIndex) {
            return true;
        }
        return record.getSpawnedEntityUuid() != null;
    }

    @Override
    public boolean shouldRetireVehicleEntryWhenUnspawned(
        RoomDefinition room,
        RunningRuntimeState state,
        VehicleRuntimeRecord record
    ) {
        if (!supportsVehicleDeploy(room, state) || record == null || record.getSpawnedEntityUuid() != null) {
            return false;
        }
        Integer stageIndex = normalizeVehicleStageIndex(record);
        return stageIndex != null && stageIndex < runtimeState.getCurrentStageIndex();
    }

    @Override
    public RoomViewPosition resolveDeployViewPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomGameplayDeployViewRequest request
    ) {
        if (room == null) {
            return null;
        }
        return room.breakthroughConfig().stageDeployViewPosition(runtimeState.getCurrentStageIndex());
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
        return resolveBaseDeployIconPosition(room, teamId);
    }

    @Override
    public List<RoomDeployHudIcon> resolveAdditionalDeployIcons(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId selfTeam
    ) {
        if (room == null || selfTeam == null) {
            return List.of();
        }
        List<RoomDeployHudIcon> icons = new java.util.ArrayList<>();
        int currentStageIndex = runtimeState.getCurrentStageIndex();
        if (selfTeam == RoomTeamId.CAMP_A) {
            appendStageSpawnIcons(icons, room, currentStageIndex - 1, selfTeam);
        } else if (selfTeam == RoomTeamId.CAMP_B) {
            appendStageSpawnIcons(icons, room, currentStageIndex + 1, selfTeam);
        }
        return icons;
    }

    @Override
    public String teamDisplayName(RoomDefinition room, RoomTeamId teamId) {
        if (room != null) {
            if (teamId == RoomTeamId.CAMP_A) {
                return room.breakthroughConfig().campADisplayName();
            }
            if (teamId == RoomTeamId.CAMP_B) {
                return room.breakthroughConfig().campBDisplayName();
            }
        }
        return RoomTeamPresentationSupport.defaultTeamDisplayName(teamId);
    }

    @Override
    public void syncEndingHudSubtitles(RoomDefinition room, long gameTick) {
        RoomTeamId winner = runningState.getResultWinnerTeam();
        String reason = runningState.getResultReason();
        for (java.util.UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player == null) {
                endingSubtitleByPlayer.remove(playerId);
                continue;
            }
            RoomTeamId selfTeam = runningState.getRoomTeams().get(playerId);
            String desired = resolveEndingSubtitleText(winner, selfTeam, reason);
            String previous = endingSubtitleByPlayer.getOrDefault(playerId, "");
            if (desired.equals(previous)) {
                continue;
            }
            if (desired.isBlank()) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent("bt_ending"));
                endingSubtitleByPlayer.remove(playerId);
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.setPersistent("bt_ending", desired));
            endingSubtitleByPlayer.put(playerId, desired);
        }
    }

    private int resolveRemainSeconds(long gameTick) {
        if (runtimeState.isDesperateModeActive()) {
            long deadline = runtimeState.getDesperateModeDeadlineTick();
            return deadline <= 0L || gameTick >= deadline ? 0 : (int) Math.max(0L, (deadline - gameTick + 19L) / 20L);
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.ADVANCE_COUNTDOWN) {
            long deadline = runtimeState.getAdvanceCountdownEndTick();
            return deadline <= 0L || gameTick >= deadline ? 0 : (int) Math.max(0L, (deadline - gameTick + 19L) / 20L);
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.RETREAT) {
            long deadline = runtimeState.getRetreatDeadlineTick();
            return deadline <= 0L || gameTick >= deadline ? 0 : (int) Math.max(0L, (deadline - gameTick + 19L) / 20L);
        }
        long deadline = runtimeState.getStageDeadlineTick();
        if (deadline <= 0L || gameTick >= deadline) {
            return 0;
        }
        return (int) Math.max(0L, (deadline - gameTick + 19L) / 20L);
    }

    private String resolveTopStatus(boolean inDeployFlow, long gameTick) {
        if (runtimeState.getCurrentStageIndex() <= 0) {
            return "等待阶段初始化";
        }
        if (runtimeState.getDesperateMode() == BreakthroughRuntimeState.DesperateMode.BACK_TO_WALL) {
            return hudStyledText("BT_LAST_STAND_STATUS", "背水一战");
        }
        if (runtimeState.getDesperateMode() == BreakthroughRuntimeState.DesperateMode.BURN_BOATS) {
            return hudStyledText("BT_BURN_BOATS_STATUS", "破釜沉舟");
        }
        String stageLabel = stageLabel(runtimeState.getCurrentStageIndex());
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.RETREAT) {
            return "清理守军中";
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.ADVANCE_COUNTDOWN) {
            return "即将进入阶段" + stageLabel(Math.min(runtimeState.getMaxStageCount(), runtimeState.getCurrentStageIndex() + 1));
        }
        return inDeployFlow ? "部署中，准备进入阶段" + stageLabel : "阶段" + stageLabel + "战斗中";
    }

    private String buildFocusPayload(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return "-";
        }
        RoomObjectivePresentationContext context = buildObjectiveContext(room, player);
        if (context == null) {
            return "-";
        }
        return objectiveFocusPayloadService.buildFocusPayload(context);
    }

    private void startAdvanceCountdown(RoomDefinition room, long gameTick) {
        clearRetreatPersistentSubtitles();
        titleService.clearRetreatTitles(runningState, runtimeState);
        clearRetreatDefenderMarkers(room, gameTick);
        runtimeState.startAdvanceCountdown(gameTick, STAGE_ADVANCE_COUNTDOWN_SECONDS);
        titleService.clearCountdownTitles(runningState, runtimeState);
        broadcastStageTroopDeltaSubtitle(room, gameTick);
    }

    private void refreshDeployViewForRespawnPlayers(RoomDefinition room, long gameTick, int sourceStageIndex, int targetStageIndex) {
        if (room == null) {
            return;
        }
        int normalizedSourceStageIndex = Mth.clamp(sourceStageIndex, 1, runtimeState.getMaxStageCount());
        int normalizedTargetStageIndex = Mth.clamp(targetStageIndex, 1, runtimeState.getMaxStageCount());
        RoomViewPosition previousView = room.breakthroughConfig().stageDeployViewPosition(normalizedSourceStageIndex);
        RoomViewPosition nextView = room.breakthroughConfig().stageDeployViewPosition(normalizedTargetStageIndex);
        if (previousView == null || nextView == null) {
            return;
        }
        for (java.util.UUID playerId : runningState.getRoomPlayers()) {
            if (!runningState.isInDeployFlow(playerId)) {
                continue;
            }
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player == null) {
                continue;
            }
            int remainTicks = Math.max(0, (int) (runningState.getDeployReadyTicks().getOrDefault(playerId, gameTick) - gameTick));
            NetworkManager.sendToPlayer(
                player,
                RoomDeathFlowPacket.openDeployFrom(
                    previousView.x() + 0.5D,
                    previousView.y(),
                    previousView.z() + 0.5D,
                    nextView.x() + 0.5D,
                    nextView.y(),
                    nextView.z() + 0.5D,
                    nextView.yaw(),
                    nextView.pitch(),
                    remainTicks
                )
            );
        }
    }

    private String stageLabel(int stageIndex) {
        if (stageIndex <= 0) {
            return "-";
        }
        return String.valueOf((char) ('A' + stageIndex - 1));
    }

    private int resolveUpcomingDefenderStageIndex() {
        int current = runtimeState.getCurrentStageIndex();
        if (current <= 0) {
            return 0;
        }
        int upcoming = current + 1;
        if (upcoming > runtimeState.getMaxStageCount()) {
            return 0;
        }
        return upcoming;
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
            activeObjectivePoints(room),
            objectiveRuntimeView,
            activeObjectiveCode(room, player),
            selfTeam
        );
    }

    private void initializeObjectiveSubtitles(RoomDefinition room) {
        if (room == null) {
            objectiveSubtitleState.clear();
            return;
        }
        objectiveSubtitleRuntimeService.initialize(
            objectiveSubtitleState,
            activeStageOwnerSnapshot(room),
            activeStageDrivingSnapshot(room)
        );
    }

    private void initializeAttackTroopCapForOpeningStage(RoomDefinition room) {
        runtimeState.initializeAttackTroops(resolveAttackTroopBalance(room).troopCap());
    }

    private void refreshAttackTroopCapForNextStage(RoomDefinition room) {
        runtimeState.setAttackTroopCap(resolveAttackTroopBalance(room).troopCap(), true);
    }

    private void syncCombatNarrativeSubtitles(RoomDefinition room, long gameTick) {
        if (!initialCombatSubtitleDispatched && enteredTick > 0L && gameTick >= enteredTick) {
            broadcastPerspectiveSubtitleSequence(
                "bt_initial_unlock_" + gameTick,
                List.of("攻破所有据点", "突破敌军防线"),
                List.of("防守全部据点", "阻止敌军进攻")
            );
            initialCombatSubtitleDispatched = true;
        }
        if (enteredTick <= 0L || gameTick < enteredTick) {
            return;
        }
        syncAttackTroopThresholdSubtitles(gameTick);
    }

    private void syncAttackTroopThresholdSubtitles(long gameTick) {
        if (runtimeState.getAttackTroopCap() <= 0 || runtimeState.isDesperateModeActive()) {
            return;
        }
        double ratio = runtimeState.getAttackTroops() / (double) runtimeState.getAttackTroopCap();
        if (!attackTroopCriticalSubtitleDispatched && ratio <= 0.2D) {
            broadcastStageTransitionSubtitle(
                "bt_attack_troops_critical_" + gameTick,
                "我军仅剩20%兵力",
                "敌军仅剩20%兵力"
            );
            attackTroopCriticalSubtitleDispatched = true;
            attackTroopHalfSubtitleDispatched = true;
            return;
        }
        if (!attackTroopHalfSubtitleDispatched && ratio <= 0.5D) {
            broadcastStageTransitionSubtitle(
                "bt_attack_troops_half_" + gameTick,
                "我方还有50%兵力",
                "敌军还有50%兵力"
            );
            attackTroopHalfSubtitleDispatched = true;
        }
    }

    private int applyStageReward(RoomDefinition room) {
        AttackTroopBalanceSnapshot balance = resolveAttackTroopBalance(room);
        return runtimeState.refillAttackTroops(balance.troopCap(), balance.stageReward());
    }

    private AttackTroopBalanceSnapshot resolveAttackTroopBalance(RoomDefinition room) {
        int referencePlayerCount = resolveTroopBalanceReferencePlayerCount(room);
        if (room == null) {
            return new AttackTroopBalanceSnapshot(referencePlayerCount, 0, 0);
        }
        int configuredTroopCap = room.breakthroughConfig().maxAttackTroopsAt12Players();
        int troopCap = resolveCeilScaledValue(configuredTroopCap, referencePlayerCount, ATTACK_TROOP_REFERENCE_PLAYERS);
        int stageReward = resolveCeilScaledValue(configuredTroopCap, referencePlayerCount, ATTACK_TROOP_REFERENCE_PLAYERS * STAGE_REWARD_DIVISOR);
        return new AttackTroopBalanceSnapshot(referencePlayerCount, troopCap, stageReward);
    }

    private int resolveTroopBalanceReferencePlayerCount(RoomDefinition room) {
        if (room == null) {
            return 0;
        }
        RoomTroopBalanceReference reference = room.breakthroughConfig().troopBalanceReference();
        if (reference == RoomTroopBalanceReference.DEFENDER) {
            return countPlayersInTeam(RoomTeamId.CAMP_B);
        }
        return countPlayersInTeam(RoomTeamId.CAMP_A);
    }

    private int countPlayersInTeam(RoomTeamId teamId) {
        if (teamId == null) {
            return 0;
        }
        int count = 0;
        for (java.util.UUID playerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(playerId) == teamId) {
                count++;
            }
        }
        return count;
    }

    private int resolveCeilScaledValue(int baseValue, int playerCount, int divisor) {
        if (baseValue <= 0 || playerCount <= 0 || divisor <= 0) {
            return 0;
        }
        return (int) Math.ceil(baseValue * playerCount / (double) divisor);
    }

    private void tickObjectiveSubtitles(RoomDefinition room) {
        if (room == null) {
            return;
        }
        objectiveSubtitleRuntimeService.tickObjectiveEvents(
            objectiveSubtitleState,
            runningState,
            activeObjectiveSubtitleEntries(room),
            activeStageOwnerSnapshot(room),
            activeStageDrivingSnapshot(room)
        );
    }

    private List<RoomObjectiveSubtitleEntry> activeObjectiveSubtitleEntries(RoomDefinition room) {
        return activeObjectivePoints(room).stream()
            .map(point -> new RoomObjectiveSubtitleEntry(point.code(), point.displayName()))
            .toList();
    }

    private Map<String, RoomTeamId> activeStageOwnerSnapshot(RoomDefinition room) {
        Map<String, RoomTeamId> snapshot = new LinkedHashMap<>();
        if (room == null) {
            return snapshot;
        }
        for (RoomCapturePointDefinition point : activeObjectivePoints(room)) {
            String code = point.code();
            snapshot.put(code, runtimeState.getOwnerByCode().get(code));
        }
        return snapshot;
    }

    private Map<String, RoomTeamId> activeStageDrivingSnapshot(RoomDefinition room) {
        Map<String, RoomTeamId> snapshot = new LinkedHashMap<>();
        if (room == null) {
            return snapshot;
        }
        for (RoomCapturePointDefinition point : activeObjectivePoints(room)) {
            String code = point.code();
            snapshot.put(code, runtimeState.getDrivingTeamByCode().get(code));
        }
        return snapshot;
    }

    private List<List<RoomPosition>> allowedBoundaries(RoomDefinition room, RoomTeamId teamId) {
        java.util.ArrayList<List<RoomPosition>> boundaries = new java.util.ArrayList<>();
        if (teamId == RoomTeamId.CAMP_A) {
            addBoundaryIfValid(boundaries, room.breakthroughConfig().campABaseBoundary());
            for (int stageIndex = 1; stageIndex <= runtimeState.getCurrentStageIndex(); stageIndex++) {
                addBoundaryIfValid(boundaries, room.breakthroughConfig().stageBoundary(stageIndex));
            }
            return boundaries;
        }
        if (teamId == RoomTeamId.CAMP_B) {
            addBoundaryIfValid(boundaries, room.breakthroughConfig().campBBaseBoundary());
            for (int stageIndex = defenderBoundaryStartStage(); stageIndex <= runtimeState.getMaxStageCount(); stageIndex++) {
                addBoundaryIfValid(boundaries, room.breakthroughConfig().stageBoundary(stageIndex));
            }
        }
        return boundaries;
    }

    private String stageBoundaryToken(int stageIndex) {
        int displayFrontlineStage = displayFrontlineStage();
        if (stageIndex == displayFrontlineStage) {
            return "N";
        }
        return stageIndex < displayFrontlineStage ? "T" : "S";
    }

    private void appendBoundaryRows(List<RoomDeployHudElement> elements, List<RoomPosition> positions, String teamToken, String polygonId) {
        if (positions == null || positions.size() < 2) {
            return;
        }
        for (RoomPosition position : positions) {
            elements.add(RoomDeployHudElement.boundary(teamToken, position, polygonId));
        }
    }

    private void addBoundaryIfValid(List<List<RoomPosition>> boundaries, List<RoomPosition> boundary) {
        if (boundary != null && boundary.size() >= 3) {
            boundaries.add(boundary);
        }
    }

    private void syncRetreatDefenderMarkers(RoomDefinition room, long gameTick) {
        if (room == null) {
            return;
        }
        int retreatStageIndex = runtimeState.getRetreatStageIndex();
        if (retreatStageIndex <= 0) {
            clearRetreatDefenderMarkers(room, gameTick);
            return;
        }
        Set<java.util.UUID> currentDefenders = captureRuntimeService.collectDefendersInStageBoundary(room, runningState, retreatStageIndex);
        boolean changed = false;
        for (java.util.UUID defenderId : new LinkedHashSet<>(retreatMarkedDefenders)) {
            if (currentDefenders.contains(defenderId)) {
                continue;
            }
            changed |= markerRuntimeService.removeGameplayMarker(runningState, retreatMarkerKeyForAttackers(defenderId));
            changed |= markerRuntimeService.removeGameplayMarker(runningState, retreatMarkerKeyForDefenders(defenderId));
            retreatMarkedDefenders.remove(defenderId);
        }
        long expireTick = gameTick + RETREAT_MARKER_REFRESH_TICKS;
        for (java.util.UUID defenderId : currentDefenders) {
            changed |= markerRuntimeService.putGameplayTrackedMarker(
                runningState,
                retreatMarkerKeyForAttackers(defenderId),
                RoomTeamId.CAMP_A,
                defenderId,
                RETREAT_MARKER_OWNER_NAME,
                gameTick,
                expireTick
            );
            changed |= markerRuntimeService.putGameplayTrackedMarker(
                runningState,
                retreatMarkerKeyForDefenders(defenderId),
                RoomTeamId.CAMP_B,
                defenderId,
                RETREAT_MARKER_OWNER_NAME,
                gameTick,
                expireTick
            );
            retreatMarkedDefenders.add(defenderId);
        }
        if (changed) {
            markerRuntimeService.syncRunningRoom(room, runningState, gameTick);
        }
    }

    private void clearRetreatDefenderMarkers(RoomDefinition room, long gameTick) {
        boolean changed = markerRuntimeService.clearGameplayMarkersByPrefix(runningState, RETREAT_MARKER_ATTACKER_PREFIX);
        changed |= markerRuntimeService.clearGameplayMarkersByPrefix(runningState, RETREAT_MARKER_DEFENDER_PREFIX);
        retreatMarkedDefenders.clear();
        if (changed && room != null) {
            markerRuntimeService.syncRunningRoom(room, runningState, gameTick);
        }
    }

    private String retreatMarkerKeyForAttackers(java.util.UUID defenderId) {
        return RETREAT_MARKER_ATTACKER_PREFIX + defenderId;
    }

    private String retreatMarkerKeyForDefenders(java.util.UUID defenderId) {
        return RETREAT_MARKER_DEFENDER_PREFIX + defenderId;
    }

    private void syncRetreatPersistentSubtitles(RoomDefinition room) {
        if (room == null || runtimeState.getPhase() != BreakthroughRuntimeState.Phase.RETREAT) {
            clearRetreatPersistentSubtitles();
            return;
        }
        int defenders = captureRuntimeService.countDefendersInStageBoundary(room, runningState, runtimeState.getRetreatStageIndex());
        for (java.util.UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player == null) {
                retreatSubtitleByPlayer.remove(playerId);
                continue;
            }
            RoomTeamId selfTeam = runningState.getRoomTeams().get(playerId);
            String desired = resolveRetreatPersistentSubtitle(selfTeam, defenders);
            String previous = retreatSubtitleByPlayer.getOrDefault(playerId, "");
            if (desired.isBlank()) {
                if (!previous.isBlank()) {
                    NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent("bt_retreat"));
                    retreatSubtitleByPlayer.remove(playerId);
                }
                continue;
            }
            if (desired.equals(previous)) {
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.setPersistent("bt_retreat", desired));
            retreatSubtitleByPlayer.put(playerId, desired);
        }
    }

    private void clearRetreatPersistentSubtitles() {
        for (java.util.UUID playerId : new LinkedHashSet<>(retreatSubtitleByPlayer.keySet())) {
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player != null) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent("bt_retreat"));
            }
        }
        retreatSubtitleByPlayer.clear();
    }

    private void syncFinalSegmentPersistentSubtitles() {
        boolean active = runtimeState.getPhase() == BreakthroughRuntimeState.Phase.COMBAT
            && !runtimeState.isDesperateModeActive()
            && runtimeState.getCurrentStageIndex() == runtimeState.getMaxStageCount()
            && runtimeState.isLastStageFinalFightPressure();
        for (java.util.UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player == null) {
                finalSegmentSubtitleByPlayer.remove(playerId);
                continue;
            }
            RoomTeamId selfTeam = runningState.getRoomTeams().get(playerId);
            String desired = active ? resolveFinalSegmentSubtitle(selfTeam) : "";
            String previous = finalSegmentSubtitleByPlayer.getOrDefault(playerId, "");
            if (desired.equals(previous)) {
                continue;
            }
            if (desired.isBlank()) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent("bt_final_segment"));
                finalSegmentSubtitleByPlayer.remove(playerId);
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.setPersistent("bt_final_segment", desired));
            finalSegmentSubtitleByPlayer.put(playerId, desired);
        }
    }

    private void clearFinalSegmentPersistentSubtitles() {
        for (java.util.UUID playerId : new LinkedHashSet<>(finalSegmentSubtitleByPlayer.keySet())) {
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player != null) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent("bt_final_segment"));
            }
        }
        finalSegmentSubtitleByPlayer.clear();
    }

    private String buildEventText(RoomDefinition room, ServerPlayer player, RoomTeamId selfTeam, long gameTick) {
        if (runtimeState.getCurrentStageIndex() <= 0) {
            return "-";
        }
        if (runtimeState.isDesperateModeActive()) {
            String teamEvent = runtimeState.resolveTeamEvent(selfTeam, gameTick);
            if (!"-".equals(teamEvent)) {
                return teamEvent;
            }
            return resolveDesperateEventText();
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.RETREAT) {
            int defenders = captureRuntimeService.countDefendersInStageBoundary(room, runningState, runtimeState.getRetreatStageIndex());
            return resolveRetreatPersistentSubtitle(selfTeam, defenders);
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.ADVANCE_COUNTDOWN) {
            return selfTeam == RoomTeamId.CAMP_A ? "组织下一波攻势" : "构建新的防线";
        }
        if (selfTeam != null && runtimeState.getCurrentStageIndex() == runtimeState.getMaxStageCount()
            && runtimeState.isLastStageFinalFightPressure()) {
            return selfTeam == RoomTeamId.CAMP_A ? "敌军防线即将瓦解" : "坚守我方最后的据点";
        }
        String teamEvent = runtimeState.resolveTeamEvent(selfTeam, gameTick);
        if (!"-".equals(teamEvent)) {
            return teamEvent;
        }
        String objectiveEvent = objectiveEventTextService.buildFocusEventText(buildObjectiveContext(room, player));
        if (!"-".equals(objectiveEvent)) {
            return objectiveEvent;
        }
        int activeObjectiveCount = room == null ? 0 : room.breakthroughConfig().stageActivePoints(runtimeState.getCurrentStageIndex()).size();
        return "当前阶段据点数 " + activeObjectiveCount;
    }

    private int displayFrontlineStage() {
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.ADVANCE_COUNTDOWN) {
            return Math.min(runtimeState.getMaxStageCount(), runtimeState.getCurrentStageIndex() + 1);
        }
        return runtimeState.getCurrentStageIndex();
    }

    private Integer normalizeVehicleStageIndex(VehicleRuntimeRecord record) {
        if (record == null || record.getOriginStageIndex() == null) {
            return null;
        }
        return Math.max(1, record.getOriginStageIndex());
    }

    private int defenderBoundaryStartStage() {
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.ADVANCE_COUNTDOWN) {
            return Math.min(runtimeState.getMaxStageCount(), runtimeState.getCurrentStageIndex() + 1);
        }
        return runtimeState.getCurrentStageIndex();
    }

    private void broadcastStageTroopDeltaSubtitle(RoomDefinition room, long gameTick) {
        int delta = previewStageTroopDelta(room);
        String attackerText = delta >= 0
            ? "我方得到 " + delta + " 名士兵"
            : "我方失去 " + Math.abs(delta) + " 名士兵";
        String defenderText = delta >= 0
            ? "敌军得到 " + delta + " 名士兵"
            : "敌军失去 " + Math.abs(delta) + " 名士兵";
        broadcastStageTransitionSubtitle("bt_stage_troop_delta_" + gameTick, attackerText, defenderText);
    }

    private int previewStageTroopDelta(RoomDefinition room) {
        AttackTroopBalanceSnapshot balance = resolveAttackTroopBalance(room);
        int currentTroops = runtimeState.getAttackTroops();
        int targetTroops = Math.min(balance.troopCap(), currentTroops + balance.stageReward());
        return targetTroops - currentTroops;
    }

    private void broadcastStageStartSubtitles(long gameTick) {
        java.util.ArrayList<String> attackerTexts = new java.util.ArrayList<>(List.of("准备进攻", "组织下一波攻势"));
        java.util.ArrayList<String> defenderTexts = new java.util.ArrayList<>(List.of("准备防守", "构建新的防线"));
        if (runtimeState.getCurrentStageIndex() == runtimeState.getMaxStageCount()) {
            attackerTexts.add("这是敌军最后的区域");
            attackerTexts.add("击溃他们拿下胜利");
            defenderTexts.add("这是我军最后的防线");
            defenderTexts.add("必须守住");
        }
        broadcastPerspectiveSubtitleSequence(
            "bt_stage_begin_" + runtimeState.getCurrentStageIndex() + "_" + gameTick,
            attackerTexts,
            defenderTexts
        );
    }

    private void playStageAdvanceVoiceForAttackers() {
        for (java.util.UUID playerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(playerId) != RoomTeamId.CAMP_A) {
                continue;
            }
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player != null) {
                FrontlineVoiceBridge.playCue(player, RoomThirdPersonAudioCueRegistry.PLAYER_GOGOGO);
            }
        }
    }

    private void broadcastStageTransitionSubtitle(String key, String attackerText, String defenderText) {
        if (key == null || key.isBlank()) {
            return;
        }
        subtitleBroadcastService.broadcastPerspectiveOnce(runningState, key, RoomTeamId.CAMP_A, attackerText, defenderText);
    }

    private void broadcastOutcomeSubtitle(String key, String attackerText, String defenderText) {
        broadcastStageTransitionSubtitle(key, attackerText, defenderText);
    }

    private void broadcastPerspectiveSubtitleSequence(String keyPrefix, List<String> attackerTexts, List<String> defenderTexts) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return;
        }
        int count = Math.max(attackerTexts == null ? 0 : attackerTexts.size(), defenderTexts == null ? 0 : defenderTexts.size());
        for (int index = 0; index < count; index++) {
            String attackerText = attackerTexts != null && index < attackerTexts.size() ? attackerTexts.get(index) : "";
            String defenderText = defenderTexts != null && index < defenderTexts.size() ? defenderTexts.get(index) : "";
            if ((attackerText == null || attackerText.isBlank()) && (defenderText == null || defenderText.isBlank())) {
                continue;
            }
            subtitleBroadcastService.broadcastPerspectiveOnce(
                runningState,
                keyPrefix + "_" + index,
                RoomTeamId.CAMP_A,
                attackerText,
                defenderText
            );
        }
    }

    private String buildAudioState(RoomTeamId selfTeam) {
        if (runtimeState.isDesperateModeActive()) {
            return "BT_DESPERATE";
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.RETREAT) {
            return "BT_RETREAT";
        }
        if (runtimeState.getPhase() == BreakthroughRuntimeState.Phase.ADVANCE_COUNTDOWN) {
            return "BT_STAGE_ADVANCE";
        }
        if (runtimeState.getCurrentStageIndex() == runtimeState.getMaxStageCount()
            && runtimeState.isLastStageFinalFightPressure()) {
            return "BT_LAST_SEGMENT";
        }
        if (runtimeState.shouldPlayLastObjectiveLoop()) {
            return resolveFinalObjectiveAudioState(selfTeam);
        }
        if (runtimeState.getAttackTroopCap() > 0) {
            double ratio = runtimeState.getAttackTroops() / (double) runtimeState.getAttackTroopCap();
            if (ratio <= 0.2D) {
                return resolveFinalObjectiveAudioState(selfTeam);
            }
        }
        return "RUNNING";
    }

    private String resolveFinalObjectiveAudioState(RoomTeamId selfTeam) {
        if (selfTeam == RoomTeamId.CAMP_B) {
            return "FINAL_OBJECTIVE_VICTORY";
        }
        if (selfTeam == RoomTeamId.CAMP_A) {
            return "FINAL_OBJECTIVE_DEFEAT";
        }
        return "FINAL_OBJECTIVE";
    }

    private RoomPosition resolveBaseDeployIconPosition(RoomDefinition room, RoomTeamId teamId) {
        return room == null || teamId == null ? null : room.breakthroughConfig().baseDeployIconPosition(teamId);
    }

    private void appendStageSpawnIcons(
        List<RoomDeployHudIcon> icons,
        RoomDefinition room,
        int stageIndex,
        RoomTeamId ownerTeam
    ) {
        if (icons == null || room == null || ownerTeam == null) {
            return;
        }
        if (stageIndex <= 0 || stageIndex > runtimeState.getMaxStageCount()) {
            return;
        }
        for (RoomCapturePointDefinition point : room.breakthroughConfig().stageActivePoints(stageIndex)) {
            if (point == null || point.centerPosition() == null) {
                continue;
            }
            icons.add(new RoomDeployHudIcon(
                "CAPTURE:" + point.code().toUpperCase(java.util.Locale.ROOT),
                point.centerPosition(),
                ownerTeam,
                point.displayName(),
                true
            ));
        }
    }

    private String resolveEndingSubtitleText(RoomTeamId winner, RoomTeamId selfTeam, String reason) {
        return "对局结束";
    }

    private String resolveRetreatPersistentSubtitle(RoomTeamId selfTeam, int defenders) {
        if (selfTeam == RoomTeamId.CAMP_B) {
            return "我方还有 " + defenders + " 名未撤退的士兵";
        }
        if (selfTeam == RoomTeamId.CAMP_A) {
            return "清理残余的 " + defenders + " 名敌方士兵";
        }
        return "";
    }

    private String resolveFinalSegmentSubtitle(RoomTeamId selfTeam) {
        if (selfTeam == RoomTeamId.CAMP_A) {
            return "敌军防线即将瓦解";
        }
        if (selfTeam == RoomTeamId.CAMP_B) {
            return "坚守我方最后的据点";
        }
        return "";
    }

    private boolean tickDesperateModeState(RoomDefinition room, long gameTick) {
        if (!runtimeState.isDesperateModeActive()) {
            return false;
        }
        if (runtimeState.getDesperateMode() == BreakthroughRuntimeState.DesperateMode.BURN_BOATS
            && areAllActiveObjectivesFullyControlledByDefenders(room)) {
            failDesperateMode(gameTick, "破釜沉舟失败，进攻方兵力耗尽", "破釜沉舟失败", "敌军破釜沉舟失败");
            return true;
        }
        long deadline = runtimeState.getDesperateModeDeadlineTick();
        if (deadline > 0L && gameTick < deadline) {
            return false;
        }
        if (runtimeState.getDesperateMode() == BreakthroughRuntimeState.DesperateMode.BACK_TO_WALL) {
            if (shouldTriggerDesperateMode(room)) {
                activateBurnBoats(gameTick);
            } else {
                failDesperateMode(gameTick, "背水一战结束，进攻方兵力耗尽", "背水一战失败，兵力耗尽", "敌军背水一战失败");
            }
            return true;
        }
        failDesperateMode(gameTick, "破釜沉舟结束，进攻方兵力耗尽", "破釜沉舟失败，兵力耗尽", "敌军破釜沉舟失败");
        return true;
    }

    private boolean tryActivateBackToWall(RoomDefinition room, long gameTick) {
        if (!isBackToWallEnabled(room) || runtimeState.isDesperateModeActive() || runtimeState.getPhase() != BreakthroughRuntimeState.Phase.COMBAT) {
            return false;
        }
        if (!shouldTriggerDesperateMode(room)) {
            return false;
        }
        activateBackToWall(gameTick);
        return true;
    }

    private boolean isBackToWallEnabled(RoomDefinition room) {
        return room != null && room.commonConfig().enableBackAgainstWall();
    }

    private boolean shouldTriggerDesperateMode(RoomDefinition room) {
        if (room == null || runtimeState.getCurrentStageIndex() <= 0) {
            return false;
        }
        List<RoomCapturePointDefinition> activePoints = room.breakthroughConfig().stageActivePoints(runtimeState.getCurrentStageIndex());
        if (activePoints.isEmpty()) {
            return false;
        }
        int neutralDrivenByAttackers = 0;
        for (RoomCapturePointDefinition point : activePoints) {
            String code = point.code();
            RoomTeamId owner = runtimeState.getOwnerByCode().get(code);
            RoomTeamId driving = runtimeState.getDrivingTeamByCode().get(code);
            if (owner == null && driving == RoomTeamId.CAMP_A) {
                neutralDrivenByAttackers++;
                continue;
            }
            if (!runtimeState.isFullyControlledBy(code, RoomTeamId.CAMP_A)) {
                return false;
            }
        }
        return neutralDrivenByAttackers == 1;
    }

    private boolean areAllActiveObjectivesFullyControlledByDefenders(RoomDefinition room) {
        if (room == null || runtimeState.getCurrentStageIndex() <= 0) {
            return false;
        }
        List<RoomCapturePointDefinition> activePoints = room.breakthroughConfig().stageActivePoints(runtimeState.getCurrentStageIndex());
        if (activePoints.isEmpty()) {
            return false;
        }
        for (RoomCapturePointDefinition point : activePoints) {
            if (!runtimeState.isFullyControlledBy(point.code(), RoomTeamId.CAMP_B)) {
                return false;
            }
        }
        return true;
    }

    private void activateBackToWall(long gameTick) {
        runtimeState.startBackToWall(gameTick, BACK_TO_WALL_DURATION_SECONDS);
    }

    private void activateBurnBoats(long gameTick) {
        runtimeState.startBurnBoats(gameTick, BURN_BOATS_DURATION_SECONDS);
    }

    private void failDesperateMode(long gameTick, String reason, String attackerText, String defenderText) {
        runtimeState.clearDesperateMode();
        broadcastOutcomeSubtitle("bt_desperate_fail_" + gameTick, attackerText, defenderText);
        pendingEndRequest = new RoomMatchEndRequest(RoomTeamId.CAMP_B, reason);
    }

    private void syncDesperatePersistentSubtitles() {
        boolean active = runtimeState.isDesperateModeActive();
        for (java.util.UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player == null) {
                desperateSubtitleByPlayer.remove(playerId);
                continue;
            }
            String desired = active ? resolveDesperateEventText() : "";
            String previous = desperateSubtitleByPlayer.getOrDefault(playerId, "");
            if (desired.equals(previous)) {
                continue;
            }
            if (desired.isBlank()) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent("bt_desperate_mode"));
                desperateSubtitleByPlayer.remove(playerId);
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.setPersistent("bt_desperate_mode", desired));
            desperateSubtitleByPlayer.put(playerId, desired);
        }
    }

    private void clearDesperatePersistentSubtitles() {
        for (java.util.UUID playerId : new LinkedHashSet<>(desperateSubtitleByPlayer.keySet())) {
            ServerPlayer player = captureRuntimeService.findOnlinePlayer(playerId);
            if (player != null) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent("bt_desperate_mode"));
            }
        }
        desperateSubtitleByPlayer.clear();
    }



    private String resolveDesperateEventText() {
        if (runtimeState.getDesperateMode() == BreakthroughRuntimeState.DesperateMode.BACK_TO_WALL) {
            return hudStyledText("BT_LAST_STAND_PERSISTENT", "背水一战");
        }
        if (runtimeState.getDesperateMode() == BreakthroughRuntimeState.DesperateMode.BURN_BOATS) {
            return hudStyledText("BT_BURN_BOATS_PERSISTENT", "破釜沉舟");
        }
        return "-";
    }

    private String hudStyledText(String style, String text) {
        if (style == null || style.isBlank() || text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return "<hud:" + style + ">" + text;
    }

    private record AttackTroopBalanceSnapshot(int attackerCount, int troopCap, int stageReward) {
    }
}
