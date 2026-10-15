package org.mods.gd656conquest.server.room.vehicle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.room.runtime.RoomDeployBoundarySupport;

public final class VehicleDeployBoundarySupport {
    private VehicleDeployBoundarySupport() {}

    public static boolean allows(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, VehicleRuntimeRecord record, Entity entity) {
        if (!RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) return true;
        if (record == null || player == null) return false;
        boolean aircraft = isAircraft(room, player, record, entity);
        double x = entity == null ? record.getConfiguredSpawnX() : entity.getX();
        double z = entity == null ? record.getConfiguredSpawnZ() : entity.getZ();
        return aircraft || RoomDeployBoundarySupport.allows(room, state, state.getRoomTeams().get(player.getUUID()), x, z);
    }

    public static boolean isAircraft(RoomDefinition room, ServerPlayer player, VehicleRuntimeRecord record, Entity entity) {
        if (entity != null) return VehicleCompatFacade.isAircraftEntity(entity);
        return room != null && player != null && record != null
            && VehicleCompatFacade.isAircraftId(ConquestDataManager.of(player.server).serverConfig().getVehicleBackend(),
                player.serverLevel(), record.getConfiguredVehicleId());
    }
}
