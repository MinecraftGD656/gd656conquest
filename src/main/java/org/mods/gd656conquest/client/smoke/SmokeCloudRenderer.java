package org.mods.gd656conquest.client.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.mods.gd656conquest.common.smoke.SmokeCloudInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds LR smoke deterministically and draws it with vanilla translucent render types.
 */
public final class SmokeCloudRenderer {
    private static final int SLOT_COUNT = 320;
    private static final int PARTICLE_LIFETIME_TICKS = 20;
    private static final float BASE_QUAD_SIZE = 0.55F;
    private static final ResourceLocation[] SMOKE_TEXTURES = buildTextures();

    public void render(RenderLevelStageEvent event) {
        if (event == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options == null) {
            return;
        }
        String dimensionId = minecraft.level.dimension().location().toString();
        List<SmokeCloudInstance> clouds = SmokeCloudStore.get(dimensionId);
        if (clouds.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        double viewDistance = Math.max(16.0D, minecraft.options.renderDistance().get() * 16.0D);
        long gameTick = minecraft.level.getGameTime();
        List<CloudDistance> orderedClouds = new ArrayList<>();
        for (SmokeCloudInstance cloud : clouds) {
            if (gameTick < cloud.startTick() || gameTick > cloud.endTick()) {
                continue;
            }
            double distance = cameraPos.distanceTo(new Vec3(cloud.x(), cloud.y(), cloud.z()));
            if (distance <= viewDistance) {
                orderedClouds.add(new CloudDistance(cloud, distance));
            }
        }
        orderedClouds.sort(Comparator.comparingDouble(CloudDistance::distance).reversed());
        if (orderedClouds.isEmpty()) {
            return;
        }

        List<Quad> quads = new ArrayList<>();
        for (CloudDistance cloud : orderedClouds) {
            appendCloudQuads(quads, cloud.cloud(), cloud.distance() / viewDistance, gameTick, cameraPos);
        }
        quads.sort(Comparator.comparingDouble(Quad::distanceToCamera).reversed());
        drawQuads(event, quads, cameraPos);
    }

    private void appendCloudQuads(
        List<Quad> quads,
        SmokeCloudInstance cloud,
        double normalizedDistance,
        long gameTick,
        Vec3 cameraPos
    ) {
        if (normalizedDistance > 0.85D) {
            appendDistantLayers(quads, cloud, normalizedDistance, cameraPos);
            return;
        }
        int visibleSlots = resolveSlotCount(normalizedDistance);
        // Sparse LOD tiers need an intentionally oversized quad to preserve cloud coverage.
        float size = BASE_QUAD_SIZE * 1.75F * (float) Math.sqrt((double) SLOT_COUNT / visibleSlots);
        for (int visibleIndex = 0; visibleIndex < visibleSlots; visibleIndex++) {
            int slot = visibleIndex * SLOT_COUNT / visibleSlots;
            // A hash-derived phase prevents low-slot LOD tiers from ageing in lockstep.
            int phase = (int) Math.floorMod(mix(cloud.seed() ^ ((long) slot * 0xD1342543DE82EF95L)), PARTICLE_LIFETIME_TICKS);
            long elapsed = gameTick - cloud.startTick() - phase;
            if (elapsed < 0L) {
                continue;
            }
            long cycle = Math.floorDiv(elapsed, PARTICLE_LIFETIME_TICKS);
            int age = (int) Math.floorMod(elapsed, PARTICLE_LIFETIME_TICKS);
            long particleSeed = mix(cloud.seed() ^ ((long) slot * 0x9E3779B97F4A7C15L) ^ cycle);
            double x = cloud.x() + triangle(particleSeed, cloud.radiusXZ());
            double y = cloud.y() + triangle(mix(particleSeed + 1L), cloud.radiusY());
            double z = cloud.z() + triangle(mix(particleSeed + 2L), cloud.radiusXZ());
            quads.add(new Quad(
                x,
                y,
                z,
                size,
                1.0F,
                SMOKE_TEXTURES[Math.min(SMOKE_TEXTURES.length - 1, age * SMOKE_TEXTURES.length / PARTICLE_LIFETIME_TICKS)],
                cameraPos.distanceToSqr(x, y, z)
            ));
        }
    }

    private void appendDistantLayers(
        List<Quad> quads,
        SmokeCloudInstance cloud,
        double normalizedDistance,
        Vec3 cameraPos
    ) {
        int layerCount = Math.max(1, 4 - (int) ((normalizedDistance - 0.85D) / 0.05D));
        for (int layer = 0; layer < layerCount; layer++) {
            float scale = 2.8F * (1.0F - layer * 0.18F);
            float alpha = (0.22F - layer * 0.035F) * (float) Mth.clamp(1.15D - normalizedDistance, 0.25D, 1.0D);
            quads.add(new Quad(
                cloud.x(),
                cloud.y() + layer * 0.08D,
                cloud.z(),
                BASE_QUAD_SIZE * scale * (float) cloud.radiusXZ(),
                alpha,
                SMOKE_TEXTURES[(layer * 4) % SMOKE_TEXTURES.length],
                cameraPos.distanceToSqr(cloud.x(), cloud.y(), cloud.z())
            ));
        }
    }

    private int resolveSlotCount(double normalizedDistance) {
        if (normalizedDistance < 0.10D) {
            return 320;
        }
        if (normalizedDistance < 0.30D) {
            return 128;
        }
        if (normalizedDistance < 0.60D) {
            return 32;
        }
        return 8;
    }

    private void drawQuads(RenderLevelStageEvent event, List<Quad> quads, Vec3 cameraPos) {
        if (quads.isEmpty()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        // The cloud center remains camera-relative below. Only the quad's axes
        // are rotated into world space so every particle faces the camera.
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(event.getCamera().rotation());
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(event.getCamera().rotation());
        Map<ResourceLocation, List<Quad>> byTexture = new LinkedHashMap<>();
        for (Quad quad : quads) {
            byTexture.computeIfAbsent(quad.texture(), ignored -> new ArrayList<>()).add(quad);
        }

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(buffer);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            for (Map.Entry<ResourceLocation, List<Quad>> entry : byTexture.entrySet()) {
                RenderType renderType = RenderType.entityTranslucent(entry.getKey());
                VertexConsumer vertices = buffers.getBuffer(renderType);
                for (Quad quad : entry.getValue()) {
                    appendQuad(vertices, matrix, quad, cameraPos, right, up);
                }
                buffers.endBatch(renderType);
            }
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private void appendQuad(
        VertexConsumer vertices,
        Matrix4f matrix,
        Quad quad,
        Vec3 cameraPos,
        Vector3f right,
        Vector3f up
    ) {
        float halfSize = quad.size() * 0.5F;
        float centerX = (float) (quad.x() - cameraPos.x);
        float centerY = (float) (quad.y() - cameraPos.y);
        float centerZ = (float) (quad.z() - cameraPos.z);
        appendVertex(vertices, matrix, centerX, centerY, centerZ, right, up, -halfSize, -halfSize, 0.0F, 0.0F, quad.alpha());
        appendVertex(vertices, matrix, centerX, centerY, centerZ, right, up, -halfSize, halfSize, 0.0F, 1.0F, quad.alpha());
        appendVertex(vertices, matrix, centerX, centerY, centerZ, right, up, halfSize, halfSize, 1.0F, 1.0F, quad.alpha());
        appendVertex(vertices, matrix, centerX, centerY, centerZ, right, up, halfSize, -halfSize, 1.0F, 0.0F, quad.alpha());
    }

    private void appendVertex(
        VertexConsumer vertices,
        Matrix4f matrix,
        float centerX,
        float centerY,
        float centerZ,
        Vector3f right,
        Vector3f up,
        float offsetRight,
        float offsetUp,
        float u,
        float v,
        float alpha
    ) {
        vertices.vertex(matrix,
                centerX + right.x * offsetRight + up.x * offsetUp,
                centerY + right.y * offsetRight + up.y * offsetUp,
                centerZ + right.z * offsetRight + up.z * offsetUp)
            .color(1.0F, 1.0F, 1.0F, alpha)
            .uv(u, v)
            // entityTranslucent uses NEW_ENTITY: overlay and normal are mandatory.
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(15728880)
            .normal(0.0F, 1.0F, 0.0F)
            .endVertex();
    }

    private double triangle(long seed, double scale) {
        return (unit(seed) - unit(mix(seed + 3L))) * scale;
    }

    private double unit(long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    private long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static ResourceLocation[] buildTextures() {
        ResourceLocation[] textures = new ResourceLocation[18];
        for (int index = 0; index < textures.length; index++) {
            textures[index] = new ResourceLocation("lrtactical", "textures/particle/smoke_" + (index + 1) + ".png");
        }
        return textures;
    }

    private record CloudDistance(SmokeCloudInstance cloud, double distance) {
    }

    private record Quad(
        double x,
        double y,
        double z,
        float size,
        float alpha,
        ResourceLocation texture,
        double distanceToCamera
    ) {
    }
}
