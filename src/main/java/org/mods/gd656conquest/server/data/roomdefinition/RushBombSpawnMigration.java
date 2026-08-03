package org.mods.gd656conquest.server.data.roomdefinition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
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

/**
 * 一次性数据迁移：将 Rush 房间配置中 bombCampASpawnGroup / bombCampBSpawnGroup
 * 字段名更名为 bombInnerSpawnGroup / bombDefenderInnerSpawnGroup。
 * 触发方式：/gdc debug update260803
 */
public final class RushBombSpawnMigration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RushBombSpawnMigration() {
    }

    public static void execute(MinecraftServer server, net.minecraft.commands.CommandSourceStack source) {
        Path dataDir = ServerDataPaths.getRoomDataDirectory(server);
        if (!Files.isDirectory(dataDir)) {
            source.sendFailure(Component.literal("房间数据目录不存在: " + dataDir));
            return;
        }

        List<String> messages = new ArrayList<>();
        int successCount = 0;
        int skipCount = 0;
        int failureCount = 0;

        try (Stream<Path> stream = Files.list(dataDir)) {
            List<Path> files = stream.filter(p -> p.toString().endsWith(".json")).toList();
            source.sendSystemMessage(Component.literal("扫描 " + files.size() + " 个房间配置文件..."));

            for (Path file : files) {
                try {
                    MigrationAction action = migrateBombSpawnFields(file);
                    switch (action) {
                        case MIGRATED -> successCount++;
                        case SKIPPED -> skipCount++;
                        case FAILED -> failureCount++;
                    }
                } catch (Exception e) {
                    failureCount++;
                    messages.add("异常: " + file.getFileName() + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("读取目录失败: " + e.getMessage()));
            return;
        }

        source.sendSystemMessage(Component.literal(
            "Rush 炸弹出生点字段迁移完成: 已迁移 " + successCount
            + " 个, 跳过 " + skipCount + " 个, 失败 " + failureCount + " 个"));
        for (String msg : messages) {
            source.sendSystemMessage(Component.literal("  " + msg));
        }
    }

    enum MigrationAction { MIGRATED, SKIPPED, FAILED }

    private static MigrationAction migrateBombSpawnFields(Path file) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // 仅处理 Rush 模式的房间
        if (!root.has("gameplayId")) {
            return MigrationAction.SKIPPED;
        }
        String gameplayId = root.get("gameplayId").getAsString();
        if (!"rush".equalsIgnoreCase(gameplayId)) {
            return MigrationAction.SKIPPED;
        }

        // 检查是否有 gameplayConfig
        if (!root.has("gameplayConfig") || !root.get("gameplayConfig").isJsonObject()) {
            return MigrationAction.SKIPPED;
        }

        JsonObject gameplayConfig = root.getAsJsonObject("gameplayConfig");
        boolean changed = false;

        // 收集需要重命名的 key
        List<String> oldKeys = new ArrayList<>();
        for (String key : gameplayConfig.keySet()) {
            if (key.startsWith("bombCampASpawnGroup")) {
                oldKeys.add(key);
            } else if (key.startsWith("bombCampBSpawnGroup")) {
                oldKeys.add(key);
            }
        }

        if (oldKeys.isEmpty()) {
            return MigrationAction.SKIPPED;
        }

        for (String oldKey : oldKeys) {
            JsonElement value = gameplayConfig.get(oldKey);
            String newKey;
            if (oldKey.startsWith("bombCampBSpawnGroup")) {
                newKey = "bombDefenderInnerSpawnGroup" + oldKey.substring("bombCampBSpawnGroup".length());
            } else {
                newKey = "bombInnerSpawnGroup" + oldKey.substring("bombCampASpawnGroup".length());
            }
            gameplayConfig.remove(oldKey);
            gameplayConfig.add(newKey, value);
            changed = true;
        }

        if (!changed) {
            return MigrationAction.SKIPPED;
        }

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(root, writer);
        }

        return MigrationAction.MIGRATED;
    }
}
