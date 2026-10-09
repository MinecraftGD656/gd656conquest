package org.mods.gd656conquest.server.room.gameplay.deathmatch;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryHudProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DeathmatchGameplayRuntime implements
    IRoomGameplayRuntime,
    RoomGameplayBoundaryHudProvider,
    RoomGameplayBoundaryPolicyProvider,
    RoomGameplayPresentationProvider,
    RoomGameplayDeployPresentationProvider {
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
        RoomTeamId killerTeam = runningState.getRoomTeams().get(killerPlayer.getUUID());
        if (killerTeam == null || killerTeam == teamId) {
            return;
        }
        int target = room.arenaConfig().targetScore();
        if (killerTeam == RoomTeamId.CAMP_A) {
            campAScore++;
            if (campAScore >= target) {
                pendingEndRequest = new RoomMatchEndRequest(RoomTeamId.CAMP_A, room.gameplayTeamDisplayName(RoomTeamId.CAMP_A) + "达到目标得分");
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
                pendingEndRequest = new RoomMatchEndRequest(RoomTeamId.CAMP_B, room.gameplayTeamDisplayName(RoomTeamId.CAMP_B) + "达到目标得分");
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
        if (room == null || teamId == null) {
            return List.of();
        }
        List<List<RoomPosition>> boundaries = new ArrayList<>();
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
        if (campAScore > campBScore) {
            return new RoomMatchEndRequest(RoomTeamId.CAMP_A, "达到时长，" + safeTeamName(room, RoomTeamId.CAMP_A) + "得分领先");
        }
        if (campBScore > campAScore) {
            return new RoomMatchEndRequest(RoomTeamId.CAMP_B, "达到时长，" + safeTeamName(room, RoomTeamId.CAMP_B) + "得分领先");
        }
        return new RoomMatchEndRequest((RoomTeamId) null, "达到时长，判定平局");
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
}
