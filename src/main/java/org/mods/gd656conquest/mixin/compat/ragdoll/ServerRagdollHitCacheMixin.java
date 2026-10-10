package org.mods.gd656conquest.mixin.compat.ragdoll;

import java.util.Map;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollHitCacheGuard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.ServerRagdollHitTracker", remap = false)
public abstract class ServerRagdollHitCacheMixin {
    @Shadow @Final private static Map<Integer, Object> HIT_INFO;

    @Inject(method = "onLivingHurt", at = @At("HEAD"), require = 1)
    private static void gd656conquest$beginHit(LivingHurtEvent event, CallbackInfo ci) {
        RagdollHitCacheGuard.beforeServerHurt(event, HIT_INFO);
    }

    @Inject(method = "onLivingHurt", at = @At("RETURN"), require = 1)
    private static void gd656conquest$bindHit(LivingHurtEvent event, CallbackInfo ci) {
        RagdollHitCacheGuard.afterServerHurt(event, HIT_INFO);
    }

    @Inject(method = "onTaczHurtPre", at = @At("RETURN"), require = 1)
    private static void gd656conquest$bindGunHit(Event event, CallbackInfo ci) {
        RagdollHitCacheGuard.afterGunHit(event, HIT_INFO, false);
    }

    @Inject(method = {"consume", "peek"}, at = @At("RETURN"), cancellable = true, require = 1)
    private static void gd656conquest$rejectExpiredHit(int id, CallbackInfoReturnable<Object> cir) {
        if (!RagdollHitCacheGuard.fresh(cir.getReturnValue())) {
            HIT_INFO.remove(id);
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "clear", at = @At("RETURN"), require = 1)
    private static void gd656conquest$clearMetadata(CallbackInfo ci) {
        RagdollHitCacheGuard.clearServerMetadata();
    }
}
