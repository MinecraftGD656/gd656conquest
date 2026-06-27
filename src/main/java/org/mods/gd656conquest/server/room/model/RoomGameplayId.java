package org.mods.gd656conquest.server.room.model;

import java.util.Locale;
import java.util.Objects;

public record RoomGameplayId(String value) {
    public static final RoomGameplayId CONQUEST = new RoomGameplayId("conquest");
    public static final RoomGameplayId BREAKTHROUGH = new RoomGameplayId("breakthrough");

    public RoomGameplayId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("玩法标识不能为空");
        }
    }

    public static RoomGameplayId of(String value) {
        return new RoomGameplayId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}