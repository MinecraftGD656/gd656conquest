package tests;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

/** Applies current reobfuscated Mixins plus their generated refmap to actual Forge SRG classes. */
public final class DeathCauseMixinCheck {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("migration-death-causes.mixins.json");
        IMixinTransformer transformer = ((IsolationMixinService) MixinService.getService()).factory().factory().createTransformer();
        for (var target : Map.of("net.minecraft.server.commands.KillCommand", "commandKill",
            "net.minecraft.server.level.ServerPlayer", "dispatchPlayerDeath").entrySet()) {
            byte[] original;
            try (InputStream input = DeathCauseMixinCheck.class.getClassLoader().getResourceAsStream(target.getKey().replace('.', '/') + ".class")) {
                original = input.readAllBytes();
            }
            byte[] transformed = transformer.transformClass(environment, target.getKey(), original);
            if (Arrays.equals(original, transformed)) throw new AssertionError("Mixin did not apply: " + target.getKey());
            ClassNode node = new ClassNode();
            new ClassReader(transformed).accept(node, 0);
            long calls = node.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode call && call.name.equals(target.getValue())
                    && call.owner.startsWith("org/mods/gd656conquest/")).count();
            if (calls != 1) throw new AssertionError("Expected one fatal hook: " + target.getKey() + " => " + calls);
            System.out.println("PASS actual Forge SRG Mixin + refmap: " + target.getKey());
        }
    }
}
