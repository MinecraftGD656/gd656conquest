package org.mods.gd656conquest.mixin.server;

import java.util.List;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potions;
import org.mods.gd656conquest.server.compat.tacz.ShellingPresentationProtection;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AreaEffectCloud.class)
public abstract class ShellingCloudPresentationMixin {
    @Shadow @Final private List<MobEffectInstance> effects;
    @Inject(method="tick",at=@At("HEAD"),require=1)
    private void gd656conquest$removeDamagingEffects(CallbackInfo ci) {
        AreaEffectCloud cloud=(AreaEffectCloud)(Object)this;
        if(!cloud.level().isClientSide()&&ShellingPresentationProtection.harmless(cloud)){effects.clear();cloud.setPotion(Potions.EMPTY);}
    }
}
