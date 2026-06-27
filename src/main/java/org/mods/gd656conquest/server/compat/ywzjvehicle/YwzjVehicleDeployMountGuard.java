package org.mods.gd656conquest.server.compat.ywzjvehicle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMountEvent;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class YwzjVehicleDeployMountGuard {
    private static final long PROTECT_WINDOW_MILLIS = 1500L;
    private static final ConcurrentHashMap<UUID, GuardState> PROTECTED_PLAYERS = new ConcurrentHashMap<>();

    private YwzjVehicleDeployMountGuard() {
    }

    public static void markRecentDeployMount(ServerPlayer player, Entity vehicle) {
        if (player == null || vehicle == null) {
            return;
        }
        long protectUntil = System.currentTimeMillis() + PROTECT_WINDOW_MILLIS;
        PROTECTED_PLAYERS.put(player.getUUID(), new GuardState(vehicle.getUUID(), protectUntil));
    }

    public static boolean handleMountEvent(EntityMountEvent event) {
        if (event == null || !event.isDismounting()) {
            return false;
        }
        if (!(event.getEntityMounting() instanceof ServerPlayer player)) {
            return false;
        }
        Entity vehicle = event.getEntityBeingMounted();
        if (vehicle == null || !VehicleCompatFacade.isVehicleEntity(vehicle)) {
            return false;
        }
        GuardState state = PROTECTED_PLAYERS.get(player.getUUID());
        if (state == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (state.protectUntilMillis < now) {
            PROTECTED_PLAYERS.remove(player.getUUID(), state);
            return false;
        }
        if (!vehicle.getUUID().equals(state.vehicleUuid)) {
            return false;
        }
        event.setCanceled(true);
        return true;
    }

    private record GuardState(UUID vehicleUuid, long protectUntilMillis) {
    }
}
