package org.mods.gd656conquest.server.data.roomstatisticdata;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class RoomStatisticDataStore extends JsonDataStoreSupport {
    private static final int DEFAULT_ROOM_COUNT = 32;
    public static final long ROOM_RATING_WRITE_DELAY_TICKS = 30L * 20L;

    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private final Map<String, RoomStatisticDataModel> cache = new LinkedHashMap<>();
    private boolean initialized;

    public RoomStatisticDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        ensureDirectory(ServerDataPaths.getRoomStatisticDataDirectory(server));
        cache.clear();
        for (String roomId : defaultRoomIds()) {
            RoomStatisticDataModel model = loadOrCreate(roomId);
            RoomStatisticDataModel normalized = normalize(roomId, model);
            cache.put(normalized.getRoomId(), normalized);
            save(normalized);
        }
        initialized = true;
    }

    public synchronized RoomStatisticDataModel getRoomStatistics(String roomId) {
        ensureInitialized();
        String normalizedRoomId = normalizeRoomId(roomId);
        RoomStatisticDataModel cached = cache.get(normalizedRoomId);
        if (cached != null) {
            return cached;
        }
        RoomStatisticDataModel created = normalize(normalizedRoomId, null);
        cache.put(normalizedRoomId, created);
        save(created);
        return created;
    }

    public synchronized List<RoomStatisticDataModel> getAllRoomStatistics() {
        ensureInitialized();
        return new ArrayList<>(cache.values());
    }

    public synchronized RoomStatisticDataModel saveRoomStatistics(RoomStatisticDataModel model) {
        return saveRoomStatistics(model, 5L * 20L);
    }

    public synchronized RoomStatisticDataModel saveRoomStatistics(RoomStatisticDataModel model, long delayTicks) {
        ensureInitialized();
        String normalizedRoomId = normalizeRoomId(model == null ? null : model.getRoomId());
        RoomStatisticDataModel normalized = normalize(normalizedRoomId, model);
        cache.put(normalizedRoomId, normalized);
        save(normalized, delayTicks);
        return normalized;
    }

    public synchronized void saveAll(Collection<RoomStatisticDataModel> models) {
        ensureInitialized();
        if (models == null) {
            return;
        }
        for (RoomStatisticDataModel model : models) {
            saveRoomStatistics(model);
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private RoomStatisticDataModel loadOrCreate(String roomId) {
        Path path = ServerDataPaths.getRoomStatisticDataFile(server, roomId);
        RoomStatisticDataModel model = read(path, RoomStatisticDataModel.class, RoomStatisticDataModel::new);
        return normalize(roomId, model);
    }

    private void save(RoomStatisticDataModel model) {
        save(model, 5L * 20L);
    }

    private void save(RoomStatisticDataModel model, long delayTicks) {
        delayedJsonWriteService.schedule(
            ServerDataPaths.getRoomStatisticDataFile(server, model.getRoomId()),
            model,
            server.getTickCount(),
            delayTicks
        );
    }

    private RoomStatisticDataModel normalize(String roomId, RoomStatisticDataModel model) {
        RoomStatisticDataModel resolved = model == null ? new RoomStatisticDataModel() : model;
        resolved.setRoomId(normalizeRoomId(roomId == null ? resolved.getRoomId() : roomId));
        resolved.setTotalPlayCount(Math.max(0, resolved.getTotalPlayCount() == null ? 0 : resolved.getTotalPlayCount()));
        resolved.setTotalMatches(Math.max(0, resolved.getTotalMatches() == null ? 0 : resolved.getTotalMatches()));
        resolved.setCampAWins(Math.max(0, resolved.getCampAWins() == null ? 0 : resolved.getCampAWins()));
        resolved.setCampBWins(Math.max(0, resolved.getCampBWins() == null ? 0 : resolved.getCampBWins()));
        resolved.setDrawMatches(Math.max(0, resolved.getDrawMatches() == null ? 0 : resolved.getDrawMatches()));
        resolved.setTotalMatchDurationSeconds(Math.max(0L, resolved.getTotalMatchDurationSeconds() == null ? 0L : resolved.getTotalMatchDurationSeconds()));
        resolved.setRatingVoteCount(Math.max(0, resolved.getRatingVoteCount() == null ? 0 : resolved.getRatingVoteCount()));
        resolved.setRatingScoreTotal(Math.max(0, resolved.getRatingScoreTotal() == null ? 0 : resolved.getRatingScoreTotal()));
        return resolved;
    }

    private List<String> defaultRoomIds() {
        List<String> result = new ArrayList<>(DEFAULT_ROOM_COUNT);
        for (int i = 1; i <= DEFAULT_ROOM_COUNT; i++) {
            result.add(String.format(Locale.ROOT, "room_%02d", i));
        }
        return result;
    }

    private String normalizeRoomId(String roomId) {
        String normalized = roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("room_0[1-9]") || normalized.matches("room_[12]\\d") || "room_30".equals(normalized) || "room_31".equals(normalized) || "room_32".equals(normalized)) {
            return normalized;
        }
        if (normalized.matches("room0[1-9]") || normalized.matches("room[12]\\d") || "room30".equals(normalized) || "room31".equals(normalized) || "room32".equals(normalized)) {
            return "room_" + normalized.substring(normalized.length() - 2);
        }
        throw new IllegalArgumentException("房间ID格式无效，必须为 Room_01~Room_32: " + roomId);
    }
}
