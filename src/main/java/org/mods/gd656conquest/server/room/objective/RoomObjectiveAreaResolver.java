package org.mods.gd656conquest.server.room.objective;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;

import java.util.Set;

public interface RoomObjectiveAreaResolver {
    String resolvePlayerInsideObjectiveCode(ServerPlayer player, RoomCaptureConfig config, Set<String> allowedCodes);
}
