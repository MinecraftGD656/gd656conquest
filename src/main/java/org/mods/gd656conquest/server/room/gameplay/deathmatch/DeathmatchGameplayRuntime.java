package org.mods.gd656conquest.server.room.gameplay.deathmatch;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.match.MatchResultType;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryHudProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomAutoBalanceWindowSnapshot;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayVehicleDeployRuleProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudBuildContext;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudElement;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DeathmatchGameplayRuntime implements
    IRoomGameplayRuntime,
    RoomGameplayBoundaryHudProvider,
    RoomGameplayBoundaryPolicyProvider,
    RoomGameplayPresentationProvider,
    RoomGameplayDeployPresentationProvider,
    RoomGameplayVehicleDeployRuleProvider {
    private final MinecraftServer server;
    private final RunningRuntimeState runningState;
     private final org.mods.gd656conquest.server.room.gameplay.RoomHudSubtitleBroadcastService subtitleBroadcastService = new org.mods.gd656conquest.server.room.gameplay.RoomHudSubtitleBroadcastService();
    private long enteredTick = -1L;
    private int campAScore;
    private int campBScore;
    private RoomTeamId previousLeader;
    private boolean campA50Sent;
    private boolean campB50Sent;
    private boolean campA80Sent;
    private boolean campB80Sent;
    private boolean campALeading20Sent;
    private boolean campBLeading20Sent;
    private RoomMatchEndRequest pendingEndRequest;

    public DeathmatchGameplayRuntime(MinecraftServer server, RunningRuntimeState runningState) {
        this.server = Objects.requireNonNull(server, "server");
        this.runningState = Objects.requireNonNull(runningState, "runningState");
    }

    @Override
    public boolean supportsVehicleDeploy(RoomDefinition room, RunningRuntimeState state) {
        return room != null && room.lifecycleState() == RoomLifecycleState.RUNNING;
    }

    @Override
    public void onEnterMainPhase(RoomDefinition room, long gameTick) {
        enteredTick = gameTick + RoomPlayerRespawnService.initialCombatUnlockDelayTicks(room);
        campAScore = 0;
        campBScore = 0;
        previousLeader = null;
        campA50Sent = false;
        campB50Sent = false;
        campA80Sent = false;
        campB80Sent = false;
        campALeading20Sent = false;
        campBLeading20Sent = false;
        pendingEndRequest = null;
    }

    @Override
    public RoomAutoBalanceWindowSnapshot autoBalanceWindowSnapshot(RoomDefinition room, long gameTick) {
        if (isSoloMode(room)) {
            return RoomAutoBalanceWindowSnapshot.locked("混战模式不支持自动换队");
        }
        if (matchDurationSeconds(gameTick) < 300) {
            return RoomAutoBalanceWindowSnapshot.locked("主对局前 300 秒不自动换队");
        }
        if (room == null || room.arenaConfig().targetScore() <= 0) {
            return RoomAutoBalanceWindowSnapshot.allowed();
        }
        double targetScore = Math.max(1, room.arenaConfig().targetScore());
        if ((campAScore / targetScore) > 0.75D || (campBScore / targetScore) > 0.75D) {
            return RoomAutoBalanceWindowSnapshot.locked("死斗模式阵营分数已超过 75%");
        }
        return RoomAutoBalanceWindowSnapshot.allowed();
    }

    @Override
    public void tick(RoomDefinition room, long gameTick) {
        if (pendingEndRequest != null || room == null || enteredTick < 0L) {
            return;
        }
        int durationMinutes = room.arenaConfig().matchDurationMinutes();
        if (durationMinutes <= 0) {
            return;
        }
        long endTick = enteredTick + durationMinutes * 60L * 20L;
        if (gameTick >= endTick) {
            pendingEndRequest = buildTimeLimitEndRequest(room);
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
        if (room == null || playerId == null || reason != RoomPlayerInvalidationReason.DEATH || pendingEndRequest != null) {
            return;
        }
        ServerPlayer deadPlayer = runningState.getRoomPlayers().contains(playerId)
            ? findOnlinePlayer(playerId)
            : null;
        ServerPlayer killerPlayer = resolveKillerPlayer(deadPlayer, killerEntityId);
        if (killerPlayer == null || killerPlayer.getUUID().equals(playerId)) {
            return;
        }
        if (isSoloMode(room)) {
            return;
        }
        RoomTeamId killerTeam = runningState.getRoomTeams().get(killerPlayer.getUUID());
        if (killerTeam == null || killerTeam == teamId) {
            return;
        }
        int target = room.arenaConfig().targetScore();
        if (killerTeam == RoomTeamId.CAMP_A) {
            campAScore++;
            if (campAScore >= target) {
                pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_A, room.gameplayTeamDisplayName(RoomTeamId.CAMP_A) + "达到目标得分");
            } else {
                if (!campA50Sent && campAScore >= target * 0.5f) {
                    campA50Sent = true;
                    subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campa_50", RoomTeamId.CAMP_A, "我军累计分数已经过半", "敌军累计分数已经过半");
                }
                if (!campA80Sent && campAScore >= target * 0.8f) {
                    campA80Sent = true;
                    subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campa_80", RoomTeamId.CAMP_A, "我军累计分数仅剩20%", "敌军累计分数仅剩20%");
                }
                if (campAScore > campBScore && previousLeader != RoomTeamId.CAMP_A) {
                    if (previousLeader == RoomTeamId.CAMP_B) {
                        subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campa_overtake_" + campAScore, RoomTeamId.CAMP_A, "我军已经反超比分", "敌军已经反超比分");
                    }
                    previousLeader = RoomTeamId.CAMP_A;
                }
                if (!campALeading20Sent && (campAScore - campBScore) >= target * 0.2f) {
                    campALeading20Sent = true;
                    subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campa_lead20", RoomTeamId.CAMP_A, "我军保持领先优势", "敌军占据了上风");
                }
            }
        } else if (killerTeam == RoomTeamId.CAMP_B) {
            campBScore++;
            if (campBScore >= target) {
                pendingEndRequest = RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), RoomTeamId.CAMP_B, room.gameplayTeamDisplayName(RoomTeamId.CAMP_B) + "达到目标得分");
            } else {
                if (!campB50Sent && campBScore >= target * 0.5f) {
                    campB50Sent = true;
                    subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campb_50", RoomTeamId.CAMP_B, "我军累计分数已经过半", "敌军累计分数已经过半");
                }
                if (!campB80Sent && campBScore >= target * 0.8f) {
                    campB80Sent = true;
                    subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campb_80", RoomTeamId.CAMP_B, "我军累计分数仅剩20%", "敌军累计分数仅剩20%");
                }
                if (campBScore > campAScore && previousLeader != RoomTeamId.CAMP_B) {
                    if (previousLeader == RoomTeamId.CAMP_A) {
                        subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campb_overtake_" + campBScore, RoomTeamId.CAMP_B, "我军已经反超比分", "敌军已经反超比分");
                    }
                    previousLeader = RoomTeamId.CAMP_B;
                }
                if (!campBLeading20Sent && (campBScore - campAScore) >= target * 0.2f) {
                    campBLeading20Sent = true;
                    subtitleBroadcastService.broadcastPerspectiveOnce(runningState, "dm_campb_lead20", RoomTeamId.CAMP_B, "我军保持领先优势", "敌军占据了上风");
                }
            }
        }
    }

    @Override
    public Optional<RoomMatchEndRequest> pollMatchEndRequest() {
        RoomMatchEndRequest request = pendingEndRequest;
        pendingEndRequest = null;
        return Optional.ofNullable(request);
    }

    @Override
    public RoomGameplayHudState buildHudState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        boolean inDeployFlow,
        long gameTick
    ) {
        if (room == null) {
            return RoomGameplayHudState.defaults();
        }
        if (isSoloMode(room)) {
            return buildSoloHudState(room, state, player, inDeployFlow, gameTick);
        }
        boolean initialLockActive = state != null && player != null && state.isInitialMovementLockActive(player.getUUID(), gameTick);
        RoomTeamId selfTeam = state == null || player == null ? null : state.getRoomTeams().get(player.getUUID());
        return new RoomGameplayHudState(
            campAScore,
            campBScore,
            matchDurationSeconds(gameTick),
            "SCORE_TARGET",
            room.arenaConfig().targetScore(),
            room.arenaConfig().targetScore(),
            resolveRemainSeconds(room, state, gameTick),
            resolveTopStatus(room, inDeployFlow),
            "击杀敌人为团队夺得更高的分数",
            "-",
            room.lifecycleState() == org.mods.gd656conquest.server.room.model.RoomLifecycleState.ENDING
                ? "ENDING"
                : initialLockActive ? "INITIAL_STAGE_BEGIN" : buildAudioState(room, selfTeam),
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
    public int boundaryPenaltySeconds(RoomDefinition room) {
        return room == null ? 0 : room.gameplayBoundaryPenaltySeconds();
    }

    @Override
    public boolean hasConfiguredBoundary(RoomDefinition room, RunningRuntimeState state) {
        return RoomBoundaryRuleEvaluator.hasConfiguredBoundary(resolveConfiguredBoundaries(room));
    }

    @Override
    public List<List<RoomPosition>> resolveAllowedBoundaries(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId
    ) {
        if (room == null) {
            return List.of();
        }
        List<List<RoomPosition>> boundaries = new ArrayList<>();
        if (isSoloMode(room)) {
            addBoundaryIfValid(boundaries, room.deathmatchConfig().soloBoundary());
            return boundaries;
        }
        if (teamId == null) {
            return List.of();
        }
        if (teamId == RoomTeamId.CAMP_A) {
            addBoundaryIfValid(boundaries, room.arenaConfig().campABoundary());
        } else if (teamId == RoomTeamId.CAMP_B) {
            addBoundaryIfValid(boundaries, room.arenaConfig().campBBoundary());
        }
        return boundaries;
    }

    @Override
    public void collectBoundaryElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        if (elements == null || context == null || context.room() == null) {
            return;
        }
        if (isSoloMode(context.room())) {
            appendBoundaryRows(elements, context.room().deathmatchConfig().soloBoundary(), "N", "dm_solo");
            return;
        }
        appendBoundaryRows(elements, context.room().arenaConfig().campABoundary(), "T", "dm_camp_a");
        appendBoundaryRows(elements, context.room().arenaConfig().campBBoundary(), "S", "dm_camp_b");
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
        RoomPosition lockedTarget = state.getRespawnTargetPositions().get(player.getUUID());
        if (lockedTarget != null) {
            return lockedTarget;
        }
        if (isSoloMode(room)) {
            return new RoomSpawnCandidateSelectionService(player.server).pickSpawn(
                room.deathmatchConfig().soloSpawnPositions(),
                state,
                null
            );
        }
        RoomTeamId teamId = state.getRoomTeams().get(player.getUUID());
        if (teamId == RoomTeamId.CAMP_A) {
            return new RoomSpawnCandidateSelectionService(player.server).pickSpawn(room.arenaConfig().campASpawnGroup(), state, teamId);
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return new RoomSpawnCandidateSelectionService(player.server).pickSpawn(room.arenaConfig().campBSpawnGroup(), state, teamId);
        }
        return room.commonConfig().queueSpawn();
    }

    @Override
    public RoomPosition resolveRespawnHoldingPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        if (state != null && player != null) {
            RoomPosition lockedTarget = state.getRespawnTargetPositions().get(player.getUUID());
            if (lockedTarget != null) {
                return lockedTarget;
            }
        }
        return resolveRespawnAnchorPosition(room, state, player);
    }

    @Override
    public RoomViewPosition resolveDeployViewPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployViewRequest request
    ) {
        return room == null ? null : room.arenaConfig().deployViewPosition();
    }

    @Override
    public RoomPosition resolveTeamDeployIconPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId
    ) {
        if (room != null && isSoloMode(room)) {
            RoomPosition iconPosition = room.deathmatchConfig().soloDeployIconPosition();
            if (iconPosition != null) {
                return iconPosition;
            }
            return room.deathmatchConfig().soloSpawnPositions().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }
        if (room == null || teamId == null) {
            return null;
        }
        RoomSpawnGroupConfig group = teamId == RoomTeamId.CAMP_A
            ? room.arenaConfig().campASpawnGroup()
            : room.arenaConfig().campBSpawnGroup();
        return firstSpawn(group);
    }

    @Override
    public Integer resolveRespawnDelaySeconds(RoomDefinition room, RunningRuntimeState state) {
        return room == null ? null : room.arenaConfig().respawnSeconds();
    }

    private ServerPlayer findOnlinePlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(playerId);
    }

    private ServerPlayer resolveKillerPlayer(ServerPlayer deadPlayer, int killerEntityId) {
        if (killerEntityId < 0) {
            return null;
        }
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer candidate = server.getPlayerList().getPlayer(playerId);
            if (candidate != null && candidate.getId() == killerEntityId) {
                return candidate;
            }
        }
        return null;
    }

    private int resolveRemainSeconds(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room != null
            && room.lifecycleState() == org.mods.gd656conquest.server.room.model.RoomLifecycleState.ENDING
            && state != null
            && state.getResultSequenceEndTick() > 0) {
            return (int) Math.max(0L, (state.getResultSequenceEndTick() - gameTick + 19L) / 20L);
        }
        if (room == null || enteredTick < 0L || room.arenaConfig().matchDurationMinutes() <= 0) {
            return 0;
        }
        long endTick = enteredTick + room.arenaConfig().matchDurationMinutes() * 60L * 20L;
        return (int) Math.max(0L, (endTick - gameTick + 19L) / 20L);
    }

    private String resolveTopStatus(RoomDefinition room, boolean inDeployFlow) {
        if (room == null) {
            return "-";
        }
        if (room.lifecycleState() == org.mods.gd656conquest.server.room.model.RoomLifecycleState.ENDING) {
            return "结算中";
        }
        if (inDeployFlow) {
            return "重生中";
        }
        return "进行中";
    }

    private RoomMatchEndRequest buildTimeLimitEndRequest(RoomDefinition room) {
        if (isSoloMode(room)) {
            return buildSoloTimeLimitEndRequest();
        }
        RoomTeamId winner = null;
        if (campAScore > campBScore) {
            winner = RoomTeamId.CAMP_A;
        } else if (campBScore > campAScore) {
            winner = RoomTeamId.CAMP_B;
        }
        String reason = winner == null
            ? "达到时长，判定平局"
            : "达到时长，" + safeTeamName(room, winner) + "得分领先";
        return RoomMatchEndRequest.byTeam(runningState.getRoomPlayers(), runningState.getRoomTeams(), winner, reason);
    }

    private String buildAudioState(RoomDefinition room, RoomTeamId selfTeam) {
        if (!isScoreNearEnd(room)) {
            return "RUNNING";
        }
        RoomTeamId leadingTeam = resolveLeadingTeam();
        if (leadingTeam == null) {
            return "FINAL_OBJECTIVE";
        }
        if (selfTeam == leadingTeam) {
            return "FINAL_OBJECTIVE_VICTORY";
        }
        if (selfTeam == RoomTeamId.CAMP_A || selfTeam == RoomTeamId.CAMP_B) {
            return "FINAL_OBJECTIVE_DEFEAT";
        }
        return "FINAL_OBJECTIVE";
    }

    private boolean isScoreNearEnd(RoomDefinition room) {
        if (room == null || room.arenaConfig().targetScore() <= 0) {
            return false;
        }
        double threshold = room.arenaConfig().targetScore() * 0.9D;
        return campAScore >= threshold || campBScore >= threshold;
    }

    private RoomTeamId resolveLeadingTeam() {
        if (campAScore > campBScore) {
            return RoomTeamId.CAMP_A;
        }
        if (campBScore > campAScore) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private String safeTeamName(RoomDefinition room, RoomTeamId teamId) {
        if (teamId == null) {
            return "-";
        }
        if (room == null) {
            return teamId == RoomTeamId.CAMP_A ? "CampA" : "CampB";
        }
        return room.gameplayTeamDisplayName(teamId);
    }

    private List<List<RoomPosition>> resolveConfiguredBoundaries(RoomDefinition room) {
        if (room == null) {
            return List.of();
        }
        List<List<RoomPosition>> boundaries = new ArrayList<>();
        if (isSoloMode(room)) {
            addBoundaryIfValid(boundaries, room.deathmatchConfig().soloBoundary());
            return boundaries;
        }
        addBoundaryIfValid(boundaries, room.arenaConfig().campABoundary());
        addBoundaryIfValid(boundaries, room.arenaConfig().campBBoundary());
        return boundaries;
    }

    private void addBoundaryIfValid(List<List<RoomPosition>> boundaries, List<RoomPosition> boundary) {
        if (boundary != null && boundary.size() >= 3) {
            boundaries.add(boundary);
        }
    }

    private void appendBoundaryRows(
        List<RoomDeployHudElement> elements,
        List<RoomPosition> boundary,
        String teamToken,
        String polygonId
    ) {
        if (elements == null || boundary == null || boundary.size() < 3) {
            return;
        }
        for (RoomPosition position : boundary) {
            if (position != null) {
                elements.add(RoomDeployHudElement.boundary(teamToken, position, polygonId));
            }
        }
    }

    private RoomPosition firstSpawn(RoomSpawnGroupConfig group) {
        if (group == null || !group.enabled() || group.spawns().isEmpty()) {
            return null;
        }
        for (RoomPosition spawn : group.spawns()) {
            if (spawn != null) {
                return spawn;
            }
        }
        return null;
    }

    private RoomGameplayHudState buildSoloHudState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        boolean inDeployFlow,
        long gameTick
    ) {
        UUID selfId = player == null ? null : player.getUUID();
        int selfKills = selfId == null ? 0 : killOf(selfId);
        List<UUID> leaders = resolveLeaders();
        boolean selfIsLeader = selfId != null && leaders.size() == 1 && selfId.equals(leaders.get(0));
        UUID displayLeader = leaders.isEmpty()
            ? null
            : leaders.get((int) Math.floorMod(gameTick / 20L, leaders.size()));
        String payload = "DMFFA|self=" + encode(player == null ? "-" : player.getScoreboardName())
            + "|selfkills=" + selfKills
            + "|leader=" + encode(resolvePlayerName(displayLeader))
            + "|leaderkills=" + (displayLeader == null ? 0 : killOf(displayLeader))
            + "|selflead=" + (selfIsLeader ? 1 : 0);
        boolean initialLockActive = state != null && player != null && state.isInitialMovementLockActive(selfId, gameTick);
        return new RoomGameplayHudState(
            0,
            0,
            matchDurationSeconds(gameTick),
            "DEATHMATCH_SOLO",
            0,
            0,
            resolveRemainSeconds(room, state, gameTick),
            resolveTopStatus(room, inDeployFlow),
            "击败所有对手，时间结束时击杀数最高者获胜",
            "-",
            room.lifecycleState() == RoomLifecycleState.ENDING ? "ENDING" : initialLockActive ? "INITIAL_STAGE_BEGIN" : "RUNNING",
            payload
        );
    }

    private RoomMatchEndRequest buildSoloTimeLimitEndRequest() {
        List<UUID> sorted = sortPlayersByKillDesc();
        if (sorted.isEmpty()) {
            return RoomMatchEndRequest.forAll(runningState.getRoomPlayers(), MatchResultType.DRAW, "达到时长，判定平局");
        }
        List<UUID> top = resolveLeaders(sorted);
        Map<UUID, Integer> rankByPlayer = resolveKillRanks(sorted);
        Map<UUID, RoomMatchEndRequest.PlayerResultEntry> results = new LinkedHashMap<>();
        for (UUID playerId : runningState.getRoomPlayers()) {
            boolean isTop = top.contains(playerId);
            MatchResultType result = top.size() == 1
                ? (isTop ? MatchResultType.VICTORY : MatchResultType.DEFEAT)
                : (isTop ? MatchResultType.DRAW : MatchResultType.DEFEAT);
            results.put(playerId, new RoomMatchEndRequest.PlayerResultEntry(
                result,
                resolveRankExtra(rankByPlayer.getOrDefault(playerId, 1))
            ));
        }
        String reason = top.size() == 1
            ? "达到时长，当前击杀数第一的玩家获胜"
            : "达到时长，多名玩家击杀数并列第一，判定平局";
        return new RoomMatchEndRequest(runningState.getRoomPlayers(), results, reason);
    }

    private List<UUID> resolveLeaders() {
        return resolveLeaders(sortPlayersByKillDesc());
    }

    private List<UUID> resolveLeaders(List<UUID> sorted) {
        if (sorted.isEmpty()) {
            return List.of();
        }
        int highestKill = killOf(sorted.get(0));
        List<UUID> leaders = new ArrayList<>();
        for (UUID playerId : sorted) {
            if (killOf(playerId) != highestKill) {
                break;
            }
            leaders.add(playerId);
        }
        return leaders;
    }

    private List<UUID> sortPlayersByKillDesc() {
        List<UUID> players = new ArrayList<>(runningState.getRoomPlayers());
        players.sort(Comparator.comparingInt(this::killOf).reversed().thenComparing(UUID::toString));
        return players;
    }

    private Map<UUID, Integer> resolveKillRanks(List<UUID> sortedByKillDesc) {
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        int rank = 0;
        int previousKills = Integer.MAX_VALUE;
        for (int index = 0; index < sortedByKillDesc.size(); index++) {
            UUID playerId = sortedByKillDesc.get(index);
            int kills = killOf(playerId);
            if (kills < previousKills) {
                rank = index + 1;
                previousKills = kills;
            }
            ranks.put(playerId, rank);
        }
        return ranks;
    }

    private int resolveRankExtra(int rank) {
        int[] rewards = {10, 9, 8, 7, 6, 6, 5, 4, 3, 2};
        return rank <= rewards.length ? rewards[Math.max(0, rank - 1)] : 2;
    }

    private int killOf(UUID playerId) {
        return KilliconScoreboardBridge.getPlayerSnapshot(runningState, playerId).kill();
    }

    private String resolvePlayerName(UUID playerId) {
        ServerPlayer player = findOnlinePlayer(playerId);
        return player == null ? "-" : player.getScoreboardName();
    }

    private boolean isSoloMode(RoomDefinition room) {
        return room != null && room.deathmatchConfig().isSoloMode();
    }

    private String encode(String value) {
        return value == null || value.isBlank() ? "-" : value.replace("|", "/").replace("=", ":").trim();
    }
}
