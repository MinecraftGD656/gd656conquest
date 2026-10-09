package org.mods.gd656conquest.server.data.equipmentset;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EquipmentSetDataStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private EquipmentSetDataModel cache;

    public EquipmentSetDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getEquipmentSetDataFile(server), EquipmentSetDataModel.class, EquipmentSetDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized EquipmentSetDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(EquipmentSetDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getEquipmentSetDataFile(server), model, server.getTickCount());
    }

    public synchronized EquipmentSetDataModel reloadFromDisk() {
        cache = read(ServerDataPaths.getEquipmentSetDataFile(server), EquipmentSetDataModel.class, EquipmentSetDataModel::new);
        normalize(cache);
        return cache;
    }

    private void normalize(EquipmentSetDataModel model) {
        if (model == null) {
            return;
        }
        model.setVersion(model.getVersion());
        Map<String, EquipmentSetDataModel.EquipmentSetEntry> source = model.getEntriesById();
        Map<String, EquipmentSetDataModel.EquipmentSetEntry> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, EquipmentSetDataModel.EquipmentSetEntry> entry : source.entrySet()) {
            String rawId = entry.getKey();
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String normalizedId = rawId.trim();
            EquipmentSetDataModel.EquipmentSetEntry resolved = entry.getValue();
            if (resolved == null) {
                resolved = new EquipmentSetDataModel.EquipmentSetEntry();
            }
            resolved.setId(resolved.getId().isBlank() ? normalizedId : resolved.getId());
            resolved.setHeadItemSnbt(resolved.getHeadItemSnbt());
            resolved.setChestItemSnbt(resolved.getChestItemSnbt());
            resolved.setLegsItemSnbt(resolved.getLegsItemSnbt());
            resolved.setFeetItemSnbt(resolved.getFeetItemSnbt());
            normalized.put(normalizedId, resolved);
        }
        normalized.put(EquipmentSetDataModel.EMPTY_ID, EquipmentSetDataModel.emptyEntry());
        model.setEntriesById(normalized);
    }
}
