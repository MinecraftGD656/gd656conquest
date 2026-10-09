package org.mods.gd656conquest.server.data.roomdefinition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class RoomDefinitionMigrator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int TARGET_VERSION = 15;

    public static MigrationResult migrateLegacyRooms(MinecraftServer server) {
        Path legacyDir = ServerDataPaths.getRoomDefinitionDirectory(server);
        Path newDir = ServerDataPaths.getRoomDataDirectory(server);
        
        if (!Files.isDirectory(legacyDir)) {
            return new MigrationResult(0, 0, List.of("旧房间配置目录不存在: " + legacyDir));
        }

        try {
            Files.createDirectories(newDir);
        } catch (IOException e) {
            return new MigrationResult(0, 0, List.of("无法创建新房间配置目录: " + newDir));
        }

        List<String> messages = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        try (Stream<Path> stream = Files.list(legacyDir)) {
            List<Path> files = stream.filter(p -> p.toString().endsWith(".json")).toList();
            for (Path file : files) {
                try {
                    boolean success = migrateFile(file, newDir.resolve(file.getFileName()));
                    if (success) {
                        successCount++;
                        messages.add("已迁移: " + file.getFileName());
                    } else {
                        failureCount++;
                        messages.add("迁移失败: " + file.getFileName() + " - 无效的格式");
                    }
                } catch (Exception e) {
                    failureCount++;
                    messages.add("迁移异常: " + file.getFileName() + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            messages.add("读取目录失败: " + e.getMessage());
        }

        return new MigrationResult(successCount, failureCount, messages);
    }

    private static boolean migrateFile(Path legacyFile, Path targetFile) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(legacyFile)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // 1. 获取最里层的 definition 对象
        JsonObject definitionObj = root.has("definition") && root.get("definition").isJsonObject()
            ? root.getAsJsonObject("definition")
            : root; // 如果没有 definition 包装，就假定外层就是 definition
            
        // 2. 提取 gameplayId
        String gameplayIdStr = "conquest"; // 默认 fallback
        if (definitionObj.has("gameplayId")) {
            JsonElement idElement = definitionObj.get("gameplayId");
            if (idElement.isJsonObject() && idElement.getAsJsonObject().has("value")) {
                gameplayIdStr = idElement.getAsJsonObject().get("value").getAsString();
            } else if (idElement.isJsonPrimitive()) {
                gameplayIdStr = idElement.getAsString();
            }
        }
        
        // 3. 构建新的目标 JSON
        JsonObject newRoot = new JsonObject();
        newRoot.addProperty("version", TARGET_VERSION);
        
        // 复制基础字段
        copyProperty(definitionObj, newRoot, "roomId");
        copyProperty(definitionObj, newRoot, "displayName");
        newRoot.addProperty("gameplayId", gameplayIdStr);
        copyProperty(definitionObj, newRoot, "lifecycleState");
        
        // 复制 commonConfig
        if (definitionObj.has("commonConfig")) {
            newRoot.add("commonConfig", definitionObj.get("commonConfig"));
        } else {
            // 如果连 commonConfig 都没有，说明格式彻底不对，放弃迁移
            return false;
        }
        
        // 4. 根据 gameplayId 选择正确的 config 作为 gameplayConfig
        String configKey = gameplayIdStr.toLowerCase() + "Config";
        if (definitionObj.has(configKey)) {
            newRoot.add("gameplayConfig", definitionObj.get(configKey));
        } else {
            // 如果找不到对应的 config，则使用一个空对象
            newRoot.add("gameplayConfig", new JsonObject());
        }
        
        // 5. 写入新文件
        try (Writer writer = Files.newBufferedWriter(targetFile)) {
            GSON.toJson(newRoot, writer);
        }
        
        return true;
    }
    
    private static void copyProperty(JsonObject source, JsonObject target, String key) {
        if (source.has(key)) {
            target.add(key, source.get(key));
        }
    }

    public record MigrationResult(int successCount, int failureCount, List<String> messages) {}
}
