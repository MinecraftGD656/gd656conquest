package org.mods.gd656conquest.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientFovScrollService {
    private static double targetFov = -1;
    private static double currentFov = -1;
    private static double prevFov = -1;
    private static double step = 2.5;
    // GD656Mods: 临时观测，测完删除
    private static double lastAppliedFov = -1;

    private ClientFovScrollService() {
    }

    public static boolean handleKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return false;
        }

        if (event.getAction() != GLFW.GLFW_PRESS) {
            return false;
        }

        if (!isControlBindingDown(minecraft)) {
            return false;
        }

        int key = event.getKey();
        if (key == GLFW.GLFW_KEY_UP) {
            step += 1.0;
            displayStepMessage(minecraft);
            return true;
        } else if (key == GLFW.GLFW_KEY_DOWN) {
            step -= 1.0;
            if (step < 0.5) {
                step = 0.5;
            }
            displayStepMessage(minecraft);
            return true;
        }

        return false;
    }

    private static void displayStepMessage(Minecraft minecraft) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("FOV 缩放步长: " + String.format("%.1f", step)), true);
        }
    }

    public static boolean handleMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return false;
        }

        if (!isControlBindingDown(minecraft)) {
            return false;
        }

        double scrollDelta = event.getScrollDelta();
        if (scrollDelta == 0) {
            return false;
        }

        int optionFov = minecraft.options.fov().get();
        if (targetFov == -1 || Math.abs(targetFov - optionFov) > 2.0) {
            targetFov = optionFov;
            currentFov = optionFov;
            prevFov = optionFov;
        }

        targetFov -= Math.signum(scrollDelta) * step;
        targetFov = Mth.clamp(targetFov, 30.0, 110.0);

        // 同步原版选项以供菜单显示（取整），但不依赖它进行渲染
        minecraft.options.fov().set((int) Math.round(targetFov));

        event.setCanceled(true);
        return true;
    }

    public static void onClientTick() {
        if (targetFov == -1) {
            return;
        }
        prevFov = currentFov;
        // 平滑插值
        currentFov += (targetFov - currentFov) * 0.35;
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (targetFov == -1) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int optionFov = minecraft.options.fov().get();
        if (Math.abs(targetFov - optionFov) > 2.0) {
            // 如果玩家手动在设置中调整了FOV滑块，则重置目标FOV
            targetFov = optionFov;
            currentFov = optionFov;
            prevFov = optionFov;
            return;
        }

        double partialTick = event.getPartialTick();
        double interpolatedFov = Mth.lerp(partialTick, prevFov, currentFov);
        // GD656Mods: 临时观测，测完删除
        lastAppliedFov = interpolatedFov;

        // event.getFOV() 已经包含了药水、疾跑等修饰符：(原版基础FOV * modifier)
        // 我们通过除以原版基础FOV得到修饰符，然后乘上我们精确的小数FOV
        double modifier = event.getFOV() / (double) optionFov;
        event.setFOV(interpolatedFov * modifier);
    }

    // GD656Mods: 临时观测，测完删除
    public static double getLastAppliedFov() {
        return lastAppliedFov;
    }

    private static boolean isControlBindingDown(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        KeyMapping keyMapping = ClientKeyMappings.FOV_SCROLL_CONTROL;
        InputConstants.Key key = keyMapping.getKey();
        if (key == null || key == InputConstants.UNKNOWN) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM, SCANCODE -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        };
    }
}
