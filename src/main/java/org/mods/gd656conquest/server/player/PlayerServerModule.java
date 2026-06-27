package org.mods.gd656conquest.server.player;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.mods.gd656conquest.server.player.regen.PassiveRegenService;
import org.mods.gd656conquest.server.player.state.FireExtinguishService;
import org.mods.gd656conquest.server.player.state.HubDimensionService;
import org.mods.gd656conquest.server.player.state.PlayerItemInteractionService;
import org.mods.gd656conquest.server.player.state.PlayerSessionStateService;
import org.mods.gd656conquest.server.player.state.StandalonePlayerStateService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.RoomLobbyTransferService;

public final class PlayerServerModule implements ServerModule {
    @Override
    public void onServerStarting(MinecraftServer server) {
        if (server == null) {
            return;
        }
        applyGlobalVanillaRules(server);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        PassiveRegenService.clearAll();
        FireExtinguishService.clearAll();
        ServerPlayerLockService.clearAll(server);
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        var config = ConquestDataManager.of(player.server).serverConfig();
        RoomCoreRuntimeManager roomRuntimeManager = RoomCoreRuntimeManager.of(player.server);
        if (RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).isEmpty() && player.tickCount % 10 == 0) {
            StandalonePlayerStateService.enforceStandaloneWorldPolicy(player);
        }
        if (player.tickCount % 200 == 0) {
            PlayerSessionStateService.maintainLobbyHealth(player);
        }
        if (!roomRuntimeManager.shouldBlockPassiveRegen(player)) {
            PassiveRegenService.onPlayerTick(
                player,
                player.server.getTickCount(),
                config.getPassiveRegenStartDelaySeconds(),
                config.getPassiveRegenIntervalTicks()
            );
        }
        FireExtinguishService.onPlayerTick(player, player.server.getTickCount());
        if (player.tickCount % 20 == 0) {
            PlayerLevelService.clampPlayerLevel(player);
        }
        ServerPlayerLockService.of(player.server).onPlayerTick(player);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        PassiveRegenService.resetPlayer(player);
        PlayerLevelService.clampPlayerLevel(player);
        forcePlayerIntoHubOnLogin(player);
        PlayerSessionStateService.onPlayerLogin(player);
        StandalonePlayerStateService.enforceStandaloneWorldPolicy(player);
        ServerPlayerLockService.of(player.server).syncPlayer(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        PassiveRegenService.resetPlayer(player);
        FireExtinguishService.onPlayerLogout(player);
        if (player != null && player.server != null) {
            ServerPlayerLockService.of(player.server).onPlayerLogout(player);
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayer player) {
        PassiveRegenService.resetPlayer(player);
        PlayerSessionStateService.onStandaloneTransition(player);
        StandalonePlayerStateService.enforceStandaloneWorldPolicy(player);
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        PlayerSessionStateService.onStandaloneTransition(player);
        StandalonePlayerStateService.enforceStandaloneWorldPolicy(player);
    }

    @Override
    public void onPlayerItemToss(ItemTossEvent event) {
        PlayerItemInteractionService.handlePlayerItemToss(event);
    }

    @Override
    public void onEntityItemPickup(EntityItemPickupEvent event) {
        PlayerItemInteractionService.handleEntityItemPickup(event);
    }

    @Override
    public void onLivingHurt(LivingHurtEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (shouldCancelIncomingDamage(player)) {
            event.setCanceled(true);
            return;
        }
        if (event.getAmount() > 0.0F && player.server != null) {
            PassiveRegenService.markPlayerDamaged(player, player.server.getTickCount());
        }
    }

    @Override
    public void onLivingAttack(LivingAttackEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (shouldCancelIncomingDamage(player)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onPlayerXpPickup(PlayerXpEvent.PickupXp event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!PlayerLevelService.shouldBlockVanillaExperience(player)) {
            return;
        }
        if (event.getOrb() != null) {
            event.getOrb().discard();
        }
        event.setCanceled(true);
    }

    @Override
    public void onPlayerXpChange(PlayerXpEvent.XpChange event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAmount() <= 0) {
            return;
        }
        if (!PlayerLevelService.shouldBlockVanillaExperience(player)) {
            return;
        }
        event.setAmount(0);
    }

    private boolean shouldCancelIncomingDamage(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        return roomManager.shouldCancelDamage(player) || StandalonePlayerStateService.shouldCancelStandaloneDamage(player);
    }

    private void forcePlayerIntoHubOnLogin(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(player.server);
        if (rangeManager.isInRange(player.getUUID())) {
            rangeManager.leaveRangeForTransfer(player);
        }
        roomManager.leaveMatching(player.getUUID());
        if (roomManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            roomManager.sendPlayerToHub(player);
        } else {
            new RoomLobbyTransferService(player.server).transferToLobby(player);
        }
        normalizeHubLoginGameMode(player);
    }

    private void normalizeHubLoginGameMode(ServerPlayer player) {
        if (player == null || player.server == null || !HubDimensionService.isHubDimension(player.level())) {
            return;
        }
        if (!player.hasPermissions(2)
            && player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.ADVENTURE) {
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        }
    }

    private void applyGlobalVanillaRules(MinecraftServer server) {
        boolean naturalRegenEnabled = ConquestDataManager.of(server).serverConfig().isNaturalRegenerationEnabled();
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(naturalRegenEnabled, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(false, server);
        }
    }
}
