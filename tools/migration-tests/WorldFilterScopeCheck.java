package tests;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.util.TraceClassVisitor;

/** Compares the prior main JAR and current production artifact; no game/transport fixtures. */
public final class WorldFilterScopeCheck {
    static int checks;
    static final String BASE = "org/mods/gd656conquest/";
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    static byte[] bytes(JarFile jar, String name) throws Exception {
        try (InputStream input = jar.getInputStream(jar.getJarEntry(name))) { return input.readAllBytes(); }
    }
    static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode node = new ClassNode(); new ClassReader(bytes(jar, BASE + name + ".class")).accept(node, 0); return node;
    }
    static List<String> calls(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name))
            .flatMap(m -> Arrays.stream(m.instructions.toArray()))
            .filter(i -> i instanceof MethodInsnNode).map(i -> ((MethodInsnNode)i).name).toList();
    }
    static String withoutDebug(byte[] bytes) {
        var text = new StringWriter();
        new ClassReader(bytes).accept(new TraceClassVisitor(new PrintWriter(text)), ClassReader.SKIP_DEBUG);
        return text.toString();
    }
    public static void main(String[] args) throws Exception {
        try (JarFile previous = new JarFile(args[0]); JarFile current = new JarFile(args[1])) {
            Set<String> changed = new HashSet<>(Set.of(BASE + "client/room/ClientDeathFlowState.class", BASE + "client/room/ClientDeathCauseState.class"));
            // 0.0.7 deliberately integrates the card/manual gate. Its separate verifier audits these changes.
            if (current.getJarEntry(BASE + "network/KillerCardNetwork.class") != null) {
                for (String type : List.of("network/NetworkManager", "server/room/runtime/RoomDeathEventDispatcher",
                    "server/room/entry/RoomDeathCauseService", "server/room/runtime/RoomPlayerRespawnService",
                    "client/room/ClientDownedState", "client/room/DownedRagdollClientHooks", "client/room/ClientDownedCamera"))
                    changed.add(BASE + type + ".class");
            }
            for (var entry : previous.stream().toList()) {
                check(current.getJarEntry(entry.getName()) != null, "Existing entry retained: " + entry.getName());
                if (entry.getName().startsWith(BASE) && entry.getName().endsWith(".class") && !changed.contains(entry.getName())) {
                    String name = entry.getName(); int inner = name.indexOf('$');
                    boolean helper = inner >= 0 && changed.contains(name.substring(0, inner) + ".class");
                    byte[] before = bytes(previous, name), after = bytes(current, name);
                    check(Arrays.equals(before, after) || helper && withoutDebug(before).equals(withoutDebug(after)),
                        "Prior production behavior outside registered migration edits retained: " + name);
                }
            }
            ClassNode flow = read(current, "client/room/ClientDeathFlowState");
            check(!calls(flow, "applyDesaturation").contains("enableDesaturate"), "Old global shader slot no longer enabled");
            check(calls(flow, "currentHurtFlashAlpha").contains("hideDamageOverlay"), "Death fade suppresses hurt flash");
            check(calls(flow, "deathFilterKillerId").contains("isDeathObservationPhase"), "Fallback killer is restricted to death phase");
            var darkness = flow.methods.stream().filter(m -> m.name.equals("currentDarknessAlpha")).findFirst().orElseThrow();
            check(Arrays.stream(darkness.instructions.toArray()).filter(i -> i.getOpcode() >= 0).map(i -> i.getOpcode()).toList()
                .equals(List.of(Opcodes.FCONST_0, Opcodes.FRETURN)), "Legacy death/respawn black overlay disabled");
            check(calls(read(current, "client/room/ClientDeathCauseState"), "onRender").contains("deathActive"), "Banner shares local death state");
            ClassNode filter = read(current, "client/room/ClientDeathFilter");
            check(calls(filter, "releaseResources").contains("releaseResources") && calls(filter, "releaseResources").contains("releaseDeathChain"),
                "Death cleanup releases both private chains");
            for (String name : List.of("client/room/ClientDeathFilter", "client/room/ClientReviveEffect")) {
                ClassNode node = read(current, name);
                List<String> operations = node.methods.stream().flatMap(m -> calls(node, m.name).stream()).toList();
                check(!operations.contains("m_109128_") && !operations.contains("m_109086_"), "Private effect leaves shared GameRenderer shader slot alone");
                check(!operations.contains("m_147207_") && !operations.contains("m_143403_"), "Visual effect does not change glowing/game mode");
            }
        }
        System.out.println("PASS prior behavior scope / render integration: " + checks);
    }
}
