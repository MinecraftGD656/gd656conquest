package org.mods.gd656conquest.server.room.gameplay.gunmaster;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
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
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudBuildContext;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudElement;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterLoadoutService;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterWeaponPoolRules;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GunmasterGameplayRuntime implements
    IRoomGameplayRuntime,
    RoomGameplayBoundaryHudProvider,
    RoomGameplayBoundaryPolicyProvider,
    RoomGameplayPresentationProvider,
    RoomGameplayDeployPresentationProvider {
    private final MinecraftServer gunmasterServer;
    private final RunningRuntimeState gunmasterRunningState;
    private final RoomGunmasterLoadoutService gunmasterLoadoutService;
    private long enteredTick = -1L;
    private RoomMatchEndRequest pendingEndRequest;

    public GunmasterGameplayRuntime(MinecraftServer server, RunningRuntimeState runningState) {
        this.gunmasterServer = Objects.requireNonNull(server, "server");
        this.gunmasterRunningState = Objects.requireNonNull(runningState, "runningState");
        this.gunmasterLoadoutService = new RoomGunmasterLoadoutService(server);
    }

    @Override
    public void onEnterMainPhase(RoomDefinition room, long gameTick) {
        enteredTick = gameTick + RoomPlayerRespawnService.initialCombatUnlockDelayTicks(room);
        pendingEndRequest = null;
        gunmasterRunningState.getGunmasterStageIndexByPlayer().clear();
        gunmasterRunningState.getGunmasterStageKillsByPlayer().clear();
        gunmasterRunningState.getGunmasterMatchWeaponSequenceIds().clear();
        List<String> sequence = buildWeaponSequence(room);
        gunmasterRunningState.getGunmasterMatchWeaponSequenceIds().addAll(sequence);
        for (UUID playerId : gunmasterRunningState.getRoomPlayers()) {
            gunmasterRunningState.getGunmasterStageIndexByPlayer().put(playerId, 0);
            gunmasterRunningState.getGunmasterStageKillsByPlayer().put(playerId, 0);
        }
    }

    @Override
    public void tick(RoomDefinition room, long gameTick) {
        if (pendingEndRequest != null || room == null || enteredTick < 0L) {
            return;
        }
        int durationMinutes = room.gunmasterConfig().matchDurationMinutes();
        if (durationMinutes <= 0) {
            return;
        }
        long endTick = enteredTick + durationMinutes * 60L * 20L;
        if (gameTick < endTick) {
            return;
        }
        pendingEndRequest = buildTimeLimitEndRequest(room);
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
        if (pendingEndRequest != null || room == null || playerId == null || reason != RoomPlayerInvalidationReason.DEATH || killerEntityId < 0) {
            return;
        }
        ServerPlayer killer = resolveKillerPlayer(killerEntityId);
        if (killer == null) {
            return;
        }
        UUID killerId = killer.getUUID();
        if (!gunmasterRunningState.getRoomPlayers().contains(killerId) || killerId.equals(playerId)) {
            return;
        }
        if (!room.gunmasterConfig().isSoloGrouping()) {
            RoomTeamId killerTeam = gunmasterRunningState.getRoomTeams().get(killerId);
            if (killerTeam == null || killerTeam == teamId) {
                return;
            }
        }
        advancePlayerStage(room, killerId);
    }

    @Override
    public Optional<RoomMatchEndRequest> pollMatchEndRequest() {
        RoomMatchEndRequest request = pendingEndRequest;
        pendingEndRequest = null;
        return Optional.ofNullable(request);
    }

    @Override
    public RoomPosition resolveRespawnAnchorPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        if (room != null && room.gunmasterConfig().isSoloGrouping()) {
            RoomPosition lockedTarget = state == null || player == null ? null : state.getRespawnTargetPositions().get(player.getUUID());
            if (lockedTarget != null) {
                return lockedTarget;
            }
            return pickSoloSpawn(room, state, player);
        }
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
        if (room != null && room.gunmasterConfig().isSoloGrouping()) {
            return resolveRespawnAnchorPosition(room, state, player);
        }
        if (state != null && player != null) {
            RoomPosition lockedTarget = state.getRespawnTargetPositions().get(player.getUUID());
            if (lockedTarget != null) {
                return lockedTarget;
            }
        }
        return resolveRespawnAnchorPosition(room, state, player);
    }

    @Override
    public RoomPosition resolveTeamDeployIconPosition(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        if (room != null && room.gunmasterConfig().isSoloGrouping()) {
            return room.gunmasterConfig().soloSpawnPositions().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
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
    public RoomViewPosition resolveDeployViewPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployViewRequest request
    ) {
        return room == null ? null : room.arenaConfig().deployViewPosition();
    }

    @Override
    public Integer resolveRespawnDelaySeconds(RoomDefinition room, RunningRuntimeState state) {
        return room == null ? null : room.arenaConfig().respawnSeconds();
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
        appendBoundaryRows(elements, context.room().arenaConfig().campABoundary(), "T", "gm_camp_a");
        appendBoundaryRows(elements, context.room().arenaConfig().campBBoundary(), "S", "gm_camp_b");
    }

    private String buildHudExtraPayload(
        boolean soloGrouping,
        int currentStageIndex,
        int currentSequenceStageCount,
        int[] stageTotalCounts,
        int[] stageEnemyCounts,
        boolean finalEnemyAlert
    ) {
        return String.format(
            Locale.ROOT,
            "GMHUD|solo=%d|next=%s|stage=%d|sequence=%d|finalalert=%d|counts=%s|enemy=%s",
            soloGrouping ? 1 : 0,
            encode(resolveNextWeaponName(currentStageIndex)),
            currentStageIndex + 1,
            Math.max(1, currentSequenceStageCount),
            finalEnemyAlert ? 1 : 0,
            join(stageTotalCounts),
            join(stageEnemyCounts)
        );
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
        boolean soloGrouping = room.gunmasterConfig().isSoloGrouping();
        int stageCount = Math.max(1, room.gunmasterConfig().enabledMiddleWeaponCount());
        int actualSequenceStageCount = Math.max(1, gunmasterRunningState.getGunmasterMatchWeaponSequenceIds().size());
        int[] stageTotalCounts = new int[stageCount];
        int[] stageEnemyCounts = new int[stageCount];
        int actualCurrentStageIndex = Math.max(0, state == null || player == null ? 0 : state.getGunmasterStageIndexByPlayer().getOrDefault(player.getUUID(), 0));
        int currentStageIndex = remapHudStageIndex(actualCurrentStageIndex, actualSequenceStageCount, stageCount);
        if (state != null) {
            for (UUID playerId : state.getRoomPlayers()) {
                RoomTeamId teamId = state.getRoomTeams().get(playerId);
                int sequenceStageIndex = Math.max(0, state.getGunmasterStageIndexByPlayer().getOrDefault(playerId, 0));
                int stageIndex = remapHudStageIndex(sequenceStageIndex, actualSequenceStageCount, stageCount);
                stageTotalCounts[stageIndex]++;
                if (soloGrouping) {
                    if (player == null || !playerId.equals(player.getUUID())) {
                        stageEnemyCounts[stageIndex]++;
                    }
                } else if (selfTeam != null && teamId != null && teamId != selfTeam) {
                    stageEnemyCounts[stageIndex]++;
                }
            }
        }
        boolean finalEnemyAlert = stageEnemyCounts[Math.max(0, stageCount - 1)] > 0;
        return new RoomGameplayHudState(
            0,
            0,
            matchDurationSeconds(gameTick),
            "GUNMASTER",
            stageCount,
            stageCount,
            resolveRemainSeconds(room, state, gameTick),
            resolveTopStatus(room, inDeployFlow),
            initialLockActive ? "击杀敌军升级枪械" : "击杀敌军升级枪械",
            "-",
            room.lifecycleState() == RoomLifecycleState.ENDING ? "ENDING" : "RUNNING",
            buildHudExtraPayload(soloGrouping, currentStageIndex, actualSequenceStageCount, stageTotalCounts, stageEnemyCounts, finalEnemyAlert)
        );
    }

    private int remapHudStageIndex(int actualStageIndex, int actualStageCount, int hudStageCount) {
        int resolvedHudStageCount = Math.max(1, hudStageCount);
        int resolvedActualStageCount = Math.max(1, actualStageCount);
        int boundedActualStage = Math.max(0, Math.min(actualStageIndex, resolvedActualStageCount - 1));
        if (resolvedHudStageCount <= 1) {
            return 0;
        }
        if (resolvedActualStageCount <= 1) {
            return resolvedHudStageCount - 1;
        }
        if (boundedActualStage <= 0) {
            return 0;
        }
        if (boundedActualStage >= resolvedActualStageCount - 1) {
            return resolvedHudStageCount - 1;
        }
        int middleActualCount = Math.max(1, resolvedActualStageCount - 2);
        int middleHudCount = Math.max(1, resolvedHudStageCount - 2);
        int middleActualIndex = boundedActualStage - 1;
        int mappedMiddleIndex = middleHudCount <= 1
            ? 0
            : Math.round(middleActualIndex * (middleHudCount - 1) / (float) Math.max(1, middleActualCount - 1));
        return Math.max(1, Math.min(resolvedHudStageCount - 2, mappedMiddleIndex + 1));
    }

    private RoomPosition pickSoloSpawn(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (room == null || player == null) {
            return null;
        }
        return new RoomSpawnCandidateSelectionService(player.server).pickSpawn(
            room.gunmasterConfig().soloSpawnPositions(),
            state,
            null
        );
    }

    private List<String> buildWeaponSequence(RoomDefinition room) {
        if (room == null) {
            return List.of();
        }
        List<String> initialCandidates = normalizePool(room.gunmasterConfig().initialWeaponPool());
        List<String> middleCandidates = normalizePool(room.gunmasterConfig().middleWeaponPool());
        List<String> finalCandidates = normalizePool(room.gunmasterConfig().finalWeaponPool());
        List<String> sequence = new ArrayList<>();
        String initial = pickSingleWeapon(initialCandidates);
        if (!initial.isBlank()) {
            sequence.add(initial);
        }
        sequence.addAll(pickMiddleWeapons(middleCandidates, room.gunmasterConfig().enabledMiddleWeaponCount()));
        String ending = pickSingleWeapon(finalCandidates);
        if (!ending.isBlank()) {
            sequence.add(ending);
        }
        return List.copyOf(sequence);
    }

    private String resolveNextWeaponName(int currentStageIndex) {
        List<String> sequence = gunmasterRunningState.getGunmasterMatchWeaponSequenceIds();
        int nextStageIndex = Math.max(0, currentStageIndex) + 1;
        if (sequence.isEmpty() || nextStageIndex >= sequence.size()) {
            return "-";
        }
        String nextWeaponId = sequence.get(nextStageIndex);
        if (nextWeaponId == null || nextWeaponId.isBlank()) {
            return "-";
        }
        EquipmentDataModel equipmentData = ConquestDataManager.of(gunmasterServer).equipmentData().getData();
        EquipmentDataModel.EquipmentEntry entry = RoomGunmasterWeaponPoolRules.resolveEntry(equipmentData, nextWeaponId);
        if (entry == null) {
            return nextWeaponId;
        }
        String displayName = entry.getDisplayName();
        return displayName == null || displayName.isBlank() ? nextWeaponId : displayName.trim();
    }

    private List<String> normalizePool(List<String> raw) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                String normalized = value == null ? "" : value.trim();
                if (!normalized.isBlank()) {
                    unique.add(normalized);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private String pickSingleWeapon(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private List<String> pickMiddleWeapons(List<String> candidates, int targetCount) {
        int count = Math.max(0, targetCount);
        if (count <= 0 || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() >= count) {
            List<String> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            return List.copyOf(shuffled.subList(0, count));
        }
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            List<String> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            for (String value : shuffled) {
                result.add(value);
                if (result.size() >= count) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private void advancePlayerStage(RoomDefinition room, UUID killerId) {
        List<String> sequence = gunmasterRunningState.getGunmasterMatchWeaponSequenceIds();
        if (sequence.isEmpty()) {
            return;
        }
        int killsNeeded = Math.max(1, room.gunmasterConfig().killsPerWeaponSwitch());
        int currentStage = Math.max(0, gunmasterRunningState.getGunmasterStageIndexByPlayer().getOrDefault(killerId, 0));
        int nextKills = gunmasterRunningState.getGunmasterStageKillsByPlayer().getOrDefault(killerId, 0) + 1;
        int finalStageIndex = Math.max(0, sequence.size() - 1);
        if (currentStage >= finalStageIndex) {
            if (nextKills >= killsNeeded) {
                RoomTeamId killerTeam = gunmasterRunningState.getRoomTeams().get(killerId);
                pendingEndRequest = room.gunmasterConfig().isSoloGrouping()
                    ? new RoomMatchEndRequest(killerId, "最终武器达成击杀")
                    : new RoomMatchEndRequest(killerTeam, killerId, room.gameplayTeamDisplayName(killerTeam) + "以最终武器完成终结击杀");
            } else {
                gunmasterRunningState.getGunmasterStageKillsByPlayer().put(killerId, nextKills);
            }
            return;
        }
        if (nextKills >= killsNeeded) {
            gunmasterRunningState.getGunmasterStageIndexByPlayer().put(killerId, currentStage + 1);
            gunmasterRunningState.getGunmasterStageKillsByPlayer().put(killerId, 0);
            ServerPlayer killer = gunmasterServer.getPlayerList().getPlayer(killerId);
            if (killer != null) {
                gunmasterLoadoutService.applyStageSwitchLoadout(room, gunmasterRunningState, killer);
            }
        } else {
            gunmasterRunningState.getGunmasterStageKillsByPlayer().put(killerId, nextKills);
        }
    }

    private ServerPlayer resolveKillerPlayer(int killerEntityId) {
        if (killerEntityId < 0) {
            return null;
        }
        for (UUID playerId : gunmasterRunningState.getRoomPlayers()) {
            ServerPlayer candidate = gunmasterServer.getPlayerList().getPlayer(playerId);
            if (candidate != null && candidate.getId() == killerEntityId) {
                return candidate;
            }
        }
        return null;
    }

    private int resolveRemainSeconds(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room != null
            && room.lifecycleState() == RoomLifecycleState.ENDING
            && state != null
            && state.getResultSequenceEndTick() > 0) {
            return (int) Math.max(0L, (state.getResultSequenceEndTick() - gameTick + 19L) / 20L);
        }
        if (room == null || enteredTick < 0L || room.gunmasterConfig().matchDurationMinutes() <= 0) {
            return 0;
        }
        long endTick = enteredTick + room.gunmasterConfig().matchDurationMinutes() * 60L * 20L;
        return (int) Math.max(0L, (endTick - gameTick + 19L) / 20L);
    }

    private String resolveTopStatus(RoomDefinition room, boolean inDeployFlow) {
        if (room == null) {
            return "-";
        }
        if (room.lifecycleState() == RoomLifecycleState.ENDING) {
            return "结算中";
        }
        if (inDeployFlow) {
            return "重生中";
        }
        return "进行中";
    }

    private RoomMatchEndRequest buildTimeLimitEndRequest(RoomDefinition room) {
        if (room == null) {
            return new RoomMatchEndRequest((RoomTeamId) null, "达到时长，判定平局");
        }
        if (room.gunmasterConfig().isSoloGrouping()) {
            UUID winnerId = resolveSoloLeader();
            return winnerId == null
                ? new RoomMatchEndRequest((RoomTeamId) null, "达到时长，判定平局")
                : new RoomMatchEndRequest(winnerId, "达到时长，当前排名第一的玩家获胜");
        }
        RoomTeamId winnerTeam = resolveLeadingTeam();
        return winnerTeam == null
            ? new RoomMatchEndRequest((RoomTeamId) null, "达到时长，判定平局")
            : new RoomMatchEndRequest(winnerTeam, "达到时长，" + room.gameplayTeamDisplayName(winnerTeam) + "领先");
    }

    private RoomTeamId resolveLeadingTeam() {
        int campAProgress = 0;
        int campBProgress = 0;
        for (UUID playerId : gunmasterRunningState.getRoomPlayers()) {
            RoomTeamId teamId = gunmasterRunningState.getRoomTeams().get(playerId);
            int stageIndex = Math.max(0, gunmasterRunningState.getGunmasterStageIndexByPlayer().getOrDefault(playerId, 0));
            int kills = Math.max(0, gunmasterRunningState.getGunmasterStageKillsByPlayer().getOrDefault(playerId, 0));
            int progress = stageIndex * 1000 + kills;
            if (teamId == RoomTeamId.CAMP_A) {
                campAProgress = Math.max(campAProgress, progress);
            } else if (teamId == RoomTeamId.CAMP_B) {
                campBProgress = Math.max(campBProgress, progress);
            }
        }
        if (campAProgress > campBProgress) {
            return RoomTeamId.CAMP_A;
        }
        if (campBProgress > campAProgress) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private UUID resolveSoloLeader() {
        UUID leaderId = null;
        int bestProgress = Integer.MIN_VALUE;
        for (UUID playerId : gunmasterRunningState.getRoomPlayers()) {
            int stageIndex = Math.max(0, gunmasterRunningState.getGunmasterStageIndexByPlayer().getOrDefault(playerId, 0));
            int kills = Math.max(0, gunmasterRunningState.getGunmasterStageKillsByPlayer().getOrDefault(playerId, 0));
            int progress = stageIndex * 1000 + kills;
            if (progress > bestProgress) {
                bestProgress = progress;
                leaderId = playerId;
            } else if (progress == bestProgress) {
                leaderId = null;
            }
        }
        return leaderId;
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

    private String join(int[] values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append('.');
            }
            builder.append(Math.max(0, values[index]));
        }
        return builder.toString();
    }

    private String encode(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("|", "/").replace("=", ":").trim();
    }
}
