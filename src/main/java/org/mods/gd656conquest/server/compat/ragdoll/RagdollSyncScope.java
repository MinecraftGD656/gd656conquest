package org.mods.gd656conquest.server.compat.ragdoll;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/** Separate client/server maps also support an integrated server in the same JVM. */
public final class RagdollSyncScope {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<Integer, Long> SERVER = new ConcurrentHashMap<>();
    private static final Map<Integer, long[]> SERVER_STATS = new ConcurrentHashMap<>();
    private static final Set<Thread> SERVER_THREADS = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, ClientSession> CLIENT = new ConcurrentHashMap<>();
    private static final Map<Integer, ClientSession> EARLY = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> CLOSED = new ConcurrentHashMap<>();
    private static final Map<Integer, WeakReference<Object>> TRANSMITTERS = new ConcurrentHashMap<>();
    private static final Set<Integer> SUSPENDED = ConcurrentHashMap.newKeySet();
    private static final ClassValue<Field> IDS = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            for (String name : new String[]{"entityId", "originalEntityId", "ragdollId", "ragdollEntityId"}) {
                try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
                catch (NoSuchFieldException ignored) {}
            }
            throw new IllegalStateException("Unknown Ragdollified packet ID: " + type.getName());
        }
    };

    private RagdollSyncScope() {}

    public static int packetId(Object packet) {
        try { return IDS.get(packet.getClass()).getInt(packet); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }

    public static void beginServer(int id, long generation) {
        SERVER_THREADS.add(Thread.currentThread());
        SERVER.put(id, generation);
        SERVER_STATS.put(id, new long[]{System.nanoTime(), 0});
    }
    public static void endServer(int id, long generation) {
        if (SERVER.remove(id, generation)) SERVER_STATS.remove(id);
    }
    public static long serverGeneration(int id) { return SERVER.getOrDefault(id, 0L); }
    public static void clearServer() { SERVER.clear(); SERVER_STATS.clear(); SERVER_THREADS.remove(Thread.currentThread()); }

    public static void beginClient(int id, long generation, Executor executor) {
        SUSPENDED.remove(id);
        CLIENT.compute(id, (key, previous) -> previous != null && previous.generation == generation
            ? previous : new ClientSession(generation, executor));
        ClientSession early = EARLY.get(id);
        if (early != null && early.generation <= generation && EARLY.remove(id, early)
                && early.generation == generation) CLIENT.get(id).waiting.addAll(early.waiting);
    }

    public static void endClient(int id, long generation) {
        CLOSED.merge(id, generation, Math::max);
        CLIENT.computeIfPresent(id, (key, previous) -> previous.generation == generation
            ? new ClientSession(0, previous.executor) : previous);
    }

    public static void resetClient() { CLIENT.clear(); EARLY.clear(); CLOSED.clear(); TRANSMITTERS.clear(); SUSPENDED.clear(); }

    public static void suspendClient(int id) {
        SUSPENDED.add(id);
        ClientSession session = CLIENT.get(id);
        if (session != null) { session.body = null; session.waiting.clear(); }
        EARLY.remove(id);
    }
    public static void resumeClient(int id) { SUSPENDED.remove(id); }

    public static boolean isCurrentClientBody(int id, Object body) {
        ClientSession session = CLIENT.get(id);
        return session != null && session.generation > 0 && session.body == body;
    }

    public static long captureGeneration(int id) {
        if (SERVER_THREADS.contains(Thread.currentThread())) return serverGeneration(id);
        ClientSession session = CLIENT.get(id);
        return session == null || session.body == null ? 0 : session.generation;
    }

    /** Evaluated inside the original main-thread packet task, rather than on the network thread. */
    public static void receive(int id, long generation, boolean serverbound, Runnable task) {
        receive(id, generation, serverbound, false, task);
    }

    private static void receive(int id, long generation, boolean serverbound, boolean ownership, Runnable task) {
        if (serverbound) {
            if (generation == serverGeneration(id)) task.run();
            return;
        }
        if (SUSPENDED.contains(id) && !ownership) return;
        ClientSession session = CLIENT.get(id);
        if (generation == 0 && session == null) { task.run(); return; }
        if (session == null || generation != session.generation) {
            // Native ownership/state packets precede the GD binding during registerLive.
            // Keep future packets without ever running them against the previous object.
            if (generation > 0 && generation > CLOSED.getOrDefault(id, 0L)
                    && (session == null || generation > session.generation)
                    && (EARLY.containsKey(id) || EARLY.size() < 256)) {
                EARLY.compute(id, (key, early) -> {
                    if (early == null || generation > early.generation) early = new ClientSession(generation, null);
                    if (generation == early.generation && early.waiting.size() < 128) early.waiting.offer(task);
                    return early;
                });
            }
            return;
        }
        if (generation == 0) { task.run(); return; }
        if (session.body == null) {
            // Keep this generation's early pose/ownership until the worker has finished replacing.
            if (session.waiting.size() < 128) session.waiting.offer(task);
            return;
        }
        Runnable waiting;
        while ((waiting = session.waiting.poll()) != null) waiting.run();
        task.run();
    }

    public static boolean canTransmit(int id, Object body) {
        ClientSession session = CLIENT.get(id);
        return session == null || (session.body != null && session.body == body);
    }

    /** Recheck object identity on the sending thread, including unbound/ordinary corpses. */
    public static void prepareTransmit(int id, Object body, Class<?> manager) {
        WeakReference<Object> previous = TRANSMITTERS.get(id);
        if (previous != null && previous.get() == body) return;
        resetSenderCounters(id, manager);
        TRANSMITTERS.put(id, new WeakReference<>(body));
        LOGGER.info("[GD-Ragdoll-Sync] sender-rebound id={} gdGeneration={}", id, captureGeneration(id));
    }

    private static void resetSenderCounters(int id, Class<?> manager) {
        try {
            for (String name : new String[]{"streamSendSequences", "lastStreamSampleTicks"}) {
                Field field = manager.getDeclaredField(name); field.setAccessible(true);
                ((Map<?, ?>)field.get(null)).remove(id);
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Ragdoll sender API changed", error); }
    }

    /** One summary at settlement identifies a missing motion stream in real multiplayer logs. */
    public static void receivePacket(Object packet, long generation, boolean serverbound, Runnable task) {
        int id = packetId(packet);
        // Ownership must still arrive while hidden: the server can elect this client to simulate.
        boolean ownership = packet.getClass().getSimpleName().equals("RagdollStreamOwnerPacket");
        receive(id, generation, serverbound, ownership, () -> {
            trace(packet, generation, serverbound, false);
            task.run();
        });
    }

    public static void traceSend(Object packet) {
        trace(packet, ((RagdollPacketScope)packet).gd656conquest$generation(), false, true);
    }

    private static void trace(Object packet, long generation, boolean serverbound, boolean outgoing) {
        if (generation <= 0) return;
        String kind = packet.getClass().getSimpleName();
        if (!kind.equals("RagdollStreamPacket") && !kind.equals("RagdollStatePacket")) return;
        int id = packetId(packet);
        ClientSession session = CLIENT.get(id);
        long[] stats = serverbound ? SERVER_STATS.get(id) : null;
        if (serverbound && stats == null) return;
        if (!serverbound && (session == null || session.generation != generation)) return;
        if (kind.equals("RagdollStreamPacket")) {
            if (serverbound) stats[1]++;
            else if (outgoing) session.sentFrames++;
            else session.receivedFrames++;
            return;
        }
        try {
            Field settled = packet.getClass().getDeclaredField("settled"); settled.setAccessible(true);
            if (!settled.getBoolean(packet)) return;
            long started = serverbound ? stats[0] : session.started;
            long frames = serverbound ? stats[1] : outgoing ? session.sentFrames : session.receivedFrames;
            LOGGER.info("[GD-Ragdoll-Sync] {} id={} generation={} motionFrames={} elapsedMs={}",
                serverbound ? "server-received-settled" : outgoing ? "client-sent-settled" : "client-received-settled",
                id, generation, frames, (System.nanoTime() - started) / 1_000_000);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    /** Called on the physics worker after removing the old object and before constructing the new one. */
    public static void prepareSpawn(int id, Class<?> manager) {
        // A native death spawn can replace the body before any GD binding exists.
        resetSenderCounters(id, manager);
        ClientSession session = CLIENT.get(id);
        if (session == null) return;
        session.body = null;
        try {
            for (String name : new String[]{"authoritativeStates", "pendingStreamPoses"}) {
                if (session.generation == 0) continue;
                Field field = manager.getDeclaredField(name); field.setAccessible(true);
                ((Map<?, ?>)field.get(null)).remove(id);
            }
            if (session.generation == 0) return;
            for (String name : new String[]{"streamPoseQueue", "impulseQueue", "severQueue"}) {
                Field field = manager.getDeclaredField(name); field.setAccessible(true);
                ((Queue<?>)field.get(null)).removeIf(update -> packetId(update) == id);
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Ragdoll replacement API changed", error); }
    }

    /** Called after publishing the new object in ragdolls; queued tasks return to the client thread. */
    public static void commitSpawn(int id, Object body) {
        ClientSession session = CLIENT.get(id);
        if (session == null) return;
        if (session.generation == 0) { CLIENT.remove(id, session); return; }
        session.body = body;
        session.executor.execute(() -> {
            Runnable task;
            while (CLIENT.get(id) == session && (task = session.waiting.poll()) != null) task.run();
            session.waiting.clear();
        });
    }

    private static final class ClientSession {
        final long generation;
        final Executor executor;
        final Queue<Runnable> waiting = new ConcurrentLinkedQueue<>();
        final long started = System.nanoTime();
        int sentFrames;
        int receivedFrames;
        volatile Object body;
        ClientSession(long generation, Executor executor) { this.generation = generation; this.executor = executor; }
    }
}
