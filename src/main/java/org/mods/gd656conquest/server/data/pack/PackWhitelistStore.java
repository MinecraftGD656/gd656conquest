package org.mods.gd656conquest.server.data.pack;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.common.pack.PackAuditEntry;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class PackWhitelistStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private PackWhitelistDataModel cache;

    public PackWhitelistStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getPackWhitelistFile(server), PackWhitelistDataModel.class, PackWhitelistDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized PackWhitelistDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(PackWhitelistDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getPackWhitelistFile(server), model, server.getTickCount());
    }

    public synchronized AppendResult appendEntries(List<PackAuditEntry> incomingEntries) {
        PackWhitelistDataModel model = get();
        Map<String, PackAuditEntry> index = toIdentityIndex(model.getEntries());
        int addedCount = 0;
        int existingCount = 0;
        for (PackAuditEntry entry : safeEntries(incomingEntries)) {
            PackAuditEntry normalized = normalizeEntry(entry);
            if (!normalized.isValid()) {
                continue;
            }
            if (index.putIfAbsent(normalized.identityKey(), normalized) == null) {
                addedCount++;
            } else {
                existingCount++;
            }
        }
        model.setEntries(new ArrayList<>(index.values()));
        save(model);
        return new AppendResult(addedCount, existingCount, model.getEntries().size());
    }

    public synchronized List<PackAuditEntry> removeByFileName(String rawFileName) {
        String normalizedFileName = normalizeText(rawFileName);
        if (normalizedFileName.isBlank()) {
            return List.of();
        }
        PackWhitelistDataModel model = get();
        List<PackAuditEntry> retained = new ArrayList<>();
        List<PackAuditEntry> removed = new ArrayList<>();
        for (PackAuditEntry entry : safeEntries(model.getEntries())) {
            PackAuditEntry normalized = normalizeEntry(entry);
            if (!normalized.isValid()) {
                continue;
            }
            if (normalizeText(normalized.getFileName()).equals(normalizedFileName)) {
                removed.add(normalized);
            } else {
                retained.add(normalized);
            }
        }
        model.setEntries(retained);
        save(model);
        return removed;
    }

    public synchronized List<String> suggestFileNames() {
        List<String> names = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (PackAuditEntry entry : safeEntries(get().getEntries())) {
            PackAuditEntry normalized = normalizeEntry(entry);
            if (!normalized.isValid()) {
                continue;
            }
            seen.putIfAbsent(normalizeText(normalized.getFileName()), normalized.getFileName());
        }
        names.addAll(seen.values());
        return List.copyOf(names);
    }

    public synchronized boolean contains(PackAuditEntry entry) {
        if (entry == null) {
            return false;
        }
        PackAuditEntry normalized = normalizeEntry(entry);
        if (!normalized.isValid()) {
            return false;
        }
        return toIdentityIndex(get().getEntries()).containsKey(normalized.identityKey());
    }

    private void normalize(PackWhitelistDataModel model) {
        model.setEntries(new ArrayList<>(toIdentityIndex(model.getEntries()).values()));
    }

    private static Map<String, PackAuditEntry> toIdentityIndex(List<PackAuditEntry> entries) {
        Map<String, PackAuditEntry> index = new LinkedHashMap<>();
        for (PackAuditEntry entry : safeEntries(entries)) {
            PackAuditEntry normalized = normalizeEntry(entry);
            if (!normalized.isValid()) {
                continue;
            }
            index.putIfAbsent(normalized.identityKey(), normalized);
        }
        return index;
    }

    private static List<PackAuditEntry> safeEntries(List<PackAuditEntry> entries) {
        return entries == null ? List.of() : entries;
    }

    private static PackAuditEntry normalizeEntry(PackAuditEntry entry) {
        if (entry == null) {
            return new PackAuditEntry();
        }
        return new PackAuditEntry(
            entry.getType(),
            entry.getFileName(),
            normalizeText(entry.getSha256()),
            Math.max(0L, entry.getSize()),
            entry.getSourceKind()
        );
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record AppendResult(int addedCount, int existingCount, int totalCount) {
    }
}
