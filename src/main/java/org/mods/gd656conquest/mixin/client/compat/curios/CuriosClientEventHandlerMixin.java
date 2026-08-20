package org.mods.gd656conquest.mixin.client.compat.curios;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.client.ClientEventHandler;
import top.theillusivec4.curios.client.KeyRegistry;

/**
 * 房间禁用背包(disableInventoryChange)时, 客户端吞掉打开 Curios 饰品栏的按键点击(openCurios),
 * 阻止打开饰品栏界面。服务端另有 CPacketOpenCuriosMixin 兜底拒绝。
 * 管理员(权限>=2)绕过全部禁用背包限制, 可正常打开饰品栏。
 */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class CuriosClientEventHandlerMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), remap = false, require = 0)
    private void gd656conquest$consumeOpenCuriosWhenInventoryLocked(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (!ClientRoomLockState.shouldLockInventory()) {
            return;
        }
        if (ClientRoomLockState.shouldBypassForAdmin(Minecraft.getInstance())) {
            return;
        }
        KeyRegistry.openCurios.consumeClick();
    }
}
