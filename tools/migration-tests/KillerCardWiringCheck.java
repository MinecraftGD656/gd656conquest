package tests;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

/** Static production ordering/scope audit, separate from dynamic input/codec/Mixin checks. */
public final class KillerCardWiringCheck {
    static int checks;
    static final String BASE = "org/mods/gd656conquest/";
    static final String RESPAWN = "server/room/runtime/RoomPlayerRespawnService";
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    static byte[] bytes(JarFile jar, String name) throws Exception {
        try (var in = jar.getInputStream(jar.getJarEntry(name))) { return in.readAllBytes(); }
    }
    static ClassNode read(JarFile jar, String name) throws Exception {
        var node = new ClassNode(); new ClassReader(bytes(jar, BASE + name + ".class")).accept(node, 0); return node;
    }
    static String withoutDebug(byte[] bytes) {
        var text = new StringWriter();
        new ClassReader(bytes).accept(new TraceClassVisitor(new PrintWriter(text)), ClassReader.SKIP_DEBUG);
        return text.toString();
    }
    static List<MethodInsnNode> invocations(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name) || name.equals("*"))
            .flatMap(m -> Arrays.stream(m.instructions.toArray())).filter(i -> i instanceof MethodInsnNode)
            .map(i -> (MethodInsnNode)i).toList();
    }
    static List<String> calls(ClassNode node, String name) {
        return invocations(node, name).stream().map(c -> c.owner.substring(c.owner.lastIndexOf('/') + 1) + "." + c.name).toList();
    }
    static boolean constant(ClassNode node, String method, Object value) {
        return node.methods.stream().filter(m -> m.name.equals(method)).flatMap(m -> Arrays.stream(m.instructions.toArray()))
            .anyMatch(i -> i instanceof LdcInsnNode c && value.equals(c.cst));
    }
    static void ordered(List<String> list, String... chain) {
        int last = -1;
        for (String call : chain) {
            int next = list.indexOf(call);
            check(next > last, "Production order: " + String.join(" -> ", chain)); last = next;
        }
    }
    public static void main(String[] args) throws Exception {
        try (var previous = new JarFile(args[0]); var current = new JarFile(args[1])) {
            Set<String> changed = Set.of("network/NetworkManager", "server/room/runtime/RoomDeathEventDispatcher",
                "server/room/entry/RoomDeathCauseService", RESPAWN, "client/room/ClientDeathCauseState",
                "client/room/ClientDownedState", "client/room/DownedRagdollClientHooks", "client/room/ClientDownedCamera");
            for (var entry : previous.stream().toList()) {
                String name = entry.getName();
                check(current.getJarEntry(name) != null, "Prior artifact entry retained: " + name);
                if (name.startsWith(BASE) && name.endsWith(".class")
                    && !changed.contains(name.substring(BASE.length(), name.length() - 6))) {
                    byte[] before = bytes(previous, name), after = bytes(current, name);
                    // Enclosing source edits move debug lines in existing nested classes.
                    String type = name.substring(BASE.length(), name.length() - 6);
                    int inner = type.indexOf('$');
                    boolean helper = inner >= 0 && changed.contains(type.substring(0, inner));
                    check(Arrays.equals(before, after) || helper && withoutDebug(before).equals(withoutDebug(after)),
                        "Other gameplay/physics/render/config bytecode retained: " + name);
                }
                if ((name.startsWith("assets/") || name.startsWith("data/")) && !entry.isDirectory())
                    check(Arrays.equals(bytes(previous, name), bytes(current, name)), "Prior resource bytes retained: " + name);
            }
            var network = read(current, "network/NetworkManager");
            ordered(calls(network, "init"), "DownedRagdollNetwork.register", "DeathCauseNetwork.register", "DownedViewNetwork.register", "KillerCardNetwork.register");
            check(network.fields.stream().anyMatch(f -> f.name.equals("PROTOCOL_VERSION") && "bf1-0.0.7".equals(f.value)), "Strict protocol updated with new messages");
            var registration = read(current, "network/KillerCardNetwork");
            check(invocations(registration, "register").stream().filter(c -> c.name.equals("registerMessage")).count() == 2, "Exactly two appended messages");
            var direction = registration.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof FieldInsnNode f && f.name.equals("PLAY_TO_CLIENT")).count();
            check(direction == 2, "Both appended snapshots explicitly S2C");
            var dispatcher = read(current, "server/room/runtime/RoomDeathEventDispatcher");
            check(calls(dispatcher, "dispatchPlayerDeath").contains("RoomDeathCauseService.withFireOwnership"), "Fire attribution still outermost scope");
            check(calls(dispatcher, "*").contains("KillerIntelService.withFatalSnapshot"), "Fatal equipment/health metadata captured before death listeners");
            var causes = read(current, "server/room/entry/RoomDeathCauseService");
            check(calls(causes, "resolveKiller").contains("KillerIntelService.capture"), "Accepted classification captures card/duel once");
            ordered(calls(causes, "publish"), "DeathCausePacket.<init>", "KillerIntelService.publish");
            for (String method : List.of("forget", "clear", "tick"))
                check(calls(causes, method).contains("KillerIntelService." + method), "Existing lifecycle carries metadata cleanup: " + method);
            var respawn = read(current, RESPAWN);
            check(constant(respawn, "onPlayerDeath", Long.MAX_VALUE), "Confirmed death is gated from native auto-open loop");
            ordered(calls(respawn, "onPlayerDeath"), "RunningRuntimeState.markPlayerDead", "DownedViewService.startDeath", "ManualDeathDeployService.begin");
            check(!calls(respawn, "onPlayerDeath").contains("RoomPlayerRespawnService.beginDeathmatchRespawnHold"), "Arena confirmed deaths use the same manual gate");
            ordered(calls(respawn, "openManualDeathDeploy"), "RoomPlayerRespawnService.enterDeployHoldingState", "RoomPlayerRespawnService.openDeployView");
            check(calls(respawn, "openManualDeathDeploy").contains("RunningRuntimeState.getDeployAutoStartOnReadyPlayers"), "Manual view disables auto deployment");
            check(calls(respawn, "handleDownedAction").contains("ManualDeathDeployService.request"), "Actual C2S action reaches manual gate");
            ordered(calls(respawn, "tick"), "ManualDeathDeployService.tick", "RoomPlayerRespawnService.tickDownedPlayers");
            for (String method : List.of("sendDownedStart", "sendDownedUpdate"))
                ordered(calls(respawn, method), "NetworkManager.sendToPlayer", "DownedDeployService.publish");
            check(calls(respawn, "downedDeployDelay").contains("RoomPlayerRespawnService.resolveDeployDelayTicksFromDowned"), "Timer shares original per-player calculation");
            var manual = read(current, "server/room/entry/ManualDeathDeployService");
            for (String guard : List.of("getRoomPlayers", "isPlayerDowned", "isPlayerPendingDowned", "getDeployViewActivePlayers",
                "getRespawnFinalizeTicks", "getRespawnCountdownStartTicks"))
                check(calls(manual, "valid").contains("RunningRuntimeState." + guard), "Server state guard: " + guard);
            check(constant(manual, "valid", Long.MAX_VALUE), "Manual request requires gated countdown marker");
            ordered(calls(manual, "request"), "ManualDeathDeployService.valid", "String.equals", "RoomPlayerRespawnService.openManualDeathDeploy", "RunningRuntimeState.getDeployViewActivePlayers");
            check(calls(manual, "begin").contains("RunningRuntimeState.getDoubledRespawnPending"), "Existing redeploy penalty consumed at deadline creation");
            var intel = read(current, "server/room/entry/KillerIntelService");
            check(calls(intel, "capture").contains("MatchDuelStats.record"), "Ledger belongs to accepted fatal snapshot");
            check(calls(intel, "snapshot").contains("PlayerLevelService.getEffectiveLevel"), "Uses actual mod level");
            check(calls(intel, "snapshot").contains("FatalHitMetadata.headshot"), "Exact hit metadata captured");
            check(calls(intel, "onMelee").contains("ItemStack.m_41777_"), "Melee item copied at attack");
            check(calls(intel, "snapshot").contains("KillWeaponResolver.resolve"), "Uses hit/launch weapon resolver");
            for (String type : List.of("server/room/entry/KillerIntelService", "server/room/entry/FatalHitMetadata", "server/room/entry/KillWeaponResolver")) {
                var methods = invocations(read(current, type), "*");
                check(methods.stream().noneMatch(c -> Set.of("setCanceled", "m_6469_", "m_21153_", "handleMarkedPlayerDeath", "handlePlayerKillSupportDrop").contains(c.name)),
                    "Metadata observer cannot cancel damage, heal, hurt or award kills: " + type);
            }
            var client = read(current, "client/room/ClientDownedState");
            check(calls(client, "render").equals(List.of("ClientDownedState.resolveSurrenderHintText", "ClientDownedState.resolveSurrenderHintColor",
                "ClientDownedState.resolveHelpHintText", "ClientDownedState.resolveHelpHintColor", "ClientKillerCardState.render")), "Original downed renderer fully handed to footer");
            ordered(calls(client, "tick"), "ClientDownedDeployState.tick", "ClientDownedState.isBeingRevivedLocked");
            check(calls(client, "tick").contains("ClientDownedDeployState.allowLongPress"), "Only not-ready phase retains long press");
            ordered(calls(client, "requestReadyDeployment"), "ClientDownedState.isBeingRevivedLocked", "RoomDownedActionPacket.surrender");
            check(calls(read(current, "client/room/DownedRagdollClientHooks"), "onDownedCleared").get(0).equals("ClientKillerCardState.hide"), "Card hides even when player is absent");
            check(calls(read(current, "client/room/ClientDownedCamera"), "accept").stream().filter(c -> c.equals("ClientKillerCardState.hide")).count() == 2,
                "PREPARE_REVIVE and END hide card");
            var chat = read(current, "client/room/ClientRoomChatState");
            check(calls(chat, "onTick").contains("ChatComponent.m_93769_"), "Native wrapping refreshes on room/width/scale changes");
            check(current.stream().noneMatch(e -> e.getName().startsWith("tests/") || e.getName().startsWith("tools/")), "Verification code not bundled");
        }
        System.out.println("PASS production card/deployment ordering and preserved scope: " + checks);
    }
}
