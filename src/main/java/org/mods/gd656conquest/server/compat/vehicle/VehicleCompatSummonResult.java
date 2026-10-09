package org.mods.gd656conquest.server.compat.vehicle;

import java.util.UUID;

public record VehicleCompatSummonResult(
    boolean success,
    String message,
    int entityId,
    UUID entityUuid,
    String vehicleId,
    String displayId
) {
    public static VehicleCompatSummonResult failure(String message) {
        return new VehicleCompatSummonResult(false, message == null ? "生成失败" : message, -1, null, "", "");
    }

    public static VehicleCompatSummonResult success(int entityId, UUID entityUuid, String vehicleId, String displayId) {
        return new VehicleCompatSummonResult(true, "生成成功", entityId, entityUuid, vehicleId, displayId);
    }
}
