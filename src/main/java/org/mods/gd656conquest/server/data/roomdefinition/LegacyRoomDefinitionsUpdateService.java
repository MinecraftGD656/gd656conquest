package org.mods.gd656conquest.server.data.roomdefinition;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomCommonConfig;
import org.mods.gd656conquest.server.room.model.RoomConquestConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomEquipmentConfig;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomPlayerStateConfig;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomResultConfig;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LegacyRoomDefinitionsUpdateService {
    private static final Gson GSON = new Gson();

    private final MinecraftServer server;
    private final RoomDefinitionRepository repository;

    public LegacyRoomDefinitionsUpdateService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.repository = ConquestDataManager.of(server).roomDefinitions();
    }

    public UpdateResult updateAll(long gameTick) {
        Path legacyDirectory = ServerDataPaths.getLegacyRoomDefinitionDirectory(server);
        if (!Files.isDirectory(legacyDirectory)) {
            return new UpdateResult(legacyDirectory, 0, 0, 0, List.of("未找到旧房间配置目录: " + legacyDirectory));
        }
        List<Path> files = listLegacyFiles(legacyDirectory);
        if (files.isEmpty()) {
            return new UpdateResult(legacyDirectory, 0, 0, 0, List.of("旧房间配置目录中没有可更新的 JSON 文件"));
        }
        List<String> messages = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(server);
        for (Path file : files) {
            try {
                RoomDefinition updated = repository.saveDefinition(readLegacyDefinition(file));
                if (runtimeManager.isInitialized()) {
                    runtimeManager.hotUpdateRoomDefinition(updated, gameTick);
                }
                successCount++;
                messages.add("已更新: " + file.getFileName() + " -> " + updated.roomId());
            } catch (Exception exception) {
                failureCount++;
                messages.add("更新失败: " + file.getFileName() + " | " + detailOf(exception));
            }
        }
        return new UpdateResult(legacyDirectory, files.size(), successCount, failureCount, List.copyOf(messages));
    }

    private List<Path> listLegacyFiles(Path legacyDirectory) {
        try (java.util.stream.Stream<Path> stream = Files.list(legacyDirectory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取旧房间配置目录: " + legacyDirectory, exception);
        }
    }

    private RoomDefinition readLegacyDefinition(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                throw new IllegalArgumentException("JSON 顶层不是对象");
            }
            JsonObject rootObject = rootElement.getAsJsonObject();
            JsonElement definitionElement = rootObject.get("definition");
            JsonObject payload = definitionElement != null && definitionElement.isJsonObject()
                ? definitionElement.getAsJsonObject()
                : rootObject;
            LegacyRoomDefinition legacy = GSON.fromJson(payload, LegacyRoomDefinition.class);
            if (legacy == null) {
                throw new IllegalArgumentException("旧房间定义为空");
            }
            RoomDefinition definition = legacy.toDefinition(resolveFallbackRoomId(file));
            if (definition == null) {
                throw new IllegalArgumentException("旧房间定义无法转换");
            }
            return definition;
        } catch (Exception exception) {
            throw new IllegalStateException("读取旧房间配置失败: " + file, exception);
        }
    }

    private String resolveFallbackRoomId(Path file) {
        String fileName = file == null || file.getFileName() == null ? "" : file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        return normalizeRoomId(baseName);
    }

    private static String normalizeRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return "room_01";
        }
        String normalized = roomId.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("room_\\d{2}")) {
            return normalized;
        }
        if (normalized.matches("room\\d{2}")) {
            return "room_" + normalized.substring(normalized.length() - 2);
        }
        return normalized;
    }

    private static RoomGameplayId parseGameplayId(LegacyGameplayId gameplayId) {
        String value = gameplayId == null ? "" : gameplayId.value;
        if (value == null || value.isBlank()) {
            return RoomGameplayId.CONQUEST;
        }
        return RoomGameplayId.of(value);
    }

    private static RoomLifecycleState parseLifecycleState(String value) {
        if (value == null || value.isBlank()) {
            return RoomLifecycleState.DISABLED;
        }
        try {
            return RoomLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return RoomLifecycleState.DISABLED;
        }
    }

    private static RoomTeamId parseTeamId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RoomTeamId.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeCaptureCode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String detailOf(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public record UpdateResult(
        Path sourceDirectory,
        int scannedCount,
        int successCount,
        int failureCount,
        List<String> messages
    ) {
        public boolean success() {
            return failureCount <= 0;
        }
    }

    private static final class LegacyRoomDefinition {
        private String roomId;
        private String displayName;
        private LegacyGameplayId gameplayId;
        private String lifecycleState;
        private LegacyMatchingConfig matchingConfig;
        private LegacyTransportConfig transportConfig;
        private LegacyPlayerStateConfig playerStateConfig;
        private LegacyRulesConfig rulesConfig;
        private LegacyResultConfig resultConfig;
        private LegacyEquipmentConfig equipmentConfig;
        private LegacyCaptureConfig captureConfig;

        private RoomDefinition toDefinition(String fallbackRoomId) {
            String normalizedRoomId = normalizeRoomId(roomId == null || roomId.isBlank() ? fallbackRoomId : roomId);
            LegacyMatchingConfig normalizedMatching = matchingConfig == null ? new LegacyMatchingConfig() : matchingConfig;
            LegacyTransportConfig normalizedTransport = transportConfig == null ? new LegacyTransportConfig() : transportConfig;
            LegacyPlayerStateConfig normalizedPlayerState = playerStateConfig == null ? new LegacyPlayerStateConfig() : playerStateConfig;
            LegacyRulesConfig normalizedRules = rulesConfig == null ? new LegacyRulesConfig() : rulesConfig;
            LegacyResultConfig normalizedResult = resultConfig == null ? new LegacyResultConfig() : resultConfig;
            LegacyEquipmentConfig normalizedEquipment = equipmentConfig == null ? new LegacyEquipmentConfig() : equipmentConfig;
            LegacyCaptureConfig normalizedCapture = captureConfig == null ? new LegacyCaptureConfig() : captureConfig;
            RoomCommonConfig commonConfig = new RoomCommonConfig(
                normalizedMatching.toModel(),
                normalizedTransport.dimensionId == null || normalizedTransport.dimensionId.isBlank()
                    ? "gd656conquest:" + normalizedRoomId
                    : normalizedTransport.dimensionId.trim(),
                normalizedTransport.queueSpawn == null ? RoomCommonConfig.defaultQueueSpawn() : normalizedTransport.queueSpawn,
                Math.max(0, normalizedPlayerState.respawnDelaySeconds),
                RoomVehicleBackend.defaultValue(),
                normalizedPlayerState.toModel(),
                Math.max(0, normalizedRules.outsideBoundaryDeathSeconds),
                0,
                normalizedCapture.campADisplayName,
                normalizedCapture.campBDisplayName,
                "",
                "",
                normalizedCapture.resolveProgressSpeed(),
                new RoomResultConfig(normalizedResult.endViewPosition),
                normalizedRules.disableHunger,
                normalizedRules.enableAutoTeamSwitchPrompt,
                normalizedRules.disableInventoryChange,
                false,
                false,
                false,
                false
            );
            RoomConquestConfig conquestConfig = new RoomConquestConfig(
                normalizedEquipment.toModel(),
                normalizedTransport.toTeamSpawnGroups(),
                normalizedTransport.toTeamDeployIcons(),
                normalizedTransport.deployViewPosition,
                normalizedRules.sharedBoundary == null ? List.of() : normalizedRules.sharedBoundary,
                normalizedRules.campABoundary == null ? List.of() : normalizedRules.campABoundary,
                normalizedRules.campBBoundary == null ? List.of() : normalizedRules.campBBoundary,
                normalizedCapture.toModel()
            );
            return new RoomDefinition(
                normalizedRoomId,
                displayName == null || displayName.isBlank() ? normalizedRoomId : displayName.trim(),
                parseGameplayId(gameplayId),
                parseLifecycleState(lifecycleState),
                commonConfig,
                conquestConfig,
                RoomBreakthroughConfig.defaults()
            );
        }
    }

    private static final class LegacyGameplayId {
        private String value;
    }

    private static final class LegacyMatchingConfig {
        private int minStartPlayers;
        private int campAMaxPlayers;
        private int campBMaxPlayers;
        private int waitAfterMinSeconds;
        private int waitAfterMaxSeconds;
        private List<String> enabledTeams;

        private RoomMatchingConfig toModel() {
            Set<RoomTeamId> teams = new LinkedHashSet<>();
            if (enabledTeams != null) {
                for (String entry : enabledTeams) {
                    RoomTeamId teamId = parseTeamId(entry);
                    if (teamId != null) {
                        teams.add(teamId);
                    }
                }
            }
            if (teams.isEmpty()) {
                teams.add(RoomTeamId.CAMP_A);
                teams.add(RoomTeamId.CAMP_B);
            }
            return new RoomMatchingConfig(
                Math.max(1, minStartPlayers),
                Math.max(0, campAMaxPlayers),
                Math.max(0, campBMaxPlayers),
                Math.max(0, waitAfterMinSeconds),
                Math.max(0, waitAfterMaxSeconds),
                teams
            );
        }
    }

    private static final class LegacyTransportConfig {
        private String dimensionId;
        private RoomPosition queueSpawn;
        private Map<String, RoomPosition> teamSpawns;
        private Map<String, LegacySpawnGroupConfig> teamSpawnGroups;
        private Map<String, RoomPosition> teamDeployIcons;
        private RoomViewPosition deployViewPosition;

        private Map<RoomTeamId, RoomSpawnGroupConfig> toTeamSpawnGroups() {
            Map<RoomTeamId, RoomSpawnGroupConfig> result = new LinkedHashMap<>();
            if (teamSpawnGroups != null) {
                for (Map.Entry<String, LegacySpawnGroupConfig> entry : teamSpawnGroups.entrySet()) {
                    RoomTeamId teamId = parseTeamId(entry.getKey());
                    if (teamId != null && entry.getValue() != null) {
                        result.put(teamId, entry.getValue().toModel());
                    }
                }
            }
            if (teamSpawns != null) {
                for (Map.Entry<String, RoomPosition> entry : teamSpawns.entrySet()) {
                    RoomTeamId teamId = parseTeamId(entry.getKey());
                    RoomPosition position = entry.getValue();
                    if (teamId == null || position == null) {
                        continue;
                    }
                    RoomSpawnGroupConfig existing = result.get(teamId);
                    if (existing == null) {
                        result.put(teamId, new RoomSpawnGroupConfig(true, List.of(position)));
                        continue;
                    }
                    if (existing.spawns().isEmpty()) {
                        result.put(teamId, new RoomSpawnGroupConfig(existing.enabled(), List.of(position)));
                    }
                }
            }
            return Map.copyOf(result);
        }

        private Map<RoomTeamId, RoomPosition> toTeamDeployIcons() {
            Map<RoomTeamId, RoomPosition> result = new LinkedHashMap<>();
            if (teamDeployIcons != null) {
                for (Map.Entry<String, RoomPosition> entry : teamDeployIcons.entrySet()) {
                    RoomTeamId teamId = parseTeamId(entry.getKey());
                    if (teamId != null && entry.getValue() != null) {
                        result.put(teamId, entry.getValue());
                    }
                }
            }
            return Map.copyOf(result);
        }
    }

    private static final class LegacyPlayerStateConfig {
        private String gameMode;
        private double invulnerableSeconds;
        private int respawnDelaySeconds;

        private RoomPlayerStateConfig toModel() {
            String resolvedGameMode = gameMode == null || gameMode.isBlank()
                ? RoomPlayerStateConfig.defaults().gameMode()
                : gameMode.trim();
            return new RoomPlayerStateConfig(
                resolvedGameMode,
                invulnerableSeconds,
                RoomPlayerStateConfig.defaults().revivedInvulnerableSeconds()
            );
        }
    }

    private static final class LegacyRulesConfig {
        private boolean disableHunger;
        private boolean enableAutoTeamSwitchPrompt;
        private boolean disableInventoryChange;
        private int outsideBoundaryDeathSeconds;
        private List<RoomPosition> sharedBoundary;
        private List<RoomPosition> campABoundary;
        private List<RoomPosition> campBBoundary;
    }

    private static final class LegacyResultConfig {
        private RoomViewPosition endViewPosition;
    }

    private static final class LegacyEquipmentConfig {
        private List<String> campAEquipmentSlots;
        private List<String> campBEquipmentSlots;

        private RoomEquipmentConfig toModel() {
            return new RoomEquipmentConfig(
                campAEquipmentSlots == null ? List.of() : List.copyOf(campAEquipmentSlots),
                campBEquipmentSlots == null ? List.of() : List.copyOf(campBEquipmentSlots)
            );
        }
    }

    private static final class LegacyCaptureConfig {
        private Map<String, LegacyCapturePointDefinition> points;
        private String mapId;
        private String campADisplayName;
        private String campBDisplayName;
        private double progressSpeed;
        private int drawDurationMinutes;
        private int fullCaptureEndSeconds;
        private int targetScore;
        private int captureScoreIntervalSeconds;
        private String scoreMethod;
        private int campAInitialTroops;
        private int campBInitialTroops;
        private String initialCaptureOwnership;

        private RoomCaptureConfig toModel() {
            Map<String, RoomCapturePointDefinition> normalizedPoints = new LinkedHashMap<>();
            if (points != null) {
                for (Map.Entry<String, LegacyCapturePointDefinition> entry : points.entrySet()) {
                    LegacyCapturePointDefinition legacyPoint = entry.getValue();
                    String normalizedCode = normalizeCaptureCode(
                        legacyPoint != null && legacyPoint.code != null && !legacyPoint.code.isBlank()
                            ? legacyPoint.code
                            : entry.getKey()
                    );
                    if (normalizedCode.isBlank()) {
                        continue;
                    }
                    RoomCapturePointDefinition point = legacyPoint == null
                        ? new RoomCapturePointDefinition(
                            normalizedCode,
                            normalizedCode,
                            null,
                            RoomSpawnGroupConfig.defaults(),
                            RoomSpawnGroupConfig.defaults()
                        )
                        : legacyPoint.toModel(normalizedCode);
                    normalizedPoints.put(normalizedCode, point);
                }
            }
            return new RoomCaptureConfig(
                normalizedPoints,
                mapId,
                campADisplayName,
                campBDisplayName,
                drawDurationMinutes,
                fullCaptureEndSeconds,
                targetScore,
                captureScoreIntervalSeconds,
                scoreMethod,
                campAInitialTroops,
                campBInitialTroops,
                initialCaptureOwnership
            );
        }

        private double resolveProgressSpeed() {
            if (!Double.isFinite(progressSpeed) || progressSpeed <= 0.0D) {
                return 40.0D;
            }
            if (progressSpeed <= 10.0D) {
                return 6.0D / progressSpeed;
            }
            return progressSpeed;
        }
    }

    private static final class LegacyCapturePointDefinition {
        private String code;
        private String displayName;
        private RoomPosition centerPosition;
        private LegacySpawnGroupConfig innerSpawnGroup;
        private LegacySpawnGroupConfig outerSpawnGroup;
        private Map<String, LegacySpawnGroupConfig> teamOuterSpawnGroups;
        private int stageIndex;

        private RoomCapturePointDefinition toModel(String normalizedCode) {
            Map<RoomTeamId, RoomSpawnGroupConfig> teamScopedOuter = new LinkedHashMap<>();
            if (teamOuterSpawnGroups != null) {
                for (Map.Entry<String, LegacySpawnGroupConfig> entry : teamOuterSpawnGroups.entrySet()) {
                    RoomTeamId teamId = parseTeamId(entry.getKey());
                    if (teamId != null && entry.getValue() != null) {
                        teamScopedOuter.put(teamId, entry.getValue().toModel());
                    }
                }
            }
            return new RoomCapturePointDefinition(
                normalizedCode,
                displayName == null || displayName.isBlank() ? normalizedCode : displayName.trim(),
                centerPosition,
                innerSpawnGroup == null ? RoomSpawnGroupConfig.defaults() : innerSpawnGroup.toModel(),
                outerSpawnGroup == null ? RoomSpawnGroupConfig.defaults() : outerSpawnGroup.toModel(),
                teamScopedOuter,
                stageIndex
            );
        }
    }

    private static final class LegacySpawnGroupConfig {
        private boolean enabled = true;
        private List<RoomPosition> spawns;

        private RoomSpawnGroupConfig toModel() {
            return new RoomSpawnGroupConfig(enabled, spawns == null ? List.of() : List.copyOf(spawns));
        }
    }
}
