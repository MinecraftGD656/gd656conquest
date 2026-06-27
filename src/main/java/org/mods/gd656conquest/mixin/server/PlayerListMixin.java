package org.mods.gd656conquest.mixin.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "respawn", at = @At("RETURN"), require = 0)
    private void gd656conquest$respawnToDeathPoint(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer newPlayer = cir.getReturnValue();
        if (newPlayer == null || server == null) {
            return;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
        if (coreManager.findPlayerRoomId(newPlayer.getUUID()).isPresent()) {
            coreManager.applyRespawnPostProcess(newPlayer);
        }
    }

    @Inject(method = "respawn", at = @At("HEAD"), require = 0)
    private void gd656conquest$prepareRespawnAnchor(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfoReturnable<ServerPlayer> cir) {
        if (oldPlayer == null || server == null) {
            return;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
        if (coreManager.findPlayerRoomId(oldPlayer.getUUID()).isPresent()) {
            coreManager.prepareRespawnAnchor(oldPlayer);
        }
    }
}
