package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareVehicleCompatBridge;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;

final class ClientVehicleMountedHotbarLockService {
    static final int LOCKED_HOTBAR_SLOT = 6;
    static final int POST_DISMOUNT_HOTBAR_SLOT = 0;
    private boolean wasMountedVehicle;

    void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            wasMountedVehicle = false;
            return;
        }
        boolean mountedVehicle = isMountedVehicle(minecraft);
        if (mountedVehicle && !wasMountedVehicle) {
            minecraft.player.getInventory().selected = LOCKED_HOTBAR_SLOT;
        }
        if (!mountedVehicle && wasMountedVehicle) {
            minecraft.player.getInventory().selected = POST_DISMOUNT_HOTBAR_SLOT;
        }
        wasMountedVehicle = mountedVehicle;
    }

    int resolveLockedHotbarSlot(Minecraft minecraft) {
        return shouldLockForVehicle(minecraft) ? LOCKED_HOTBAR_SLOT : -1;
    }

    boolean shouldCancelHotbarKeyInput(Minecraft minecraft, InputEvent.Key event) {
        if (!shouldLockForVehicle(minecraft) || minecraft == null || minecraft.screen != null || event == null) {
            return false;
        }
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return false;
        }
        if (minecraft.options == null) {
            return false;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.options.keyHotbarSlots[slot].matches(event.getKey(), event.getScanCode())) {
                return true;
            }
        }
        return false;
    }

    boolean shouldCancelMouseScroll(Minecraft minecraft) {
        if (!shouldLockForVehicle(minecraft) || minecraft == null || minecraft.screen != null) {
            return false;
        }
        Entity vehicle = minecraft.player == null ? null : minecraft.player.getVehicle();
        return !SuperbWarfareVehicleCompatBridge.isVehicleEntity(vehicle);
    }

    void reset() {
        wasMountedVehicle = false;
    }

    private boolean shouldLockForVehicle(Minecraft minecraft) {
        return minecraft != null
            && minecraft.player != null
            && minecraft.level != null
            && VehicleCompatFacade.shouldLockMountedHotbar(minecraft.player.getVehicle());
    }

    private boolean isMountedVehicle(Minecraft minecraft) {
        return minecraft != null
            && minecraft.player != null
            && minecraft.level != null
            && VehicleCompatFacade.isVehicleEntity(minecraft.player.getVehicle());
    }

    private static int clampHotbarSlot(int slot) {
        if (slot < 0) {
            return 0;
        }
        if (slot > 8) {
            return 8;
        }
        return slot;
    }
}
