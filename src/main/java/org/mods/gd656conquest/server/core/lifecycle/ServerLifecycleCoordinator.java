package org.mods.gd656conquest.server.core.lifecycle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.ChunkEvent;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import org.mods.gd656conquest.server.core.bootstrap.ServerModuleRegistry;

import java.util.Objects;

public final class ServerLifecycleCoordinator {
    private final ServerModuleRegistry moduleRegistry;

    public ServerLifecycleCoordinator(ServerModuleRegistry moduleRegistry) {
        this.moduleRegistry = Objects.requireNonNull(moduleRegistry, "moduleRegistry");
    }

    public void registerCommands(RegisterCommandsEvent event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.registerCommands(event);
        }
    }

    public void onServerStarting(MinecraftServer server) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onServerStarting(server);
        }
    }

    public void onServerTick(MinecraftServer server, long gameTick) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onServerTick(server, gameTick);
        }
    }

    public void onServerStopped(MinecraftServer server) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onServerStopped(server);
        }
    }

    public void onPlayerRespawn(ServerPlayer player) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerRespawn(player);
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerLogout(player);
        }
    }

    public void onPlayerLogin(ServerPlayer player) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerLogin(player);
        }
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerChangedDimension(player);
        }
    }

    public void onPlayerTick(ServerPlayer player) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerTick(player);
        }
    }

    public void onPlayerItemToss(ItemTossEvent event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerItemToss(event);
        }
    }

    public void onEntityItemPickup(EntityItemPickupEvent event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onEntityItemPickup(event);
        }
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onChunkLoad(event);
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onRightClickBlock(event);
        }
    }

    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onLeftClickBlock(event);
        }
    }

    public void onLivingHurt(LivingHurtEvent event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onLivingHurt(event);
        }
    }

    public void onLivingAttack(LivingAttackEvent event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onLivingAttack(event);
        }
    }

    public void onLivingDeath(LivingDeathEvent event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onLivingDeath(event);
        }
    }

    public void onProjectileImpact(ProjectileImpactEvent event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onProjectileImpact(event);
        }
    }

    public void onPlayerXpPickup(PlayerXpEvent.PickupXp event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerXpPickup(event);
        }
    }

    public void onPlayerXpChange(PlayerXpEvent.XpChange event) {
        for (ServerModule module : moduleRegistry.modules()) {
            module.onPlayerXpChange(event);
        }
    }
}
