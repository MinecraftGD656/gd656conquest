package org.mods.gd656conquest.server.data.vehicledata;

public final class VehicleConfigEntry {
    private String entryId = "";
    private String gameplay = "";
    private String team = "";
    private String vehicleId = "";
    private String ywzjVehicleId = "";
    // Serialized field name is kept as displayId for backward compatibility.
    // Semantically this is a custom GUI display string, not a vehicle API resource id.
    private String displayId = "";
    private int requiredTeamSize = 0;
    private int respawnSeconds = -1;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private int spawnYaw45;
    private Integer stageIndex;
    private boolean teamWideDeployEnabled;
    private boolean enabled = true;

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId == null ? "" : entryId.trim();
    }

    public String getGameplay() {
        return gameplay;
    }

    public void setGameplay(String gameplay) {
        this.gameplay = gameplay == null ? "" : gameplay.trim();
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team == null ? "" : team.trim();
    }

    public String getVehicleId() {
        return vehicleId == null || vehicleId.isBlank() ? getYwzjVehicleId() : vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId == null ? "" : vehicleId.trim();
    }

    public String getYwzjVehicleId() {
        return ywzjVehicleId;
    }

    public void setYwzjVehicleId(String ywzjVehicleId) {
        this.ywzjVehicleId = ywzjVehicleId == null ? "" : ywzjVehicleId.trim();
        if (this.vehicleId == null || this.vehicleId.isBlank()) {
            this.vehicleId = this.ywzjVehicleId;
        }
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String displayId) {
        this.displayId = displayId == null ? "" : displayId.trim();
    }

    public String getDisplayName() {
        return displayId;
    }

    public void setDisplayName(String displayName) {
        setDisplayId(displayName);
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public int getRequiredTeamSize() {
        return requiredTeamSize;
    }

    public void setRequiredTeamSize(int requiredTeamSize) {
        this.requiredTeamSize = Math.max(0, requiredTeamSize);
    }

    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(double spawnX) {
        this.spawnX = spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public void setSpawnY(double spawnY) {
        this.spawnY = spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public void setSpawnZ(double spawnZ) {
        this.spawnZ = spawnZ;
    }

    public int getSpawnYaw45() {
        return spawnYaw45;
    }

    public void setSpawnYaw45(int spawnYaw45) {
        this.spawnYaw45 = spawnYaw45;
    }

    public Integer getStageIndex() {
        return stageIndex;
    }

    public void setStageIndex(Integer stageIndex) {
        this.stageIndex = stageIndex;
    }

    public boolean isTeamWideDeployEnabled() {
        return teamWideDeployEnabled;
    }

    public void setTeamWideDeployEnabled(boolean teamWideDeployEnabled) {
        this.teamWideDeployEnabled = teamWideDeployEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
