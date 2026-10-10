package org.mods.gd656conquest.server.room.runtime;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Selects a rescue target without substituting another player for an invalid explicit request. */
public final class ReviveTargetSelection {
    private ReviveTargetSelection() {
    }

    /** The caller supplies the authoritative room, team, distance and rescue-state checks. */
    public static UUID select(UUID requestedTargetId, Supplier<UUID> nearestTarget, Predicate<UUID> canRevive) {
        UUID targetId = requestedTargetId != null ? requestedTargetId : nearestTarget.get();
        return targetId != null && canRevive.test(targetId) ? targetId : null;
    }
}
