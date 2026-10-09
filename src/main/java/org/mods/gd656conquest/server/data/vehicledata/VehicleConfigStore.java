package org.mods.gd656conquest.server.data.vehicledata;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class VehicleConfigStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private final Map<String, VehicleGameplayConfig> cacheByKey = new LinkedHashMap<>();

    public VehicleConfigStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        try {
            Files.createDirectories(ServerDataPaths.getVehicleDataDirectory(server));
        } catch (IOException ignored) {
        }
    }

    public synchronized VehicleGameplayConfig getConfig(String roomId, String gameplayId, RoomVehicleBackend backend) {
        RoomVehicleBackend resolvedBackend = backend == null ? RoomVehicleBackend.defaultValue() : backend;
        String cacheKey = toCacheKey(roomId, gameplayId, resolvedBackend);
        VehicleGameplayConfig cached = cacheByKey.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        VehicleGameplayConfig loaded = read(resolveReadPath(roomId, gameplayId, resolvedBackend), VehicleGameplayConfig.class, VehicleGameplayConfig::new);
        loaded.getEntries();
        cacheByKey.put(cacheKey, loaded);
        return loaded;
    }

    public synchronized void saveConfig(String roomId, String gameplayId, RoomVehicleBackend backend, VehicleGameplayConfig config) {
        RoomVehicleBackend resolvedBackend = backend == null ? RoomVehicleBackend.defaultValue() : backend;
        VehicleGameplayConfig resolved = config == null ? new VehicleGameplayConfig() : config;
        resolved.getEntries();
        cacheByKey.put(toCacheKey(roomId, gameplayId, resolvedBackend), resolved);
        delayedJsonWriteService.schedule(
            ServerDataPaths.getVehicleDataFile(server, roomId, gameplayId, resolvedBackend),
            resolved,
            server.getTickCount()
        );
    }

    private java.nio.file.Path resolveReadPath(String roomId, String gameplayId, RoomVehicleBackend backend) {
        java.nio.file.Path backendPath = ServerDataPaths.getVehicleDataFile(server, roomId, gameplayId, backend);
        if (Files.exists(backendPath)) {
            return backendPath;
        }
        if (backend == RoomVehicleBackend.YWZJ) {
            java.nio.file.Path legacyPath = ServerDataPaths.getVehicleDataFile(server, roomId, gameplayId);
            if (Files.exists(legacyPath)) {
                return legacyPath;
            }
        }
        return backendPath;
    }

    private static String toCacheKey(String roomId, String gameplayId, RoomVehicleBackend backend) {
        String safeRoomId = roomId == null ? "" : roomId.trim();
        String safeGameplayId = gameplayId == null ? "" : gameplayId.trim();
        String safeBackend = backend == null ? RoomVehicleBackend.defaultValue().value() : backend.value();
        return safeRoomId + "::" + safeGameplayId + "::" + safeBackend;
    }
}
