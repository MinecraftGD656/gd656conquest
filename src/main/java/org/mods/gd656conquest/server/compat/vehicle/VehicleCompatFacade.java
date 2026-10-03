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

    /**
     * GD656Mods: 该座位是否应锁定"物品栏选中槽位"(selected 不能改变)。
     * 仅 1 号驾驶位(驾驶员)启用物品栏锁定, 其他座位(2号炮手/车长/三号位/副驾驶等)一律不锁定。
     * 注意: 仅锁定选中槽位, 绝不拦截滚轮事件本身。
     */
    public static boolean shouldLockHotbarForSeat(Entity vehicle, Entity passenger) {
        return isDriver(vehicle, passenger);
    }

    public static boolean isManagedVehicleEntity(Entity entity) {
        return YwzjVehicleCompatBridge.isManagedVehicleEntity(entity)
            || SuperbWarfareVehicleCompatBridge.isManagedVehicleEntity(entity);
    }

    public static boolean shouldLockMountedHotbar(Entity entity) {
        return isVehicleEntity(entity) && !isAircraftEntity(entity);
    }

    /**
     * GD656Mods: 玩家死亡时的强制下车。
     *
     * <p>背景: 玩家在载具上阵亡后仍能操作载具武器 —— 因为服务端只校验"是该载具的乘客/座位",
     * 而死亡并不会让人下车, 于是"死亡 → 重生"之间存在可用武器的窗口。
     *
     * <p>为什么直接用原版 {@code stopRiding()}: 卓越前线与万众载具都通过
     * {@code Player.wantsToStopRiding()} 恒返回 false 来禁止"主动下车"(它们要求走自己的换座/下车机制),
     * 而 {@code Entity.stopRiding()} 内部走的是 {@code removeVehicle() → vehicle.removePassenger(this)},
     * <b>不经过该判定</b>, 因此服务端强制下车对两个模组同样有效(也顺带绕过了卓越前线的座位级
     * {@code allowEjection} 限制 —— 死亡必须离车, 不应被座位开关挡住)。
     *
     * <p>兜底说明: 这里没有可用的公开 API 再"强制"一次 —— {@code Entity.removePassenger} 是
     * protected, 而 {@code ejectPassengers()} 会把车上所有人一起弹出(会连带踢掉仍在操作武器的车长/炮手),
     * 因此只在未生效时记录具体车载类型, 便于定位是哪个模组改了下车路径。
     *
     * @return 调用后玩家是否已不在载具上
     */
    public static boolean forceDismountForDeath(ServerPlayer player) {
        if (player == null || !player.isPassenger()) {
            return false;
        }
        Entity vehicle = player.getVehicle();
        player.stopRiding();
        if (!player.isPassenger()) {
            return true;
        }
        // 理论上不可达: 两个模组都只 mixin 了 wantsToStopRiding(拦"主动下车"), 未拦截 stopRiding/removeVehicle
        org.mods.gd656conquest.server.shared.log.ServerLogger.failure(
            "死亡强制下车未生效(载具模组拦截?): player=%s vehicle=%s",
            player.getScoreboardName(),
            vehicle == null ? "-" : vehicle.getClass().getName()
        );
        return false;
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
