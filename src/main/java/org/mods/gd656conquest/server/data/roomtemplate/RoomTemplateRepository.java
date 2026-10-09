package org.mods.gd656conquest.server.data.roomtemplate;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.runtime.RoomDimensionResetService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class RoomTemplateRepository {
    private final MinecraftServer server;
    private final RoomDimensionResetService dimensionResetService;

    public RoomTemplateRepository(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.dimensionResetService = new RoomDimensionResetService(server);
    }

    public List<String> listTemplateIds() {
        Path root = ServerDataPaths.getRoomTemplateDirectory(server);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream
                .filter(Files::isDirectory)
                .map(path -> path.getFileName() == null ? "" : path.getFileName().toString())
                .filter(name -> !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    public Path saveCurrentDimensionAsTemplate(RoomDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Path templatePath = resolveTemplatePath(definition);
        ensureDirectory(ServerDataPaths.getRoomTemplateDirectory(server));
        return dimensionResetService.saveCurrentDimensionAsTemplate(definition, templatePath);
    }

    public Path resetRoomDimensionFromTemplate(RoomDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Path templatePath = resolveTemplatePath(definition);
        return dimensionResetService.resetDimensionFromTemplate(definition, templatePath);
    }

    private Path resolveTemplatePath(RoomDefinition definition) {
        String mapId = definition.gameplayMapId();
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalStateException("地图模板 mapId 未配置，无法执行模板读写");
        }
        return ServerDataPaths.getRoomTemplatePath(server, mapId.trim());
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("创建目录失败: " + path, exception);
        }
    }
}
