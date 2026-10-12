package org.mods.gd656conquest.mixin.compat.lrtactical;

import net.minecraft.world.entity.AreaEffectCloud;
import org.mods.gd656conquest.server.compat.tacz.ShellingPresentationProtection;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo @Mixin(targets="me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity",remap=false)
public abstract class ShellingFlamePresentationMixin {
    @Shadow private boolean ignite;
    @Inject(method="m_8119_()V",at=@At("HEAD"),cancellable=true,require=1)
    private void gd656conquest$visualFlames(CallbackInfo ci) {
        AreaEffectCloud cloud=(AreaEffectCloud)(Object)this;
        if(cloud.level().isClientSide())return;
        if(ShellingPresentationProtection.expired(cloud)){cloud.discard();ci.cancel();return;}
        if(ShellingPresentationProtection.harmless(cloud)){ignite=false;}
    }
}
