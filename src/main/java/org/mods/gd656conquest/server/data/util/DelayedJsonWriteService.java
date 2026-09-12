package org.mods.gd656conquest.server.data.util;

import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DelayedJsonWriteService {
    private static final long DEFAULT_DELAY_TICKS = 5L * 20L;

    private final Map<Path, PendingWrite> pendingWrites = new LinkedHashMap<>();

    public synchronized void schedule(Path path, Object data, long gameTick) {
        schedule(path, data, gameTick, DEFAULT_DELAY_TICKS);
    }

    public synchronized void schedule(Path path, Object data, long gameTick, long delayTicks) {
        if (path == null) {
            return;
        }
        Path normalized = normalize(path);
        long safeDelayTicks = Math.max(0L, delayTicks);
        pendingWrites.put(normalized, new PendingWrite(normalized, data, Math.max(0L, gameTick) + safeDelayTicks));
    }

    public void flushDue(long gameTick) {
        flushMatching(gameTick, false);
    }

    public void flushAll() {
        flushMatching(Long.MAX_VALUE, true);
    }

    private void flushMatching(long gameTick, boolean forceAll) {
        List<PendingWrite> dueWrites = new ArrayList<>();
        synchronized (this) {
            var iterator = pendingWrites.entrySet().iterator();
            while (iterator.hasNext()) {
                PendingWrite write = iterator.next().getValue();
                if (!forceAll && gameTick < write.dueTick()) {
                    continue;
                }
                dueWrites.add(write);
                iterator.remove();
            }
        }
        for (PendingWrite write : dueWrites) {
            if (!writeNow(write.path(), write.data())) {
                synchronized (this) {
                    pendingWrites.put(write.path(), new PendingWrite(write.path(), write.data(), Math.max(0L, gameTick) + DEFAULT_DELAY_TICKS));
                }
            }
        }
    }

    private boolean writeNow(Path path, Object data) {
        try {
            ensurePath(path);
            try (Writer writer = Files.newBufferedWriter(path)) {
                JsonDataStoreSupport.GSON.toJson(data, writer);
            }
            return true;
        } catch (IOException exception) {
            ServerLogger.failure("Failed to write delayed json file: %s", path);
            return false;
        }
    }

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private void ensurePath(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.notExists(path)) {
            Files.createFile(path);
        }
    }

    private record PendingWrite(Path path, Object data, long dueTick) {
        private PendingWrite {
            Objects.requireNonNull(path, "path");
        }
    }
}
