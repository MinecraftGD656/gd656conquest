package org.mods.gd656conquest.server.data.serverconfig;

import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

public final class ServerConfigDataModel {
    private Boolean boolOverworldInvulnerable = true;
    private Boolean boolRestrictConquestDimension = true;
    private Boolean boolNaturalRegeneration = false;
    private Integer passiveRegenIntervalTicks = 3;
    private Integer passiveRegenStartDelaySeconds = 6;
    private Integer partyMaxMembers = 4;
    private RoomPosition overworldSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition rangeJoinSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition rangeLeaveSpawn = new RoomPosition(0, 100, 0);
    private RoomVehicleBackend vehicleBackend = RoomVehicleBackend.defaultValue();

    public Boolean getBoolOverworldInvulnerable() {
        return boolOverworldInvulnerable;
    }

    public void setBoolOverworldInvulnerable(Boolean boolOverworldInvulnerable) {
        this.boolOverworldInvulnerable = boolOverworldInvulnerable;
    }

    public Boolean getBoolRestrictConquestDimension() {
        return boolRestrictConquestDimension;
    }

    public void setBoolRestrictConquestDimension(Boolean boolRestrictConquestDimension) {
        this.boolRestrictConquestDimension = boolRestrictConquestDimension;
    }

    public Boolean getBoolNaturalRegeneration() {
        return boolNaturalRegeneration;
    }

    public void setBoolNaturalRegeneration(Boolean boolNaturalRegeneration) {
        this.boolNaturalRegeneration = boolNaturalRegeneration;
    }

    public Integer getPassiveRegenIntervalTicks() {
        return passiveRegenIntervalTicks;
    }

    public void setPassiveRegenIntervalTicks(Integer passiveRegenIntervalTicks) {
        this.passiveRegenIntervalTicks = passiveRegenIntervalTicks;
    }

    public Integer getPassiveRegenStartDelaySeconds() {
        return passiveRegenStartDelaySeconds;
    }

    public void setPassiveRegenStartDelaySeconds(Integer passiveRegenStartDelaySeconds) {
        this.passiveRegenStartDelaySeconds = passiveRegenStartDelaySeconds;
    }

    public Integer getPartyMaxMembers() {
        return partyMaxMembers;
    }

    public void setPartyMaxMembers(Integer partyMaxMembers) {
        this.partyMaxMembers = partyMaxMembers;
    }

    public RoomPosition getOverworldSpawn() {
        return overworldSpawn;
    }

    public void setOverworldSpawn(RoomPosition overworldSpawn) {
        this.overworldSpawn = overworldSpawn;
    }

    public RoomPosition getRangeJoinSpawn() {
        return rangeJoinSpawn;
    }

    public void setRangeJoinSpawn(RoomPosition rangeJoinSpawn) {
        this.rangeJoinSpawn = rangeJoinSpawn;
    }

    public RoomPosition getRangeLeaveSpawn() {
        return rangeLeaveSpawn;
    }

    public void setRangeLeaveSpawn(RoomPosition rangeLeaveSpawn) {
        this.rangeLeaveSpawn = rangeLeaveSpawn;
    }

    public RoomVehicleBackend getVehicleBackend() {
        return vehicleBackend;
    }

    public void setVehicleBackend(RoomVehicleBackend vehicleBackend) {
        this.vehicleBackend = vehicleBackend;
    }
}
