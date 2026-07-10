package org.mods.gd656conquest.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomDamageSourceResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$blockFriendlyManagedVehicleDamage(
        DamageSource source,
        float amount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Entity entity = (Entity) (Object) this;
        if (entity == null || source == null || amount <= 0.0F || entity.level().isClientSide()) {
            return;
        }
        if (!VehicleCompatFacade.isManagedVehicleEntity(entity)) {
            return;
        }
        ServerPlayer attacker = RoomDamageSourceResolver.resolveAttackingPlayer(source, entity);
        if (attacker == null || attacker.server == null) {
            return;
        }
        if (!RoomCoreRuntimeManager.of(attacker.server).shouldCancelDamageToVehicle(attacker, entity)) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "setSecondsOnFire", at = @At("HEAD"), require = 0)
    private void gd656conquest$trackFireCloudOwner(int seconds, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide() || !(entity instanceof LivingEntity living)) {
            return;
        }
        List<AreaEffectCloud> clouds = living.level().getEntitiesOfClass(
            AreaEffectCloud.class,
            living.getBoundingBox().inflate(0.1D)
        );
        for (AreaEffectCloud cloud : clouds) {
            if (cloud.getOwner() instanceof ServerPlayer owner) {
                String className = cloud.getClass().getName();
                if (className.endsWith("SpEffectCloudEntity") || className.endsWith("EffectCloudGrenadeEntity")) {
                    living.setLastHurtByPlayer(owner);
                    living.setLastHurtByMob(owner);
                    break;
                }
            }
        }
    }
}
