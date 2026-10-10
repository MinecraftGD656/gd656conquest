package tests;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Audits packet/cleanup ordering in current production bytecode, not simulated game behavior. */
public final class DownedViewWiringCheck {
    static int checks;
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    static List<String> calls(JarFile jar, String type, String method) throws Exception {
        var node = new ClassNode();
        try (InputStream input = jar.getInputStream(jar.getJarEntry("org/mods/gd656conquest/" + type + ".class"))) {
            new ClassReader(input.readAllBytes()).accept(node, 0);
        }
        List<String> calls = new ArrayList<>();
        node.methods.stream().filter(m -> m.name.equals(method)).forEach(m -> {
            for (var instruction : m.instructions) if (instruction instanceof MethodInsnNode call)
                calls.add(call.owner.substring(call.owner.lastIndexOf('/') + 1) + "." + call.name);
        });
        return calls;
    }
    static void ordered(List<String> calls, String... chain) {
        int previous = -1;
        for (String call : chain) {
            int found = calls.indexOf(call);
            check(found > previous, "Production order: " + String.join(" -> ", chain)); previous = found;
        }
    }
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            var revive = calls(jar, "server/room/runtime/RoomPlayerRespawnService", "completeRevive");
            ordered(revive, "DownedViewService.prepareRevive", "DownedRagdollBridge.stop",
                "RoomPlayerStateService.applyRevivedState", "RoomDownedStatePacket.clear", "RoomDeathFlowPacket.clear",
                "RoomPlayerRespawnService.broadcastPlayerAnimatorState", "DownedViewService.completeRevive");
            check(!revive.contains("RoomPlayerRespawnService.applyReviveBlindEffect"), "Only successful revive drops old blindness");
            for (String entry : List.of("tryEnterDownedState", "activateDownedState"))
                ordered(calls(jar, "server/room/runtime/RoomPlayerRespawnService", entry),
                    "DownedViewService.start", "DownedRagdollBridge.start", "RoomPlayerStateService.applyDownedState");
            for (String entry : List.of("openDeploySequence", "openDeployInstant"))
                ordered(calls(jar, "client/room/ClientDeathFlowState", entry),
                    "ClientDownedCamera.handoffToDeployment", "DownedRagdollClientHooks.onDeployTransition");
            ordered(calls(jar, "client/room/ClientDownedCamera", "handoffToDeployment"),
                "ClientDeathFlowState.adoptCameraPose", "ClientDownedCamera.cancelCamera");
            check(calls(jar, "server/compat/ragdoll/RagdollSubscriptions", "mandatory").contains("DownedViewService.observesCorpse"),
                "Observed ordinary corpse is subscribed");
            check(calls(jar, "server/room/runtime/RoomPlayerStateService", "normalizePlayerModelState").contains("DownedViewService.end"),
                "Normalize ends camera/prone session");
            for (String method : List.of("onPlayerLogout", "onPlayerChangedDimension")) {
                check(calls(jar, "server/room/entry/RoomServerModule", method).contains("DownedViewService.end"),
                    "Player lifecycle ends camera: " + method);
            }
        }
        System.out.println("PASS production view/revive/deployment ordering: " + checks);
    }
}
