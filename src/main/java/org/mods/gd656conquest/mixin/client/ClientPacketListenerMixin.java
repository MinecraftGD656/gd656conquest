package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.mods.gd656conquest.client.event.handler.ClientEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void gd656conquest$syncCaptureOutlineSingleBlock(ClientboundBlockUpdatePacket packet, CallbackInfo callbackInfo) {
        ClientEventHandler.getInstance().handleClientBlockStateUpdate(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void gd656conquest$syncCaptureOutlineSectionBlocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo callbackInfo) {
        packet.runUpdates((pos, state) -> ClientEventHandler.getInstance().handleClientBlockStateUpdate(pos.immutable(), state));
    }
}
