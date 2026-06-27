package org.mods.gd656conquest.server.room.vehicle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSnapshot;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSummonRequest;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSummonResult;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleDeployMountGuard;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoomVehicleDeployExecutionService {
    private final RoomVehicleRuntimeService vehicleRuntimeService;

    public RoomVehicleDeployExecutionService(MinecraftServer server, RoomVehicleRuntimeService vehicleRuntimeService) {
        Objects.requireNonNull(server, "server");
        this.vehicleRuntimeService = Objects.requireNonNull(vehicleRuntimeService, "vehicleRuntimeService");
    }

    public RoomPosition resolveTransitionPosition(RoomDefinition room, VehicleDeployResolution resolution) {
        if (room == null || resolution == null || resolution.entryId() == null || resolution.entryId().isBlank()) {
            return null;
        }
        VehicleRuntimeRecord record = vehicleRuntimeService.getRecord(room.roomId(), resolution.entryId()).orElse(null);
        if (record == null) {
            return null;
        }
        if (resolution.actionType() == VehicleDeployActionType.SPAWN_AND_BOARD) {
            return new RoomPosition((int) Math.floor(record.getConfiguredSpawnX()), record.getConfiguredSpawnY(), (int) Math.floor(record.getConfiguredSpawnZ()));
        }
        Entity entity = resolution.targetVehicleUuid() == null
            ? null
            : vehicleRuntimeService.findEntity(resolution.targetVehicleUuid()).orElse(null);
        if (entity != null) {
            return new RoomPosition((int) Math.floor(entity.getX()), entity.getY(), (int) Math.floor(entity.getZ()));
        }
        if (record.getSpawnedEntityUuid() != null) {
            Entity trackedEntity = vehicleRuntimeService.findEntity(record.getSpawnedEntityUuid()).orElse(null);
            if (trackedEntity != null) {
                return new RoomPosition((int) Math.floor(trackedEntity.getX()), trackedEntity.getY(), (int) Math.floor(trackedEntity.getZ()));
            }
        }
        if (resolution.reservedDriverPlayerId() != null) {
            return new RoomPosition((int) Math.floor(record.getConfiguredSpawnX()), record.getConfiguredSpawnY(), (int) Math.floor(record.getConfiguredSpawnZ()));
        }
        return new RoomPosition((int) Math.floor(record.getX()), record.getY(), (int) Math.floor(record.getZ()));
    }

    public float resolveTransitionYaw(RoomDefinition room, VehicleDeployResolution resolution, ServerPlayer player) {
        if (resolution == null) {
            return player == null ? 0.0F : player.getYRot();
        }
        Entity entity = resolution.targetVehicleUuid() == null
            ? null
            : vehicleRuntimeService.findEntity(resolution.targetVehicleUuid()).orElse(null);
        if (entity != null) {
            return entity.getYRot();
        }
        if (room != null && resolution.entryId() != null && !resolution.entryId().isBlank()) {
            VehicleRuntimeRecord record = vehicleRuntimeService.getRecord(room.roomId(), resolution.entryId()).orElse(null);
            if (record != null) {
                return yaw45ToDegrees(record.getConfiguredSpawnYaw45());
            }
        }
        return player == null ? 0.0F : player.getYRot();
    }

    public Optional<Entity> findTrackedEntity(UUID entityUuid) {
        if (entityUuid == null) {
            return Optional.empty();
        }
        return vehicleRuntimeService.findEntity(entityUuid);
    }

    public VehicleDeployExecutionResult execute(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        VehicleDeployResolution resolution,
        long gameTick
    ) {
        if (room == null || state == null || player == null || resolution == null) {
            return VehicleDeployExecutionResult.failure("载具部署参数不完整");
        }
        return switch (resolution.actionType()) {
            case SPAWN_AND_BOARD -> executeSpawnAndBoard(room, state, player, resolution, gameTick);
            case BOARD_EXISTING_DRIVER, BOARD_EXISTING_PASSENGER -> executeBoardExisting(room, state, player, resolution, gameTick);
            case FAIL -> VehicleDeployExecutionResult.failure(resolution.message());
        };
    }

    private VehicleDeployExecutionResult executeSpawnAndBoard(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        VehicleDeployResolution resolution,
        long gameTick
    ) {
        VehicleRuntimeRecord record = vehicleRuntimeService.getRecord(room.roomId(), resolution.entryId()).orElse(null);
        if (record == null) {
            return VehicleDeployExecutionResult.failure("未找到目标载具条目");
        }
        vehicleRuntimeService.clearResidualManagedVehicles(room.roomId(), resolution.entryId());
        ServerLevel level = player.serverLevel();
        Vec3 spawnPosition = new Vec3(record.getConfiguredSpawnX(), record.getConfiguredSpawnY(), record.getConfiguredSpawnZ());
        VehicleCompatSummonResult summonResult = VehicleCompatFacade.summon(new VehicleCompatSummonRequest(
            ConquestDataManager.of(player.server).serverConfig().getVehicleBackend(),
            level,
            spawnPosition,
            record.getConfiguredSpawnYaw45(),
            record.getConfiguredVehicleId(),
            record.getConfiguredDisplayId().isBlank() ? record.getConfiguredVehicleId() : record.getConfiguredDisplayId()
        ));
        if (!summonResult.success()) {
            return VehicleDeployExecutionResult.failure("生成载具失败: " + summonResult.message());
        }
        Entity entity = summonResult.entityUuid() == null
            ? null
            : vehicleRuntimeService.findEntity(summonResult.entityUuid()).orElse(null);
        if (entity == null || !VehicleCompatFacade.isVehicleEntity(entity)) {
            return VehicleDeployExecutionResult.failure("载具生成后未找到实体");
        }
        vehicleRuntimeService.markManagedVehicleEntity(room.roomId(), resolution.entryId(), entity);
        vehicleRuntimeService.trackSpawnedEntity(room.roomId(), resolution.entryId(), entity, gameTick);
        vehicleRuntimeService.refreshRecordSnapshot(room.roomId(), resolution.entryId(), gameTick);
        if (!mountPlayerToVehicle(player, entity, true, record.getConfiguredSpawnYaw45(), 0.0F)) {
            entity.discard();
            return VehicleDeployExecutionResult.failure("生成载具成功，但进入驾驶位失败");
        }
        return VehicleDeployExecutionResult.success("已部署新载具并进入驾驶位");
    }

    private VehicleDeployExecutionResult executeBoardExisting(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        VehicleDeployResolution resolution,
        long gameTick
    ) {
        Entity entity = resolution.targetVehicleUuid() == null
            ? null
            : vehicleRuntimeService.findEntity(resolution.targetVehicleUuid()).orElse(null);
        VehicleRuntimeRecord record = vehicleRuntimeService.getRecord(room.roomId(), resolution.entryId()).orElse(null);
        if (entity == null && record != null && record.getSpawnedEntityUuid() != null) {
            entity = vehicleRuntimeService.findEntity(record.getSpawnedEntityUuid()).orElse(null);
        }
        if (entity == null || !VehicleCompatFacade.isVehicleEntity(entity)) {
            if (shouldWaitForReservedDriver(state, resolution)) {
                return VehicleDeployExecutionResult.retry("等待预留载具生成");
            }
            return VehicleDeployExecutionResult.failure("目标载具已不存在");
        }
        if (!VehicleCompatFacade.canAcceptPassenger(entity, player)) {
            return VehicleDeployExecutionResult.failure("目标载具当前已满或无法搭乘");
        }
        final Entity mountedEntity = entity;
        if (!mountPlayerToVehicle(
            player,
            mountedEntity,
            resolution.actionType() == VehicleDeployActionType.BOARD_EXISTING_DRIVER,
            player.getYRot(),
            player.getXRot()
        )) {
            return VehicleDeployExecutionResult.failure("进入目标载具失败");
        }
        vehicleRuntimeService.refreshRecordSnapshot(room.roomId(), resolution.entryId(), gameTick);
        awardDriverDeployBonusIfNeeded(player, resolution, mountedEntity);
        return VehicleDeployExecutionResult.success(
            resolution.actionType() == VehicleDeployActionType.BOARD_EXISTING_DRIVER
                ? "已进入现存载具驾驶位"
                : "已进入现存载具乘客位"
        );
    }

    private boolean shouldWaitForReservedDriver(RunningRuntimeState state, VehicleDeployResolution resolution) {
        if (state == null || resolution == null || resolution.reservedDriverPlayerId() == null) {
            return false;
        }
        VehicleDeployResolution driverResolution = state.getPendingVehicleDeployResolutions().get(resolution.reservedDriverPlayerId());
        return driverResolution != null
            && driverResolution.actionType() == VehicleDeployActionType.SPAWN_AND_BOARD
            && resolution.entryId().equalsIgnoreCase(driverResolution.entryId());
    }

    private boolean mountPlayerToVehicle(ServerPlayer player, Entity vehicle, boolean driverSeat, float yRot, float xRot) {
        if (player == null || vehicle == null) {
            return false;
        }
        player.teleportTo(player.serverLevel(), vehicle.getX(), vehicle.getY(), vehicle.getZ(), yRot, xRot);
        boolean ridingStarted = player.startRiding(vehicle, true);
        if (!ridingStarted) {
            return false;
        }
        YwzjVehicleDeployMountGuard.markRecentDeployMount(player, vehicle);
        if (driverSeat) {
            VehicleCompatFacade.ensureDriverSeat(vehicle, player);
        }
        VehicleCompatFacade.alignPassengerToSeat(vehicle, player);
        VehicleCompatFacade.syncPassengerSeatState(vehicle, player);
        if (player.server != null) {
            player.server.execute(() -> VehicleCompatFacade.syncPassengerSeatState(vehicle, player));
        }
        return true;
    }

    private float yaw45ToDegrees(int yaw45) {
        int normalized = yaw45 % 8;
        if (normalized < 0) {
            normalized += 8;
        }
        return normalized * 45.0F;
    }

    private void awardDriverDeployBonusIfNeeded(ServerPlayer deployer, VehicleDeployResolution resolution, Entity vehicle) {
        if (deployer == null || resolution == null || vehicle == null) {
            return;
        }
        if (resolution.actionType() != VehicleDeployActionType.BOARD_EXISTING_PASSENGER) {
            return;
        }
        VehicleCompatSnapshot snapshot = VehicleCompatFacade.snapshot(vehicle).orElse(null);
        UUID driverId = snapshot != null && snapshot.driverUuid() != null
            ? snapshot.driverUuid()
            : resolution.reservedDriverPlayerId();
        if (driverId == null) {
            return;
        }
        if (driverId.equals(deployer.getUUID())) {
            return;
        }
        ServerPlayer driver = deployer.server == null ? null : deployer.server.getPlayerList().getPlayer(driverId);
        if (driver == null) {
            return;
        }
        KilliconScoreboardBridge.tryAwardConquestBonus(driver, "FRIENDLY_DEPLOY_ON_YOUR_VEHICLE", 1.0F, "");
    }
}
