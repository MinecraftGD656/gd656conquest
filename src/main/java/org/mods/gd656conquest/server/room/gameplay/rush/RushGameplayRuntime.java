package org.mods.gd656conquest.server.room.gameplay.rush;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientLoopSoundPacket;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.gameplay.RoomAttackTroopBalanceSupport;
import org.mods.gd656conquest.server.room.gameplay.RoomDeployHudIcon;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryHudProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayCommonUseHandler;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployViewRequest;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveDefinitionProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveSnapshotProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveFocusPayloadService;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectivePresentationContext;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeProvider;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSpawnPolicy;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryPolygonSupport;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudBuildContext;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudElement;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

import org.mods.gd656conquest.network.packet.RoomHudSubtitlePacket;

public final class RushGameplayRuntime implements IRoomGameplayRuntime,
    RoomGameplayPresentationProvider,
    RoomGameplayObjectiveSnapshotProvider,
    RoomGameplayObjectiveDefinitionProvider,
    RoomObjectiveRuntimeProvider,
    RoomGameplayDeployPresentationProvider,
    RoomGameplayBoundaryPolicyProvider,
    RoomGameplayBoundaryHudProvider,
    RoomGameplayCommonUseHandler {
    private static final int STAGE_ADVANCE_COUNTDOWN_SECONDS = 5;
    // GD656Mods: Rush 背水一战时长 2 分钟(短于突破模式的 180 秒)
    private static final int RUSH_BACK_TO_WALL_SECONDS = 120;
    private static final double OBJECTIVE_FOCUS_RADIUS_SQ = 2.5D * 2.5D;
    private static final double BOMB_INTERACT_RADIUS_SQ = 2.5D * 2.5D;
    private static final String ACTIONBAR_SOURCE_BOMB_USE = "rush_bomb_use";
    private static final String SOUND_RUSH_CHARGE_BEEP = "gd656conquest:sfx.rush.charge_beep";
    private static final String SOUND_RUSH_CHARGE_PLANT_INTRO = "gd656conquest:sfx.rush.charge_plant_intro_01";
    private static final String SOUND_RUSH_CHARGE_PLANT_FINISH = "gd656conquest:sfx.rush.charge_plant_finish_01";
    private static final String SOUND_RUSH_CHARGE_DISARM_LEADOUT = "gd656conquest:sfx.rush.charge_disarm_leadout_01";
    private static final ResourceLocation SOUND_RUSH_ALARM_01 = new ResourceLocation("gd656conquest", "sfx.rush.alarm_01");
    private static final ResourceLocation SOUND_RUSH_ALARM_LEADOUT_LOOP = new ResourceLocation("gd656conquest", "sfx.rush.alarm_leadout_loop");
    private static final ResourceLocation SOUND_RUSH_ALARM_LEADOUT_FINAL = new ResourceLocation("gd656conquest", "sfx.rush.alarm_leadout_01");
    private static final ResourceLocation SOUND_RUSH_BRICK_DESTRUCTION = new ResourceLocation("gd656conquest", "sfx.rush.brick_destruction_l");
    private static final String VOICE_RUSH_PLANT_SET_A = "gd656conquest:voice.rush.plant_set_a";
    private static final String VOICE_RUSH_PLANT_SET_B = "gd656conquest:voice.rush.plant_set_b";
    private static final String VOICE_RUSH_ATTACK_DEFUSE_A = "gd656conquest:voice.rush.attack_defuse_a";
    private static final String VOICE_RUSH_ATTACK_DEFUSE_B = "gd656conquest:voice.rush.attack_defuse_b";
    private static final String VOICE_RUSH_DEFEND_DEFUSE_A = "gd656conquest:voice.rush.defend_defuse_a";
    private static final String VOICE_RUSH_DEFEND_DEFUSE_B = "gd656conquest:voice.rush.defend_defuse_b";
    private static final long FINAL_LEADOUT_TICKS = 4L * 20L;
    private static final long LEADOUT_WINDOW_TICKS = 12L * 20L;
    private static final long REGULAR_ALARM_INTERVAL_TICKS = 20L;
    private static final long LEADOUT_LOOP_INTERVAL_TICKS = 4L * 20L;
    private static final int RUSH_BOMB_PLANTED_SCORE_VICTIM_ID = -6501;
    private static final int RUSH_BOMB_DEFUSED_SCORE_VICTIM_ID = -6502;
    private static final int RUSH_OBJECTIVE_DESTROYED_SCORE_VICTIM_ID = -6503;
    private static final String RUSH_CAPTURE_PAYLOAD_PLANTED = "rush_bomb_planted";
    private static final String RUSH_CAPTURE_PAYLOAD_DEFUSED = "rush_bomb_defused";
    private static final String RUSH_CAPTURE_PAYLOAD_DESTROYED = "rush_objective_destroyed";

    private final MinecraftServer server;
    private final RunningRuntimeState runningState;
    private final RushObjectiveRuntimeView objectiveRuntimeView = new RushObjectiveRuntimeView();
    private final RushObjectiveSpawnPolicy objectiveSpawnPolicy = new RushObjectiveSpawnPolicy();
    private final RoomObjectiveFocusPayloadService objectiveFocusPayloadService = new RoomObjectiveFocusPayloadService();
    private final RushSubtitleService subtitleService;

    private final Map<String, BombSiteState> bombStates = new LinkedHashMap<>();
    private final Map<String, Double> objectiveProgressByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> objectiveOwnerByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> objectiveDrivingByCode = new LinkedHashMap<>();
    private final Map<String, Integer> campACountByCode = new LinkedHashMap<>();
    private final Map<String, Integer> campBCountByCode = new LinkedHashMap<>();
    private final Map<UUID, BombUseHold> activeHolds = new LinkedHashMap<>();
    private final Map<UUID, BombHoldAudioState> holdAudioStates = new LinkedHashMap<>();
    private final Map<String, BombArmedAudioState> armedBombAudioStates = new LinkedHashMap<>();
    private final List<PendingBombExplosion> pendingSecondaryExplosions = new ArrayList<>();
    private static final String RETREAT_PERSISTENT_SOURCE = "rush_retreat";
    private static final String DESPERATE_PERSISTENT_SOURCE = "bt_desperate_mode";
    private final Map<UUID, String> retreatSubtitleByPlayer = new LinkedHashMap<>();
    private final Map<UUID, String> desperateSubtitleByPlayer = new LinkedHashMap<>();
    private static final String ACTIONBAR_SOURCE_RETREAT = "rush_retreat";
    private static final int TITLE_FADE_IN_TICKS = 5;
    private static final int TITLE_STAY_TICKS = 20;
    private static final int TITLE_FADE_OUT_TICKS = 10;
    private static final int TITLE_REFRESH_FADE_IN_TICKS = 0;
    private static final int TITLE_REFRESH_STAY_TICKS = 22;
    private static final int TITLE_REFRESH_FADE_OUT_TICKS = 4;
    private static final int COUNTDOWN_COLOR_NORMAL = 0x55FFFF;
    private static final int COUNTDOWN_COLOR_GREEN = 0x55FF55;
    private static final int COUNTDOWN_COLOR_YELLOW = 0xFFFF55;
    private static final int COUNTDOWN_COLOR_ORANGE = 0xFF9900;
    private static final int COUNTDOWN_COLOR_RED = 0xFF5555;
    private static final int SUBTITLE_COLOR_ATTACKER = 0x55FFFF;
    private static final int SUBTITLE_COLOR_DEFENDER = 0xFF5555;
    private final Map<UUID, Integer> retreatCountdownLastShownSecondsByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Integer> advanceCountdownLastShownSecondsByPlayer = new LinkedHashMap<>();

    private long enteredTick = -1L;
    private long stageDeadlineTick = -1L;
    private long retreatDeadlineTick = -1L;
    private long advanceCountdownEndTick = -1L;
    private int currentStageIndex = 1;
    private int configuredStageCount = 1;
    private int retreatStageIndex = 0;
    private int attackTroops;
    private int attackTroopCap;
    private Phase phase = Phase.COMBAT;
    // GD656Mods: Rush 背水一战/破釜沉舟(进攻方兵力耗尽且已有炸弹安放时触发)
    private DesperateMode desperateMode = DesperateMode.NONE;
    private long desperateDeadlineTick = -1L;
    private RoomMatchEndRequest pendingEndRequest;
    /** 地面控制/突破者: 本阶段每玩家击杀数(击杀敌方玩家/载具的击杀者), 阶段推进时结算并清空。 */
    private final java.util.Map<UUID, Integer> stageKills = new java.util.concurrent.ConcurrentHashMap<>();

    public RushGameplayRuntime(MinecraftServer server, RunningRuntimeState runningState) {
        this.server = Objects.requireNonNull(server, "server");
        this.runningState = Objects.requireNonNull(runningState, "runningState");
        this.subtitleService = new RushSubtitleService(runningState);
    }

    @Override
    public void onEnterMainPhase(RoomDefinition room, long gameTick) {
        enteredTick = gameTick + RoomPlayerRespawnService.initialCombatUnlockDelayTicks();
        currentStageIndex = 1;
        configuredStageCount = room == null ? 1 : Math.max(1, room.rushConfig().stageCount());
        retreatStageIndex = 0;
        phase = Phase.COMBAT;
        retreatDeadlineTick = -1L;
        advanceCountdownEndTick = -1L;
        stageDeadlineTick = resolveStageDeadlineTick(room, enteredTick);
        RoomAttackTroopBalanceSupport.AttackTroopBalanceSnapshot troopBalance = resolveAttackTroopBalance(room);
        attackTroopCap = troopBalance.troopCap();
        attackTroops = attackTroopCap;
        pendingEndRequest = null;
        desperateMode = DesperateMode.NONE;
        desperateDeadlineTick = -1L;
        stageKills.clear();
        activeHolds.clear();
        holdAudioStates.clear();
        armedBombAudioStates.clear();
        pendingSecondaryExplosions.clear();
        subtitleService.reset();
        clearRetreatPersistentSubtitles();
        clearDesperatePersistentSubtitles();
        clearRetreatTitles();
        clearCountdownTitles();
        initializeBombStates(room);
        syncObjectivePresence(room);
        syncObjectiveSnapshots(room, enteredTick);
    }

    @Override
    public void tick(RoomDefinition room, long gameTick) {
        if (room == null) {
            return;
        }
        processPendingSecondaryExplosions(room, gameTick);
        syncBombHoldState(room, gameTick);
        syncArmedBombAudio(room, gameTick);
        syncObjectivePresence(room);
        syncObjectiveSnapshots(room, gameTick);
        subtitleService.syncStagePressure(currentStageIndex, currentStageRemainingCodes(room), currentStageArmedBombCount(room), gameTick);
        syncBombActionbars(room, gameTick);
        syncDesperatePersistentSubtitles();
        if (pendingEndRequest != null) {
            return;
        }
        if (phase == Phase.COMBAT) {
            String fusedBombCode = findFusedBombCode(room, gameTick);
            if (fusedBombCode != null) {
                handleBombExploded(room, fusedBombCode, gameTick);
                return;
            }
            // GD656Mods: 背水一战/破釜沉舟状态机
            if (tickDesperateMode(room, gameTick)) {
                return;
            }
            if (stageDeadlineTick > 0L && gameTick >= stageDeadlineTick) {
                subtitleService.onStageTimeout(gameTick);
                // GD656Mods: 阶段时间耗尽 -> 防守方直接获胜(不再平局)
                pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_B, "阶段时间结束，防守方获胜");
            }
            return;
        }
        if (phase == Phase.RETREAT) {
            syncRetreatPersistentSubtitles(room);
            syncRetreatActionbar(room, gameTick);
            if (countDefendersInStageBoundary(room, retreatStageIndex) <= 0 || gameTick >= retreatDeadlineTick) {
                startAdvanceCountdown(room, gameTick);
            }
            return;
        }
        if (phase == Phase.ADVANCE_COUNTDOWN) {
            syncCountdownTitles(room, gameTick);
            if (gameTick >= advanceCountdownEndTick) {
                clearCountdownTitles();
                int previousStageIndex = currentStageIndex;
                int nextStageIndex = Math.min(Math.max(1, room.rushConfig().stageCount()), currentStageIndex + 1);
                refreshAttackTroopCapForNextStage(room, nextStageIndex);
                int rewardTroops = applyStageReward(room, nextStageIndex);
                currentStageIndex = nextStageIndex;
                configuredStageCount = Math.max(1, room.rushConfig().stageCount());
                phase = Phase.COMBAT;
                retreatStageIndex = 0;
                retreatDeadlineTick = -1L;
                advanceCountdownEndTick = -1L;
                stageDeadlineTick = resolveStageDeadlineTick(room, gameTick);
                clearAllHolds(room, gameTick, false);
                syncObjectivePresence(room);
                syncObjectiveSnapshots(room, gameTick);
                refreshDeployViewForRespawnPlayers(room, gameTick, previousStageIndex, currentStageIndex);
            }
        }
    }

    @Override
    public void onPlayerInvalidated(
        RoomDefinition room,
        UUID playerId,
        RoomTeamId teamId,
        RoomPlayerInvalidationReason reason,
        int killerEntityId,
        long gameTick
    ) {
        clearPlayerHold(room, playerId, gameTick);
        // 地面控制/突破者: 阶段内击杀统计(击杀者击杀敌方玩家; 忽略自我击杀/环境击杀)
        if (reason == RoomPlayerInvalidationReason.DEATH && killerEntityId >= 0) {
            ServerPlayer deadPlayer = findOnlinePlayer(playerId);
            if (deadPlayer != null) {
                ServerPlayer killerPlayer = resolveKillerPlayer(deadPlayer, killerEntityId);
                if (killerPlayer != null
                        && !killerPlayer.getUUID().equals(playerId)
                        && isEnemyTeam(teamId, runningState.getRoomTeams().get(killerPlayer.getUUID()))) {
                    stageKills.merge(killerPlayer.getUUID(), 1, Integer::sum);
                }
            }
        }
        if (room == null || pendingEndRequest != null) {
            return;
        }
        if (reason == RoomPlayerInvalidationReason.DEATH && teamId == RoomTeamId.CAMP_A) {
            // GD656Mods: 背水一战/破釜沉舟期间进攻方兵力无限
            if (desperateMode != DesperateMode.NONE) {
                return;
            }
            if (phase != Phase.RETREAT) {
                consumeAttackTroop(1);
            }
            if (attackTroops <= 0) {
                subtitleService.onAttackTroopsChanged(attackTroops, attackTroopCap, gameTick);
                // GD656Mods: 兵力耗尽且已有炸弹安放等待爆炸 -> 触发背水一战(2分钟)
                if (tryActivateBackToWall(room, gameTick)) {
                    return;
                }
                pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_B, "进攻方兵力耗尽");
            } else {
                subtitleService.onAttackTroopsChanged(attackTroops, attackTroopCap, gameTick);
            }
            return;
        }
        if (reason == RoomPlayerInvalidationReason.REDEPLOY || teamId != RoomTeamId.CAMP_B || phase != Phase.RETREAT) {
            return;
        }
        ServerPlayer deadPlayer = findOnlinePlayer(playerId);
        if (deadPlayer == null) {
            return;
        }
        if (!RoomBoundaryPolygonSupport.isPointInsideBoundary(deadPlayer.getX(), deadPlayer.getZ(),
            room.rushConfig().stageBoundary(retreatStageIndex))) {
            return;
        }
        ServerPlayer killerPlayer = resolveKillerPlayer(deadPlayer, killerEntityId);
        if (killerPlayer == null) {
            return;
        }
        if (runningState.getRoomTeams().get(killerPlayer.getUUID()) == RoomTeamId.CAMP_A) {
            addAttackTroops(room.rushConfig().troopGainPerDefenderKill());
        }
    }

    @Override
    public void onLeaveMainPhase(RoomDefinition room, long gameTick) {
        clearAllBombActionbars();
        activeHolds.clear();
        holdAudioStates.clear();
        armedBombAudioStates.clear();
        pendingSecondaryExplosions.clear();
        bombStates.clear();
        desperateMode = DesperateMode.NONE;
        desperateDeadlineTick = -1L;
        objectiveProgressByCode.clear();
        objectiveOwnerByCode.clear();
        objectiveDrivingByCode.clear();
        campACountByCode.clear();
        campBCountByCode.clear();
        enteredTick = -1L;
        stageDeadlineTick = -1L;
        retreatDeadlineTick = -1L;
        advanceCountdownEndTick = -1L;
        currentStageIndex = 1;
        configuredStageCount = 1;
        retreatStageIndex = 0;
        attackTroops = 0;
        attackTroopCap = 0;
        phase = Phase.COMBAT;
        pendingEndRequest = null;
        subtitleService.reset();
        clearRetreatPersistentSubtitles();
        clearDesperatePersistentSubtitles();
        clearRetreatTitles();
        clearCountdownTitles();
    }

    @Override
    public Optional<RoomMatchEndRequest> pollMatchEndRequest() {
        RoomMatchEndRequest request = pendingEndRequest;
        pendingEndRequest = null;
        return Optional.ofNullable(request);
    }

    @Override
    public int boundaryPenaltySeconds(RoomDefinition room) {
        return room == null ? 0 : room.gameplayBoundaryPenaltySeconds();
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
            : initialLockActive ? "INITIAL_STAGE_BEGIN" : buildAudioState(room, selfTeam, gameTick);
        return new RoomGameplayHudState(
            // GD656Mods: 背水一战/破釜沉舟期间进攻方兵力无限(客户端显示 ∞)
            desperateMode != DesperateMode.NONE ? -1 : attackTroops,
            currentStageIndex,
            elapsedSeconds,
            "RUSH",
            attackTroopCap,
            room == null ? 1 : Math.max(1, room.rushConfig().stageCount()),
            resolveRemainSeconds(gameTick),
            room != null && room.lifecycleState() == RoomLifecycleState.ENDING ? "结算中" : resolveTopStatus(room, inDeployFlow, gameTick),
            buildEventText(room, player, selfTeam),
            buildFocusPayload(room, player, selfTeam),
            audioState,
            resolveBombActionPayload(room, player, selfTeam)
        );
    }

    @Override
    public boolean isPerformingPriorityAction(UUID playerId) {
        return playerId != null && activeHolds.containsKey(playerId);
    }

    /**
     * 本玩家当前是否处于可拆弹/可装弹位置(Rush 炸弹交互半径内且阵营/炸弹状态匹配)。
     * 编码进 HUD extraPayload 供客户端判定"拆弹/装弹优先于救援"。
     */
    private String resolveBombActionPayload(RoomDefinition room, ServerPlayer player, RoomTeamId selfTeam) {
        if (phase != Phase.COMBAT || room == null || player == null || selfTeam == null) {
            return "-";
        }
        RoomCapturePointDefinition point = nearestStageObjectivePoint(room, player, BOMB_INTERACT_RADIUS_SQ, true);
        if (point == null || resolveBombAction(room, player, selfTeam, point) == null) {
            return "-";
        }
        return "RUSH_BOMB_ACTION|1";
    }

    /** 顶部状态样式文本(客户端 ClientHudStyledText 解析, 样式复用突破模式) */
    private String hudStyledText(String style, String text) {
        if (style == null || style.isBlank() || text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return "<hud:" + style + ">" + text;
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
        return new LinkedHashMap<>(objectiveProgressByCode);
    }

    @Override
    public Map<String, RoomTeamId> objectiveOwnerSnapshot() {
        return new LinkedHashMap<>(objectiveOwnerByCode);
    }

    @Override
    public Map<String, RoomTeamId> objectiveDrivingSnapshot() {
        return new LinkedHashMap<>(objectiveDrivingByCode);
    }

    @Override
    public String activeObjectiveCode(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return null;
        }
        String bestCode = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (RoomCapturePointDefinition point : activeObjectivePoints(room)) {
            if (point == null || point.centerPosition() == null) {
                continue;
            }
            double distanceSq = distanceSq(player, point.centerPosition());
            if (distanceSq > OBJECTIVE_FOCUS_RADIUS_SQ || distanceSq >= bestDistanceSq) {
                continue;
            }
            bestCode = normalizeCode(point.code());
            bestDistanceSq = distanceSq;
        }
        return bestCode;
    }

    @Override
    public List<RoomCapturePointDefinition> activeObjectivePoints(RoomDefinition room) {
        return currentStageObjectivePoints(room, false);
    }

    @Override
    public RoomCapturePointDefinition findObjectivePoint(RoomDefinition room, String objectiveCode) {
        if (room == null || objectiveCode == null || objectiveCode.isBlank()) {
            return null;
        }
        String normalized = normalizeCode(objectiveCode);
        for (RoomCapturePointDefinition point : room.rushConfig().definedObjectivePoints()) {
            if (point != null && normalizeCode(point.code()).equals(normalized)) {
                return point;
            }
        }
        return null;
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
    public boolean hasConfiguredBoundary(RoomDefinition room, RunningRuntimeState state) {
        if (room == null) {
            return false;
        }
        ArrayList<List<RoomPosition>> configured = new ArrayList<>();
        addBoundaryIfValid(configured, room.rushConfig().campABaseBoundary());
        addBoundaryIfValid(configured, room.rushConfig().campBBaseBoundary());
        for (int stageIndex = 1; stageIndex <= room.rushConfig().stageCount(); stageIndex++) {
            addBoundaryIfValid(configured, room.rushConfig().stageBoundary(stageIndex));
        }
        return RoomBoundaryRuleEvaluator.hasConfiguredBoundary(configured);
    }

    @Override
    public List<List<RoomPosition>> resolveAllowedBoundaries(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        return room == null || teamId == null ? List.of() : allowedBoundaries(room, teamId);
    }

    @Override
    public void collectBoundaryElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        if (context == null || context.room() == null) {
            return;
        }
        appendBoundaryRows(elements, context.room().rushConfig().campABaseBoundary(), "T", "rs_camp_a_base");
        appendBoundaryRows(elements, context.room().rushConfig().campBBaseBoundary(), "S", "rs_camp_b_base");
        for (int stageIndex = 1; stageIndex <= context.room().rushConfig().stageCount(); stageIndex++) {
            appendBoundaryRows(elements, context.room().rushConfig().stageBoundary(stageIndex), stageBoundaryToken(stageIndex), "rs_stage_" + stageIndex);
        }
    }

    @Override
    public RoomPosition resolveRespawnAnchorPosition(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (room == null || state == null || player == null) {
            return null;
        }
        RoomTeamId teamId = state.getRoomTeams().get(player.getUUID());
        RoomPosition spawn = new RoomSpawnCandidateSelectionService(player.server).pickSpawn(
            teamId == RoomTeamId.CAMP_A ? room.rushConfig().campABaseSpawnGroup() : room.rushConfig().campBBaseSpawnGroup(),
            state,
            teamId
        );
        return spawn != null ? spawn : room.commonConfig().queueSpawn();
    }

    @Override
    public RoomViewPosition resolveDeployViewPosition(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, RoomGameplayDeployViewRequest request) {
        if (room == null) {
            return null;
        }
        return room.rushConfig().stageDeployViewPosition(currentStageIndex);
    }

    @Override
    public RoomPosition resolveTeamDeployIconPosition(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        if (room == null || teamId == null) {
            return null;
        }
        return room.rushConfig().baseDeployIconPosition(teamId);
    }

    @Override
    public List<RoomDeployHudIcon> resolveAdditionalDeployIcons(RoomDefinition room, RunningRuntimeState state, RoomTeamId selfTeam) {
        if (room == null) {
            return List.of();
        }
        ArrayList<RoomDeployHudIcon> icons = new ArrayList<>();
        if (selfTeam == RoomTeamId.CAMP_A) {
            appendPreviousStageSpawnIcons(icons, room, currentStageIndex - 1, selfTeam);
        } else if (selfTeam == RoomTeamId.CAMP_B) {
            int nextStage = currentStageIndex + 1;
            if (nextStage <= room.rushConfig().stageCount()) {
                appendStageObjectiveIcons(icons, room, nextStage, selfTeam);
            }
        }
        return icons;
    }

    @Override
    public String teamDisplayName(RoomDefinition room, RoomTeamId teamId) {
        if (room != null) {
            if (teamId == RoomTeamId.CAMP_A) {
                return room.rushConfig().campADisplayName();
            }
            if (teamId == RoomTeamId.CAMP_B) {
                return room.rushConfig().campBDisplayName();
            }
        }
        return RoomTeamPresentationSupport.defaultTeamDisplayName(teamId);
    }

    @Override
    public boolean handleCommonUseStart(RoomDefinition room, ServerPlayer player, long gameTick) {
        if (room == null || player == null || pendingEndRequest != null || runningState.isInDeployFlow(player.getUUID())) {
            return false;
        }
        RoomTeamId teamId = runningState.getRoomTeams().get(player.getUUID());
        RoomCapturePointDefinition point = nearestStageObjectivePoint(room, player, BOMB_INTERACT_RADIUS_SQ, true);
        BombHoldAction action = resolveBombAction(room, player, teamId, point);
        if (action == null) {
            return false;
        }
        if (point == null) {
            return false;
        }
        BombUseHold hold = new BombUseHold(normalizeCode(point.code()), teamId, action, gameTick);
        activeHolds.put(player.getUUID(), hold);
        if (action == BombHoldAction.PLANT && countActiveHolds(hold.objectiveCode(), BombHoldAction.PLANT) == 1) {
            subtitleService.onBombPlantStarted(hold.objectiveCode(), gameTick);
        }
        if (action == BombHoldAction.DEFUSE && countActiveHolds(hold.objectiveCode(), BombHoldAction.DEFUSE) == 1) {
            subtitleService.onBombDefuseStarted(hold.objectiveCode(), gameTick);
        }
        beginHoldAudio(room, player, hold, point, gameTick);
        return true;
    }

    @Override
    public void handleCommonUseStop(RoomDefinition room, ServerPlayer player, long gameTick) {
        clearPlayerHold(room, player == null ? null : player.getUUID(), gameTick);
    }

    private void initializeBombStates(RoomDefinition room) {
        bombStates.clear();
        armedBombAudioStates.clear();
        objectiveProgressByCode.clear();
        objectiveOwnerByCode.clear();
        objectiveDrivingByCode.clear();
        campACountByCode.clear();
        campBCountByCode.clear();
        if (room == null) {
            return;
        }
        for (RoomCapturePointDefinition point : room.rushConfig().definedObjectivePoints()) {
            if (point == null) {
                continue;
            }
            String code = normalizeCode(point.code());
            bombStates.put(code, new BombSiteState(point.stageIndex(), BombPhase.SAFE, -1L, null));
            objectiveProgressByCode.put(code, point.stageIndex() < currentStageIndex ? 100.0D : 0.0D);
            objectiveOwnerByCode.put(code, point.stageIndex() < currentStageIndex ? RoomTeamId.CAMP_A : RoomTeamId.CAMP_B);
            objectiveDrivingByCode.put(code, null);
            campACountByCode.put(code, 0);
            campBCountByCode.put(code, 0);
        }
    }

    private void syncBombHoldState(RoomDefinition room, long gameTick) {
        if (room == null || activeHolds.isEmpty()) {
            return;
        }
        if (currentStageObjectivePoints(room, true).isEmpty()) {
            clearAllHolds(room, gameTick, false);
            return;
        }
        UUID completedPlayerId = null;
        BombUseHold completedHold = null;
        for (Map.Entry<UUID, BombUseHold> entry : List.copyOf(activeHolds.entrySet())) {
            UUID playerId = entry.getKey();
            BombUseHold hold = entry.getValue();
            ServerPlayer player = findOnlinePlayer(playerId);
            RoomCapturePointDefinition holdPoint = findObjectivePoint(room, hold.objectiveCode());
            if (!isValidHold(room, holdPoint, player, hold)) {
                clearPlayerHold(room, playerId, gameTick);
                continue;
            }
            long requiredTicks = hold.action() == BombHoldAction.PLANT
                ? room.rushConfig().bombPlantTimeSeconds() * 20L
                : room.rushConfig().bombDefuseTimeSeconds() * 20L;
            syncHoldAudio(room, player, hold, requiredTicks, gameTick);
            if (requiredTicks <= 0L || gameTick - hold.startTick() >= requiredTicks) {
                completedPlayerId = playerId;
                completedHold = hold;
                break;
            }
        }
        if (completedHold == null) {
            return;
        }
        if (completedHold.action() == BombHoldAction.PLANT) {
            handleBombPlanted(completedHold.objectiveCode(), completedPlayerId, gameTick);
        } else {
            handleBombDefused(completedHold.objectiveCode(), completedPlayerId, gameTick);
        }
        clearPlayerHold(room, completedPlayerId, gameTick, false);
        clearAllHolds(room, gameTick, false);
        showBombCompletionActionbar(
            completedPlayerId,
            completedHold.action() == BombHoldAction.PLANT ? "炸弹已经安放" : "炸弹已经被拆除"
        );
    }

    private boolean isValidHold(RoomDefinition room, RoomCapturePointDefinition holdPoint, ServerPlayer player, BombUseHold hold) {
        if (player == null || hold == null || holdPoint == null || holdPoint.stageIndex() != currentStageIndex) {
            return false;
        }
        if (!normalizeCode(holdPoint.code()).equals(hold.objectiveCode())) {
            return false;
        }
        if (runningState.isInDeployFlow(player.getUUID())) {
            return false;
        }
        RoomTeamId actualTeam = runningState.getRoomTeams().get(player.getUUID());
        return actualTeam == hold.teamId() && resolveBombAction(room, player, actualTeam, holdPoint) == hold.action();
    }

    private void handleBombPlanted(String objectiveCode, UUID planterId, long gameTick) {
        String code = normalizeCode(objectiveCode);
        if (code.isBlank()) {
            return;
        }
        bombStates.put(code, new BombSiteState(currentStageIndex, BombPhase.ARMED, gameTick, planterId));
        armedBombAudioStates.put(code, new BombArmedAudioState(gameTick, gameTick, gameTick, false, 0));
        playRushVoiceToTeam(RoomTeamId.CAMP_A, resolveRushPlantVoice(code));
        notifyRushKilliconEvent(planterId, "RUSH_BOMB_PLANTED", RUSH_BOMB_PLANTED_SCORE_VICTIM_ID, false, RUSH_CAPTURE_PAYLOAD_PLANTED, code);
        subtitleService.onBombPlanted(code, gameTick);
    }

    private void handleBombDefused(String objectiveCode, UUID defuserId, long gameTick) {
        String code = normalizeCode(objectiveCode);
        if (code.isBlank()) {
            return;
        }
        // GD656Mods: 破釜沉舟阶段任一炸弹被拆除 -> 直接判定进攻失败
        if (desperateMode == DesperateMode.BURN_BOATS) {
            failDesperateMode(gameTick, "破釜沉舟失败，炸弹被拆除");
            return;
        }
        armedBombAudioStates.remove(code);
        bombStates.put(code, new BombSiteState(currentStageIndex, BombPhase.SAFE, -1L, null));
        playRushVoiceToTeam(RoomTeamId.CAMP_A, resolveRushAttackDefuseVoice(code));
        playRushVoiceToTeam(RoomTeamId.CAMP_B, resolveRushDefendDefuseVoice(code));
        notifyRushKilliconEvent(defuserId, "RUSH_BOMB_DEFUSED", RUSH_BOMB_DEFUSED_SCORE_VICTIM_ID, false, RUSH_CAPTURE_PAYLOAD_DEFUSED, code);
        subtitleService.onBombDefused(code, gameTick);
    }

    private void handleBombExploded(RoomDefinition room, String objectiveCode, long gameTick) {
        String code = normalizeCode(objectiveCode);
        if (code.isBlank()) {
            return;
        }
        // GD656Mods: 破釜沉舟阶段炸弹爆炸(最长引信到点) -> 进攻胜利
        if (desperateMode == DesperateMode.BURN_BOATS) {
            desperateMode = DesperateMode.NONE;
            desperateDeadlineTick = -1L;
            subtitleService.onBurnBoatsSucceed(gameTick);
            pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_A, "破釜沉舟成功");
            return;
        }
        BombSiteState previousState = bombStates.get(code);
        armedBombAudioStates.remove(code);
        triggerBombExplosion(room, code);
        pendingSecondaryExplosions.add(new PendingBombExplosion(code, gameTick + 5L));
        bombStates.put(code, new BombSiteState(currentStageIndex, BombPhase.EXPLODED, -1L, previousState == null ? null : previousState.planterId()));
        notifyRushKilliconEvent(
            previousState == null ? null : previousState.planterId(),
            "RUSH_OBJECTIVE_DESTROYED",
            RUSH_OBJECTIVE_DESTROYED_SCORE_VICTIM_ID,
            true,
            RUSH_CAPTURE_PAYLOAD_DESTROYED,
            code
        );
        subtitleService.onBombExploded(code, gameTick);
        clearAllHolds(room, gameTick, false);
        syncObjectivePresence(room);
        syncObjectiveSnapshots(room, gameTick);
        if (!areAllCurrentStageBombsExploded(room)) {
            return;
        }
        // 地面控制/突破者: 上一阶段结束(所有通讯设备被炸毁)立即结算, 结算后清空本阶段统计
        triggerStageEndSettlement(room);
        subtitleService.onStageDestroyed(currentStageIndex, gameTick);
        if (currentStageIndex >= Math.max(1, room.rushConfig().stageCount())) {
            pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_A, "进攻方爆破全部阶段");
            subtitleService.onAttackCompletedAllStages(gameTick);
            return;
        }
        int defenders = countDefendersInStageBoundary(room, currentStageIndex);
        if (room.rushConfig().defenderRetreatSeconds() <= 0 || defenders <= 0) {
            startAdvanceCountdown(room, gameTick);
            return;
        }
        phase = Phase.RETREAT;
        retreatStageIndex = currentStageIndex;
        retreatDeadlineTick = gameTick + room.rushConfig().defenderRetreatSeconds() * 20L;
        subtitleService.onRetreat(currentStageIndex, code, gameTick);
    }

    private void processPendingSecondaryExplosions(RoomDefinition room, long gameTick) {
        if (room == null || pendingSecondaryExplosions.isEmpty()) {
            return;
        }
        for (int index = pendingSecondaryExplosions.size() - 1; index >= 0; index--) {
            PendingBombExplosion pending = pendingSecondaryExplosions.get(index);
            if (pending == null || gameTick < pending.triggerTick()) {
                continue;
            }
            triggerBombExplosion(room, pending.objectiveCode());
            pendingSecondaryExplosions.remove(index);
        }
    }

    private void syncArmedBombAudio(RoomDefinition room, long gameTick) {
        if (room == null) {
            armedBombAudioStates.clear();
            return;
        }
        long fuseTicks = Math.max(1L, room.rushConfig().bombFuseTimeSeconds() * 20L);
        for (RoomCapturePointDefinition point : room.rushConfig().definedObjectivePoints()) {
            if (point == null) {
                continue;
            }
            String code = normalizeCode(point.code());
            BombSiteState state = bombStates.get(code);
            if (state == null || state.phase() != BombPhase.ARMED || state.armedAtTick() < 0L) {
                armedBombAudioStates.remove(code);
                continue;
            }
            long leadoutStartTick = state.armedAtTick() + Math.max(0L, fuseTicks - LEADOUT_WINDOW_TICKS);
            BombArmedAudioState audioState = armedBombAudioStates.computeIfAbsent(
                code,
                ignored -> new BombArmedAudioState(state.armedAtTick(), state.armedAtTick(), leadoutStartTick, false, 0)
            );
            audioState.nextRegularAlarmTick = Math.max(audioState.nextRegularAlarmTick, state.armedAtTick());
            audioState.nextLeadoutLoopTick = Math.max(audioState.nextLeadoutLoopTick, leadoutStartTick);
            long remainTicks = Math.max(0L, fuseTicks - (gameTick - state.armedAtTick()));
            if (remainTicks > LEADOUT_WINDOW_TICKS) {
                if (gameTick >= audioState.nextRegularAlarmTick) {
                    playBombAlarmSound(room, code, SOUND_RUSH_ALARM_01, 1.0F, 1.0F);
                    audioState.nextRegularAlarmTick = gameTick + REGULAR_ALARM_INTERVAL_TICKS;
                }
                continue;
            }
            if (remainTicks > FINAL_LEADOUT_TICKS) {
                if (audioState.leadoutLoopCount < 2 && gameTick >= audioState.nextLeadoutLoopTick) {
                    playBombAlarmSound(room, code, SOUND_RUSH_ALARM_LEADOUT_LOOP, 1.0F, 1.0F);
                    audioState.leadoutLoopCount++;
                    audioState.nextLeadoutLoopTick = gameTick + LEADOUT_LOOP_INTERVAL_TICKS;
                }
                continue;
            }
            if (remainTicks > 0L && !audioState.finalLeadoutPlayed) {
                subtitleService.onBombFinalCountdown(code, gameTick);
                playBombAlarmSound(room, code, SOUND_RUSH_ALARM_LEADOUT_FINAL, 1.0F, 1.0F);
                audioState.finalLeadoutPlayed = true;
            }
        }
    }

    private void beginHoldAudio(RoomDefinition room, ServerPlayer player, BombUseHold hold, RoomCapturePointDefinition point, long gameTick) {
        if (player == null || hold == null) {
            return;
        }
        BombHoldAudioState audioState = new BombHoldAudioState();
        holdAudioStates.put(player.getUUID(), audioState);
        if (hold.action() == BombHoldAction.PLANT) {
            playShortSoundToPlayer(player, SOUND_RUSH_CHARGE_PLANT_INTRO, 1.0F, 1.0F);
            audioState.introPlayed = true;
            return;
        }
        playLoopSoundToPlayer(player, SOUND_RUSH_CHARGE_BEEP, 1.0F, 0);
        audioState.beepLoopActive = true;
    }

    private void syncHoldAudio(RoomDefinition room, ServerPlayer player, BombUseHold hold, long requiredTicks, long gameTick) {
        if (room == null || player == null || hold == null) {
            return;
        }
        BombHoldAudioState audioState = holdAudioStates.computeIfAbsent(player.getUUID(), ignored -> new BombHoldAudioState());
        long elapsedTicks = Math.max(0L, gameTick - hold.startTick());
        long finishWindowTicks = resolveHoldFinishWindowTicks(requiredTicks, hold.action());
        long finishStartTick = Math.max(hold.startTick(), hold.startTick() + Math.max(0L, requiredTicks - finishWindowTicks));
        if (hold.action() == BombHoldAction.PLANT) {
            long introWindowTicks = resolvePlantIntroWindowTicks(requiredTicks, finishWindowTicks);
            long introEndTick = hold.startTick() + introWindowTicks;
            if (!audioState.beepLoopActive && !audioState.finishPlayed && gameTick >= introEndTick && gameTick < finishStartTick) {
                playLoopSoundToPlayer(player, SOUND_RUSH_CHARGE_BEEP, 1.0F, 0);
                audioState.beepLoopActive = true;
            }
        }
        if (!audioState.finishPlayed && gameTick >= finishStartTick && elapsedTicks < requiredTicks) {
            if (audioState.beepLoopActive) {
                stopLoopSoundToPlayer(player, SOUND_RUSH_CHARGE_BEEP, 0);
                audioState.beepLoopActive = false;
            }
            playShortSoundToPlayer(
                player,
                hold.action() == BombHoldAction.PLANT ? SOUND_RUSH_CHARGE_PLANT_FINISH : SOUND_RUSH_CHARGE_DISARM_LEADOUT,
                1.0F,
                1.0F
            );
            audioState.finishPlayed = true;
        }
    }

    private void startAdvanceCountdown(RoomDefinition room, long gameTick) {
        clearRetreatPersistentSubtitles();
        clearRetreatTitles();
        clearCountdownTitles();
        phase = Phase.ADVANCE_COUNTDOWN;
        retreatDeadlineTick = -1L;
        advanceCountdownEndTick = gameTick + STAGE_ADVANCE_COUNTDOWN_SECONDS * 20L;
        subtitleService.onAdvanceCountdown(Math.min(Math.max(1, room.rushConfig().stageCount()), currentStageIndex + 1), gameTick);
    }

    private void refreshDeployViewForRespawnPlayers(RoomDefinition room, long gameTick, int sourceStageIndex, int targetStageIndex) {
        if (room == null) {
            return;
        }
        int maxStageCount = Math.max(1, room.rushConfig().stageCount());
        int normalizedSourceStageIndex = Mth.clamp(sourceStageIndex, 1, maxStageCount);
        int normalizedTargetStageIndex = Mth.clamp(targetStageIndex, 1, maxStageCount);
        RoomViewPosition previousView = room.rushConfig().stageDeployViewPosition(normalizedSourceStageIndex);
        RoomViewPosition nextView = room.rushConfig().stageDeployViewPosition(normalizedTargetStageIndex);
        if (previousView == null || nextView == null) {
            return;
        }
        for (UUID playerId : runningState.getRoomPlayers()) {
            if (!runningState.isInDeployFlow(playerId)) {
                continue;
            }
            ServerPlayer player = findOnlinePlayer(playerId);
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

    private void syncObjectivePresence(RoomDefinition room) {
        for (String code : List.copyOf(campACountByCode.keySet())) {
            campACountByCode.put(code, 0);
            campBCountByCode.put(code, 0);
        }
        if (room == null) {
            return;
        }
        for (UUID playerId : List.copyOf(runningState.getRoomPlayers())) {
            ServerPlayer player = findOnlinePlayer(playerId);
            RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
            if (player == null || teamId == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            RoomCapturePointDefinition closest = nearestTrackedPoint(room, player);
            if (closest == null) {
                continue;
            }
            String code = normalizeCode(closest.code());
            if (teamId == RoomTeamId.CAMP_A) {
                campACountByCode.put(code, campACountByCode.getOrDefault(code, 0) + 1);
            } else if (teamId == RoomTeamId.CAMP_B) {
                campBCountByCode.put(code, campBCountByCode.getOrDefault(code, 0) + 1);
            }
        }
    }

    private void syncObjectiveSnapshots(RoomDefinition room, long gameTick) {
        if (room == null) {
            return;
        }
        for (RoomCapturePointDefinition point : room.rushConfig().definedObjectivePoints()) {
            if (point == null) {
                continue;
            }
            String code = normalizeCode(point.code());
            int stageIndex = point.stageIndex();
            if (stageIndex < currentStageIndex) {
                objectiveOwnerByCode.put(code, RoomTeamId.CAMP_A);
                objectiveDrivingByCode.put(code, null);
                objectiveProgressByCode.put(code, 100.0D);
                continue;
            }
            if (stageIndex > currentStageIndex) {
                objectiveOwnerByCode.put(code, RoomTeamId.CAMP_B);
                objectiveDrivingByCode.put(code, null);
                objectiveProgressByCode.put(code, 0.0D);
                continue;
            }
            BombSiteState state = bombStates.getOrDefault(code, new BombSiteState(stageIndex, BombPhase.SAFE, -1L, null));
            if (state.phase() == BombPhase.EXPLODED) {
                objectiveOwnerByCode.put(code, RoomTeamId.CAMP_A);
                objectiveDrivingByCode.put(code, null);
                objectiveProgressByCode.put(code, 100.0D);
                continue;
            }
            if (state.phase() == BombPhase.ARMED) {
                objectiveOwnerByCode.put(code, null);
                objectiveDrivingByCode.put(code, RoomTeamId.CAMP_A);
                objectiveProgressByCode.put(code, resolveBombFuseProgress(room, gameTick, state));
            } else {
                objectiveOwnerByCode.put(code, RoomTeamId.CAMP_B);
                objectiveDrivingByCode.put(code, null);
                objectiveProgressByCode.put(code, 0.0D);
            }
        }
    }

    private void syncBombActionbars(RoomDefinition room, long gameTick) {
        for (UUID playerId : List.copyOf(runningState.getRoomPlayers())) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player == null) {
                continue;
            }
            ServerActionBarService.Builder builder = buildBombActionbar(room, player, gameTick);
            if (builder == null) {
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_BOMB_USE);
                continue;
            }
            ServerActionBarService.showBuilder(player, ACTIONBAR_SOURCE_BOMB_USE, builder, 30);
        }
    }

    private ServerActionBarService.Builder buildBombActionbar(RoomDefinition room, ServerPlayer player, long gameTick) {
        if (room == null || player == null || runningState.isInDeployFlow(player.getUUID())) {
            return null;
        }
        RoomTeamId teamId = runningState.getRoomTeams().get(player.getUUID());
        if (teamId == null) {
            return null;
        }
        BombUseHold hold = activeHolds.get(player.getUUID());
        RoomCapturePointDefinition point = hold == null
            ? nearestStageObjectivePoint(room, player, BOMB_INTERACT_RADIUS_SQ, true)
            : findObjectivePoint(room, hold.objectiveCode());
        if (point == null || point.centerPosition() == null || distanceSq(player, point.centerPosition()) > BOMB_INTERACT_RADIUS_SQ) {
            return null;
        }
        BombSiteState state = bombState(point);
        if (state == null) {
            return null;
        }
        String code = normalizeCode(point.code());
        ServerActionBarService.Builder builder = ServerActionBarService.builder();
        if (hold != null && code.equals(hold.objectiveCode())) {
            appendHoldActionbar(builder, room, hold, gameTick);
        } else if (state.phase() == BombPhase.ARMED) {
            if (teamId == RoomTeamId.CAMP_B) {
                builder.text("拆除炸弹", ChatFormatting.RED)
                    .separator()
                    .text("按下", ChatFormatting.WHITE)
                    .key("F")
                    .text("拆除炸弹", ChatFormatting.WHITE);
            } else {
                builder.text("守住炸弹", ChatFormatting.RED);
            }
        } else if (state.phase() == BombPhase.EXPLODED) {
            if (teamId == RoomTeamId.CAMP_A) {
                builder.text("此通讯设施已被我军炸毁", ChatFormatting.GREEN);
            } else {
                builder.text("此通讯设施已被敌军炸毁", ChatFormatting.RED);
            }
        } else if (teamId == RoomTeamId.CAMP_A) {
            builder.text("摧毁通讯设施", ChatFormatting.RED)
                .separator()
                .text("按下", ChatFormatting.WHITE)
                .key("F")
                .text("安装炸弹", ChatFormatting.WHITE);
        } else {
            builder.text("防守通讯设施", ChatFormatting.RED);
        }
        if (state.phase() == BombPhase.ARMED) {
            builder.separator()
                .text("即将爆炸：", ChatFormatting.RED)
                .text(String.valueOf(resolveBombFuseRemainSeconds(room, code, gameTick)), ChatFormatting.DARK_RED);
        }
        return builder.build().getString().isBlank() ? null : builder;
    }

    private String buildEventText(RoomDefinition room, ServerPlayer player, RoomTeamId selfTeam) {
        if (room == null || player == null || selfTeam == null) {
            return "-";
        }
        // GD656Mods: 背水一战/破釜沉舟事件字幕(用户指定文案)
        if (desperateMode == DesperateMode.BACK_TO_WALL) {
            return selfTeam == RoomTeamId.CAMP_A
                ? "支援抵达，掌握机会，摧毁全部通讯设施！"
                : "我们受到了敌军大规模还击，必须死守全部通讯设施！";
        }
        if (desperateMode == DesperateMode.BURN_BOATS) {
            return selfTeam == RoomTeamId.CAMP_A
                ? "我军已没有退路，与炸弹同归于尽！"
                : "敌军的进攻仍然猛烈！全力拆除炸弹！";
        }
        if (phase == Phase.RETREAT) {
            int defenders = countDefendersInStageBoundary(room, retreatStageIndex);
            return selfTeam == RoomTeamId.CAMP_A
                ? "清理残余的 " + defenders + " 名敌方士兵"
                : "我方还有 " + defenders + " 名未撤离士兵";
        }
        if (phase == Phase.ADVANCE_COUNTDOWN) {
            return selfTeam == RoomTeamId.CAMP_A ? "组织下一波攻势" : "构建新的防线";
        }
        RoomCapturePointDefinition point = nearestStageObjectivePoint(room, player, OBJECTIVE_FOCUS_RADIUS_SQ, true);
        BombSiteState state = bombState(point);
        if (point == null || point.centerPosition() == null || state == null) {
            return "-";
        }
        if (state.phase() == BombPhase.EXPLODED) {
            return selfTeam == RoomTeamId.CAMP_A ? "此通讯设施已被我军炸毁" : "此通讯设施已被敌军炸毁";
        }
        if (state.phase() == BombPhase.ARMED) {
            return selfTeam == RoomTeamId.CAMP_A ? "守住炸弹" : "拆除炸弹";
        }
        return selfTeam == RoomTeamId.CAMP_A ? "摧毁通讯设施" : "防守通讯设施";
    }

    private String buildFocusPayload(RoomDefinition room, ServerPlayer player, RoomTeamId selfTeam) {
        if (room == null || player == null || selfTeam == null) {
            return "-";
        }
        RoomObjectivePresentationContext context = new RoomObjectivePresentationContext(
            activeObjectivePoints(room),
            objectiveRuntimeView,
            activeObjectiveCode(room, player),
            selfTeam
        );
        return objectiveFocusPayloadService.buildFocusPayload(context);
    }

    private List<List<RoomPosition>> allowedBoundaries(RoomDefinition room, RoomTeamId teamId) {
        ArrayList<List<RoomPosition>> boundaries = new ArrayList<>();
        if (teamId == RoomTeamId.CAMP_A) {
            addBoundaryIfValid(boundaries, room.rushConfig().campABaseBoundary());
            for (int stageIndex = 1; stageIndex <= currentStageIndex; stageIndex++) {
                addBoundaryIfValid(boundaries, room.rushConfig().stageBoundary(stageIndex));
            }
            return boundaries;
        }
        if (teamId == RoomTeamId.CAMP_B) {
            addBoundaryIfValid(boundaries, room.rushConfig().campBBaseBoundary());
            for (int stageIndex = defenderBoundaryStartStage(room); stageIndex <= room.rushConfig().stageCount(); stageIndex++) {
                addBoundaryIfValid(boundaries, room.rushConfig().stageBoundary(stageIndex));
            }
        }
        return boundaries;
    }

    private String resolveTopStatus(RoomDefinition room, boolean inDeployFlow, long gameTick) {
        RoomCapturePointDefinition point = currentObjectivePoint(room);
        if (point == null) {
            return "等待阶段初始化";
        }
        String code = normalizeCode(point.code());
        // GD656Mods: 背水一战/破釜沉舟顶部状态(样式复用突破模式, 客户端已支持蓝/红)
        if (desperateMode == DesperateMode.BACK_TO_WALL) {
            return hudStyledText("BT_LAST_STAND_STATUS", "背水一战");
        }
        if (desperateMode == DesperateMode.BURN_BOATS) {
            return hudStyledText("BT_BURN_BOATS_STATUS", "破釜沉舟");
        }
        if (phase == Phase.RETREAT) {
            return "清理阶段" + stageLabel(retreatStageIndex) + "区域守军中";
        }
        if (phase == Phase.ADVANCE_COUNTDOWN) {
            return "即将进入阶段" + stageLabel(Math.min(Math.max(1, room.rushConfig().stageCount()), currentStageIndex + 1));
        }
        if (inDeployFlow) {
            return "部署中，准备前往阶段" + stageLabel(currentStageIndex) + "通讯设施";
        }
        if (isAnyCurrentStageBombArmed(room)) {
            return "炸弹点 " + code + " 已安装炸弹，剩余 " + resolveBombFuseRemainSeconds(room, code, gameTick) + " 秒";
        }
        if (currentHoldProgress(code, BombHoldAction.PLANT, room.rushConfig().bombPlantTimeSeconds()) > 0.0D) {
            return "炸弹点 " + code + " 安装中";
        }
        if (currentHoldProgress(code, BombHoldAction.DEFUSE, room.rushConfig().bombDefuseTimeSeconds()) > 0.0D) {
            return "炸弹点 " + code + " 拆除中";
        }
        return "炸弹点 " + code + " 安全";
    }

    private String stageBoundaryToken(int stageIndex) {
        int displayFrontlineStage = phase == Phase.ADVANCE_COUNTDOWN
            ? Math.min(Math.max(1, currentStageIndex), currentStageIndex + 1)
            : currentStageIndex;
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

    private long resolveStageDeadlineTick(RoomDefinition room, long combatStartTick) {
        if (room == null || room.rushConfig().stageDurationMinutes() <= 0) {
            return -1L;
        }
        return combatStartTick + room.rushConfig().stageDurationMinutes() * 60L * 20L;
    }

    private int resolveRemainSeconds(long gameTick) {
        // GD656Mods: 背水一战/破釜沉舟显示对应倒计时
        if (desperateMode != DesperateMode.NONE) {
            return desperateDeadlineTick <= 0L || gameTick >= desperateDeadlineTick
                ? 0
                : (int) Math.max(0L, (desperateDeadlineTick - gameTick + 19L) / 20L);
        }
        if (phase == Phase.RETREAT) {
            return retreatDeadlineTick <= 0L || gameTick >= retreatDeadlineTick ? 0 : (int) Math.max(0L, (retreatDeadlineTick - gameTick + 19L) / 20L);
        }
        if (phase == Phase.ADVANCE_COUNTDOWN) {
            return advanceCountdownEndTick <= 0L || gameTick >= advanceCountdownEndTick ? 0 : (int) Math.max(0L, (advanceCountdownEndTick - gameTick + 19L) / 20L);
        }
        if (stageDeadlineTick <= 0L || gameTick >= stageDeadlineTick) {
            return 0;
        }
        return (int) Math.max(0L, (stageDeadlineTick - gameTick + 19L) / 20L);
    }

    private boolean isCurrentBombArmed() {
        for (BombSiteState state : bombStates.values()) {
            if (state != null && state.stageIndex() == currentStageIndex && state.phase() == BombPhase.ARMED) {
                return true;
            }
        }
        return false;
    }

    private String buildAudioState(RoomDefinition room, RoomTeamId selfTeam, long gameTick) {
        if (phase == Phase.RETREAT) {
            return "BT_RETREAT";
        }
        if (phase == Phase.ADVANCE_COUNTDOWN) {
            return "BT_STAGE_ADVANCE";
        }
        if (shouldPlayFinalBombLoop(room, gameTick)) {
            if (room != null && currentStageIndex >= Math.max(1, room.rushConfig().stageCount())) {
                return "BT_LAST_SEGMENT";
            }
            return resolveFinalObjectiveAudioState(selfTeam);
        }
        if (attackTroopCap > 0 && attackTroops <= Math.max(1, attackTroopCap / 5)) {
            return resolveFinalObjectiveAudioState(selfTeam);
        }
        return isAnyCurrentStageBombArmed(room) ? "RUSH_BOMB_PLANTED" : "RUNNING";
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

    private boolean shouldPlayFinalBombLoop(RoomDefinition room, long gameTick) {
        if (room == null || phase != Phase.COMBAT) {
            return false;
        }
        RoomCapturePointDefinition point = resolveSingleRemainingBombPoint(room);
        if (point == null) {
            return false;
        }
        BombSiteState state = bombState(point);
        if (state.phase() != BombPhase.ARMED || state.armedAtTick() < 0L) {
            return false;
        }
        long fuseTicks = Math.max(1L, room.rushConfig().bombFuseTimeSeconds() * 20L);
        long elapsedTicks = Math.max(0L, gameTick - state.armedAtTick());
        return elapsedTicks * 2L >= fuseTicks;
    }

    private RoomCapturePointDefinition resolveSingleRemainingBombPoint(RoomDefinition room) {
        if (room == null) {
            return null;
        }
        RoomCapturePointDefinition remaining = null;
        for (RoomCapturePointDefinition point : currentStageObjectivePoints(room, true)) {
            if (point == null || bombState(point).phase() == BombPhase.EXPLODED) {
                continue;
            }
            if (remaining != null) {
                return null;
            }
            remaining = point;
        }
        return remaining;
    }

    private boolean hasCurrentBombFused(RoomDefinition room, long gameTick) {
        return findFusedBombCode(room, gameTick) != null;
    }

    private int resolveBombFuseRemainSeconds(RoomDefinition room, long gameTick) {
        if (room == null) {
            return 0;
        }
        RoomCapturePointDefinition point = currentObjectivePoint(room);
        if (point == null) {
            return 0;
        }
        return resolveBombFuseRemainSeconds(room, normalizeCode(point.code()), gameTick);
    }

    private int resolveBombFuseRemainSeconds(RoomDefinition room, String objectiveCode, long gameTick) {
        if (room == null || objectiveCode == null || objectiveCode.isBlank()) {
            return 0;
        }
        BombSiteState state = bombStates.get(normalizeCode(objectiveCode));
        if (state == null || state.phase() != BombPhase.ARMED || state.armedAtTick() < 0L) {
            return 0;
        }
        long remainTicks = room.rushConfig().bombFuseTimeSeconds() * 20L - (gameTick - state.armedAtTick());
        return (int) Math.max(0L, (remainTicks + 19L) / 20L);
    }

    private double currentHoldProgress(String objectiveCode, BombHoldAction action, int requiredSeconds) {
        if (objectiveCode == null || objectiveCode.isBlank() || requiredSeconds <= 0) {
            return 0.0D;
        }
        long currentTick = server.getTickCount();
        double best = 0.0D;
        long requiredTicks = requiredSeconds * 20L;
        for (BombUseHold hold : activeHolds.values()) {
            if (hold == null || hold.action() != action || !objectiveCode.equals(hold.objectiveCode())) {
                continue;
            }
            best = Math.max(best, ((double) (currentTick - hold.startTick()) / (double) requiredTicks) * 100.0D);
        }
        return clampPercentage(best);
    }

    private void appendHoldActionbar(ServerActionBarService.Builder builder, RoomDefinition room, BombUseHold hold, long gameTick) {
        if (builder == null || room == null || hold == null) {
            return;
        }
        int requiredSeconds = hold.action() == BombHoldAction.PLANT
            ? Math.max(1, room.rushConfig().bombPlantTimeSeconds())
            : Math.max(1, room.rushConfig().bombDefuseTimeSeconds());
        int remainingTenths = resolveRemainingHoldTenths(hold, requiredSeconds, gameTick);
        builder.text(hold.action() == BombHoldAction.PLANT ? "正在安装炸弹：" : "正在拆除炸弹：", ChatFormatting.AQUA)
            .text(formatTenths(remainingTenths), holdCountdownColor(remainingTenths));
    }

    private void showBombCompletionActionbar(UUID playerId, String text) {
        ServerPlayer player = findOnlinePlayer(playerId);
        if (player == null || text == null || text.isBlank()) {
            return;
        }
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_BOMB_USE,
            ServerActionBarService.builder().text(text, ChatFormatting.GREEN),
            40
        );
    }

    private RoomCapturePointDefinition nearestTrackedPoint(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return null;
        }
        RoomCapturePointDefinition best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (RoomCapturePointDefinition point : activeObjectivePoints(room)) {
            if (point == null || point.centerPosition() == null) {
                continue;
            }
            double distanceSq = distanceSq(player, point.centerPosition());
            if (distanceSq > OBJECTIVE_FOCUS_RADIUS_SQ || distanceSq >= bestDistanceSq) {
                continue;
            }
            best = point;
            bestDistanceSq = distanceSq;
        }
        return best;
    }

    private List<RoomCapturePointDefinition> currentStageObjectivePoints(RoomDefinition room, boolean includeExploded) {
        if (room == null) {
            return List.of();
        }
        ArrayList<RoomCapturePointDefinition> points = new ArrayList<>();
        for (RoomCapturePointDefinition point : room.rushConfig().stageObjectivePoints(currentStageIndex)) {
            if (point == null) {
                continue;
            }
            if (!includeExploded && bombState(point).phase() == BombPhase.EXPLODED) {
                continue;
            }
            points.add(point);
        }
        return List.copyOf(points);
    }

    private RoomCapturePointDefinition nearestStageObjectivePoint(RoomDefinition room, ServerPlayer player, double maxDistanceSq, boolean includeExploded) {
        if (room == null || player == null) {
            return null;
        }
        RoomCapturePointDefinition best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (RoomCapturePointDefinition point : currentStageObjectivePoints(room, includeExploded)) {
            if (point == null || point.centerPosition() == null) {
                continue;
            }
            double distanceSq = distanceSq(player, point.centerPosition());
            if (distanceSq > maxDistanceSq || distanceSq >= bestDistanceSq) {
                continue;
            }
            best = point;
            bestDistanceSq = distanceSq;
        }
        return best;
    }

    private RoomCapturePointDefinition currentObjectivePoint(RoomDefinition room) {
        RoomCapturePointDefinition firstSafe = null;
        RoomCapturePointDefinition firstExploded = null;
        for (RoomCapturePointDefinition point : currentStageObjectivePoints(room, true)) {
            BombSiteState state = bombState(point);
            if (state.phase() == BombPhase.ARMED) {
                return point;
            }
            if (state.phase() == BombPhase.SAFE && firstSafe == null) {
                firstSafe = point;
            }
            if (state.phase() == BombPhase.EXPLODED && firstExploded == null) {
                firstExploded = point;
            }
        }
        return firstSafe != null ? firstSafe : firstExploded;
    }

    private BombSiteState bombState(RoomCapturePointDefinition point) {
        if (point == null) {
            return new BombSiteState(currentStageIndex, BombPhase.SAFE, -1L, null);
        }
        return bombStates.getOrDefault(normalizeCode(point.code()), new BombSiteState(point.stageIndex(), BombPhase.SAFE, -1L, null));
    }

    private boolean isAnyCurrentStageBombArmed(RoomDefinition room) {
        for (RoomCapturePointDefinition point : currentStageObjectivePoints(room, true)) {
            if (bombState(point).phase() == BombPhase.ARMED) {
                return true;
            }
        }
        return false;
    }

    private String findFusedBombCode(RoomDefinition room, long gameTick) {
        if (room == null) {
            return null;
        }
        long fuseTicks = room.rushConfig().bombFuseTimeSeconds() * 20L;
        for (RoomCapturePointDefinition point : currentStageObjectivePoints(room, true)) {
            BombSiteState state = bombState(point);
            if (state.phase() != BombPhase.ARMED || state.armedAtTick() < 0L) {
                continue;
            }
            if (gameTick - state.armedAtTick() >= fuseTicks) {
                return normalizeCode(point.code());
            }
        }
        return null;
    }

    private boolean areAllCurrentStageBombsExploded(RoomDefinition room) {
        List<RoomCapturePointDefinition> points = currentStageObjectivePoints(room, true);
        if (points.isEmpty()) {
            return false;
        }
        for (RoomCapturePointDefinition point : points) {
            if (bombState(point).phase() != BombPhase.EXPLODED) {
                return false;
            }
        }
        return true;
    }

    private BombHoldAction resolveBombAction(RoomDefinition room, ServerPlayer player, RoomTeamId teamId, RoomCapturePointDefinition point) {
        if (phase != Phase.COMBAT || room == null || player == null || teamId == null || point == null || point.stageIndex() != currentStageIndex) {
            return null;
        }
        if (point.centerPosition() == null || distanceSq(player, point.centerPosition()) > BOMB_INTERACT_RADIUS_SQ) {
            return null;
        }
        BombSiteState state = bombState(point);
        if (teamId == RoomTeamId.CAMP_A && state.phase() == BombPhase.SAFE) {
            return BombHoldAction.PLANT;
        }
        if (teamId == RoomTeamId.CAMP_B && state.phase() == BombPhase.ARMED) {
            return BombHoldAction.DEFUSE;
        }
        return null;
    }

    private RoomAttackTroopBalanceSupport.AttackTroopBalanceSnapshot resolveAttackTroopBalance(RoomDefinition room) {
        return resolveAttackTroopBalance(room, currentStageIndex);
    }

    private RoomAttackTroopBalanceSupport.AttackTroopBalanceSnapshot resolveAttackTroopBalance(RoomDefinition room, int targetStageIndex) {
        if (room == null) {
            return RoomAttackTroopBalanceSupport.resolve(runningState, null, 0, 0.0D);
        }
        return RoomAttackTroopBalanceSupport.resolve(
            runningState,
            room.rushConfig().troopBalanceReference(),
            room.rushConfig().maxAttackTroopsAt12Players(),
            room.rushConfig().stageTroopRestoreRatio(targetStageIndex)
        );
    }

    private int countDefendersInStageBoundary(RoomDefinition room, int stageIndex) {
        if (room == null || stageIndex <= 0) {
            return 0;
        }
        List<RoomPosition> boundary = room.rushConfig().stageBoundary(stageIndex);
        if (boundary == null || boundary.size() < 3) {
            return 0;
        }
        int count = 0;
        for (UUID playerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(playerId) != RoomTeamId.CAMP_B) {
                continue;
            }
            if (runningState.isPlayerPendingDowned(playerId) || runningState.isPlayerDowned(playerId)) {
                continue;
            }
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (RoomBoundaryPolygonSupport.isPointInsideBoundary(player.getX(), player.getZ(), boundary)) {
                count++;
            }
        }
        return count;
    }

    private void appendPreviousStageSpawnIcons(List<RoomDeployHudIcon> icons, RoomDefinition room, int stageIndex, RoomTeamId ownerTeam) {
        if (icons == null || room == null || ownerTeam == null) {
            return;
        }
        if (stageIndex <= 0 || stageIndex > room.rushConfig().stageCount()) {
            return;
        }
        for (RoomCapturePointDefinition point : room.rushConfig().definedObjectivePoints()) {
            if (point == null || point.centerPosition() == null || point.stageIndex() != stageIndex) {
                continue;
            }
            icons.add(new RoomDeployHudIcon(
                "CAPTURE:" + point.code().toUpperCase(Locale.ROOT),
                point.centerPosition(),
                ownerTeam,
                point.displayName(),
                true
            ));
        }
    }

    private void appendStageObjectiveIcons(List<RoomDeployHudIcon> icons, RoomDefinition room, int stageIndex, RoomTeamId ownerTeam) {
        if (icons == null || room == null || ownerTeam == null) {
            return;
        }
        int maxStage = room.rushConfig().stageCount();
        if (stageIndex <= 0 || stageIndex > maxStage) {
            return;
        }
        for (RoomCapturePointDefinition point : room.rushConfig().stageObjectivePoints(stageIndex)) {
            if (point == null || point.centerPosition() == null) {
                continue;
            }
            icons.add(new RoomDeployHudIcon(
                "CAPTURE:" + point.code().toUpperCase(Locale.ROOT),
                point.centerPosition(),
                ownerTeam,
                point.displayName(),
                true
            ));
        }
    }

    private int defenderBoundaryStartStage(RoomDefinition room) {
        if (room == null) {
            return currentStageIndex;
        }
        if (phase == Phase.ADVANCE_COUNTDOWN) {
            return Math.min(room.rushConfig().stageCount(), currentStageIndex + 1);
        }
        return currentStageIndex;
    }

    private String stageLabel(int stageIndex) {
        if (stageIndex <= 0) {
            return "-";
        }
        return String.valueOf((char) ('A' + stageIndex - 1));
    }

    private void setAttackTroopCap(int cap, boolean clampCurrentTroops) {
        attackTroopCap = Math.max(0, cap);
        if (clampCurrentTroops && attackTroops > attackTroopCap) {
            attackTroops = attackTroopCap;
        }
    }

    private void refreshAttackTroopCapForNextStage(RoomDefinition room, int targetStageIndex) {
        setAttackTroopCap(resolveAttackTroopBalance(room, targetStageIndex).troopCap(), true);
    }

    private int applyStageReward(RoomDefinition room) {
        return applyStageReward(room, currentStageIndex);
    }

    private int applyStageReward(RoomDefinition room, int targetStageIndex) {
        if (room == null) {
            return 0;
        }
        RoomAttackTroopBalanceSupport.AttackTroopBalanceSnapshot balance = resolveAttackTroopBalance(room, targetStageIndex);
        setAttackTroopCap(balance.troopCap(), true);
        if (balance.stageReward() <= 0 || attackTroops >= attackTroopCap) {
            return 0;
        }
        int actualAdd = Math.min(balance.stageReward(), Math.max(0, attackTroopCap - attackTroops));
        attackTroops += actualAdd;
        return actualAdd;
    }

    private void consumeAttackTroop(int amount) {
        attackTroops = Math.max(0, attackTroops - Math.max(0, amount));
    }

    // ===== GD656Mods: Rush 背水一战 / 破釜沉舟 =====

    private boolean isBackToWallEnabled(RoomDefinition room) {
        return room != null && room.commonConfig().enableBackAgainstWall();
    }

    /**
     * 触发背水一战: 进攻方兵力耗尽 且 当前阶段至少一个炸弹点已安放炸弹等待爆炸。
     * 背水一战持续 2 分钟(短于突破), 期间进攻方兵力无限、游戏正常运行。
     */
    private boolean tryActivateBackToWall(RoomDefinition room, long gameTick) {
        if (!isBackToWallEnabled(room) || desperateMode != DesperateMode.NONE || phase != Phase.COMBAT) {
            return false;
        }
        if (!isAnyCurrentStageBombArmed(room)) {
            return false;
        }
        desperateMode = DesperateMode.BACK_TO_WALL;
        desperateDeadlineTick = gameTick + RUSH_BACK_TO_WALL_SECONDS * 20L;
        subtitleService.onBackToWallStart(gameTick);
        playRushDesperateVoice(true);
        return true;
    }

    /**
     * 背水一战/破釜沉舟状态机。返回 true 表示已处理(转换/结束), 上层停止本 tick 其他判定。
     */
    private boolean tickDesperateMode(RoomDefinition room, long gameTick) {
        if (desperateMode == DesperateMode.NONE) {
            return false;
        }
        if (desperateMode == DesperateMode.BACK_TO_WALL) {
            if (desperateDeadlineTick > 0L && gameTick < desperateDeadlineTick) {
                return false;
            }
            // 2 分钟到: 当前阶段所有尚未炸毁的炸弹点均已安放炸弹等待爆炸 -> 进入破釜沉舟; 否则进攻失败
            if (areAllRemainingCurrentStageBombsArmed(room)) {
                activateBurnBoats(room, gameTick);
            } else {
                failDesperateMode(gameTick, "背水一战结束，进攻方未能安装全部炸弹");
            }
            return true;
        }
        // BURN_BOATS: 倒计时结束(最长引信炸弹爆炸, 正常由 handleBombExploded 触发胜利, 此处兜底)
        if (desperateDeadlineTick > 0L && gameTick >= desperateDeadlineTick) {
            desperateMode = DesperateMode.NONE;
            desperateDeadlineTick = -1L;
            subtitleService.onBurnBoatsSucceed(gameTick);
            pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_A, "破釜沉舟成功");
            return true;
        }
        return false;
    }

    private boolean areAllRemainingCurrentStageBombsArmed(RoomDefinition room) {
        if (room == null) {
            return false;
        }
        List<RoomCapturePointDefinition> points = currentStageObjectivePoints(room, true);
        if (points.isEmpty()) {
            return false;
        }
        boolean hasRemainingBomb = false;
        for (RoomCapturePointDefinition point : points) {
            if (point == null) {
                continue;
            }
            BombSiteState state = bombState(point);
            if (state.phase() == BombPhase.EXPLODED) {
                continue;
            }
            hasRemainingBomb = true;
            if (state.phase() != BombPhase.ARMED) {
                return false;
            }
        }
        return hasRemainingBomb;
    }

    /** 进入破釜沉舟: 时长 = 所有已安放炸弹中引信剩余最长者 */
    private void activateBurnBoats(RoomDefinition room, long gameTick) {
        desperateMode = DesperateMode.BURN_BOATS;
        long maxRemainTicks = 0L;
        for (BombSiteState state : bombStates.values()) {
            if (state == null || state.stageIndex() != currentStageIndex || state.phase() != BombPhase.ARMED || state.armedAtTick() < 0L) {
                continue;
            }
            long fuseTicks = room == null ? 0L : Math.max(1L, room.rushConfig().bombFuseTimeSeconds() * 20L);
            long remain = Math.max(0L, fuseTicks - (gameTick - state.armedAtTick()));
            maxRemainTicks = Math.max(maxRemainTicks, remain);
        }
        desperateDeadlineTick = maxRemainTicks > 0L ? gameTick + maxRemainTicks : gameTick + 20L;
        subtitleService.onBurnBoatsStart(gameTick);
        playRushDesperateVoice(false);
    }

    private void failDesperateMode(long gameTick, String reason) {
        desperateMode = DesperateMode.NONE;
        desperateDeadlineTick = -1L;
        subtitleService.onDesperateFail(gameTick);
        pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_B, reason);
    }

    /** 背水一战/破釜沉舟触发音效: 无专属语音, 复用炸弹警报音表达紧张感 */
    private void playRushDesperateVoice(boolean backToWall) {
        String alarm = SOUND_RUSH_ALARM_01.toString();
        playRushVoiceToTeam(RoomTeamId.CAMP_A, alarm);
        playRushVoiceToTeam(RoomTeamId.CAMP_B, alarm);
    }

    private int addAttackTroops(int amount) {
        if (amount <= 0 || attackTroops >= attackTroopCap) {
            return 0;
        }
        int actualAdd = Math.min(amount, Math.max(0, attackTroopCap - attackTroops));
        attackTroops += actualAdd;
        return actualAdd;
    }

    private List<String> currentStageRemainingCodes(RoomDefinition room) {
        if (room == null) {
            return List.of();
        }
        ArrayList<String> remainingCodes = new ArrayList<>();
        for (RoomCapturePointDefinition point : currentStageObjectivePoints(room, true)) {
            if (point == null || bombState(point).phase() == BombPhase.EXPLODED) {
                continue;
            }
            remainingCodes.add(normalizeCode(point.code()));
        }
        return List.copyOf(remainingCodes);
    }

    private int currentStageArmedBombCount(RoomDefinition room) {
        if (room == null) {
            return 0;
        }
        int count = 0;
        for (RoomCapturePointDefinition point : currentStageObjectivePoints(room, true)) {
            if (point != null && bombState(point).phase() == BombPhase.ARMED) {
                count++;
            }
        }
        return count;
    }

    private int countActiveHolds(String objectiveCode, BombHoldAction action) {
        String normalizedCode = normalizeCode(objectiveCode);
        int count = 0;
        for (BombUseHold hold : activeHolds.values()) {
            if (hold != null && hold.action() == action && normalizedCode.equals(hold.objectiveCode())) {
                count++;
            }
        }
        return count;
    }

    private void clearPlayerHold(RoomDefinition room, UUID playerId, long gameTick) {
        clearPlayerHold(room, playerId, gameTick, true);
    }

    private void clearPlayerHold(RoomDefinition room, UUID playerId, long gameTick, boolean notifyInterrupt) {
        if (playerId == null) {
            return;
        }
        BombUseHold removedHold = activeHolds.remove(playerId);
        BombHoldAudioState audioState = holdAudioStates.remove(playerId);
        ServerPlayer player = findOnlinePlayer(playerId);
        if (player != null) {
            if (audioState != null && audioState.beepLoopActive) {
                stopLoopSoundToPlayer(player, SOUND_RUSH_CHARGE_BEEP, 0);
            }
            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_BOMB_USE);
        }
        if (!notifyInterrupt || removedHold == null || room == null) {
            return;
        }
        if (countActiveHolds(removedHold.objectiveCode(), removedHold.action()) > 0) {
            return;
        }
        if (removedHold.action() == BombHoldAction.PLANT) {
            subtitleService.onBombPlantInterrupted(removedHold.objectiveCode(), gameTick);
        } else {
            subtitleService.onBombDefuseInterrupted(removedHold.objectiveCode(), gameTick);
        }
    }

    private void clearAllHolds(RoomDefinition room, long gameTick, boolean notifyInterrupt) {
        for (UUID playerId : List.copyOf(activeHolds.keySet())) {
            clearPlayerHold(room, playerId, gameTick, notifyInterrupt);
        }
        activeHolds.clear();
    }

    private void clearAllBombActionbars() {
        for (UUID playerId : List.copyOf(runningState.getRoomPlayers())) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player != null) {
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_BOMB_USE);
            }
        }
    }

    private void notifyRushKilliconEvent(
        UUID playerId,
        String bonusTypeName,
        int victimId,
        boolean sendCaptureNotification,
        String payloadType,
        String objectiveCode
    ) {
        ServerPlayer player = findOnlinePlayer(playerId);
        if (player == null || bonusTypeName == null || bonusTypeName.isBlank()) {
            return;
        }
        String code = normalizeCode(objectiveCode);
        KilliconScoreboardBridge.tryAwardConquestBonus(player, bonusTypeName, 1.0F, "", victimId, null);
        if (sendCaptureNotification) {
            KilliconScoreboardBridge.trySendCaptureNotification(player, victimId, payloadType + "|" + code);
        }
    }

    private ServerPlayer findOnlinePlayer(UUID playerId) {
        return playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    private void playShortSoundToPlayer(ServerPlayer player, String soundId, float volume, float pitch) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientShortSoundPacket.oneShot(soundId, volume, pitch));
    }

    private void playLoopSoundToPlayer(ServerPlayer player, String soundId, float volume, int fadeInTicks) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientLoopSoundPacket.play(soundId, volume, fadeInTicks));
    }

    private void stopLoopSoundToPlayer(ServerPlayer player, String soundId, int fadeOutTicks) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientLoopSoundPacket.stop(soundId, fadeOutTicks));
    }

    private void playRushVoiceToTeam(RoomTeamId teamId, String soundId) {
        if (teamId == null || soundId == null || soundId.isBlank()) {
            return;
        }
        for (UUID playerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(playerId) != teamId) {
                continue;
            }
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player != null) {
                playShortSoundToPlayer(player, soundId, 1.0F, 1.0F);
            }
        }
    }

    private String resolveRushPlantVoice(String objectiveCode) {
        return isRushVoiceGroupA(objectiveCode) ? VOICE_RUSH_PLANT_SET_A : VOICE_RUSH_PLANT_SET_B;
    }

    private String resolveRushAttackDefuseVoice(String objectiveCode) {
        return isRushVoiceGroupA(objectiveCode) ? VOICE_RUSH_ATTACK_DEFUSE_A : VOICE_RUSH_ATTACK_DEFUSE_B;
    }

    private String resolveRushDefendDefuseVoice(String objectiveCode) {
        return isRushVoiceGroupA(objectiveCode) ? VOICE_RUSH_DEFEND_DEFUSE_A : VOICE_RUSH_DEFEND_DEFUSE_B;
    }

    private boolean isRushVoiceGroupA(String objectiveCode) {
        String normalizedCode = normalizeCode(objectiveCode);
        return normalizedCode.endsWith("1") || !normalizedCode.endsWith("2") && !normalizedCode.endsWith("3");
    }

    private void syncRetreatPersistentSubtitles(RoomDefinition room) {
        if (room == null || phase != Phase.RETREAT) {
            clearRetreatPersistentSubtitles();
            return;
        }
        int defenders = countDefendersInStageBoundary(room, retreatStageIndex);
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player == null) {
                retreatSubtitleByPlayer.remove(playerId);
                continue;
            }
            RoomTeamId selfTeam = runningState.getRoomTeams().get(playerId);
            String desired;
            if (selfTeam == RoomTeamId.CAMP_B) {
                desired = "我方还有 " + defenders + " 名未撤退的士兵";
            } else if (selfTeam == RoomTeamId.CAMP_A) {
                desired = "清理残余的 " + defenders + " 名敌方士兵";
            } else {
                desired = "";
            }
            String previous = retreatSubtitleByPlayer.getOrDefault(playerId, "");
            if (desired.isBlank()) {
                if (!previous.isBlank()) {
                    NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent(RETREAT_PERSISTENT_SOURCE));
                    retreatSubtitleByPlayer.remove(playerId);
                }
                continue;
            }
            if (desired.equals(previous)) {
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.setPersistent(RETREAT_PERSISTENT_SOURCE, desired));
            retreatSubtitleByPlayer.put(playerId, desired);
        }
    }

    private void clearRetreatPersistentSubtitles() {
        for (UUID playerId : new LinkedHashSet<>(retreatSubtitleByPlayer.keySet())) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player != null) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent(RETREAT_PERSISTENT_SOURCE));
            }
        }
        retreatSubtitleByPlayer.clear();
    }

    private void syncDesperatePersistentSubtitles() {
        boolean active = desperateMode != DesperateMode.NONE;
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player == null) {
                desperateSubtitleByPlayer.remove(playerId);
                continue;
            }
            String desired = active ? resolveDesperatePersistentText() : "";
            String previous = desperateSubtitleByPlayer.getOrDefault(playerId, "");
            if (desired.equals(previous)) {
                continue;
            }
            if (desired.isBlank()) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent(DESPERATE_PERSISTENT_SOURCE));
                desperateSubtitleByPlayer.remove(playerId);
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.setPersistent(DESPERATE_PERSISTENT_SOURCE, desired));
            desperateSubtitleByPlayer.put(playerId, desired);
        }
    }

    private void clearDesperatePersistentSubtitles() {
        for (UUID playerId : new LinkedHashSet<>(desperateSubtitleByPlayer.keySet())) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player != null) {
                NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent(DESPERATE_PERSISTENT_SOURCE));
            }
        }
        desperateSubtitleByPlayer.clear();
    }

    private String resolveDesperatePersistentText() {
        if (desperateMode == DesperateMode.BACK_TO_WALL) {
            return hudStyledText("BT_LAST_STAND_PERSISTENT", "背水一战");
        }
        if (desperateMode == DesperateMode.BURN_BOATS) {
            return hudStyledText("BT_BURN_BOATS_PERSISTENT", "破釜沉舟");
        }
        return "";
    }

    private void syncRetreatActionbar(RoomDefinition room, long gameTick) {
        if (room == null || retreatDeadlineTick <= 0L || retreatStageIndex <= 0) {
            return;
        }
        int remainingSeconds = (int) Math.max(1L, (retreatDeadlineTick - gameTick + 19L) / 20L);
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player == null) {
                retreatCountdownLastShownSecondsByPlayer.remove(playerId);
                continue;
            }
            boolean inRetreatZone = runningState.getRoomTeams().get(playerId) == RoomTeamId.CAMP_B
                && !runningState.isInRespawnFlow(playerId)
                && player.isAlive()
                && !player.isSpectator()
                && RoomBoundaryPolygonSupport.isPointInsideBoundary(
                    player.getX(), player.getZ(), room.rushConfig().stageBoundary(retreatStageIndex));
            if (!inRetreatZone) {
                if (retreatCountdownLastShownSecondsByPlayer.remove(playerId) != null && player.connection != null) {
                    player.connection.send(new ClientboundClearTitlesPacket(true));
                }
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_RETREAT);
                continue;
            }
            int lastShown = retreatCountdownLastShownSecondsByPlayer.getOrDefault(playerId, -1);
            if (lastShown == remainingSeconds) {
                continue;
            }
            if (lastShown >= 0 && player.connection != null) {
                player.connection.send(new ClientboundClearTitlesPacket(true));
            }
            sendRetreatActionbar(player, remainingSeconds);
            retreatCountdownLastShownSecondsByPlayer.put(playerId, remainingSeconds);
        }
    }

    private void sendRetreatActionbar(ServerPlayer player, int remainingSeconds) {
        if (player == null) {
            return;
        }
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_RETREAT,
            ServerActionBarService.builder()
                .text("请在 ", ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(remainingSeconds))
                    .withStyle(style -> style.withColor(resolveCountdownColor(remainingSeconds)).withBold(true)))
                .text(" 秒内撤离至下一阶段", ChatFormatting.GRAY),
            30
        );
    }

    private void clearRetreatTitles() {
        for (UUID playerId : new LinkedHashSet<>(retreatCountdownLastShownSecondsByPlayer.keySet())) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player != null) {
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_RETREAT);
            }
        }
        retreatCountdownLastShownSecondsByPlayer.clear();
    }

    private void syncCountdownTitles(RoomDefinition room, long gameTick) {
        if (room == null || advanceCountdownEndTick <= 0L) {
            return;
        }
        int remainingSeconds = (int) Math.max(1L, (advanceCountdownEndTick - gameTick + 19L) / 20L);
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player == null) {
                continue;
            }
            int lastShown = advanceCountdownLastShownSecondsByPlayer.getOrDefault(playerId, -1);
            if (lastShown == remainingSeconds) {
                continue;
            }
            sendCountdownTitle(player, runningState.getRoomTeams().get(playerId), remainingSeconds, lastShown < 0);
            advanceCountdownLastShownSecondsByPlayer.put(playerId, remainingSeconds);
        }
    }

    private void sendCountdownTitle(ServerPlayer player, RoomTeamId teamId, int remainingSeconds, boolean firstShow) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
            firstShow ? TITLE_FADE_IN_TICKS : TITLE_REFRESH_FADE_IN_TICKS,
            firstShow ? TITLE_STAY_TICKS : TITLE_REFRESH_STAY_TICKS,
            firstShow ? TITLE_FADE_OUT_TICKS : TITLE_REFRESH_FADE_OUT_TICKS
        ));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal(String.valueOf(remainingSeconds))
                .withStyle(style -> style.withBold(true).withColor(resolveCountdownColor(remainingSeconds)))
        ));
        boolean attacker = teamId == RoomTeamId.CAMP_A;
        MutableComponent subtitle = Component.literal(attacker ? "准备炸毁下个阶段的通讯设施" : "准备死守通讯设施");
        subtitle.withStyle(style -> style.withColor(attacker ? SUBTITLE_COLOR_ATTACKER : SUBTITLE_COLOR_DEFENDER).withBold(true));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private void clearCountdownTitles() {
        for (UUID playerId : new LinkedHashSet<>(advanceCountdownLastShownSecondsByPlayer.keySet())) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player != null && player.connection != null) {
                player.connection.send(new ClientboundClearTitlesPacket(true));
            }
        }
        advanceCountdownLastShownSecondsByPlayer.clear();
    }

    private int resolveCountdownColor(int remainingSeconds) {
        if (remainingSeconds <= 1) {
            return COUNTDOWN_COLOR_RED;
        }
        if (remainingSeconds == 2) {
            return COUNTDOWN_COLOR_ORANGE;
        }
        if (remainingSeconds == 3) {
            return COUNTDOWN_COLOR_YELLOW;
        }
        if (remainingSeconds <= 5) {
            return COUNTDOWN_COLOR_GREEN;
        }
        return COUNTDOWN_COLOR_NORMAL;
    }

    private void playBombAlarmSound(RoomDefinition room, String objectiveCode, ResourceLocation soundId, float volume, float pitch) {
        if (room == null || objectiveCode == null || objectiveCode.isBlank() || soundId == null) {
            return;
        }
        RoomCapturePointDefinition point = findObjectivePoint(room, objectiveCode);
        if (point == null || point.centerPosition() == null) {
            return;
        }
        ServerLevel level = resolveExplosionLevel();
        if (level == null) {
            return;
        }
        RoomPosition center = point.centerPosition();
        double maxDistance = 50.0D;
        double maxDistanceSq = maxDistance * maxDistance;
        String soundIdStr = soundId.toString();
        for (ServerPlayer player : level.players()) {
            if (player == null || !player.isAlive()) {
                continue;
            }
            double dx = player.getX() - center.x();
            double dz = player.getZ() - center.z();
            double distSq = dx * dx + dz * dz;
            if (distSq > maxDistanceSq) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            float scaledVolume = (float) (volume * Math.max(0.0D, 1.0D - dist / maxDistance));
            if (scaledVolume <= 0.0F) {
                continue;
            }
            NetworkManager.sendToPlayer(player, ClientShortSoundPacket.oneShot(soundIdStr, scaledVolume, pitch));
        }
    }

    private void playBombWorldSound(RoomDefinition room, String objectiveCode, ResourceLocation soundId, float volume, float pitch) {
        if (room == null || objectiveCode == null || objectiveCode.isBlank() || soundId == null) {
            return;
        }
        RoomCapturePointDefinition point = findObjectivePoint(room, objectiveCode);
        if (point == null || point.centerPosition() == null) {
            return;
        }
        ServerLevel level = resolveExplosionLevel();
        if (level == null) {
            return;
        }
        RoomPosition center = point.centerPosition();
        level.playSound(
            null,
            center.x(),
            center.y(),
            center.z(),
            SoundEvent.createVariableRangeEvent(soundId),
            SoundSource.PLAYERS,
            Math.max(0.0F, volume),
            pitch
        );
    }

    private ServerPlayer resolveKillerPlayer(ServerPlayer deadPlayer, int killerEntityId) {
        if (deadPlayer == null || killerEntityId < 0 || deadPlayer.serverLevel() == null) {
            return null;
        }
        net.minecraft.world.entity.Entity entity = deadPlayer.serverLevel().getEntity(killerEntityId);
        return entity instanceof ServerPlayer killerPlayer && killerPlayer.isAlive() ? killerPlayer : null;
    }

    /** 击杀判定: 击杀者与受害者是否敌对(不同阵营; 任一无阵营视为非敌方)。 */
    private static boolean isEnemyTeam(RoomTeamId victimTeam, RoomTeamId killerTeam) {
        return victimTeam != null && killerTeam != null && victimTeam != killerTeam;
    }

    /** 地面控制/突破者: 阶段结束结算(突破模式), 结算后清空本阶段统计。 */
    private void triggerStageEndSettlement(RoomDefinition room) {
        if (room == null) {
            return;
        }
        org.mods.gd656conquest.server.integration.KilliconHonorBridge.triggerStageEnd(
            server,
            stageKills,
            runningState.getRoomTeams(),
            true
        );
        stageKills.clear();
    }

    private static double clampPercentage(double value) {
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private static long resolvePlantIntroWindowTicks(long requiredTicks, long finishWindowTicks) {
        if (requiredTicks <= 0L) {
            return 0L;
        }
        long introTicks = Math.min(20L, Math.max(8L, requiredTicks / 5L));
        return Math.max(0L, Math.min(introTicks, Math.max(0L, requiredTicks - finishWindowTicks)));
    }

    private static long resolveHoldFinishWindowTicks(long requiredTicks, BombHoldAction action) {
        if (requiredTicks <= 0L) {
            return 0L;
        }
        long desiredTicks = action == BombHoldAction.PLANT ? 20L : 24L;
        return Math.max(8L, Math.min(desiredTicks, requiredTicks));
    }

    private static double resolveBombFuseProgress(RoomDefinition room, long gameTick, BombSiteState state) {
        if (room == null || state == null || state.phase() != BombPhase.ARMED || state.armedAtTick() < 0L) {
            return 50.0D;
        }
        long fuseTicks = Math.max(1L, room.rushConfig().bombFuseTimeSeconds() * 20L);
        double elapsedRatio = Math.max(0.0D, Math.min(1.0D, (double) (gameTick - state.armedAtTick()) / (double) fuseTicks));
        return 50.0D + elapsedRatio * 50.0D;
    }

    private static int resolveRemainingHoldTenths(BombUseHold hold, int requiredSeconds, long gameTick) {
        if (hold == null || requiredSeconds <= 0) {
            return 0;
        }
        long requiredTicks = requiredSeconds * 20L;
        long elapsedTicks = Math.max(0L, gameTick - hold.startTick());
        long remainTicks = Math.max(0L, requiredTicks - elapsedTicks);
        return (int) Math.max(0L, (remainTicks + 1L) / 2L);
    }

    private static String formatTenths(int tenths) {
        int clampedTenths = Math.max(0, tenths);
        return (clampedTenths / 10) + "." + (clampedTenths % 10);
    }

    private static ChatFormatting holdCountdownColor(int tenths) {
        double seconds = Math.max(0, tenths) / 10.0D;
        if (seconds > 3.0D) {
            return ChatFormatting.AQUA;
        }
        if (seconds > 2.0D) {
            return ChatFormatting.YELLOW;
        }
        if (seconds > 1.0D) {
            return ChatFormatting.GOLD;
        }
        return ChatFormatting.RED;
    }

    private static double distanceSq(ServerPlayer player, RoomPosition position) {
        if (player == null || position == null) {
            return Double.MAX_VALUE;
        }
        return player.distanceToSqr(position.x(), position.y(), position.z());
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String stageCode(int stageIndex) {
        return stageIndex < 1 || stageIndex > 6 ? "" : String.valueOf((char) ('A' + stageIndex - 1));
    }

    private enum Phase {
        COMBAT,
        RETREAT,
        ADVANCE_COUNTDOWN
    }

    private enum BombPhase {
        SAFE,
        ARMED,
        EXPLODED
    }

    /** GD656Mods: Rush 背水一战/破釜沉舟 */
    private enum DesperateMode {
        NONE,
        BACK_TO_WALL,
        BURN_BOATS
    }

    private enum BombHoldAction {
        PLANT,
        DEFUSE
    }

    private void triggerBombExplosion(RoomDefinition room, String objectiveCode) {
        if (room == null || objectiveCode == null || objectiveCode.isBlank()) {
            return;
        }
        RoomCapturePointDefinition point = findObjectivePoint(room, objectiveCode);
        if (point == null || point.centerPosition() == null || room.rushConfig().bombExplosionRadius() <= 0.0D) {
            return;
        }
        ServerLevel level = resolveExplosionLevel();
        if (level == null) {
            return;
        }
        RoomPosition center = point.centerPosition();
        level.playSound(
            null,
            center.x(),
            center.y(),
            center.z(),
            SoundEvent.createVariableRangeEvent(SOUND_RUSH_BRICK_DESTRUCTION),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
        level.explode(
            null,
            center.x(),
            center.y(),
            center.z(),
            (float) room.rushConfig().bombExplosionRadius(),
            Level.ExplosionInteraction.NONE
        );
    }

    private ServerLevel resolveExplosionLevel() {
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = findOnlinePlayer(playerId);
            if (player != null && player.serverLevel() != null) {
                return player.serverLevel();
            }
        }
        return server.overworld();
    }

    private record BombSiteState(int stageIndex, BombPhase phase, long armedAtTick, UUID planterId) {
    }

    private static final class BombArmedAudioState {
        private final long armedAtTick;
        private long nextRegularAlarmTick;
        private long nextLeadoutLoopTick;
        private boolean finalLeadoutPlayed;
        private int leadoutLoopCount;

        private BombArmedAudioState(long armedAtTick, long nextRegularAlarmTick, long nextLeadoutLoopTick, boolean finalLeadoutPlayed, int leadoutLoopCount) {
            this.armedAtTick = armedAtTick;
            this.nextRegularAlarmTick = nextRegularAlarmTick;
            this.nextLeadoutLoopTick = nextLeadoutLoopTick;
            this.finalLeadoutPlayed = finalLeadoutPlayed;
            this.leadoutLoopCount = leadoutLoopCount;
        }
    }

    private static final class BombHoldAudioState {
        private boolean introPlayed;
        private boolean beepLoopActive;
        private boolean finishPlayed;
    }

    private record PendingBombExplosion(String objectiveCode, long triggerTick) {
    }

    private record BombUseHold(String objectiveCode, RoomTeamId teamId, BombHoldAction action, long startTick) {
    }

    private final class RushObjectiveRuntimeView implements RoomObjectiveRuntimeView {
        public int currentStageIndex() {
            return RushGameplayRuntime.this.currentStageIndex;
        }

        public int maxStageCount() {
            return Math.max(1, configuredStageCount);
        }

        @Override
        public RoomTeamId ownerOf(String objectiveCode) {
            return objectiveOwnerByCode.get(normalizeCode(objectiveCode));
        }

        @Override
        public RoomTeamId drivingTeamOf(String objectiveCode) {
            return objectiveDrivingByCode.get(normalizeCode(objectiveCode));
        }

        @Override
        public double progressOf(String objectiveCode) {
            return objectiveProgressByCode.getOrDefault(normalizeCode(objectiveCode), 0.0D);
        }

        @Override
        public int teamCount(String objectiveCode, RoomTeamId teamId) {
            String normalized = normalizeCode(objectiveCode);
            if (teamId == RoomTeamId.CAMP_A) {
                return campACountByCode.getOrDefault(normalized, 0);
            }
            if (teamId == RoomTeamId.CAMP_B) {
                return campBCountByCode.getOrDefault(normalized, 0);
            }
            return 0;
        }

        @Override
        public boolean isFullyControlledBy(String objectiveCode, RoomTeamId teamId) {
            return teamId != null
                && ownerOf(objectiveCode) == teamId
                && drivingTeamOf(objectiveCode) == null
                && (teamId == RoomTeamId.CAMP_A ? progressOf(objectiveCode) >= 100.0D : progressOf(objectiveCode) <= 0.0D);
        }
    }

    private final class RushObjectiveSpawnPolicy implements RoomObjectiveSpawnPolicy {
        @Override
        public boolean canDeploy(RoomObjectiveRuntimeView runtimeView, RoomCapturePointDefinition point, RoomTeamId teamId, String objectiveCode) {
            if (!(runtimeView instanceof RushObjectiveRuntimeView rushView) || point == null || teamId == null) {
                return false;
            }
            int currentStage = rushView.currentStageIndex();
            if (currentStage <= 0) {
                return false;
            }
            int pointStage = point.stageIndex();
            if (teamId == RoomTeamId.CAMP_A) {
                if (pointStage != currentStage && pointStage != currentStage - 1) {
                    return false;
                }
                return runtimeView.ownerOf(objectiveCode) == RoomTeamId.CAMP_A;
            }
            if (pointStage == currentStage && runtimeView.ownerOf(objectiveCode) == RoomTeamId.CAMP_B) {
                return true;
            }
            if (pointStage == currentStage + 1) {
                return true;
            }
            return false;
        }

        @Override
        public List<RoomPosition> resolveCandidateSpawns(
            RoomObjectiveRuntimeView runtimeView,
            RoomCapturePointDefinition point,
            RoomTeamId teamId,
            String objectiveCode,
            boolean enemyNearby
        ) {
            if (!canDeploy(runtimeView, point, teamId, objectiveCode) || point == null || teamId == null) {
                return List.of();
            }
            if (teamId == RoomTeamId.CAMP_A) {
                return point.innerSpawns();
            }
            if (runtimeView instanceof RushObjectiveRuntimeView rushView && point.stageIndex() == rushView.currentStageIndex() + 1) {
                return point.innerSpawns();
            }
            return point.outerSpawns(teamId);
        }

        @Override
        public double objectiveThreatRadiusSq(RoomCapturePointDefinition point, RoomTeamId teamId, String objectiveCode) {
            return 0.0D;
        }
    }
}
