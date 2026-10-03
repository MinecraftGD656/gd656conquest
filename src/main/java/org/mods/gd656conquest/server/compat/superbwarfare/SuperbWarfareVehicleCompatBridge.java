package org.mods.gd656conquest.server.compat.superbwarfare;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSnapshot;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSummonRequest;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSummonResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SuperbWarfareVehicleCompatBridge {
    public static final String MOD_ID = "superbwarfare";
    private static final ResourceLocation CREATIVE_AMMO_ITEM_ID = new ResourceLocation(MOD_ID, "creative_ammo_box");
    private static final String MANAGED_VEHICLE_PERSISTENT_KEY = "gd656conquestManagedVehicle";
    private static final String VEHICLE_ENTITY_CLASS_NAME = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity";
    private static final String VEHICLE_CONTAINER_MENU_CLASS_NAME = "com.atsuishio.superbwarfare.inventory.menu.AbstractVehicleContainerMenu";

    private static boolean reflectionResolved;
    private static boolean available;
    private static String availabilityDetail = "未初始化";
    private static volatile List<String> cachedVehicleIds;
    private static Class<?> vehicleEntityClass;
    private static Class<?> vehicleContainerMenuClass;
    private static Method getHealthMethod;
    private static Method getMaxHealthMethod;
    private static Method getEnergyMethod;
    private static Method setEnergyMethod;
    private static Method getMaxEnergyMethod;
    private static Method hasEnergyStorageMethod;
    private static Method getMaxPassengersMethod;
    private static Method getVehicleTypeMethod;
    private static Method changeSeatMethod;
    private static Method getNthEntityMethod;
    private static Method getSeatMethod;
    private static Method getSeatIndexMethod;
    private static Method getGunDataMethod;
    private static Method banHandMethod;
    private static Method isWreckMethod;
    private static Method getVehicleFromMenuMethod;
    private static Method getMenuRowsMethod;

    private SuperbWarfareVehicleCompatBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isAvailable() {
        return resolveReflection();
    }

    public static String describeAvailability() {
        if (!isLoaded()) {
            return "SuperbWarfare 未加载";
        }
        resolveReflection();
        return availabilityDetail == null || availabilityDetail.isBlank()
            ? (available ? "可用" : "已加载但反射初始化失败")
            : availabilityDetail;
    }

    /**
     * GD656Mods: 列出全部可部署的卓越前线载具 ID(含附属模组)。
     * 判据是"实体实例为 sbw VehicleEntity 子类", 而不是"注册命名空间为 superbwarfare":
     * 附属模组(例如 dragonrise_reforge)的载具注册在自己的命名空间下, 但同样继承 VehicleEntity,
     * 必须一并列出, 否则这些载具无法被房间配置选中与部署。
     * 探测需要实例化每个已注册实体类型才能判定, 而本方法还会被命令补全高频调用,
     * 故结果在服务端生命周期内缓存(EntityType 注册表在服务器运行期间不再变化)。
     */
    public static List<String> listVehicleIds(ServerLevel level) {
        if (!resolveReflection() || level == null) {
            return List.of();
        }
        List<String> cached = cachedVehicleIds;
        if (cached != null) {
            return cached;
        }
        return scanVehicleIds(level);
    }

    private static synchronized List<String> scanVehicleIds(ServerLevel level) {
        List<String> cached = cachedVehicleIds;
        if (cached != null) {
            return cached;
        }
        List<String> values = new ArrayList<>();
        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            EntityType<?> type = entry.getValue();
            ResourceLocation id = entry.getKey().location();
            if (type == null || id == null) {
                continue;
            }
            Entity probe = null;
            try {
                probe = type.create(level);
                if (probe != null && isVehicleEntity(probe)) {
                    values.add(id.toString());
                }
            } catch (Throwable ignored) {
            } finally {
                if (probe != null) {
                    probe.discard();
                }
            }
        }
        if (values.isEmpty()) {
            return List.of();
        }
        values.sort(Comparator.naturalOrder());
        cachedVehicleIds = List.copyOf(values);
        return cachedVehicleIds;
    }

    public static boolean isVehicleEntity(Entity entity) {
        return entity != null && resolveReflection() && vehicleEntityClass != null && vehicleEntityClass.isInstance(entity);
    }

    /**
     * GD656Mods: 玩家是否为载具驾驶员(第一个座位/0号实体)。
     */
    public static boolean isDriver(Entity vehicle, Entity passenger) {
        if (!isVehicleEntity(vehicle) || passenger == null) {
            return false;
        }
        // GD656Mods: 驾驶位判定按"乘客座位号 == 0"(1号驾驶位)。
        // getSeatIndex = orderedPassengers.indexOf(entity): orderedPassengers 是按座位号索引的数组
        // (空位为 null), indexOf 返回真实座位号, 且客户端本地同样维护该数组, 判定可靠。
        // 不使用 getTagSeatIndex(乘客 NBT 座位号): 客户端本地 NBT 可能未同步, 恒返回 0 导致所有座位误判为驾驶位。
        if (getSeatIndexMethod != null) {
            try {
                Object indexValue = getSeatIndexMethod.invoke(vehicle, passenger);
                if (indexValue instanceof Number number) {
                    return number.intValue() == 0;
                }
            } catch (Throwable ignored) {
            }
        }
        return passenger == resolveDriver(vehicle);
    }

    /**
     * GD656Mods: 玩家当前座位是否有可操作的武器。
     * 以该座位"当前选中的武器数据(getGunData(passenger))"为准:
     * 配置了武器但不可操作/未选中的座位(如坦克车长位)返回 false。
     */
    public static boolean hasOperableWeapon(Entity vehicle, Entity passenger) {
        if (!isVehicleEntity(vehicle) || passenger == null || getGunDataMethod == null) {
            return false;
        }
        try {
            Object gunData = getGunDataMethod.invoke(vehicle, passenger);
            return gunData != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * GD656Mods: 复用 sbw 模组权威判定 banHand(player):
     * 该座位是否有武器(getGunData)或 seat.banHand 配置 -> 禁用物品栏/快捷栏。
     * 无武器座位(如坦克车长/三号位)返回 false, 可自由切换物品栏。
     */
    public static boolean isBanHand(Entity vehicle, Entity passenger) {
        if (!isVehicleEntity(vehicle) || passenger == null || banHandMethod == null) {
            return false;
        }
        try {
            Object result = banHandMethod.invoke(vehicle, passenger);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isManagedVehicleEntity(Entity entity) {
        return isVehicleEntity(entity)
            && entity.getPersistentData().getBoolean(MANAGED_VEHICLE_PERSISTENT_KEY);
    }

    public static boolean isAircraftEntity(Entity entity) {
        if (!isVehicleEntity(entity) || getVehicleTypeMethod == null) {
            return false;
        }
        try {
            Object vehicleType = getVehicleTypeMethod.invoke(entity);
            if (vehicleType == null) {
                return false;
            }
            String typeName = vehicleType.toString();
            return "AIRPLANE".equalsIgnoreCase(typeName)
                || "HELICOPTER".equalsIgnoreCase(typeName)
                || "AIRSHIP".equalsIgnoreCase(typeName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Optional<VehicleCompatSnapshot> snapshot(Entity entity) {
        if (!isVehicleEntity(entity)) {
            return Optional.empty();
        }
        try {
            float health = toFloat(getHealthMethod.invoke(entity));
            float maxHealth = toFloat(getMaxHealthMethod.invoke(entity));
            Entity driver = resolveDriver(entity);
            String vehicleId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            String displayName = entity.getName() == null ? vehicleId : entity.getName().getString();
            return Optional.of(new VehicleCompatSnapshot(
                entity.getUUID(),
                entity.getId(),
                vehicleId,
                "",
                displayName == null || displayName.isBlank() ? vehicleId : displayName,
                readWreckState(entity) || entity.isRemoved(),
                health,
                maxHealth,
                entity.getPassengers().size(),
                driver == null ? null : driver.getUUID(),
                driver == null || driver.getName() == null ? "" : driver.getName().getString(),
                entity.getX(),
                entity.getY(),
                entity.getZ()
            ));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public static VehicleCompatSummonResult summon(VehicleCompatSummonRequest request) {
        if (!resolveReflection()) {
            return VehicleCompatSummonResult.failure("Superb Warfare 未加载或反射失败");
        }
        if (request == null || request.level() == null || request.position() == null) {
            return VehicleCompatSummonResult.failure("生成参数不完整");
        }
        ResourceLocation vehicleId = ResourceLocation.tryParse(blankToEmpty(request.vehicleId()));
        if (vehicleId == null) {
            return VehicleCompatSummonResult.failure("载具 ID 非法: " + request.vehicleId());
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(vehicleId);
        if (entityType == null || vehicleId.equals(BuiltInRegistries.ENTITY_TYPE.getDefaultKey())) {
            return VehicleCompatSummonResult.failure("未找到卓越前线载具: " + vehicleId);
        }
        Entity entity = entityType.create(request.level());
        if (!isVehicleEntity(entity)) {
            if (entity != null) {
                entity.discard();
            }
            return VehicleCompatSummonResult.failure("目标实体不是卓越前线载具: " + vehicleId);
        }
        Vec3 position = request.position();
        entity.setUUID(UUID.randomUUID());
        entity.moveTo(position.x(), position.y(), position.z(), request.yRot(), 0.0F);
        markManagedVehicle(entity);
        request.level().addFreshEntity(entity);
        ensureCreativeAmmoLoaded(entity);
        ensureFuelFull(entity);
        return VehicleCompatSummonResult.success(entity.getId(), entity.getUUID(), vehicleId.toString(), blankToEmpty(request.displayId()));
    }

    public static boolean ensureCreativeAmmoLoaded(Entity entity) {
        if (!isVehicleEntity(entity)) {
            return false;
        }
        Item creativeAmmoItem = resolveCreativeAmmoItem();
        if (creativeAmmoItem == null) {
            return false;
        }
        IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null || handler.getSlots() <= 0) {
            return false;
        }
        int firstEmptySlot = -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (isCreativeAmmoStack(stack)) {
                return true;
            }
            if (firstEmptySlot < 0 && (stack == null || stack.isEmpty())) {
                firstEmptySlot = slot;
            }
        }
        if (firstEmptySlot < 0) {
            return false;
        }
        ItemStack remainder = handler.insertItem(firstEmptySlot, new ItemStack(creativeAmmoItem), false);
        return remainder == null || remainder.isEmpty();
    }

    public static boolean ensureFuelFull(Entity entity) {
        if (!isVehicleEntity(entity) || !supportsEnergyStorage(entity)) {
            return false;
        }
        try {
            int maxEnergy = toInt(getMaxEnergyMethod.invoke(entity));
            if (maxEnergy <= 0) {
                return false;
            }
            int currentEnergy = toInt(getEnergyMethod.invoke(entity));
            if (currentEnergy >= maxEnergy) {
                return true;
            }
            setEnergyMethod.invoke(entity, maxEnergy);
            return toInt(getEnergyMethod.invoke(entity)) >= maxEnergy;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isCreativeAmmoStack(ItemStack stack) {
        Item creativeAmmoItem = resolveCreativeAmmoItem();
        return creativeAmmoItem != null
            && stack != null
            && !stack.isEmpty()
            && stack.getItem() == creativeAmmoItem;
    }

    public static boolean isManagedVehicleContainerMenu(AbstractContainerMenu menu) {
        return resolveManagedVehicleMenuVehicle(menu) != null;
    }

    public static int resolveManagedVehicleContainerSlotCount(AbstractContainerMenu menu) {
        Entity vehicle = resolveManagedVehicleMenuVehicle(menu);
        if (vehicle == null || getMenuRowsMethod == null) {
            return 0;
        }
        try {
            Object rows = getMenuRowsMethod.invoke(menu);
            return rows instanceof Number number ? Math.max(0, number.intValue() * 9) : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean isManagedVehicleCreativeAmmoSlot(AbstractContainerMenu menu, int slotIndex) {
        if (menu == null || slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return false;
        }
        int vehicleSlotCount = resolveManagedVehicleContainerSlotCount(menu);
        if (vehicleSlotCount <= 0) {
            return false;
        }
        int vehicleSlotStart = menu.slots.size() - vehicleSlotCount;
        if (slotIndex < vehicleSlotStart || slotIndex >= menu.slots.size()) {
            return false;
        }
        Slot slot = menu.slots.get(slotIndex);
        return slot != null && isCreativeAmmoStack(slot.getItem());
    }

    public static boolean canAcceptPassenger(Entity vehicle, Entity passenger) {
        if (!isVehicleEntity(vehicle) || passenger == null) {
            return false;
        }
        try {
            int maxPassengers = toInt(getMaxPassengersMethod.invoke(vehicle));
            return vehicle.getPassengers().size() < maxPassengers;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean ensureDriverSeat(Entity vehicle, ServerPlayer player) {
        if (!isVehicleEntity(vehicle) || player == null) {
            return false;
        }
        Entity driver = resolveDriver(vehicle);
        if (driver == player) {
            return true;
        }
        try {
            Object result = changeSeatMethod.invoke(vehicle, player, 0);
            return !(result instanceof Boolean value) || value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean alignPassengerToSeat(Entity vehicle, Entity passenger) {
        return isVehicleEntity(vehicle) && passenger != null;
    }

    public static boolean syncPassengerSeatState(Entity vehicle, ServerPlayer player) {
        return isVehicleEntity(vehicle) && player != null;
    }

    public static boolean destroyManagedVehicleNow(Entity entity) {
        if (!isManagedVehicleEntity(entity)) {
            return false;
        }
        entity.remove(Entity.RemovalReason.KILLED);
        entity.discard();
        return true;
    }

    private static Entity resolveDriver(Entity vehicle) {
        if (vehicle == null) {
            return null;
        }
        try {
            Object value = getNthEntityMethod.invoke(vehicle, 0);
            if (value instanceof Entity entity) {
                return entity;
            }
        } catch (Throwable ignored) {
        }
        return vehicle.getPassengers().isEmpty() ? null : vehicle.getPassengers().get(0);
    }

    private static boolean readWreckState(Entity vehicle) {
        if (!isVehicleEntity(vehicle) || isWreckMethod == null) {
            return false;
        }
        try {
            Object value = isWreckMethod.invoke(vehicle);
            return value instanceof Boolean state && state;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markManagedVehicle(Entity entity) {
        if (entity != null) {
            entity.getPersistentData().putBoolean(MANAGED_VEHICLE_PERSISTENT_KEY, true);
        }
    }

    private static Entity resolveManagedVehicleMenuVehicle(AbstractContainerMenu menu) {
        if (menu == null || !resolveReflection() || vehicleContainerMenuClass == null || getVehicleFromMenuMethod == null) {
            return null;
        }
        if (!vehicleContainerMenuClass.isInstance(menu)) {
            return null;
        }
        try {
            Object vehicle = getVehicleFromMenuMethod.invoke(menu);
            return vehicle instanceof Entity entity && isManagedVehicleEntity(entity) ? entity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized boolean resolveReflection() {
        if (reflectionResolved) {
            return available;
        }
        reflectionResolved = true;
        available = false;
        availabilityDetail = "已加载但反射初始化失败";
        if (!isLoaded()) {
            availabilityDetail = "SuperbWarfare 未加载";
            return false;
        }
        try {
            vehicleEntityClass = Class.forName(VEHICLE_ENTITY_CLASS_NAME);
            getHealthMethod = vehicleEntityClass.getMethod("getHealth");
            getMaxHealthMethod = vehicleEntityClass.getMethod("getMaxHealth");
            getEnergyMethod = vehicleEntityClass.getMethod("getEnergy");
            setEnergyMethod = vehicleEntityClass.getMethod("setEnergy", int.class);
            getMaxEnergyMethod = vehicleEntityClass.getMethod("getMaxEnergy");
            hasEnergyStorageMethod = vehicleEntityClass.getMethod("hasEnergyStorage");
            getMaxPassengersMethod = vehicleEntityClass.getMethod("getMaxPassengers");
            getVehicleTypeMethod = vehicleEntityClass.getMethod("getVehicleType");
            changeSeatMethod = vehicleEntityClass.getMethod("changeSeat", Entity.class, int.class);
            getNthEntityMethod = vehicleEntityClass.getMethod("getNthEntity", int.class);
            getSeatMethod = resolveSeatMethod(vehicleEntityClass);
            getSeatIndexMethod = resolveSeatIndexMethod(vehicleEntityClass);
            getGunDataMethod = resolveGunDataMethod(vehicleEntityClass);
            banHandMethod = resolveBanHandMethod(vehicleEntityClass);
            isWreckMethod = resolveMethod(vehicleEntityClass, "isWreck", "getIsWreck");
            resolveMenuReflection();
            available = true;
            if (vehicleContainerMenuClass == null || getVehicleFromMenuMethod == null || getMenuRowsMethod == null) {
                availabilityDetail = "可用，容器菜单反射不可用";
            } else {
                availabilityDetail = "可用";
            }
        } catch (Throwable throwable) {
            available = false;
            availabilityDetail = "实体反射失败: " + throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null || throwable.getMessage().isBlank() ? "" : " - " + throwable.getMessage());
        }
        return available;
    }

    private static void resolveMenuReflection() {
        try {
            vehicleContainerMenuClass = Class.forName(VEHICLE_CONTAINER_MENU_CLASS_NAME);
            getVehicleFromMenuMethod = vehicleContainerMenuClass.getMethod("getVehicle");
            getMenuRowsMethod = vehicleContainerMenuClass.getMethod("getRows");
        } catch (Throwable ignored) {
            vehicleContainerMenuClass = null;
            getVehicleFromMenuMethod = null;
            getMenuRowsMethod = null;
        }
    }

    private static Method resolveMethod(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                return owner.getMethod(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** GD656Mods: 解析 getSeat(Entity) 重载(按乘客实体取座位信息), 用于判断座位是否有可操作武器 */
    private static Method resolveSeatMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            return vehicleClass.getMethod("getSeat", Entity.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** GD656Mods: 解析 getSeatIndex(Entity) 重载(乘客在按座位号索引的有序乘客数组中的位置, 即真实座位号) */
    private static Method resolveSeatIndexMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            return vehicleClass.getMethod("getSeatIndex", Entity.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** GD656Mods: 解析 getGunData(Entity) 重载(按乘客实体取该座位当前选中的武器数据) */
    private static Method resolveGunDataMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            return vehicleClass.getMethod("getGunData", Entity.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** GD656Mods: 解析 banHand(LivingEntity)(载具座位是否禁用物品栏/快捷栏, sbw 权威判定) */
    private static Method resolveBanHandMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            return vehicleClass.getMethod("banHand", net.minecraft.world.entity.LivingEntity.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float toFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0F;
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean supportsEnergyStorage(Entity entity) {
        if (!isVehicleEntity(entity) || hasEnergyStorageMethod == null) {
            return false;
        }
        try {
            Object result = hasEnergyStorageMethod.invoke(entity);
            return result instanceof Boolean value && value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Item resolveCreativeAmmoItem() {
        return BuiltInRegistries.ITEM.get(CREATIVE_AMMO_ITEM_ID);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
