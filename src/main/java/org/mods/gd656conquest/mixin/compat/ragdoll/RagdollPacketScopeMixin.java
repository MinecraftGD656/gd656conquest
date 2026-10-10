package org.mods.gd656conquest.mixin.compat.ragdoll;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollPacketScope;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSyncScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {
    "com.raiiiden.ragdollified.network.RagdollSpawnPacket",
    "com.raiiiden.ragdollified.network.RagdollStreamPacket",
    "com.raiiiden.ragdollified.network.RagdollStatePacket",
    "com.raiiiden.ragdollified.network.RagdollStreamOwnerPacket",
    "com.raiiiden.ragdollified.network.RagdollImpulsePacket",
    "com.raiiiden.ragdollified.network.RagdollSeverPacket"
}, remap = false)
public abstract class RagdollPacketScopeMixin implements RagdollPacketScope {
    @Unique private long gd656conquest$generation;
    public long gd656conquest$generation() { return gd656conquest$generation; }
    public void gd656conquest$generation(long generation) { gd656conquest$generation = generation; }

    @Inject(method = "<init>*", at = @At("RETURN"), require = 1)
    private void gd656conquest$captureScope(CallbackInfo ci) {
        gd656conquest$generation = RagdollSyncScope.captureGeneration(RagdollSyncScope.packetId(this));
    }

    @Inject(method = "encode", at = @At("RETURN"), require = 1)
    private static void gd656conquest$encodeScope(@Coerce Object packet, FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeLong(((RagdollPacketScope)packet).gd656conquest$generation());
    }

    @Inject(method = "decode", at = @At("RETURN"), require = 1)
    private static void gd656conquest$decodeScope(FriendlyByteBuf buffer, CallbackInfoReturnable<Object> cir) {
        ((RagdollPacketScope)cir.getReturnValue()).gd656conquest$generation(buffer.readableBytes() >= 8 ? buffer.readLong() : 0);
    }

    @Redirect(method = "handle", require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/NetworkEvent$Context;enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<Void> gd656conquest$guardQueuedPacket(NetworkEvent.Context context, Runnable task,
            @Coerce Object packet, Supplier<NetworkEvent.Context> supplier) {
        long generation = ((RagdollPacketScope)packet).gd656conquest$generation();
        boolean serverbound = context.getDirection() == NetworkDirection.PLAY_TO_SERVER;
        return context.enqueueWork(() -> RagdollSyncScope.receivePacket(packet, generation, serverbound, task));
    }
}
