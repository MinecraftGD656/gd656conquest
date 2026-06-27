package org.mods.gd656conquest.server.room.vehicle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSnapshot;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayVehicleDeployRuleProvider;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigEntry;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigRepository;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomVehicleRuntimeService {
    private static final long CONFIG_SYNC_INTERVAL_TICKS = 20L;
    private static final long CREATIVE_AMMO_CHECK_INTERVAL_TICKS = 10L * 20L;
    private static final long NEVER_RESPAWN_TICK = Long.MAX_VALUE;
    private static final String MANAGED_ROOM_ID_KEY = "gd656conquestManagedRoomId";
    private static final String MANAGED_ENTRY_ID_KEY = "gd656conquestManagedEntryId";

    private final MinecraftServer server;
    private final VehicleConfigRepository vehicleConfigRepository;
    private final Map<String, RoomVehicleRuntimeState> roomStates = new ConcurrentHashMap<>();

    public RoomVehicleRuntimeService(MinecraftServer server, VehicleConfigRepository vehicleConfigRepository) {
        this.server = Objects.requireNonNull(server, "server");
        this.vehicleConfigRepository = Objects.requireNonNull(vehicleConfigRepository, "vehicleConfigRepository");
    }

    public boolean isCompatAvailable() {
        return VehicleCompatFacade.isCompatAvailable(RoomVehicleBackend.YWZJ)
            || VehicleCompatFacade.isCompatAvailable(RoomVehicleBackend.SUPERBWARFARE);
    }

    public VehicleConfigRepository configRepository() {
        return vehicleConfigRepository;
    }

    public RoomVehicleRuntimeState getOrCreateRoomState(String roomId) {
        return roomStates.computeIfAbsent(normalizeRoomId(roomId), ignored -> new RoomVehicleRuntimeState());
    }

    public List<VehicleRuntimeRecord> getRecords(String roomId) {
        return new ArrayList<>(getOrCreateRoomState(roomId).getRecordsByEntryId().values());
    }

    public Optional<VehicleRuntimeRecord> getRecord(String roomId, String entryId) {
        if (entryId == null || entryId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(getOrCreateRoomState(roomId).getRecordsByEntryId().get(entryId));
    }

    public VehicleRuntimeRecord upsertRecord(String roomId, VehicleConfigEntry entry) {
        String entryId = entry == null ? "" : entry.getEntryId();
        VehicleRuntimeRecord record = getOrCreateRoomState(roomId)
            .getRecordsByEntryId()
            .computeIfAbsent(entryId, VehicleRuntimeRecord::new);
        record.applyConfig(entry);
        return record;
    }

    public void syncConfiguredEntries(String roomId, String gameplayId, RoomVehicleBackend backend) {
        List<VehicleConfigEntry> configuredEntries = vehicleConfigRepository.getEntries(roomId, gameplayId, backend);
        RoomVehicleRuntimeState runtimeState = getOrCreateRoomState(roomId);
        Set<String> activeEntryIds = new LinkedHashSet<>();
        for (VehicleConfigEntry entry : configuredEntries) {
            if (entry == null || !entry.isEnabled() || entry.getEntryId().isBlank()) {
                continue;
            }
            activeEntryIds.add(entry.getEntryId());
            upsertRecord(roomId, entry);
        }
        runtimeState.getRecordsByEntryId().keySet().removeIf(entryId -> !activeEntryIds.contains(entryId));
    }

    public void tickRoom(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        RoomVehicleRuntimeState roomState = getOrCreateRoomState(room.roomId());
        if (roomState.getLastConfigSyncTick() < 0L || gameTick - roomState.getLastConfigSyncTick() >= CONFIG_SYNC_INTERVAL_TICKS) {
            syncConfiguredEntries(room.roomId(), room.gameplayId().value(), ConquestDataManager.of(server).serverConfig().getVehicleBackend());
            roomState.setLastConfigSyncTick(gameTick);
        }
        boolean shouldCheckCreativeAmmo = gameTick % CREATIVE_AMMO_CHECK_INTERVAL_TICKS == 0L;
        for (VehicleRuntimeRecord record : roomState.getRecordsByEntryId().values()) {
            refreshRecord(room, state, record, gameTick, shouldCheckCreativeAmmo);
        }
    }

    public void trackSpawnedEntity(String roomId, String entryId, Entity entity, long gameTick) {
        if (entryId == null || entryId.isBlank() || entity == null) {
            return;
        }
        markManagedVehicleEntity(roomId, entryId, entity);
        VehicleRuntimeRecord record = getOrCreateRoomState(roomId)
            .getRecordsByEntryId()
            .computeIfAbsent(entryId, VehicleRuntimeRecord::new);
        VehicleCompatFacade.snapshot(entity).ifPresent(snapshot -> {
            record.applySnapshot(snapshot, gameTick);
            record.setWaitingUntilTick(-1L);
            record.setOwnershipState(VehicleOwnershipState.FRIENDLY);
        });
    }

    public void refreshRecordSnapshot(String roomId, String entryId, long gameTick) {
        VehicleRuntimeRecord record = getRecord(roomId, entryId).orElse(null);
        if (record == null || record.getSpawnedEntityUuid() == null) {
            return;
        }
        Entity entity = findEntity(record.getSpawnedEntityUuid()).orElse(null);
        if (entity == null || !VehicleCompatFacade.isVehicleEntity(entity)) {
            record.clearSpawnedEntity();
            return;
        }
        Optional<VehicleCompatSnapshot> snapshot = VehicleCompatFacade.snapshot(entity);
        snapshot.ifPresent(value -> record.applySnapshot(value, gameTick));
    }

    public boolean markVehicleDestroyed(RoomDefinition room, RunningRuntimeState state, UUID entityUuid, long gameTick) {
        if (entityUuid == null) {
            return false;
        }
        RoomVehicleRuntimeState roomState = getOrCreateRoomState(room == null ? "" : room.roomId());
        RoomGameplayVehicleDeployRuleProvider gameplayRules = RoomGameplayVehicleDeployRuleProvider.resolve(state);
        for (VehicleRuntimeRecord record : roomState.getRecordsByEntryId().values()) {
            if (record == null || !entityUuid.equals(record.getSpawnedEntityUuid())) {
                continue;
            }
            transitionToWaiting(room, state, record, gameplayRules, gameTick);
            return true;
        }
        return false;
    }

    public Optional<Entity> findEntity(UUID entityUuid) {
        if (entityUuid == null) {
            return Optional.empty();
        }
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityUuid);
            if (entity != null) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    public void markManagedVehicleEntity(String roomId, String entryId, Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentData().putString(MANAGED_ROOM_ID_KEY, normalizeRoomId(roomId));
        entity.getPersistentData().putString(MANAGED_ENTRY_ID_KEY, normalizeEntryId(entryId));
    }

    public Optional<String> resolveManagedRoomId(Entity entity) {
        if (entity == null || !VehicleCompatFacade.isManagedVehicleEntity(entity)) {
            return Optional.empty();
        }
        String roomId = normalizeRoomId(entity.getPersistentData().getString(MANAGED_ROOM_ID_KEY));
        return roomId.isBlank() ? Optional.empty() : Optional.of(roomId);
    }

    public Optional<String> resolveManagedEntryId(Entity entity) {
        if (entity == null || !VehicleCompatFacade.isManagedVehicleEntity(entity)) {
            return Optional.empty();
        }
        String entryId = normalizeEntryId(entity.getPersistentData().getString(MANAGED_ENTRY_ID_KEY));
        return entryId.isBlank() ? Optional.empty() : Optional.of(entryId);
    }

    public Optional<VehicleRuntimeRecord> findManagedRecord(Entity entity) {
        String roomId = resolveManagedRoomId(entity).orElse("");
        String entryId = resolveManagedEntryId(entity).orElse("");
        if (roomId.isBlank() || entryId.isBlank()) {
            return Optional.empty();
        }
        return getRecord(roomId, entryId);
    }

    public boolean shouldBlockFriendlyVehicleDamage(Entity entity, RunningRuntimeState state, RoomTeamId attackerTeam) {
        if (entity == null || state == null || attackerTeam == null) {
            return false;
        }
        VehicleRuntimeRecord record = findManagedRecord(entity).orElse(null);
        if (record == null) {
            return false;
        }
        RoomTeamId configuredTeam = VehicleRuntimeTeamResolver.resolveConfiguredTeam(record.getConfiguredTeam());
        if (configuredTeam == null) {
            return false;
        }
        if (record.getLogicalState() == VehicleLogicalState.ENEMY_CONTROLLED
            || record.getOwnershipState() == VehicleOwnershipState.ENEMY) {
            return configuredTeam != attackerTeam;
        }
        return configuredTeam == attackerTeam;
    }

    public int clearResidualManagedVehicles(String roomId, String entryId) {
        String normalizedRoomId = normalizeRoomId(roomId);
        String normalizedEntryId = normalizeEntryId(entryId);
        if (normalizedRoomId.isBlank() || normalizedEntryId.isBlank()) {
            return 0;
        }
        int cleared = 0;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null || !VehicleCompatFacade.isManagedVehicleEntity(entity)) {
                    continue;
                }
                if (!normalizedRoomId.equalsIgnoreCase(entity.getPersistentData().getString(MANAGED_ROOM_ID_KEY))) {
                    continue;
                }
                if (!normalizedEntryId.equalsIgnoreCase(entity.getPersistentData().getString(MANAGED_ENTRY_ID_KEY))) {
                    continue;
                }
                boolean destroyed = VehicleCompatFacade.destroyManagedVehicleNow(entity);
                if (!destroyed && !entity.isRemoved()) {
                    entity.discard();
                }
                cleared++;
            }
        }
        return cleared;
    }

    public void clearRoom(String roomId) {
        roomStates.remove(normalizeRoomId(roomId));
    }

    public void clearAll() {
        roomStates.clear();
    }

    private void refreshRecord(RoomDefinition room, RunningRuntimeState state, VehicleRuntimeRecord record, long gameTick, boolean shouldCheckCreativeAmmo) {
        if (record == null || record.getConfiguredVehicleId().isBlank()) {
            return;
        }
        RoomGameplayVehicleDeployRuleProvider gameplayRules = RoomGameplayVehicleDeployRuleProvider.resolve(state);
        if (record.getSpawnedEntityUuid() == null) {
            refreshWaitingState(room, state, record, gameplayRules, gameTick);
            return;
        }
        Entity entity = findEntity(record.getSpawnedEntityUuid()).orElse(null);
        if (entity == null || !VehicleCompatFacade.isVehicleEntity(entity)) {
            transitionToWaiting(room, state, record, gameplayRules, gameTick);
            return;
        }
        Optional<VehicleCompatSnapshot> snapshotOptional = VehicleCompatFacade.snapshot(entity);
        if (snapshotOptional.isEmpty()) {
            transitionToWaiting(room, state, record, gameplayRules, gameTick);
            return;
        }
        VehicleCompatSnapshot snapshot = snapshotOptional.get();
        if (snapshot.destroyed() || snapshot.health() <= 0.0F) {
            transitionToWaiting(room, state, record, gameplayRules, gameTick);
            return;
        }
        if (shouldCheckCreativeAmmo && VehicleCompatFacade.isManagedVehicleEntity(entity)) {
            VehicleCompatFacade.ensureManagedVehicleSupplies(entity);
        }
        record.applySnapshot(snapshot, gameTick);
        record.setWaitingUntilTick(-1L);
        updateControlledState(record, entity, state);
    }

    private void updateControlledState(VehicleRuntimeRecord record, Entity entity, RunningRuntimeState state) {
        Set<UUID> occupantPlayerIds = VehicleRuntimeTeamResolver.collectPassengerPlayers(entity);
        record.setOccupantPlayerIds(occupantPlayerIds);
        RoomTeamId configuredTeam = VehicleRuntimeTeamResolver.resolveConfiguredTeam(record.getConfiguredTeam());
        int enemyPassengers = VehicleRuntimeTeamResolver.countEnemyPassengers(occupantPlayerIds, state, configuredTeam);
        int friendlyPassengers = VehicleRuntimeTeamResolver.countFriendlyPassengers(occupantPlayerIds, state, configuredTeam);
        if (enemyPassengers > 0) {
            record.setOwnershipState(VehicleOwnershipState.ENEMY);
            record.setLogicalState(VehicleLogicalState.ENEMY_CONTROLLED);
            record.setDisplayState(VehicleDisplayState.ENEMY_CONTROLLED);
            return;
        }
        record.setOwnershipState(configuredTeam == null ? VehicleOwnershipState.NEUTRAL : VehicleOwnershipState.FRIENDLY);
        if (friendlyPassengers > 0 || record.getPassengerCount() > 0) {
            record.setLogicalState(VehicleLogicalState.FRIENDLY_CONTROLLED);
            record.setDisplayState(VehicleDisplayState.TEAM_CONTROLLED);
            return;
        }
        record.setLogicalState(VehicleLogicalState.FRIENDLY_EMPTY);
        record.setDisplayState(VehicleDisplayState.FRIENDLY_EMPTY);
    }

    private void refreshWaitingState(
        RoomDefinition room,
        RunningRuntimeState state,
        VehicleRuntimeRecord record,
        RoomGameplayVehicleDeployRuleProvider gameplayRules,
        long gameTick
    ) {
        if (shouldRetireVehicleEntry(room, state, record, gameplayRules)) {
            record.setWaitingUntilTick(NEVER_RESPAWN_TICK);
        }
        long waitingUntilTick = record.getWaitingUntilTick();
        if (waitingUntilTick == NEVER_RESPAWN_TICK) {
            record.setOwnershipState(VehicleOwnershipState.FRIENDLY);
            record.setLogicalState(VehicleLogicalState.WAITING);
            record.setDisplayState(VehicleDisplayState.WAITING);
            return;
        }
        if (waitingUntilTick >= 0L && gameTick < waitingUntilTick) {
            record.setOwnershipState(VehicleOwnershipState.FRIENDLY);
            record.setLogicalState(VehicleLogicalState.WAITING);
            record.setDisplayState(VehicleDisplayState.WAITING);
            return;
        }
        record.setWaitingUntilTick(-1L);
        record.setOwnershipState(VehicleOwnershipState.FRIENDLY);
        record.setLogicalState(VehicleLogicalState.READY);
        record.setDisplayState(VehicleDisplayState.READY_TO_DEPLOY);
    }

    private void transitionToWaiting(
        RoomDefinition room,
        RunningRuntimeState state,
        VehicleRuntimeRecord record,
        RoomGameplayVehicleDeployRuleProvider gameplayRules,
        long gameTick
    ) {
        record.clearSpawnedEntity();
        if (record.getRespawnSeconds() < 0 || shouldRetireVehicleEntry(room, state, record, gameplayRules)) {
            record.setWaitingUntilTick(NEVER_RESPAWN_TICK);
        } else {
            record.setWaitingUntilTick(gameTick + (long) record.getRespawnSeconds() * 20L);
        }
        refreshWaitingState(room, state, record, gameplayRules, gameTick);
    }

    private boolean shouldRetireVehicleEntry(
        RoomDefinition room,
        RunningRuntimeState state,
        VehicleRuntimeRecord record,
        RoomGameplayVehicleDeployRuleProvider gameplayRules
    ) {
        return gameplayRules != null && gameplayRules.shouldRetireVehicleEntryWhenUnspawned(room, state, record);
    }

    private static String normalizeRoomId(String roomId) {
        return roomId == null ? "" : roomId.trim();
    }

    private static String normalizeEntryId(String entryId) {
        return entryId == null ? "" : entryId.trim();
    }
}
