package org.mods.gd656conquest.mixin.server;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.mods.gd656conquest.server.support.SupportDropGuard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SupportDropMixin {
    @Shadow public ServerPlayer player;
    @Redirect(method="handlePlayerAction",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;drop(Z)Z"),require=2)
    private boolean gd656$q(ServerPlayer p,boolean all){return SupportDropGuard.drop(p,all);}
    @Inject(method="handleContainerClick",at=@At("HEAD"),cancellable=true,require=1)
    private void gd656$ui(ServerboundContainerClickPacket packet,CallbackInfo ci){
        if(player.server.isSameThread() && SupportDropGuard.blockClick(player,packet)) {
            player.containerMenu.broadcastFullState();ci.cancel();
        }
    }
}
