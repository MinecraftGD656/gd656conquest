package org.mods.gd656conquest.mixin.compat.tacz;
import com.tacz.guns.entity.EntityKineticBullet;
import org.mods.gd656conquest.server.support.TaczSuppression;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public abstract class SupportBulletMixin {
    @Inject(method="<init>",at=@At("RETURN"),require=1) private void gd656$tag(CallbackInfo ci){TaczSuppression.tag((EntityKineticBullet)(Object)this);}
}
