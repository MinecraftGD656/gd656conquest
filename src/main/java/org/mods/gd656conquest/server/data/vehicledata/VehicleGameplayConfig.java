package org.mods.gd656conquest.server.data.vehicledata;

import java.util.ArrayList;
import java.util.List;

public final class VehicleGameplayConfig {
    private List<VehicleConfigEntry> entries = new ArrayList<>();

    public List<VehicleConfigEntry> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public void setEntries(List<VehicleConfigEntry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }
}
