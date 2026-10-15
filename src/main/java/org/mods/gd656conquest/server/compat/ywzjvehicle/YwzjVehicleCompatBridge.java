package org.mods.gd656conquest.server.compat.ywzjvehicle;

import net.minecraft.world.Container;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class YwzjVehicleCompatBridge {
    public static final String MOD_ID = "ywzj_vehicle";
    private static final ResourceLocation AMMO_CREATIVE_ITEM_ID = new ResourceLocation(MOD_ID, "ammo_creative");
    private static final String MANAGED_VEHICLE_PERSISTENT_KEY = "gd656conquestManagedVehicle";

    private static boolean reflectionResolved;
    private static boolean available;
    private static String lastFailureReason = "";
    private static Class<?> abstractVehicleClass;
    private static Method summonMethod;
    private static Method getVehicleDataManagerMethod;
    private static Method getVehicleDataMapMethod;
    private static Method getVehicleIdMethod;
    private static Method getDisplayIdMethod;
    private static Method getDriverMethod;
    private static Method changeSeatMethod;
    private static Method getHealthMethod;
    private static Method getMaxHealthMethod;
    private static Method isDestroyedMethod;
    private static Method getItemStacksMethod;
    private static Method canAddPassengerMethod;
    private static Method passengerCapacityMethod;
    private static Method getOwnOperatorUnitMethod;
    private static Method worldSeatPositionMethod;
    private static Method applySeatRotMethod;
    private static Method positionRiderMethod;
    private static SimpleChannel networkChannel;
    private static java.lang.reflect.Constructor<?> serverVehicleSeatsChangeConstructor;

    private YwzjVehicleCompatBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isAvailable() {
        return resolveReflection();
    }

    public static String describeAvailability() {
        if (!isLoaded()) {
            return "模组未加载";
        }
        return resolveReflection()
            ? "兼容桥可用"
            : "模组已加载，但 API 反射失败: " + (lastFailureReason == null || lastFailureReason.isBlank() ? "-" : lastFailureReason);
    }

    public static List<String> listVehicleIds() {
        if (!resolveReflection()) {
            return List.of();
        }
        try {
            Object dataManager = getVehicleDataManagerMethod.invoke(null);
            Object mapValue = getVehicleDataMapMethod.invoke(dataManager);
            if (!(mapValue instanceof Map<?, ?> map) || map.isEmpty()) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (Object key : map.keySet()) {
                String id = stringifyResourceLocation(key);
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            ids.sort(Comparator.naturalOrder());
            return ids;
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    public static boolean isVehicleEntity(Entity entity) {
        return entity != null && resolveReflection() && abstractVehicleClass != null && abstractVehicleClass.isInstance(entity);
    }

    public static boolean isManagedVehicleEntity(Entity entity) {
        return isVehicleEntity(entity)
            && entity.getPersistentData().getBoolean(MANAGED_VEHICLE_PERSISTENT_KEY);
    }

    public static boolean isAircraftId(String vehicleId) {
        if (!resolveReflection()) return false;
        try {
            Object values = getVehicleDataMapMethod.invoke(getVehicleDataManagerMethod.invoke(null));
            Object data = values instanceof Map<?, ?> map ? map.get(ResourceLocation.tryParse(vehicleId)) : null;
            for (Class<?> type = data == null ? null : data.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getSimpleName().equals("FixedWingVehicleData") || type.getSimpleName().equals("RotaryWingVehicleData")) return true;
            }
        } catch (ReflectiveOperationException ignored) {}
        return false;
    }

    public static Optional<YwzjVehicleSnapshot> snapshot(Entity entity) {
        if (!isVehicleEntity(entity)) {
            return Optional.empty();
        }
        try {
            String vehicleId = stringifyResourceLocation(getVehicleIdMethod.invoke(entity));
            String displayId = stringifyResourceLocation(getDisplayIdMethod.invoke(entity));
            Object driverValue = getDriverMethod.invoke(entity);
            UUID driverUuid = driverValue instanceof Entity driverEntity ? driverEntity.getUUID() : null;
            String driverName = driverValue instanceof Entity driverEntity ? driverEntity.getName().getString() : "";
            float health = toFloat(getHealthMethod.invoke(entity));
            float maxHealth = toFloat(getMaxHealthMethod.invoke(entity));
            boolean destroyed = Boolean.TRUE.equals(isDestroyedMethod.invoke(entity));
            Component entityName = entity.getName();
            String displayName = entityName == null ? vehicleId : entityName.getString();
            return Optional.of(new YwzjVehicleSnapshot(
                entity.getUUID(),
                entity.getId(),
                vehicleId,
                displayId,
                displayName == null || displayName.isBlank() ? vehicleId : displayName,
                destroyed,
                health,
                maxHealth,
                entity.getPassengers().size(),
                driverUuid,
                driverName == null ? "" : driverName,
                entity.getX(),
                entity.getY(),
                entity.getZ()
            ));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<YwzjVehicleSnapshot> snapshotMountedVehicle(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        return snapshot(player.getVehicle());
    }

    public static YwzjVehicleSummonResult summon(YwzjVehicleSummonRequest request) {
        if (!resolveReflection()) {
            return YwzjVehicleSummonResult.failure(describeAvailability());
        }
        if (request == null || request.level() == null || request.position() == null) {
            return YwzjVehicleSummonResult.failure("生成参数不完整");
        }
        ResourceLocation vehicleId = ResourceLocation.tryParse(blankToEmpty(request.vehicleId()));
        if (vehicleId == null) {
            return YwzjVehicleSummonResult.failure("载具 ID 非法: " + request.vehicleId());
        }
        ResourceLocation displayId = ResourceLocation.tryParse(blankToFallback(request.displayId(), vehicleId.toString()));
        if (displayId == null) {
            displayId = vehicleId;
        }
        try {
            Object result = summonMethod.invoke(null, request.level(), request.position(), request.yRot(), vehicleId, displayId);
            if (!(result instanceof Entity entity)) {
                return YwzjVehicleSummonResult.failure("API 未返回载具实体");
            }
            alignVehicleBottomToPosition(entity, request.position());
            markManagedVehicle(entity);
            ensureCreativeAmmoLoaded(entity);
            return YwzjVehicleSummonResult.success(entity.getId(), entity.getUUID(), vehicleId.toString(), displayId.toString());
        } catch (ReflectiveOperationException ignored) {
            return YwzjVehicleSummonResult.failure("调用载具生成 API 失败");
        }
    }

    public static boolean ensureCreativeAmmoLoaded(Entity entity) {
        if (!isVehicleEntity(entity)) {
            return false;
        }
        Item creativeAmmoItem = resolveCreativeAmmoItem();
        if (creativeAmmoItem == null) {
            return false;
        }
        try {
            List<ItemStack> itemStacks = resolveVehicleItemStacks(entity);
            if (itemStacks.isEmpty()) {
                return false;
            }
            int firstEmptySlot = -1;
            for (int slot = 0; slot < itemStacks.size(); slot++) {
                ItemStack stack = itemStacks.get(slot);
                if (!stack.isEmpty() && stack.getItem() == creativeAmmoItem) {
                    return true;
                }
                if (firstEmptySlot < 0 && stack.isEmpty()) {
                    firstEmptySlot = slot;
                }
            }
            if (firstEmptySlot < 0) {
                return false;
            }
            itemStacks.set(firstEmptySlot, new ItemStack(creativeAmmoItem));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
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
        return resolveManagedVehicleContainer(menu) != null;
    }

    public static int resolveManagedVehicleContainerSlotCount(AbstractContainerMenu menu) {
        Container managedVehicleContainer = resolveManagedVehicleContainer(menu);
        if (managedVehicleContainer == null || menu == null) {
            return 0;
        }
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot == null || slot.container != managedVehicleContainer) {
                break;
            }
            count++;
        }
        return count;
    }

    public static boolean isManagedVehicleCreativeAmmoSlot(AbstractContainerMenu menu, int slotIndex) {
        Container managedVehicleContainer = resolveManagedVehicleContainer(menu);
        if (managedVehicleContainer == null || menu == null || slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return false;
        }
        Slot slot = menu.slots.get(slotIndex);
        return slot != null
            && slot.container == managedVehicleContainer
            && isCreativeAmmoStack(slot.getItem());
    }

    public static boolean canAcceptPassenger(Entity vehicle, Entity passenger) {
        if (!isVehicleEntity(vehicle) || passenger == null) {
            return false;
        }
        try {
            if (canAddPassengerMethod != null) {
                Object result = canAddPassengerMethod.invoke(vehicle, passenger);
                if (result instanceof Boolean value) {
                    return value;
                }
            }
            if (passengerCapacityMethod != null) {
                Object result = passengerCapacityMethod.invoke(vehicle);
                if (result instanceof Number number) {
                    return number.intValue() > vehicle.getPassengers().size();
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        return true;
    }

    public static boolean ensureDriverSeat(Entity vehicle, ServerPlayer player) {
        if (!isVehicleEntity(vehicle) || player == null || getDriverMethod == null) {
            return false;
        }
        try {
            Object currentDriver = getDriverMethod.invoke(vehicle);
            if (currentDriver == player) {
                return true;
            }
            if (changeSeatMethod == null) {
                return false;
            }
            Object result = changeSeatMethod.invoke(vehicle, player, 0);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean alignPassengerToSeat(Entity vehicle, Entity passenger) {
        if (!isVehicleEntity(vehicle) || passenger == null || getOwnOperatorUnitMethod == null || worldSeatPositionMethod == null) {
            return false;
        }
        try {
            Object partUnit = getOwnOperatorUnitMethod.invoke(vehicle, passenger);
            if (partUnit == null) {
                return false;
            }
            if (applySeatRotMethod != null) {
                applySeatRotMethod.invoke(partUnit, passenger);
            }
            Object positionValue = worldSeatPositionMethod.invoke(partUnit);
            if (!(positionValue instanceof Vec3 seatPosition)) {
                return false;
            }
            if (positionRiderMethod != null && passenger instanceof net.minecraft.world.entity.LivingEntity) {
                positionRiderMethod.invoke(vehicle, passenger, (Entity.MoveFunction) (movedPassenger, x, y, z) -> {
                    movedPassenger.setPos(x, y, z);
                    movedPassenger.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    movedPassenger.fallDistance = 0.0F;
                });
            }
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean syncPassengerSeatState(Entity vehicle, ServerPlayer player) {
        if (!isVehicleEntity(vehicle) || player == null || networkChannel == null || serverVehicleSeatsChangeConstructor == null) {
            return false;
        }
        try {
            Object packet = serverVehicleSeatsChangeConstructor.newInstance(vehicle);
            networkChannel.send(PacketDistributor.PLAYER.with(() -> player), packet);
            networkChannel.send(PacketDistributor.TRACKING_ENTITY.with(() -> vehicle), packet);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean destroyManagedVehicleNow(Entity entity) {
        if (!isManagedVehicleEntity(entity)) {
            return false;
        }
        try {
            entity.hurt(entity.damageSources().genericKill(), Float.MAX_VALUE);
            if (!entity.isRemoved() && snapshot(entity).map(YwzjVehicleSnapshot::destroyed).orElse(false)) {
                entity.hurt(entity.damageSources().genericKill(), Float.MAX_VALUE);
            }
            if (!entity.isRemoved()) {
                entity.discard();
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void markManagedVehicle(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentData().putBoolean(MANAGED_VEHICLE_PERSISTENT_KEY, true);
    }

    private static void alignVehicleBottomToPosition(Entity entity, Vec3 desiredBottomPosition) {
        if (entity == null || desiredBottomPosition == null) {
            return;
        }
        double currentBottomY = entity.getBoundingBox().minY;
        double deltaY = desiredBottomPosition.y - currentBottomY;
        if (Math.abs(deltaY) < 1.0E-4D) {
            return;
        }
        entity.setPos(entity.getX(), entity.getY() + deltaY, entity.getZ());
    }

    private static boolean resolveReflection() {
        if (reflectionResolved) {
            return available;
        }
        reflectionResolved = true;
        if (!isLoaded()) {
            available = false;
            lastFailureReason = "模组未通过 ModList 检测";
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.ywzj.vehicle.api.YwzjVehicleAPI");
            abstractVehicleClass = Class.forName("org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
            Class<?> containerCraftClass = Class.forName("org.ywzj.vehicle.entity.ContainerCraft");
            summonMethod = apiClass.getMethod("summon", Level.class, Vec3.class, float.class, ResourceLocation.class, ResourceLocation.class);
            getVehicleDataManagerMethod = apiClass.getMethod("getVehicleDataManager");
            Class<?> dataManagerInterface = Class.forName("org.ywzj.vehicle.api.custom.IVehicleDataManager");
            getVehicleDataMapMethod = dataManagerInterface.getMethod("getVehicleData");
            getVehicleIdMethod = abstractVehicleClass.getMethod("getVehicleId");
            getDisplayIdMethod = abstractVehicleClass.getMethod("getDisplayId");
            getDriverMethod = abstractVehicleClass.getMethod("getDriver");
            changeSeatMethod = resolveChangeSeatMethod(abstractVehicleClass);
            getHealthMethod = abstractVehicleClass.getMethod("getHealth");
            getMaxHealthMethod = abstractVehicleClass.getMethod("getMaxHealth");
            isDestroyedMethod = abstractVehicleClass.getMethod("isDestroyed");
            getItemStacksMethod = resolveItemStacksMethod(containerCraftClass);
            canAddPassengerMethod = resolveCanAddPassengerMethod(abstractVehicleClass);
            passengerCapacityMethod = resolvePassengerCapacityMethod(abstractVehicleClass);
            getOwnOperatorUnitMethod = resolveOwnOperatorUnitMethod(abstractVehicleClass);
            worldSeatPositionMethod = resolveWorldSeatPositionMethod(abstractVehicleClass);
            applySeatRotMethod = resolveApplySeatRotMethod(abstractVehicleClass);
            positionRiderMethod = resolvePositionRiderMethod(abstractVehicleClass);
            networkChannel = resolveNetworkChannel();
            serverVehicleSeatsChangeConstructor = resolveServerVehicleSeatsChangeConstructor(abstractVehicleClass);
            if (getItemStacksMethod == null) {
                throw new NoSuchMethodException("org.ywzj.vehicle.entity.ContainerCraft.<item stack accessor>");
            }
            available = true;
            lastFailureReason = "";
        } catch (Throwable ignored) {
            abstractVehicleClass = null;
            summonMethod = null;
            getVehicleDataManagerMethod = null;
            getVehicleDataMapMethod = null;
            getVehicleIdMethod = null;
            getDisplayIdMethod = null;
            getDriverMethod = null;
            changeSeatMethod = null;
            getHealthMethod = null;
            getMaxHealthMethod = null;
            isDestroyedMethod = null;
            getItemStacksMethod = null;
            canAddPassengerMethod = null;
            passengerCapacityMethod = null;
            getOwnOperatorUnitMethod = null;
            worldSeatPositionMethod = null;
            applySeatRotMethod = null;
            positionRiderMethod = null;
            networkChannel = null;
            serverVehicleSeatsChangeConstructor = null;
            available = false;
            lastFailureReason = ignored.getClass().getSimpleName() + ": " + ignored.getMessage();
        }
        return available;
    }

    private static String stringifyResourceLocation(Object value) {
        return value == null ? "" : Objects.toString(value, "");
    }

    private static float toFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0F;
    }

    private static Container resolveManagedVehicleContainer(AbstractContainerMenu menu) {
        if (menu == null || menu.slots.isEmpty()) {
            return null;
        }
        Slot firstSlot = menu.slots.get(0);
        if (firstSlot == null) {
            return null;
        }
        Container container = firstSlot.container;
        if (!(container instanceof Entity entity) || !isManagedVehicleEntity(entity)) {
            return null;
        }
        return container;
    }

    private static Item resolveCreativeAmmoItem() {
        return BuiltInRegistries.ITEM.getOptional(AMMO_CREATIVE_ITEM_ID).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> resolveVehicleItemStacks(Entity entity) throws ReflectiveOperationException {
        if (entity == null || getItemStacksMethod == null) {
            return List.of();
        }
        Object result = getItemStacksMethod.invoke(entity);
        if (result instanceof List<?> list) {
            return (List<ItemStack>) list;
        }
        return List.of();
    }

    private static Method resolveItemStacksMethod(Class<?> containerCraftClass) {
        if (containerCraftClass == null) {
            return null;
        }
        for (Method method : containerCraftClass.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!List.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if ("getItemStacks".equals(method.getName()) || "m_213659_".equals(method.getName())) {
                return method;
            }
        }
        for (Method method : containerCraftClass.getMethods()) {
            if (method.getParameterCount() == 0 && List.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static Method resolveCanAddPassengerMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        for (Method method : vehicleClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 1
                || method.getParameterTypes()[0] != Entity.class
                || method.getReturnType() != boolean.class) {
                continue;
            }
            if ("canAddPassenger".equals(method.getName()) || "m_7310_".equals(method.getName())) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Method method : vehicleClass.getDeclaredMethods()) {
            if (method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Entity.class
                && method.getReturnType() == boolean.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method resolveChangeSeatMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        for (Method method : vehicleClass.getMethods()) {
            if (!"changeSeat".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            if (method.getParameterTypes()[1] != int.class || method.getReturnType() != boolean.class) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static Method resolvePassengerCapacityMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        for (Method method : vehicleClass.getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() != int.class) {
                continue;
            }
            if ("passengerCapacity".equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    private static Method resolveOwnOperatorUnitMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        for (Method method : vehicleClass.getMethods()) {
            if (method.getParameterCount() == 1
                && "getOwnOperatorUnit".equals(method.getName())) {
                return method;
            }
        }
        for (Method method : vehicleClass.getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() != Void.TYPE) {
                return method;
            }
        }
        return null;
    }

    private static Method resolveWorldSeatPositionMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            Class<?> partUnitClass = Class.forName("org.ywzj.vehicle.vehicle.part.PartUnit");
            return partUnitClass.getMethod("worldSeatPosition");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveApplySeatRotMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            Class<?> partUnitClass = Class.forName("org.ywzj.vehicle.vehicle.part.PartUnit");
            for (Method method : partUnitClass.getMethods()) {
                if (method.getName().equals("applySeatRot") && method.getParameterCount() == 1) {
                    return method;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static Method resolvePositionRiderMethod(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            Method method = vehicleClass.getDeclaredMethod("positionRider", Entity.class, Entity.MoveFunction.class);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SimpleChannel resolveNetworkChannel() {
        try {
            Class<?> channelClass = Class.forName("org.ywzj.vehicle.network.Channel");
            java.lang.reflect.Field field = channelClass.getField("CHANNEL");
            Object value = field.get(null);
            return value instanceof SimpleChannel simpleChannel ? simpleChannel : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static java.lang.reflect.Constructor<?> resolveServerVehicleSeatsChangeConstructor(Class<?> vehicleClass) {
        if (vehicleClass == null) {
            return null;
        }
        try {
            Class<?> packetClass = Class.forName("org.ywzj.vehicle.network.message.ServerVehicleSeatsChange");
            return packetClass.getConstructor(vehicleClass);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToFallback(String value, String fallback) {
        String trimmed = blankToEmpty(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
