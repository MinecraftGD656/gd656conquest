package org.mods.gd656conquest.server.room.vehicle;

public final class VehicleDisplayStateTokenResolver {
    private VehicleDisplayStateTokenResolver() {
    }

    public static String toToken(VehicleDisplayState displayState) {
        if (displayState == null) {
            return "READY";
        }
        return switch (displayState) {
            case READY_TO_DEPLOY -> "READY";
            case FRIENDLY_EMPTY -> "EMPTY";
            case SQUAD_CONTROLLED -> "SQUAD";
            case TEAM_CONTROLLED -> "TEAM";
            case FULL -> "FULL";
            case WAITING -> "WAIT";
            case ENEMY_CONTROLLED -> "ENEMY";
        };
    }
}
