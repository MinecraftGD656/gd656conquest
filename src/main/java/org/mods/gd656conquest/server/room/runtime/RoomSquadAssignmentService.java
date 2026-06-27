package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RoomSquadAssignmentService {
    private final MinecraftServer server;

    public RoomSquadAssignmentService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void assignMatchingPlayer(RoomRuntimeState runtimeState, UUID playerId, RoomTeamId teamId) {
        if (runtimeState == null || playerId == null) {
            return;
        }
        int squadSize = resolveSquadSize();
        if (squadSize <= 0 || teamId == null) {
            runtimeState.getSquadLabelsByPlayer().remove(playerId);
            return;
        }
        String existing = runtimeState.getSquadLabelsByPlayer().get(playerId);
        if (existing != null && !existing.isBlank() && !isMatchingSquadFull(runtimeState, teamId, existing, squadSize, playerId)) {
            return;
        }
        Map<String, Integer> counts = countMatchingSquadMembers(runtimeState, teamId);
        String chosen = chooseSquadLabel(counts, squadSize);
        runtimeState.getSquadLabelsByPlayer().put(playerId, chosen);
    }

    public String allocateMatchingSquadLabel(RoomRuntimeState runtimeState, RoomTeamId teamId) {
        if (runtimeState == null || teamId == null) {
            return "";
        }
        return chooseSquadLabel(countMatchingSquadMembers(runtimeState, teamId), resolveSquadSize());
    }

    public void assignRunningPlayer(RoomRuntimeState runtimeState, UUID playerId, RoomTeamId teamId) {
        if (runtimeState == null || playerId == null) {
            return;
        }
        int squadSize = resolveSquadSize();
        if (squadSize <= 0 || teamId == null) {
            runtimeState.getSquadLabelsByPlayer().remove(playerId);
            runtimeState.getRunningState().getSquadLabelsByPlayer().remove(playerId);
            return;
        }
        String existing = runtimeState.getRunningState().getSquadLabelsByPlayer().get(playerId);
        if (existing != null && !existing.isBlank() && !isRunningSquadFull(runtimeState, teamId, existing, squadSize, playerId)) {
            runtimeState.getSquadLabelsByPlayer().put(playerId, existing);
            return;
        }
        Map<String, Integer> counts = countRunningSquadMembers(runtimeState, teamId);
        String chosen = chooseSquadLabel(counts, squadSize);
        runtimeState.getSquadLabelsByPlayer().put(playerId, chosen);
        runtimeState.getRunningState().getSquadLabelsByPlayer().put(playerId, chosen);
    }

    public String allocateRunningSquadLabel(RoomRuntimeState runtimeState, RoomTeamId teamId) {
        if (runtimeState == null || teamId == null) {
            return "";
        }
        return chooseSquadLabel(countRunningSquadMembers(runtimeState, teamId), resolveSquadSize());
    }

    public void assignMatchingSquad(RoomRuntimeState runtimeState, UUID playerId, String squadLabel) {
        if (runtimeState == null || playerId == null) {
            return;
        }
        if (squadLabel == null || squadLabel.isBlank()) {
            runtimeState.getSquadLabelsByPlayer().remove(playerId);
            return;
        }
        runtimeState.getSquadLabelsByPlayer().put(playerId, squadLabel);
    }

    public void assignRunningSquad(RoomRuntimeState runtimeState, UUID playerId, String squadLabel) {
        if (runtimeState == null || playerId == null) {
            return;
        }
        if (squadLabel == null || squadLabel.isBlank()) {
            runtimeState.getSquadLabelsByPlayer().remove(playerId);
            runtimeState.getRunningState().getSquadLabelsByPlayer().remove(playerId);
            return;
        }
        runtimeState.getSquadLabelsByPlayer().put(playerId, squadLabel);
        runtimeState.getRunningState().getSquadLabelsByPlayer().put(playerId, squadLabel);
    }

    public void removePlayer(RoomRuntimeState runtimeState, UUID playerId) {
        if (runtimeState == null || playerId == null) {
            return;
        }
        runtimeState.getSquadLabelsByPlayer().remove(playerId);
    }

    public String describeSquad(RoomRuntimeState runtimeState, UUID playerId) {
        if (runtimeState == null || playerId == null) {
            return "";
        }
        return runtimeState.getSquadLabelsByPlayer().getOrDefault(playerId, "");
    }

    private Map<String, Integer> countMatchingSquadMembers(RoomRuntimeState runtimeState, RoomTeamId teamId) {
        MatchingRuntimeState matchingState = runtimeState.getMatchingState();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (UUID memberId : matchingState.getQueuePlayers()) {
            if (matchingState.getQueueTeams().get(memberId) != teamId) {
                continue;
            }
            String label = runtimeState.getSquadLabelsByPlayer().get(memberId);
            if (label == null || label.isBlank()) {
                continue;
            }
            counts.merge(label, 1, Integer::sum);
        }
        return counts;
    }

    private boolean isMatchingSquadFull(RoomRuntimeState runtimeState, RoomTeamId teamId, String squadLabel, int squadSize, UUID excludingPlayerId) {
        int count = 0;
        MatchingRuntimeState matchingState = runtimeState.getMatchingState();
        for (UUID memberId : matchingState.getQueuePlayers()) {
            if (memberId.equals(excludingPlayerId)) {
                continue;
            }
            if (matchingState.getQueueTeams().get(memberId) != teamId) {
                continue;
            }
            String label = runtimeState.getSquadLabelsByPlayer().get(memberId);
            if (squadLabel.equalsIgnoreCase(label)) {
                count++;
            }
        }
        return count >= squadSize;
    }

    private Map<String, Integer> countRunningSquadMembers(RoomRuntimeState runtimeState, RoomTeamId teamId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (UUID memberId : runtimeState.getRunningState().getRoomPlayers()) {
            if (runtimeState.getRunningState().getRoomTeams().get(memberId) != teamId) {
                continue;
            }
            String label = runtimeState.getRunningState().getSquadLabelsByPlayer().get(memberId);
            if (label == null || label.isBlank()) {
                continue;
            }
            counts.merge(label, 1, Integer::sum);
        }
        return counts;
    }

    private boolean isRunningSquadFull(RoomRuntimeState runtimeState, RoomTeamId teamId, String squadLabel, int squadSize, UUID excludingPlayerId) {
        int count = 0;
        for (UUID memberId : runtimeState.getRunningState().getRoomPlayers()) {
            if (memberId.equals(excludingPlayerId)) {
                continue;
            }
            if (runtimeState.getRunningState().getRoomTeams().get(memberId) != teamId) {
                continue;
            }
            String label = runtimeState.getRunningState().getSquadLabelsByPlayer().get(memberId);
            if (squadLabel.equalsIgnoreCase(label)) {
                count++;
            }
        }
        return count >= squadSize;
    }

    private String chooseSquadLabel(Map<String, Integer> counts, int squadSize) {
        for (int i = 0; i < 2048; i++) {
            String label = toSquadLabel(i);
            int count = counts.getOrDefault(label, 0);
            if (count < squadSize) {
                return label;
            }
        }
        return toSquadLabel(counts.size());
    }

    private int resolveSquadSize() {
        try {
            return Math.max(1, ConquestDataManager.of(server).serverConfig().getPartyMaxMembers());
        } catch (Exception ignored) {
            return 4;
        }
    }

    private String toSquadLabel(int index) {
        int normalized = Math.max(0, index);
        if (normalized < 26) {
            return String.valueOf((char) ('A' + normalized));
        }
        return String.valueOf(normalized + 1);
    }
}

