package org.mods.gd656conquest.client.room;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSyncScope;

@EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT)
public final class ClientDownedRagdollState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static final Map<UUID, Long> CLOSED_GENERATIONS = new HashMap<>();
    private static final long LEASE_TICKS = 80;
    private static long tick;
    private static Api api;
    private static boolean resolved;
    private static boolean cameraAttached;
    private static int cameraEntityId = -1;
    private static Object lastLevel;
    private static int observedDeathId = -1;
    private static UUID observedDeathOwner;
    private static Vec3 observedDeathOrigin;
    private static Object observedDeathBody;

    private ClientDownedRagdollState() {}

    public static void accept(UUID playerId, int entityId, long generation, String dimension,
                              boolean active, Vec3 anchor) {
        accept(playerId, entityId, generation, dimension, active, anchor, new byte[0], null);
    }

    public static void accept(UUID playerId, int entityId, long generation, String dimension,
                              boolean active, Vec3 anchor, byte[] spawn, UUID owner) {
        if (resolve() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (lastLevel != mc.level) {
            reset();
            lastLevel = mc.level;
        }
        Entry previous = ENTRIES.get(playerId);
        if (previous != null && generation < previous.generation) return;
        if (!active) {
            CLOSED_GENERATIONS.merge(playerId, generation, Math::max);
            RagdollSyncScope.endClient(entityId, generation);
            if (previous != null && previous.generation == generation) {
                release(previous);
                ENTRIES.remove(playerId);
            }
            return;
        }
        if (generation <= CLOSED_GENERATIONS.getOrDefault(playerId, Long.MIN_VALUE)) return;
        if (!mc.level.dimension().location().toString().equals(dimension) || !finite(anchor)) return;
        if (previous == null || previous.generation != generation || previous.entityId != entityId) {
            if (previous != null) release(previous);
            previous = new Entry(playerId, entityId, generation);
            ENTRIES.put(playerId, previous);
            RagdollSyncScope.beginClient(entityId, generation, mc::execute);
        }
        previous.anchor = anchor;
        previous.expiresTick = tick + LEASE_TICKS;
        previous.owner = owner;
        if (spawn.length != 0 && previous.spawn == null) {
            previous.spawn = spawn.clone();
            queueReplacement(previous);
        }
    }

    public static boolean isDowned(UUID playerId) {
        return ENTRIES.containsKey(playerId);
    }

    public static boolean hasObservation(int entityId, long generation) {
        for (Entry entry : ENTRIES.values())
            if (entry.entityId == entityId && entry.generation == generation && entry.spawn != null) return true;
        return false;
    }

    public static boolean isBodyReady(UUID playerId) {
        Entry entry = ENTRIES.get(playerId);
        return entry != null && ready(entry);
    }

    public static boolean hasCameraOverride() {
        Minecraft mc = Minecraft.getInstance();
        return cameraAttached && mc.player != null && (ClientDownedCamera.observingDeath() && deathSnapshot()!=null
            || ClientDownedState.isActive() && isBodyReady(mc.player.getUUID()));
    }
    public static void beginDeathObservation(int id, UUID owner, Vec3 origin) {
        Object level=Minecraft.getInstance().level;
        if(lastLevel!=level){reset();lastLevel=level;}
        endDeathObservation();resolve();observedDeathId=id;observedDeathOwner=owner;observedDeathOrigin=origin;
    }
    public static void endDeathObservation() {
        if (cameraAttached && cameraEntityId==observedDeathId) detachCamera();
        observedDeathId=-1;observedDeathOwner=null;observedDeathOrigin=null;observedDeathBody=null;
    }
    /** Reads native published snapshots only. Ordinary corpses are never replaced or made live. */
    private static Object deathSnapshot() {
        if (api==null || observedDeathId<0 || !ClientDownedCamera.observingDeath()) return null;
        Object body=invoke(api.get,observedDeathId);
        if (body==null || observedDeathBody!=null && observedDeathBody!=body
            || !observedDeathOwner.equals(invokeOn(api.playerUuid,body))
            || !Boolean.FALSE.equals(invokeOn(api.destroyed,body))) return null;
        Object snapshot=invokeOn(api.snapshot,body);
        if(snapshot==null)return null;
        try {
            SnapshotFields fields=SNAPSHOT_FIELDS.get(snapshot.getClass());
            if(fields.destroyed.getBoolean(snapshot))return null;
            Object p=fields.position.get(snapshot);
            Vec3 position=new Vec3(fields.x.getFloat(p),fields.y.getFloat(p),fields.z.getFloat(p));
            if(!finite(position))return null;
            if(observedDeathBody==null && (position.distanceToSqr(observedDeathOrigin)>144
                || snapshot.getClass().getField("ageTicks").getInt(snapshot)>80))return null;
            observedDeathBody=body;return snapshot;
        } catch(ReflectiveOperationException | IllegalArgumentException error){return null;}
    }

    public static double distanceSquared(Player viewer, Player target) {
        Entry entry = ENTRIES.get(target.getUUID());
        if (entry == null || entry.anchor == null) return viewer.distanceToSqr(target);
        ready(entry);
        return viewer.position().distanceToSqr(entry.anchor);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        tick++;
        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }
        if (mc.level == null) return;
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        boolean attachForSelf = false;
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (tick >= entry.expiresTick) {
                release(entry);
                iterator.remove();
                continue;
            }
            if (!ready(entry)) {
                if (entry.spawn != null && entry.spawnAttempts < 3 && tick >= entry.retryTick) {
                    queueReplacement(entry);
                }
                // A failed physics spawn must leave a visible player and the original animation.
                invoke(api.hidden, entry.entityId, false);
                continue;
            }
            invoke(api.hidden, entry.entityId, true);
            if (mc.player != null && entry.playerId.equals(mc.player.getUUID())
                && ClientDownedState.isActive()) {
                attachForSelf = true;
                if (ClientDownedCamera.ownsCamera()) {
                    if (cameraAttached) detachCamera();
                } else if (!cameraAttached || cameraEntityId != entry.entityId) {
                    Object result = invoke(api.attach, entry.entityId, api.head, api.cameraOptions);
                    cameraAttached = Boolean.TRUE.equals(result);
                    cameraEntityId = entry.entityId;
                }
                // Keep Conquest's blackout/HUD phase, but let the ragdoll own the camera position.
                ClientDeathFlowState.enterDownedView();
            }
        }
        if (ClientDownedCamera.usesFixedDeathCamera() && deathSnapshot()!=null) {
            attachForSelf=true;
            if (!cameraAttached || cameraEntityId!=observedDeathId)
                cameraAttached=Boolean.TRUE.equals(invoke(api.attach,observedDeathId,api.head,api.cameraOptions));
            if(cameraAttached)cameraEntityId=observedDeathId;
        }
        if (!attachForSelf && cameraAttached) detachCamera();
    }

    public static void reset() {
        endDeathObservation();
        for (Entry entry : ENTRIES.values()) release(entry);
        ENTRIES.clear();
        RagdollSyncScope.resetClient();
        CLOSED_GENERATIONS.clear();
        if (cameraAttached) detachCamera();
    }

    public static void finishLocal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID playerId = mc.player.getUUID();
        org.mods.gd656conquest.server.compat.ragdoll.RagdollHitCacheGuard.clearClient(mc.player.getId());
        Entry entry = ENTRIES.remove(playerId);
        if (entry != null) {
            CLOSED_GENERATIONS.merge(playerId, entry.generation, Math::max);
            release(entry);
        }
        if (cameraAttached) detachCamera();
        // Ordinary death corpses have no entry and must remain on the ground.
    }

    private static void release(Entry entry) {
        release(entry, true, true);
    }

    public static void suspendObservation(int entityId, long generation) {
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.entityId == entityId && entry.generation == generation) {
                release(entry, false, false);
                it.remove();
            }
        }
    }

    private static void release(Entry entry, boolean close, boolean remove) {
        org.mods.gd656conquest.server.compat.ragdoll.RagdollHitCacheGuard.clearClient(entry.entityId);
        if (close) RagdollSyncScope.endClient(entry.entityId, entry.generation);
        org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState.stopPreview(entry.playerId);
        DownedRagdollClientHooks.clearForcedPose(entry.playerId);
        if (api == null) return;
        invoke(api.hidden, entry.entityId, false);
        invoke(api.persistent, entry.entityId, false);
        if (remove) invoke(api.remove, entry.entityId);
        if (cameraAttached && cameraEntityId == entry.entityId) detachCamera();
    }

    private static void detachCamera() {
        invoke(api.detach, cameraEntityId);
        cameraAttached = false;
        cameraEntityId = -1;
    }

    private static boolean ready(Entry entry) {
        if (api == null || !entry.replacementQueued) return false;
        Object body = invoke(api.get, entry.entityId);
        if (body == null || body == entry.displacedBody || !Boolean.FALSE.equals(invokeOn(api.destroyed, body))) return false;
        if (!entry.playerId.equals(invokeOn(api.playerUuid, body))) return false;
        // Use the published render snapshot rather than reading a worker-owned mutable vector.
        Object snapshot = invokeOn(api.snapshot, body);
        if (snapshot == null) return false;
        if (snapshot != null) {
            try {
                SnapshotFields fields = SNAPSHOT_FIELDS.get(snapshot.getClass());
                if (fields.destroyed.getBoolean(snapshot)) return false;
                Object position = fields.position.get(snapshot);
                Vec3 anchor = new Vec3(fields.x.getFloat(position), fields.y.getFloat(position), fields.z.getFloat(position));
                if (!finite(anchor)) return false;
                entry.anchor = anchor;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return true;
    }

    public static Vec3 cameraAnchor() {
        Minecraft mc = Minecraft.getInstance();
        Entry entry = mc.player == null ? null : ENTRIES.get(mc.player.getUUID());
        if (!ClientDownedCamera.observingDeath() && entry!=null && ready(entry))return entry.anchor;
        Object snapshot=deathSnapshot();
        if(snapshot==null)return null;
        try {
            SnapshotFields fields=SNAPSHOT_FIELDS.get(snapshot.getClass());Object p=fields.position.get(snapshot);
            return new Vec3(fields.x.getFloat(p),fields.y.getFloat(p),fields.z.getFloat(p));
        }catch(ReflectiveOperationException error){return null;}
    }

    /** Only the published head position is sampled; worker rotations are never read here. */
    public static Vec3 cameraHeadPosition() {
        Minecraft mc = Minecraft.getInstance();
        Entry entry = mc.player == null ? null : ENTRIES.get(mc.player.getUUID());
        if (headIndex < 0) return null;
        Object body = !ClientDownedCamera.observingDeath() && entry != null && ready(entry) ? invoke(api.get, entry.entityId) : null;
        Object snapshot = body == null ? deathSnapshot() : invokeOn(api.snapshot, body);
        if (snapshot == null) return null;
        try {
            BoundsFields fields = BOUNDS_FIELDS.get(snapshot.getClass());
            Object[] positions = (Object[])fields.positions.get(snapshot);
            if (headIndex >= positions.length || positions[headIndex] == null) return null;
            Object head = positions[headIndex];
            Vec3 position = new Vec3(fields.x.getFloat(head), fields.y.getFloat(head), fields.z.getFloat(head));
            return finite(position) ? position : null;
        } catch (ReflectiveOperationException | IllegalArgumentException error) { return null; }
    }
    private static int headIndex = -1;

    public static AABB cameraBounds() {
        Minecraft mc = Minecraft.getInstance();
        Entry entry = mc.player == null ? null : ENTRIES.get(mc.player.getUUID());
        Object body = !ClientDownedCamera.observingDeath() && entry != null && ready(entry) ? invoke(api.get, entry.entityId) : null;
        Object snapshot = body == null ? deathSnapshot() : invokeOn(api.snapshot, body);
        if (snapshot == null) return null;
        try {
            BoundsFields fields = BOUNDS_FIELDS.get(snapshot.getClass());
            Object[] positions = (Object[])fields.positions.get(snapshot), extents = (Object[])fields.extents.get(snapshot);
            double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=maxX,maxZ=maxX;
            for (int i=0;i<positions.length && i<extents.length;i++) {
                if (positions[i]==null || extents[i]==null) continue;
                double x=fields.x.getFloat(positions[i]),y=fields.y.getFloat(positions[i]),z=fields.z.getFloat(positions[i]);
                // A sphere encloses a rotated part without touching physics-worker state.
                double radius=Math.sqrt(Math.pow(fields.x.getFloat(extents[i]),2)+Math.pow(fields.y.getFloat(extents[i]),2)
                    +Math.pow(fields.z.getFloat(extents[i]),2));
                if (!Double.isFinite(x+y+z+radius)) continue;
                minX=Math.min(minX,x-radius);minY=Math.min(minY,y-radius);minZ=Math.min(minZ,z-radius);
                maxX=Math.max(maxX,x+radius);maxY=Math.max(maxY,y+radius);maxZ=Math.max(maxZ,z+radius);
            }
            return Double.isFinite(minX) ? new AABB(minX,minY,minZ,maxX,maxY,maxZ) : null;
        } catch (ReflectiveOperationException | IllegalArgumentException error) { return null; }
    }
    private record BoundsFields(java.lang.reflect.Field positions, java.lang.reflect.Field extents,
        java.lang.reflect.Field x, java.lang.reflect.Field y, java.lang.reflect.Field z) {}
    private static final ClassValue<BoundsFields> BOUNDS_FIELDS = new ClassValue<>() {
        protected BoundsFields computeValue(Class<?> type) {
            try {
                var positions=type.getField("positions");var vector=positions.getType().getComponentType();
                return new BoundsFields(positions,type.getField("halfExtents"),vector.getField("x"),vector.getField("y"),vector.getField("z"));
            } catch (ReflectiveOperationException error) { throw new IllegalArgumentException("Missing render bounds",error); }
        }
    };

    private static void queueReplacement(Entry entry) {
        entry.spawnAttempts++;
        entry.retryTick = tick + 10;
        if (entry.spawnAttempts == 1) entry.displacedBody = invoke(api.get, entry.entityId);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(entry.spawn));
        try {
            Object packet = api.decode.invoke(null, buffer);
            // All operations run in one client task. Do not enqueue an ID-based removal:
            // the physics worker replaces/destroys the old object while processing this spawn.
            invoke(api.hidden, entry.entityId, false);
            if (cameraAttached && cameraEntityId == entry.entityId) detachCamera();
            api.persistent.invoke(null, entry.entityId, false);
            Minecraft mc = Minecraft.getInstance();
            api.ownership.invoke(null, entry.entityId,
                mc.player != null && mc.player.getUUID().equals(entry.owner));
            api.spawn.invoke(null, packet);
            entry.replacementQueued = true;
            api.persistent.invoke(null, entry.entityId, true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            if (entry.spawnAttempts == 1) LOGGER.warn("Could not replace previous corpse with downed ragdoll", error);
        } finally {
            buffer.release();
        }
    }

    private record SnapshotFields(java.lang.reflect.Field destroyed, java.lang.reflect.Field position,
                                  java.lang.reflect.Field x, java.lang.reflect.Field y, java.lang.reflect.Field z) {}
    private static final ClassValue<SnapshotFields> SNAPSHOT_FIELDS = new ClassValue<>() {
        protected SnapshotFields computeValue(Class<?> type) {
            try {
                java.lang.reflect.Field position = type.getField("cachedTorsoPos");
                return new SnapshotFields(type.getField("destroyed"), position, position.getType().getField("x"),
                    position.getType().getField("y"), position.getType().getField("z"));
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Ragdoll snapshot API changed", error); }
        }
    };

    private static Object invoke(Method method, Object... args) {
        return invokeOn(method, null, args);
    }

    private static Object invokeOn(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
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
            Class<?> clientApi = Class.forName("com.raiiiden.ragdollified.api.RagdollifiedApi");
            Class<?> manager = Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager");
            Class<?> body = Class.forName("com.raiiiden.ragdollified.client.ClientRagdoll");
            Class<?> part = Class.forName("com.raiiiden.ragdollified.RagdollPart");
            Class<?> camera = Class.forName("com.raiiiden.ragdollified.api.CameraOptions");
            Class<?> packet = Class.forName("com.raiiiden.ragdollified.network.RagdollSpawnPacket");
            Method spawn = packet.getDeclaredMethod("handleClient", packet);
            spawn.setAccessible(true);
            api = new Api(clientApi.getMethod("setEntityHidden", int.class, boolean.class),
                manager.getMethod("setPersistent", int.class, boolean.class), clientApi.getMethod("remove", int.class),
                manager.getMethod("get", int.class), body.getMethod("isDestroyed"), body.getMethod("getPlayerUUID"), body.getMethod("getSnapshot"),
                clientApi.getMethod("attachCamera", int.class, part, camera),
                clientApi.getMethod("detachCamera", int.class), part.getField("HEAD").get(null),
                camera.getField("DEFAULT").get(null), packet.getMethod("decode", FriendlyByteBuf.class), spawn,
                manager.getMethod("enqueueStreamOwnership", int.class, boolean.class));
            // Optional to the camera reaction; a missing field must not disable the whole integration.
            try { headIndex = part.getField("index").getInt(api.head); }
            catch (ReflectiveOperationException ignored) { headIndex = -1; }
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("Ragdollified client API unavailable; using normal downed presentation", error);
        }
        return api;
    }

    private record Api(Method hidden, Method persistent, Method remove, Method get,
                       Method destroyed, Method playerUuid, Method snapshot, Method attach, Method detach,
                       Object head, Object cameraOptions, Method decode, Method spawn, Method ownership) {}

    private static final class Entry {
        final UUID playerId;
        final int entityId;
        final long generation;
        Vec3 anchor;
        long expiresTick;
        byte[] spawn;
        UUID owner;
        Object displacedBody;
        boolean replacementQueued;
        int spawnAttempts;
        long retryTick;

        Entry(UUID playerId, int entityId, long generation) {
            this.playerId = playerId;
            this.entityId = entityId;
            this.generation = generation;
        }
    }
}
