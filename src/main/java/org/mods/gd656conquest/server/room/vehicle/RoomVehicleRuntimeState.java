package org.mods.gd656conquest.server.room.vehicle;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RoomVehicleRuntimeState {
    private final Map<String, VehicleRuntimeRecord> recordsByEntryId = new LinkedHashMap<>();
    private long lastConfigSyncTick = -1L;

    public Map<String, VehicleRuntimeRecord> getRecordsByEntryId() {
        return recordsByEntryId;
    }

    public long getLastConfigSyncTick() {
        return lastConfigSyncTick;
    }

    public void setLastConfigSyncTick(long lastConfigSyncTick) {
        this.lastConfigSyncTick = lastConfigSyncTick;
    }
}
