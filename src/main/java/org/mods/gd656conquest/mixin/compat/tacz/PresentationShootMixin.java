package org.mods.gd656conquest.mixin.compat.tacz;

import com.tacz.guns.api.entity.ShootResult;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stop server gun effects before TACZ consumes ammunition or creates bullets. */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class PresentationShootMixin {
    @Shadow @Final private LivingEntity shooter;

    @Inject(method = "shoot", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656$blockShot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp,
        CallbackInfoReturnable<ShootResult> cir) {
        if (shooter instanceof ServerPlayer player && ServerPlayerLockService.of(player.server).shouldBlockGameplayInteraction(player))
            cir.setReturnValue(ShootResult.FORGE_EVENT_CANCEL);
    }
}
