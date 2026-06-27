package org.mods.gd656conquest.server.title;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PlayerTitleRegistryStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private RegistryData cache;
    private boolean initialized;

    public PlayerTitleRegistryStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        Path path = ServerDataPaths.getTitleRegistryFile(server);
        cache = read(path, RegistryData.class, RegistryData::new).normalized();
        initialized = true;
    }

    public synchronized Map<String, TitleDefinition> getAll() {
        initialize();
        return cache.toDefinitions();
    }

    public synchronized Optional<TitleDefinition> find(String titleId) {
        initialize();
        String normalized = PlayerTitleService.normalizeTitleId(titleId);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        TitleEntry entry = cache.titles.get(normalized);
        if (entry == null || entry.text == null || entry.text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new TitleDefinition(normalized, entry.text));
    }

    public synchronized boolean saveTitle(String titleId, String rawText) {
        initialize();
        String normalized = PlayerTitleService.normalizeTitleId(titleId);
        String text = rawText == null ? "" : rawText;
        if (normalized.isBlank() || text.isBlank()) {
            throw new IllegalArgumentException("称号 ID 或内容不能为空");
        }
        TitleEntry previous = cache.titles.putIfAbsent(normalized, new TitleEntry(text));
        if (previous != null) {
            return false;
        }
        persist();
        return true;
    }

    public synchronized boolean updateTitle(String titleId, String rawText) {
        initialize();
        String normalized = PlayerTitleService.normalizeTitleId(titleId);
        String text = rawText == null ? "" : rawText;
        if (normalized.isBlank() || text.isBlank()) {
            throw new IllegalArgumentException("称号 ID 或内容不能为空");
        }
        TitleEntry previous = cache.titles.get(normalized);
        if (previous == null) {
            return false;
        }
        if (text.equals(previous.text)) {
            return true;
        }
        previous.text = text;
        persist();
        return true;
    }

    private void persist() {
        delayedJsonWriteService.schedule(ServerDataPaths.getTitleRegistryFile(server), cache.normalized(), server.getTickCount());
    }

    public record TitleDefinition(String id, String text) {
    }

    private static final class RegistryData {
        private Map<String, TitleEntry> titles = new LinkedHashMap<>();

        private RegistryData normalized() {
            RegistryData normalized = new RegistryData();
            if (titles != null) {
                for (Map.Entry<String, TitleEntry> entry : titles.entrySet()) {
                    String id = PlayerTitleService.normalizeTitleId(entry.getKey());
                    if (id.isBlank() || entry.getValue() == null || entry.getValue().text == null || entry.getValue().text.isBlank()) {
                        continue;
                    }
                    normalized.titles.put(id, new TitleEntry(entry.getValue().text));
                }
            }
            return normalized;
        }

        private Map<String, TitleDefinition> toDefinitions() {
            Map<String, TitleDefinition> result = new LinkedHashMap<>();
            for (Map.Entry<String, TitleEntry> entry : titles.entrySet()) {
                if (entry.getValue() == null || entry.getValue().text == null || entry.getValue().text.isBlank()) {
                    continue;
                }
                result.put(entry.getKey(), new TitleDefinition(entry.getKey(), entry.getValue().text));
            }
            return Map.copyOf(result);
        }
    }

    private static final class TitleEntry {
        private String text;

        private TitleEntry() {
        }

        private TitleEntry(String text) {
            this.text = text;
        }
    }
}
