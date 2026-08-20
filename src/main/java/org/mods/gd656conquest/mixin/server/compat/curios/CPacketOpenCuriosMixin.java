package org.mods.gd656conquest.mixin.server.compat.curios;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

/**
 * 房间禁用背包(disableInventoryChange)时, 服务端拒绝打开 Curios 饰品栏:
 * 拦截 {@link CPacketOpenCurios#handle} 处理, 即使客户端绕过按键直接发包也无法打开。
 */
@Mixin(value = CPacketOpenCurios.class, remap = false)
public abstract class CPacketOpenCuriosMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void gd656conquest$blockOpenCuriosWhenInventoryLocked(
        CPacketOpenCurios message,
        Supplier<NetworkEvent.Context> contextSupplier,
        CallbackInfo ci
    ) {
        if (message == null || contextSupplier == null) {
            return;
        }
        NetworkEvent.Context context = contextSupplier.get();
        if (context == null) {
            return;
        }
        ServerPlayer sender = context.getSender();
        if (sender == null || sender.getServer() == null) {
            return;
        }
        if (ServerPlayerLockService.of(sender.getServer()).shouldBlockInventoryOperation(sender)) {
            ci.cancel();
            context.setPacketHandled(true);
        }
    }
}
