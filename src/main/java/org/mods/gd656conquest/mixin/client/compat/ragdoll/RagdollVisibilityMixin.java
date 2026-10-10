package org.mods.gd656conquest.mixin.client.compat.ragdoll;

import org.mods.gd656conquest.client.room.ClientRagdollSubscriptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets="com.raiiiden.ragdollified.client.ClientRagdollRenderer",remap=false)
public abstract class RagdollVisibilityMixin {
    @Redirect(method="onRenderLevel",require=1,
        at=@At(value="INVOKE",target="Lcom/raiiiden/ragdollified/client/ClientRagdoll;getSnapshot()Lcom/raiiiden/ragdollified/client/ClientRagdoll$TransformSnapshot;"))
    @Coerce
    private static Object gd656conquest$hideUnobserved(@Coerce Object body) {
        return ClientRagdollSubscriptions.renderSnapshot(body);
    }
    @ModifyVariable(method="onRenderLevel",at=@At("STORE"),ordinal=1,require=1)
    private static double gd656conquest$visibleBeyondNativeDistance(double squaredDistance) {
        return ClientRagdollSubscriptions.hasDirectory()?Double.POSITIVE_INFINITY:squaredDistance;
    }
}
