package org.mods.gd656conquest.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.mods.gd656conquest.server.room.runtime.RoomDeathEventDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {
    @Redirect(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
        at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDeath(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z"),
        require = 1)
    private boolean gd656conquest$dispatchBeforeDeathTakeover(LivingEntity entity, DamageSource source) {
        return RoomDeathEventDispatcher.dispatchPlayerDeath(entity, source);
    }
}
