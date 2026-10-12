package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;

/** Optional room-local pause; the room and its accumulated match statistics stay running. */
public interface RoomGameplayIntermissionProvider {
    org.mods.gd656conquest.common.match.BattalionSequence.Phase intermissionPhase();
    long intermissionSequenceId();
    boolean isCombatPaused();
    boolean isDeploymentPreparation();
    void synchronizeIntermissionPlayers(RoomDefinition room, RoomPlayerRespawnService respawns, long tick);
}
