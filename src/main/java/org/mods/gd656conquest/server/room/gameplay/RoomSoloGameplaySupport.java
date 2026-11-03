package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;

public final class RoomSoloGameplaySupport {
    private RoomSoloGameplaySupport() {
    }

    public static boolean isDeathmatchSolo(RoomDefinition room) {
        return room != null
            && RoomGameplayId.DEATHMATCH.equals(room.gameplayId())
            && room.deathmatchConfig().isSoloMode();
    }

    public static boolean isGunmasterSolo(RoomDefinition room) {
        return room != null
            && RoomGameplayId.GUNMASTER.equals(room.gameplayId())
            && room.gunmasterConfig().isSoloGrouping();
    }

    public static boolean isSoloFreeForAll(RoomDefinition room) {
        return isDeathmatchSolo(room) || isGunmasterSolo(room);
    }
}
