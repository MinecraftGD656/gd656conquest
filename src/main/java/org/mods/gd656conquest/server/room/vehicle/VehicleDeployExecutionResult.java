package org.mods.gd656conquest.server.room.vehicle;

public record VehicleDeployExecutionResult(
    boolean success,
    boolean retry,
    String message
) {
    public static VehicleDeployExecutionResult success(String message) {
        return new VehicleDeployExecutionResult(true, false, message == null ? "载具部署完成" : message);
    }

    public static VehicleDeployExecutionResult failure(String message) {
        return new VehicleDeployExecutionResult(false, false, message == null ? "载具部署失败" : message);
    }

    public static VehicleDeployExecutionResult retry(String message) {
        return new VehicleDeployExecutionResult(false, true, message == null ? "载具部署等待中" : message);
    }
}
