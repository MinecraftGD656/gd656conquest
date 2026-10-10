package org.mods.gd656conquest.mixin.compat.ywzjvehicle;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.BoundaryVehicleDestruction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.ywzj.vehicle.entity.vehicle.AbstractVehicle", remap = false)
public abstract class BoundaryDestructionMixin {
    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
        target = "Lorg/ywzj/vehicle/util/VehicleExplosion;explode(Ljava/util/List;)V"), require = 1)
    private void gd656conquest$beginBoundaryExplosion(DamageSource source, float damage, CallbackInfoReturnable<Boolean> ci) {
        BoundaryVehicleDestruction.beginNativeDestruction((Entity)(Object)this);
    }
    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
        target = "Lorg/ywzj/vehicle/util/VehicleExplosion;explode(Ljava/util/List;)V", shift = At.Shift.AFTER), require = 1)
    private void gd656conquest$finishBoundaryExplosion(DamageSource source, float damage, CallbackInfoReturnable<Boolean> ci) {
        BoundaryVehicleDestruction.finishNativeDestruction((Entity)(Object)this);
    }
}
