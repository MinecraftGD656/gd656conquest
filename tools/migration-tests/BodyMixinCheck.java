package tests;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

/** Applies the current JAR's body Mixins to the unmodified, production Ragdollified JAR. */
public final class BodyMixinCheck {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        MixinEnvironment environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.valueOf(args[0].equals("ABSENT") ? "CLIENT" : args[0]));
        Mixins.addConfiguration("migration-body.mixins.json");
        IMixinTransformer transformer = ((IsolationMixinService) MixinService.getService()).factory().factory().createTransformer();
        if (args[0].equals("ABSENT")) {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "diagnostic/NoRagdoll", null, "java/lang/Object", null);
            writer.visitEnd();
            byte[] original = writer.toByteArray();
            if (!Arrays.equals(original, transformer.transformClass(environment, "diagnostic.NoRagdoll", original))) {
                throw new AssertionError("Optional body Mixins changed an unrelated class");
            }
            System.out.println("PASS optional body Mixin configuration without Ragdollified");
            return;
        }
        List<String> targets = new ArrayList<>();
        for (String packet : List.of("Spawn", "Stream", "State", "StreamOwner", "Impulse", "Sever")) {
            targets.add("com.raiiiden.ragdollified.network.Ragdoll" + packet + "Packet");
        }
        targets.addAll(List.of("com.raiiiden.ragdollified.ServerRagdollHitTracker",
            "com.raiiiden.ragdollified.PhysicsHooks", "com.raiiiden.ragdollified.server.ServerRagdollSyncManager"));
        if (args[0].equals("CLIENT")) {
            targets.addAll(List.of("com.raiiiden.ragdollified.client.ClientRagdollManager",
                "com.raiiiden.ragdollified.client.RenderPlayback", "com.raiiiden.ragdollified.client.RagdollHitTracker",
                "com.raiiiden.ragdollified.client.ClientRagdollRenderer", "com.raiiiden.ragdollified.client.ClientRagdoll"));
        }
        int changed = 0;
        for (String target : targets) {
            byte[] original;
            try (InputStream input = BodyMixinCheck.class.getClassLoader().getResourceAsStream(target.replace('.', '/') + ".class")) {
                if (input == null) throw new AssertionError("Missing real dependency class: " + target);
                original = input.readAllBytes();
            }
            byte[] transformed = transformer.transformClass(environment, target, original);
            if (target.endsWith(".ClientRagdoll")) {
                if (!Arrays.equals(original, transformed)) throw new AssertionError("Native physics was modified");
            } else {
                if (Arrays.equals(original, transformed)) throw new AssertionError("Mixin did not apply: " + target);
                changed++;
            }
            ClassNode node = new ClassNode();
            new ClassReader(transformed).accept(node, 0);
            if (target.endsWith("Packet")) {
                if (!node.interfaces.contains("org/mods/gd656conquest/server/compat/ragdoll/RagdollPacketScope")) {
                    throw new AssertionError("Missing generation interface: " + target);
                }
                for (var method : node.methods) if (method.name.equals("<init>") &&
                    Arrays.stream(method.instructions.toArray()).noneMatch(i -> i instanceof MethodInsnNode call && call.name.contains("captureScope"))) {
                    throw new AssertionError("Constructor lacks generation capture: " + target + method.desc);
                }
            }
            Path output = Path.of(args[1]).resolve(target.replace('.', '/') + ".class");
            Files.createDirectories(output.getParent());
            Files.write(output, transformed);
        }
        System.out.println("PASS real " + args[0] + " body Mixin targets: " + changed);
    }
}
