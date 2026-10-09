package org.mods.gd656conquest.server.compat.vehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

public record VehicleCompatSummonRequest(
    RoomVehicleBackend backend,
    ServerLevel level,
    Vec3 position,
    float yRot,
    String vehicleId,
    String displayId
) {
}
