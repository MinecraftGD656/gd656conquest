package org.mods.gd656conquest.server.data.roomdefinition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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
import org.mods.gd656conquest.server.room.model.RoomDeathmatchConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomGunmasterConfig;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomPlayerStateConfig;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomResultConfig;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomTroopBalanceReference;
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
    private static final int DEFAULT_ROOM_COUNT = 24;
    private static final int STORAGE_VERSION = 15;

    private static final Gson REPOSITORY_GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(RoomDefinition.class, new RoomDefinitionTypeAdapter())
        .create();

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
        
        RoomDefinition definition = readWithGson(file);
        if (definition == null) {
            return Optional.empty();
        }
        
        RoomDefinition normalized = validateAndNormalize(definition);
        cache.put(normalized.roomId(), normalized);
        if (!normalized.equals(definition)) {
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
            RoomDefinition definition = readWithGson(file);
            if (definition != null) {
                return definition;
            }
        }
        
        RoomDefinition created = createDefaultDefinition(roomId);
        persistNow(created);
        return created;
    }

    private RoomDefinition readWithGson(Path path) {
        try (java.io.Reader reader = Files.newBufferedReader(path)) {
            return REPOSITORY_GSON.fromJson(reader, RoomDefinition.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private void persist(RoomDefinition definition) {
        persistNow(definition);
    }

    private void persistNow(RoomDefinition definition) {
        Path path = ServerDataPaths.getRoomDefinitionFile(server, definition.roomId());
        ensurePath(path);
        try (java.io.Writer writer = Files.newBufferedWriter(path)) {
            REPOSITORY_GSON.toJson(definition, writer);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法写入数据文件: " + path, exception);
        }
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
            base.commonConfig().playerMaxY(),
            base.commonConfig().aircraftPassengerMaxY(),
            base.commonConfig().campADisplayName(),
            base.commonConfig().campBDisplayName(),
            base.commonConfig().campAVoicePack(),
            base.commonConfig().campBVoicePack(),
            base.commonConfig().progressSpeed(),
            base.commonConfig().matchLoadViewPosition(),
            base.commonConfig().resultConfig(),
            base.commonConfig().disableHunger(),
            base.commonConfig().enableAutoTeamSwitchPrompt(),
            base.commonConfig().disableInventoryChange(),
            base.commonConfig().enableGoatHorn(),
            base.commonConfig().enablePlayerDowned(),
            base.commonConfig().enableRespawnCountdownWhileDowned(),
            base.commonConfig().enableBackAgainstWall(),
            base.commonConfig().enableRandomLoadout(),
            base.commonConfig().enableRandomAttachments(),
            base.commonConfig().enableParachute(),
            base.commonConfig().enableDeathSupportDrop(),
            base.commonConfig().enableGiveArrow(),
            base.commonConfig().disableUndyingTotem(),
            base.commonConfig().allowPlayerCampSwitch(),
            base.commonConfig().supportCampAHeroKits(),
            base.commonConfig().supportCampBHeroKits(),
            base.commonConfig().supportCampAShellings(),
            base.commonConfig().supportCampBShellings(),
            base.commonConfig().campAPrimaryWeaponIds(),
            base.commonConfig().campASecondaryWeaponIds(),
            base.commonConfig().campAMeleeWeaponIds(),
            base.commonConfig().campAGadget1Ids(),
            base.commonConfig().campAGadget2Ids(),
            base.commonConfig().campAThrowableIds(),
            base.commonConfig().campBPrimaryWeaponIds(),
            base.commonConfig().campBSecondaryWeaponIds(),
            base.commonConfig().campBMeleeWeaponIds(),
            base.commonConfig().campBGadget1Ids(),
            base.commonConfig().campBGadget2Ids(),
            base.commonConfig().campBThrowableIds(),
            base.commonConfig().campAEquipmentSetId(),
            base.commonConfig().campBEquipmentSetId(),
            base.commonConfig().mapName(),
            base.commonConfig().roomDescription()
        );
        org.mods.gd656conquest.server.room.model.IRoomGameplayConfig gameplayConfig;
        if (base.gameplayConfig() instanceof RoomConquestConfig conquest) {
            gameplayConfig = new RoomConquestConfig(
                conquest.teamSpawnGroups(),
                conquest.teamDeployIcons(),
                conquest.deployViewPosition(),
                conquest.sharedBoundary(),
                conquest.campABoundary(),
                conquest.campBBoundary(),
                normalizeCaptureConfig(conquest.captureConfig()),
                conquest.ammoBoxPositions(),
                conquest.medicalBoxPositions()
            );
        } else if (base.gameplayConfig() instanceof RoomDeathmatchConfig deathmatch) {
            gameplayConfig = new RoomDeathmatchConfig(
                deathmatch.mapId(),
                deathmatch.matchDurationMinutes(),
                deathmatch.targetScore(),
                deathmatch.startCountdownSeconds(),
                deathmatch.respawnSeconds(),
                deathmatch.deployViewPosition(),
                deathmatch.campABoundary(),
                deathmatch.campBBoundary(),
                deathmatch.campASpawnGroup(),
                deathmatch.campBSpawnGroup(),
                deathmatch.ammoBoxPositions(),
                deathmatch.medicalBoxPositions()
            );
        } else if (base.gameplayConfig() instanceof RoomGunmasterConfig gunmaster) {
            gameplayConfig = new RoomGunmasterConfig(
                gunmaster.mapId(),
                gunmaster.matchDurationMinutes(),
                gunmaster.targetScore(),
                gunmaster.startCountdownSeconds(),
                gunmaster.respawnSeconds(),
                gunmaster.deployViewPosition(),
                gunmaster.campABoundary(),
                gunmaster.campBBoundary(),
                gunmaster.campASpawnGroup(),
                gunmaster.campBSpawnGroup(),
                gunmaster.ammoBoxPositions(),
                gunmaster.medicalBoxPositions(),
                gunmaster.groupingMode(),
                gunmaster.soloSpawnPositions(),
                gunmaster.soloBoundary(),
                gunmaster.initialWeaponPool(),
                gunmaster.middleWeaponPool(),
                gunmaster.finalWeaponPool(),
                gunmaster.enabledMiddleWeaponCount(),
                gunmaster.killsPerWeaponSwitch()
            );
        } else {
            gameplayConfig = base.gameplayConfig();
        }
        return new RoomDefinition(
            roomId,
            base.displayName(),
            base.gameplayId(),
            base.lifecycleState(),
            commonConfig,
            gameplayConfig
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
            source.fullCaptureResponseMode(),
            source.antiBaseNukeTriggerSeconds(),
            source.antiBaseNukeRespawnDelaySeconds(),
            source.antiBaseNukeTriggerCooldownSeconds(),
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
            0,
            0,
            "CampA",
            "CampB",
            "",
            "",
            40.0D,
            null, // matchLoadViewPosition
            RoomResultConfig.defaults(),
            false, // disableHunger
            false, // enableAutoTeamSwitchPrompt
            false, // disableInventoryChange
            false, // enableGoatHorn
            false, // enablePlayerDowned
            false, // enableRespawnCountdownWhileDowned
            false, // enableBackAgainstWall
            false, // enableRandomLoadout
            false, // enableRandomAttachments
            false, // enableParachute
            true,  // enableDeathSupportDrop
            false, // enableGiveArrow
            false, // disableUndyingTotem
            true,  // allowPlayerCampSwitch
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "",
            "",
            "",
            ""
        );
        return new RoomDefinition(
            normalized,
            "房间 " + index,
            RoomGameplayId.CONQUEST,
            RoomLifecycleState.DISABLED,
            commonConfig,
            RoomConquestConfig.defaults()
        );
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

}
