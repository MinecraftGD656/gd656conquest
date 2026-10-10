package tests;

import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Validates the actual optional adapter surface and the current production entrypoint wiring. */
public final class DeathCauseWiringCheck {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private static ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name.replace('.', '/') + ".class");
        if (entry == null) throw new AssertionError("Missing production class: " + name);
        ClassNode node = new ClassNode();
        try (InputStream input = jar.getInputStream(entry)) { new ClassReader(input.readAllBytes()).accept(node, 0); }
        return node;
    }
    private static void call(JarFile jar, String type, String method, String owner, String target) throws Exception {
        ClassNode node = read(jar, "org.mods.gd656conquest." + type);
        long count = node.methods.stream().filter(m -> m.name.equals(method))
            .flatMap(m -> Arrays.stream(m.instructions.toArray()))
            .filter(i -> i instanceof MethodInsnNode c && c.owner.equals("org/mods/gd656conquest/" + owner) && c.name.equals(target)).count();
        check(count == 1, "Exactly one death-cause call: " + type + "." + method + " => " + target);
    }
    public static void main(String[] args) throws Exception {
        try (JarFile nativeJar = new JarFile(args[1]); JarFile current = new JarFile(args[0])) {
            ClassNode engine = read(nativeJar, "org.mods.gd656killicon.server.event.ServerCombatEngine");
            check(engine.fields.stream().anyMatch(f -> f.name.equals("fireAttribution") && f.desc.equals("Ljava/util/Map;")), "Current Killicon history map exists");
            check(engine.fields.stream().anyMatch(f -> f.name.equals("FIRE_ATTRIBUTION_TIMEOUT_MS") && (f.desc.equals("J") || f.desc.equals("I"))), "Current Killicon expiry field readable as long");
            ClassNode fire = read(nativeJar, "org.mods.gd656killicon.server.event.FireAttribution");
            check(fire.methods.stream().anyMatch(m -> m.name.equals("attackerId") && m.desc.equals("()Ljava/util/UUID;")), "Current Killicon attacker accessor exists");
            check(fire.methods.stream().anyMatch(m -> m.name.equals("timestamp") && m.desc.equals("()J")), "Current Killicon timestamp accessor exists");
            ClassNode attribution = read(nativeJar, "org.mods.gd656killicon.server.event.ServerCombatAttribution");
            check(attribution.methods.stream().anyMatch(m -> m.name.equals("resolveMolotovOwner") && m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/server/level/ServerPlayer;")), "Current burning cloud resolver exists");
            ClassNode adapter = read(current, "org.mods.gd656conquest.server.room.entry.KilliconFireOwnership");
            check(adapter.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray())).noneMatch(i ->
                i instanceof MethodInsnNode c && c.owner.equals("java/util/Map") && java.util.Set.of("put", "remove", "clear", "compute").contains(c.name)), "Fire adapter never mutates the external attribution map");
            String service = "server/room/entry/RoomDeathCauseService";
            call(current, "server.room.runtime.RoomDeathEventDispatcher", "dispatchPlayerDeath", service, "withFireOwnership");
            call(current, "server.room.runtime.RoomDeathEventDispatcher", "handleRoomFatalDeath", service, "killerEntityId");
            call(current, "server.room.entry.RoomServerModule", "onLivingDeath", service, "resolveKiller");
            call(current, "server.room.entry.RoomServerModule", "onLivingDeath", service, "publish");
            call(current, "server.room.core.RoomCoreService", "handlePlayerFatalState", service, "consumeBoundaryDeath");
            // 0.0.7 deliberately replaces the legacy observation delay with manual deployment.
            call(current, "server.room.runtime.RoomPlayerRespawnService", "onPlayerDeath", "server/room/entry/ManualDeathDeployService", "begin");
            call(current, "server.room.runtime.RoomPlayerRespawnService", "tryEnterDownedState", service, "skipDowned");
            call(current, "server.room.runtime.RoomPlayerStateService", "normalizePlayerModelState", service, "forget");
            call(current, "client.room.ClientDeathFlowState", "clear", "client/room/ClientDeathCauseState", "hide");
        }
        System.out.println("PASS current Killicon API / production death-cause wiring: " + checks);
    }
}
