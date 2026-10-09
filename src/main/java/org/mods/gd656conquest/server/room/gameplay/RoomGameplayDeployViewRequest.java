package org.mods.gd656conquest.server.room.gameplay;

public record RoomGameplayDeployViewRequest(
    RoomGameplayDeployViewKind kind,
    long gameTick,
    int deployDelayTicks,
    boolean instantView
) {
}
