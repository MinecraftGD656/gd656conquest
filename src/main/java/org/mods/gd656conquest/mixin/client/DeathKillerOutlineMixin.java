package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.client.room.ClientDeathFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LevelRenderer.class)
public abstract class DeathKillerOutlineMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z"), require = 1)
    private boolean gd656conquest$localKillerGlow(Minecraft mc, Entity entity) {
        return ClientDeathFilter.isKiller(entity) || mc.shouldEntityAppearGlowing(entity);
    }
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"), require = 1)
    private int gd656conquest$localKillerColor(Entity entity) {
        return ClientDeathFilter.isKiller(entity) ? ClientDeathFilter.KILLER_RED : entity.getTeamColor();
    }
}
