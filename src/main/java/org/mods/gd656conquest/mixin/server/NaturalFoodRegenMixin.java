package org.mods.gd656conquest.mixin.server;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.support.SupportRuntime;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(FoodData.class)
public abstract class NaturalFoodRegenMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;isHurt()Z"),require=2)
    private boolean gd656$canHeal(Player p){return p.isHurt() && (!(p instanceof ServerPlayer sp) || !SupportRuntime.pausePassive(sp));}
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),require=1)
    private boolean gd656$roomFoodRule(net.minecraft.world.level.GameRules rules,net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> key,Player p) {
        boolean original=rules.getBoolean(key);
        return p instanceof ServerPlayer sp?org.mods.gd656conquest.server.player.regen.NaturalRegenService.vanillaAllowed(sp,original):original;
    }
}
