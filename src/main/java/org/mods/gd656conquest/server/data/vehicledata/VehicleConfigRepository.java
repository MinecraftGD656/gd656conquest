package org.mods.gd656conquest.server.data.vehicledata;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VehicleConfigRepository {
    private final VehicleConfigStore store;

    public VehicleConfigRepository(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.store = new VehicleConfigStore(
            Objects.requireNonNull(server, "server"),
            Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService")
        );
    }

    public synchronized void initialize() {
        store.initialize();
    }

    public synchronized VehicleGameplayConfig getConfig(String roomId, String gameplayId, RoomVehicleBackend backend) {
        return store.getConfig(roomId, gameplayId, backend);
    }

    public synchronized List<VehicleConfigEntry> getEntries(String roomId, String gameplayId, RoomVehicleBackend backend) {
        return new ArrayList<>(store.getConfig(roomId, gameplayId, backend).getEntries());
    }

    public synchronized void saveEntries(String roomId, String gameplayId, RoomVehicleBackend backend, List<VehicleConfigEntry> entries) {
        VehicleGameplayConfig config = store.getConfig(roomId, gameplayId, backend);
        config.setEntries(entries);
        store.saveConfig(roomId, gameplayId, backend, config);
    }
}
