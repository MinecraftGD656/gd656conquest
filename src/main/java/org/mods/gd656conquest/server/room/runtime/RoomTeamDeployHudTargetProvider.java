package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationSupport;
import org.mods.gd656conquest.server.room.gameplay.RoomDeployHudIcon;
import org.mods.gd656conquest.server.room.gameplay.RoomSoloGameplaySupport;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;

public final class RoomTeamDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        RoomTeamId selfTeam = context.selfTeam();
        if (selfTeam == null) {
            appendSoloDeathmatchSpawn(elements, context);
            return;
        }
        RoomGameplayDeployPresentationProvider provider = RoomGameplayDeployPresentationProvider.resolve(context.state());
        RoomPosition iconPosition = RoomGameplayDeployPresentationSupport.resolveTeamDeployIconPosition(
            context.room(),
            context.state(),
            selfTeam
        );
        if (iconPosition != null) {
            elements.add(RoomDeployHudElement.spawn(selfTeam.name(), iconPosition, selfTeam, ""));
        }
        appendAdditionalRows(elements, provider, context, selfTeam);
    }

    private void appendSoloDeathmatchSpawn(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        if (!RoomSoloGameplaySupport.isDeathmatchSolo(context.room())) {
            return;
        }
        RoomPosition iconPosition = RoomGameplayDeployPresentationSupport.resolveTeamDeployIconPosition(
            context.room(),
            context.state(),
            null
        );
        if (iconPosition != null) {
            elements.add(RoomDeployHudElement.spawn("SOLO", iconPosition, null, "混战部署点", List.of("SOLO_DEPLOY")));
        }
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
