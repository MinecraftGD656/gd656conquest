package org.mods.gd656conquest.server.smoke;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.mods.gd656conquest.common.entity.ShellingSmokeEntity;
import org.mods.gd656conquest.common.smoke.SmokeCloudInstance;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.SmokeCloudSyncPacket;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes stationary smoke clouds independently of entity tracking range.
 */
public final class SmokeCloudSyncService {
    private static final String SHELLING_SMOKE_ID = "gd656conquest:shelling_smoke";
    // ModEntities registers the current LR smoke grenade as "smoke_grenade".
    // Keep the old internal-build name as a compatibility alias.
    private static final String LR_SMOKE_GRENADE_ID = "lrtactical:smoke_grenade";
    private static final String LR_SMOKE_GRENADE_LEGACY_ID = "lrtactical:smoke_grenade_entity";
    private final Map<UUID, SmokeCloudInstance> activeClouds = new LinkedHashMap<>();

    public void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Map<UUID, SmokeCloudInstance> seen = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            long gameTick = level.getGameTime();
            for (Entity entity : level.getAllEntities()) {
                SmokeCloudInstance instance = resolveCloud(level, entity, gameTick);
                if (instance == null) {
                    continue;
                }
                seen.put(instance.id(), instance);
                if (!activeClouds.containsKey(instance.id())) {
                    activeClouds.put(instance.id(), instance);
                    broadcast(server, SmokeCloudSyncPacket.create(instance));
                }
            }
        }
        Iterator<Map.Entry<UUID, SmokeCloudInstance>> iterator = activeClouds.entrySet().iterator();
        while (iterator.hasNext()) {
            SmokeCloudInstance instance = iterator.next().getValue();
            if (seen.containsKey(instance.id())) {
                continue;
            }
            broadcast(server, SmokeCloudSyncPacket.remove(instance.id(), instance.dimensionId()));
            iterator.remove();
        }
    }

    public void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        for (SmokeCloudInstance instance : activeClouds.values()) {
            NetworkManager.sendToPlayer(player, SmokeCloudSyncPacket.create(instance));
        }
    }

    public void clear() {
        activeClouds.clear();
    }

    private SmokeCloudInstance resolveCloud(ServerLevel level, Entity entity, long gameTick) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) {
            return null;
        }
        String id = entityId.toString();
        if (LR_SMOKE_GRENADE_ID.equals(id) || LR_SMOKE_GRENADE_LEGACY_ID.equals(id)) {
            // Match LR's original smoke activation threshold. Its custom bouncing
            // projectile does not reliably report vanilla onGround state.
            if (entity.tickCount < 40) {
                return null;
            }
            return createCloud(level, entity, gameTick, Long.MAX_VALUE);
        }
        if (SHELLING_SMOKE_ID.equals(id) && entity instanceof ShellingSmokeEntity shellingSmoke) {
            return createCloud(level, entity, gameTick, gameTick + shellingSmoke.getRemainingDurationTicks());
        }
        return null;
    }

    private SmokeCloudInstance createCloud(ServerLevel level, Entity entity, long startTick, long endTick) {
        UUID id = entity.getUUID();
        return new SmokeCloudInstance(
            id,
            level.dimension().location().toString(),
            entity.getX(),
            entity.getY(),
            entity.getZ(),
            5.5D,
            4.5D,
            id.getMostSignificantBits() ^ id.getLeastSignificantBits(),
            startTick,
            endTick
        );
    }

    private void broadcast(MinecraftServer server, SmokeCloudSyncPacket packet) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, packet);
        }
    }
}
