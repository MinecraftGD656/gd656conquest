package org.mods.gd656conquest.mixin.client.compat.ragdoll;

import java.util.Map;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollHitCacheGuard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.RagdollHitTracker", remap = false)
public abstract class ClientRagdollHitCacheMixin {
    @Shadow @Final private static Map<Integer, Object> HIT_INFO;

    @Inject(method = "onTaczHurt", at = @At("RETURN"), require = 1)
    private static void gd656conquest$bindGunHit(Event event, CallbackInfo ci) {
        RagdollHitCacheGuard.afterGunHit(event, HIT_INFO, true);
    }

    @Redirect(method = "resolveAtDeath", require = 1,
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object gd656conquest$consumeHit(Map<Integer, ?> cache, Object key,
            int id, DamageSource source, LivingEntity victim) {
        return RagdollHitCacheGuard.consumeClient(cache, id, source, victim);
    }

    @Inject(method = "clear", at = @At("RETURN"), require = 1)
    private static void gd656conquest$clearMetadata(CallbackInfo ci) {
        RagdollHitCacheGuard.clearClientMetadata();
    }
}
