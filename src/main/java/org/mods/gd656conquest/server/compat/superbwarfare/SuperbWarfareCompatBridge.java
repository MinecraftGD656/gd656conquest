package org.mods.gd656conquest.server.compat.superbwarfare;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class SuperbWarfareCompatBridge {
    private static final String MOD_ID = "superbwarfare";
    private static final String GUN_ITEM_CLASS_NAME = "com.atsuishio.superbwarfare.item.gun.GunItem";
    private static final String GUN_DATA_CLASS_NAME = "com.atsuishio.superbwarfare.data.gun.GunData";
    private static final ResourceLocation ARTILLERY_INDICATOR_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "artillery_indicator");
    private static final String TAG_GD656_SHELLING_REQUEST = "gd656ShellingRequest";
    private static final String TAG_GD656_ROOM_ID = "gd656RoomId";
    private static final String TAG_GD656_PLAYER_ID = "gd656PlayerId";
    private static final String TAG_GD656_SHELLING_ID = "gd656ShellingId";
    private static final String TAG_TARGET_X = "TargetX";
    private static final String TAG_TARGET_Y = "TargetY";
    private static final String TAG_TARGET_Z = "TargetZ";

    private static Class<?> gunItemClass;
    private static Method gunDataFromMethod;
    private static Field virtualAmmoField;
    private static Method intValueSetMethod;
    private static boolean reflectionInitialized;

    private SuperbWarfareCompatBridge() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isGunItem(ItemStack stack) {
        if (!isAvailable() || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            ensureReflectionInitialized();
            return gunItemClass != null && gunItemClass.isInstance(stack.getItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean applyReserveAmmo(ItemStack gunStack, int reserveAmmo) {
        if (!isGunItem(gunStack) || reserveAmmo <= 0) {
            return false;
        }
        try {
            ensureReflectionInitialized();
            if (gunDataFromMethod == null || virtualAmmoField == null || intValueSetMethod == null) {
                return false;
            }
            Object gunData = gunDataFromMethod.invoke(null, gunStack);
            if (gunData == null) {
                return false;
            }
            Object virtualAmmoValue = virtualAmmoField.get(gunData);
            if (virtualAmmoValue == null) {
                return false;
            }
            intValueSetMethod.invoke(virtualAmmoValue, reserveAmmo);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int getReserveAmmo(ItemStack gunStack) {
        if (!isGunItem(gunStack)) {
            return 0;
        }
        try {
            ensureReflectionInitialized();
            if (gunDataFromMethod == null || virtualAmmoField == null) {
                return 0;
            }
            Object gunData = gunDataFromMethod.invoke(null, gunStack);
            if (gunData == null) {
                return 0;
            }
            Object virtualAmmoValue = virtualAmmoField.get(gunData);
            if (virtualAmmoValue == null) {
                return 0;
            }
            Method getMethod = virtualAmmoValue.getClass().getMethod("get");
            Object value = getMethod.invoke(virtualAmmoValue);
            return value instanceof Integer reserveAmmo ? Math.max(0, reserveAmmo) : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean isArtilleryIndicator(ItemStack stack) {
        if (!isAvailable() || stack == null || stack.isEmpty()) {
            return false;
        }
        Item indicatorItem = ForgeRegistries.ITEMS.getValue(ARTILLERY_INDICATOR_ID);
        return indicatorItem != null && indicatorItem != Items.AIR && stack.is(indicatorItem);
    }

    public static ItemStack createManagedArtilleryIndicator(String roomId, UUID playerId, String shellingId) {
        if (!isAvailable()) {
            return ItemStack.EMPTY;
        }
        Item indicatorItem = ForgeRegistries.ITEMS.getValue(ARTILLERY_INDICATOR_ID);
        if (indicatorItem == null || indicatorItem == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(indicatorItem);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_GD656_SHELLING_REQUEST, true);
        tag.putString(TAG_GD656_ROOM_ID, roomId == null ? "" : roomId);
        tag.putString(TAG_GD656_PLAYER_ID, playerId == null ? "" : playerId.toString());
        tag.putString(TAG_GD656_SHELLING_ID, shellingId == null ? "" : shellingId);
        return stack;
    }

    public static boolean isManagedArtilleryIndicator(ItemStack stack) {
        if (!isArtilleryIndicator(stack)) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_GD656_SHELLING_REQUEST);
    }

    public static String getManagedShellingRoomId(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(TAG_GD656_ROOM_ID);
    }

    public static String getManagedShellingId(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(TAG_GD656_SHELLING_ID);
    }

    public static UUID getManagedShellingPlayerId(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        if (tag == null) {
            return null;
        }
        try {
            String raw = tag.getString(TAG_GD656_PLAYER_ID);
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean hasManagedTarget(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag != null && tag.contains(TAG_TARGET_X) && tag.contains(TAG_TARGET_Y) && tag.contains(TAG_TARGET_Z);
    }

    public static BlockPos getManagedTargetPos(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        if (tag == null || !hasManagedTarget(stack)) {
            return null;
        }
        return new BlockPos(tag.getInt(TAG_TARGET_X), tag.getInt(TAG_TARGET_Y), tag.getInt(TAG_TARGET_Z));
    }

    private static synchronized void ensureReflectionInitialized() throws Exception {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        gunItemClass = Class.forName(GUN_ITEM_CLASS_NAME);
        Class<?> gunDataClass = Class.forName(GUN_DATA_CLASS_NAME);
        gunDataFromMethod = gunDataClass.getMethod("from", ItemStack.class);
        virtualAmmoField = gunDataClass.getField("virtualAmmo");
        Class<?> intValueClass = virtualAmmoField.getType();
        intValueSetMethod = intValueClass.getMethod("set", int.class);
    }
}
