package org.mods.gd656conquest.server.compat.ywzjvehicle;

import java.util.UUID;

public record YwzjVehicleSnapshot(
    UUID entityUuid,
    int entityId,
    String vehicleId,
    String displayId,
    String displayName,
    boolean destroyed,
    float health,
    float maxHealth,
    int passengerCount,
    UUID driverUuid,
    String driverName,
    double x,
    double y,
    double z
) {
}
