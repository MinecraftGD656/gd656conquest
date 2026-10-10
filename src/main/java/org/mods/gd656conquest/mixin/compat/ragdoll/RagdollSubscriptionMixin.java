package org.mods.gd656conquest.mixin.compat.ragdoll;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSubscriptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.raiiiden.ragdollified.server.ServerRagdollSyncManager",remap=false)
public abstract class RagdollSubscriptionMixin {
    @Redirect(method={"register(Lnet/minecraft/world/entity/LivingEntity;Lcom/raiiiden/ragdollified/network/RagdollSpawnPacket;IZ)V","handleStreamFrame","handlePoseReport","onPlayerTick"},require=4,
        at=@At(value="INVOKE",target="Lcom/raiiiden/ragdollified/server/ServerRagdollSyncManager;isNear(Lnet/minecraft/server/level/ServerPlayer;Lcom/raiiiden/ragdollified/server/ServerRagdollSyncManager$RetainedRagdoll;D)Z"))
    private static boolean gd656conquest$observerRange(ServerPlayer player,@Coerce Object record,double radius) {
        return RagdollSubscriptions.shouldReceive(player,record,radius);
    }
    @Inject(method="sendRetained",at=@At("HEAD"),cancellable=true,require=1)
    private static void gd656conquest$bindBeforePose(ServerPlayer player,@Coerce Object record,int now,CallbackInfo ci) {
        if(!RagdollSubscriptions.shouldReceive(player,record,0))ci.cancel();
        else RagdollSubscriptions.beforeRetained(player,record);
    }
    @Inject(method="invalidateSettledPose(Lnet/minecraft/resources/ResourceKey;II)V",at=@At("RETURN"),require=1)
    private static void gd656conquest$wakeDiscovery(ResourceKey<Level> dimension,int id,int revision,CallbackInfo ci) {
        RagdollSubscriptions.wake(id);
    }
}
