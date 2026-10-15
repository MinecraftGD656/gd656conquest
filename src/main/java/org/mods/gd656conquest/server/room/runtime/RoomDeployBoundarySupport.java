package org.mods.gd656conquest.server.room.runtime;

import java.util.List;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.model.*;

/** Deployment never inherits a living defender's temporary retreat permission. */
public final class RoomDeployBoundarySupport {
    private RoomDeployBoundarySupport() {}

    public static boolean allows(RoomDefinition room, RunningRuntimeState state, RoomTeamId team, double x, double z) {
        var policy = RoomGameplayBoundaryPolicyProvider.resolve(state);
        return policy == null || policy.isDeploymentPositionAllowed(room, state, team, x, z);
    }

    public static boolean allows(RoomDefinition room, RunningRuntimeState state, RoomTeamId team, RoomPosition position) {
        return position != null && allows(room, state, team, position.x() + 0.5D, position.z() + 0.5D);
    }

    public static List<RoomPosition> filter(RoomDefinition room, RunningRuntimeState state, RoomTeamId team, List<RoomPosition> candidates) {
        return candidates == null ? List.of() : candidates.stream().filter(p -> allows(room, state, team, p)).toList();
    }
}
