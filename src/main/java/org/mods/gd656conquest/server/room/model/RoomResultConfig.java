package org.mods.gd656conquest.server.room.model;

public record RoomResultConfig(RoomViewPosition endViewPosition) {
    public static RoomResultConfig defaults() {
        return new RoomResultConfig(null);
    }
}
