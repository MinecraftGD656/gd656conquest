package org.mods.gd656conquest.server.room.runtime;

public enum RoomDeployHudElementKind {
    CAPTURE("C"),
    SPAWN("S"),
    TEAMMATE("P"),
    BOUNDARY("B"),
    BEACON("R"),
    VEHICLE("V");

    private final String payloadToken;

    RoomDeployHudElementKind(String payloadToken) {
        this.payloadToken = payloadToken;
    }

    public String payloadToken() {
        return payloadToken;
    }
}
