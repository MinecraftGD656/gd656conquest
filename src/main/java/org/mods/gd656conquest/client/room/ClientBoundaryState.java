package org.mods.gd656conquest.client.room;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.mods.gd656conquest.client.audio.ClientAudioManager;
import org.mods.gd656conquest.mixin.client.DeathPostChainAccessor;

/** Local warning: one presentation for infantry, ground vehicles and aircraft. */
public final class ClientBoundaryState {
    private static final ResourceLocation EFFECT = new ResourceLocation("gd656conquest", "shaders/post/boundary_world.json");
    private static final BoundaryPresentation PRESENTATION = new BoundaryPresentation();
    private static boolean active, failed;
    private static int remainingTicks, kind, width = -1, height = -1;
    private static Object level;
    private static long frameNanos;
    private static PostChain chain;
    private static RenderTarget target;
    private static Uniform strength, suppression;
    private ClientBoundaryState() {}

    public static void accept(int ticks, int mountedKind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ticks <= 0) return;
        if (level != mc.level) { reset(); level = mc.level; }
        boolean entering = !active;
        remainingTicks = Math.max(0, ticks);
        kind = Math.max(0, Math.min(2, mountedKind));
        active = true;
        if (entering && PRESENTATION.tryVoice(System.nanoTime()))
            ClientAudioManager.getInstance().enqueuePlaySound("gd656conquest:voice.broadcast.leaving_area", 1F, 1F);
    }
    public static void setActive(boolean value) {
        // Legacy cleanup packets must also clear the new warning. Repeated updates never replay voice.
        if (!value) { active = false; remainingTicks = 0; }
    }
    public static boolean hidePlayerCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.player != null && mc.player.getVehicle() == null;
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.player == null) { reset(); level = mc.level; return; }
        if (!mc.player.isAlive() || ClientDeathFilter.deathActive()
            || ClientDeathFlowState.shouldRenderDeployBoundaries()) setActive(false);
        if (active && remainingTicks > 0) remainingTicks--;
    }
    public static void beginFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.player == null) { reset(); level = mc.level; }
        long now = System.nanoTime();
        float dt = frameNanos == 0 ? 0 : Math.max(0, (now - frameNanos) / 1e9F);
        frameNanos = now;
        PRESENTATION.advance(active, dt);
    }
    public static void renderWarning(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || remainingTicks <= 0 || mc.player == null) return;
        CombatAreaWarningRenderer.render(gui, "\u56de\u5230\u6218\u6597\u533a\u57df", remainingTicks);
    }
    public static boolean isActive() { return active; }
    public static boolean hasWorldEffect() { return PRESENTATION.strength() > .0001F; }
    public static void renderWorld(float partialTick) {
        if (PRESENTATION.strength() <= .0001F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ensureChain(mc)) return;
        try {
            strength.set(PRESENTATION.strength());
            suppression.set(ClientSuppressionState.opacity());
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
            chain.process(partialTick);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.defaultBlendFunc();
        }
    }
    private static boolean ensureChain(Minecraft mc) {
        if (failed) return false;
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null || target != main) {
                releaseResources();
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, EFFECT); target = main;
                strength = ((DeathPostChainAccessor)chain).gd656conquest$passes().get(0).getEffect().getUniform("BoundaryStrength");
                suppression = ((DeathPostChainAccessor)chain).gd656conquest$passes().get(0).getEffect().getUniform("SuppressionOpacity");
                if (strength == null || suppression == null) throw new IllegalStateException("BoundaryStrength uniform missing");
                width = height = -1;
            }
            if (width != main.width || height != main.height) {
                width = main.width; height = main.height; chain.resize(width, height);
            }
            return true;
        } catch (Exception error) {
            releaseResources(); failed = true;
            LogUtils.getLogger().error("Unable to load boundary filter; retry after resource reload", error);
            return false;
        }
    }
    public static void releaseResources() {
        if (chain != null) chain.close();
        chain = null; target = null; strength = null; suppression = null; failed = false;
    }
    public static void reset() {
        active = false; remainingTicks = 0; kind = 0; frameNanos = 0;
        PRESENTATION.reset(); releaseResources();
    }
}
