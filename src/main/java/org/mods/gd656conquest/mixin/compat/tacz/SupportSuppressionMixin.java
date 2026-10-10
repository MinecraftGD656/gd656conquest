package org.mods.gd656conquest.mixin.compat.tacz;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.server.support.TaczSuppression;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="com.tacz.guns.util.EntityUtil",remap=false)
public abstract class SupportSuppressionMixin {
    @Inject(method="findEntityOnPath",at=@At("RETURN"),require=1)
    private static void gd656$single(Projectile bullet,Vec3 start,Vec3 end,CallbackInfoReturnable<EntityKineticBullet.EntityResult> ci){
        var hit=ci.getReturnValue();TaczSuppression.path(bullet,start,hit==null?end:hit.getHitPos());
    }
    @Inject(method="findEntitiesOnPath",at=@At("RETURN"),require=1)
    private static void gd656$multiple(Projectile bullet,Vec3 start,Vec3 end,CallbackInfoReturnable<java.util.List<EntityKineticBullet.EntityResult>> ci){
        // EntityKineticBullet consumes at most its configured pierce count; cap the observed path likewise.
        if(!(bullet instanceof EntityKineticBullet))return;
        int pierce=Math.max(1,((KineticBulletSupportAccessor)bullet).gd656$pierce());
        var hits=ci.getReturnValue();
        if(hits!=null && hits.size()>=pierce)end=hits.stream().sorted(java.util.Comparator.comparingDouble(h->h.getHitPos().distanceToSqr(start))).toList().get(pierce-1).getHitPos();
        TaczSuppression.path(bullet,start,end);
    }
}
