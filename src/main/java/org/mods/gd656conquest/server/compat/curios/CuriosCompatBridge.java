package org.mods.gd656conquest.server.compat.curios;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

public final class CuriosCompatBridge {
    private static final boolean LOADED = ModList.get().isLoaded("curios");
    private static boolean initialized = false;
    private static Method getCuriosHelper;
    private static Method getCuriosHandler;
    private static Method getStacksHandler;
    private static Method getStacks;
    private static Method setEquippedCurio;

    private CuriosCompatBridge() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    private static void initialize() {
        if (initialized || !LOADED) {
            return;
        }
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            getCuriosHelper = curiosApiClass.getMethod("getCuriosHelper");

            Class<?> curiosHelperClass = Class.forName("top.theillusivec4.curios.api.type.util.ICuriosHelper");
            getCuriosHandler = curiosHelperClass.getMethod("getCuriosHandler", net.minecraft.world.entity.LivingEntity.class);
            setEquippedCurio = curiosHelperClass.getMethod(
                "setEquippedCurio",
                net.minecraft.world.entity.LivingEntity.class,
                String.class,
                int.class,
                ItemStack.class
            );

            Class<?> curiosItemHandlerClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            getStacksHandler = curiosItemHandlerClass.getMethod("getStacksHandler", String.class);

            Class<?> slotResultClass = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            getStacks = slotResultClass.getMethod("getStacks");
        } catch (Throwable ignored) {
        }
        initialized = true;
    }

    public static boolean setCurio(ServerPlayer player, String identifier, int index, ItemStack stack) {
        if (!LOADED || player == null || identifier == null || identifier.isBlank() || stack == null) {
            return false;
        }
        initialize();
        if (getCuriosHelper == null || getCuriosHandler == null || getStacksHandler == null || getStacks == null || setEquippedCurio == null) {
            return false;
        }
        try {
            Object helper = getCuriosHelper.invoke(null);
            Object handlerLazyOptional = getCuriosHandler.invoke(helper, player);
            Method isPresent = handlerLazyOptional.getClass().getMethod("isPresent");
            if (!(Boolean) isPresent.invoke(handlerLazyOptional)) {
                return false;
            }

            Method resolve = handlerLazyOptional.getClass().getMethod("resolve");
            Object handlerOptional = resolve.invoke(handlerLazyOptional);
            if (!(handlerOptional instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            Object handler = optional.get();

            Object stacksHandlerOptional = getStacksHandler.invoke(handler, identifier);
            if (!(stacksHandlerOptional instanceof Optional<?> optStacksHandler) || optStacksHandler.isEmpty()) {
                return false;
            }
            Object stacksHandler = optStacksHandler.get();
            Object stacks = getStacks.invoke(stacksHandler);
            Method getSlots = stacks.getClass().getMethod("getSlots");
            int slots = (Integer) getSlots.invoke(stacks);
            if (index < 0 || index >= slots) {
                return false;
            }

            setEquippedCurio.invoke(helper, player, identifier, index, stack.copy());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
