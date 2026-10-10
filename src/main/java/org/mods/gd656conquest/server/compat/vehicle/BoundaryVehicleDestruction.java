package org.mods.gd656conquest.server.compat.vehicle;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.UnifiedBoundaryService;

/** Trigger the vehicle mod's own zero-health destruction; never discard an entity as a fallback. */
public final class BoundaryVehicleDestruction {
    private static final Map<Entity, Request> REQUESTS = new IdentityHashMap<>();
    private static final class Request {
        final MinecraftServer server;
        final RoomDefinition room;
        final RunningRuntimeState state;
        List<ServerPlayer> passengers;
        Request(MinecraftServer server, RoomDefinition room, RunningRuntimeState state) {
            this.server = server; this.room = room; this.state = state;
        }
    }
    private BoundaryVehicleDestruction() {}
    public static boolean destroy(Entity vehicle) {
        return destroy(vehicle, null, null);
    }
    public static boolean destroy(Entity vehicle, RoomDefinition room, RunningRuntimeState state) {
        if (vehicle == null || vehicle.isRemoved() || !VehicleCompatFacade.isVehicleEntity(vehicle)) return false;
        if (REQUESTS.containsKey(vehicle)) return true;
        try {
            if (flag(vehicle, "isDestroyed") || flag(vehicle, "isWreck")) return false;
            MinecraftServer server = vehicle.getServer();
            if (server == null) return false;
            Method health = vehicle.getClass().getMethod("getHealth");
            // Track the vehicle, never a passenger list at the timeout/health assignment.
            REQUESTS.put(vehicle, new Request(server, room, state));
            vehicle.getClass().getMethod("setHealth", float.class).invoke(vehicle, 0F);
            if (isYwzj(vehicle.getClass())) {
                // YWZJ checks zero health in its hurt callback, not in setHealth/tick.
                // Zero damage triggers the first wreck/explosion once, without damaging the resulting wreck.
                vehicle.hurt(vehicle.damageSources().genericKill(), 0F);
                boolean destroyed = flag(vehicle, "isDestroyed") || vehicle.isRemoved();
                if (!destroyed) REQUESTS.remove(vehicle);
                return destroyed;
            }
            // Superb Warfare's server tick observes zero health and invokes its native destroy().
            return ((Number)health.invoke(vehicle)).floatValue() <= 0;
        } catch (ReflectiveOperationException | RuntimeException error) {
            REQUESTS.remove(vehicle);
            LogUtils.getLogger().error("Unable to trigger native boundary vehicle destruction for {}", vehicle.getClass().getName(), error);
            return false;
        }
    }

    /** Called immediately before the mod's native explosion/passenger damage. */
    public static void beginNativeDestruction(Entity vehicle) {
        Request request = REQUESTS.get(vehicle);
        if (request == null || request.passengers != null) return;
        List<ServerPlayer> occupants = new ArrayList<>();
        collectPassengers(vehicle, occupants, Collections.newSetFromMap(new IdentityHashMap<>()));
        request.passengers = List.copyOf(occupants);
        // Set every marker before the first blast death can dispatch a room death event.
        for (ServerPlayer player : request.passengers) UnifiedBoundaryService.markDeserter(player);
    }

    private static void collectPassengers(Entity vehicle, List<ServerPlayer> occupants, Set<Entity> visited) {
        if (!visited.add(vehicle)) return;
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof ServerPlayer player) occupants.add(player);
            collectPassengers(passenger, occupants, visited);
        }
    }

    /** Native explosion/removal has finished; kill only survivors not already in deployment. */
    public static void finishNativeDestruction(Entity vehicle) {
        Request request = REQUESTS.remove(vehicle);
        if (request == null || request.passengers == null) return;
        for (ServerPlayer player : request.passengers) {
            if (player.server == request.server && request.server.getPlayerList().getPlayer(player.getUUID()) == player)
                UnifiedBoundaryService.killDeserter(player);
        }
    }

    public static void clear(MinecraftServer server) {
        REQUESTS.values().removeIf(request -> request.server == server);
    }
    public static void tick(MinecraftServer server) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(server);
        REQUESTS.entrySet().removeIf(entry -> {
            Request request = entry.getValue();
            if (request.server != server) return false;
            return entry.getKey().isRemoved() || entry.getKey().getServer() != server
                || (request.room != null && manager.findRunningState(request.room.roomId()).orElse(null) != request.state);
        });
    }
    private static boolean isYwzj(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            if (current.getName().equals("org.ywzj.vehicle.entity.vehicle.AbstractVehicle")) return true;
        return false;
    }
    private static boolean flag(Entity vehicle, String name) throws ReflectiveOperationException {
        try { return Boolean.TRUE.equals(vehicle.getClass().getMethod(name).invoke(vehicle)); }
        catch (NoSuchMethodException absent) { return false; }
    }
}
