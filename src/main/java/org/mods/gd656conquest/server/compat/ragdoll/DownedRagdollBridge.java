package org.mods.gd656conquest.server.compat.ragdoll;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.DownedRagdollStatePacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.slf4j.Logger;

/** Optional server integration; no client or Ragdollified class is linked on a dedicated server. */
public final class DownedRagdollBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Body> BODIES = new HashMap<>();
    private static final int BODY_LIFETIME_TICKS = 1_000_000;
    private static final ThreadLocal<FatalLaunch> FATAL_LAUNCH = new ThreadLocal<>();
    private static long nextGeneration;
    private static Api api;
    private static boolean resolved;

    private DownedRagdollBridge() {}

    public static boolean availableFor(ServerPlayer player) {
        Api resolvedApi = resolve();
        return player != null && resolvedApi != null
            && resolvedApi.channel.isRemotePresent(player.connection.connection);
    }

    public static boolean isActive(ServerPlayer player) {
        Body body = player == null ? null : BODIES.get(player.getUUID());
        return body != null && body.server == player.server && body.entityId == player.getId();
    }

    /** Keep the fatal source scoped to this room transition; rejected deaths cannot leak momentum. */
    public static boolean withFatalSource(ServerPlayer player, DamageSource source, BooleanSupplier transition) {
        FatalLaunch previous = FATAL_LAUNCH.get();
        FATAL_LAUNCH.remove();
        try {
            if (player != null && source != null && resolve() != null) {
                try {
                    Vec3 kick = (Vec3)api.explosionKick.invoke(null, player, source);
                    Vec3 centre = source.getSourcePosition();
                    Vec3 movement = player.getDeltaMovement().scale(20.0D);
                    if (finite(kick) && finite(centre) && finite(movement)) {
                        FATAL_LAUNCH.set(new FatalLaunch(player, movement, kick, centre));
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                    LOGGER.warn("Could not capture fatal explosion launch; keeping ordinary downed momentum", error);
                }
            }
            return transition.getAsBoolean();
        } finally {
            if (previous == null) FATAL_LAUNCH.remove();
            else FATAL_LAUNCH.set(previous);
        }
    }

    public static boolean start(ServerPlayer player, RunningRuntimeState state) {
        if (!availableFor(player)) return false;
        stop(player, false);
        Body body = new Body(player, state, ++nextGeneration);
        RagdollSyncScope.beginServer(body.entityId, body.generation);
        try {
            FatalLaunch launch = FATAL_LAUNCH.get();
            if (promoteDeathBody(player, body)) {
                // Preserve the death listener's appearance, equipment and explosion impulse.
            } else if (launch != null && launch.player == player) {
                startExplosion(player, body, launch);
            } else {
                api.start.invoke(null, player, body.anchor, player.getYRot(), player.getXRot(),
                    player.getDeltaMovement().scale(20.0D), BODY_LIFETIME_TICKS);
            }
            captureSpawn(body);
            BODIES.put(body.playerId, body);
            player.noPhysics = true;
            player.setNoGravity(true);
            broadcast(body, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            RagdollSyncScope.endServer(body.entityId, body.generation);
            BODIES.remove(body.playerId, body);
            player.noPhysics = body.previousNoPhysics;
            player.setNoGravity(body.previousNoGravity);
            try {
                api.stop.invoke(null, body.entityId);
            } catch (ReflectiveOperationException | LinkageError cleanupError) {
                error.addSuppressed(cleanupError);
            }
            LOGGER.warn("Could not start downed ragdoll; keeping the normal downed presentation", error);
            return false;
        }
    }

    static Object retainedRecord(ServerPlayer player) {
        if (resolve() == null) return null;
        try {
            return ((Map<?, ?>)api.records.get(null)).get(player.getId());
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.warn("Could not read the pre-death ragdoll record", error);
            return null;
        }
    }
    static Vec3 ordinaryDeathAnchor(ServerPlayer player, Object record, Vec3 fallback) {
        if (record == null || retainedRecord(player) != record) return fallback;
        try {
            if (api.rawLive.getBoolean(record)) return fallback;
            Vec3 position = (Vec3)api.rawAnchor.invoke(record);
            return position != null && Double.isFinite(position.x+position.y+position.z) ? position : fallback;
        } catch (ReflectiveOperationException | RuntimeException error) { return fallback; }
    }

    private static boolean promoteDeathBody(ServerPlayer player, Body body) throws ReflectiveOperationException {
        Object record = retainedRecord(player);
        if (!RagdollFatalContext.isFreshDeathRecord(player, record)) return false;
        if (record == null || api.rawLive.getBoolean(record)) return false;
        api.registerLive.invoke(null, player, api.spawn.get(record), BODY_LIFETIME_TICKS);
        return true;
    }

    private static void startExplosion(ServerPlayer player, Body body, FatalLaunch launch)
            throws ReflectiveOperationException {
        String type = EntityType.getKey(player.getType()).toString();
        Vec3 offset = new Vec3(launch.centre.x - body.anchor.x,
            launch.centre.y - body.anchor.y, launch.centre.z - body.anchor.z);
        // Match the death packet: ordinary movement plus a -3 whole-body explosion velocity kick.
        // Keep the kick in SpawnData so a not-yet-created/replaced body cannot lose an impulse packet.
        Object packet = api.explosionSpawn.newInstance(body.entityId, true, type,
            api.modelType.invoke(null, true, type), 1.0F, body.playerId.toString(), player.getName().getString(),
            body.anchor.x, body.anchor.y, body.anchor.z,
            player.getYRot(), player.getXRot(),
            launch.movement.x, launch.movement.y, launch.movement.z,
            player.getPose() == Pose.SWIMMING, false,
            player.getItemBySlot(EquipmentSlot.HEAD).copy(), player.getItemBySlot(EquipmentSlot.CHEST).copy(),
            player.getItemBySlot(EquipmentSlot.LEGS).copy(), player.getItemBySlot(EquipmentSlot.FEET).copy(),
            (byte)0, (byte)-3, (float)launch.kick.x, (float)launch.kick.y, (float)launch.kick.z,
            (float)offset.x, (float)offset.y, (float)offset.z,
            (byte)0, "", "", (byte)0);
        api.registerLive.invoke(null, player, packet, BODY_LIFETIME_TICKS);
    }

    public static Vec3 position(ServerPlayer player) {
        if (player == null) return Vec3.ZERO;
        Body body = BODIES.get(player.getUUID());
        if (body == null || body.server != player.server) return player.position();
        updateAnchor(body);
        return body.anchor;
    }

    public static double distanceSquared(ServerPlayer viewer, ServerPlayer target) {
        if (!viewer.level().dimension().equals(target.level().dimension())) return Double.POSITIVE_INFINITY;
        return viewer.position().distanceToSqr(position(target));
    }

    /** Descriptor-compatible entry point for inherited Entity distance calls. */
    public static double distanceSquared(Entity viewer, Entity target) {
        if (viewer instanceof ServerPlayer serverViewer && target instanceof ServerPlayer serverTarget) {
            return distanceSquared(serverViewer, serverTarget);
        }
        return viewer.distanceToSqr(target);
    }

    public static void maintainProxy(ServerPlayer player) {
        if (!isActive(player)) return;
        player.noPhysics = true;
        player.setNoGravity(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    /** Runs after the room state update so leaving, ending a match and revival all retire the body. */
    public static void tick(MinecraftServer server) {
        for (Body body : new ArrayList<>(BODIES.values())) {
            if (body.server != server) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(body.playerId);
            if (player == null || player.getId() != body.entityId
                || !player.level().dimension().equals(body.dimension)
                || !body.state.isPlayerDowned(body.playerId)
                || !RoomCoreRuntimeManager.of(server).isPlayerDowned(body.playerId)) {
                retire(body, player, false);
                continue;
            }
            updateAnchor(body);
            // Keep chunk tracking and the invisible player proxy near the simulated torso.
            player.noPhysics = true;
            player.setNoGravity(true);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (RagdollSubscriptionPolicy.heartbeatDue(server.getTickCount(), body.generation)) {
                if (player.position().distanceToSqr(body.anchor) > 0.25D) {
                    player.teleportTo(player.serverLevel(), body.anchor.x, body.anchor.y,
                        body.anchor.z, player.getYRot(), player.getXRot());
                }
                body.state.getDeathPositions().put(body.playerId,
                    new RoomPosition((int)Math.floor(body.anchor.x), body.anchor.y,
                        (int)Math.floor(body.anchor.z)));
                broadcast(body, true);
            }
        }
    }

    public static void stop(ServerPlayer player, boolean revive) {
        if (player == null) return;
        RagdollHitCacheGuard.clearServer(player.getId());
        Body body = BODIES.get(player.getUUID());
        if (body != null && body.server == player.server) retire(body, player, revive);
    }

    public static void clear(MinecraftServer server) {
        for (Body body : new ArrayList<>(BODIES.values())) {
            if (body.server == server) retire(body, server.getPlayerList().getPlayer(body.playerId), false);
        }
        RagdollSyncScope.clearServer();
    }

    private static void retire(Body body, ServerPlayer player, boolean revive) {
        updateAnchor(body, false);
        BODIES.remove(body.playerId, body);
        RagdollSyncScope.endServer(body.entityId, body.generation);
        if (player != null && player.getId() == body.entityId) {
            if (revive && player.level().dimension().equals(body.dimension)) {
                Vec3 prone = DownedViewService.safePronePosition(player, body.anchor, body.origin);
                player.setForcedPose(Pose.SWIMMING);
                player.setPose(Pose.SWIMMING);
                player.teleportTo(player.serverLevel(), prone.x, prone.y, prone.z,
                    DownedViewService.reviveYaw(player), 0.0F);
            }
            player.noPhysics = body.previousNoPhysics;
            player.setNoGravity(body.previousNoGravity);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
        try {
            api.stop.invoke(null, body.entityId);
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("Could not retire the server ragdoll record", error);
        }
        // stopRagdoll only removes the server record; explicitly clear every observing client.
        broadcast(body, false);
    }

    private static void updateAnchor(Body body) {
        updateAnchor(body, true);
    }

    private static void updateAnchor(Body body, boolean restart) {
        try {
            Object retained = ((Map<?, ?>)api.records.get(null)).get(body.entityId);
            if (retained == null || !api.rawLive.getBoolean(retained)) {
                ServerPlayer player = body.server.getPlayerList().getPlayer(body.playerId);
                if (restart && player != null && body.state.isPlayerDowned(body.playerId)) {
                    // Recover an expired record or an automatic death spawn that raced the live spawn.
                    api.start.invoke(null, player, body.anchor, player.getYRot(), player.getXRot(),
                        Vec3.ZERO, BODY_LIFETIME_TICKS);
                    captureSpawn(body);
                    body.viewers.clear();
                    body.poseTick = Integer.MIN_VALUE;
                }
                return;
            }
            int now = body.server.getTickCount();
            if (body.poseTick == now && body.poseRecord == retained) return;
            body.poseTick = now;
            Object settled = api.rawSettled.get(retained);
            boolean reuse = settled != null && body.poseRecord == retained && body.settledPose == settled;
            body.poseRecord = retained;
            body.settledPose = settled;
            if (!reuse) {
                Vec3 anchor = (Vec3)api.rawAnchor.invoke(retained);
                if (finite(anchor)) body.anchor = anchor;
            }
            body.owner = (UUID)api.rawOwner.get(retained);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            // One failed body must not interrupt the room/server tick.
            if (!body.reportedFailure) {
                body.reportedFailure = true;
                LOGGER.warn("Could not read the downed ragdoll pose", error);
            }
        }
    }

    private static void broadcast(Body body, boolean active) {
        if (active) updateAnchor(body);
        Set<UUID> viewers = new HashSet<>();
        for (ServerPlayer viewer : body.server.getPlayerList().getPlayers()) {
            boolean sameDimension = viewer.level().dimension().equals(body.dimension);
            if (active && sameDimension && availableFor(viewer) && RagdollSubscriptions.observes(viewer, body.entityId)) {
                // Initial spawn and its GD generation share one ordered message. Heartbeats stay small.
                NetworkManager.sendToPlayer(viewer, body.packet(true, !body.viewers.contains(viewer.getUUID()), body.owner));
                viewers.add(viewer.getUUID());
            } else if (!active && body.viewers.contains(viewer.getUUID())) {
                NetworkManager.sendToPlayer(viewer, body.packet(false));
            }
        }
        body.viewers = viewers;
    }

    /** A view subscription resumes the same downed generation, before native pose delivery. */
    public static void observe(ServerPlayer viewer, int entityId) {
        for (Body body : BODIES.values()) if (body.server == viewer.server && body.entityId == entityId) {
            updateAnchor(body);
            if (!body.viewers.contains(viewer.getUUID()) && body.spawnTick != body.server.getTickCount()) {
                try { captureSpawn(body); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not refresh observer spawn", error); }
            }
            NetworkManager.sendToPlayer(viewer, body.packet(true, !body.viewers.contains(viewer.getUUID()), body.owner));
            body.viewers.add(viewer.getUUID());
            return;
        }
    }

    public static void unobserve(ServerPlayer viewer, int entityId) {
        for (Body body : BODIES.values()) if (body.server == viewer.server && body.entityId == entityId)
            body.viewers.remove(viewer.getUUID());
    }

    private static void captureSpawn(Body body) throws ReflectiveOperationException {
        Object record = ((Map<?, ?>)api.records.get(null)).get(body.entityId);
        if (record == null) throw new IllegalStateException("Missing live ragdoll record");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            api.encode.invoke(null, api.spawn.get(record), buffer);
            if (buffer.readableBytes() > DownedRagdollStatePacket.MAX_SPAWN_BYTES) {
                throw new IllegalStateException("Downed ragdoll spawn exceeds packet limit");
            }
            body.spawn = new byte[buffer.readableBytes()];
            buffer.readBytes(body.spawn);
            body.spawnTick = body.server.getTickCount();
        } finally {
            buffer.release();
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
            && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static Api resolve() {
        if (resolved) return api;
        resolved = true;
        if (!ModList.get().isLoaded("ragdollified")) return null;
        try {
            Class<?> serverApi = Class.forName("com.raiiiden.ragdollified.api.RagdollifiedServerApi");
            Class<?> sync = Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager");
            Class<?> retained = Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager$RetainedBody");
            Class<?> record = Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager$RetainedRagdoll");
            Class<?> packet = Class.forName("com.raiiiden.ragdollified.network.RagdollSpawnPacket");
            Class<?> model = Class.forName("com.raiiiden.ragdollified.MobModelHelper$ModelType");
            Class<?> spawnState = Class.forName("com.raiiiden.ragdollified.RagdollSpawnState");
            Constructor<?> explosionSpawn = packet.getConstructor(int.class, boolean.class, String.class, model,
                float.class, String.class, String.class, double.class, double.class, double.class,
                float.class, float.class, double.class, double.class, double.class, boolean.class, boolean.class,
                ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class, byte.class, byte.class,
                float.class, float.class, float.class, float.class, float.class, float.class,
                byte.class, String.class, String.class, byte.class);
            Field records = sync.getDeclaredField("RETAINED");
            Field spawn = record.getDeclaredField("spawnPacket");
            Method rawAnchor = record.getDeclaredMethod("anchor");
            Field rawLive = record.getDeclaredField("live"), rawOwner = record.getDeclaredField("streamOwner");
            Field rawSettled = record.getDeclaredField("settledTransforms");
            records.setAccessible(true);
            spawn.setAccessible(true);
            rawAnchor.setAccessible(true);
            rawLive.setAccessible(true);
            rawOwner.setAccessible(true);
            rawSettled.setAccessible(true);
            SimpleChannel channel = (SimpleChannel)Class.forName("com.raiiiden.ragdollified.network.ModNetwork")
                .getField("CHANNEL").get(null);
            api = new Api(serverApi.getMethod("startPlayerRagdoll", ServerPlayer.class, Vec3.class,
                float.class, float.class, Vec3.class, int.class), serverApi.getMethod("stopRagdoll", int.class),
                sync.getMethod("retainedBody", int.class, ResourceKey.class), retained.getMethod("anchor"),
                retained.getMethod("live"), retained.getMethod("streamOwner"), records, spawn,
                packet.getMethod("encode", packet, FriendlyByteBuf.class), channel,
                spawnState.getMethod("captureExplosionVelocityKick", LivingEntity.class, DamageSource.class),
                explosionSpawn, packet.getMethod("resolveModelType", boolean.class, String.class),
                sync.getMethod("registerLive", LivingEntity.class, packet, int.class), rawAnchor, rawLive, rawOwner, rawSettled);
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("Ragdollified API is unavailable; using normal downed players", error);
        }
        return api;
    }

    private record Api(Method start, Method stop, Method retained, Method anchor, Method live, Method owner,
                       Field records, Field spawn, Method encode, SimpleChannel channel, Method explosionKick,
                       Constructor<?> explosionSpawn, Method modelType, Method registerLive,
                       Method rawAnchor, Field rawLive, Field rawOwner, Field rawSettled) {}

    private record FatalLaunch(ServerPlayer player, Vec3 movement, Vec3 kick, Vec3 centre) {}

    private static final class Body {
        final MinecraftServer server;
        final RunningRuntimeState state;
        final UUID playerId;
        final int entityId;
        final long generation;
        final ResourceKey<Level> dimension;
        final Vec3 origin;
        final boolean previousNoPhysics;
        final boolean previousNoGravity;
        Vec3 anchor;
        byte[] spawn = new byte[0];
        Set<UUID> viewers = new HashSet<>();
        boolean reportedFailure;
        int poseTick = Integer.MIN_VALUE;
        int spawnTick = Integer.MIN_VALUE;
        Object poseRecord;
        Object settledPose;
        UUID owner;

        Body(ServerPlayer player, RunningRuntimeState state, long generation) {
            this.server = player.server;
            this.state = state;
            this.playerId = player.getUUID();
            this.entityId = player.getId();
            this.generation = generation;
            this.dimension = player.level().dimension();
            this.origin = player.position();
            this.anchor = origin;
            this.previousNoPhysics = player.noPhysics;
            this.previousNoGravity = player.isNoGravity();
        }

        DownedRagdollStatePacket packet(boolean active) {
            return packet(active, false, null);
        }

        DownedRagdollStatePacket packet(boolean active, boolean includeSpawn, UUID owner) {
            return new DownedRagdollStatePacket(playerId, entityId, generation,
                dimension.location().toString(), active, anchor, includeSpawn ? spawn : new byte[0], owner);
        }
    }
}
