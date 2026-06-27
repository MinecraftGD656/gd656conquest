package org.mods.gd656conquest.server.room.core;

import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public interface RoomRuntimeRepository<TRuntime> {
    Optional<RoomRuntimeSlot<TRuntime>> find(String roomId);

    default Optional<RoomDefinition> findRoom(String roomId) {
        return find(roomId).map(RoomRuntimeSlot::room);
    }

    default Optional<TRuntime> findRuntime(String roomId) {
        return find(roomId).map(RoomRuntimeSlot::runtime);
    }

    RoomRuntimeSlot<TRuntime> save(RoomDefinition room, Supplier<TRuntime> runtimeFactory);

    Collection<RoomRuntimeSlot<TRuntime>> list();

    void remove(String roomId);

    static <TRuntime> RoomRuntimeRepository<TRuntime> createInMemory() {
        return new InMemoryRoomRuntimeRepository<>();
    }
}

final class RoomRuntimeSlot<TRuntime> {
    private RoomDefinition room;
    private final TRuntime runtime;

    RoomRuntimeSlot(RoomDefinition room, TRuntime runtime) {
        this.room = Objects.requireNonNull(room, "room");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    RoomDefinition room() {
        return room;
    }

    TRuntime runtime() {
        return runtime;
    }

    void replaceRoom(RoomDefinition room) {
        this.room = Objects.requireNonNull(room, "room");
    }
}

final class InMemoryRoomRuntimeRepository<TRuntime> implements RoomRuntimeRepository<TRuntime> {
    private final Map<String, RoomRuntimeSlot<TRuntime>> slotByRoomId = new LinkedHashMap<>();

    @Override
    public Optional<RoomRuntimeSlot<TRuntime>> find(String roomId) {
        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(slotByRoomId.get(normalizedRoomId));
    }

    @Override
    public RoomRuntimeSlot<TRuntime> save(RoomDefinition room, Supplier<TRuntime> runtimeFactory) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        String normalizedRoomId = normalizeRoomId(room.roomId());
        if (normalizedRoomId == null) {
            throw new IllegalArgumentException("roomId 不能为空");
        }
        RoomRuntimeSlot<TRuntime> slot = slotByRoomId.get(normalizedRoomId);
        if (slot != null) {
            slot.replaceRoom(room);
            return slot;
        }
        RoomRuntimeSlot<TRuntime> created = new RoomRuntimeSlot<>(room, runtimeFactory.get());
        slotByRoomId.put(normalizedRoomId, created);
        return created;
    }

    @Override
    public Collection<RoomRuntimeSlot<TRuntime>> list() {
        return java.util.List.copyOf(slotByRoomId.values());
    }

    @Override
    public void remove(String roomId) {
        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId == null) {
            return;
        }
        slotByRoomId.remove(normalizedRoomId);
    }

    private String normalizeRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        return roomId.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
