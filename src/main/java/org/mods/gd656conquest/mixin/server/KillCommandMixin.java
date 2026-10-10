package org.mods.gd656conquest.mixin.server;

import net.minecraft.server.commands.KillCommand;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.room.entry.RoomDeathCauseService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KillCommand.class)
public abstract class KillCommandMixin {
    @Redirect(method = "kill(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;kill()V"), require = 1)
    private static void gd656conquest$identifyCommandDeath(Entity target) {
        RoomDeathCauseService.commandKill(target);
    }
}
