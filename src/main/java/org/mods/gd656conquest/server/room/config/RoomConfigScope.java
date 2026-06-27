package org.mods.gd656conquest.server.room.config;

import org.mods.gd656conquest.server.room.model.RoomGameplayId;

public enum RoomConfigScope {
    COMMON,
    CONQUEST,
    BREAKTHROUGH;

    public String displayName() {
        return name();
    }

    public boolean appliesTo(RoomGameplayId gameplayId) {
        if (this == COMMON) {
            return true;
        }
        if (gameplayId == null) {
            return false;
        }
        return switch (this) {
            case COMMON -> true;
            case CONQUEST -> RoomGameplayId.CONQUEST.equals(gameplayId);
            case BREAKTHROUGH -> RoomGameplayId.BREAKTHROUGH.equals(gameplayId);
        };
    }
}
