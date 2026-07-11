package org.mods.gd656conquest.server.core.event;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;
import org.mods.gd656conquest.server.compat.tacz.TaczShellingImpactEventEntrypoint;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleDeployMountGuard;
import org.mods.gd656conquest.server.core.lifecycle.ServerLifecycleCoordinator;

import java.util.Objects;

public final class ServerEventEntrypoint {
    private final ServerLifecycleCoordinator lifecycleCoordinator;

    public ServerEventEntrypoint(ServerLifecycleCoordinator lifecycleCoordinator) {
        this.lifecycleCoordinator = Objects.requireNonNull(lifecycleCoordinator, "lifecycleCoordinator");
        MinecraftForge.EVENT_BUS.register(new TaczShellingImpactEventEntrypoint());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        lifecycleCoordinator.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
            lifecycleCoordinator.onServerTick(event.getServer(), event.getServer().getTickCount());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (event.getServer() != null) {
            lifecycleCoordinator.onServerStopped(event.getServer());
        }
        PlayerAnimatorDebugDimensionsState.resetAll();
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            lifecycleCoordinator.onPlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PlayerAnimatorDebugDimensionsState.clearPlayer(player);
            lifecycleCoordinator.onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            lifecycleCoordinator.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            lifecycleCoordinator.onPlayerChangedDimension(player);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof net.minecraft.server.level.ServerPlayer player) {
            PlayerAnimatorDebugDimensionsState.tickPlayer(player);
            if (event.phase == TickEvent.Phase.END) {
                lifecycleCoordinator.onPlayerTick(player);
            }
        }
    }

    @SubscribeEvent
    public void onEntitySize(EntityEvent.Size event) {
        PlayerAnimatorDebugDimensionsState.handleEntitySize(event);
    }

    @SubscribeEvent
    public void onEntityMount(EntityMountEvent event) {
        YwzjVehicleDeployMountGuard.handleMountEvent(event);
    }

    @SubscribeEvent
    public void onPlayerItemToss(ItemTossEvent event) {
        lifecycleCoordinator.onPlayerItemToss(event);
    }

    @SubscribeEvent
    public void onEntityItemPickup(EntityItemPickupEvent event) {
        lifecycleCoordinator.onEntityItemPickup(event);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        lifecycleCoordinator.onChunkLoad(event);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        lifecycleCoordinator.onRightClickBlock(event);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        lifecycleCoordinator.onRightClickItem(event);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        lifecycleCoordinator.onLeftClickBlock(event);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        lifecycleCoordinator.onLivingHurt(event);
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        lifecycleCoordinator.onLivingAttack(event);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        lifecycleCoordinator.onLivingDeath(event);
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        lifecycleCoordinator.onProjectileImpact(event);
    }

    @SubscribeEvent
    public void onPlayerXpPickup(PlayerXpEvent.PickupXp event) {
        lifecycleCoordinator.onPlayerXpPickup(event);
    }

    @SubscribeEvent
    public void onPlayerXpChange(PlayerXpEvent.XpChange event) {
        lifecycleCoordinator.onPlayerXpChange(event);
    }
}
