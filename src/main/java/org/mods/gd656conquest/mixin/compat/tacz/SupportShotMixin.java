package org.mods.gd656conquest.mixin.compat.tacz;
import org.mods.gd656conquest.server.support.TaczSuppression;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="com.tacz.guns.item.ModernKineticGunScriptAPI",remap=false)
public abstract class SupportShotMixin {
    @Inject(method="shootOnce(Z)V",at=@At("HEAD"),require=1) private void gd656$begin(boolean tracer,CallbackInfo ci){TaczSuppression.beginShot();}
    @Inject(method="shootOnce(Z)V",at=@At("RETURN"),require=1) private void gd656$end(boolean tracer,CallbackInfo ci){TaczSuppression.endShot();}
}
