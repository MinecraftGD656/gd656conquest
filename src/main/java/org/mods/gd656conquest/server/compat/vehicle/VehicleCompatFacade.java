package org.mods.gd656conquest.server.compat.vehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareVehicleCompatBridge;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleCompatBridge;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleSnapshot;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleSummonRequest;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleSummonResult;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

import java.util.List;
import java.util.Optional;

public final class VehicleCompatFacade {
    private VehicleCompatFacade() {
    }

    public static boolean isCompatAvailable(RoomVehicleBackend backend) {
        RoomVehicleBackend resolved = backend == null ? RoomVehicleBackend.defaultValue() : backend;
        return switch (resolved) {
            case YWZJ -> YwzjVehicleCompatBridge.isAvailable();
            case SUPERBWARFARE -> SuperbWarfareVehicleCompatBridge.isAvailable();
        };
    }

    public static List<String> listVehicleIds(RoomVehicleBackend backend, ServerLevel level) {
        RoomVehicleBackend resolved = backend == null ? RoomVehicleBackend.defaultValue() : backend;
        return switch (resolved) {
            case YWZJ -> YwzjVehicleCompatBridge.listVehicleIds();
            case SUPERBWARFARE -> SuperbWarfareVehicleCompatBridge.listVehicleIds(level);
        };
    }

    public static boolean isVehicleEntity(Entity entity) {
        return YwzjVehicleCompatBridge.isVehicleEntity(entity)
            || SuperbWarfareVehicleCompatBridge.isVehicleEntity(entity);
    }

    /** GD656Mods: 玩家是否为载具驾驶员 */
    public static boolean isDriver(Entity vehicle, Entity passenger) {
        if (YwzjVehicleCompatBridge.isVehicleEntity(vehicle)) {
            return YwzjVehicleCompatBridge.isDriver(vehicle, passenger);
        }
        return SuperbWarfareVehicleCompatBridge.isDriver(vehicle, passenger);
    }

    /** GD656Mods: 玩家当前座位是否有可操作的武器 */
    public static boolean hasOperableWeapon(Entity vehicle, Entity passenger) {
        if (YwzjVehicleCompatBridge.isVehicleEntity(vehicle)) {
            return YwzjVehicleCompatBridge.hasOperableWeapon(vehicle, passenger);
        }
        return SuperbWarfareVehicleCompatBridge.hasOperableWeapon(vehicle, passenger);
    }

    public static boolean isManagedVehicleEntity(Entity entity) {
        return YwzjVehicleCompatBridge.isManagedVehicleEntity(entity)
            || SuperbWarfareVehicleCompatBridge.isManagedVehicleEntity(entity);
    }

    public static boolean shouldLockMountedHotbar(Entity entity) {
        return isVehicleEntity(entity) && !isAircraftEntity(entity);
    }

    public static boolean isAircraftEntity(Entity entity) {
        if (!isVehicleEntity(entity)) {
            return false;
        }
        if (SuperbWarfareVehicleCompatBridge.isAircraftEntity(entity)) {
            return true;
        }
        return matchesAircraftKeywords(entity);
    }

    public static Optional<VehicleCompatSnapshot> snapshot(Entity entity) {
        Optional<VehicleCompatSnapshot> ywzj = YwzjVehicleCompatBridge.snapshot(entity).map(VehicleCompatFacade::fromYwzjSnapshot);
        if (ywzj.isPresent()) {
            return ywzj;
        }
        return SuperbWarfareVehicleCompatBridge.snapshot(entity);
    }

    public static VehicleCompatSummonResult summon(VehicleCompatSummonRequest request) {
        RoomVehicleBackend backend = request == null || request.backend() == null
            ? RoomVehicleBackend.defaultValue()
            : request.backend();
        return switch (backend) {
            case YWZJ -> fromYwzjSummonResult(YwzjVehicleCompatBridge.summon(new YwzjVehicleSummonRequest(
                request.level(),
                request.position(),
                request.yRot(),
                request.vehicleId(),
                request.displayId()
            )));
            case SUPERBWARFARE -> SuperbWarfareVehicleCompatBridge.summon(request);
        };
    }

    public static boolean canAcceptPassenger(Entity vehicle, Entity passenger) {
        if (YwzjVehicleCompatBridge.isVehicleEntity(vehicle)) {
            return YwzjVehicleCompatBridge.canAcceptPassenger(vehicle, passenger);
        }
        return SuperbWarfareVehicleCompatBridge.canAcceptPassenger(vehicle, passenger);
    }

    public static boolean ensureDriverSeat(Entity vehicle, ServerPlayer player) {
        if (YwzjVehicleCompatBridge.isVehicleEntity(vehicle)) {
            return YwzjVehicleCompatBridge.ensureDriverSeat(vehicle, player);
        }
        return SuperbWarfareVehicleCompatBridge.ensureDriverSeat(vehicle, player);
    }

    public static boolean alignPassengerToSeat(Entity vehicle, Entity passenger) {
        if (YwzjVehicleCompatBridge.isVehicleEntity(vehicle)) {
            return YwzjVehicleCompatBridge.alignPassengerToSeat(vehicle, passenger);
        }
        return SuperbWarfareVehicleCompatBridge.alignPassengerToSeat(vehicle, passenger);
    }

    public static boolean syncPassengerSeatState(Entity vehicle, ServerPlayer player) {
        if (YwzjVehicleCompatBridge.isVehicleEntity(vehicle)) {
            return YwzjVehicleCompatBridge.syncPassengerSeatState(vehicle, player);
        }
        return SuperbWarfareVehicleCompatBridge.syncPassengerSeatState(vehicle, player);
    }

    public static boolean destroyManagedVehicleNow(Entity entity) {
        if (YwzjVehicleCompatBridge.isManagedVehicleEntity(entity)) {
            return YwzjVehicleCompatBridge.destroyManagedVehicleNow(entity);
        }
        return SuperbWarfareVehicleCompatBridge.destroyManagedVehicleNow(entity);
    }

    public static void ensureManagedVehicleSupplies(Entity entity) {
        if (YwzjVehicleCompatBridge.isManagedVehicleEntity(entity)) {
            YwzjVehicleCompatBridge.ensureCreativeAmmoLoaded(entity);
            return;
        }
        if (SuperbWarfareVehicleCompatBridge.isManagedVehicleEntity(entity)) {
            SuperbWarfareVehicleCompatBridge.ensureCreativeAmmoLoaded(entity);
            SuperbWarfareVehicleCompatBridge.ensureFuelFull(entity);
        }
    }

    public static boolean isManagedVehicleContainerMenu(AbstractContainerMenu menu) {
        return YwzjVehicleCompatBridge.isManagedVehicleContainerMenu(menu)
            || SuperbWarfareVehicleCompatBridge.isManagedVehicleContainerMenu(menu);
    }

    public static int resolveManagedVehicleContainerSlotCount(AbstractContainerMenu menu) {
        if (YwzjVehicleCompatBridge.isManagedVehicleContainerMenu(menu)) {
            return YwzjVehicleCompatBridge.resolveManagedVehicleContainerSlotCount(menu);
        }
        return SuperbWarfareVehicleCompatBridge.resolveManagedVehicleContainerSlotCount(menu);
    }

    public static boolean isManagedVehicleCreativeAmmoSlot(AbstractContainerMenu menu, int slotIndex) {
        if (YwzjVehicleCompatBridge.isManagedVehicleContainerMenu(menu)) {
            return YwzjVehicleCompatBridge.isManagedVehicleCreativeAmmoSlot(menu, slotIndex);
        }
        return SuperbWarfareVehicleCompatBridge.isManagedVehicleCreativeAmmoSlot(menu, slotIndex);
    }

    public static boolean isCreativeAmmoStack(ItemStack stack) {
        return YwzjVehicleCompatBridge.isCreativeAmmoStack(stack)
            || SuperbWarfareVehicleCompatBridge.isCreativeAmmoStack(stack);
    }

    private static VehicleCompatSnapshot fromYwzjSnapshot(YwzjVehicleSnapshot snapshot) {
        return new VehicleCompatSnapshot(
            snapshot.entityUuid(),
            snapshot.entityId(),
            snapshot.vehicleId(),
            snapshot.displayId(),
            snapshot.displayName(),
            snapshot.destroyed(),
            snapshot.health(),
            snapshot.maxHealth(),
            snapshot.passengerCount(),
            snapshot.driverUuid(),
            snapshot.driverName(),
            snapshot.x(),
            snapshot.y(),
            snapshot.z()
        );
    }

    private static boolean matchesAircraftKeywords(Entity entity) {
        if (entity == null) {
            return false;
        }
        String vehicleId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(java.util.Locale.ROOT);
        String className = entity.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        String displayName = entity.getName() == null ? "" : entity.getName().getString().toLowerCase(java.util.Locale.ROOT);
        return containsAircraftKeyword(vehicleId)
            || containsAircraftKeyword(className)
            || containsAircraftKeyword(displayName);
    }

    private static boolean containsAircraftKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("aircraft")
            || text.contains("airplane")
            || text.contains("plane")
            || text.contains("helicopter")
            || text.contains("heli")
            || text.contains("airship")
            || text.contains("fighter")
            || text.contains("bomber")
            || text.contains("jet")
            || text.contains("飞机")
            || text.contains("直升机")
            || text.contains("飞艇");
    }

    private static VehicleCompatSummonResult fromYwzjSummonResult(YwzjVehicleSummonResult result) {
        return new VehicleCompatSummonResult(
            result.success(),
            result.message(),
            result.entityId(),
            result.entityUuid(),
            result.vehicleId(),
            result.displayId()
        );
    }
}
