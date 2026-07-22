package org.mods.gd656conquest.mixin.client.compat.lrtactical;

import me.xjqsh.lrtactical.client.input.AttackKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.xjqsh.lrtactical.client.input.AttackKeys", remap = false)
public class AttackKeysMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void gd656conquest$cancelStuckAttack(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (mc.screen != null || !mc.mouseHandler.isMouseGrabbed() || player.isDeadOrDying()) {
            while (AttackKeys.NORMAL_ATTACK.consumeClick()) {}
            while (AttackKeys.SPECIAL_ATTACK.consumeClick()) {}
            AttackKeys.NORMAL_ATTACK.setDown(false);
            AttackKeys.SPECIAL_ATTACK.setDown(false);
        }
    }
}
