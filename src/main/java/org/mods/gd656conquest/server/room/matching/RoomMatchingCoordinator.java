package org.mods.gd656conquest.server.room.matching;

import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RoomMatchingCoordinator {
    public boolean joinQueue(MatchingRuntimeState state, UUID playerId, long gameTick, RoomMatchingConfig config) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(config, "config");
        if (!state.getQueuePlayers().contains(playerId) && state.getQueuePlayers().size() >= config.totalMaxPlayers()) {
            return false;
        }
        if (state.getQueuePlayers().add(playerId) && state.getQueueStartTick() < 0L) {
            state.setQueueStartTick(gameTick);
        }
        RoomTeamId chosenTeam = chooseBestTeam(state, config, 1);
        if (chosenTeam == null) {
            state.getQueuePlayers().remove(playerId);
            return false;
        }
        state.getQueueTeams().put(playerId, chosenTeam);
        state.setLastPrimaryActionBarTick(-1L);
        state.setLastSecondaryActionBarTick(-1L);
        return true;
    }

    public void leaveQueue(MatchingRuntimeState state, UUID playerId) {
        if (state == null || playerId == null) {
            return;
        }
        state.getQueuePlayers().remove(playerId);
        state.getQueueTeams().remove(playerId);
        state.setLastPrimaryActionBarTick(-1L);
        state.setLastSecondaryActionBarTick(-1L);
        if (state.getQueuePlayers().isEmpty()) {
            state.resetCountdown();
            state.setPreMatchDeployEndTick(-1L);
        }
    }

    public boolean hasPreMatchDeployPhase(MatchingRuntimeState state) {
        return state != null && state.getPreMatchDeployEndTick() > 0L;
    }

    public boolean canStart(MatchingRuntimeState state, RoomMatchingConfig config) {
        if (state == null || config == null) {
            return false;
        }
        return state.getQueuePlayers().size() >= Math.max(1, config.minStartPlayers())
            && countReadyTeams(state, config.enabledTeams()) >= config.requiredReadyTeams();
    }

    public int countReadyTeams(MatchingRuntimeState state, Set<RoomTeamId> enabledTeams) {
        if (state == null || enabledTeams == null || enabledTeams.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RoomTeamId teamId : enabledTeams) {
            if (state.getQueueTeams().containsValue(teamId)) {
                count++;
            }
        }
        return count;
    }

    public boolean isFull(MatchingRuntimeState state, RoomMatchingConfig config) {
        if (state == null || config == null) {
            return false;
        }
        for (RoomTeamId teamId : config.enabledTeams()) {
            if (countPlayersInTeam(state, teamId) < config.maxPlayersForTeam(teamId)) {
                return false;
            }
        }
        return !config.enabledTeams().isEmpty();
    }

    public RoomTeamId chooseBestTeam(MatchingRuntimeState state, RoomMatchingConfig config, int requiredSlots) {
        if (state == null || config == null || requiredSlots <= 0) {
            return null;
        }
        List<RoomTeamId> eligibleTeams = new ArrayList<>();
        int minPlayers = Integer.MAX_VALUE;
        for (RoomTeamId teamId : config.enabledTeams()) {
            if (availableSlots(state, config, teamId) < requiredSlots) {
                continue;
            }
            int count = countPlayersInTeam(state, teamId);
            if (count < minPlayers) {
                minPlayers = count;
                eligibleTeams.clear();
                eligibleTeams.add(teamId);
            } else if (count == minPlayers) {
                eligibleTeams.add(teamId);
            }
        }
        if (eligibleTeams.isEmpty()) {
            return null;
        }
        return eligibleTeams.get(ThreadLocalRandom.current().nextInt(eligibleTeams.size()));
    }

    public RoomTeamId chooseSimpleTeam(MatchingRuntimeState state, RoomMatchingConfig config, int requiredSlots) {
        return chooseBestTeam(state, config, requiredSlots);
    }

    public RoomTeamId chooseSimpleTeam(
        Set<RoomTeamId> enabledTeams,
        Map<RoomTeamId, Integer> countByTeam,
        Map<RoomTeamId, Integer> maxPlayersByTeam,
        int requiredSlots
    ) {
        if (enabledTeams == null || enabledTeams.isEmpty() || requiredSlots <= 0) {
            return null;
        }
        List<RoomTeamId> eligibleTeams = new ArrayList<>();
        int minPlayers = Integer.MAX_VALUE;
        for (RoomTeamId teamId : enabledTeams) {
            int currentCount = Math.max(0, countByTeam.getOrDefault(teamId, 0));
            if (Math.max(0, maxPlayersByTeam.getOrDefault(teamId, 0)) - currentCount < requiredSlots) {
                continue;
            }
            if (currentCount < minPlayers) {
                minPlayers = currentCount;
                eligibleTeams.clear();
                eligibleTeams.add(teamId);
            } else if (currentCount == minPlayers) {
                eligibleTeams.add(teamId);
            }
        }
        if (eligibleTeams.isEmpty()) {
            return null;
        }
        return eligibleTeams.get(ThreadLocalRandom.current().nextInt(eligibleTeams.size()));
    }

    private int availableSlots(MatchingRuntimeState state, RoomMatchingConfig config, RoomTeamId teamId) {
        if (state == null || config == null || teamId == null) {
            return 0;
        }
        return config.maxPlayersForTeam(teamId) - countPlayersInTeam(state, teamId);
    }

    private int countPlayersInTeam(MatchingRuntimeState state, RoomTeamId teamId) {
        int count = 0;
        for (RoomTeamId assignedTeam : state.getQueueTeams().values()) {
            if (assignedTeam == teamId) {
                count++;
            }
        }
        return count;
    }
}
