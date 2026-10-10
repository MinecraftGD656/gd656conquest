package tests;

import java.io.InputStream;
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

/** Applies actual accessors/refmaps and optional TACZ hooks to unmodified dependency classes. */
public final class DownedViewMixinCheck {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(args[0].equals("SERVER") ? MixinEnvironment.Side.SERVER : MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("migration-downed-view.mixins.json");
        IMixinTransformer transformer = ((IsolationMixinService) MixinService.getService()).factory().factory().createTransformer();
        if (args[0].equals("ABSENT")) {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "diagnostic/NoTacz", null, "java/lang/Object", null);
            writer.visitEnd(); byte[] original = writer.toByteArray();
            if (!Arrays.equals(original, transformer.transformClass(environment, "diagnostic.NoTacz", original)))
                throw new AssertionError("Optional TACZ hooks affected unrelated class");
            System.out.println("PASS optional prone hooks without TACZ"); return;
        }
        List<String> targets = args[0].equals("SERVER")
            ? List.of("com.tacz.guns.entity.shooter.LivingEntityCrawl")
            : List.of("net.minecraft.client.Camera", "net.minecraft.client.MouseHandler",
                "com.tacz.guns.entity.shooter.LivingEntityCrawl", "com.tacz.guns.client.gameplay.LocalPlayerCrawl",
                "com.tacz.guns.client.input.CrawlKey");
        for (String target : targets) {
            byte[] original;
            try (InputStream input = DownedViewMixinCheck.class.getClassLoader().getResourceAsStream(target.replace('.', '/') + ".class")) {
                if (input == null) throw new AssertionError("Missing actual target " + target);
                original = input.readAllBytes();
            }
            byte[] transformed = transformer.transformClass(environment, target, original);
            if (Arrays.equals(original, transformed)) throw new AssertionError("Hook not applied: " + target);
            ClassNode node = new ClassNode(); new ClassReader(transformed).accept(node, 0);
            if (target.equals("net.minecraft.client.Camera")) {
                for (String accessor : List.of("gd656conquest$setPosition", "gd656conquest$setRotation", "gd656conquest$setDetached"))
                    if (node.methods.stream().noneMatch(m -> m.name.equals(accessor))) throw new AssertionError("Missing accessor " + accessor);
            } else if (node.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .noneMatch(i -> i instanceof MethodInsnNode call && call.owner.startsWith("org/mods/gd656conquest/")))
                throw new AssertionError("No GD hook in actual target " + target);
            System.out.println("PASS actual " + args[0] + " view/prone Mixin + refmap: " + target);
        }
    }
}
