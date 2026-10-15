package org.mods.gd656conquest.server.room.objective;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveDefinitionProvider;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudBuildContext;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudElement;
import org.mods.gd656conquest.server.room.runtime.RoomDeployHudTargetProvider;

import java.util.List;

public final class RoomObjectiveDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        RoomObjectiveRuntimeView runtimeView = context.gameplayRuntime()
            .filter(RoomObjectiveRuntimeProvider.class::isInstance)
            .map(RoomObjectiveRuntimeProvider.class::cast)
            .map(RoomObjectiveRuntimeProvider::objectiveRuntimeView)
            .orElse(null);
        RoomGameplayObjectiveDefinitionProvider definitionProvider = context.gameplayRuntime()
            .filter(RoomGameplayObjectiveDefinitionProvider.class::isInstance)
            .map(RoomGameplayObjectiveDefinitionProvider.class::cast)
            .orElse(null);
        List<RoomCapturePointDefinition> points = definitionProvider == null
            ? context.room().configuredObjectivePoints()
            : definitionProvider.activeObjectivePoints(context.room());
        for (RoomCapturePointDefinition point : points) {
            String objectiveCode = RoomObjectiveSupport.normalizeCode(point.code());
            RoomTeamId owner = runtimeView == null ? null : runtimeView.ownerOf(objectiveCode);
            RoomTeamId driving = runtimeView == null ? null : runtimeView.drivingTeamOf(objectiveCode);
            double progress = runtimeView == null ? 50.0D : runtimeView.progressOf(objectiveCode);
            int campACount = runtimeView == null ? 0 : runtimeView.teamCount(objectiveCode, RoomTeamId.CAMP_A);
            int campBCount = runtimeView == null ? 0 : runtimeView.teamCount(objectiveCode, RoomTeamId.CAMP_B);
            if (point.centerPosition() == null) {
                continue;
            }
            RoomDeployHudElement element = RoomDeployHudElement.capture(
                point.code(),
                point.centerPosition(),
                owner,
                driving,
                progress,
                point.displayName(),
                campACount,
                campBCount
            );
            if (org.mods.gd656conquest.server.room.model.RoomGameplayId.BREAKTHROUGH.equals(context.room().gameplayId())) {
                var viewer = context.server().getPlayerList().getPlayer(context.playerId());
                if (!new RoomObjectiveSpawnSelectionService(context.server()).assessObjectiveSpawn(context.room(), context.state(), viewer, context.selfTeam(), point.code()).success()) {
                    element = new RoomDeployHudElement(element.kind(), element.code(), element.x(), element.y(), element.z(),
                        element.ownerToken(), element.drivingToken(), element.progress(), element.note(),
                        List.of(Integer.toString(campACount), Integer.toString(campBCount), "DEPLOY_BLOCKED"));
                }
            }
            elements.add(element);
        }
    }
}
