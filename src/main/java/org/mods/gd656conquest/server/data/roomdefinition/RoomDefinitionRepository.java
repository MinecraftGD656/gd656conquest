package org.mods.gd656conquest.server.data.roomdefinition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RoomDefinitionRepository extends JsonDataStoreSupport {
    private static final int DEFAULT_ROOM_COUNT = 16;
    private static final int STORAGE_VERSION = 11;

    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private final Map<String, RoomDefinition> cache = new LinkedHashMap<>();
    private boolean initialized;

    public RoomDefinitionRepository(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        ensureDirectory(ServerDataPaths.getRoomDefinitionDirectory(server));
        cache.clear();
        for (String roomId : defaultRoomIds()) {
            RoomDefinition definition = loadOrCreate(roomId);
            RoomDefinition normalized = validateAndNormalize(definition);
            cache.put(normalized.roomId(), normalized);
            if (!normalized.equals(definition)) {
                persistNow(normalized);
            }
        }
        initialized = true;
    }

    public synchronized List<RoomDefinition> getAllDefinitions() {
        ensureInitialized();
        return cache.values().stream()
            .sorted(Comparator.comparing(RoomDefinition::roomId, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public synchronized Optional<RoomDefinition> findDefinition(String roomId) {
        ensureInitialized();
        String normalized = normalizeRoomId(roomId);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        RoomDefinition exact = cache.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.empty();
    }

    public synchronized RoomDefinition readDefinition(String roomId) {
        ensureInitialized();
        return findDefinition(roomId).orElseGet(() -> {
            RoomDefinition created = validateAndNormalize(createDefaultDefinition(normalizeRoomId(roomId)));
            cache.put(created.roomId(), created);
            persist(created);
            return created;
        });
    }

    public synchronized RoomDefinition saveDefinition(RoomDefinition definition) {
        ensureInitialized();
        RoomDefinition normalized = validateAndNormalize(definition);
        cache.put(normalized.roomId(), normalized);
        persist(normalized);
        return normalized;
    }

    public synchronized RoomDefinition resetDefinition(String roomId) {
        ensureInitialized();
        String normalizedRoomId = normalizeRoomId(roomId);
        RoomDefinition reset = validateAndNormalize(createDefaultDefinition(normalizedRoomId));
        cache.put(reset.roomId(), reset);
        persist(reset);
        return reset;
    }

    public synchronized Optional<RoomDefinition> reloadDefinitionFromFile(String roomId) {
        ensureInitialized();
        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId.isBlank()) {
            return Optional.empty();
        }
        Path file = ServerDataPaths.getRoomDefinitionFile(server, normalizedRoomId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        ProgressSpeedMigrationData progressSpeedMigration = readProgressSpeedMigrationData(file);
        StoredRoomDefinition stored = read(file, StoredRoomDefinition.class, StoredRoomDefinition::new);
        RoomDefinition definition = stored.toDefinition(normalizedRoomId, progressSpeedMigration);
        if (definition == null) {
            return Optional.empty();
        }
        RoomDefinition normalized = validateAndNormalize(definition);
        cache.put(normalized.roomId(), normalized);
        if (!normalized.equals(definition) || stored.shouldRewrite()) {
            persistNow(normalized);
        }
        return Optional.of(normalized);
    }

    public synchronized Collection<String> getRoomIds() {
        ensureInitialized();
        return new ArrayList<>(cache.keySet());
    }

    public synchronized Optional<RoomDefinition> findByDimensionId(String dimensionId) {
        ensureInitialized();
        if (dimensionId == null || dimensionId.isBlank()) {
            return Optional.empty();
        }
        return cache.values().stream()
            .filter(definition -> definition != null)
            .filter(definition -> dimensionId.equalsIgnoreCase(definition.commonConfig().dimensionId()))
            .findFirst();
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private RoomDefinition loadOrCreate(String roomId) {
        Path file = ServerDataPaths.getRoomDefinitionFile(server, roomId);
        if (Files.exists(file)) {
            ProgressSpeedMigrationData progressSpeedMigration = readProgressSpeedMigrationData(file);
            StoredRoomDefinition stored = read(file, StoredRoomDefinition.class, StoredRoomDefinition::new);
            RoomDefinition definition = stored.toDefinition(roomId, progressSpeedMigration);
            if (definition != null) {
                if (stored.shouldRewrite()) {
                    persistNow(definition);
                }
                return definition;
            }
        }
        Optional<RoomDefinition> migratedLegacy = readLegacyDefinition(roomId);
        if (migratedLegacy.isPresent()) {
            RoomDefinition migrated = migratedLegacy.get();
            persistNow(migrated);
            return migrated;
        }
        RoomDefinition created = createDefaultDefinition(roomId);
        persistNow(created);
        return created;
    }

    private void persist(RoomDefinition definition) {
        persistNow(definition);
    }

    private void persistNow(RoomDefinition definition) {
        write(ServerDataPaths.getRoomDefinitionFile(server, definition.roomId()), StoredRoomDefinition.from(definition));
    }

    private RoomDefinition validateAndNormalize(RoomDefinition definition) {
        RoomDefinition normalized = normalizeDefinition(definition);
        RoomConfigValidationResult validation = RoomConfigValidator.validate(normalized);
        if (!validation.isComplete() && shouldForceDisabled(normalized.lifecycleState())) {
            normalized = normalized.withLifecycleState(RoomLifecycleState.DISABLED);
        }
        return normalized;
    }

    private boolean shouldForceDisabled(RoomLifecycleState lifecycleState) {
        if (lifecycleState == null) {
            return true;
        }
        return lifecycleState == RoomLifecycleState.IDLE
            || lifecycleState == RoomLifecycleState.MATCHING
            || lifecycleState == RoomLifecycleState.RUNNING;
    }

    private RoomDefinition normalizeDefinition(RoomDefinition definition) {
        String roomId = normalizeRoomId(definition == null ? null : definition.roomId());
        RoomDefinition base = definition == null ? createDefaultDefinition(roomId) : definition;
        String dimensionId = base.commonConfig().dimensionId() == null || base.commonConfig().dimensionId().isBlank()
            ? buildDimensionId(roomId)
            : base.commonConfig().dimensionId();
        RoomPosition queueSpawn = base.commonConfig().queueSpawn() == null
            ? RoomCommonConfig.defaultQueueSpawn()
            : base.commonConfig().queueSpawn();
        RoomCommonConfig commonConfig = new RoomCommonConfig(
            base.commonConfig().matchingConfig(),
            dimensionId,
            queueSpawn,
            base.commonConfig().respawnDelaySeconds(),
            base.commonConfig().vehicleBackend(),
            base.commonConfig().playerStateConfig(),
            base.commonConfig().outsideBoundaryDeathSeconds(),
            base.commonConfig().aircraftOutsideBoundaryCleanupSeconds(),
            base.commonConfig().campADisplayName(),
            base.commonConfig().campBDisplayName(),
            base.commonConfig().campAVoicePack(),
            base.commonConfig().campBVoicePack(),
            base.commonConfig().progressSpeed(),
            base.commonConfig().resultConfig(),
            base.commonConfig().disableHunger(),
            base.commonConfig().enableAutoTeamSwitchPrompt(),
            base.commonConfig().disableInventoryChange(),
            base.commonConfig().enableGoatHorn(),
            base.commonConfig().enablePlayerDowned(),
            base.commonConfig().enableBackAgainstWall(),
            base.commonConfig().enableRandomLoadout(),
            base.commonConfig().enableRandomAttachments()
        );
        RoomConquestConfig conquestConfig = new RoomConquestConfig(
            base.conquestConfig().equipmentConfig(),
            base.conquestConfig().teamSpawnGroups(),
            base.conquestConfig().teamDeployIcons(),
            base.conquestConfig().deployViewPosition(),
            base.conquestConfig().sharedBoundary(),
            base.conquestConfig().campABoundary(),
            base.conquestConfig().campBBoundary(),
            normalizeCaptureConfig(base.conquestConfig().captureConfig())
        );
        return new RoomDefinition(
            roomId,
            base.displayName(),
            base.gameplayId(),
            base.lifecycleState(),
            commonConfig,
            conquestConfig,
            base.breakthroughConfig() == null ? RoomBreakthroughConfig.defaults() : base.breakthroughConfig()
        );
    }

    private RoomCaptureConfig normalizeCaptureConfig(RoomCaptureConfig captureConfig) {
        RoomCaptureConfig source = captureConfig == null ? RoomCaptureConfig.defaults() : captureConfig;
        Map<String, RoomCapturePointDefinition> normalizedPoints = new LinkedHashMap<>();
        if (source.points() != null) {
            normalizedPoints.putAll(source.points());
        }
        for (String captureCode : ConquestBlocks.captureCodes()) {
            if (captureCode == null || captureCode.isBlank()) {
                continue;
            }
            String normalizedCode = captureCode.trim().toUpperCase(Locale.ROOT);
            normalizedPoints.computeIfAbsent(
                normalizedCode,
                ignored -> new RoomCapturePointDefinition(
                    normalizedCode,
                    normalizedCode,
                    null,
                    RoomSpawnGroupConfig.defaults(),
                    RoomSpawnGroupConfig.defaults()
                )
            );
        }
        return new RoomCaptureConfig(
            normalizedPoints,
            source.mapId(),
            source.campADisplayName(),
            source.campBDisplayName(),
            source.drawDurationMinutes(),
            source.fullCaptureEndSeconds(),
            source.targetScore(),
            source.captureScoreIntervalSeconds(),
            source.scoreMethod(),
            source.campAInitialTroops(),
            source.campBInitialTroops(),
            source.initialCaptureOwnership()
        );
    }

    private RoomDefinition createDefaultDefinition(String roomId) {
        String normalized = normalizeRoomId(roomId);
        int index = parseRoomIndex(normalized);
        RoomCommonConfig commonConfig = new RoomCommonConfig(
            RoomMatchingConfig.defaults(),
            buildDimensionId(normalized),
            RoomCommonConfig.defaultQueueSpawn(),
            0,
            RoomVehicleBackend.defaultValue(),
            RoomPlayerStateConfig.defaults(),
            0,
            0,
            "CampA",
            "CampB",
            "",
            "",
            40.0D,
            RoomResultConfig.defaults(),
            false,
            false,
            false,
            false,
            false,
            false,
            false
        );
        RoomConquestConfig conquestConfig = RoomConquestConfig.defaults();
        RoomDefinition base = new RoomDefinition(
            normalized,
            "房间 " + index,
            RoomGameplayId.CONQUEST,
            RoomLifecycleState.DISABLED,
            commonConfig,
            conquestConfig,
            RoomBreakthroughConfig.defaults()
        );
        return new RoomDefinition(
            base.roomId(),
            base.displayName(),
            RoomGameplayId.CONQUEST,
            base.lifecycleState(),
            base.commonConfig(),
            base.conquestConfig(),
            base.breakthroughConfig()
        );
    }

    private ProgressSpeedMigrationData readProgressSpeedMigrationData(Path file) {
        if (file == null || Files.notExists(file)) {
            return ProgressSpeedMigrationData.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return ProgressSpeedMigrationData.from(root);
        } catch (Exception ignored) {
            return ProgressSpeedMigrationData.empty();
        }
    }

    private Optional<RoomDefinition> readLegacyDefinition(String roomId) {
        Path legacyFile = ServerDataPaths.getLegacyRoomDataFile(server, legacyRoomFileId(roomId));
        if (!Files.exists(legacyFile)) {
            return Optional.empty();
        }
        LegacyRoomDefinition legacy = read(legacyFile, LegacyRoomDefinition.class, LegacyRoomDefinition::new);
        return Optional.ofNullable(legacy.toDefinition(roomId));
    }

    private String legacyRoomFileId(String roomId) {
        return String.format(Locale.ROOT, "Room_%02d", parseRoomIndex(roomId));
    }

    private List<String> defaultRoomIds() {
        List<String> result = new ArrayList<>(DEFAULT_ROOM_COUNT);
        for (int i = 1; i <= DEFAULT_ROOM_COUNT; i++) {
            result.add(String.format(Locale.ROOT, "room_%02d", i));
        }
        return result;
    }

    private String normalizeRoomId(String roomId) {
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

    private String buildDimensionId(String roomId) {
        return "gd656conquest:" + normalizeRoomId(roomId);
    }

    private int parseRoomIndex(String roomId) {
        if (roomId == null || roomId.length() < 2) {
            return 1;
        }
        try {
            return Integer.parseInt(roomId.substring(roomId.length() - 2));
        } catch (Exception exception) {
            return 1;
        }
    }

    private static final class StoredRoomDefinition {
        private int version;
        private CommonConfig commonConfig;
        private ConquestConfig conquestConfig;
        private RoomBreakthroughConfig breakthroughConfig;
        private RoomDefinition definition;

        public static StoredRoomDefinition from(RoomDefinition definition) {
            StoredRoomDefinition stored = new StoredRoomDefinition();
            stored.version = STORAGE_VERSION;
            stored.definition = definition;
            return stored;
        }

        public RoomDefinition toDefinition(String fallbackRoomId, ProgressSpeedMigrationData progressSpeedMigration) {
            if (definition != null) {
                return upgradeLegacyRevivedInvulnerabilityIfNeeded(
                    upgradeLegacyProgressSpeedIfNeeded(definition, version),
                    version
                );
            }
            if (commonConfig != null || conquestConfig != null || breakthroughConfig != null) {
                CommonConfig normalizedCommon = commonConfig == null
                    ? CommonConfig.defaults(fallbackRoomId)
                    : commonConfig.normalized(fallbackRoomId);
                ConquestConfig normalizedConquest = conquestConfig == null
                    ? ConquestConfig.defaults()
                    : conquestConfig.normalized();
                RoomBreakthroughConfig normalizedBreakthrough = breakthroughConfig == null
                    ? RoomBreakthroughConfig.defaults()
                    : breakthroughConfig;
                double resolvedProgressSpeed = upgradeLegacyProgressSpeedIfNeeded(
                    progressSpeedMigration.resolve(normalizedCommon.progressSpeed()),
                    version
                );
                return new RoomDefinition(
                    normalizedCommon.roomId(),
                    normalizedCommon.displayName(),
                    normalizedCommon.gameplayId(),
                    normalizedCommon.lifecycleState(),
                    new RoomCommonConfig(
                        normalizedCommon.matchingConfig(),
                        normalizedCommon.dimensionId(),
                        normalizedCommon.queueSpawn(),
                        normalizedCommon.respawnDelaySeconds(),
                        normalizedCommon.resolveVehicleBackend(),
                        normalizedCommon.resolvePlayerStateConfig(normalizedConquest.playerStateConfig()),
                        normalizedCommon.resolveOutsideBoundaryDeathSeconds(normalizedConquest.outsideBoundaryDeathSeconds()),
                        normalizedCommon.resolveAircraftOutsideBoundaryCleanupSeconds(),
                        normalizedCommon.resolveCampADisplayName(normalizedConquest.captureConfig()),
                        normalizedCommon.resolveCampBDisplayName(normalizedConquest.captureConfig()),
                        normalizedCommon.resolveCampAVoicePack(),
                        normalizedCommon.resolveCampBVoicePack(),
                        resolvedProgressSpeed,
                        normalizedCommon.resultConfig(),
                        normalizedCommon.resolveDisableHunger(normalizedConquest.disableHunger()),
                        normalizedCommon.resolveEnableAutoTeamSwitchPrompt(normalizedConquest.enableAutoTeamSwitchPrompt()),
                        normalizedCommon.resolveDisableInventoryChange(normalizedConquest.disableInventoryChange()),
                        normalizedCommon.resolveEnableGoatHorn(false),
                        normalizedCommon.enablePlayerDowned(),
                        normalizedCommon.enableBackAgainstWall(),
                        normalizedCommon.enableRandomLoadout(),
                        normalizedCommon.enableRandomAttachments()
                    ),
                    new RoomConquestConfig(
                        normalizedConquest.equipmentConfig(),
                        normalizedConquest.teamSpawnGroups(),
                        normalizedConquest.teamDeployIcons(),
                        normalizedCommon.deployViewPosition(),
                        normalizedConquest.sharedBoundary(),
                        normalizedConquest.campABoundary(),
                        normalizedConquest.campBBoundary(),
                        normalizedConquest.captureConfig()
                    ),
                    normalizedBreakthrough
                );
            }
            return fallbackRoomId == null || fallbackRoomId.isBlank() ? null : new RoomDefinition(
                fallbackRoomId,
                RoomGameplayId.CONQUEST,
                RoomLifecycleState.DISABLED
            );
        }

        public boolean shouldRewrite() {
            return version < STORAGE_VERSION || definition == null;
        }
    }

    private static final class LegacyRoomDefinition {
        private String roomId;
        private String displayName;
        private String gameplayType;
        private String state;
        private String dimensionId;
        private LegacyGameplayConfig tdmConfig;
        private LegacyGameplayConfig captureConfig;

        public RoomDefinition toDefinition(String fallbackRoomId) {
            LegacyGameplayConfig gameplayConfig = captureConfig != null ? captureConfig : tdmConfig;
            String normalizedRoomId = fallbackRoomId == null || fallbackRoomId.isBlank() ? normalizeLegacyRoomId(roomId) : fallbackRoomId;
            RoomGameplayId gameplayId = parseLegacyGameplayId(gameplayType);
            RoomLifecycleState lifecycleState = parseLegacyLifecycleState(state);
            RoomMatchingConfig matchingConfig = gameplayConfig == null
                ? RoomMatchingConfig.defaults()
                : gameplayConfig.toMatchingConfig();
            RoomCommonConfig commonConfig = new RoomCommonConfig(
                matchingConfig,
                dimensionId == null || dimensionId.isBlank() ? "gd656conquest:" + normalizedRoomId : dimensionId,
                RoomCommonConfig.defaultQueueSpawn(),
                gameplayConfig == null ? 0 : Math.max(0, gameplayConfig.respawnDelaySeconds),
                RoomVehicleBackend.defaultValue(),
                gameplayConfig == null ? RoomPlayerStateConfig.defaults() : gameplayConfig.toPlayerStateConfig(),
                0,
                0,
                gameplayConfig == null ? "CampA" : gameplayConfig.resolveCampADisplayName(),
                gameplayConfig == null ? "CampB" : gameplayConfig.resolveCampBDisplayName(),
                "",
                "",
                40.0D,
                RoomResultConfig.defaults(),
                gameplayConfig != null && gameplayConfig.disableHunger(),
                gameplayConfig != null && gameplayConfig.enableAutoTeamSwitchPrompt(),
                gameplayConfig != null && gameplayConfig.disableInventoryChange(),
                false,
                false,
                false,
                false
            );
            RoomConquestConfig conquestConfig = new RoomConquestConfig(
                RoomEquipmentConfig.defaults(),
                gameplayConfig == null ? Map.of() : gameplayConfig.toTeamSpawnGroups(),
                Map.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                gameplayConfig == null ? RoomCaptureConfig.defaults() : gameplayConfig.toCaptureConfig()
            );
            return new RoomDefinition(
                normalizedRoomId,
                displayName == null || displayName.isBlank() ? normalizedRoomId : displayName,
                gameplayId,
                lifecycleState,
                commonConfig,
                conquestConfig,
                RoomBreakthroughConfig.defaults()
            );
        }

        private String normalizeLegacyRoomId(String value) {
            if (value == null || value.isBlank()) {
                return "room_01";
            }
            String trimmed = value.trim();
            if (trimmed.matches("(?i)room_\\d{2}")) {
                return trimmed.toLowerCase(Locale.ROOT);
            }
            if (trimmed.matches("(?i)room\\d{2}")) {
                return "room_" + trimmed.substring(trimmed.length() - 2).toLowerCase(Locale.ROOT);
            }
            return trimmed.toLowerCase(Locale.ROOT);
        }

        private RoomGameplayId parseLegacyGameplayId(String value) {
            if (value == null || value.isBlank()) {
                return RoomGameplayId.CONQUEST;
            }
            return "BREAKTHROUGH".equalsIgnoreCase(value.trim())
                ? RoomGameplayId.BREAKTHROUGH
                : RoomGameplayId.CONQUEST;
        }

        private RoomLifecycleState parseLegacyLifecycleState(String value) {
            if (value == null || value.isBlank()) {
                return RoomLifecycleState.DISABLED;
            }
            try {
                return RoomLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return RoomLifecycleState.DISABLED;
            }
        }
    }

    private static final class LegacyGameplayConfig {
        private int queueMinStartPlayers;
        private int queueMaxPlayers;
        private int queueWaitAfterMinSeconds;
        private int queueWaitAfterMaxSeconds;
        private LegacyTeamGroup redTeam;
        private LegacyTeamGroup blueTeam;
        private String playerGameMode;
        private int respawnDelaySeconds;
        private double respawnInvulnerableSeconds;
        private Double revivedInvulnerableSeconds;
        private Boolean boolDisableHunger;
        private Boolean boolEnableAutoTeamSwitchPrompt;
        private Boolean boolDisableInventoryChange;
        private Boolean allowTeamChangeOutOfCombat;
        private Boolean disablePlayerBackpack;
        private int drawDurationMinutes;
        private int targetScore;
        private int captureScoreIntervalSeconds;

        private RoomMatchingConfig toMatchingConfig() {
            java.util.Set<RoomTeamId> enabledTeams = new java.util.LinkedHashSet<>();
            if (redTeam != null && redTeam.enabled()) {
                enabledTeams.add(RoomTeamId.CAMP_A);
            }
            if (blueTeam != null && blueTeam.enabled()) {
                enabledTeams.add(RoomTeamId.CAMP_B);
            }
            if (enabledTeams.isEmpty()) {
                enabledTeams.add(RoomTeamId.CAMP_A);
                enabledTeams.add(RoomTeamId.CAMP_B);
            }
            int totalPlayers = Math.max(2, queueMaxPlayers);
            int campAPlayers = enabledTeams.contains(RoomTeamId.CAMP_A) ? Math.max(1, (totalPlayers + 1) / 2) : 0;
            int campBPlayers = enabledTeams.contains(RoomTeamId.CAMP_B) ? Math.max(1, totalPlayers / 2) : 0;
            return new RoomMatchingConfig(
                Math.max(1, queueMinStartPlayers),
                campAPlayers,
                campBPlayers,
                Math.max(0, queueWaitAfterMinSeconds),
                Math.max(0, queueWaitAfterMaxSeconds),
                enabledTeams
            );
        }

        private RoomPlayerStateConfig toPlayerStateConfig() {
            return new RoomPlayerStateConfig(
                playerGameMode == null || playerGameMode.isBlank() ? RoomPlayerStateConfig.defaults().gameMode() : playerGameMode.trim(),
                respawnInvulnerableSeconds,
                revivedInvulnerableSeconds == null
                    ? RoomPlayerStateConfig.defaults().revivedInvulnerableSeconds()
                    : revivedInvulnerableSeconds
            );
        }

        private Map<RoomTeamId, RoomSpawnGroupConfig> toTeamSpawnGroups() {
            Map<RoomTeamId, RoomSpawnGroupConfig> result = new LinkedHashMap<>();
            if (redTeam != null) {
                result.put(RoomTeamId.CAMP_A, redTeam.toSpawnGroup());
            }
            if (blueTeam != null) {
                result.put(RoomTeamId.CAMP_B, blueTeam.toSpawnGroup());
            }
            return result;
        }

        private String resolveCampADisplayName() {
            RoomCaptureConfig defaults = RoomCaptureConfig.defaults();
            String configured = defaults.campADisplayName();
            return configured == null || configured.isBlank() ? "CampA" : configured;
        }

        private String resolveCampBDisplayName() {
            RoomCaptureConfig defaults = RoomCaptureConfig.defaults();
            String configured = defaults.campBDisplayName();
            return configured == null || configured.isBlank() ? "CampB" : configured;
        }

        private RoomCaptureConfig toCaptureConfig() {
            RoomCaptureConfig defaults = RoomCaptureConfig.defaults();
            return new RoomCaptureConfig(
                defaults.points(),
                defaults.mapId(),
                defaults.campADisplayName(),
                defaults.campBDisplayName(),
                drawDurationMinutes > 0 ? drawDurationMinutes : defaults.drawDurationMinutes(),
                defaults.fullCaptureEndSeconds(),
                targetScore > 0 ? targetScore : defaults.targetScore(),
                captureScoreIntervalSeconds > 0 ? captureScoreIntervalSeconds : defaults.captureScoreIntervalSeconds(),
                defaults.scoreMethod(),
                defaults.campAInitialTroops(),
                defaults.campBInitialTroops(),
                defaults.initialCaptureOwnership()
            );
        }

        private boolean disableHunger() {
            return Boolean.TRUE.equals(boolDisableHunger);
        }

        private boolean enableAutoTeamSwitchPrompt() {
            return Boolean.TRUE.equals(boolEnableAutoTeamSwitchPrompt) || Boolean.TRUE.equals(allowTeamChangeOutOfCombat);
        }

        private boolean disableInventoryChange() {
            return Boolean.TRUE.equals(boolDisableInventoryChange) || Boolean.TRUE.equals(disablePlayerBackpack);
        }
    }

    private static final class LegacyTeamGroup {
        private boolean enabled;
        private List<RoomPosition> spawns;

        private boolean enabled() {
            return enabled;
        }

        private RoomSpawnGroupConfig toSpawnGroup() {
            return new RoomSpawnGroupConfig(enabled, spawns == null ? List.of() : List.copyOf(spawns));
        }
    }

    private static final class CommonConfig {
        private String roomId;
        private String displayName;
        private RoomGameplayId gameplayId;
        private RoomLifecycleState lifecycleState;
        private RoomMatchingConfig matchingConfig;
        private String dimensionId;
        private RoomPosition queueSpawn;
        private int respawnDelaySeconds;
        private RoomVehicleBackend vehicleBackend;
        private RoomPlayerStateConfig playerStateConfig;
        private Integer outsideBoundaryDeathSeconds;
        private Integer aircraftOutsideBoundaryCleanupSeconds;
        private String campADisplayName;
        private String campBDisplayName;
        private String campAVoicePack;
        private String campBVoicePack;
        private double progressSpeed;
        private RoomViewPosition deployViewPosition;
        private RoomResultConfig resultConfig;
        private Boolean disableHunger;
        private Boolean enableAutoTeamSwitchPrompt;
        private Boolean disableInventoryChange;
        private Boolean enableGoatHorn;
        private boolean enablePlayerDowned;
        private boolean enableBackAgainstWall;
        private boolean enableRandomLoadout;
        private boolean enableRandomAttachments;

        public static CommonConfig from(RoomDefinition definition) {
            CommonConfig config = new CommonConfig();
            if (definition == null) {
                return config;
            }
            config.roomId = definition.roomId();
            config.displayName = definition.displayName();
            config.gameplayId = definition.gameplayId();
            config.lifecycleState = definition.lifecycleState();
            config.matchingConfig = definition.commonConfig().matchingConfig();
            config.dimensionId = definition.commonConfig().dimensionId();
            config.queueSpawn = definition.commonConfig().queueSpawn();
            config.respawnDelaySeconds = definition.commonConfig().respawnDelaySeconds();
            config.vehicleBackend = definition.commonConfig().vehicleBackend();
            config.playerStateConfig = definition.commonConfig().playerStateConfig();
            config.outsideBoundaryDeathSeconds = definition.commonConfig().outsideBoundaryDeathSeconds();
            config.aircraftOutsideBoundaryCleanupSeconds = definition.commonConfig().aircraftOutsideBoundaryCleanupSeconds();
            config.campADisplayName = definition.commonConfig().campADisplayName();
            config.campBDisplayName = definition.commonConfig().campBDisplayName();
            config.campAVoicePack = definition.commonConfig().campAVoicePack();
            config.campBVoicePack = definition.commonConfig().campBVoicePack();
            config.progressSpeed = definition.commonConfig().progressSpeed();
            config.deployViewPosition = definition.conquestConfig().deployViewPosition();
            config.resultConfig = definition.commonConfig().resultConfig();
            config.disableHunger = definition.commonConfig().disableHunger();
            config.enableAutoTeamSwitchPrompt = definition.commonConfig().enableAutoTeamSwitchPrompt();
            config.disableInventoryChange = definition.commonConfig().disableInventoryChange();
            config.enableGoatHorn = definition.commonConfig().enableGoatHorn();
            config.enablePlayerDowned = definition.commonConfig().enablePlayerDowned();
            config.enableBackAgainstWall = definition.commonConfig().enableBackAgainstWall();
            config.enableRandomLoadout = definition.commonConfig().enableRandomLoadout();
            config.enableRandomAttachments = definition.commonConfig().enableRandomAttachments();
            return config;
        }

        public static CommonConfig defaults(String fallbackRoomId) {
            CommonConfig config = new CommonConfig();
            String roomId = fallbackRoomId == null || fallbackRoomId.isBlank() ? "room_01" : fallbackRoomId;
            config.roomId = roomId;
            config.displayName = roomId;
            config.gameplayId = RoomGameplayId.CONQUEST;
            config.lifecycleState = RoomLifecycleState.DISABLED;
            config.matchingConfig = RoomMatchingConfig.defaults();
            config.dimensionId = "";
            config.queueSpawn = RoomCommonConfig.defaultQueueSpawn();
            config.respawnDelaySeconds = 0;
            config.vehicleBackend = RoomVehicleBackend.defaultValue();
            config.playerStateConfig = RoomPlayerStateConfig.defaults();
            config.outsideBoundaryDeathSeconds = 0;
            config.aircraftOutsideBoundaryCleanupSeconds = 0;
            config.campADisplayName = "CampA";
            config.campBDisplayName = "CampB";
            config.campAVoicePack = "";
            config.campBVoicePack = "";
            config.progressSpeed = 40.0D;
            config.deployViewPosition = null;
            config.resultConfig = RoomResultConfig.defaults();
            config.disableHunger = false;
            config.enableAutoTeamSwitchPrompt = false;
            config.disableInventoryChange = false;
            config.enableGoatHorn = false;
            config.enablePlayerDowned = false;
            config.enableBackAgainstWall = false;
            config.enableRandomLoadout = false;
            config.enableRandomAttachments = false;
            return config;
        }

        public CommonConfig normalized(String fallbackRoomId) {
            CommonConfig defaults = defaults(fallbackRoomId);
            CommonConfig normalized = new CommonConfig();
            normalized.roomId = roomId == null || roomId.isBlank() ? defaults.roomId : roomId;
            normalized.displayName = displayName == null || displayName.isBlank() ? normalized.roomId : displayName;
            normalized.gameplayId = gameplayId == null ? RoomGameplayId.CONQUEST : gameplayId;
            normalized.lifecycleState = lifecycleState == null ? RoomLifecycleState.DISABLED : lifecycleState;
            normalized.matchingConfig = matchingConfig == null ? RoomMatchingConfig.defaults() : matchingConfig;
            normalized.dimensionId = dimensionId == null ? "" : dimensionId;
            normalized.queueSpawn = queueSpawn == null ? defaults.queueSpawn : queueSpawn;
            normalized.respawnDelaySeconds = Math.max(0, respawnDelaySeconds);
            normalized.vehicleBackend = vehicleBackend == null ? defaults.vehicleBackend : vehicleBackend;
            normalized.playerStateConfig = playerStateConfig;
            normalized.outsideBoundaryDeathSeconds = outsideBoundaryDeathSeconds == null ? null : Math.max(0, outsideBoundaryDeathSeconds);
            normalized.aircraftOutsideBoundaryCleanupSeconds = aircraftOutsideBoundaryCleanupSeconds == null ? null : Math.max(0, aircraftOutsideBoundaryCleanupSeconds);
            normalized.campADisplayName = campADisplayName == null ? null : campADisplayName.trim();
            normalized.campBDisplayName = campBDisplayName == null ? null : campBDisplayName.trim();
            normalized.campAVoicePack = campAVoicePack == null ? "" : campAVoicePack.trim();
            normalized.campBVoicePack = campBVoicePack == null ? "" : campBVoicePack.trim();
            normalized.progressSpeed = Double.isFinite(progressSpeed) && progressSpeed > 0.0D ? progressSpeed : defaults.progressSpeed;
            normalized.deployViewPosition = deployViewPosition;
            normalized.resultConfig = resultConfig == null ? RoomResultConfig.defaults() : resultConfig;
            normalized.disableHunger = disableHunger;
            normalized.enableAutoTeamSwitchPrompt = enableAutoTeamSwitchPrompt;
            normalized.disableInventoryChange = disableInventoryChange;
            normalized.enableGoatHorn = enableGoatHorn;
            normalized.enablePlayerDowned = enablePlayerDowned;
            normalized.enableBackAgainstWall = enableBackAgainstWall;
            normalized.enableRandomLoadout = enableRandomLoadout;
            normalized.enableRandomAttachments = enableRandomAttachments;
            return normalized;
        }

        public String roomId() {
            return roomId;
        }

        public String displayName() {
            return displayName;
        }

        public RoomGameplayId gameplayId() {
            return gameplayId;
        }

        public RoomLifecycleState lifecycleState() {
            return lifecycleState;
        }

        public RoomMatchingConfig matchingConfig() {
            return matchingConfig;
        }

        public RoomResultConfig resultConfig() {
            return resultConfig;
        }

        public String dimensionId() {
            return dimensionId;
        }

        public RoomPosition queueSpawn() {
            return queueSpawn;
        }

        public int respawnDelaySeconds() {
            return respawnDelaySeconds;
        }

        public RoomVehicleBackend resolveVehicleBackend() {
            return vehicleBackend == null ? RoomVehicleBackend.defaultValue() : vehicleBackend;
        }

        public RoomPlayerStateConfig resolvePlayerStateConfig(RoomPlayerStateConfig legacyFallback) {
            if (playerStateConfig != null) {
                return playerStateConfig;
            }
            return legacyFallback == null ? RoomPlayerStateConfig.defaults() : legacyFallback;
        }

        public int resolveOutsideBoundaryDeathSeconds(int legacyFallback) {
            return outsideBoundaryDeathSeconds == null ? Math.max(0, legacyFallback) : outsideBoundaryDeathSeconds;
        }

        public int resolveAircraftOutsideBoundaryCleanupSeconds() {
            return aircraftOutsideBoundaryCleanupSeconds == null ? 0 : aircraftOutsideBoundaryCleanupSeconds;
        }

        public String resolveCampAVoicePack() {
            return campAVoicePack == null ? "" : campAVoicePack;
        }

        public String resolveCampADisplayName(RoomCaptureConfig legacyFallback) {
            if (campADisplayName != null && !campADisplayName.isBlank()) {
                return campADisplayName;
            }
            if (legacyFallback != null && legacyFallback.campADisplayName() != null && !legacyFallback.campADisplayName().isBlank()) {
                return legacyFallback.campADisplayName();
            }
            return "CampA";
        }

        public String resolveCampBVoicePack() {
            return campBVoicePack == null ? "" : campBVoicePack;
        }

        public String resolveCampBDisplayName(RoomCaptureConfig legacyFallback) {
            if (campBDisplayName != null && !campBDisplayName.isBlank()) {
                return campBDisplayName;
            }
            if (legacyFallback != null && legacyFallback.campBDisplayName() != null && !legacyFallback.campBDisplayName().isBlank()) {
                return legacyFallback.campBDisplayName();
            }
            return "CampB";
        }

        public boolean resolveDisableHunger(boolean legacyFallback) {
            return disableHunger == null ? legacyFallback : disableHunger;
        }

        public boolean resolveEnableAutoTeamSwitchPrompt(boolean legacyFallback) {
            return enableAutoTeamSwitchPrompt == null ? legacyFallback : enableAutoTeamSwitchPrompt;
        }

        public boolean resolveDisableInventoryChange(boolean legacyFallback) {
            return disableInventoryChange == null ? legacyFallback : disableInventoryChange;
        }

        public boolean resolveEnableGoatHorn(boolean legacyFallback) {
            return enableGoatHorn == null ? legacyFallback : enableGoatHorn;
        }

        public double progressSpeed() {
            return progressSpeed;
        }

        public RoomViewPosition deployViewPosition() {
            return deployViewPosition;
        }

        public boolean enablePlayerDowned() {
            return enablePlayerDowned;
        }

        public boolean enableBackAgainstWall() {
            return enableBackAgainstWall;
        }

        public boolean enableRandomLoadout() {
            return enableRandomLoadout;
        }

        public boolean enableRandomAttachments() {
            return enableRandomAttachments;
        }
    }

    private record ProgressSpeedMigrationData(Double commonProgressSpeed, Double legacyCaptureProgressSpeed) {
        private static ProgressSpeedMigrationData empty() {
            return new ProgressSpeedMigrationData(null, null);
        }

        private static ProgressSpeedMigrationData from(JsonObject root) {
            if (root == null) {
                return empty();
            }
            JsonObject payload = getObject(root, "definition");
            JsonObject source = payload != null ? payload : root;
            Double commonProgressSpeed = readPositiveDouble(getObject(source, "commonConfig"), "progressSpeed");
            Double legacyCaptureProgressSpeed = readPositiveDouble(
                getObject(getObject(source, "conquestConfig"), "captureConfig"),
                "progressSpeed"
            );
            return new ProgressSpeedMigrationData(commonProgressSpeed, legacyCaptureProgressSpeed);
        }

        private double resolve(double fallback) {
            if (commonProgressSpeed != null) {
                return commonProgressSpeed;
            }
            if (legacyCaptureProgressSpeed != null) {
                return legacyCaptureProgressSpeed;
            }
            return fallback;
        }

        private static JsonObject getObject(JsonObject parent, String key) {
            if (parent == null || key == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
                return null;
            }
            return parent.getAsJsonObject(key);
        }

        private static Double readPositiveDouble(JsonObject parent, String key) {
            if (parent == null || key == null || !parent.has(key)) {
                return null;
            }
            try {
                double value = parent.get(key).getAsDouble();
                return Double.isFinite(value) && value > 0.0D ? value : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static RoomDefinition upgradeLegacyProgressSpeedIfNeeded(RoomDefinition definition, int storageVersion) {
        if (definition == null || storageVersion >= STORAGE_VERSION) {
            return definition;
        }
        double upgradedProgressSpeed = upgradeLegacyProgressSpeedIfNeeded(definition.commonConfig().progressSpeed(), storageVersion);
        if (Double.compare(upgradedProgressSpeed, definition.commonConfig().progressSpeed()) == 0) {
            return definition;
        }
        RoomCommonConfig upgradedCommon = new RoomCommonConfig(
            definition.commonConfig().matchingConfig(),
            definition.commonConfig().dimensionId(),
            definition.commonConfig().queueSpawn(),
            definition.commonConfig().respawnDelaySeconds(),
            definition.commonConfig().vehicleBackend(),
            definition.commonConfig().playerStateConfig(),
            definition.commonConfig().outsideBoundaryDeathSeconds(),
            definition.commonConfig().aircraftOutsideBoundaryCleanupSeconds(),
            definition.commonConfig().campADisplayName(),
            definition.commonConfig().campBDisplayName(),
            definition.commonConfig().campAVoicePack(),
            definition.commonConfig().campBVoicePack(),
            upgradedProgressSpeed,
            definition.commonConfig().resultConfig(),
            definition.commonConfig().disableHunger(),
            definition.commonConfig().enableAutoTeamSwitchPrompt(),
            definition.commonConfig().disableInventoryChange(),
            definition.commonConfig().enableGoatHorn(),
            definition.commonConfig().enablePlayerDowned(),
            definition.commonConfig().enableBackAgainstWall(),
            definition.commonConfig().enableRandomLoadout(),
            definition.commonConfig().enableRandomAttachments()
        );
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            upgradedCommon,
            definition.conquestConfig(),
            definition.breakthroughConfig()
        );
    }

    private static RoomDefinition upgradeLegacyRevivedInvulnerabilityIfNeeded(RoomDefinition definition, int storageVersion) {
        if (definition == null || storageVersion >= 9) {
            return definition;
        }
        RoomPlayerStateConfig playerStateConfig = definition.commonConfig().playerStateConfig();
        if (playerStateConfig == null || playerStateConfig.revivedInvulnerableSeconds() > 0.0D) {
            return definition;
        }
        RoomPlayerStateConfig upgradedPlayerStateConfig = new RoomPlayerStateConfig(
            playerStateConfig.gameMode(),
            playerStateConfig.invulnerableSeconds(),
            RoomPlayerStateConfig.defaults().revivedInvulnerableSeconds()
        );
        RoomCommonConfig upgradedCommon = new RoomCommonConfig(
            definition.commonConfig().matchingConfig(),
            definition.commonConfig().dimensionId(),
            definition.commonConfig().queueSpawn(),
            definition.commonConfig().respawnDelaySeconds(),
            definition.commonConfig().vehicleBackend(),
            upgradedPlayerStateConfig,
            definition.commonConfig().outsideBoundaryDeathSeconds(),
            definition.commonConfig().aircraftOutsideBoundaryCleanupSeconds(),
            definition.commonConfig().campADisplayName(),
            definition.commonConfig().campBDisplayName(),
            definition.commonConfig().campAVoicePack(),
            definition.commonConfig().campBVoicePack(),
            definition.commonConfig().progressSpeed(),
            definition.commonConfig().resultConfig(),
            definition.commonConfig().disableHunger(),
            definition.commonConfig().enableAutoTeamSwitchPrompt(),
            definition.commonConfig().disableInventoryChange(),
            definition.commonConfig().enableGoatHorn(),
            definition.commonConfig().enablePlayerDowned(),
            definition.commonConfig().enableBackAgainstWall(),
            definition.commonConfig().enableRandomLoadout(),
            definition.commonConfig().enableRandomAttachments()
        );
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            upgradedCommon,
            definition.conquestConfig(),
            definition.breakthroughConfig()
        );
    }

    private static double upgradeLegacyProgressSpeedIfNeeded(double progressSpeed, int storageVersion) {
        double normalized = Double.isFinite(progressSpeed) && progressSpeed > 0.0D ? progressSpeed : 40.0D;
        if (storageVersion >= STORAGE_VERSION) {
            return normalized;
        }
        // 旧版本这里存的是倍率；新版改成 0->100 所需秒数。
        if (normalized <= 10.0D) {
            return 6.0D / normalized;
        }
        return normalized;
    }

    private static final class ConquestConfig {
        private Map<RoomTeamId, RoomPosition> teamSpawns;
        private Map<RoomTeamId, RoomSpawnGroupConfig> teamSpawnGroups;
        private Map<RoomTeamId, RoomPosition> teamDeployIcons;
        private RoomPlayerStateConfig playerStateConfig;
        private boolean disableHunger;
        private boolean enableAutoTeamSwitchPrompt;
        private boolean disableInventoryChange;
        private int outsideBoundaryDeathSeconds;
        private int aircraftOutsideBoundaryCleanupSeconds;
        private List<RoomPosition> sharedBoundary;
        private List<RoomPosition> campABoundary;
        private List<RoomPosition> campBBoundary;
        private RoomEquipmentConfig equipmentConfig;
        private RoomCaptureConfig captureConfig;

        public static ConquestConfig from(RoomDefinition definition) {
            ConquestConfig config = new ConquestConfig();
            if (definition == null) {
                return config;
            }
            config.teamSpawns = Map.of();
            config.teamSpawnGroups = definition.conquestConfig().teamSpawnGroups();
            config.teamDeployIcons = definition.conquestConfig().teamDeployIcons();
            config.playerStateConfig = definition.commonConfig().playerStateConfig();
            config.disableHunger = definition.commonConfig().disableHunger();
            config.enableAutoTeamSwitchPrompt = definition.commonConfig().enableAutoTeamSwitchPrompt();
            config.disableInventoryChange = definition.commonConfig().disableInventoryChange();
            config.outsideBoundaryDeathSeconds = definition.commonConfig().outsideBoundaryDeathSeconds();
            config.aircraftOutsideBoundaryCleanupSeconds = definition.commonConfig().aircraftOutsideBoundaryCleanupSeconds();
            config.sharedBoundary = definition.conquestConfig().sharedBoundary();
            config.campABoundary = definition.conquestConfig().campABoundary();
            config.campBBoundary = definition.conquestConfig().campBBoundary();
            config.equipmentConfig = definition.conquestConfig().equipmentConfig();
            config.captureConfig = definition.conquestConfig().captureConfig();
            return config;
        }

        public static ConquestConfig defaults() {
            ConquestConfig config = new ConquestConfig();
            config.teamSpawns = Map.of();
            config.teamSpawnGroups = Map.of();
            config.teamDeployIcons = Map.of();
            config.playerStateConfig = RoomPlayerStateConfig.defaults();
            config.disableHunger = false;
            config.enableAutoTeamSwitchPrompt = false;
            config.disableInventoryChange = false;
            config.outsideBoundaryDeathSeconds = 0;
            config.aircraftOutsideBoundaryCleanupSeconds = 0;
            config.sharedBoundary = List.of();
            config.campABoundary = List.of();
            config.campBBoundary = List.of();
            config.equipmentConfig = RoomEquipmentConfig.defaults();
            config.captureConfig = RoomCaptureConfig.defaults();
            return config;
        }

        public ConquestConfig normalized() {
            ConquestConfig normalized = new ConquestConfig();
            normalized.teamSpawns = teamSpawns == null ? Map.of() : Map.copyOf(teamSpawns);
            normalized.teamSpawnGroups = teamSpawnGroups == null ? Map.of() : Map.copyOf(teamSpawnGroups);
            normalized.teamDeployIcons = teamDeployIcons == null ? Map.of() : Map.copyOf(teamDeployIcons);
            normalized.playerStateConfig = playerStateConfig == null ? RoomPlayerStateConfig.defaults() : playerStateConfig;
            normalized.disableHunger = disableHunger;
            normalized.enableAutoTeamSwitchPrompt = enableAutoTeamSwitchPrompt;
            normalized.disableInventoryChange = disableInventoryChange;
            normalized.outsideBoundaryDeathSeconds = Math.max(0, outsideBoundaryDeathSeconds);
            normalized.aircraftOutsideBoundaryCleanupSeconds = Math.max(0, aircraftOutsideBoundaryCleanupSeconds);
            normalized.sharedBoundary = sharedBoundary == null ? List.of() : List.copyOf(sharedBoundary);
            normalized.campABoundary = campABoundary == null ? List.of() : List.copyOf(campABoundary);
            normalized.campBBoundary = campBBoundary == null ? List.of() : List.copyOf(campBBoundary);
            normalized.equipmentConfig = equipmentConfig == null ? RoomEquipmentConfig.defaults() : equipmentConfig;
            normalized.captureConfig = captureConfig == null ? RoomCaptureConfig.defaults() : captureConfig;
            return normalized;
        }

        public Map<RoomTeamId, RoomPosition> teamSpawns() {
            return teamSpawns;
        }

        public Map<RoomTeamId, RoomSpawnGroupConfig> teamSpawnGroups() {
            return teamSpawnGroups;
        }

        public Map<RoomTeamId, RoomPosition> teamDeployIcons() {
            return teamDeployIcons;
        }

        public RoomPlayerStateConfig playerStateConfig() {
            return playerStateConfig;
        }

        public boolean disableHunger() {
            return disableHunger;
        }

        public boolean enableAutoTeamSwitchPrompt() {
            return enableAutoTeamSwitchPrompt;
        }

        public boolean disableInventoryChange() {
            return disableInventoryChange;
        }

        public int outsideBoundaryDeathSeconds() {
            return outsideBoundaryDeathSeconds;
        }

        public int aircraftOutsideBoundaryCleanupSeconds() {
            return aircraftOutsideBoundaryCleanupSeconds;
        }

        public List<RoomPosition> sharedBoundary() {
            return sharedBoundary;
        }

        public List<RoomPosition> campABoundary() {
            return campABoundary;
        }

        public List<RoomPosition> campBBoundary() {
            return campBBoundary;
        }

        public RoomEquipmentConfig equipmentConfig() {
            return equipmentConfig;
        }

        public RoomCaptureConfig captureConfig() {
            return captureConfig;
        }
    }
}
