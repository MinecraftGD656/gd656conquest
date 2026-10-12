package org.mods.gd656conquest.server.room.vehicle;

import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSnapshot;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigEntry;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class VehicleRuntimeRecord {
    private final String entryId;
    private String configuredVehicleId = "";
    private String configuredDisplayId = "";
    private String configuredTeam = "";
    private int configuredRequiredTeamSize;
    private boolean configuredTeamWideDeployEnabled;
    private Integer originStageIndex;
    private int respawnSeconds = -1;
    private double configuredSpawnX;
    private double configuredSpawnY;
    private double configuredSpawnZ;
    private int configuredSpawnYaw45;
    private VehicleLogicalState logicalState = VehicleLogicalState.READY;
    private VehicleDisplayState displayState = VehicleDisplayState.READY_TO_DEPLOY;
    private VehicleOwnershipState ownershipState = VehicleOwnershipState.FRIENDLY;
    private UUID spawnedEntityUuid;
    private int spawnedEntityId = -1;
    private boolean destroyed;
    private long waitingUntilTick = -1L;
    private long lastSeenTick = -1L;
    private float health;
    private float maxHealth;
    private int passengerCount;
    private UUID driverPlayerId;
    private String driverName = "";
    private final Set<UUID> occupantPlayerIds = new LinkedHashSet<>();
    private double x;
    private double y;
    private double z;

    public VehicleRuntimeRecord(String entryId) {
        this.entryId = entryId == null ? "" : entryId;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getConfiguredVehicleId() {
        return configuredVehicleId;
    }

    public String getConfiguredDisplayId() {
        return configuredDisplayId;
    }

    public String getConfiguredTeam() {
        return configuredTeam;
    }

    public Integer getOriginStageIndex() {
        return originStageIndex;
    }

    public int getConfiguredRequiredTeamSize() {
        return configuredRequiredTeamSize;
    }

    public boolean isConfiguredTeamWideDeployEnabled() {
        return configuredTeamWideDeployEnabled;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public double getConfiguredSpawnX() {
        return configuredSpawnX;
    }

    public double getConfiguredSpawnY() {
        return configuredSpawnY;
    }

    public double getConfiguredSpawnZ() {
        return configuredSpawnZ;
    }

    public int getConfiguredSpawnYaw45() {
        return configuredSpawnYaw45;
    }

    public VehicleLogicalState getLogicalState() {
        return logicalState;
    }

    public void setLogicalState(VehicleLogicalState logicalState) {
        this.logicalState = logicalState == null ? VehicleLogicalState.READY : logicalState;
    }

    public VehicleDisplayState getDisplayState() {
        return displayState;
    }

    public void setDisplayState(VehicleDisplayState displayState) {
        this.displayState = displayState == null ? VehicleDisplayState.READY_TO_DEPLOY : displayState;
    }

    public VehicleOwnershipState getOwnershipState() {
        return ownershipState;
    }

    public void setOwnershipState(VehicleOwnershipState ownershipState) {
        this.ownershipState = ownershipState == null ? VehicleOwnershipState.FRIENDLY : ownershipState;
    }

    public UUID getSpawnedEntityUuid() {
        return spawnedEntityUuid;
    }

    public int getSpawnedEntityId() {
        return spawnedEntityId;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public long getWaitingUntilTick() {
        return waitingUntilTick;
    }

    public void setWaitingUntilTick(long waitingUntilTick) {
        this.waitingUntilTick = waitingUntilTick;
    }

    public void setOccupantPlayerIds(Set<UUID> occupantPlayerIds) {
        this.occupantPlayerIds.clear();
        if (occupantPlayerIds != null) {
            this.occupantPlayerIds.addAll(occupantPlayerIds);
        }
    }

    public long getLastSeenTick() {
        return lastSeenTick;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public UUID getDriverPlayerId() {
        return driverPlayerId;
    }

    public String getDriverName() {
        return driverName;
    }

    public Set<UUID> getOccupantPlayerIds() {
        return occupantPlayerIds;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void applyConfig(VehicleConfigEntry entry) {
        if (entry == null) {
            return;
        }
        configuredVehicleId = entry.getVehicleId();
        configuredDisplayId = entry.getDisplayId();
        configuredTeam = entry.getTeam();
        configuredRequiredTeamSize = entry.getRequiredTeamSize();
        configuredTeamWideDeployEnabled = entry.isTeamWideDeployEnabled();
        originStageIndex = entry.getStageIndex();
        respawnSeconds = entry.getRespawnSeconds();
        configuredSpawnX = entry.getSpawnX();
        configuredSpawnY = entry.getSpawnY();
        configuredSpawnZ = entry.getSpawnZ();
        configuredSpawnYaw45 = entry.getSpawnYaw45();
    }

    public void applySnapshot(VehicleCompatSnapshot snapshot, long gameTick) {
        if (snapshot == null) {
            return;
        }
        spawnedEntityUuid = snapshot.entityUuid();
        spawnedEntityId = snapshot.entityId();
        destroyed = snapshot.destroyed();
        lastSeenTick = gameTick;
        health = snapshot.health();
        maxHealth = snapshot.maxHealth();
        passengerCount = snapshot.passengerCount();
        driverPlayerId = snapshot.driverUuid();
        driverName = snapshot.driverName();
        x = snapshot.x();
        y = snapshot.y();
        z = snapshot.z();
    }

    public void resetForBattalion() {
        clearSpawnedEntity();
        waitingUntilTick = -1;
        lastSeenTick = -1;
        logicalState = VehicleLogicalState.READY;
        displayState = VehicleDisplayState.READY_TO_DEPLOY;
        ownershipState = VehicleOwnershipState.FRIENDLY;
        x = configuredSpawnX;
        y = configuredSpawnY;
        z = configuredSpawnZ;
    }

    public void clearSpawnedEntity() {
        spawnedEntityUuid = null;
        spawnedEntityId = -1;
        destroyed = false;
        health = 0.0F;
        maxHealth = 0.0F;
        passengerCount = 0;
        driverPlayerId = null;
        driverName = "";
        occupantPlayerIds.clear();
    }
}
