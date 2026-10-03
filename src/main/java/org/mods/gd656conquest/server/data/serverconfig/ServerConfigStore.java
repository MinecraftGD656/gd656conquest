package org.mods.gd656conquest.server.data.serverconfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.title.PlayerTitleService;

import java.util.Objects;

public final class ServerConfigStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private ServerConfigDataModel cache;

    public ServerConfigStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getServerConfigFile(server), ServerConfigDataModel.class, ServerConfigDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized ServerConfigDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(ServerConfigDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getServerConfigFile(server), model, server.getTickCount());
    }

    public synchronized boolean isOverworldInvulnerableEnabled() {
        return Boolean.TRUE.equals(get().getBoolOverworldInvulnerable());
    }

    public synchronized boolean isConquestDimensionRestricted() {
        return Boolean.TRUE.equals(get().getBoolRestrictConquestDimension());
    }

    public synchronized boolean isNaturalRegenerationEnabled() {
        return Boolean.TRUE.equals(get().getBoolNaturalRegeneration());
    }

    public synchronized int getPassiveRegenIntervalTicks() {
        return Math.max(1, get().getPassiveRegenIntervalTicks() == null ? 3 : get().getPassiveRegenIntervalTicks());
    }

    public synchronized int getPassiveRegenStartDelaySeconds() {
        return Math.max(0, get().getPassiveRegenStartDelaySeconds() == null ? 6 : get().getPassiveRegenStartDelaySeconds());
    }

    public synchronized double getMaxScopePistol() {
        return sanitizeScopeLimit(get().getMaxScopePistol(), 2.0D);
    }

    public synchronized double getMaxScopeSniper() {
        return sanitizeScopeLimit(get().getMaxScopeSniper(), 12.0D);
    }

    public synchronized double getMaxScopeRifle() {
        return sanitizeScopeLimit(get().getMaxScopeRifle(), 6.0D);
    }

    public synchronized double getMaxScopeShotgun() {
        return sanitizeScopeLimit(get().getMaxScopeShotgun(), 2.0D);
    }

    public synchronized double getMaxScopeSmg() {
        return sanitizeScopeLimit(get().getMaxScopeSmg(), 3.0D);
    }

    public synchronized double getMaxScopeRpg() {
        return sanitizeScopeLimit(get().getMaxScopeRpg(), 1.5D);
    }

    public synchronized double getMaxScopeMg() {
        return sanitizeScopeLimit(get().getMaxScopeMg(), 4.0D);
    }

    public synchronized RoomVehicleBackend getVehicleBackend() {
        RoomVehicleBackend configured = get().getVehicleBackend();
        return configured == null ? RoomVehicleBackend.defaultValue() : configured;
    }

    public synchronized boolean isExperimentalPlayerBalanceEnabled() {
        return Boolean.TRUE.equals(get().getBoolEnableExperimentalPlayerBalance());
    }

    public synchronized boolean isMaintenanceModeEnabled() {
        return Boolean.TRUE.equals(get().getBoolMaintenanceMode());
    }

    public synchronized int getDroneBatteryDurationSeconds() {
        Integer value = get().getDroneBatteryDurationSeconds();
        return value == null || value <= 0 ? 60 : value;
    }

    public synchronized int getKamikazeDroneBatteryDurationSeconds() {
        Integer value = get().getKamikazeDroneBatteryDurationSeconds();
        return value == null || value <= 0 ? 30 : value;
    }

    /** GD656Mods: 玩家等级上限; 默认 100, 允许 1..1024。 */
    public synchronized int getMaxPlayerLevel() {
        return sanitizeMaxPlayerLevel(get().getMaxPlayerLevel());
    }

    /** GD656Mods: 等级上限取值约束。 */
    public static int sanitizeMaxPlayerLevel(Integer value) {
        if (value == null) {
            return 100;
        }
        return Math.max(1, Math.min(1024, value));
    }

    /** GD656Mods: 最大活跃房间数; 0 = 不限制, 上限 64。 */
    public synchronized int getMaxActiveRooms() {
        return sanitizeMaxActiveRooms(get().getMaxActiveRooms());
    }

    /** GD656Mods: 最大活跃房间数取值约束 —— 负数一律视为"禁用限制"(-1), 0 表示不允许任何对局, 上限 64。 */
    public static int sanitizeMaxActiveRooms(Integer value) {
        if (value == null) {
            return -1;
        }
        if (value < 0) {
            return -1;
        }
        return Math.min(64, value);
    }

    public synchronized int getAmmoBagTaczPistol() {
        Integer value = get().getAmmoBagTaczPistol();
        return value == null ? 6 : Math.max(0, value);
    }

    public synchronized int getAmmoBagTaczSmg() {
        Integer value = get().getAmmoBagTaczSmg();
        return value == null ? 15 : Math.max(0, value);
    }

    public synchronized int getAmmoBagTaczRifle() {
        Integer value = get().getAmmoBagTaczRifle();
        return value == null ? 10 : Math.max(0, value);
    }

    public synchronized int getAmmoBagTaczSniper() {
        Integer value = get().getAmmoBagTaczSniper();
        return value == null ? 3 : Math.max(0, value);
    }

    public synchronized int getAmmoBagTaczShotgun() {
        Integer value = get().getAmmoBagTaczShotgun();
        return value == null ? 4 : Math.max(0, value);
    }

    public synchronized int getAmmoBagTaczMg() {
        Integer value = get().getAmmoBagTaczMg();
        return value == null ? 20 : Math.max(0, value);
    }

    public synchronized int getAmmoBagTaczRpg() {
        Integer value = get().getAmmoBagTaczRpg();
        return value == null ? 1 : Math.max(0, value);
    }

    public synchronized int getAmmoBagSbw() {
        Integer value = get().getAmmoBagSbw();
        return value == null ? 1 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagTaczPistol() {
        Integer value = get().getSupportAmmoBagTaczPistol();
        return value == null ? 6 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagTaczSmg() {
        Integer value = get().getSupportAmmoBagTaczSmg();
        return value == null ? 15 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagTaczRifle() {
        Integer value = get().getSupportAmmoBagTaczRifle();
        return value == null ? 10 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagTaczSniper() {
        Integer value = get().getSupportAmmoBagTaczSniper();
        return value == null ? 3 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagTaczShotgun() {
        Integer value = get().getSupportAmmoBagTaczShotgun();
        return value == null ? 4 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagTaczMg() {
        Integer value = get().getSupportAmmoBagTaczMg();
        return value == null ? 20 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagTaczRpg() {
        Integer value = get().getSupportAmmoBagTaczRpg();
        return value == null ? 1 : Math.max(0, value);
    }

    public synchronized int getSupportAmmoBagSbw() {
        Integer value = get().getSupportAmmoBagSbw();
        return value == null ? 1 : Math.max(0, value);
    }

    public synchronized double getMissileKillRadius() {
        Double value = get().getMissileKillRadius();
        return value == null || value < 0.0 ? 35.0 : value;
    }

    public synchronized double getGroundSensorScanRadius() {
        Double value = get().getGroundSensorScanRadius();
        return value == null || Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D ? 10.0D : value;
    }

    public synchronized int getGroundSensorScanIntervalSeconds() {
        Integer value = get().getGroundSensorScanIntervalSeconds();
        return value == null || value <= 0 ? 4 : value;
    }

    public synchronized int getGroundSensorMarkDurationSeconds() {
        Integer value = get().getGroundSensorMarkDurationSeconds();
        return value == null || value <= 0 ? 2 : value;
    }

    public synchronized int getGroundSensorScanBonusCooldownSeconds() {
        Integer value = get().getGroundSensorScanBonusCooldownSeconds();
        return value == null || value <= 0 ? 10 : value;
    }

    public synchronized RoomPosition getOverworldSpawnFallbackShared() {
        RoomPosition configured = get().getOverworldSpawn();
        if (configured != null) {
            return configured;
        }
        BlockPos shared = server.overworld().getSharedSpawnPos();
        return new RoomPosition(shared.getX(), shared.getY(), shared.getZ());
    }

    public synchronized RoomPosition getRangeJoinSpawnFallbackShared() {
        RoomPosition configured = get().getRangeJoinSpawn();
        if (configured != null) {
            return configured;
        }
        return getOverworldSpawnFallbackShared();
    }

    public synchronized RoomPosition getRangeLeaveSpawnFallbackShared() {
        RoomPosition configured = get().getRangeLeaveSpawn();
        if (configured != null) {
            return configured;
        }
        return getOverworldSpawnFallbackShared();
    }

    public synchronized RoomPosition getParkourStartPosFallbackShared() {
        RoomPosition configured = get().getParkourStartPos();
        if (configured != null) {
            return configured;
        }
        return getOverworldSpawnFallbackShared();
    }

    public synchronized RoomPosition getParkourEndPosFallbackShared() {
        RoomPosition configured = get().getParkourEndPos();
        if (configured != null) {
            return configured;
        }
        return getOverworldSpawnFallbackShared();
    }

    public synchronized String getParkourCompletionTitleId() {
        return normalizeTitleId(get().getParkourCompletionTitleId());
    }

    public synchronized String getParkourBestTitleId() {
        return normalizeTitleId(get().getParkourBestTitleId());
    }

    private void normalize(ServerConfigDataModel model) {
        if (model.getBoolOverworldInvulnerable() == null) {
            model.setBoolOverworldInvulnerable(true);
        }
        if (model.getBoolRestrictConquestDimension() == null) {
            model.setBoolRestrictConquestDimension(true);
        }
        if (model.getBoolNaturalRegeneration() == null) {
            model.setBoolNaturalRegeneration(false);
        }
        if (model.getPassiveRegenIntervalTicks() == null || model.getPassiveRegenIntervalTicks() <= 0) {
            model.setPassiveRegenIntervalTicks(3);
        }
        if (model.getPassiveRegenStartDelaySeconds() == null || model.getPassiveRegenStartDelaySeconds() < 0) {
            model.setPassiveRegenStartDelaySeconds(6);
        }
        model.setMaxScopePistol(sanitizeScopeLimit(model.getMaxScopePistol(), 2.0D));
        model.setMaxScopeSniper(sanitizeScopeLimit(model.getMaxScopeSniper(), 12.0D));
        model.setMaxScopeRifle(sanitizeScopeLimit(model.getMaxScopeRifle(), 6.0D));
        model.setMaxScopeShotgun(sanitizeScopeLimit(model.getMaxScopeShotgun(), 2.0D));
        model.setMaxScopeSmg(sanitizeScopeLimit(model.getMaxScopeSmg(), 3.0D));
        model.setMaxScopeRpg(sanitizeScopeLimit(model.getMaxScopeRpg(), 1.5D));
        model.setMaxScopeMg(sanitizeScopeLimit(model.getMaxScopeMg(), 4.0D));
        if (model.getVehicleBackend() == null) {
            model.setVehicleBackend(RoomVehicleBackend.defaultValue());
        }
        if (model.getBoolEnableExperimentalPlayerBalance() == null) {
            model.setBoolEnableExperimentalPlayerBalance(false);
        }
        if (model.getBoolMaintenanceMode() == null) {
            model.setBoolMaintenanceMode(false);
        }
        // GD656Mods: 等级上限约束(默认 100, 1..1024)
        model.setMaxPlayerLevel(sanitizeMaxPlayerLevel(model.getMaxPlayerLevel()));
        // GD656Mods: 最大活跃房间数(0 = 不限制, 上限 64)
        model.setMaxActiveRooms(sanitizeMaxActiveRooms(model.getMaxActiveRooms()));
        if (model.getDroneBatteryDurationSeconds() == null || model.getDroneBatteryDurationSeconds() <= 0) {
            model.setDroneBatteryDurationSeconds(60);
        }
        if (model.getKamikazeDroneBatteryDurationSeconds() == null || model.getKamikazeDroneBatteryDurationSeconds() <= 0) {
            model.setKamikazeDroneBatteryDurationSeconds(30);
        }
        if (model.getOverworldSpawn() == null) {
            BlockPos shared = server.overworld().getSharedSpawnPos();
            model.setOverworldSpawn(new RoomPosition(shared.getX(), shared.getY(), shared.getZ()));
        }
        if (model.getRangeJoinSpawn() == null) {
            model.setRangeJoinSpawn(model.getOverworldSpawn());
        }
        if (model.getRangeLeaveSpawn() == null) {
            model.setRangeLeaveSpawn(model.getOverworldSpawn());
        }
        if (model.getParkourStartPos() == null) {
            model.setParkourStartPos(model.getOverworldSpawn());
        }
        if (model.getParkourEndPos() == null) {
            model.setParkourEndPos(model.getParkourStartPos());
        }
        model.setParkourCompletionTitleId(normalizeTitleId(model.getParkourCompletionTitleId()));
        model.setParkourBestTitleId(normalizeTitleId(model.getParkourBestTitleId()));
        if (model.getMissileKillRadius() == null || model.getMissileKillRadius() < 0.0) {
            model.setMissileKillRadius(35.0);
        }
        model.setGroundSensorScanRadius(sanitizeScopeLimit(model.getGroundSensorScanRadius(), 10.0D));
        if (model.getGroundSensorScanIntervalSeconds() == null || model.getGroundSensorScanIntervalSeconds() <= 0) {
            model.setGroundSensorScanIntervalSeconds(4);
        }
        if (model.getGroundSensorMarkDurationSeconds() == null || model.getGroundSensorMarkDurationSeconds() <= 0) {
            model.setGroundSensorMarkDurationSeconds(2);
        }
        if (model.getGroundSensorScanBonusCooldownSeconds() == null || model.getGroundSensorScanBonusCooldownSeconds() <= 0) {
            model.setGroundSensorScanBonusCooldownSeconds(10);
        }
        // GD656Mods: 弹药包单次补给数量, 负数钳为 0(不补给该类型)
        model.setAmmoBagTaczPistol(sanitizeAmmoBagSupply(model.getAmmoBagTaczPistol(), 6));
        model.setAmmoBagTaczSmg(sanitizeAmmoBagSupply(model.getAmmoBagTaczSmg(), 15));
        model.setAmmoBagTaczRifle(sanitizeAmmoBagSupply(model.getAmmoBagTaczRifle(), 10));
        model.setAmmoBagTaczSniper(sanitizeAmmoBagSupply(model.getAmmoBagTaczSniper(), 3));
        model.setAmmoBagTaczShotgun(sanitizeAmmoBagSupply(model.getAmmoBagTaczShotgun(), 4));
        model.setAmmoBagTaczMg(sanitizeAmmoBagSupply(model.getAmmoBagTaczMg(), 20));
        model.setAmmoBagTaczRpg(sanitizeAmmoBagSupply(model.getAmmoBagTaczRpg(), 1));
        model.setAmmoBagSbw(sanitizeAmmoBagSupply(model.getAmmoBagSbw(), 1));
        model.setSupportAmmoBagTaczPistol(sanitizeAmmoBagSupply(model.getSupportAmmoBagTaczPistol(), 6));
        model.setSupportAmmoBagTaczSmg(sanitizeAmmoBagSupply(model.getSupportAmmoBagTaczSmg(), 15));
        model.setSupportAmmoBagTaczRifle(sanitizeAmmoBagSupply(model.getSupportAmmoBagTaczRifle(), 10));
        model.setSupportAmmoBagTaczSniper(sanitizeAmmoBagSupply(model.getSupportAmmoBagTaczSniper(), 3));
        model.setSupportAmmoBagTaczShotgun(sanitizeAmmoBagSupply(model.getSupportAmmoBagTaczShotgun(), 4));
        model.setSupportAmmoBagTaczMg(sanitizeAmmoBagSupply(model.getSupportAmmoBagTaczMg(), 20));
        model.setSupportAmmoBagTaczRpg(sanitizeAmmoBagSupply(model.getSupportAmmoBagTaczRpg(), 1));
        model.setSupportAmmoBagSbw(sanitizeAmmoBagSupply(model.getSupportAmmoBagSbw(), 1));
    }

    private static int sanitizeAmmoBagSupply(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, value);
    }

    private static double sanitizeScopeLimit(Double value, double fallback) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(0.0D, value);
    }

    private static String normalizeTitleId(String raw) {
        return raw == null || raw.isBlank() ? "" : PlayerTitleService.normalizeTitleId(raw);
    }
}
