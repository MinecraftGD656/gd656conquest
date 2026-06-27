package org.mods.gd656conquest.server.room.vehicle;

import java.util.UUID;

public record VehicleDeployResolution(
    VehicleDeployActionType actionType,
    String entryId,
    UUID targetVehicleUuid,
    UUID reservedDriverPlayerId,
    VehicleDisplayState displayState,
    String message
) {
    public boolean success() {
        return actionType != VehicleDeployActionType.FAIL;
    }

    public static VehicleDeployResolution success(
        VehicleDeployActionType actionType,
        String entryId,
        UUID targetVehicleUuid,
        UUID reservedDriverPlayerId,
        VehicleDisplayState displayState,
        String message
    ) {
        return new VehicleDeployResolution(
            actionType == null ? VehicleDeployActionType.FAIL : actionType,
            entryId == null ? "" : entryId,
            targetVehicleUuid,
            reservedDriverPlayerId,
            displayState == null ? VehicleDisplayState.READY_TO_DEPLOY : displayState,
            message == null ? "" : message
        );
    }

    public static VehicleDeployResolution fail(String entryId, VehicleDisplayState displayState, String message) {
        return new VehicleDeployResolution(
            VehicleDeployActionType.FAIL,
            entryId == null ? "" : entryId,
            null,
            null,
            displayState == null ? VehicleDisplayState.READY_TO_DEPLOY : displayState,
            message == null ? "不可部署" : message
        );
    }

}
