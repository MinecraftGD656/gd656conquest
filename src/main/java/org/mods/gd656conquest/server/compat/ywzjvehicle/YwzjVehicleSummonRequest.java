package org.mods.gd656conquest.server.compat.ywzjvehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record YwzjVehicleSummonRequest(
    ServerLevel level,
    Vec3 position,
    float yRot,
    String vehicleId,
    String displayId
) {
}
