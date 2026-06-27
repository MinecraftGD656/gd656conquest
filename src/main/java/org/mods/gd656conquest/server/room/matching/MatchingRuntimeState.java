package org.mods.gd656conquest.server.room.matching;

import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MatchingRuntimeState {
    private final Set<UUID> queuePlayers = new LinkedHashSet<>();
    private final Map<UUID, RoomTeamId> queueTeams = new LinkedHashMap<>();
    private long queueStartTick = -1L;
    private long queueDeadlineTick = -1L;
    private long preMatchDeployEndTick = -1L;
    private long lastPrimaryActionBarTick = -1L;
    private long lastSecondaryActionBarTick = -1L;

    public Set<UUID> getQueuePlayers() {
        return queuePlayers;
    }

    public long getQueueStartTick() {
        return queueStartTick;
    }

    public Map<UUID, RoomTeamId> getQueueTeams() {
        return queueTeams;
    }

    public void setQueueStartTick(long queueStartTick) {
        this.queueStartTick = queueStartTick;
    }

    public long getQueueDeadlineTick() {
        return queueDeadlineTick;
    }

    public void setQueueDeadlineTick(long queueDeadlineTick) {
        this.queueDeadlineTick = queueDeadlineTick;
    }

    public long getPreMatchDeployEndTick() {
        return preMatchDeployEndTick;
    }

    public void setPreMatchDeployEndTick(long preMatchDeployEndTick) {
        this.preMatchDeployEndTick = preMatchDeployEndTick;
    }

    public long getLastPrimaryActionBarTick() {
        return lastPrimaryActionBarTick;
    }

    public void setLastPrimaryActionBarTick(long lastPrimaryActionBarTick) {
        this.lastPrimaryActionBarTick = lastPrimaryActionBarTick;
    }

    public long getLastSecondaryActionBarTick() {
        return lastSecondaryActionBarTick;
    }

    public void setLastSecondaryActionBarTick(long lastSecondaryActionBarTick) {
        this.lastSecondaryActionBarTick = lastSecondaryActionBarTick;
    }

    public void resetCountdown() {
        queueStartTick = -1L;
        queueDeadlineTick = -1L;
        lastPrimaryActionBarTick = -1L;
        lastSecondaryActionBarTick = -1L;
    }

    public void clearQueue() {
        queuePlayers.clear();
        queueTeams.clear();
        resetCountdown();
        preMatchDeployEndTick = -1L;
    }
}
