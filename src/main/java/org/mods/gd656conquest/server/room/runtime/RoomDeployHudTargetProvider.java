package org.mods.gd656conquest.server.room.runtime;

import java.util.List;

public interface RoomDeployHudTargetProvider {
    void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context);
}
