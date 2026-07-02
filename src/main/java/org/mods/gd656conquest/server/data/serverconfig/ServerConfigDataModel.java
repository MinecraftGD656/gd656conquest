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
    private Double maxScopePistol = 2.0;
    private Double maxScopeSniper = 12.0;
    private Double maxScopeRifle = 6.0;
    private Double maxScopeShotgun = 2.0;
    private Double maxScopeSmg = 3.0;
    private Double maxScopeRpg = 1.5;
    private Double maxScopeMg = 4.0;
    private RoomPosition overworldSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition rangeJoinSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition rangeLeaveSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition parkourStartPos = new RoomPosition(0, 100, 0);
    private RoomPosition parkourEndPos = new RoomPosition(0, 100, 0);
    private String parkourCompletionTitleId = "";
    private String parkourBestTitleId = "";
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

    public Double getMaxScopePistol() {
        return maxScopePistol;
    }

    public void setMaxScopePistol(Double maxScopePistol) {
        this.maxScopePistol = maxScopePistol;
    }

    public Double getMaxScopeSniper() {
        return maxScopeSniper;
    }

    public void setMaxScopeSniper(Double maxScopeSniper) {
        this.maxScopeSniper = maxScopeSniper;
    }

    public Double getMaxScopeRifle() {
        return maxScopeRifle;
    }

    public void setMaxScopeRifle(Double maxScopeRifle) {
        this.maxScopeRifle = maxScopeRifle;
    }

    public Double getMaxScopeShotgun() {
        return maxScopeShotgun;
    }

    public void setMaxScopeShotgun(Double maxScopeShotgun) {
        this.maxScopeShotgun = maxScopeShotgun;
    }

    public Double getMaxScopeSmg() {
        return maxScopeSmg;
    }

    public void setMaxScopeSmg(Double maxScopeSmg) {
        this.maxScopeSmg = maxScopeSmg;
    }

    public Double getMaxScopeRpg() {
        return maxScopeRpg;
    }

    public void setMaxScopeRpg(Double maxScopeRpg) {
        this.maxScopeRpg = maxScopeRpg;
    }

    public Double getMaxScopeMg() {
        return maxScopeMg;
    }

    public void setMaxScopeMg(Double maxScopeMg) {
        this.maxScopeMg = maxScopeMg;
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

    public RoomPosition getParkourStartPos() {
        return parkourStartPos;
    }

    public void setParkourStartPos(RoomPosition parkourStartPos) {
        this.parkourStartPos = parkourStartPos;
    }

    public RoomPosition getParkourEndPos() {
        return parkourEndPos;
    }

    public void setParkourEndPos(RoomPosition parkourEndPos) {
        this.parkourEndPos = parkourEndPos;
    }

    public String getParkourCompletionTitleId() {
        return parkourCompletionTitleId;
    }

    public void setParkourCompletionTitleId(String parkourCompletionTitleId) {
        this.parkourCompletionTitleId = parkourCompletionTitleId;
    }

    public String getParkourBestTitleId() {
        return parkourBestTitleId;
    }

    public void setParkourBestTitleId(String parkourBestTitleId) {
        this.parkourBestTitleId = parkourBestTitleId;
    }

    public RoomVehicleBackend getVehicleBackend() {
        return vehicleBackend;
    }

    public void setVehicleBackend(RoomVehicleBackend vehicleBackend) {
        this.vehicleBackend = vehicleBackend;
    }
}
