package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeRecord;

public interface RoomGameplayVehicleDeployRuleProvider {
    default boolean supportsVehicleDeploy(RoomDefinition room, RunningRuntimeState state) {
        return false;
    }

    default boolean isVehicleDeployEntryActive(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        VehicleRuntimeRecord record
    ) {
        return true;
    }

    default String describeInactiveVehicleDeployEntry(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        VehicleRuntimeRecord record
    ) {
        return "该载具当前不在本玩法可部署范围内";
    }

    default boolean shouldDisplayVehicleDeployEntry(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        VehicleRuntimeRecord record
    ) {
        return true;
    }

    default boolean shouldRetireVehicleEntryWhenUnspawned(
        RoomDefinition room,
        RunningRuntimeState state,
        VehicleRuntimeRecord record
    ) {
        return false;
    }

    static RoomGameplayVehicleDeployRuleProvider resolve(RunningRuntimeState state) {
        if (state == null) {
            return null;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayVehicleDeployRuleProvider.class::isInstance)
            .map(RoomGameplayVehicleDeployRuleProvider.class::cast)
            .orElse(null);
    }
}
