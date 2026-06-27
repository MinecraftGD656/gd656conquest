package org.mods.gd656conquest.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomDamageSourceResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        ServerPlayer attacker = RoomDamageSourceResolver.resolveAttackingPlayer(source);
        if (attacker == null || attacker.server == null) {
            return;
        }
        if (!RoomCoreRuntimeManager.of(attacker.server).shouldCancelDamageToVehicle(attacker, entity)) {
            return;
        }
        cir.setReturnValue(false);
    }
}
