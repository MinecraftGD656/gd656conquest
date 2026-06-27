package org.mods.gd656conquest.server.compat.superbwarfare;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SuperbWarfareCompatBridge {
    private static final String MOD_ID = "superbwarfare";
    private static final String GUN_ITEM_CLASS_NAME = "com.atsuishio.superbwarfare.item.gun.GunItem";
    private static final String GUN_DATA_CLASS_NAME = "com.atsuishio.superbwarfare.data.gun.GunData";

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
