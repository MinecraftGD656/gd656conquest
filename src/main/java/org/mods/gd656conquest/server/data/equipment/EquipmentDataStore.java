package org.mods.gd656conquest.server.data.equipment;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.Objects;

public final class EquipmentDataStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private EquipmentDataModel cache;

    public EquipmentDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        EquipmentDataModel data = getData();
        write(ServerDataPaths.getEquipmentDataFile(server), data);
    }

    public synchronized EquipmentDataModel getData() {
        if (cache != null) {
            return cache;
        }
        EquipmentDataModel data = read(ServerDataPaths.getEquipmentDataFile(server), EquipmentDataModel.class, EquipmentDataModel::new);
        data.getEquipmentById();
        data.getDefaultIdBySlot();
        data.getArmorByCampClass();
        cache = data;
        return data;
    }

    public synchronized void saveData(EquipmentDataModel data) {
        EquipmentDataModel resolved = data == null ? new EquipmentDataModel() : data;
        resolved.getEquipmentById();
        resolved.getDefaultIdBySlot();
        resolved.getArmorByCampClass();
        cache = resolved;
        delayedJsonWriteService.schedule(ServerDataPaths.getEquipmentDataFile(server), resolved, server.getTickCount());
    }
}
