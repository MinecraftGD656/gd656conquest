package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class RoomDimensionResetService {
    private final MinecraftServer server;

    public RoomDimensionResetService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public Path saveCurrentDimensionAsTemplate(RoomDefinition definition, Path templatePath) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(templatePath, "templatePath");
        ResourceKey<Level> dimensionKey = requireDimensionKey(definition);
        Path savePath = resolveDimensionSavePath(dimensionKey);
        prepareLevelForReplacement(dimensionKey);
        if (!RoomDimensionControlService.unloadRoomDimension(server, dimensionKey)) {
            throw new IllegalStateException("卸载维度失败: " + dimensionKey.location());
        }
        RuntimeException copyFailure = null;
        try {
            if (!Files.exists(savePath)) {
                ensureDirectory(savePath);
            }
            replaceTemplateByCurrentDimensionSafely(savePath, templatePath);
        } catch (RuntimeException exception) {
            copyFailure = exception;
        } finally {
            reloadAndVerifyDimension(dimensionKey);
            prepareLevelAfterReplacement(dimensionKey);
        }
        if (copyFailure != null) {
            throw copyFailure;
        }
        return templatePath;
    }

    public Path resetDimensionFromTemplate(RoomDefinition definition, Path templatePath) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(templatePath, "templatePath");
        ResourceKey<Level> dimensionKey = requireDimensionKey(definition);
        Path savePath = resolveDimensionSavePath(dimensionKey);
        Path templateDataRoot = resolveTemplateDataRoot(templatePath, dimensionKey.location());
        prepareLevelForReplacement(dimensionKey);
        if (!RoomDimensionControlService.unloadRoomDimension(server, dimensionKey)) {
            throw new IllegalStateException("卸载维度失败: " + dimensionKey.location());
        }
        try {
            replaceByTemplate(templateDataRoot, savePath);
        } finally {
            reloadAndVerifyDimension(dimensionKey);
            prepareLevelAfterReplacement(dimensionKey);
        }
        return savePath;
    }

    private ResourceKey<Level> requireDimensionKey(RoomDefinition definition) {
        ResourceKey<Level> dimensionKey = RoomDimensionControlService.resolveRoomDimensionKey(definition);
        if (dimensionKey == null) {
            throw new IllegalStateException("房间维度未配置，无法执行维度重置");
        }
        return dimensionKey;
    }

    private Path resolveDimensionSavePath(ResourceKey<Level> dimensionKey) {
        return server.getWorldPath(LevelResource.ROOT)
            .resolve("dimensions")
            .resolve(dimensionKey.location().getNamespace())
            .resolve(dimensionKey.location().getPath());
    }

    private void prepareLevelForReplacement(ResourceKey<Level> dimensionKey) {
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return;
        }
        evacuatePlayersFromLevel(level);
        flushServerData();
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

    private void evacuatePlayersFromLevel(ServerLevel roomLevel) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(server);
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            if (player == null || player.serverLevel() != roomLevel) {
                continue;
            }
            manager.sendPlayerToHub(player);
        }
    }

    private void reloadAndVerifyDimension(ResourceKey<Level> dimensionKey) {
        boolean reloaded = RoomDimensionControlService.reloadRoomDimension(server, dimensionKey);
        if (!reloaded) {
            throw new IllegalStateException("重载维度失败: " + dimensionKey.location());
        }
        if (server.getLevel(dimensionKey) == null) {
            throw new IllegalStateException("重载维度后未找到目标维度: " + dimensionKey.location());
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

    private Path resolveTemplateDataRoot(Path templatePath, ResourceLocation dimensionLocation) {
        if (!Files.exists(templatePath) || !Files.isDirectory(templatePath)) {
            throw new IllegalStateException("地图模板不存在或不是目录: " + templatePath);
        }
        if (containsRegionChunkData(templatePath)) {
            return templatePath;
        }
        Path nestedDimensionPath = templatePath
            .resolve("dimensions")
            .resolve(dimensionLocation.getNamespace())
            .resolve(dimensionLocation.getPath());
        if (containsRegionChunkData(nestedDimensionPath)) {
            return nestedDimensionPath;
        }
        try (Stream<Path> children = Files.list(templatePath)) {
            List<Path> directories = children.filter(Files::isDirectory).toList();
            for (Path child : directories) {
                if (containsRegionChunkData(child)) {
                    return child;
                }
                Path childNestedDimensionPath = child
                    .resolve("dimensions")
                    .resolve(dimensionLocation.getNamespace())
                    .resolve(dimensionLocation.getPath());
                if (containsRegionChunkData(childNestedDimensionPath)) {
                    return childNestedDimensionPath;
                }
            }
        } catch (IOException ignored) {
        }
        throw new IllegalStateException("模板目录不包含可用区块数据（region/*.mca）: " + templatePath);
    }

    private boolean containsRegionChunkData(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return false;
        }
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

    private void replaceTemplateByCurrentDimensionSafely(Path savePath, Path templatePath) {
        Path parent = templatePath.getParent();
        if (parent == null) {
            throw new IllegalStateException("模板路径非法: " + templatePath);
        }
        ensureDirectory(parent);
        String suffix = templatePath.getFileName() == null ? "template" : templatePath.getFileName().toString();
        Path staging = parent.resolve(suffix + "__staging");
        Path backup = parent.resolve(suffix + "__backup");
        deleteIfExists(staging);
        deleteIfExists(backup);
        replaceByTemplate(savePath, staging);
        boolean backupReady = false;
        try {
            if (Files.exists(templatePath)) {
                replaceByTemplate(templatePath, backup);
                backupReady = true;
            }
            replaceByTemplate(staging, templatePath);
            deleteIfExists(staging);
            if (backupReady) {
                deleteIfExists(backup);
            }
        } catch (Exception exception) {
            try {
                deleteIfExists(staging);
            } catch (Exception ignored) {
            }
            if (backupReady && Files.exists(backup)) {
                try {
                    replaceByTemplate(backup, templatePath);
                } catch (Exception rollbackException) {
                    throw new IllegalStateException("模板覆盖失败且回滚失败: " + templatePath, rollbackException);
                }
            }
            throw new IllegalStateException("模板覆盖失败，已中止覆盖: " + templatePath, exception);
        }
    }

    private void replaceByTemplate(Path sourcePath, Path targetPath) {
        if (!Files.isDirectory(sourcePath)) {
            throw new IllegalStateException("源目录不存在或不是目录: " + sourcePath);
        }
        deleteIfExists(targetPath);
        ensureDirectory(targetPath);
        try (Stream<Path> stream = Files.walk(sourcePath)) {
            stream.forEach(source -> {
                try {
                    if (!Files.isDirectory(source) && isKnownExternalRuntimeFile(source)) {
                        return;
                    }
                    Path target = targetPath.resolve(sourcePath.relativize(source).toString());
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
                    if (isLockedByOtherProcess(exception) && isKnownExternalRuntimeFile(source)) {
                        return;
                    }
                    throw new IllegalStateException("维度模板复制失败: " + source, exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("读取维度模板失败: " + sourcePath, exception);
        }
    }

    private void deleteIfExists(Path targetPath) {
        if (targetPath == null || !Files.exists(targetPath)) {
            return;
        }
        List<Path> allPaths;
        try (Stream<Path> stream = Files.walk(targetPath)) {
            allPaths = stream.sorted(Comparator.reverseOrder()).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("清理维度目录失败: " + targetPath, exception);
        }
        Set<Path> skippedLockedFiles = new HashSet<>();
        List<IOException> hardFailures = new ArrayList<>();
        for (Path path : allPaths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (canIgnoreDeleteFailure(path, exception, skippedLockedFiles)) {
                    continue;
                }
                hardFailures.add(exception);
            }
        }
        if (!hardFailures.isEmpty()) {
            IOException first = hardFailures.get(0);
            throw new IllegalStateException("删除旧维度数据失败: " + first.getMessage(), first);
        }
    }

    private boolean canIgnoreDeleteFailure(Path path, IOException exception, Set<Path> skippedLockedFiles) {
        if (exception instanceof DirectoryNotEmptyException && containsSkippedFile(path, skippedLockedFiles)) {
            return true;
        }
        if (isLockedByOtherProcess(exception) && isKnownExternalRuntimeFile(path)) {
            skippedLockedFiles.add(path.toAbsolutePath().normalize());
            return true;
        }
        return false;
    }

    private boolean containsSkippedFile(Path directory, Set<Path> skippedLockedFiles) {
        Path normalized = directory.toAbsolutePath().normalize();
        for (Path skipped : skippedLockedFiles) {
            if (skipped.startsWith(normalized)) {
                return true;
            }
        }
        return false;
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

    private boolean isKnownExternalRuntimeFile(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.equals("distanthorizons.sqlite")
            || fileName.equals("distanthorizons.sqlite-wal")
            || fileName.equals("distanthorizons.sqlite-shm");
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("创建目录失败: " + path, exception);
        }
    }
}
