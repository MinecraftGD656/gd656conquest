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
        return Math.max(1, get().getPassiveRegenIntervalTicks() == null ? 10 : get().getPassiveRegenIntervalTicks());
    }

    public synchronized int getPassiveRegenStartDelaySeconds() {
        return Math.max(0, get().getPassiveRegenStartDelaySeconds() == null ? 6 : get().getPassiveRegenStartDelaySeconds());
    }

    public synchronized boolean isPassiveRegenerationEnabled() {
        return !Boolean.FALSE.equals(get().getBoolPassiveRegeneration());
    }

    public synchronized double getPassiveRegenHealAmount() {
        return org.mods.gd656conquest.server.room.model.RoomNaturalRegenConfig.amount(get().getPassiveRegenHealAmount());
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

    public synchronized boolean isPackValidationEnabled() {
        return Boolean.TRUE.equals(get().getEnablePackValidation());
    }

    public synchronized double getMissileKillRadius() {
        Double value = get().getMissileKillRadius();
        return value == null || value < 0.0 ? 35.0 : value;
    }

    public synchronized double getSupplyBoxHealth() {
        return ServerConfigDataModel.normalizeSupplyBoxHealth(get().getSupplyBoxHealth());
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
        model.setSupplyBoxHealth(model.getSupplyBoxHealth());
        if (model.getBoolPassiveRegeneration() == null) model.setBoolPassiveRegeneration(true);
        model.setPassiveRegenHealAmount(org.mods.gd656conquest.server.room.model.RoomNaturalRegenConfig.amount(model.getPassiveRegenHealAmount()));
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
            model.setPassiveRegenIntervalTicks(10);
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
        if (model.getEnablePackValidation() == null) {
            model.setEnablePackValidation(false);
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
