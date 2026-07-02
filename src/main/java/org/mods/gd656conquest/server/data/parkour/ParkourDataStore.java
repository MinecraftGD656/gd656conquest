package org.mods.gd656conquest.server.data.parkour;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ParkourDataStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private ParkourDataModel cache;

    public ParkourDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getParkourDataFile(server), ParkourDataModel.class, ParkourDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized ParkourDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(ParkourDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getParkourDataFile(server), model, server.getTickCount());
    }

    private void normalize(ParkourDataModel model) {
        if (model.getPlayerRecords() == null) {
            model.setPlayerRecords(new LinkedHashMap<>());
            return;
        }
        Map<String, ParkourDataModel.PlayerRecord> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ParkourDataModel.PlayerRecord> entry : model.getPlayerRecords().entrySet()) {
            String uuid = entry.getKey();
            if (uuid == null || uuid.isBlank()) {
                continue;
            }
            ParkourDataModel.PlayerRecord record = entry.getValue();
            if (record == null) {
                record = new ParkourDataModel.PlayerRecord();
            }
            record.setPlayerName(record.getPlayerName());
            record.setBestTimeMillis(sanitizeTime(record.getBestTimeMillis()));
            record.setLastTimeMillis(sanitizeTime(record.getLastTimeMillis()));
            record.setBestUpdatedAtEpochMs(Math.max(0L, record.getBestUpdatedAtEpochMs() == null ? 0L : record.getBestUpdatedAtEpochMs()));
            record.setLastUpdatedAtEpochMs(Math.max(0L, record.getLastUpdatedAtEpochMs() == null ? 0L : record.getLastUpdatedAtEpochMs()));
            normalized.put(uuid, record);
        }
        model.setPlayerRecords(normalized);
    }

    private long sanitizeTime(Long value) {
        if (value == null || value <= 0L) {
            return 0L;
        }
        return value;
    }
}
