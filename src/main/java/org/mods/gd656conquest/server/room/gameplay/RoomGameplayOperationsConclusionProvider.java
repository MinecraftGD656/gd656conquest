package org.mods.gd656conquest.server.room.gameplay;

import java.util.Optional;
import org.mods.gd656conquest.common.match.OperationsConclusion;

/** Allows the final operations presentation to continue while the room is ENDING. */
public interface RoomGameplayOperationsConclusionProvider {
    Optional<OperationsConclusion> operationsConclusion();
}
