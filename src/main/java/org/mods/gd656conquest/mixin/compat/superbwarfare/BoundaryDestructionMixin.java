package org.mods.gd656conquest.mixin.compat.superbwarfare;

import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.BoundaryVehicleDestruction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
public abstract class BoundaryDestructionMixin {
    @Inject(method="m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",at=@At("HEAD"),cancellable=true,require=1)
    private void gd656conquest$protectIntermission(net.minecraft.world.damagesource.DamageSource source,float damage,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        Entity vehicle=(Entity)(Object)this;
        if(!vehicle.level().isClientSide()&&(org.mods.gd656conquest.server.room.vehicle.RoomVehicleRuntimeService.isSceneFrozen(vehicle)
            ||org.mods.gd656conquest.server.compat.tacz.ShellingPresentationProtection.blocksDamage(source)))ci.setReturnValue(false);
    }

    @Inject(method = "destroy()V", at = @At("HEAD"), require = 1)
    private void gd656conquest$beginBoundaryExplosion(CallbackInfo ci) {
        BoundaryVehicleDestruction.beginNativeDestruction((Entity)(Object)this);
    }
    @Inject(method = "destroy()V", at = @At("RETURN"), require = 1)
    private void gd656conquest$finishBoundaryExplosion(CallbackInfo ci) {
        BoundaryVehicleDestruction.finishNativeDestruction((Entity)(Object)this);
    }
}
