package org.mods.gd656conquest.server.room.core;

import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RoomRuntimeState {
    private final MatchingRuntimeState matchingState = new MatchingRuntimeState();
    private final RunningRuntimeState runningState = new RunningRuntimeState();
    private final Map<UUID, String> inventorySnapshots = new LinkedHashMap<>();
    private final Map<UUID, String> squadLabelsByPlayer = new LinkedHashMap<>();

    public MatchingRuntimeState getMatchingState() {
        return matchingState;
    }

    public RunningRuntimeState getRunningState() {
        return runningState;
    }

    public Map<UUID, String> getInventorySnapshots() {
        return inventorySnapshots;
    }

    public Map<UUID, String> getSquadLabelsByPlayer() {
        return squadLabelsByPlayer;
    }
}
