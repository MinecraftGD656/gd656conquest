package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryHudProvider;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;

public final class RoomBoundaryDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        RoomGameplayBoundaryHudProvider provider = RoomGameplayBoundaryHudProvider.resolve(context.state());
        if (provider != null) {
            provider.collectBoundaryElements(elements, context);
            return;
        }
        appendBoundaryRows(elements, context.room().conquestConfig().allowedBoundariesForTeam(RoomTeamId.CAMP_A), RoomTeamId.CAMP_A);
        appendBoundaryRows(elements, context.room().conquestConfig().allowedBoundariesForTeam(RoomTeamId.CAMP_B), RoomTeamId.CAMP_B);
    }

    private void appendBoundaryRows(List<RoomDeployHudElement> elements, List<List<RoomPosition>> boundaries, RoomTeamId teamId) {
        if (boundaries == null || boundaries.isEmpty()) {
            return;
        }
        int polygonIndex = 0;
        for (List<RoomPosition> positions : boundaries) {
            if (positions == null || positions.size() < 2) {
                polygonIndex++;
                continue;
            }
            String polygonId = teamId.name() + "_" + polygonIndex++;
            for (RoomPosition position : positions) {
                elements.add(RoomDeployHudElement.boundary(RoomDeployHudElement.toTeamToken(teamId), position, polygonId));
            }
        }
    }
}
