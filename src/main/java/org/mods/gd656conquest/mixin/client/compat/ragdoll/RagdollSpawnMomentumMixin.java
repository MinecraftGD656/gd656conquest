package org.mods.gd656conquest.mixin.client.compat.ragdoll;

import com.raiiiden.ragdollified.client.RagdollHitTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollHitCacheGuard;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSyncScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.network.RagdollSpawnPacket", remap = false)
public abstract class RagdollSpawnMomentumMixin {
    @Inject(method = "handleClient", at = @At("HEAD"), require = 1)
    private static void gd656conquest$consumeOldClientHit(@Coerce Object packet, CallbackInfo ci) {
        RagdollHitCacheGuard.clearClient(RagdollSyncScope.packetId(packet));
    }

    /** A server spawn with no hit must not invent one from an unrelated local attack. */
    @Redirect(method = "handleClient", require = 1,
        at = @At(value = "INVOKE", target = "Lcom/raiiiden/ragdollified/client/RagdollHitTracker;resolveAndPlan(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Lcom/raiiiden/ragdollified/client/RagdollHitTracker$ResolvedHit;"))
    private static RagdollHitTracker.ResolvedHit gd656conquest$keepServerMomentum(LivingEntity victim, DamageSource source) {
        return null;
    }
}
