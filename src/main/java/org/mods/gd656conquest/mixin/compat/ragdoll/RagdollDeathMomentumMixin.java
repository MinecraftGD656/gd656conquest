package org.mods.gd656conquest.mixin.compat.ragdoll;

import com.raiiiden.ragdollified.ServerRagdollHitTracker;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollHitCacheGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.PhysicsHooks", remap = false)
public abstract class RagdollDeathMomentumMixin {
    @Redirect(method = "onLivingDeath", require = 1,
        at = @At(value = "INVOKE", target = "Lcom/raiiiden/ragdollified/ServerRagdollHitTracker;consume(I)Lcom/raiiiden/ragdollified/ServerRagdollHitTracker$HitInfo;"))
    private static ServerRagdollHitTracker.HitInfo gd656conquest$consumeCurrentHit(int id, LivingDeathEvent event) {
        return (ServerRagdollHitTracker.HitInfo)RagdollHitCacheGuard.forServerDeath(
            event.getEntity(), event.getSource(), ServerRagdollHitTracker.consume(id));
    }
}
