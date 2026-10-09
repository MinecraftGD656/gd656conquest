package org.mods.gd656conquest.server.player.state;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.mods.gd656conquest.server.room.runtime.RoomDimensionControlService;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class HubDimensionMigrationService {
    private static final Set<String> COPYABLE_TOP_LEVEL_DIRECTORIES = Set.of(
        "region",
        "entities",
        "poi",
        "generated"
    );

    private final MinecraftServer server;

    public HubDimensionMigrationService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public MigrationResult migrateOverworldToHub() {
        ResourceKey<Level> hubKey = HubDimensionService.hubDimension();
        validatePlayers();
        Path sourcePath = resolveOverworldDataPath();
        Path targetPath = resolveDimensionSavePath(hubKey);
        if (!containsRegionChunkData(sourcePath)) {
            throw new IllegalStateException("当前 overworld 不包含可迁移的区块数据（region/*.mca）");
        }
        prepareLevelForReplacement(hubKey);
        if (!RoomDimensionControlService.unloadRoomDimension(server, hubKey)) {
            throw new IllegalStateException("卸载主城维度失败: " + hubKey.location());
        }
        try {
            replaceHubByOverworld(sourcePath, targetPath);
        } finally {
            reloadAndVerify(hubKey);
            prepareLevelAfterReplacement(hubKey);
        }
        return new MigrationResult(
            sourcePath,
            targetPath,
            "已将当前 overworld 维度数据复制到主城维度 gd656conquest:hub；原 overworld 保留不变"
        );
    }

    private void validatePlayers() {
        List<String> blockingPlayers = new ArrayList<>();
        ServerLevel hubLevel = server.getLevel(HubDimensionService.hubDimension());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }
            if (player.serverLevel() == server.overworld()) {
                blockingPlayers.add(player.getGameProfile().getName() + "@overworld");
                continue;
            }
            if (hubLevel != null && player.serverLevel() == hubLevel) {
                blockingPlayers.add(player.getGameProfile().getName() + "@hub");
            }
        }
        if (!blockingPlayers.isEmpty()) {
            throw new IllegalStateException("迁移前请先确保 overworld 与主城维度无人停留: " + String.join(", ", blockingPlayers));
        }
    }

    private Path resolveOverworldDataPath() {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private Path resolveDimensionSavePath(ResourceKey<Level> dimensionKey) {
        return server.getWorldPath(LevelResource.ROOT)
            .resolve("dimensions")
            .resolve(dimensionKey.location().getNamespace())
            .resolve(dimensionKey.location().getPath());
    }

    private void prepareLevelForReplacement(ResourceKey<Level> dimensionKey) {
        flushServerData();
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return;
        }
        pumpChunkUnloads(level, 24, false);
        pumpChunkUnloads(level, 12, true);
        flushServerData();
    }

    private void prepareLevelAfterReplacement(ResourceKey<Level> dimensionKey) {
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return;
        }
        flushServerData();
        pumpChunkUnloads(level, 16, true);
        pumpChunkUnloads(level, 8, false);
    }

    private void reloadAndVerify(ResourceKey<Level> dimensionKey) {
        if (!RoomDimensionControlService.reloadRoomDimension(server, dimensionKey)) {
            throw new IllegalStateException("重载主城维度失败: " + dimensionKey.location());
        }
        if (server.getLevel(dimensionKey) == null) {
            throw new IllegalStateException("重载后未找到主城维度: " + dimensionKey.location());
        }
    }

    private void flushServerData() {
        try {
            var saveEverything = MinecraftServer.class.getMethod("saveEverything", boolean.class, boolean.class, boolean.class);
            saveEverything.invoke(server, true, false, true);
            return;
        } catch (Exception ignored) {
        }
        try {
            var saveEverything = MinecraftServer.class.getMethod("saveEverything", boolean.class, boolean.class, boolean.class, boolean.class);
            saveEverything.invoke(server, true, false, true, false);
            return;
        } catch (Exception ignored) {
        }
        try {
            var saveAllChunks = MinecraftServer.class.getMethod("saveAllChunks", boolean.class, boolean.class, boolean.class);
            saveAllChunks.invoke(server, true, false, true);
        } catch (Exception ignored) {
        }
    }

    private void pumpChunkUnloads(ServerLevel level, int cycles, boolean forceProcessAllChunks) {
        Object chunkSource = level.getChunkSource();
        if (chunkSource == null) {
            return;
        }
        BooleanSupplier keepTicking = () -> true;
        for (int i = 0; i < cycles; i++) {
            try {
                var tickMethod = chunkSource.getClass().getMethod("tick", BooleanSupplier.class, boolean.class);
                tickMethod.invoke(chunkSource, keepTicking, forceProcessAllChunks);
            } catch (Exception ignored) {
                break;
            }
        }
    }

    private boolean containsRegionChunkData(Path root) {
        Path regionDir = root.resolve("region");
        if (!Files.isDirectory(regionDir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(regionDir)) {
            return stream.anyMatch(path -> {
                String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(path) && name.endsWith(".mca");
            });
        } catch (IOException exception) {
            return false;
        }
    }

    private void replaceHubByOverworld(Path sourcePath, Path targetPath) {
        deleteIfExists(targetPath);
        ensureDirectory(targetPath);
        try (Stream<Path> stream = Files.walk(sourcePath)) {
            stream.forEach(source -> {
                try {
                    Path relative = sourcePath.relativize(source);
                    if (relative.getNameCount() == 0) {
                        return;
                    }
                    if (!shouldCopyOverworldEntry(relative)) {
                        return;
                    }
                    Path target = targetPath.resolve(relative.toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException exception) {
                    if (isLockedByOtherProcess(exception)) {
                        return;
                    }
                    throw new IllegalStateException("主城维度迁移复制失败: " + source, exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("读取 overworld 维度数据失败: " + sourcePath, exception);
        }
    }

    private boolean shouldCopyOverworldEntry(Path relative) {
        if (relative == null || relative.getNameCount() == 0) {
            return false;
        }
        String rootName = normalizePathName(relative.getName(0));
        return COPYABLE_TOP_LEVEL_DIRECTORIES.contains(rootName);
    }

    private String normalizePathName(Path path) {
        return path == null || path.getFileName() == null
            ? ""
            : path.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private void deleteIfExists(Path targetPath) {
        if (targetPath == null || !Files.exists(targetPath)) {
            return;
        }
        List<Path> allPaths;
        try (Stream<Path> stream = Files.walk(targetPath)) {
            allPaths = stream.sorted(Comparator.reverseOrder()).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("清理旧主城维度数据失败: " + targetPath, exception);
        }
        for (Path path : allPaths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (exception instanceof DirectoryNotEmptyException) {
                    continue;
                }
                throw new IllegalStateException("删除旧主城维度数据失败: " + path, exception);
            }
        }
    }

    private boolean isLockedByOtherProcess(IOException exception) {
        if (!(exception instanceof FileSystemException fileSystemException)) {
            return false;
        }
        String reason = fileSystemException.getReason();
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("being used by another process")
            || lower.contains("另一个程序正在使用此文件")
            || lower.contains("进程无法访问");
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("创建目录失败: " + path, exception);
        }
    }

    public record MigrationResult(Path sourcePath, Path targetPath, String message) {
    }
}
