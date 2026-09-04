package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.common.block.ConquestBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class ClientCaptureOutlineController {
    static final double CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE = 50.0D;
    static final double CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE_SQR = CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE * CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE;
    private static final double DEBUG_SCAN_RADIUS = 25.0D;
    private static final double DEBUG_SCAN_RADIUS_SQR = DEBUG_SCAN_RADIUS * DEBUG_SCAN_RADIUS;
    private static final int DEBUG_SCAN_VERTICAL_RADIUS = 25;
    private static final long DEBUG_SCAN_INTERVAL_MS = 1_000L;
    private static final int CAPTURE_SUPPORT_SEARCH_MAX_DEPTH = 16;
    private static final float CAPTURE_BLOCK_FACADE_BOTTOM_ALPHA = 1.0F;
    private static final float CAPTURE_BLOCK_FACADE_TOP_ALPHA = 0.0F;
    private static final double CAPTURE_BLOCK_FACADE_HEIGHT = 0.5D;
    private static final double CAPTURE_BLOCK_FACADE_OUTSET = 0.00125D;

    private final Map<BlockPos, String> knownCaptureBlocks = new LinkedHashMap<>();
    private final Map<BlockPos, String> bakedCaptureBlocks = new LinkedHashMap<>();
    private boolean nearbyCacheDirty = true;
    private BlockPos lastPlayerBlockPos;
    private Set<BlockPos> cachedCaptureOutlineBlocks = Set.of();
    private List<OutlineFacade> cachedCaptureOutlineFacades = List.of();
    private boolean debugScanEnabled;
    private boolean usingScanData;
    private long lastDebugScanAtMs;

    void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        BlockPos playerPos = minecraft.player.blockPosition();
        Item heldCapturePointItem = resolveHeldCapturePointItem(minecraft);
        boolean shouldUseScanData = debugScanEnabled || heldCapturePointItem != null;
        if (shouldUseScanData) {
            long now = System.currentTimeMillis();
            if (!usingScanData || now - lastDebugScanAtMs >= DEBUG_SCAN_INTERVAL_MS) {
                scanNearbyCaptureBlocks(minecraft.level, playerPos);
                lastDebugScanAtMs = now;
            }
        } else if (usingScanData) {
            usingScanData = false;
            lastDebugScanAtMs = 0L;
            setKnownCaptureBlocks(bakedCaptureBlocks);
        }
        if (nearbyCacheDirty || !Objects.equals(lastPlayerBlockPos, playerPos)) {
            rebuildNearbyCaches(minecraft.level, playerPos);
            lastPlayerBlockPos = playerPos.immutable();
        }
        if (heldCapturePointItem != null) {
            for (BlockPos pos : cachedCaptureOutlineBlocks) {
                spawnBarrierMarkerParticle(minecraft.level, pos);
            }
        }
    }

    void onBlockStateUpdate(ClientLevel level, BlockPos pos, BlockState newState) {
    }

    void clear() {
        bakedCaptureBlocks.clear();
        nearbyCacheDirty = true;
        lastPlayerBlockPos = null;
        knownCaptureBlocks.clear();
        cachedCaptureOutlineBlocks = Set.of();
        cachedCaptureOutlineFacades = List.of();
        debugScanEnabled = false;
        usingScanData = false;
        lastDebugScanAtMs = 0L;
    }

    void applyBakedCaptureBlocks(Map<BlockPos, String> blocks) {
        bakedCaptureBlocks.clear();
        if (blocks != null && !blocks.isEmpty()) {
            for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                    bakedCaptureBlocks.put(entry.getKey().immutable(), entry.getValue());
                }
            }
        }
        if (!usingScanData) {
            setKnownCaptureBlocks(bakedCaptureBlocks);
        }
    }

    void setDebugScanEnabled(boolean enabled) {
        debugScanEnabled = enabled;
        if (!enabled && usingScanData) {
            usingScanData = false;
            lastDebugScanAtMs = 0L;
            setKnownCaptureBlocks(bakedCaptureBlocks);
        } else if (enabled) {
            nearbyCacheDirty = true;
        }
    }

    int debugTotalOutlineCacheSize() {
        return knownCaptureBlocks.size()
            + cachedCaptureOutlineBlocks.size()
            + cachedCaptureOutlineFacades.size();
    }

    void renderCaptureBlockOutlineLines(RenderLevelStageEvent event, TeamToken selfTeam) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null || cachedCaptureOutlineFacades.isEmpty()) {
            return;
        }
        Map<String, String> perspectiveByCode = resolveCapturePerspectiveByCode(selfTeam);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            Matrix4f matrix = poseStack.last().pose();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (OutlineFacade facade : cachedCaptureOutlineFacades) {
                if (!isFacadeWithinRenderDistance(facade, camera)) {
                    continue;
                }
                float[] color = resolveBoundaryLineColor(facade.code(), perspectiveByCode, selfTeam);
                appendCaptureFacadeQuad(buffer, matrix, facade, color);
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            poseStack.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private void setKnownCaptureBlocks(Map<BlockPos, String> source) {
        knownCaptureBlocks.clear();
        if (source != null && !source.isEmpty()) {
            for (Map.Entry<BlockPos, String> entry : source.entrySet()) {
                BlockPos pos = entry.getKey();
                String code = entry.getValue();
                if (pos == null || code == null) {
                    continue;
                }
                knownCaptureBlocks.put(pos.immutable(), code);
            }
        }
        nearbyCacheDirty = true;
    }

    private void scanNearbyCaptureBlocks(ClientLevel level, BlockPos center) {
        if (level == null || center == null) {
            setKnownCaptureBlocks(Map.of());
            usingScanData = true;
            return;
        }
        Map<BlockPos, String> scanned = new LinkedHashMap<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int minX = Mth.floor(center.getX() - DEBUG_SCAN_RADIUS);
        int maxX = Mth.floor(center.getX() + DEBUG_SCAN_RADIUS);
        int minZ = Mth.floor(center.getZ() - DEBUG_SCAN_RADIUS);
        int maxZ = Mth.floor(center.getZ() + DEBUG_SCAN_RADIUS);
        int minY = center.getY() - DEBUG_SCAN_VERTICAL_RADIUS;
        int maxY = center.getY() + DEBUG_SCAN_VERTICAL_RADIUS;
        for (int x = minX; x <= maxX; x++) {
            double dx = x + 0.5D - (center.getX() + 0.5D);
            for (int z = minZ; z <= maxZ; z++) {
                double dz = z + 0.5D - (center.getZ() + 0.5D);
                if (dx * dx + dz * dz > DEBUG_SCAN_RADIUS_SQR) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);
                    Optional<String> code = resolveIndexedCaptureCode(level.getBlockState(mutable));
                    if (code.isPresent()) {
                        scanned.put(mutable.immutable(), code.get());
                    }
                }
            }
        }
        usingScanData = true;
        setKnownCaptureBlocks(scanned);
    }

    private void rebuildNearbyCaches(Level level, BlockPos playerPos) {
        if (level == null || playerPos == null || knownCaptureBlocks.isEmpty()) {
            cachedCaptureOutlineBlocks = Set.of();
            cachedCaptureOutlineFacades = List.of();
            nearbyCacheDirty = false;
            return;
        }
        Set<BlockPos> nearbyBlocks = new HashSet<>();
        Vec3 playerCenter = Vec3.atCenterOf(playerPos);
        for (BlockPos pos : knownCaptureBlocks.keySet()) {
            if (distanceSqr(playerCenter, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE_SQR) {
                nearbyBlocks.add(pos);
            }
        }
        if (nearbyBlocks.isEmpty()) {
            cachedCaptureOutlineBlocks = Set.of();
            cachedCaptureOutlineFacades = List.of();
            nearbyCacheDirty = false;
            return;
        }
        cachedCaptureOutlineBlocks = Set.copyOf(nearbyBlocks);
        cachedCaptureOutlineFacades = buildCaptureOutlineFacades(level, cachedCaptureOutlineBlocks);
        nearbyCacheDirty = false;
    }

    private Optional<String> resolveIndexedCaptureCode(BlockState state) {
        if (ConquestBlocks.isCapturePoint(state)) {
            return ConquestBlocks.resolveCaptureCode(state);
        }
        if (ConquestBlocks.isOutOfBoundsBlock(state)) {
            return Optional.of("");
        }
        return Optional.empty();
    }

    private Map<String, String> resolveCapturePerspectiveByCode(TeamToken selfTeam) {
        Map<String, String> perspectives = new HashMap<>();
        String payload = ClientHudDebugState.getCaptureIcons();
        if (payload == null || payload.isBlank() || "-".equals(payload)) {
            return perspectives;
        }
        String[] rows = payload.split(";");
        for (String row : rows) {
            String[] cols = row.trim().split(",");
            if (cols.length < 8 || !"C".equalsIgnoreCase(cols[0])) {
                continue;
            }
            TeamToken owner = TeamToken.from(cols[5]);
            String perspective = toPerspectiveToken(owner, selfTeam);
            if (!"NEUTRAL".equals(perspective)) {
                perspectives.put(cols[1], perspective);
            }
        }
        return perspectives;
    }

    private List<OutlineFacade> buildCaptureOutlineFacades(Level level, Set<BlockPos> captureBlocks) {
        if (level == null || captureBlocks == null || captureBlocks.isEmpty()) {
            return List.of();
        }
        Map<Long, BlockPos> lowestByColumn = new HashMap<>();
        for (BlockPos pos : captureBlocks) {
            if (ConquestBlocks.isOutOfBoundsBlock(level.getBlockState(pos))) {
                continue;
            }
            long key = packColumnKey(pos.getX(), pos.getZ());
            BlockPos existing = lowestByColumn.get(key);
            if (existing == null || pos.getY() < existing.getY()) {
                lowestByColumn.put(key, pos.immutable());
            }
        }
        if (lowestByColumn.isEmpty()) {
            return List.of();
        }
        Map<Long, OutlineCell> cells = new HashMap<>();
        for (BlockPos pos : lowestByColumn.values()) {
            OutlineRenderSupport support = resolveCaptureRenderSupport(level, pos);
            if (support == null) {
                continue;
            }
            String code = knownCaptureBlocks.getOrDefault(pos, "");
            cells.put(packColumnKey(pos.getX(), pos.getZ()), new OutlineCell(pos.getX(), pos.getZ(), support.baseY, code, support.outwardOffset));
        }
        if (cells.isEmpty()) {
            return List.of();
        }
        List<OutlineFacade> facades = new ArrayList<>();
        for (OutlineCell cell : cells.values()) {
            tryAddFacade(facades, cell, cells, 1, 0);
            tryAddFacade(facades, cell, cells, -1, 0);
            tryAddFacade(facades, cell, cells, 0, 1);
            tryAddFacade(facades, cell, cells, 0, -1);
        }
        return facades.isEmpty() ? List.of() : List.copyOf(facades);
    }

    private OutlineRenderSupport resolveCaptureRenderSupport(Level level, BlockPos capturePos) {
        int minY = level.getMinBuildHeight();
        int checkY = capturePos.getY() - 1;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(capturePos.getX(), checkY, capturePos.getZ());
        for (int depth = 0; depth <= CAPTURE_SUPPORT_SEARCH_MAX_DEPTH && checkY >= minY; depth++) {
            mutable.setY(checkY);
            BlockState supportState = level.getBlockState(mutable);
            if (isBottomSlab(supportState)) {
                return new OutlineRenderSupport(checkY + 0.5D, true);
            }
            if (isTopSlab(supportState)) {
                return new OutlineRenderSupport(checkY + 1.0D, false);
            }
            if (isClosedTopTrapdoor(supportState)) {
                return new OutlineRenderSupport(checkY + 1.0D, true);
            }
            if (isStairBlock(supportState)) {
                return new OutlineRenderSupport(checkY + 1.0D, shouldUseOutwardOffset(supportState));
            }
            if (supportState.isCollisionShapeFullBlock(level, mutable) || supportState.isFaceSturdy(level, mutable, Direction.UP)) {
                return new OutlineRenderSupport(checkY + 1.0D, shouldUseOutwardOffset(supportState));
            }
            checkY--;
        }
        return null;
    }

    private void tryAddFacade(List<OutlineFacade> facades, OutlineCell cell, Map<Long, OutlineCell> cells, int dx, int dz) {
        OutlineCell neighbor = cells.get(packColumnKey(cell.x + dx, cell.z + dz));
        if (neighbor != null && sameCaptureCode(cell, neighbor)) {
            return;
        }
        double minY = cell.y;
        double maxY = cell.y + CAPTURE_BLOCK_FACADE_HEIGHT;
        double offsetSign = cell.outwardOffset ? 1.0D : -1.0D;
        double outsetX = offsetSign * dx * CAPTURE_BLOCK_FACADE_OUTSET;
        double outsetZ = offsetSign * dz * CAPTURE_BLOCK_FACADE_OUTSET;
        if (dx == 1) {
            facades.add(new OutlineFacade(cell.x + 1.0D + outsetX, minY, cell.z + outsetZ, cell.x + 1.0D + outsetX, maxY, cell.z + 1.0D + outsetZ, cell.code));
            return;
        }
        if (dx == -1) {
            facades.add(new OutlineFacade(cell.x + outsetX, minY, cell.z + outsetZ, cell.x + outsetX, maxY, cell.z + 1.0D + outsetZ, cell.code));
            return;
        }
        if (dz == 1) {
            facades.add(new OutlineFacade(cell.x + outsetX, minY, cell.z + 1.0D + outsetZ, cell.x + 1.0D + outsetX, maxY, cell.z + 1.0D + outsetZ, cell.code));
            return;
        }
        facades.add(new OutlineFacade(cell.x + outsetX, minY, cell.z + outsetZ, cell.x + 1.0D + outsetX, maxY, cell.z + outsetZ, cell.code));
    }

    private long packColumnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private boolean isBottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private boolean isTopSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    private boolean isClosedTopTrapdoor(BlockState state) {
        if (!(state.getBlock() instanceof TrapDoorBlock) || !state.hasProperty(TrapDoorBlock.HALF) || !state.hasProperty(TrapDoorBlock.OPEN)) {
            return false;
        }
        return state.getValue(TrapDoorBlock.HALF) == Half.TOP && !state.getValue(TrapDoorBlock.OPEN);
    }

    private boolean isStairBlock(BlockState state) {
        return state.getBlock() instanceof StairBlock;
    }

    private boolean shouldUseOutwardOffset(BlockState state) {
        return isBottomSlab(state) || state.getBlock() instanceof StairBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    private boolean sameCaptureCode(OutlineCell a, OutlineCell b) {
        return a != null && b != null && a.code.equalsIgnoreCase(b.code);
    }

    private void appendCaptureFacadeQuad(BufferBuilder buffer, Matrix4f matrix, OutlineFacade facade, float[] color) {
        double ax = facade.ax;
        double az = facade.az;
        double bx = facade.bx;
        double bz = facade.bz;
        double minY = Math.min(facade.ay, facade.by);
        double maxY = Math.max(facade.ay, facade.by);
        if (Math.abs(ax - bx) < 1.0E-6D && Math.abs(az - bz) < 1.0E-6D) {
            return;
        }
        buffer.vertex(matrix, (float) ax, (float) minY, (float) az).color(color[0], color[1], color[2], CAPTURE_BLOCK_FACADE_BOTTOM_ALPHA).endVertex();
        buffer.vertex(matrix, (float) bx, (float) minY, (float) bz).color(color[0], color[1], color[2], CAPTURE_BLOCK_FACADE_BOTTOM_ALPHA).endVertex();
        buffer.vertex(matrix, (float) bx, (float) maxY, (float) bz).color(color[0], color[1], color[2], CAPTURE_BLOCK_FACADE_TOP_ALPHA).endVertex();
        buffer.vertex(matrix, (float) ax, (float) maxY, (float) az).color(color[0], color[1], color[2], CAPTURE_BLOCK_FACADE_TOP_ALPHA).endVertex();
    }

    private void spawnBarrierMarkerParticle(Level level, BlockPos pos) {
        BlockState captureState = level.getBlockState(pos);
        if (!ConquestBlocks.isCapturePoint(captureState) && !ConquestBlocks.isOutOfBoundsBlock(captureState)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean holdingMatching = false;
        if (ConquestBlocks.isOutOfBoundsBlock(captureState)) {
            holdingMatching = ConquestBlocks.isOutOfBoundsBlockItem(minecraft.player.getMainHandItem().getItem()) ||
                ConquestBlocks.isOutOfBoundsBlockItem(minecraft.player.getOffhandItem().getItem());
        } else {
            String code = knownCaptureBlocks.getOrDefault(pos, "");
            if (!code.isBlank()) {
                Item matchItem = ConquestBlocks.getCaptureBlockItem(code);
                if (matchItem != null) {
                    holdingMatching = minecraft.player.getMainHandItem().is(matchItem) ||
                        minecraft.player.getOffhandItem().is(matchItem);
                }
            }
        }
        if (!holdingMatching) {
            return;
        }
        spawnAlwaysVisible(level, new BlockParticleOption(ParticleTypes.BLOCK_MARKER, ConquestBlocks.resolveCaptureMarkerState(captureState)), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private Item resolveHeldCapturePointItem(Minecraft minecraft) {
        if (minecraft.player == null) {
            return null;
        }
        Item main = minecraft.player.getMainHandItem().getItem();
        if (ConquestBlocks.isCapturePointItem(main) || ConquestBlocks.isOutOfBoundsBlockItem(main)) {
            return main;
        }
        Item off = minecraft.player.getOffhandItem().getItem();
        if (ConquestBlocks.isCapturePointItem(off) || ConquestBlocks.isOutOfBoundsBlockItem(off)) {
            return off;
        }
        return null;
    }

    private void spawnAlwaysVisible(Level level, ParticleOptions options, double x, double y, double z) {
        if (level instanceof ClientLevel clientLevel) {
            clientLevel.addAlwaysVisibleParticle(options, x, y, z, 0.0D, 0.0D, 0.0D);
            return;
        }
        level.addParticle(options, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    private float[] resolveBoundaryLineColor(String code, Map<String, String> perspectiveByCode, TeamToken selfTeam) {
        if (selfTeam == TeamToken.NEUTRAL) {
            return new float[]{0.95F, 0.95F, 0.95F};
        }
        String perspective = perspectiveByCode.getOrDefault(code, "NEUTRAL");
        if ("ALLY".equalsIgnoreCase(perspective)) {
            return new float[]{0.25F, 0.45F, 1.0F};
        }
        if ("ENEMY".equalsIgnoreCase(perspective)) {
            return new float[]{1.0F, 0.2F, 0.2F};
        }
        return new float[]{0.95F, 0.95F, 0.95F};
    }

    private boolean isFacadeWithinRenderDistance(OutlineFacade facade, Vec3 camera) {
        if (facade == null || camera == null) {
            return false;
        }
        double centerX = (facade.ax + facade.bx) * 0.5D;
        double centerY = (facade.ay + facade.by) * 0.5D;
        double centerZ = (facade.az + facade.bz) * 0.5D;
        return distanceSqr(camera, centerX, centerY, centerZ) <= CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE_SQR;
    }

    private double distanceSqr(Vec3 from, double x, double y, double z) {
        double dx = from.x - x;
        double dy = from.y - y;
        double dz = from.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private String toPerspectiveToken(TeamToken token, TeamToken selfTeam) {
        if (token == TeamToken.NEUTRAL || selfTeam == TeamToken.NEUTRAL) {
            return "NEUTRAL";
        }
        return token == selfTeam ? "ALLY" : "ENEMY";
    }

    private static final class OutlineCell {
        private final int x;
        private final int z;
        private final double y;
        private final String code;
        private final boolean outwardOffset;

        private OutlineCell(int x, int z, double y, String code, boolean outwardOffset) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.code = code == null ? "" : code;
            this.outwardOffset = outwardOffset;
        }
    }

    private static final class OutlineRenderSupport {
        private final double baseY;
        private final boolean outwardOffset;

        private OutlineRenderSupport(double baseY, boolean outwardOffset) {
            this.baseY = baseY;
            this.outwardOffset = outwardOffset;
        }
    }

    private static final class OutlineFacade {
        private final double ax;
        private final double ay;
        private final double az;
        private final double bx;
        private final double by;
        private final double bz;
        private final String code;

        private OutlineFacade(double ax, double ay, double az, double bx, double by, double bz, String code) {
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.code = code == null ? "" : code;
        }

        private String code() {
            return code;
        }
    }
}
