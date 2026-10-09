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
        LinkedHashMap<String, PackAuditLogEntry> byPlayer = toPlayerIndex(model.getEntries());
        PackAuditLogEntry incoming = entry == null ? new PackAuditLogEntry() : entry;
        String key = keyOf(incoming);
        if (key.isBlank()) {
            return;
        }
        PackAuditLogEntry merged = byPlayer.containsKey(key)
            ? merge(byPlayer.get(key), incoming)
            : normalizeEntry(incoming);
        byPlayer.put(key, merged);
        List<PackAuditLogEntry> entries = new ArrayList<>(byPlayer.values());
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(Math.max(0, entries.size() - MAX_ENTRIES), entries.size()));
        }
        model.setEntries(entries);
        save(model);
    }

    private void normalize(PackAuditLogDataModel model) {
        model.setEntries(new ArrayList<>(toPlayerIndex(model.getEntries()).values()));
    }

    private LinkedHashMap<String, PackAuditLogEntry> toPlayerIndex(List<PackAuditLogEntry> entries) {
        LinkedHashMap<String, PackAuditLogEntry> index = new LinkedHashMap<>();
        for (PackAuditLogEntry entry : entries == null ? List.<PackAuditLogEntry>of() : entries) {
            PackAuditLogEntry normalized = normalizeEntry(entry);
            String key = keyOf(normalized);
            if (key.isBlank()) {
                continue;
            }
            index.merge(key, normalized, this::merge);
        }
        return index;
    }

    private PackAuditLogEntry merge(PackAuditLogEntry base, PackAuditLogEntry incoming) {
        PackAuditLogEntry left = normalizeEntry(base);
        PackAuditLogEntry right = normalizeEntry(incoming);
        PackAuditLogEntry merged = new PackAuditLogEntry();
        merged.setPlayerName(!right.getPlayerName().isBlank() ? right.getPlayerName() : left.getPlayerName());
        merged.setPlayerUuid(!right.getPlayerUuid().isBlank() ? right.getPlayerUuid() : left.getPlayerUuid());
        merged.setKickTime(!right.getKickTime().isBlank() ? right.getKickTime() : left.getKickTime());
        merged.setTrigger(!right.getTrigger().isBlank() ? right.getTrigger() : left.getTrigger());
        merged.setReason(!right.getReason().isBlank() ? right.getReason() : left.getReason());
        merged.setDisconnectMessage(!right.getDisconnectMessage().isBlank() ? right.getDisconnectMessage() : left.getDisconnectMessage());
        merged.setViolationTimes(mergeTimes(left.getViolationTimes(), left.getKickTime(), right.getViolationTimes(), right.getKickTime()));
        merged.setIllegalEntries(mergePackEntries(left.getIllegalEntries(), right.getIllegalEntries()));
        merged.setMissingWhitelistEntries(mergePackEntries(left.getMissingWhitelistEntries(), right.getMissingWhitelistEntries()));
        return merged;
    }

    private PackAuditLogEntry normalizeEntry(PackAuditLogEntry source) {
        PackAuditLogEntry entry = source == null ? new PackAuditLogEntry() : source;
        PackAuditLogEntry normalized = new PackAuditLogEntry();
        normalized.setPlayerName(trim(entry.getPlayerName()));
        normalized.setPlayerUuid(trim(entry.getPlayerUuid()));
        normalized.setKickTime(trim(entry.getKickTime()));
        normalized.setTrigger(trim(entry.getTrigger()));
        normalized.setReason(trim(entry.getReason()));
        normalized.setDisconnectMessage(trim(entry.getDisconnectMessage()));
        normalized.setViolationTimes(mergeTimes(entry.getViolationTimes(), entry.getKickTime(), List.of(), ""));
        normalized.setIllegalEntries(mergePackEntries(entry.getIllegalEntries(), List.of()));
        normalized.setMissingWhitelistEntries(mergePackEntries(entry.getMissingWhitelistEntries(), List.of()));
        return normalized;
    }

    private static List<String> mergeTimes(List<String> first, String firstKickTime, List<String> second, String secondKickTime) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        addTime(merged, firstKickTime);
        for (String value : first == null ? List.<String>of() : first) {
            addTime(merged, value);
        }
        addTime(merged, secondKickTime);
        for (String value : second == null ? List.<String>of() : second) {
            addTime(merged, value);
        }
        return new ArrayList<>(merged.values());
    }

    private static void addTime(Map<String, String> target, String value) {
        String normalized = trim(value);
        if (!normalized.isBlank()) {
            target.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
    }

    private static List<PackAuditEntry> mergePackEntries(List<PackAuditEntry> first, List<PackAuditEntry> second) {
        LinkedHashMap<String, PackAuditEntry> merged = new LinkedHashMap<>();
        for (PackAuditEntry entry : first == null ? List.<PackAuditEntry>of() : first) {
            if (entry != null && entry.isValid()) {
                merged.putIfAbsent(entry.identityKey(), entry);
            }
        }
        for (PackAuditEntry entry : second == null ? List.<PackAuditEntry>of() : second) {
            if (entry != null && entry.isValid()) {
                merged.putIfAbsent(entry.identityKey(), entry);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static String keyOf(PackAuditLogEntry entry) {
        if (entry == null) {
            return "";
        }
        String playerUuid = trim(entry.getPlayerUuid());
        if (!playerUuid.isBlank()) {
            return "uuid:" + playerUuid.toLowerCase(Locale.ROOT);
        }
        String playerName = trim(entry.getPlayerName());
        return playerName.isBlank() ? "" : "name:" + playerName.toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
