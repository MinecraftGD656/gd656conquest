package org.mods.gd656conquest.client.room;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.mixin.client.DeathPostChainAccessor;

/** Local presentation only: never changes entity glowing flags, teams or server state. */
public final class ClientDeathFilter {
    private static final DeathFilterTransition TRANSITION = new DeathFilterTransition();
    private static final ResourceLocation EFFECT = new ResourceLocation("gd656conquest", "shaders/post/death_world.json");
    public static final int KILLER_RED = 0xFF3030;
    private static PostChain chain;
    private static Uniform strengthUniform, contrastUniform;
    private static RenderTarget target;
    private static Object level;
    private static long lastFrameNanos;
    private static long outlineSession;
    private static long reviveSession;
    private static Entity outlineIdentity;
    private static int width, height;
    private static boolean failed;
    private static LevelRenderer deferredOutlines;
    private ClientDeathFilter() {}

    public static boolean deathActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null
            && !ClientDeathFlowState.shouldRenderDeployBoundaries()
            && (ClientDownedCamera.deathFilterActive() || ClientDownedState.isActive()
                || ClientDeathFlowState.isDeathObservationPhase());
    }

    public static boolean hideDamageOverlay() {
        return deathActive() || TRANSITION.strength() > .0001F;
    }

    /** Card and all its contents share the exact world-filter smoothstep, without its 96% cap. */
    public static float cardOpacity() { return Math.max(0, Math.min(1, TRANSITION.strength() / DeathFilterTransition.MAX_DESATURATION)); }

    public static void beginFrame() {
        Minecraft mc = Minecraft.getInstance();
        deferredOutlines = null;
        if (mc.level != level || mc.player == null) {
            releaseResources(); TRANSITION.reset(); lastFrameNanos = 0; reviveSession = 0; level = mc.level;
        }
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : Math.max(0, (now - lastFrameNanos) / 1e9F);
        lastFrameNanos = now;
        long revive = ClientDownedCamera.reviveVisualSession();
        if (revive != 0) {
            if (revive != reviveSession) TRANSITION.beginReviveRecovery();
            TRANSITION.recoverRevive(ClientDownedCamera.reviveVisualSeconds());
        } else TRANSITION.advance(deathActive(), dt);
        reviveSession = revive;
        long session = ClientDownedCamera.deathFilterSession();
        if (session != outlineSession) { outlineSession = session; outlineIdentity = null; }
        if (session == 0 && !deathActive()) outlineIdentity = null;
    }

    public static boolean isKiller(Entity entity) {
        if (!deathActive() || entity == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player || entity.isRemoved() || !entity.isAlive()
            || entity.level() != mc.level) return false;
        Entity killer = ClientDownedCamera.killerOutlineTarget();
        // A camera session owns its tracking validity, including a departed/dead killer.
        if (ClientDownedCamera.deathFilterActive()) {
            if (killer == null) return false;
            if (outlineIdentity == null) outlineIdentity = killer;
            return entity == killer && killer == outlineIdentity;
        }
        return entity.getId() == ClientDeathFlowState.deathFilterKillerId();
    }

    public static void compositeOutlines(LevelRenderer renderer) {
        if (TRANSITION.strength() > .0001F || deathActive()) deferredOutlines = renderer;
        else renderer.doEntityOutline();
    }

    /** Called after existing world post-processing, immediately before GUI depth clear. */
    public static void finishWorld(float partialTick) {
        // Camera events run after beginFrame: sample the same updated time for color and exposure.
        long revive = ClientDownedCamera.reviveVisualSession();
        if (revive != 0) {
            if (revive != reviveSession) { TRANSITION.beginReviveRecovery(); reviveSession = revive; }
            TRANSITION.recoverRevive(ClientDownedCamera.reviveVisualSeconds());
        }
        float pulse = ClientDownedCamera.revivePulseStrength();
        if (TRANSITION.strength() <= .0001F && deferredOutlines == null && pulse <= .0001F) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            // Compile/cache during the death view so success does not pay shader setup latency.
            if (deathActive()) ClientReviveEffect.prepare(mc);
            if (TRANSITION.strength() > .0001F && mc.level != null && ensureChain(mc)) {
                strengthUniform.set(TRANSITION.strength());
                contrastUniform.set(DeathFilterTransition.DEATH_CONTRAST);
                RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
                chain.process(partialTick);
            }
            mc.getMainRenderTarget().bindWrite(true);
            if (deferredOutlines != null) {
                deferredOutlines.doEntityOutline(); deferredOutlines = null;
                mc.getMainRenderTarget().bindWrite(true);
            }
            // World and world outlines brighten together; HUD/GUI have not been drawn yet.
            if (pulse > .0001F) ClientReviveEffect.render(mc, partialTick, pulse);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            if (deferredOutlines != null) {
                deferredOutlines.doEntityOutline(); deferredOutlines = null;
                mc.getMainRenderTarget().bindWrite(true);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
        }
    }

    private static boolean ensureChain(Minecraft mc) {
        if (failed) return false;
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null || target != main) {
                releaseDeathChain();
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, EFFECT); target = main;
                var pass = ((DeathPostChainAccessor)chain).gd656conquest$passes().get(0).getEffect();
                strengthUniform = pass.getUniform("DeathFilterStrength");
                contrastUniform = pass.getUniform("DeathContrast");
                if (strengthUniform == null || contrastUniform == null) throw new IllegalStateException("Death shader uniforms missing");
                width = height = -1;
            }
            if (width != main.width || height != main.height) {
                width = main.width; height = main.height; chain.resize(width, height);
            }
            return true;
        } catch (Exception error) {
            releaseDeathChain(); failed = true;
            LogUtils.getLogger().error("Unable to load death world filter; retry after resource reload", error);
            return false;
        }
    }

    public static void releaseResources() {
        ClientReviveEffect.releaseResources();
        releaseDeathChain();
    }
    private static void releaseDeathChain() {
        if (chain != null) chain.close();
        chain = null; target = null; strengthUniform = contrastUniform = null; failed = false;
    }
}
