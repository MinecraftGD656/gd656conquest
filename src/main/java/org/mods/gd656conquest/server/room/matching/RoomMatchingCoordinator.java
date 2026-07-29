package org.mods.gd656conquest.server.room.matching;

import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RoomMatchingCoordinator {
    public boolean joinQueue(MatchingRuntimeState state, UUID playerId, long gameTick, RoomMatchingConfig config) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(config, "config");
        if (!state.getQueuePlayers().contains(playerId) && state.getQueuePlayers().size() >= config.totalMaxPlayers()) {
            return false;
        }
        if (state.getQueuePlayers().add(playerId) && state.getQueueStartTick() < 0) {
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
        return state != null && state.getPreMatchDeployEndTick() > 0;
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
        for (RoomTeamId team : enabledTeams) {
            if (state.getQueueTeams().containsValue(team)) {
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
        for (RoomTeamId team : config.enabledTeams()) {
            if (availableSlots(state, config, team) < requiredSlots) {
                continue;
            }
            int count = countPlayersInTeam(state, team);
            if (count < minPlayers) {
                minPlayers = count;
                eligibleTeams.clear();
                eligibleTeams.add(team);
            } else if (count == minPlayers) {
                eligibleTeams.add(team);
            }
        }
        if (eligibleTeams.isEmpty()) {
            return null;
        }
        return eligibleTeams.get(ThreadLocalRandom.current().nextInt(eligibleTeams.size()));
    }

    private int availableSlots(MatchingRuntimeState state, RoomMatchingConfig config, RoomTeamId team) {
        if (state == null || config == null || team == null) {
            return 0;
        }
        return config.maxPlayersForTeam(team) - countPlayersInTeam(state, team);
    }

    private int countPlayersInTeam(MatchingRuntimeState state, RoomTeamId team) {
        int count = 0;
        for (RoomTeamId assigned : state.getQueueTeams().values()) {
            if (assigned == team) {
                count++;
            }
        }
        return count;
    }
}
