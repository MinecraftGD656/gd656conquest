package org.mods.gd656conquest.server.compat.ywzjvehicle;

import java.util.UUID;

public record YwzjVehicleSummonResult(
    boolean success,
    String message,
    int entityId,
    UUID entityUuid,
    String vehicleId,
    String displayId
) {
    public static YwzjVehicleSummonResult failure(String message) {
        return new YwzjVehicleSummonResult(false, message == null ? "生成失败" : message, -1, null, "", "");
    }

    public static YwzjVehicleSummonResult success(int entityId, UUID entityUuid, String vehicleId, String displayId) {
        return new YwzjVehicleSummonResult(true, "生成成功", entityId, entityUuid, vehicleId, displayId);
    }
}
