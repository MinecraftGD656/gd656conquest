package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationSupport;
import org.mods.gd656conquest.server.room.gameplay.RoomDeployHudIcon;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;

public final class RoomTeamDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        RoomTeamId selfTeam = context.selfTeam();
        if (selfTeam == null) {
            return;
        }
        RoomGameplayDeployPresentationProvider provider = RoomGameplayDeployPresentationProvider.resolve(context.state());
        RoomPosition iconPosition = RoomGameplayDeployPresentationSupport.resolveTeamDeployIconPosition(
            context.room(),
            context.state(),
            selfTeam
        );
        if (iconPosition != null && (!org.mods.gd656conquest.server.room.model.RoomGameplayId.BREAKTHROUGH.equals(context.room().gameplayId())
            || provider != null && provider.resolveRespawnAnchorPosition(context.room(), context.state(), context.server().getPlayerList().getPlayer(context.playerId())) != null)) {
            elements.add(RoomDeployHudElement.spawn(selfTeam.name(), iconPosition, selfTeam, ""));
        }
        appendAdditionalRows(elements, provider, context, selfTeam);
    }

    private void appendAdditionalRows(
        List<RoomDeployHudElement> elements,
        RoomGameplayDeployPresentationProvider provider,
        RoomDeployHudBuildContext context,
        RoomTeamId selfTeam
    ) {
        if (provider == null) {
            return;
        }
        List<RoomDeployHudIcon> extraIcons = provider.resolveAdditionalDeployIcons(context.room(), context.state(), selfTeam);
        if (extraIcons == null || extraIcons.isEmpty()) {
            return;
        }
        for (RoomDeployHudIcon icon : extraIcons) {
            if (icon == null || icon.position() == null || icon.ownerTeam() == null || icon.code() == null || icon.code().isBlank()) {
                continue;
            }
            if (icon.stageObjectiveProxy() && icon.code().startsWith("CAPTURE:")
                && !new org.mods.gd656conquest.server.room.objective.RoomObjectiveSpawnSelectionService(context.server())
                    .assessObjectiveSpawn(context.room(), context.state(), context.server().getPlayerList().getPlayer(context.playerId()), selfTeam, icon.code().substring(8)).success()) continue;
            elements.add(RoomDeployHudElement.spawn(
                icon.code(),
                icon.position(),
                icon.ownerTeam(),
                icon.note(),
                icon.stageObjectiveProxy() ? List.of("BT_STAGE_PROXY") : List.of()
            ));
        }
    }
}
