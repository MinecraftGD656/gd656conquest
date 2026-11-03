package org.mods.gd656conquest.server.room.model;

public record RoomViewPosition(double x, double y, double z, float yaw, float pitch) {
    public RoomViewPosition {
        x = roundToHundredth(x);
        z = roundToHundredth(z);
    }

    private static double roundToHundredth(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
