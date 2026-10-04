package org.mods.gd656conquest.server.room.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.matching.RoomMatchingCoordinator;
import org.mods.gd656conquest.server.room.matching.RoomTeamBalanceRatingService;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomDeploySelectionService;
import org.mods.gd656conquest.server.room.runtime.RoomHudSyncService;
import org.mods.gd656conquest.server.room.runtime.RoomNametagVisibilityService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLaunchService;
import org.mods.gd656conquest.server.room.runtime.RoomSquadAssignmentService;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoomGroupPlacementService {
    private final MinecraftServer server;
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomPlayerSessionService playerSessionService;
    private final RoomMatchingCoordinator matchingCoordinator;
    private final RoomTeamBalanceRatingService teamBalanceRatingService;
    private final RoomSquadAssignmentService squadAssignmentService;
    private final RoomDeploySelectionService deploySelectionService;
    private final RoomPlayerLaunchService playerLaunchService;
    private final RoomHudSyncService hudSyncService;
    private final RoomNametagVisibilityService nametagVisibilityService;
    private final PresentationSync presentationSync;

    public RoomGroupPlacementService(
        MinecraftServer server,
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService,
        RoomMatchingCoordinator matchingCoordinator,
        RoomTeamBalanceRatingService teamBalanceRatingService,
        RoomSquadAssignmentService squadAssignmentService,
        RoomDeploySelectionService deploySelectionService,
        RoomPlayerLaunchService playerLaunchService,
        RoomHudSyncService hudSyncService,
        RoomNametagVisibilityService nametagVisibilityService,
        PresentationSync presentationSync
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.matchingCoordinator = Objects.requireNonNull(matchingCoordinator, "matchingCoordinator");
        this.teamBalanceRatingService = Objects.requireNonNull(teamBalanceRatingService, "teamBalanceRatingService");
        this.squadAssignmentService = Objects.requireNonNull(squadAssignmentService, "squadAssignmentService");
        this.deploySelectionService = Objects.requireNonNull(deploySelectionService, "deploySelectionService");
        this.playerLaunchService = Objects.requireNonNull(playerLaunchService, "playerLaunchService");
        this.hudSyncService = Objects.requireNonNull(hudSyncService, "hudSyncService");
        this.nametagVisibilityService = Objects.requireNonNull(nametagVisibilityService, "nametagVisibilityService");
        this.presentationSync = Objects.requireNonNull(presentationSync, "presentationSync");
    }

    public Optional<RoomTeamId> findCurrentTeam(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return Optional.empty();
        }
        return runtimeRepository.find(roomId.get())
            .map(slot -> {
                RoomTeamId runningTeam = slot.runtime().getRunningState().getRoomTeams().get(playerId);
                return runningTeam != null ? runningTeam : slot.runtime().getMatchingState().getQueueTeams().get(playerId);
            });
    }

    public Optional<String> findSquadLabel(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return Optional.empty();
        }
        return runtimeRepository.find(roomId.get())
            .map(slot -> slot.runtime().getSquadLabelsByPlayer().get(playerId))
            .filter(label -> label != null && !label.isBlank());
    }

    public boolean canJoinInProgressRoom(UUID playerId, String roomId) {
        if (playerId == null || roomId == null || roomId.isBlank()) {
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState != RoomLifecycleState.RUNNING && lifecycleState != RoomLifecycleState.ENDING) {
            return false;
        }
        if (slot.runtime().getRunningState().getRejoinTickets().containsKey(playerId)) {
            return true;
        }
        return chooseRunningJoinTeam(slot.room(), slot.runtime().getRunningState(), playerId, 1, resolvePlayerStrength(playerId)) != null;
    }

    public RoomTeamId resolveRunningJoinTeam(RoomDefinition room, RunningRuntimeState state, UUID joiningPlayerId) {
        return chooseRunningJoinTeam(room, state, joiningPlayerId, 1, resolvePlayerStrength(joiningPlayerId));
    }

    public Optional<RoomGroupPlacementPlan> planGroupJoinPlacement(String roomId, int groupSize) {
        if (roomId == null || roomId.isBlank() || groupSize <= 0) {
            return Optional.empty();
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return Optional.empty();
        }
        RoomDefinition room = slot.room();
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.MAINTENANCE) {
            return Optional.of(new RoomGroupPlacementPlan(lifecycleState, null, ""));
        }
        if (lifecycleState == RoomLifecycleState.IDLE || lifecycleState == RoomLifecycleState.MATCHING) {
            RoomTeamId teamId = chooseMatchingJoinTeam(slot.runtime(), room.commonConfig().matchingConfig(), groupSize, 0.0D);
            if (teamId == null) {
                return Optional.empty();
            }
            return Optional.of(new RoomGroupPlacementPlan(
                lifecycleState,
                teamId,
                squadAssignmentService.allocateSquadLabel(slot.runtime(), teamId, groupSize, false)
            ));
        }
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            RoomTeamId teamId = chooseRunningJoinTeam(room, slot.runtime().getRunningState(), null, groupSize, 0.0D);
            if (teamId == null) {
                return Optional.empty();
            }
            return Optional.of(new RoomGroupPlacementPlan(
                lifecycleState,
                teamId,
                squadAssignmentService.allocateSquadLabel(slot.runtime(), teamId, groupSize, true)
            ));
        }
        return Optional.empty();
    }

    public Optional<RoomGroupPlacementPlan> resolvePlayerPlacement(String roomId, UUID playerId) {
        if (roomId == null || roomId.isBlank() || playerId == null) {
            return Optional.empty();
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return Optional.empty();
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState == RoomLifecycleState.MAINTENANCE) {
            return Optional.of(new RoomGroupPlacementPlan(lifecycleState, null, ""));
        }
        RoomTeamId teamId = findCurrentTeam(playerId).orElse(null);
        if (teamId == null) {
            return Optional.empty();
        }
        String squadLabel = findSquadLabel(playerId).orElseGet(() -> reserveSquadLabel(roomId, teamId).orElse(""));
        return Optional.of(new RoomGroupPlacementPlan(lifecycleState, teamId, squadLabel));
    }

    public Optional<String> reserveSquadLabel(String roomId, RoomTeamId teamId) {
        if (roomId == null || roomId.isBlank() || teamId == null) {
            return Optional.empty();
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return Optional.empty();
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            return Optional.of(squadAssignmentService.allocateSquadLabel(slot.runtime(), teamId, 1, true));
        }
        return Optional.of(squadAssignmentService.allocateSquadLabel(slot.runtime(), teamId, 1, false));
    }

    public boolean canFitGroupInTeam(String roomId, RoomTeamId teamId, int requiredSlots) {
        if (roomId == null || roomId.isBlank() || teamId == null || requiredSlots < 0) {
            return false;
        }
        if (requiredSlots == 0) {
            return true;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        RoomMatchingConfig config = slot.room().commonConfig().matchingConfig();
        if (lifecycleState == RoomLifecycleState.IDLE || lifecycleState == RoomLifecycleState.MATCHING) {
            return config.maxPlayersForTeam(teamId) - countMatchingPlayers(slot.runtime().getMatchingState(), teamId) >= requiredSlots;
        }
        if (lifecycleState == RoomLifecycleState.RUNNING) {
            return countRunningOccupiedTeamSlots(slot.runtime().getRunningState(), null, teamId) + requiredSlots
                <= config.maxPlayersForTeam(teamId);
        }
        return lifecycleState == RoomLifecycleState.ENDING;
    }

    public boolean applyGroupPlacement(String roomId, UUID playerId, RoomTeamId targetTeam, String squadLabel, long gameTick) {
        if (roomId == null || roomId.isBlank() || playerId == null) {
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState == RoomLifecycleState.MATCHING || lifecycleState == RoomLifecycleState.IDLE) {
            MatchingRuntimeState matchingState = slot.runtime().getMatchingState();
            if (!matchingState.getQueuePlayers().contains(playerId)) {
                return false;
            }
            if (targetTeam != null) {
                matchingState.getQueueTeams().put(playerId, targetTeam);
            }
            matchingState.setLastPrimaryActionBarTick(-1L);
            matchingState.setLastSecondaryActionBarTick(-1L);
            squadAssignmentService.assignSquad(slot.runtime(), playerId, matchingState.getQueueTeams().get(playerId), squadLabel, false);
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            if (player != null) {
                presentationSync.resync(player, gameTick);
            }
            hudSyncService.syncMatchingRoom(slot.room(), slot.runtime(), gameTick);
            return true;
        }
        if (lifecycleState != RoomLifecycleState.RUNNING && lifecycleState != RoomLifecycleState.ENDING) {
            return false;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        if (!runningState.getRoomPlayers().contains(playerId)) {
            return false;
        }
        if (targetTeam != null) {
            RoomTeamId previousTeam = runningState.getRoomTeams().get(playerId);
            runningState.getRoomTeams().put(playerId, targetTeam);
            if (lifecycleState == RoomLifecycleState.RUNNING && previousTeam != targetTeam) {
                runningState.clearTeamSensitiveStateForTeamChange(playerId);
                deploySelectionService.replaceSelectionWithTeamFallback(runningState, playerId, targetTeam);
            }
        }
        squadAssignmentService.assignSquad(slot.runtime(), playerId, runningState.getRoomTeams().get(playerId), squadLabel, true);
        nametagVisibilityService.syncRunningRoom(slot.room(), runningState);
        hudSyncService.syncRunningRoom(slot.room(), runningState);
        return true;
    }

    public void rebalanceMatchingQueue(RoomDefinition room, RoomRuntimeState runtimeState) {
        if (room == null || runtimeState == null) {
            return;
        }
        MatchingRuntimeState matchingState = runtimeState.getMatchingState();
        RoomMatchingConfig config = room.commonConfig().matchingConfig();
        if (matchingState.getQueuePlayers().isEmpty()) {
            matchingState.getQueueTeams().clear();
            return;
        }
        Map<UUID, Double> ratingByPlayer = teamBalanceRatingService.resolveRatings(matchingState.getQueuePlayers());
        List<MatchingUnit> units = buildMatchingUnits(runtimeState, matchingState, ratingByPlayer);
        matchingState.getQueueTeams().clear();
        LinkedHashMap<RoomTeamId, Integer> countByTeam = createZeroIntMap(config.enabledTeams());
        LinkedHashMap<RoomTeamId, Double> strengthByTeam = createZeroDoubleMap(config.enabledTeams());
        LinkedHashMap<RoomTeamId, Integer> capacityByTeam = createCapacityMap(config);
        for (MatchingUnit unit : units) {
            RoomTeamId teamId = chooseBalancedTeam(
                config.enabledTeams(),
                countByTeam,
                strengthByTeam,
                capacityByTeam,
                unit.playerIds().size(),
                unit.totalStrength(),
                true
            );
            if (teamId == null) {
                teamId = matchingCoordinator.chooseSimpleTeam(config.enabledTeams(), countByTeam, capacityByTeam, unit.playerIds().size());
            }
            if (teamId == null) {
                continue;
            }
            // GD656Mods: 不再沿用 unit 里的旧 squadLabel —— 它可能已过期或与其它 unit 指向同一个小队,
            // 那样会在重排时把两队合并进一个小队造成超员。统一按"本组人数"重新分配。
            String squadLabel = squadAssignmentService.allocateSquadLabel(runtimeState, teamId, unit.playerIds().size(), false);
            for (UUID playerId : unit.playerIds()) {
                matchingState.getQueueTeams().put(playerId, teamId);
                squadAssignmentService.assignSquad(runtimeState, playerId, teamId, squadLabel, false);
            }
            countByTeam.put(teamId, countByTeam.getOrDefault(teamId, 0) + unit.playerIds().size());
            strengthByTeam.put(teamId, strengthByTeam.getOrDefault(teamId, 0.0D) + unit.totalStrength());
        }
        matchingState.setLastPrimaryActionBarTick(-1L);
        matchingState.setLastSecondaryActionBarTick(-1L);
    }

    private RoomTeamId chooseMatchingJoinTeam(
        RoomRuntimeState runtimeState,
        RoomMatchingConfig config,
        int requiredSlots,
        double joiningStrength
    ) {
        MatchingRuntimeState matchingState = runtimeState.getMatchingState();
        if (!isExperimentalPlayerBalanceEnabled()) {
            return matchingCoordinator.chooseSimpleTeam(matchingState, config, requiredSlots);
        }
        Map<UUID, Double> ratingByPlayer = teamBalanceRatingService.resolveRatings(matchingState.getQueuePlayers());
        return chooseBalancedTeam(
            config.enabledTeams(),
            createMatchingCountMap(matchingState, config.enabledTeams()),
            createMatchingStrengthMap(matchingState, config.enabledTeams(), ratingByPlayer),
            createCapacityMap(config),
            requiredSlots,
            joiningStrength,
            true
        );
    }

    private RoomTeamId chooseRunningJoinTeam(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID joiningPlayerId,
        int requiredSlots,
        double joiningStrength
    ) {
        if (room == null || state == null || requiredSlots <= 0) {
            return null;
        }
        RoomMatchingConfig config = room.commonConfig().matchingConfig();
        Map<RoomTeamId, Integer> countByTeam = createRunningCountMap(state, config.enabledTeams(), joiningPlayerId);
        Map<RoomTeamId, Integer> capacityByTeam = createCapacityMap(config);
        if (!isExperimentalPlayerBalanceEnabled()) {
            return matchingCoordinator.chooseSimpleTeam(config.enabledTeams(), countByTeam, capacityByTeam, requiredSlots);
        }
        // GD656Mods: 对局内新加入的玩家按人数优先分配, 人数并列时才比较阵营综合战力
        Set<RoomTeamId> fewestTeams = resolveFewestCountTeams(config.enabledTeams(), countByTeam, capacityByTeam, requiredSlots);
        if (fewestTeams.isEmpty()) {
            return null;
        }
        LinkedHashSet<UUID> relatedPlayers = new LinkedHashSet<>(state.getRoomPlayers());
        relatedPlayers.addAll(state.getRejoinTickets().keySet());
        Map<UUID, Double> ratingByPlayer = teamBalanceRatingService.resolveRatings(relatedPlayers);
        return chooseBalancedTeam(
            fewestTeams,
            countByTeam,
            createRunningStrengthMap(state, config.enabledTeams(), joiningPlayerId, ratingByPlayer),
            capacityByTeam,
            requiredSlots,
            joiningStrength,
            false
        );
    }

    private Set<RoomTeamId> resolveFewestCountTeams(
        Set<RoomTeamId> teams,
        Map<RoomTeamId, Integer> countByTeam,
        Map<RoomTeamId, Integer> capacityByTeam,
        int requiredSlots
    ) {
        LinkedHashSet<RoomTeamId> fewestTeams = new LinkedHashSet<>();
        int minCount = Integer.MAX_VALUE;
        for (RoomTeamId teamId : teams) {
            int count = countByTeam.getOrDefault(teamId, 0);
            if (capacityByTeam.getOrDefault(teamId, 0) - count < requiredSlots) {
                continue;
            }
            if (count < minCount) {
                minCount = count;
                fewestTeams.clear();
            }
            if (count == minCount) {
                fewestTeams.add(teamId);
            }
        }
        return fewestTeams;
    }

    private Map<RoomTeamId, Integer> createMatchingCountMap(MatchingRuntimeState state, Set<RoomTeamId> teams) {
        LinkedHashMap<RoomTeamId, Integer> countByTeam = createZeroIntMap(teams);
        for (RoomTeamId teamId : state.getQueueTeams().values()) {
            if (teamId != null && countByTeam.containsKey(teamId)) {
                countByTeam.put(teamId, countByTeam.get(teamId) + 1);
            }
        }
        return countByTeam;
    }

    private Map<RoomTeamId, Double> createMatchingStrengthMap(
        MatchingRuntimeState state,
        Set<RoomTeamId> teams,
        Map<UUID, Double> ratingByPlayer
    ) {
        LinkedHashMap<RoomTeamId, Double> strengthByTeam = createZeroDoubleMap(teams);
        for (Map.Entry<UUID, RoomTeamId> entry : state.getQueueTeams().entrySet()) {
            RoomTeamId teamId = entry.getValue();
            if (teamId != null && strengthByTeam.containsKey(teamId)) {
                strengthByTeam.put(teamId, strengthByTeam.get(teamId) + ratingByPlayer.getOrDefault(entry.getKey(), 0.0D));
            }
        }
        return strengthByTeam;
    }

    private Map<RoomTeamId, Integer> createRunningCountMap(
        RunningRuntimeState state,
        Set<RoomTeamId> teams,
        UUID joiningPlayerId
    ) {
        LinkedHashMap<RoomTeamId, Integer> countByTeam = createZeroIntMap(teams);
        for (RoomTeamId teamId : teams) {
            countByTeam.put(teamId, countRunningOccupiedTeamSlots(state, joiningPlayerId, teamId));
        }
        return countByTeam;
    }

    private Map<RoomTeamId, Double> createRunningStrengthMap(
        RunningRuntimeState state,
        Set<RoomTeamId> teams,
        UUID joiningPlayerId,
        Map<UUID, Double> ratingByPlayer
    ) {
        LinkedHashMap<RoomTeamId, Double> strengthByTeam = createZeroDoubleMap(teams);
        for (RoomTeamId teamId : teams) {
            strengthByTeam.put(teamId, resolveRunningTeamStrength(state, joiningPlayerId, teamId, ratingByPlayer));
        }
        return strengthByTeam;
    }

    private LinkedHashMap<RoomTeamId, Integer> createCapacityMap(RoomMatchingConfig config) {
        LinkedHashMap<RoomTeamId, Integer> capacityByTeam = new LinkedHashMap<>();
        for (RoomTeamId teamId : config.enabledTeams()) {
            capacityByTeam.put(teamId, config.maxPlayersForTeam(teamId));
        }
        return capacityByTeam;
    }

    private LinkedHashMap<RoomTeamId, Integer> createZeroIntMap(Set<RoomTeamId> teams) {
        LinkedHashMap<RoomTeamId, Integer> values = new LinkedHashMap<>();
        for (RoomTeamId teamId : teams) {
            values.put(teamId, 0);
        }
        return values;
    }

    private LinkedHashMap<RoomTeamId, Double> createZeroDoubleMap(Set<RoomTeamId> teams) {
        LinkedHashMap<RoomTeamId, Double> values = new LinkedHashMap<>();
        for (RoomTeamId teamId : teams) {
            values.put(teamId, 0.0D);
        }
        return values;
    }

    private RoomTeamId chooseBalancedTeam(
        Set<RoomTeamId> enabledTeams,
        Map<RoomTeamId, Integer> countByTeam,
        Map<RoomTeamId, Double> strengthByTeam,
        Map<RoomTeamId, Integer> capacityByTeam,
        int addedPlayers,
        double addedStrength,
        boolean countFirst
    ) {
        TeamBalanceCandidate best = null;
        for (RoomTeamId teamId : enabledTeams) {
            int capacity = Math.max(0, capacityByTeam.getOrDefault(teamId, 0));
            int currentCount = Math.max(0, countByTeam.getOrDefault(teamId, 0));
            if (capacity - currentCount < addedPlayers) {
                continue;
            }
            TeamBalanceCandidate candidate = evaluateCandidate(
                enabledTeams,
                countByTeam,
                strengthByTeam,
                teamId,
                addedPlayers,
                addedStrength
            );
            if (best == null || candidate.isBetterThan(best, countFirst)) {
                best = candidate;
            }
        }
        return best == null ? null : best.teamId();
    }

    private TeamBalanceCandidate evaluateCandidate(
        Set<RoomTeamId> enabledTeams,
        Map<RoomTeamId, Integer> countByTeam,
        Map<RoomTeamId, Double> strengthByTeam,
        RoomTeamId candidateTeam,
        int addedPlayers,
        double addedStrength
    ) {
        int minCount = Integer.MAX_VALUE;
        int maxCount = Integer.MIN_VALUE;
        double minStrength = Double.POSITIVE_INFINITY;
        double maxStrength = Double.NEGATIVE_INFINITY;
        int candidateCurrentCount = 0;
        for (RoomTeamId teamId : enabledTeams) {
            int projectedCount = Math.max(0, countByTeam.getOrDefault(teamId, 0));
            double projectedStrength = Math.max(0.0D, strengthByTeam.getOrDefault(teamId, 0.0D));
            if (teamId == candidateTeam) {
                projectedCount += addedPlayers;
                projectedStrength += Math.max(0.0D, addedStrength);
                candidateCurrentCount = countByTeam.getOrDefault(teamId, 0);
            }
            minCount = Math.min(minCount, projectedCount);
            maxCount = Math.max(maxCount, projectedCount);
            minStrength = Math.min(minStrength, projectedStrength);
            maxStrength = Math.max(maxStrength, projectedStrength);
        }
        return new TeamBalanceCandidate(
            candidateTeam,
            Math.max(0, maxCount - minCount),
            Math.max(0.0D, maxStrength - minStrength),
            candidateCurrentCount
        );
    }

    private List<MatchingUnit> buildMatchingUnits(
        RoomRuntimeState runtimeState,
        MatchingRuntimeState matchingState,
        Map<UUID, Double> ratingByPlayer
    ) {
        LinkedHashSet<UUID> queuedPlayers = new LinkedHashSet<>(matchingState.getQueuePlayers());
        LinkedHashSet<UUID> visited = new LinkedHashSet<>();
        List<MatchingUnit> units = new ArrayList<>();
        for (UUID playerId : queuedPlayers) {
            if (playerId == null || visited.contains(playerId)) {
                continue;
            }
            LinkedHashSet<UUID> unitPlayers = resolveQueuedPartyMembers(playerId, queuedPlayers);
            if (unitPlayers.isEmpty()) {
                unitPlayers.add(playerId);
            }
            visited.addAll(unitPlayers);
            double totalStrength = 0.0D;
            String squadLabel = null;
            for (UUID memberId : unitPlayers) {
                totalStrength += ratingByPlayer.getOrDefault(memberId, 0.0D);
                if (squadLabel == null || squadLabel.isBlank()) {
                    String existingLabel = runtimeState.getSquadLabelsByPlayer().get(memberId);
                    if (existingLabel != null && !existingLabel.isBlank()) {
                        squadLabel = existingLabel;
                    }
                }
            }
            units.add(new MatchingUnit(List.copyOf(unitPlayers), squadLabel, totalStrength));
        }
        units.sort(Comparator
            .comparingDouble(MatchingUnit::totalStrength).reversed()
            .thenComparingInt(unit -> unit.playerIds().size()).reversed()
            .thenComparing(unit -> unit.playerIds().get(0).toString()));
        return units;
    }

    private LinkedHashSet<UUID> resolveQueuedPartyMembers(UUID playerId, Collection<UUID> queuedPlayers) {
        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        if (playerId == null) {
            return members;
        }
        PartyAggregate party = InMemoryPartyRepository.of(server).findByPlayerId(playerId).orElse(null);
        if (party == null) {
            members.add(playerId);
            return members;
        }
        for (UUID memberId : party.memberIds()) {
            if (memberId != null && queuedPlayers.contains(memberId)) {
                members.add(memberId);
            }
        }
        if (members.isEmpty()) {
            members.add(playerId);
        }
        return members;
    }

    private int countRunningOccupiedTeamSlots(RunningRuntimeState state, UUID joiningPlayerId, RoomTeamId teamId) {
        if (state == null || teamId == null) {
            return 0;
        }
        int count = 0;
        for (UUID memberId : state.getRoomPlayers()) {
            if (state.getRoomTeams().get(memberId) == teamId) {
                count++;
            }
        }
        for (Map.Entry<UUID, RunningRuntimeState.RejoinTicket> entry : state.getRejoinTickets().entrySet()) {
            UUID ticketPlayerId = entry.getKey();
            if (ticketPlayerId == null || ticketPlayerId.equals(joiningPlayerId) || state.getRoomPlayers().contains(ticketPlayerId)) {
                continue;
            }
            RunningRuntimeState.RejoinTicket ticket = entry.getValue();
            if (ticket != null && ticket.teamId() == teamId && ticket.matchInstanceId() == state.getActiveMatchInstanceId()) {
                count++;
            }
        }
        return count;
    }

    private int countMatchingPlayers(MatchingRuntimeState state, RoomTeamId teamId) {
        int count = 0;
        for (RoomTeamId assignedTeam : state.getQueueTeams().values()) {
            if (assignedTeam == teamId) {
                count++;
            }
        }
        return count;
    }

    private double resolveRunningTeamStrength(
        RunningRuntimeState state,
        UUID joiningPlayerId,
        RoomTeamId teamId,
        Map<UUID, Double> ratingByPlayer
    ) {
        if (state == null || teamId == null) {
            return 0.0D;
        }
        double strength = 0.0D;
        for (UUID memberId : state.getRoomPlayers()) {
            if (state.getRoomTeams().get(memberId) == teamId) {
                strength += ratingByPlayer.getOrDefault(memberId, 0.0D);
            }
        }
        for (Map.Entry<UUID, RunningRuntimeState.RejoinTicket> entry : state.getRejoinTickets().entrySet()) {
            UUID ticketPlayerId = entry.getKey();
            if (ticketPlayerId == null || ticketPlayerId.equals(joiningPlayerId) || state.getRoomPlayers().contains(ticketPlayerId)) {
                continue;
            }
            RunningRuntimeState.RejoinTicket ticket = entry.getValue();
            if (ticket != null && ticket.teamId() == teamId && ticket.matchInstanceId() == state.getActiveMatchInstanceId()) {
                strength += ratingByPlayer.getOrDefault(ticketPlayerId, 0.0D);
            }
        }
        return strength;
    }

    private double resolvePlayerStrength(UUID playerId) {
        return playerId == null ? 0.0D : teamBalanceRatingService.resolvePlayerRating(playerId);
    }

    private boolean isExperimentalPlayerBalanceEnabled() {
        return ConquestDataManager.of(server).serverConfig().isExperimentalPlayerBalanceEnabled();
    }

    private record TeamBalanceCandidate(RoomTeamId teamId, int countGap, double strengthGap, int currentCount) {
        private boolean isBetterThan(TeamBalanceCandidate other, boolean countFirst) {
            if (countFirst) {
                if (countGap != other.countGap) {
                    return countGap < other.countGap;
                }
                if (Double.compare(strengthGap, other.strengthGap) != 0) {
                    return strengthGap < other.strengthGap;
                }
            } else {
                if (Double.compare(strengthGap, other.strengthGap) != 0) {
                    return strengthGap < other.strengthGap;
                }
                if (countGap != other.countGap) {
                    return countGap < other.countGap;
                }
            }
            return currentCount < other.currentCount;
        }
    }

    private record MatchingUnit(List<UUID> playerIds, String squadLabel, double totalStrength) {
    }

    @FunctionalInterface
    public interface PresentationSync {
        void resync(ServerPlayer player, long gameTick);
    }
}
