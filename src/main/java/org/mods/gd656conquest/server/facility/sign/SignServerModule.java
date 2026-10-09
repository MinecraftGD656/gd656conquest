package org.mods.gd656conquest.server.facility.sign;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class SignServerModule implements ServerModule {
    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        SignPlaceholderService.of(server).onServerTick(gameTick);
    }

    @Override
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            SignPlaceholderService.of(serverLevel.getServer()).onChunkLoad(event);
        }
    }

    @Override
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            SignPlaceholderService.of(serverLevel.getServer()).onRightClickBlock(event);
        }
    }

    @Override
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            SignPlaceholderService.of(serverLevel.getServer()).onLeftClickBlock(event);
        }
    }
}
