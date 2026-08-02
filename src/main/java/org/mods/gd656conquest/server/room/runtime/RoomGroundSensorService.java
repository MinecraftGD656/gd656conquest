package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.common.entity.ConquestEntities;
import org.mods.gd656conquest.common.entity.GroundSensorEntity;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RoomPlayerSessionService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeRepository;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomGroundSensorService {
    private static final int DEFAULT_GROUND_SENSOR_SCAN_INTERVAL_TICKS = 4 * 20;
    private static final int ENEMY_GROUND_SENSOR_ACTIONBAR_INTERVAL_TICKS = 10;
    private static final int DEFAULT_GROUND_SENSOR_MARK_DURATION_TICKS = 2 * 20;
    private static final double ENEMY_GROUND_SENSOR_ALERT_RANGE_SQR = 4.0D;
    private static final String ACTIONBAR_SOURCE_GROUND_SENSOR_INVALID = "room_ground_sensor_invalid";
    private static final String ACTIONBAR_SOURCE_ENEMY_GROUND_SENSOR = "room_enemy_ground_sensor";
    private static final String GROUND_SENSOR_MARKER_PREFIX = "ground_sensor:";
    private static final String DEPLOY_SOUND_ID = "gd656conquest:sfx.ground_sensor_fire_1p";
    private static final ResourceLocation ACTIVATE_SOUND_ID =
        ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.ground_sensor_activate");
    private static final ResourceLocation BEEP_SOUND_ID =
        ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.ground_sensor_beep");

    private final MinecraftServer server;
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomPlayerSessionService playerSessionService;
    private final RoomMarkerRuntimeService markerRuntimeService;
    private final Map<String, Set<UUID>> groundSensorIdsByRoom = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Long>> scanBonusAwardTimesByOwner = new ConcurrentHashMap<>();

    public RoomGroundSensorService(
        MinecraftServer server,
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService,
        RoomMarkerRuntimeService markerRuntimeService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.markerRuntimeService = Objects.requireNonNull(markerRuntimeService, "markerRuntimeService");
    }

    public boolean tryUseGroundSensorItem(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() != ConquestItems.GROUND_SENSOR.get()) {
            return false;
        }

        var roomIdOptional = playerSessionService.findRoomId(player.getUUID());
        if (roomIdOptional.isEmpty()) {
            return false;
        }
        RoomDefinition room = runtimeRepository.findRoom(roomIdOptional.get()).orElse(null);
        RoomRuntimeState runtime = runtimeRepository.findRuntime(roomIdOptional.get()).orElse(null);
        if (room == null || runtime == null) {
            return false;
        }
        if (room.lifecycleState() != RoomLifecycleState.RUNNING && room.lifecycleState() != RoomLifecycleState.MATCHING) {
            return false;
        }

        RoomTeamId teamId = room.lifecycleState() == RoomLifecycleState.RUNNING
            ? runtime.getRunningState().getRoomTeams().get(player.getUUID())
            : runtime.getMatchingState().getQueueTeams().get(player.getUUID());
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            return false;
        }
        String squadLabel = runtime.getSquadLabelsByPlayer().get(player.getUUID());
        if (squadLabel == null || squadLabel.isBlank()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 spawnPos = resolveValidGroundSensorSpawnPosition(level, player.position());
        if (spawnPos == null) {
            ServerActionBarService.showText(player, ACTIONBAR_SOURCE_GROUND_SENSOR_INVALID, "当前地面传感器放置位置不合法", 40);
            return true;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        spawnGroundSensorAtFeet(level, room.roomId(), player, teamId, squadLabel.trim().toUpperCase(Locale.ROOT), spawnPos);
        player.swing(hand, true);
        return true;
    }

    public void tickGlobal(long gameTick) {
        if (gameTick % 20 != 0) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long cooldownMs = resolveGroundSensorScanBonusCooldownMs();
        scanBonusAwardTimesByOwner.entrySet().removeIf(entry -> {
            Map<UUID, Long> targets = entry.getValue();
            targets.entrySet().removeIf(item -> nowMs - item.getValue() >= cooldownMs);
            return targets.isEmpty();
        });
    }

    public void tickRoom(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        boolean markerChanged = pruneRoom(room.roomId(), state);
        if (room.lifecycleState() == RoomLifecycleState.RUNNING) {
            markerChanged |= processEnemyGroundSensorNearbyActionbar(room, state, gameTick);
            markerChanged |= processSensorScans(room, state, gameTick);
        }
        if (markerChanged) {
            markerRuntimeService.syncRunningRoom(room, state, gameTick);
        }
    }

    public boolean shouldCancelDamageToGroundSensor(ServerPlayer attacker, GroundSensorEntity sensor) {
        if (attacker == null || sensor == null || !sensor.isAlive()) {
            return false;
        }
        var roomIdOptional = playerSessionService.findRoomId(attacker.getUUID());
        if (roomIdOptional.isEmpty() || !roomIdOptional.get().equalsIgnoreCase(sensor.getRoomId())) {
            return false;
        }
        RoomDefinition room = runtimeRepository.findRoom(roomIdOptional.get()).orElse(null);
        RoomRuntimeState runtime = runtimeRepository.findRuntime(roomIdOptional.get()).orElse(null);
        if (room == null || runtime == null) {
            return false;
        }
        if (room.lifecycleState() != RoomLifecycleState.RUNNING) {
            return false;
        }
        RoomTeamId attackerTeam = runtime.getRunningState().getRoomTeams().get(attacker.getUUID());
        if (attackerTeam != RoomTeamId.CAMP_A && attackerTeam != RoomTeamId.CAMP_B) {
            return false;
        }
        return resolveCaptureTeamToken(attackerTeam).equalsIgnoreCase(sensor.getTeamToken());
    }

    private void spawnGroundSensorAtFeet(
        ServerLevel level,
        String roomId,
        ServerPlayer owner,
        RoomTeamId teamId,
        String squadLabel,
        Vec3 spawnPos
    ) {
        if (level == null || roomId == null || roomId.isBlank() || owner == null || teamId == null || squadLabel == null || squadLabel.isBlank() || spawnPos == null) {
            return;
        }
        RoomDefinition room = runtimeRepository.findRoom(roomId).orElse(null);
        if (room == null) {
            return;
        }
        UUID ownerId = owner.getUUID();
        removeOwnerGroundSensor(roomId, ownerId);
        GroundSensorEntity sensor = new GroundSensorEntity(ConquestEntities.GROUND_SENSOR.get(), level);
        sensor.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        sensor.setYRot(owner.getYRot());
        sensor.setOwnerUuid(ownerId);
        sensor.setRoomId(roomId);
        sensor.setTeamToken(resolveCaptureTeamToken(teamId));
        sensor.setSquadLabel(squadLabel);
        sensor.setArmed(true);
        sensor.setHealth(20.0F);
        level.addFreshEntity(sensor);
        NetworkManager.sendToPlayer(owner, ClientShortSoundPacket.oneShot(DEPLOY_SOUND_ID, 1.0F, 1.0F));
        groundSensorIdsByRoom.computeIfAbsent(roomId, key -> new HashSet<>()).add(sensor.getUUID());
    }

    private Vec3 resolveValidGroundSensorSpawnPosition(ServerLevel level, Vec3 desired) {
        if (level == null || desired == null) {
            return null;
        }
        BlockPos feet = BlockPos.containing(desired.x, desired.y, desired.z);
        if (!isOpenForGroundSensor(level, feet) || !hasGroundSupport(level, feet)) {
            return null;
        }
        return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
    }

    private boolean isOpenForGroundSensor(ServerLevel level, BlockPos pos) {
        return level != null
            && pos != null
            && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getFluidState(pos).isEmpty();
    }

    private boolean hasGroundSupport(ServerLevel level, BlockPos feet) {
        if (level == null || feet == null) {
            return false;
        }
        BlockPos supportPos = feet.below();
        return !level.getBlockState(supportPos).getCollisionShape(level, supportPos).isEmpty()
            && level.getFluidState(supportPos).isEmpty();
    }

    private void removeOwnerGroundSensor(String roomId, UUID ownerId) {
        if (roomId == null || roomId.isBlank() || ownerId == null) {
            return;
        }
        Set<UUID> tracked = groundSensorIdsByRoom.get(roomId);
        if (tracked == null || tracked.isEmpty()) {
            return;
        }
        Set<UUID> stale = new HashSet<>();
        boolean markerChanged = false;
        for (UUID sensorId : tracked) {
            var raw = findEntityByUuid(sensorId);
            if (!(raw instanceof GroundSensorEntity sensor) || !sensor.isAlive()) {
                stale.add(sensorId);
                continue;
            }
            if (!ownerId.equals(sensor.getOwnerUuid())) {
                continue;
            }
            sensor.discard();
            stale.add(sensorId);
            RoomDefinition room = runtimeRepository.findRoom(roomId).orElse(null);
            RoomRuntimeState runtime = runtimeRepository.findRuntime(roomId).orElse(null);
            if (room != null && runtime != null && room.lifecycleState() == RoomLifecycleState.RUNNING) {
                markerChanged |= clearSensorMarkers(runtime.getRunningState(), sensorId);
            }
        }
        tracked.removeAll(stale);
        if (tracked.isEmpty()) {
            groundSensorIdsByRoom.remove(roomId);
        }
        if (markerChanged) {
            RoomDefinition room = runtimeRepository.findRoom(roomId).orElse(null);
            RoomRuntimeState runtime = runtimeRepository.findRuntime(roomId).orElse(null);
            if (room != null && runtime != null && room.lifecycleState() == RoomLifecycleState.RUNNING) {
                markerRuntimeService.syncRunningRoom(room, runtime.getRunningState(), server.getTickCount());
            }
        }
    }

    private boolean processEnemyGroundSensorNearbyActionbar(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room.lifecycleState() != RoomLifecycleState.RUNNING || gameTick % ENEMY_GROUND_SENSOR_ACTIONBAR_INTERVAL_TICKS != 0) {
            return false;
        }
        Set<UUID> tracked = groundSensorIdsByRoom.get(room.roomId());
        if (tracked == null || tracked.isEmpty()) {
            return false;
        }
        Set<UUID> stale = new HashSet<>();
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            RoomTeamId selfTeam = state.getRoomTeams().get(playerId);
            if (selfTeam != RoomTeamId.CAMP_A && selfTeam != RoomTeamId.CAMP_B) {
                continue;
            }
            GroundSensorEntity nearestEnemySensor = null;
            double nearestDistanceSqr = Double.MAX_VALUE;
            for (UUID sensorId : tracked) {
                var raw = findEntityByUuid(sensorId);
                if (!(raw instanceof GroundSensorEntity sensor) || !sensor.isAlive()) {
                    stale.add(sensorId);
                    continue;
                }
                if (!room.roomId().equalsIgnoreCase(sensor.getRoomId())) {
                    stale.add(sensorId);
                    continue;
                }
                if (player.level() != sensor.level()) {
                    continue;
                }
                if (resolveCaptureTeamToken(selfTeam).equalsIgnoreCase(sensor.getTeamToken())) {
                    continue;
                }
                double distanceSqr = player.distanceToSqr(sensor);
                if (distanceSqr > ENEMY_GROUND_SENSOR_ALERT_RANGE_SQR || distanceSqr >= nearestDistanceSqr) {
                    continue;
                }
                nearestDistanceSqr = distanceSqr;
                nearestEnemySensor = sensor;
            }
            if (nearestEnemySensor != null) {
                showEnemyGroundSensorNearbyActionBar(player, nearestEnemySensor);
            }
        }
        return cleanupTrackedSensors(room.roomId(), tracked, stale, state);
    }

    private boolean processSensorScans(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        int scanIntervalTicks = resolveGroundSensorScanIntervalTicks(room);
        if (room.lifecycleState() != RoomLifecycleState.RUNNING || gameTick % scanIntervalTicks != 0) {
            return false;
        }
        Set<UUID> tracked = groundSensorIdsByRoom.get(room.roomId());
        if (tracked == null || tracked.isEmpty()) {
            return false;
        }
        boolean markerChanged = false;
        Set<UUID> stale = new HashSet<>();
        for (UUID sensorId : tracked) {
            var raw = findEntityByUuid(sensorId);
            if (!(raw instanceof GroundSensorEntity sensor) || !sensor.isAlive()) {
                stale.add(sensorId);
                markerChanged |= clearSensorMarkers(state, sensorId);
                continue;
            }
            if (!room.roomId().equalsIgnoreCase(sensor.getRoomId())) {
                stale.add(sensorId);
                markerChanged |= clearSensorMarkers(state, sensorId);
                continue;
            }
            Set<UUID> detectedEnemies = collectDetectedEnemies(state, sensor);
            playSensorScanSound(sensor, detectedEnemies.isEmpty());
            awardGroundSensorScanBonuses(sensor, detectedEnemies);
            markerChanged |= syncSensorMarkers(room, state, sensor, detectedEnemies, gameTick);
        }
        markerChanged |= cleanupTrackedSensors(room.roomId(), tracked, stale, state);
        return markerChanged;
    }

    private Set<UUID> collectDetectedEnemies(RunningRuntimeState state, GroundSensorEntity sensor) {
        Set<UUID> detected = new HashSet<>();
        if (state == null || sensor == null || !sensor.isAlive() || !(sensor.level() instanceof ServerLevel level)) {
            return detected;
        }
        double scanRadiusSqr = resolveGroundSensorScanRadiusSqr();
        RoomTeamId sensorTeam = resolveTeamId(sensor.getTeamToken());
        if (sensorTeam != RoomTeamId.CAMP_A && sensorTeam != RoomTeamId.CAMP_B) {
            return detected;
        }
        AABB scanBox = sensor.getBoundingBox().inflate(10.0D, 10.0D, 10.0D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, scanBox)) {
            UUID playerId = player.getUUID();
            if (!state.getRoomPlayers().contains(playerId) || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            RoomTeamId playerTeam = state.getRoomTeams().get(playerId);
            if (playerTeam != RoomTeamId.CAMP_A && playerTeam != RoomTeamId.CAMP_B) {
                continue;
            }
            if (playerTeam == sensorTeam) {
                continue;
            }
            if (player.position().distanceToSqr(sensor.position()) > scanRadiusSqr) {
                continue;
            }
            detected.add(playerId);
        }
        return detected;
    }

    private void playSensorScanSound(GroundSensorEntity sensor, boolean emptyResult) {
        if (sensor == null || !(sensor.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(
            null,
            sensor.getX(),
            sensor.getY(),
            sensor.getZ(),
            SoundEvent.createVariableRangeEvent(emptyResult ? BEEP_SOUND_ID : ACTIVATE_SOUND_ID),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
    }

    private void awardGroundSensorScanBonuses(GroundSensorEntity sensor, Set<UUID> detectedEnemies) {
        if (sensor == null || detectedEnemies == null || detectedEnemies.isEmpty()) {
            return;
        }
        UUID ownerId = sensor.getOwnerUuid();
        if (ownerId == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long cooldownMs = resolveGroundSensorScanBonusCooldownMs();
        Map<UUID, Long> targetTimes = scanBonusAwardTimesByOwner.computeIfAbsent(ownerId, ignored -> new ConcurrentHashMap<>());
        for (UUID targetId : detectedEnemies) {
            if (targetId == null || targetId.equals(ownerId)) {
                continue;
            }
            long lastAwardTime = targetTimes.getOrDefault(targetId, 0L);
            if (nowMs - lastAwardTime < cooldownMs) {
                continue;
            }
            targetTimes.put(targetId, nowMs);
            KilliconScoreboardBridge.tryAwardConquestBonus(owner, "GROUND_SENSOR_SCAN", 1.0F, "");
        }
    }

    private boolean syncSensorMarkers(
        RoomDefinition room,
        RunningRuntimeState state,
        GroundSensorEntity sensor,
        Set<UUID> detectedEnemies,
        long gameTick
    ) {
        if (state == null || sensor == null) {
            return false;
        }
        boolean changed = clearSensorMarkers(state, sensor.getUUID());
        RoomTeamId visibleTeam = resolveTeamId(sensor.getTeamToken());
        if (visibleTeam != RoomTeamId.CAMP_A && visibleTeam != RoomTeamId.CAMP_B) {
            return changed;
        }
        int markerDurationTicks = resolveGroundSensorMarkDurationTicks(room);
        String markerPrefix = markerPrefix(sensor.getUUID());
        for (UUID targetId : detectedEnemies) {
            changed |= markerRuntimeService.putGameplayTrackedMarker(
                state,
                markerPrefix + targetId,
                visibleTeam,
                targetId,
                "地面传感器",
                gameTick,
                gameTick + markerDurationTicks
            );
        }
        return changed;
    }

    private int resolveGroundSensorScanIntervalTicks(RoomDefinition room) {
        if (room == null || room.commonConfig() == null) {
            return DEFAULT_GROUND_SENSOR_SCAN_INTERVAL_TICKS;
        }
        int seconds = ConquestDataManager.of(server).serverConfig().getGroundSensorScanIntervalSeconds();
        return seconds > 0 ? seconds * 20 : DEFAULT_GROUND_SENSOR_SCAN_INTERVAL_TICKS;
    }

    private int resolveGroundSensorMarkDurationTicks(RoomDefinition room) {
        if (room == null) {
            return DEFAULT_GROUND_SENSOR_MARK_DURATION_TICKS;
        }
        int seconds = ConquestDataManager.of(server).serverConfig().getGroundSensorMarkDurationSeconds();
        return seconds > 0 ? seconds * 20 : DEFAULT_GROUND_SENSOR_MARK_DURATION_TICKS;
    }

    private double resolveGroundSensorScanRadiusSqr() {
        double radius = ConquestDataManager.of(server).serverConfig().getGroundSensorScanRadius();
        return radius * radius;
    }

    private long resolveGroundSensorScanBonusCooldownMs() {
        return ConquestDataManager.of(server).serverConfig().getGroundSensorScanBonusCooldownSeconds() * 1000L;
    }

    private boolean clearSensorMarkers(RunningRuntimeState state, UUID sensorId) {
        if (state == null || sensorId == null) {
            return false;
        }
        return markerRuntimeService.clearGameplayMarkersByPrefix(state, markerPrefix(sensorId));
    }

    private boolean pruneRoom(String roomId, RunningRuntimeState state) {
        if (roomId == null || roomId.isBlank()) {
            return false;
        }
        Set<UUID> tracked = groundSensorIdsByRoom.get(roomId);
        if (tracked == null || tracked.isEmpty()) {
            return false;
        }
        Set<UUID> stale = new HashSet<>();
        boolean markerChanged = false;
        for (UUID sensorId : tracked) {
            var raw = findEntityByUuid(sensorId);
            if (!(raw instanceof GroundSensorEntity sensor) || !sensor.isAlive() || !roomId.equalsIgnoreCase(sensor.getRoomId())) {
                stale.add(sensorId);
                markerChanged |= clearSensorMarkers(state, sensorId);
            }
        }
        markerChanged |= cleanupTrackedSensors(roomId, tracked, stale, state);
        return markerChanged;
    }

    private boolean cleanupTrackedSensors(String roomId, Set<UUID> tracked, Set<UUID> stale, RunningRuntimeState state) {
        if (tracked == null || tracked.isEmpty() || stale == null || stale.isEmpty()) {
            return false;
        }
        boolean markerChanged = false;
        for (UUID sensorId : stale) {
            markerChanged |= clearSensorMarkers(state, sensorId);
        }
        tracked.removeAll(stale);
        if (tracked.isEmpty() && roomId != null && !roomId.isBlank()) {
            groundSensorIdsByRoom.remove(roomId);
        }
        return markerChanged;
    }

    private void showEnemyGroundSensorNearbyActionBar(ServerPlayer viewer, GroundSensorEntity sensor) {
        if (viewer == null || sensor == null) {
            return;
        }
        UUID ownerId = sensor.getOwnerUuid();
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        String ownerName = owner == null ? "未知" : owner.getGameProfile().getName();
        ServerActionBarService.showText(
            viewer,
            ACTIONBAR_SOURCE_ENEMY_GROUND_SENSOR,
            "§c敌军地面传感器 §8| §b所属者: §6" + ownerName,
            15
        );
    }

    private net.minecraft.world.entity.Entity findEntityByUuid(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static String resolveCaptureTeamToken(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "T";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "S";
        }
        return "N";
    }

    private static RoomTeamId resolveTeamId(String teamToken) {
        if ("T".equalsIgnoreCase(teamToken)) {
            return RoomTeamId.CAMP_A;
        }
        if ("S".equalsIgnoreCase(teamToken)) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private static String markerPrefix(UUID sensorId) {
        return GROUND_SENSOR_MARKER_PREFIX + sensorId + ":";
    }
}
