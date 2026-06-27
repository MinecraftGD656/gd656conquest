package org.mods.gd656conquest.server.compat.vehicle;

import java.util.UUID;

public record VehicleCompatSnapshot(
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
