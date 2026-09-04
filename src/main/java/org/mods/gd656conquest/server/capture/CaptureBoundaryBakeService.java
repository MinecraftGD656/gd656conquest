package org.mods.gd656conquest.server.capture;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.common.capture.CaptureBoundaryBakeFile;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.CaptureBoundaryBakeChunkPacket;
import org.mods.gd656conquest.network.packet.CaptureBoundaryBakeFailurePacket;
import org.mods.gd656conquest.network.packet.CaptureBoundaryBakeMetaPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomConquestConfig;
import org.mods.gd656conquest.server.room.model.RoomDeathmatchConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomGunmasterConfig;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomRushConfig;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryPolygonSupport;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

public final class CaptureBoundaryBakeService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GD656Conquest/Bake");
    private static final Map<MinecraftServer, CaptureBoundaryBakeService> INSTANCES = new WeakHashMap<>();
    private static final int NETWORK_BATCH_BYTES = 16 * 1024;

    private final MinecraftServer server;
    private ScanTask activeScan;

    private CaptureBoundaryBakeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static CaptureBoundaryBakeService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, CaptureBoundaryBakeService::new);
        }
    }

    public static void forget(MinecraftServer server) {
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public synchronized StartScanResult startScan(CommandSourceStack source, int mode, String rawRoomId, double radius, double minY, double maxY) {
        if (activeScan != null) {
            return StartScanResult.failure("当前已有 bake 扫描任务在运行");
        }
        RoomDefinition room = resolveRoom(rawRoomId);
        if (room == null) {
            return StartScanResult.failure("未找到房间: " + rawRoomId);
        }
        ResourceKey<Level> dimensionKey = resolveRoomDimensionKey(room);
        if (dimensionKey == null) {
            return StartScanResult.failure("房间维度解析失败: " + displayRoomId(room.roomId()));
        }
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return StartScanResult.failure("目标房间维度当前不可用: " + dimensionKey.location());
        }
        int minBlockY;
        int maxBlockY;
        List<ChunkPos> chunks;
        Map<Long, YRange> perChunkYRanges = null;
        if (mode == 2) {
            int chunkRadius = Math.max(0, Mth.floor(radius));
            int down = Math.max(0, Mth.floor(Math.abs(minY)));
            int up = Math.max(0, Mth.floor(Math.abs(maxY)));
            List<RoomPosition> centers = resolveCapturePointCenters(room);
            if (centers.isEmpty()) {
                return StartScanResult.failure("模式2未找到任何据点中心点配置");
            }
            LinkedHashMap<Long, YRange> yRanges = new LinkedHashMap<>();
            LinkedHashSet<ChunkPos> chunkSet = new LinkedHashSet<>();
            for (RoomPosition center : centers) {
                if (center == null) {
                    continue;
                }
                int centerChunkX = Mth.floorDiv(center.x(), 16);
                int centerChunkZ = Mth.floorDiv(center.z(), 16);
                int centerBlockY = Mth.floor(center.y());
                int centerMinY = centerBlockY - down;
                int centerMaxY = centerBlockY + up;
                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                        ChunkPos chunkPos = new ChunkPos(centerChunkX + dx, centerChunkZ + dz);
                        chunkSet.add(chunkPos);
                        long key = (((long) chunkPos.x) << 32) | (chunkPos.z & 0xFFFFFFFFL);
                        YRange existing = yRanges.get(key);
                        if (existing == null) {
                            yRanges.put(key, new YRange(centerMinY, centerMaxY));
                        } else {
                            yRanges.put(key, existing.merge(centerMinY, centerMaxY));
                        }
                    }
                }
            }
            chunks = new ArrayList<>(chunkSet);
            if (chunks.isEmpty()) {
                return StartScanResult.failure("模式2未解析到任何可扫描区块");
            }
            perChunkYRanges = Map.copyOf(yRanges);
            minBlockY = perChunkYRanges.values().stream().mapToInt(YRange::minY).min().orElse(0);
            maxBlockY = perChunkYRanges.values().stream().mapToInt(YRange::maxY).max().orElse(0);
        } else {
            minBlockY = Mth.ceil(Math.min(minY, maxY) - 0.5D);
            maxBlockY = Mth.floor(Math.max(minY, maxY) - 0.5D);
            if (maxBlockY < minBlockY) {
                return StartScanResult.failure("扫描高度范围无效");
            }
            chunks = mode == 0
                ? resolveBoundaryChunks(room)
                : resolveRadiusChunks(room, radius);
            if (chunks.isEmpty()) {
                return StartScanResult.failure(mode == 0
                    ? "未从战斗区域边界解析到可扫描区块: " + describeBoundaryState(room)
                    : "半径模式未解析到任何可扫描区块");
            }
        }
        int nextVersion = nextRoomVersion(room.roomId());
        activeScan = new ScanTask(source, room, level, minBlockY, maxBlockY, nextVersion, chunks, perChunkYRanges);
        LOGGER.info("开始据点边界烘焙扫描: room={}, mode={}, chunks={}, y={}..{}", displayRoomId(room.roomId()), mode, chunks.size(), minBlockY, maxBlockY);
        return StartScanResult.success("开始扫描 " + displayRoomId(room.roomId()) + "，目标区块 " + chunks.size() + " 个，版本 " + nextVersion);
    }

    public synchronized List<BakeFileSummary> listFiles() {
        Path directory = ServerDataPaths.getCaptureBakeDirectory(server);
        if (Files.notExists(directory)) {
            return List.of();
        }
        try {
            List<BakeFileSummary> summaries = new ArrayList<>();
            for (Path file : Files.list(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gdcb"))
                .sorted()
                .toList()) {
                try {
                    CaptureBoundaryBakeFile bakeFile = CaptureBoundaryBakeFile.read(file);
                    summaries.add(new BakeFileSummary(
                        displayRoomId(bakeFile.roomId()),
                        Files.size(file),
                        bakeFile.dataVersion(),
                        bakeFile.totalBlocks(),
                        bakeFile.summaryLine()
                    ));
                } catch (Exception exception) {
                    summaries.add(new BakeFileSummary(file.getFileName().toString(), safeFileSize(file), -1, -1, "损坏文件"));
                }
            }
            return summaries;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 bake 目录", exception);
        }
    }

    public synchronized CopyResult copy(String fromRoomId, String toRoomId) {
        RoomDefinition fromRoom = resolveRoom(fromRoomId);
        RoomDefinition toRoom = resolveRoom(toRoomId);
        if (fromRoom == null) {
            return CopyResult.failure("未找到来源房间: " + fromRoomId);
        }
        if (toRoom == null) {
            return CopyResult.failure("未找到目标房间: " + toRoomId);
        }
        Path sourceFile = ServerDataPaths.getCaptureBakeFile(server, fromRoom.roomId());
        if (Files.notExists(sourceFile)) {
            return CopyResult.failure("来源房间没有可复制的 bake 文件");
        }
        CaptureBoundaryBakeFile source = CaptureBoundaryBakeFile.read(sourceFile);
        int nextVersion = nextRoomVersion(toRoom.roomId());
        CaptureBoundaryBakeFile copied = CaptureBoundaryBakeFile.create(toRoom.roomId(), nextVersion, source.captureCounts(), source.chunks());
        Path targetFile = ServerDataPaths.getCaptureBakeFile(server, toRoom.roomId());
        CaptureBoundaryBakeFile.writeAtomically(targetFile, copied);
        return CopyResult.success("已复制到 " + displayRoomId(toRoom.roomId()) + "，版本 " + nextVersion);
    }

    public synchronized PushResult push(String rawRoomId) {
        RoomDefinition room = resolveRoom(rawRoomId);
        if (room == null) {
            return PushResult.failure("未找到房间: " + rawRoomId);
        }
        Path file = ServerDataPaths.getCaptureBakeFile(server, room.roomId());
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isPlayerInRoomDimension(player, room)) {
                sendMeta(player, room.roomId(), file);
                count++;
            }
        }
        return PushResult.success("已通知 " + count + " 名玩家检查 " + displayRoomId(room.roomId()) + " 的 bake 数据");
    }

    public void onServerTick(long gameTick) {
        ScanTask task;
        synchronized (this) {
            task = activeScan;
        }
        if (task == null) {
            return;
        }
        try {
            if (task.shouldLogProgress(gameTick)) {
                LOGGER.info("bake扫描进度 room={} {}/{} chunks, blocks={}", displayRoomId(task.room.roomId()), task.scannedChunkCount, task.totalChunkCount(), task.totalBlocks);
            }
            boolean finished = task.processOneChunk();
            if (finished) {
                FinishSummary summary = task.finish();
                synchronized (this) {
                    activeScan = null;
                }
                LOGGER.info("bake扫描完成 room={}, chunks={}, blocks={}, version={}, file={}", displayRoomId(summary.roomId), summary.chunkCount, summary.totalBlocks, summary.version, summary.fileSizeBytes);
            }
        } catch (Exception exception) {
            LOGGER.error("bake扫描失败", exception);
            task.fail("扫描过程中发生异常: " + exception.getMessage());
            synchronized (this) {
                activeScan = null;
            }
        }
    }

    public void onPlayerLogin(ServerPlayer player) {
        sendMetaForCurrentRoom(player);
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        sendMetaForCurrentRoom(player);
    }

    public void handleDownloadRequest(ServerPlayer player, String rawRoomId) {
        if (player == null) {
            return;
        }
        String roomId = canonicalRoomId(rawRoomId);
        RoomDefinition room = resolveRoom(roomId);
        if (room == null) {
            NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeFailurePacket(roomId, "服务器不存在该房间的 bake 配置"));
            return;
        }
        if (!isPlayerInRoomDimension(player, room)) {
            NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeFailurePacket(roomId, "当前不在目标房间维度中，已拒绝下载"));
            return;
        }
        Path file = ServerDataPaths.getCaptureBakeFile(server, roomId);
        if (Files.notExists(file)) {
            NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeFailurePacket(roomId, "服务器当前没有该房间的 bake 数据"));
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            CaptureBoundaryBakeFile bakeFile = CaptureBoundaryBakeFile.read(bytes);
            int batchCount = Math.max(1, (bytes.length + NETWORK_BATCH_BYTES - 1) / NETWORK_BATCH_BYTES);
            for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
                int start = batchIndex * NETWORK_BATCH_BYTES;
                int end = Math.min(bytes.length, start + NETWORK_BATCH_BYTES);
                byte[] payload = java.util.Arrays.copyOfRange(bytes, start, end);
                NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeChunkPacket(
                    bakeFile.roomId(),
                    bakeFile.dataVersion(),
                    bakeFile.payloadHash(),
                    batchIndex,
                    batchCount,
                    payload
                ));
            }
        } catch (Exception exception) {
            LOGGER.error("bake下载下发失败 room={} player={} reason={}",
                displayRoomId(roomId),
                player.getGameProfile().getName(),
                exception.getMessage(),
                exception
            );
            NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeFailurePacket(roomId, "下载烘焙数据失败，请检查网络连接或重新进入房间"));
        }
    }

    private void sendMetaForCurrentRoom(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Optional<RoomDefinition> room = resolveCurrentRoom(player);
        room.ifPresent(value -> sendMeta(player, value.roomId(), ServerDataPaths.getCaptureBakeFile(server, value.roomId())));
    }

    private void sendMeta(ServerPlayer player, String roomId, Path file) {
        if (player == null || roomId == null || roomId.isBlank()) {
            return;
        }
        if (Files.notExists(file)) {
            NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeMetaPacket(roomId, 0, "", false));
            return;
        }
        try {
            CaptureBoundaryBakeFile bakeFile = CaptureBoundaryBakeFile.read(file);
            NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeMetaPacket(bakeFile.roomId(), bakeFile.dataVersion(), bakeFile.payloadHash(), true));
        } catch (Exception exception) {
            NetworkManager.sendToPlayer(player, new CaptureBoundaryBakeMetaPacket(roomId, 0, "", false));
        }
    }

    private RoomDefinition resolveRoom(String rawRoomId) {
        String canonical = canonicalRoomId(rawRoomId);
        return ConquestDataManager.of(server).roomDefinitions().findDefinition(canonical).orElse(null);
    }

    private Optional<RoomDefinition> resolveCurrentRoom(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return Optional.empty();
        }
        ResourceLocation location = player.serverLevel().dimension().location();
        if (location != null && "gd656conquest".equalsIgnoreCase(location.getNamespace())) {
            String roomId = canonicalRoomId(location.getPath());
            return ConquestDataManager.of(server).roomDefinitions().findDefinition(roomId);
        }
        return Optional.empty();
    }

    private ResourceKey<Level> resolveRoomDimensionKey(RoomDefinition room) {
        if (room == null || room.roomId() == null || room.roomId().isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse("gd656conquest:" + canonicalRoomId(room.roomId()));
        if (location == null) {
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, location);
    }

    private boolean isPlayerInRoomDimension(ServerPlayer player, RoomDefinition room) {
        if (player == null || player.serverLevel() == null) {
            return false;
        }
        ResourceKey<Level> dimensionKey = resolveRoomDimensionKey(room);
        return dimensionKey != null && dimensionKey.equals(player.serverLevel().dimension());
    }

    private int nextRoomVersion(String roomId) {
        Path file = ServerDataPaths.getCaptureBakeFile(server, roomId);
        if (Files.notExists(file)) {
            return 1;
        }
        try {
            return Math.max(1, CaptureBoundaryBakeFile.read(file).dataVersion() + 1);
        } catch (Exception exception) {
            return 1;
        }
    }

    private List<ChunkPos> resolveBoundaryChunks(RoomDefinition room) {
        List<List<RoomPosition>> polygons = resolveBattlePolygons(room);
        if (polygons.isEmpty()) {
            return List.of();
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (List<RoomPosition> polygon : polygons) {
            for (RoomPosition position : polygon) {
                minX = Math.min(minX, position.x());
                maxX = Math.max(maxX, position.x());
                minZ = Math.min(minZ, position.z());
                maxZ = Math.max(maxZ, position.z());
            }
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)) {
            return List.of();
        }
        int minChunkX = Mth.floor(minX) >> 4;
        int maxChunkX = Mth.floor(maxX) >> 4;
        int minChunkZ = Mth.floor(minZ) >> 4;
        int maxChunkZ = Mth.floor(maxZ) >> 4;
        List<ChunkPos> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (chunkHasAnyBlockCenterInPolygons(chunkX, chunkZ, polygons)) {
                    result.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
        return result;
    }

    private List<ChunkPos> resolveRadiusChunks(RoomDefinition room, double radius) {
        RoomViewPosition center = resolveRadiusCenter(room);
        if (center == null || !Double.isFinite(radius) || radius <= 0.0D) {
            return List.of();
        }
        int minChunkX = Mth.floor(center.x() - radius) >> 4;
        int maxChunkX = Mth.floor(center.x() + radius) >> 4;
        int minChunkZ = Mth.floor(center.z() - radius) >> 4;
        int maxChunkZ = Mth.floor(center.z() + radius) >> 4;
        double radiusSqr = radius * radius;
        List<ChunkPos> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (chunkHasAnyBlockCenterInCircle(chunkX, chunkZ, center.x(), center.z(), radiusSqr)) {
                    result.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
        return result;
    }

    private List<List<RoomPosition>> resolveBattlePolygons(RoomDefinition room) {
        if (room == null || room.gameplayId() == null) {
            return List.of();
        }
        List<List<RoomPosition>> result = new ArrayList<>();
        if (RoomGameplayId.CONQUEST.equals(room.gameplayId())) {
            RoomConquestConfig conquest = room.conquestConfig();
            addIfValid(result, conquest.sharedBoundary());
            addIfValid(result, conquest.campABoundary());
            addIfValid(result, conquest.campBBoundary());
        } else if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            RoomBreakthroughConfig breakthrough = room.breakthroughConfig();
            addIfValid(result, breakthrough.campABaseBoundary());
            addIfValid(result, breakthrough.campBBaseBoundary());
            for (int stage = 1; stage <= 6; stage++) {
                addIfValid(result, breakthrough.stageBoundary(stage));
            }
        } else if (RoomGameplayId.RUSH.equals(room.gameplayId())) {
            RoomRushConfig rush = room.rushConfig();
            addIfValid(result, rush.campABaseBoundary());
            addIfValid(result, rush.campBBaseBoundary());
            for (int stage = 1; stage <= 6; stage++) {
                addIfValid(result, rush.stageBoundary(stage));
            }
        } else if (RoomGameplayId.GUNMASTER.equals(room.gameplayId())) {
            RoomGunmasterConfig gunmaster = room.gunmasterConfig();
            if (gunmaster.isSoloGrouping()) {
                addIfValid(result, gunmaster.soloBoundary());
            } else {
                addIfValid(result, gunmaster.campABoundary());
                addIfValid(result, gunmaster.campBBoundary());
            }
        } else {
            RoomDeathmatchConfig deathmatch = room.deathmatchConfig();
            addIfValid(result, deathmatch.campABoundary());
            addIfValid(result, deathmatch.campBBoundary());
        }
        return result;
    }

    private List<RoomPosition> resolveCapturePointCenters(RoomDefinition room) {
        if (room == null || room.gameplayId() == null) {
            return List.of();
        }
        List<RoomPosition> result = new ArrayList<>();
        if (RoomGameplayId.CONQUEST.equals(room.gameplayId())) {
            RoomConquestConfig conquest = room.conquestConfig();
            if (conquest != null && conquest.captureConfig() != null && conquest.captureConfig().points() != null) {
                for (RoomCapturePointDefinition definition : conquest.captureConfig().points().values()) {
                    if (definition == null || definition.centerPosition() == null) {
                        continue;
                    }
                    result.add(definition.centerPosition());
                }
            }
            return result;
        }
        if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            RoomBreakthroughConfig breakthrough = room.breakthroughConfig();
            if (breakthrough != null && breakthrough.points() != null) {
                for (RoomCapturePointDefinition definition : breakthrough.points().values()) {
                    if (definition == null || definition.centerPosition() == null) {
                        continue;
                    }
                    result.add(definition.centerPosition());
                }
            }
            return result;
        }
        return List.of();
    }

    private String describeBoundaryState(RoomDefinition room) {
        if (room == null) {
            return "room=null";
        }
        if (RoomGameplayId.CONQUEST.equals(room.gameplayId())) {
            RoomConquestConfig conquest = room.conquestConfig();
            return "gameplay=conquest"
                + ", shared=" + sizeOf(conquest.sharedBoundary())
                + ", campA=" + sizeOf(conquest.campABoundary())
                + ", campB=" + sizeOf(conquest.campBBoundary());
        }
        if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            RoomBreakthroughConfig breakthrough = room.breakthroughConfig();
            return "gameplay=breakthrough"
                + ", campABase=" + sizeOf(breakthrough.campABaseBoundary())
                + ", campBBase=" + sizeOf(breakthrough.campBBaseBoundary());
        }
        if (RoomGameplayId.RUSH.equals(room.gameplayId())) {
            RoomRushConfig rush = room.rushConfig();
            return "gameplay=rush"
                + ", campABase=" + sizeOf(rush.campABaseBoundary())
                + ", campBBase=" + sizeOf(rush.campBBaseBoundary());
        }
        if (RoomGameplayId.GUNMASTER.equals(room.gameplayId())) {
            RoomGunmasterConfig gunmaster = room.gunmasterConfig();
            return "gameplay=gunmaster"
                + ", solo=" + sizeOf(gunmaster.soloBoundary())
                + ", campA=" + sizeOf(gunmaster.campABoundary())
                + ", campB=" + sizeOf(gunmaster.campBBoundary());
        }
        RoomDeathmatchConfig deathmatch = room.deathmatchConfig();
        return "gameplay=" + room.gameplayId().value()
            + ", campA=" + sizeOf(deathmatch.campABoundary())
            + ", campB=" + sizeOf(deathmatch.campBBoundary());
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private RoomViewPosition resolveRadiusCenter(RoomDefinition room) {
        if (room == null || room.commonConfig() == null) {
            return null;
        }
        RoomViewPosition configured = room.commonConfig().matchLoadViewPosition();
        if (configured != null) {
            return configured;
        }
        RoomViewPosition conquestDeploy = room.conquestConfig().deployViewPosition();
        if (conquestDeploy != null) {
            return conquestDeploy;
        }
        RoomPosition queueSpawn = room.commonConfig().queueSpawn();
        if (queueSpawn != null) {
            return new RoomViewPosition(queueSpawn.x(), queueSpawn.y(), queueSpawn.z(), 0.0F, 0.0F);
        }
        return null;
    }

    private void addIfValid(List<List<RoomPosition>> target, List<RoomPosition> polygon) {
        if (polygon != null && polygon.size() >= 3) {
            target.add(List.copyOf(polygon));
        }
    }

    private boolean chunkHasAnyBlockCenterInPolygons(int chunkX, int chunkZ, List<List<RoomPosition>> polygons) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int dx = 0; dx < 16; dx++) {
            double x = minX + dx + 0.5D;
            for (int dz = 0; dz < 16; dz++) {
                double z = minZ + dz + 0.5D;
                for (List<RoomPosition> polygon : polygons) {
                    if (RoomBoundaryPolygonSupport.isPointInsideBoundary(x, z, polygon)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean chunkHasAnyBlockCenterInCircle(int chunkX, int chunkZ, double centerX, double centerZ, double radiusSqr) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int dx = 0; dx < 16; dx++) {
            double x = minX + dx + 0.5D;
            for (int dz = 0; dz < 16; dz++) {
                double z = minZ + dz + 0.5D;
                double distX = x - centerX;
                double distZ = z - centerZ;
                if (distX * distX + distZ * distZ <= radiusSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String canonicalRoomId(String rawRoomId) {
        return rawRoomId == null ? "" : rawRoomId.trim().toLowerCase(Locale.ROOT);
    }

    public static String displayRoomId(String rawRoomId) {
        String canonical = canonicalRoomId(rawRoomId);
        if (canonical.startsWith("room_") && canonical.length() == 7) {
            return "Room_" + canonical.substring(5);
        }
        return canonical;
    }

    private static long safeFileSize(Path file) {
        try {
            return Files.exists(file) ? Files.size(file) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    public record StartScanResult(boolean success, String message) {
        public static StartScanResult success(String message) { return new StartScanResult(true, message); }
        public static StartScanResult failure(String message) { return new StartScanResult(false, message); }
    }

    public record CopyResult(boolean success, String message) {
        public static CopyResult success(String message) { return new CopyResult(true, message); }
        public static CopyResult failure(String message) { return new CopyResult(false, message); }
    }

    public record PushResult(boolean success, String message) {
        public static PushResult success(String message) { return new PushResult(true, message); }
        public static PushResult failure(String message) { return new PushResult(false, message); }
    }

    public record BakeFileSummary(String roomId, long fileSizeBytes, int version, int totalBlocks, String countsLine) {
    }

    private record FinishSummary(String roomId, long fileSizeBytes, int totalBlocks, int chunkCount, int version) {
    }

    private final class ScanTask {
        private final CommandSourceStack source;
        private final RoomDefinition room;
        private final ServerLevel level;
        private final int minBlockY;
        private final int maxBlockY;
        private final int version;
        private final Queue<ChunkPos> pendingChunks;
        private final Map<Long, YRange> perChunkYRanges;
        private final long startedAtMs;
        private long lastProgressGameTick = Long.MIN_VALUE;
        private int scannedChunkCount;
        private int totalBlocks;
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<Long, List<CaptureBoundaryBakeFile.BlockEntry>> blocksByChunk = new LinkedHashMap<>();

        private ScanTask(
            CommandSourceStack source,
            RoomDefinition room,
            ServerLevel level,
            int minBlockY,
            int maxBlockY,
            int version,
            List<ChunkPos> chunks,
            Map<Long, YRange> perChunkYRanges
        ) {
            this.source = source;
            this.room = room;
            this.level = level;
            this.minBlockY = minBlockY;
            this.maxBlockY = maxBlockY;
            this.version = version;
            this.pendingChunks = new ArrayDeque<>(chunks);
            this.perChunkYRanges = perChunkYRanges;
            this.startedAtMs = System.currentTimeMillis();
        }

        private int totalChunkCount() {
            return scannedChunkCount + pendingChunks.size();
        }

        private boolean shouldLogProgress(long gameTick) {
            if (lastProgressGameTick == Long.MIN_VALUE || gameTick - lastProgressGameTick >= 20L) {
                lastProgressGameTick = gameTick;
                return true;
            }
            return false;
        }

        private boolean processOneChunk() {
            ChunkPos chunkPos = pendingChunks.poll();
            if (chunkPos == null) {
                return true;
            }
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            scanChunk(chunk, chunkPos);
            scannedChunkCount++;
            return pendingChunks.isEmpty();
        }

        private void scanChunk(LevelChunk chunk, ChunkPos chunkPos) {
            if (chunk == null) {
                return;
            }
            int effectiveMinY = minBlockY;
            int effectiveMaxY = maxBlockY;
            if (perChunkYRanges != null && chunkPos != null) {
                long key = (((long) chunkPos.x) << 32) | (chunkPos.z & 0xFFFFFFFFL);
                YRange range = perChunkYRanges.get(key);
                if (range != null) {
                    effectiveMinY = range.minY;
                    effectiveMaxY = range.maxY;
                }
            }
            LevelChunkSection[] sections = chunk.getSections();
            int minBuildHeight = chunk.getMinBuildHeight();
            List<CaptureBoundaryBakeFile.BlockEntry> entries = new ArrayList<>();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int sectionBaseY = minBuildHeight + sectionIndex * 16;
                int localMinY = Math.max(0, effectiveMinY - sectionBaseY);
                int localMaxY = Math.min(15, effectiveMaxY - sectionBaseY);
                if (localMaxY < localMinY) {
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int localY = localMinY; localY <= localMaxY; localY++) {
                            String code = ConquestBlocks.resolveCaptureCode(section.getBlockState(x, localY, z)).orElse("");
                            if (code.isBlank()) {
                                continue;
                            }
                            int codeId = CaptureBoundaryBakeFile.captureCodeToId(code);
                            if (codeId < 0) {
                                continue;
                            }
                            int blockY = sectionBaseY + localY;
                            entries.add(new CaptureBoundaryBakeFile.BlockEntry(x, blockY, z, codeId));
                            totalBlocks++;
                            counts.merge(code.toUpperCase(Locale.ROOT), 1, Integer::sum);
                        }
                    }
                }
            }
            if (!entries.isEmpty()) {
                long key = (((long) chunk.getPos().x) << 32) | (chunk.getPos().z & 0xFFFFFFFFL);
                blocksByChunk.put(key, entries);
            }
        }

        private FinishSummary finish() {
            List<CaptureBoundaryBakeFile.ChunkEntry> chunks = new ArrayList<>();
            for (Map.Entry<Long, List<CaptureBoundaryBakeFile.BlockEntry>> entry : blocksByChunk.entrySet()) {
                int chunkX = (int) (entry.getKey() >> 32);
                int chunkZ = (int) (long) entry.getKey();
                chunks.add(new CaptureBoundaryBakeFile.ChunkEntry(chunkX, chunkZ, entry.getValue()));
            }
            CaptureBoundaryBakeFile bakeFile = CaptureBoundaryBakeFile.create(room.roomId(), version, counts, chunks);
            Path target = ServerDataPaths.getCaptureBakeFile(server, room.roomId());
            CaptureBoundaryBakeFile.writeAtomically(target, bakeFile);
            long elapsedMs = Math.max(1L, System.currentTimeMillis() - startedAtMs);
            long fileSize = safeFileSize(target);
            CommandFeedbacks.line(source, net.minecraft.network.chat.Component.literal(
                "bake完成: file=" + fileSize
                    + "B, blocks=" + bakeFile.totalBlocks()
                    + ", counts=" + bakeFile.summaryLine()
                    + ", chunks=" + chunks.size()
                    + ", elapsed=" + elapsedMs + "ms"
                    + ", version=" + version
            ));
            return new FinishSummary(room.roomId(), fileSize, bakeFile.totalBlocks(), chunks.size(), version);
        }

        private void fail(String message) {
            CommandFeedbacks.failure(source, message);
        }
    }

    private record YRange(int minY, int maxY) {
        private YRange merge(int minY, int maxY) {
            return new YRange(Math.min(this.minY, minY), Math.max(this.maxY, maxY));
        }
    }
}
