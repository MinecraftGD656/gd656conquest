package org.mods.gd656conquest.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.common.block.ConquestBlocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CaptureBoundaryRenderer {
    public void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        Set<Long> footprint = collectCaptureFootprint(level, minecraft.player.blockPosition(), 32, 32);
        if (footprint.isEmpty()) {
            return;
        }
        Map<Long, Integer> yByColumn = new HashMap<>();
        for (long packed : footprint) {
            int x = (int) (packed >> 32);
            int z = (int) packed;
            int y = resolveSurfaceY(level, x, z);
            if (y >= level.getMinBuildHeight()) {
                yByColumn.put(packed, y + 1);
            }
        }
        if (yByColumn.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float[] color = resolveBoundaryColor();
        Frustum frustum = event.getFrustum();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float h = 0.25F;
        for (Map.Entry<Long, Integer> entry : yByColumn.entrySet()) {
            long packed = entry.getKey();
            int x = (int) (packed >> 32);
            int z = (int) packed;
            int y = entry.getValue();
            if (!frustum.isVisible(new net.minecraft.world.phys.AABB(x, y, z, x + 1, y + h, z + 1))) {
                continue;
            }
            if (!yByColumn.containsKey(pack(x + 1, z))) {
                addQuad(buffer, x + 1, y, z, x + 1, y + h, z + 1, camera, color);
            }
            if (!yByColumn.containsKey(pack(x - 1, z))) {
                addQuad(buffer, x, y, z + 1, x, y + h, z, camera, color);
            }
            if (!yByColumn.containsKey(pack(x, z + 1))) {
                addQuad(buffer, x + 1, y, z + 1, x, y + h, z + 1, camera, color);
            }
            if (!yByColumn.containsKey(pack(x, z - 1))) {
                addQuad(buffer, x, y, z, x + 1, y + h, z, camera, color);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private Set<Long> collectCaptureFootprint(Level level, BlockPos center, int horizontal, int vertical) {
        Set<Long> columns = new HashSet<>();
        for (int x = center.getX() - horizontal; x <= center.getX() + horizontal; x++) {
            for (int z = center.getZ() - horizontal; z <= center.getZ() + horizontal; z++) {
                for (int y = center.getY() - vertical; y <= center.getY() + vertical; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (ConquestBlocks.isCapturePoint(level.getBlockState(pos))) {
                        columns.add(pack(x, z));
                        break;
                    }
                }
            }
        }
        return columns;
    }

    private int resolveSurfaceY(Level level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int min = level.getMinBuildHeight();
        y = Mth.clamp(y, min, level.getMaxBuildHeight() - 2);
        for (int i = 0; i < 16; i++) {
            BlockState ground = level.getBlockState(new BlockPos(x, y - i, z));
            BlockState air = level.getBlockState(new BlockPos(x, y - i + 1, z));
            if (ground.isCollisionShapeFullBlock(level, new BlockPos(x, y - i, z))
                    && (air.isAir() || ConquestBlocks.isCapturePoint(air))) {
                return y - i;
            }
        }
        return min - 1;
    }

    private void addQuad(BufferBuilder buffer, float x0, float y0, float z0, float x1, float y1, float z1, Vec3 camera, float[] color) {
        float r = color[0];
        float g = color[1];
        float b = color[2];
        float aBottom = 0.65F;
        float aTop = 0.15F;
        buffer.vertex(x0 - (float) camera.x, y0 - (float) camera.y, z0 - (float) camera.z).color(r, g, b, aBottom).endVertex();
        buffer.vertex(x1 - (float) camera.x, y0 - (float) camera.y, z1 - (float) camera.z).color(r, g, b, aBottom).endVertex();
        buffer.vertex(x1 - (float) camera.x, y1 - (float) camera.y, z1 - (float) camera.z).color(r, g, b, aTop).endVertex();
        buffer.vertex(x0 - (float) camera.x, y1 - (float) camera.y, z0 - (float) camera.z).color(r, g, b, aTop).endVertex();
    }

    private float[] resolveBoundaryColor() {
        String status = ClientHudDebugState.getCaptureStatus();
        String selfTeam = ClientHudDebugState.getSelfTeam();
        boolean ownerCampA = status.contains("Owner:CampA");
        boolean ownerCampB = status.contains("Owner:CampB");
        boolean selfCampA = selfTeam != null && selfTeam.contains("CampA");
        boolean selfCampB = selfTeam != null && selfTeam.contains("CampB");
        if ((ownerCampA && selfCampA) || (ownerCampB && selfCampB)) {
            return new float[]{0.25F, 0.45F, 1.0F};
        }
        if ((ownerCampA && selfCampB) || (ownerCampB && selfCampA)) {
            return new float[]{1.0F, 0.2F, 0.2F};
        }
        return new float[]{0.95F, 0.95F, 0.95F};
    }

    private long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
