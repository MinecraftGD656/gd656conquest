package org.mods.gd656conquest.server.room.runtime;

import java.util.List;
import java.util.Objects;

public final class RoomBeaconDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    private final RoomSquadBeaconService squadBeaconService;

    public RoomBeaconDeployHudTargetProvider(RoomSquadBeaconService squadBeaconService) {
        this.squadBeaconService = Objects.requireNonNull(squadBeaconService, "squadBeaconService");
    }

    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        if (context.room() != null 
            && org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(context.room().gameplayId()) 
            && context.room().gunmasterConfig() != null 
            && context.room().gunmasterConfig().isSoloGrouping()) {
            return;
        }
        squadBeaconService.collectBeaconElements(elements, context);
    }
}
