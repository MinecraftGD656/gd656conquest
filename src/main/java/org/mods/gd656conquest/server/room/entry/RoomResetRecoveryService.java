package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

public final class RoomResetRecoveryService {
    private static final MapHolder INSTANCES = new MapHolder();

    private final MinecraftServer server;
    private final RoomResetRecoveryStore store;
    private Set<String> lastSyncedPendingRoomIds = new LinkedHashSet<>();

    private RoomResetRecoveryService(MinecraftServer server) {
        this.server = server;
        this.store = new RoomResetRecoveryStore(server);
    }

    public static RoomResetRecoveryService of(MinecraftServer server) {
        synchronized (INSTANCES.values) {
            return INSTANCES.values.computeIfAbsent(server, RoomResetRecoveryService::new);
        }
    }

    public static void forget(MinecraftServer server) {
        synchronized (INSTANCES.values) {
            INSTANCES.values.remove(server);
        }
    }

    public void syncPendingResetsFromRooms(Collection<RoomDefinition> rooms) {
        Set<String> pending = new LinkedHashSet<>();
        if (rooms != null) {
            for (RoomDefinition room : rooms) {
                if (room == null || !shouldRecoverAfterShutdown(room.lifecycleState())) {
                    continue;
                }
                pending.add(room.roomId());
            }
        }
        synchronized (this) {
            if (pending.equals(lastSyncedPendingRoomIds)) {
                return;
            }
            store.replacePendingRoomIds(pending);
            lastSyncedPendingRoomIds = pending;
        }
    }

    public synchronized void markRoomForRecovery(String roomId) {
        store.addPendingRoomId(roomId);
        lastSyncedPendingRoomIds.add(roomId == null ? "" : roomId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public synchronized void clearRoomRecovery(String roomId) {
        store.removePendingRoomId(roomId);
        lastSyncedPendingRoomIds.remove(roomId == null ? "" : roomId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public void recoverPendingResets(long gameTick) {
        Set<String> pendingRoomIds = store.readPendingRoomIds();
        for (RoomDefinition room : RoomCoreRuntimeManager.of(server).listRooms()) {
            if (room == null || !shouldRecoverAfterShutdown(room.lifecycleState())) {
                continue;
            }
            pendingRoomIds.add(room.roomId());
        }
        lastSyncedPendingRoomIds = new LinkedHashSet<>(pendingRoomIds);
        if (pendingRoomIds.isEmpty()) {
            return;
        }
        RoomAdminLifecycleService lifecycleService = new RoomAdminLifecycleService(server);
        for (String roomId : pendingRoomIds) {
            RoomAdminLifecycleService.OperationResult result = lifecycleService.recoverInterruptedRoom(roomId, gameTick);
            if (result.success()) {
                clearRoomRecovery(roomId);
                ServerLogger.info("已恢复停服中断的房间重置: %s", roomId);
            } else {
                ServerLogger.failure("恢复停服中断的房间重置失败: %s | %s", roomId, result.message());
            }
        }
    }

    private boolean shouldRecoverAfterShutdown(RoomLifecycleState lifecycleState) {
        if (lifecycleState == null) {
            return false;
        }
        return lifecycleState == RoomLifecycleState.RUNNING
            || lifecycleState == RoomLifecycleState.ENDING
            || lifecycleState == RoomLifecycleState.SAVING
            || lifecycleState == RoomLifecycleState.CLEANING;
    }

    private static final class MapHolder {
        private final WeakHashMap<MinecraftServer, RoomResetRecoveryService> values = new WeakHashMap<>();
    }
}
