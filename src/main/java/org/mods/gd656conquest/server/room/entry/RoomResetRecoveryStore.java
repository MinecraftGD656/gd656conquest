package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class RoomResetRecoveryStore extends JsonDataStoreSupport {
    private final MinecraftServer server;

    RoomResetRecoveryStore(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public synchronized Set<String> readPendingRoomIds() {
        return new LinkedHashSet<>(readModel().roomIds);
    }

    public synchronized void replacePendingRoomIds(Collection<String> roomIds) {
        PendingResetModel model = new PendingResetModel();
        if (roomIds != null) {
            for (String roomId : roomIds) {
                String normalized = normalizeRoomId(roomId);
                if (!normalized.isBlank()) {
                    model.roomIds.add(normalized);
                }
            }
        }
        write(resolveFile(), model);
    }

    public synchronized void addPendingRoomId(String roomId) {
        String normalized = normalizeRoomId(roomId);
        if (normalized.isBlank()) {
            return;
        }
        PendingResetModel model = readModel();
        model.roomIds.add(normalized);
        write(resolveFile(), model);
    }

    public synchronized void removePendingRoomId(String roomId) {
        PendingResetModel model = readModel();
        model.roomIds.remove(normalizeRoomId(roomId));
        write(resolveFile(), model);
    }

    private PendingResetModel readModel() {
        PendingResetModel model = read(resolveFile(), PendingResetModel.class, PendingResetModel::new);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String roomId : model.roomIds) {
            String value = normalizeRoomId(roomId);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        model.roomIds.clear();
        model.roomIds.addAll(normalized);
        return model;
    }

    private Path resolveFile() {
        return ServerDataPaths.getRoomResetRecoveryFile(server);
    }

    private String normalizeRoomId(String roomId) {
        return roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
    }

    private static final class PendingResetModel {
        private final Set<String> roomIds = new LinkedHashSet<>();
    }
}
