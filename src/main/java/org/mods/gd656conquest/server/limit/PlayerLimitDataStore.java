package org.mods.gd656conquest.server.limit;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.Objects;

/**
 * GD656Mods: 服务器人数限制配置的读写(独立 json)。
 *
 * <p>沿用项目里其它 Store 的写法: {@link JsonDataStoreSupport} 负责读写,
 * {@link DelayedJsonWriteService} 负责延迟落盘, 内存里保留一份 cache。
 */
public final class PlayerLimitDataStore extends JsonDataStoreSupport {
    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_MAX_PLAYERS = 128;
    public static final String DEFAULT_REASON = "服务器已满！请稍后重试";
    /** GD656Mods: 上限的合法区间, 与命令参数范围保持一致。 */
    public static final int MIN_MAX_PLAYERS = 1;
    public static final int MAX_MAX_PLAYERS = 4096;

    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private PlayerLimitDataModel cache;

    public PlayerLimitDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getPlayerLimitFile(server), PlayerLimitDataModel.class, PlayerLimitDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized PlayerLimitDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(PlayerLimitDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getPlayerLimitFile(server), model, server.getTickCount());
    }

    public synchronized boolean isEnabled() {
        return Boolean.TRUE.equals(get().getEnabled());
    }

    public synchronized int getMaxPlayers() {
        return sanitizeMaxPlayers(get().getMaxPlayers());
    }

    public synchronized String getReason() {
        return sanitizeReason(get().getReason());
    }

    public synchronized void setEnabled(boolean enabled) {
        PlayerLimitDataModel model = get();
        model.setEnabled(enabled);
        save(model);
    }

    public synchronized void setMaxPlayers(int maxPlayers) {
        PlayerLimitDataModel model = get();
        model.setMaxPlayers(maxPlayers);
        save(model);
    }

    public synchronized void setReason(String reason) {
        PlayerLimitDataModel model = get();
        model.setReason(reason);
        save(model);
    }

    /** GD656Mods: 上限取值约束(默认 128, 1..4096)。 */
    public static int sanitizeMaxPlayers(Integer value) {
        if (value == null) {
            return DEFAULT_MAX_PLAYERS;
        }
        return Math.max(MIN_MAX_PLAYERS, Math.min(MAX_MAX_PLAYERS, value));
    }

    /** GD656Mods: 提示文案约束 —— 空白时回落到默认文案。 */
    public static String sanitizeReason(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_REASON;
        }
        return value.trim();
    }

    private void normalize(PlayerLimitDataModel model) {
        model.setEnabled(Boolean.TRUE.equals(model.getEnabled()));
        model.setMaxPlayers(sanitizeMaxPlayers(model.getMaxPlayers()));
        model.setReason(sanitizeReason(model.getReason()));
    }
}
