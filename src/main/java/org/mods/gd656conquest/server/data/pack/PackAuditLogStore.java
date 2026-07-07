package org.mods.gd656conquest.server.data.pack;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PackAuditLogStore extends JsonDataStoreSupport {
    private static final int MAX_ENTRIES = 1000;

    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private PackAuditLogDataModel cache;

    public PackAuditLogStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getPackLogFile(server), PackAuditLogDataModel.class, PackAuditLogDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized PackAuditLogDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(PackAuditLogDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getPackLogFile(server), model, server.getTickCount());
    }

    public synchronized void append(PackAuditLogEntry entry) {
        PackAuditLogDataModel model = get();
        List<PackAuditLogEntry> entries = new ArrayList<>(model.getEntries());
        entries.add(entry == null ? new PackAuditLogEntry() : entry);
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(Math.max(0, entries.size() - MAX_ENTRIES), entries.size()));
        }
        model.setEntries(entries);
        save(model);
    }

    private void normalize(PackAuditLogDataModel model) {
        model.setEntries(model.getEntries() == null ? List.of() : model.getEntries());
    }
}
