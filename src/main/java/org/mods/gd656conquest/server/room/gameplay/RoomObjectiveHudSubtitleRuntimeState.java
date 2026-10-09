package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RoomObjectiveHudSubtitleRuntimeState {
    private final Map<String, RoomTeamId> lastDrivingTeamByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> lastOwnerTeamByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> lastNonNeutralOwnerTeamByCode = new LinkedHashMap<>();
    private final Map<UUID, String> lastPersistentKeyByPlayer = new LinkedHashMap<>();
    private final Map<UUID, String> lastPersistentTextByPlayer = new LinkedHashMap<>();
    private RoomTeamId lastMajorityOwnerTeam;
    private RoomTeamId lastAllOwnedTeam;

    public Map<String, RoomTeamId> getLastDrivingTeamByCode() {
        return lastDrivingTeamByCode;
    }

    public Map<String, RoomTeamId> getLastOwnerTeamByCode() {
        return lastOwnerTeamByCode;
    }

    public Map<String, RoomTeamId> getLastNonNeutralOwnerTeamByCode() {
        return lastNonNeutralOwnerTeamByCode;
    }

    public Map<UUID, String> getLastPersistentKeyByPlayer() {
        return lastPersistentKeyByPlayer;
    }

    public Map<UUID, String> getLastPersistentTextByPlayer() {
        return lastPersistentTextByPlayer;
    }

    public RoomTeamId getLastMajorityOwnerTeam() {
        return lastMajorityOwnerTeam;
    }

    public void setLastMajorityOwnerTeam(RoomTeamId lastMajorityOwnerTeam) {
        this.lastMajorityOwnerTeam = lastMajorityOwnerTeam;
    }

    public RoomTeamId getLastAllOwnedTeam() {
        return lastAllOwnedTeam;
    }

    public void setLastAllOwnedTeam(RoomTeamId lastAllOwnedTeam) {
        this.lastAllOwnedTeam = lastAllOwnedTeam;
    }

    public void clear() {
        lastDrivingTeamByCode.clear();
        lastOwnerTeamByCode.clear();
        lastNonNeutralOwnerTeamByCode.clear();
        lastPersistentKeyByPlayer.clear();
        lastPersistentTextByPlayer.clear();
        lastMajorityOwnerTeam = null;
        lastAllOwnedTeam = null;
    }
}
