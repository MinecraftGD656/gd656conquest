package org.mods.gd656conquest.mixin.compat.tacz;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityReload", remap = false)
public abstract class PresentationReloadMixin {
    @Shadow @Final private LivingEntity shooter;

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656$blockReload(CallbackInfo ci) {
        if (shooter instanceof ServerPlayer player && ServerPlayerLockService.of(player.server).shouldBlockGameplayInteraction(player)) ci.cancel();
    }
}
