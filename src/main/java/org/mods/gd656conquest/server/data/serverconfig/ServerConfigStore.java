package org.mods.gd656conquest.server.data.serverconfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

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

    public synchronized int getPartyMaxMembers() {
        return Math.max(1, get().getPartyMaxMembers() == null ? 4 : get().getPartyMaxMembers());
    }

    public synchronized RoomVehicleBackend getVehicleBackend() {
        RoomVehicleBackend configured = get().getVehicleBackend();
        return configured == null ? RoomVehicleBackend.defaultValue() : configured;
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
        if (model.getPartyMaxMembers() == null || model.getPartyMaxMembers() <= 0) {
            model.setPartyMaxMembers(4);
        }
        if (model.getVehicleBackend() == null) {
            model.setVehicleBackend(RoomVehicleBackend.defaultValue());
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
    }
}
