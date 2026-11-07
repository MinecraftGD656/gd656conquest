package org.mods.gd656conquest.client.loadout.preview;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Resolves the optional TACZ model API while keeping TACZ off the compile classpath. */
final class TaczPreviewAccess {
    private static boolean reportedFailure;

    private TaczPreviewAccess() {}

    static boolean hasModel(ItemStack stack) {
        Access access = ModelApi.ACCESS;
        if (access == null || stack.isEmpty()) return false;
        try {
            if (access.getGun().invoke(null, stack) == null) return false;
            Optional<?> display = (Optional<?>)access.getDisplay().invoke(null, stack);
            return display.isPresent() && access.getModel().invoke(display.get()) != null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            if (!reportedFailure) {
                reportedFailure = true;
                LogUtils.getLogger().warn("TACZ snapshot preview uses the item icon because its model API is unavailable", failure);
            }
            return false;
        }
    }

    /** Mark this draw as GUI before TACZ selects its high-poly/LOD model. */
    static void beforeGuiRender() {
        Method marker = GuiRenderApi.MARKER;
        if (marker == null) return;
        try {
            marker.invoke(null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            if (!reportedFailure) {
                reportedFailure = true;
                LogUtils.getLogger().warn("TACZ snapshot preview could not mark its GUI render", failure);
            }
        }
    }

    private static Method resolveGuiMarker() {
        try {
            return Class.forName("com.tacz.guns.util.RenderDistance").getMethod("markGuiRenderTimestamp");
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            LogUtils.getLogger().warn("TACZ snapshot preview GUI marker API could not be resolved", failure);
            return null;
        }
    }

    private static final class GuiRenderApi {
        private static final Method MARKER = resolveGuiMarker();
    }

    private static final class ModelApi {
        private static final Access ACCESS = resolve();
    }

    private static Access resolve() {
        try {
            Class<?> gun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Class<?> display = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance");
            return new Access(gun.getMethod("getIGunOrNull", ItemStack.class),
                api.getMethod("getGunDisplay", ItemStack.class), display.getMethod("getGunModel"));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            LogUtils.getLogger().warn("TACZ snapshot preview model API could not be resolved; using item icons", failure);
            return null;
        }
    }

    private record Access(Method getGun, Method getDisplay, Method getModel) {}
}
