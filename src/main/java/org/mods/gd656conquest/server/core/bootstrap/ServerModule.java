package org.mods.gd656conquest.server.core.bootstrap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.ChunkEvent;

public interface ServerModule {
    default void registerCommands(RegisterCommandsEvent event) {
    }

    default void onServerStarting(MinecraftServer server) {
    }

    default void onServerTick(MinecraftServer server, long gameTick) {
    }

    default void onServerStopped(MinecraftServer server) {
    }

    default void onPlayerRespawn(ServerPlayer player) {
    }

    default void onPlayerLogout(ServerPlayer player) {
    }

    default void onPlayerLogin(ServerPlayer player) {
    }

    default void onPlayerChangedDimension(ServerPlayer player) {
    }

    default void onPlayerTick(ServerPlayer player) {
    }

    default void onPlayerItemToss(ItemTossEvent event) {
    }

    default void onEntityItemPickup(EntityItemPickupEvent event) {
    }

    default void onChunkLoad(ChunkEvent.Load event) {
    }

    default void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    }

    default void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
    }

    default void onLivingHurt(LivingHurtEvent event) {
    }

    default void onLivingAttack(LivingAttackEvent event) {
    }

    default void onLivingDeath(LivingDeathEvent event) {
    }

    default void onPlayerXpPickup(PlayerXpEvent.PickupXp event) {
    }

    default void onPlayerXpChange(PlayerXpEvent.XpChange event) {
    }
}
