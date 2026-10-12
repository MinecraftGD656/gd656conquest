package org.mods.gd656conquest.mixin.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleRuntimeService;
import org.mods.gd656conquest.server.compat.tacz.ShellingPresentationProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class BattalionSceneTickMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true,require=1)
    private void gd656conquest$freezeScene(Entity entity,CallbackInfo ci) {
        if(ShellingPresentationProtection.expired(entity)){entity.discard();ci.cancel();return;}
        if(org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade.isVehicleEntity(entity)&&RoomVehicleRuntimeService.isSceneFrozen(entity)) {
            entity.setDeltaMovement(0,0,0);ci.cancel();
        }
    }
}
