package tests;

import java.util.*;
import java.util.concurrent.*;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSyncScope;

public final class RagdollScopeTest {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static final Map<Integer, Integer> streamSendSequences = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> lastStreamSampleTicks = new ConcurrentHashMap<>();
    private static final Map<Integer, Object> authoritativeStates = new ConcurrentHashMap<>();
    private static final Map<Integer, Object> pendingStreamPoses = new ConcurrentHashMap<>();
    private static final Queue<Update> streamPoseQueue = new ConcurrentLinkedQueue<>();
    private static final Queue<Update> impulseQueue = new ConcurrentLinkedQueue<>();
    private static final Queue<Update> severQueue = new ConcurrentLinkedQueue<>();
    private record Update(int entityId) {}

    public static void main(String[] args) {
        int id = 42;
        List<Integer> received = new ArrayList<>();
        Queue<Runnable> clientTasks = new ArrayDeque<>();
        Object oldBody = new Object(), fresh = new Object();
        RagdollSyncScope.beginServer(id, 1);
        RagdollSyncScope.receive(id, 1, true, () -> received.add(1));
        RagdollSyncScope.beginServer(id, 2);
        RagdollSyncScope.receive(id, 1, true, () -> received.add(-1));
        RagdollSyncScope.receive(id, 0, true, () -> received.add(-2));
        RagdollSyncScope.receive(id, 2, true, () -> received.add(2));
        check(received.equals(List.of(1, 2)), "server rejects old/ordinary frames when a new live generation is registered");
        RagdollSyncScope.endServer(id, 1);
        check(RagdollSyncScope.serverGeneration(id) == 2, "old cleanup cannot close a new server generation");
        RagdollSyncScope.endServer(id, 2);
        RagdollSyncScope.receive(id, 2, true, () -> received.add(-3));
        RagdollSyncScope.receive(id, 0, true, () -> received.add(3));
        check(received.equals(List.of(1, 2, 3)), "closed live generation cannot affect subsequent ordinary corpse");
        RagdollSyncScope.clearServer();

        RagdollSyncScope.beginClient(id, 1, clientTasks::offer);
        check(!RagdollSyncScope.canTransmit(id, oldBody), "displaced ordinary corpse cannot send into the new session");
        streamSendSequences.put(id, 12); lastStreamSampleTicks.put(id, 200);
        authoritativeStates.put(id, oldBody); pendingStreamPoses.put(id, oldBody);
        streamPoseQueue.offer(new Update(id)); streamPoseQueue.offer(new Update(99));
        impulseQueue.offer(new Update(id)); impulseQueue.offer(new Update(99));
        severQueue.offer(new Update(id)); severQueue.offer(new Update(99));
        RagdollSyncScope.receive(id, 0, false, () -> received.add(-4));
        RagdollSyncScope.receive(id, 1, false, () -> received.add(4));
        check(received.size() == 3, "new generation's early pose waits while old pose is dropped");
        RagdollSyncScope.prepareSpawn(id, RagdollScopeTest.class);
        check(!streamSendSequences.containsKey(id) && !lastStreamSampleTicks.containsKey(id), "physics replacement clears both sender counters");
        check(!authoritativeStates.containsKey(id) && !pendingStreamPoses.containsKey(id), "physics replacement clears retained old poses");
        check(streamPoseQueue.size() == 1 && streamPoseQueue.peek().entityId == 99, "only this body's queued old poses are cleared");
        check(impulseQueue.size() == 1 && impulseQueue.peek().entityId == 99, "old pending impulses cannot spill into the new object");
        check(severQueue.size() == 1 && severQueue.peek().entityId == 99, "old pending sever requests cannot spill into the new object");
        RagdollSyncScope.commitSpawn(id, fresh);
        check(RagdollSyncScope.canTransmit(id, fresh) && !RagdollSyncScope.canTransmit(id, oldBody), "only exact newly published object may transmit");
        check(RagdollSyncScope.captureGeneration(id) == 1, "fresh body's outgoing packets carry its generation");
        check(received.size() == 3 && clientTasks.size() == 1, "worker schedules early packets on client thread instead of running them itself");
        clientTasks.remove().run();
        check(received.equals(List.of(1,2,3,4)), "new early pose survives old-cache clearing and applies after commit");

        RagdollSyncScope.beginClient(id, 2, clientTasks::offer);
        check(!RagdollSyncScope.canTransmit(id, fresh), "second replacement immediately stops first object's transmission");
        RagdollSyncScope.receive(id, 1, false, () -> received.add(-5));
        RagdollSyncScope.receive(id, 2, false, () -> received.add(5));
        Object second = new Object();
        RagdollSyncScope.prepareSpawn(id, RagdollScopeTest.class);
        RagdollSyncScope.commitSpawn(id, second);
        RagdollSyncScope.endClient(id, 1);
        clientTasks.remove().run();
        check(received.get(received.size()-1) == 5, "late first-generation cleanup cannot prevent second-generation pose");
        RagdollSyncScope.endClient(id, 2);
        check(!RagdollSyncScope.canTransmit(id, second), "retiring body cannot send stale ordinary frames");
        RagdollSyncScope.receive(id, 2, false, () -> received.add(-6));
        RagdollSyncScope.receive(id, 0, false, () -> received.add(6));
        check(received.get(received.size()-1) == 6, "ordinary spawn task is allowed immediately after retirement");
        Object ordinary = new Object();
        RagdollSyncScope.prepareSpawn(id, RagdollScopeTest.class);
        RagdollSyncScope.commitSpawn(id, ordinary);
        check(RagdollSyncScope.canTransmit(id, ordinary) && RagdollSyncScope.captureGeneration(id) == 0,
            "next ordinary spawn returns to native transmission");
        RagdollSyncScope.receive(99, 0, false, () -> received.add(7));
        check(received.get(received.size()-1) == 7, "other ordinary corpses retain native handling");
        RagdollSyncScope.resetClient();
        check(RagdollSyncScope.canTransmit(id, ordinary), "world reset clears client sessions");

        received.clear(); clientTasks.clear();
        RagdollSyncScope.receive(id, 3, false, () -> received.add(30));
        RagdollSyncScope.receive(id, 3, false, () -> received.add(31));
        check(received.isEmpty(), "packets before GD binding never mutate an ordinary body");
        RagdollSyncScope.beginClient(id, 3, clientTasks::offer);
        RagdollSyncScope.prepareSpawn(id, RagdollScopeTest.class);
        RagdollSyncScope.commitSpawn(id, fresh);
        clientTasks.remove().run();
        check(received.equals(List.of(30,31)), "pre-binding frames survive replacement in arrival order");
        RagdollSyncScope.receive(id, 4, false, () -> received.add(40));
        RagdollSyncScope.receive(id, 3, false, () -> received.add(32));
        check(received.equals(List.of(30,31,32)), "future packets wait while current generation continues");
        RagdollSyncScope.endClient(id, 3);
        RagdollSyncScope.beginClient(id, 4, clientTasks::offer);
        RagdollSyncScope.commitSpawn(id, second); clientTasks.remove().run();
        check(received.equals(List.of(30,31,32,40)), "future generation's early frames reach only its new object");
        RagdollSyncScope.endClient(id, 4);
        RagdollSyncScope.receive(id, 4, false, () -> received.add(-40));
        RagdollSyncScope.prepareSpawn(id, RagdollScopeTest.class);
        RagdollSyncScope.commitSpawn(id, ordinary);
        RagdollSyncScope.receive(id, 4, false, () -> received.add(-41));
        RagdollSyncScope.beginClient(id, 5, clientTasks::offer);
        RagdollSyncScope.commitSpawn(id, fresh); clientTasks.remove().run();
        check(received.equals(List.of(30,31,32,40)), "closed generations stay rejected after ordinary spawn removes tombstone");
        RagdollSyncScope.resetClient();
        streamSendSequences.put(id, 88);lastStreamSampleTicks.put(id,200);
        authoritativeStates.put(id,"fresh ordinary state");pendingStreamPoses.put(id,"fresh ordinary pose");
        RagdollSyncScope.prepareSpawn(id,RagdollScopeTest.class);
        check(!streamSendSequences.containsKey(id)&&!lastStreamSampleTicks.containsKey(id),
            "ordinary physical replacement resets sender counters without a GD binding");
        check(authoritativeStates.containsKey(id)&&pendingStreamPoses.containsKey(id),
            "ordinary spawn retains its current native pose/state input");
        RagdollSyncScope.prepareTransmit(id,fresh,RagdollScopeTest.class);
        streamSendSequences.put(id,4);lastStreamSampleTicks.put(id,4);
        RagdollSyncScope.prepareTransmit(id,fresh,RagdollScopeTest.class);
        check(streamSendSequences.get(id)==4&&lastStreamSampleTicks.get(id)==4,
            "same sender object does not reset on every tick");
        RagdollSyncScope.prepareTransmit(id,second,RagdollScopeTest.class);
        check(!streamSendSequences.containsKey(id)&&!lastStreamSampleTicks.containsKey(id),
            "sending thread resets when physical object changes");
        lastStreamSampleTicks.put(id,199); // simulate a retiring sender writing after the worker reset
        RagdollSyncScope.prepareTransmit(id,ordinary,RagdollScopeTest.class);
        check(!lastStreamSampleTicks.containsKey(id),"sender identity check removes late old-counter write");
        RagdollSyncScope.resetClient();
        System.out.println("PASS generation isolation / worker replacement / early poses: " + checks);
    }
}
