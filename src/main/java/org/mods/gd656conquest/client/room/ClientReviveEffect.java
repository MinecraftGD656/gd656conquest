package org.mods.gd656conquest.client.room;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.mods.gd656conquest.mixin.client.DeathPostChainAccessor;

/** Short world-only exposure/bloom, using a private chain rather than the shared shader slot. */
public final class ClientReviveEffect {
    private static final ResourceLocation EFFECT = new ResourceLocation("gd656conquest", "shaders/post/revive_world.json");
    private static PostChain chain;
    private static RenderTarget target;
    private static Uniform exposure, bloom;
    private static int width, height;
    private static boolean failed;
    private ClientReviveEffect() {}

    public static boolean prepare(Minecraft mc) {
        if (failed) return false;
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null || target != main) {
                releaseResources();
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, EFFECT); target = main;
                var composite = ((DeathPostChainAccessor)chain).gd656conquest$passes().get(3).getEffect();
                exposure = composite.getUniform("ExposureEV"); bloom = composite.getUniform("BloomGain");
                if (exposure == null || bloom == null) throw new IllegalStateException("Revive shader uniforms missing");
                width = height = -1;
            }
            if (width != main.width || height != main.height) {
                width = main.width; height = main.height;
                chain.resize(width, height);
                for (String name : new String[]{"revive_bright", "revive_blur"}) {
                    RenderTarget half = chain.getTempTarget(name);
                    half.resize(Math.max(1, width / 2), Math.max(1, height / 2), Minecraft.ON_OSX);
                    half.setFilterMode(9729); // Linear sampling when upscaling the half-size bloom.
                }
                // Vanilla's chain supplies a full-screen matrix to every pass. Half-size outputs
                // need their own projection, otherwise only a quarter of the target is covered.
                for (var pass : ((DeathPostChainAccessor)chain).gd656conquest$passes()) {
                    RenderTarget out = pass.outTarget;
                    pass.setOrthoMatrix(new Matrix4f().setOrtho(0, out.width, 0, out.height, .1F, 1000));
                }
            }
            return true;
        } catch (Exception error) {
            releaseResources(); failed = true;
            LogUtils.getLogger().error("Unable to load revive world pulse; retry after resource reload", error);
            return false;
        }
    }
    public static void render(Minecraft mc, float partialTick, float pulse) {
        if (pulse <= .0001F || !prepare(mc)) return;
        exposure.set(ReviveTransition.EXPOSURE_EV * pulse);
        bloom.set(ReviveTransition.BLOOM_GAIN * pulse);
        RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
        chain.process(partialTick);
    }
    public static void releaseResources() {
        if (chain != null) chain.close();
        chain = null; target = null; exposure = bloom = null; failed = false;
    }
}
