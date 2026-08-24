package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.InputEvent;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;

/**
 * GD656Mods: 载具内"物品栏选中槽位"锁定。
 * 核心语义: 绝不拦截滚轮事件本身(滚轮交给模组/原版, 如 sbw 滚轮切武器、倍镜等),
 * 只锁定玩家选中的物品栏槽位(selected 不能改变): 每帧把 selected 拉回锁定槽位。
 * 判定复用各模组权威机制: sbw 用 banHand(player)(座位有武器或 seat.banHand 配置),
 * ywzj 用驾驶员/有可操作武器座位。无武器座位(如坦克车长/三号位、直升机副驾驶)不锁定。
 */
final class ClientVehicleMountedHotbarLockService {
    static final int LOCKED_HOTBAR_SLOT = 6;
    static final int POST_DISMOUNT_HOTBAR_SLOT = 0;
    private boolean wasMountedVehicle;

    void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            wasMountedVehicle = false;
            return;
        }
        boolean lockActive = shouldLockForSeat(minecraft);
        if (lockActive && !wasMountedVehicle) {
            minecraft.player.getInventory().selected = LOCKED_HOTBAR_SLOT;
        }
        if (!lockActive && wasMountedVehicle) {
            minecraft.player.getInventory().selected = POST_DISMOUNT_HOTBAR_SLOT;
        }
        wasMountedVehicle = lockActive;
    }

    /**
     * 该座位需要锁定时返回锁定槽位(用于每帧把 selected 拉回), 否则 -1。
     * 滚轮/数字键事件本身均不拦截, 由本方法 + 调用方的 enforceLockedHotbarSelection 保证选中槽位不变。
     */
    int resolveLockedHotbarSlot(Minecraft minecraft) {
        return shouldLockForSeat(minecraft) ? LOCKED_HOTBAR_SLOT : -1;
    }

    /** GD656Mods: 数字键不再由本服务拦截 —— sbw 在 banHand 座位自行吞掉数字键点击并用于切武器/座位; 非锁定座位直接放行。 */
    boolean shouldCancelHotbarKeyInput(Minecraft minecraft, InputEvent.Key event) {
        return false;
    }

    /** GD656Mods: 滚轮事件绝不拦截(滚轮永不禁用) —— 交由模组/原版处理, 选中槽位由拉回机制锁定。 */
    boolean shouldCancelMouseScroll(Minecraft minecraft) {
        return false;
    }

    void reset() {
        wasMountedVehicle = false;
    }

    private boolean shouldLockForSeat(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return false;
        }
        Entity vehicle = minecraft.player.getVehicle();
        Entity passenger = minecraft.player;
        if (!VehicleCompatFacade.isVehicleEntity(vehicle) || passenger == null) {
            return false;
        }
        return VehicleCompatFacade.shouldLockHotbarForSeat(vehicle, passenger);
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
