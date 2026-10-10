package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerDimensionStabilityService;
import org.mods.gd656conquest.server.room.runtime.RoomDeathEventDispatcher;
import org.mods.gd656conquest.server.compat.ragdoll.DownedRagdollBridge;

public final class RoomServerModule implements ServerModule {
    @Override
    public void onServerStarting(MinecraftServer server) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(server);
        manager.initialize();
        RoomResetRecoveryService.of(server).recoverPendingResets(server.getTickCount());
        manager.runStartupCleanRoomPass(server.getTickCount());
    }

    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        RoomCoreRuntimeManager.of(server).onServerTick(gameTick);
        RoomDeathCauseService.tick(server);
        DownedRagdollBridge.tick(server);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.tick(server);
        RoomResetRecoveryService.of(server).syncPendingResetsFromRooms(RoomCoreRuntimeManager.of(server).listRooms());
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        RoomResetRecoveryService.of(server).syncPendingResetsFromRooms(RoomCoreRuntimeManager.of(server).listRooms());
        DownedRagdollBridge.clear(server);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.clear(server);
        RoomDeathCauseService.clear(server);
        RoomCoreRuntimeManager.of(server).shutdown();
        RoomResetRecoveryService.forget(server);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        DownedRagdollBridge.stop(player, false);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.end(player);
        RoomDeathCauseService.forget(player);
        RoomCoreRuntimeManager.of(player.server).handlePlayerLogout(player.getUUID(), player.server.getTickCount());
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        DownedRagdollBridge.stop(player, false);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.end(player);
        RoomDeathCauseService.forget(player);
        RoomPlayerDimensionStabilityService.onPlayerChangedDimension(player);
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        RoomPlayerDimensionStabilityService.onPlayerTick(player);
    }

    @Override
    public void onLivingAttack(LivingAttackEvent event) {
        cancelFriendlyPlayerDamage(event);
        cancelFriendlySquadBeaconDamage(event);
    }

    @Override
    public void onLivingHurt(LivingHurtEvent event) {
        cancelFriendlyPlayerDamage(event);
        cancelFriendlySquadBeaconDamage(event);
        markDamagingPlayerAsCombatDeployBlocked(event);
    }

    @Override
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null) {
            return;
        }
        if (RoomDeathEventDispatcher.deferRoomDeath(this, event)) {
            return;
        }
        if (isTacticalGadgetEntity(event.getEntity())) {
            net.minecraft.world.entity.Entity attacker = event.getSource() == null ? null : event.getSource().getEntity();
            if (attacker instanceof ServerPlayer player) {
                KilliconScoreboardBridge.tryAwardConquestBonus(player, "TACTICAL_GADGET_DESTROYED", 1.0F, "");
            }
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        ServerPlayer killer = RoomDeathCauseService.resolveKiller(event.getSource(), event.getEntity());
        RoomCoreRuntimeManager.of(player.server).handleMarkedPlayerDeath(player, killer, player.server.getTickCount());
        if (killer != null && killer != player) {
            RoomCoreRuntimeManager.of(player.server).handlePlayerKillSupportDrop(player, killer);
        }
        boolean downedHandled = RoomDeathEventDispatcher.handleRoomFatalDeath(
            RoomCoreRuntimeManager.of(player.server),
            player,
            new RoomPosition(player.getBlockX(), player.getY(), player.getBlockZ()),
            resolveKillerEntityId(event.getSource()),
            player.server.getTickCount(),
            event.getSource()
        );
        RoomDeathEventDispatcher.onRoomDeath(event, downedHandled);
        RoomDeathCauseService.publish(player);
        if (!isTaczBulletDamage(event.getSource()) && !downedHandled && killer != null && killer != player) {
            RoomCoreRuntimeManager.of(player.server).emitPlayerThirdPersonAudioCue(killer.getUUID(), RoomThirdPersonAudioCueRegistry.PLAYER_KILL_ENEMY);
        }
    }

    private static int resolveKillerEntityId(DamageSource damageSource) {
        if (damageSource == null) {
            return -1;
        }
        net.minecraft.world.entity.Entity attacker = damageSource.getEntity();
        if (attacker != null) {
            return attacker.getId();
        }
        net.minecraft.world.entity.Entity direct = damageSource.getDirectEntity();
        return direct == null ? -1 : direct.getId();
    }

    private static void cancelFriendlySquadBeaconDamage(net.minecraftforge.event.entity.living.LivingEvent event) {
        if (event == null
            || !(event.getEntity() instanceof org.mods.gd656conquest.common.entity.SquadDeployBeaconEntity beacon)) {
            return;
        }
        net.minecraft.world.damagesource.DamageSource damageSource;
        if (event instanceof LivingAttackEvent attackEvent) {
            damageSource = attackEvent.getSource();
        } else if (event instanceof LivingHurtEvent hurtEvent) {
            damageSource = hurtEvent.getSource();
        } else {
            return;
        }
        net.minecraft.world.entity.Entity sourceEntity = damageSource == null ? null : damageSource.getEntity();
        if (!(sourceEntity instanceof ServerPlayer attacker) || attacker.server == null) {
            return;
        }
        if (!RoomCoreRuntimeManager.of(attacker.server).shouldCancelDamageToSquadBeacon(attacker, beacon)) {
            return;
        }
        if (event instanceof LivingAttackEvent attackEvent) {
            attackEvent.setCanceled(true);
            return;
        }
        ((LivingHurtEvent) event).setCanceled(true);
    }

    private static void cancelFriendlyPlayerDamage(net.minecraftforge.event.entity.living.LivingEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer target) || target.server == null) {
            return;
        }
        DamageSource damageSource = extractDamageSource(event);
        ServerPlayer attacker = RoomDamageSourceResolver.resolveAttackingPlayer(damageSource, target);
        if (attacker == null || attacker == target || attacker.server == null || attacker.server != target.server) {
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(target.server);
        if (!roomManager.isSameCampPlayer(attacker, target)) {
            return;
        }
        if (event instanceof LivingAttackEvent attackEvent) {
            attackEvent.setCanceled(true);
            return;
        }
        ((LivingHurtEvent) event).setCanceled(true);
    }

    private static DamageSource extractDamageSource(net.minecraftforge.event.entity.living.LivingEvent event) {
        if (event instanceof LivingAttackEvent attackEvent) {
            return attackEvent.getSource();
        }
        if (event instanceof LivingHurtEvent hurtEvent) {
            return hurtEvent.getSource();
        }
        return null;
    }

    private static void markDamagingPlayerAsCombatDeployBlocked(LivingHurtEvent event) {
        if (event == null || event.isCanceled() || event.getAmount() <= 0.0F) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer target) || target.server == null) {
            return;
        }
        ServerPlayer attacker = RoomDamageSourceResolver.resolveAttackingPlayer(event.getSource(), target);
        if (attacker == null || attacker == target || attacker.server == null || attacker.server != target.server) {
            return;
        }
        RoomCoreRuntimeManager.of(target.server).markPlayerDealtDamage(
            attacker.getUUID(),
            target.getUUID(),
            target.server.getTickCount()
        );
    }

    private static boolean isTaczBulletDamage(DamageSource damageSource) {
        if (damageSource == null) {
            return false;
        }
        return isTaczBulletEntity(damageSource.getDirectEntity()) || isTaczBulletEntity(damageSource.getEntity());
    }

    private static boolean isTaczBulletEntity(Entity entity) {
        Class<?> currentClass = entity == null ? null : entity.getClass();
        while (currentClass != null) {
            if ("com.tacz.guns.entity.EntityKineticBullet".equals(currentClass.getName())) {
                return true;
            }
            currentClass = currentClass.getSuperclass();
        }
        return false;
    }

    private static boolean isTacticalGadgetEntity(net.minecraft.world.entity.Entity entity) {
        return entity instanceof org.mods.gd656conquest.common.entity.SquadDeployBeaconEntity
            || entity instanceof org.mods.gd656conquest.common.entity.AmmoBoxEntity
            || entity instanceof org.mods.gd656conquest.common.entity.MedicalBoxEntity;
    }
}
