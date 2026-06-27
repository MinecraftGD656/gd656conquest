package org.mods.gd656conquest.server.data.playerdata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ServerDataPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public final class LegacySurvivalDataExportService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> LEGACY_SURVIVAL_KEYS = List.of(
        "survivalModeActive",
        "survivalSpawnInitialized",
        "survivalDimensionId",
        "survivalPosition",
        "survivalInventorySnapshot",
        "hubInventorySnapshot",
        "survivalExperienceTotal",
        "survivalRandomTeleportUsesRemaining"
    );
    private static final String OUTPUT_FILE_NAME = "legacy-survival-playerdata-audit.json";

    private LegacySurvivalDataExportService() {
    }

    public static ExportResult export(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server");
        }
        Path playerDataDirectory = ServerDataPaths.getPlayerDataDirectory(server);
        Path docsDirectory = ServerDataPaths.getModRoot(server).resolve("docs");
        ensureDirectory(docsDirectory);
        List<LegacyPlayerEntry> entries = scanEntries(server, playerDataDirectory);
        entries.sort(Comparator.comparing(LegacyPlayerEntry::playerId, String.CASE_INSENSITIVE_ORDER));
        AuditPayload payload = new AuditPayload(
            Instant.now().toString(),
            playerDataDirectory.toAbsolutePath().toString(),
            LEGACY_SURVIVAL_KEYS,
            collectPlayerNames(entries),
            entries
        );
        Path outputFile = docsDirectory.resolve(OUTPUT_FILE_NAME);
        writePayload(outputFile, payload);
        return new ExportResult(outputFile, payload.entries().size());
    }

    private static List<LegacyPlayerEntry> scanEntries(MinecraftServer server, Path playerDataDirectory) {
        List<LegacyPlayerEntry> entries = new ArrayList<>();
        if (playerDataDirectory == null || Files.notExists(playerDataDirectory)) {
            return entries;
        }
        try (Stream<Path> stream = Files.list(playerDataDirectory)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(path -> {
                    LegacyPlayerEntry entry = scanSingleFile(server, path);
                    if (entry != null) {
                        entries.add(entry);
                    }
                });
        } catch (Exception ignored) {
        }
        return entries;
    }

    private static LegacyPlayerEntry scanSingleFile(MinecraftServer server, Path path) {
        if (server == null || path == null) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            List<String> matchedKeys = new ArrayList<>();
            for (String key : LEGACY_SURVIVAL_KEYS) {
                if (object.has(key)) {
                    matchedKeys.add(key);
                }
            }
            if (matchedKeys.isEmpty()) {
                return null;
            }
            String fileName = path.getFileName().toString();
            String playerId = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
            UUID uuid = tryParseUuid(playerId);
            ServerPlayer onlinePlayer = uuid == null ? null : server.getPlayerList().getPlayer(uuid);
            String lastKnownPlayerName = resolveLastKnownPlayerName(object, server, uuid, onlinePlayer);
            return new LegacyPlayerEntry(
                playerId,
                lastKnownPlayerName,
                onlinePlayer == null ? "" : onlinePlayer.getScoreboardName(),
                onlinePlayer != null,
                matchedKeys
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UUID tryParseUuid(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(playerId.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> collectPlayerNames(List<LegacyPlayerEntry> entries) {
        return entries.stream()
            .map(entry -> entry.playerName() == null || entry.playerName().isBlank() ? entry.onlineName() : entry.playerName())
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private static String resolveLastKnownPlayerName(JsonObject object, MinecraftServer server, UUID playerId, ServerPlayer onlinePlayer) {
        if (onlinePlayer != null && onlinePlayer.getScoreboardName() != null && !onlinePlayer.getScoreboardName().isBlank()) {
            return onlinePlayer.getScoreboardName();
        }
        if (object != null && object.has("lastKnownPlayerName") && object.get("lastKnownPlayerName").isJsonPrimitive()) {
            String lastKnownPlayerName = object.get("lastKnownPlayerName").getAsString();
            if (lastKnownPlayerName != null && !lastKnownPlayerName.isBlank()) {
                return lastKnownPlayerName;
            }
        }
        if (server != null && playerId != null) {
            try {
                java.util.Optional<GameProfile> profile = server.getProfileCache().get(playerId);
                if (profile.isPresent() && profile.get().getName() != null && !profile.get().getName().isBlank()) {
                    return profile.get().getName();
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static void writePayload(Path outputFile, AuditPayload payload) {
        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                ensureDirectory(parent);
            }
            try (Writer writer = Files.newBufferedWriter(
                outputFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                GSON.toJson(payload, writer);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入旧生存服数据审计文件: " + outputFile, exception);
        }
    }

    private static void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建目录: " + directory, exception);
        }
    }

    public record ExportResult(Path outputFile, int matchedPlayerCount) {
    }

    private record AuditPayload(
        String generatedAt,
        String playerDataDirectory,
        List<String> legacyKeys,
        List<String> playerNames,
        List<LegacyPlayerEntry> entries
    ) {
    }

    private record LegacyPlayerEntry(
        String playerId,
        String playerName,
        String onlineName,
        boolean online,
        List<String> matchedLegacyKeys
    ) {
    }
}
