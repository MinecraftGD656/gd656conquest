package org.mods.gd656conquest.server.data.shelling;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ShellingDataStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private ShellingDataModel cache;

    public ShellingDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getShellingDataFile(server), ShellingDataModel.class, ShellingDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized ShellingDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(ShellingDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getShellingDataFile(server), model, server.getTickCount());
    }

    public synchronized ShellingDataModel reloadFromDisk() {
        cache = read(ServerDataPaths.getShellingDataFile(server), ShellingDataModel.class, ShellingDataModel::new);
        normalize(cache);
        return cache;
    }

    private void normalize(ShellingDataModel model) {
        if (model == null) {
            return;
        }
        model.setVersion(model.getVersion());
        Map<String, ShellingDataModel.ShellingEntry> source = model.getShellingsById();
        Map<String, ShellingDataModel.ShellingEntry> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ShellingDataModel.ShellingEntry> entry : source.entrySet()) {
            String rawId = entry.getKey();
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String normalizedId = rawId.trim();
            ShellingDataModel.ShellingEntry resolved = entry.getValue();
            if (resolved == null) {
                resolved = new ShellingDataModel.ShellingEntry();
            }
            resolved.setId(resolved.getId().isBlank() ? normalizedId : resolved.getId());
            resolved.setShellHeight(resolved.getShellHeight());
            resolved.setDurationSeconds(resolved.getDurationSeconds());
            resolved.setIntervalSeconds(resolved.getIntervalSeconds());
            resolved.setRadius(resolved.getRadius());
            resolved.setCost(resolved.getCost());
            resolved.setPitch(resolved.getPitch());
            resolved.setYaw(resolved.getYaw());
            resolved.setGunItemSnbt(resolved.getGunItemSnbt());
            resolved.setGunId(resolved.getGunId());
            resolved.setGunDisplayId(resolved.getGunDisplayId());
            resolved.setGunDisplayName(resolved.getGunDisplayName());
            resolved.setDisplayName(resolved.getDisplayName());
            normalized.put(normalizedId, resolved);
        }
        model.setShellingsById(normalized);
    }
}
