package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.common.entity.ConquestEntities;
import org.mods.gd656conquest.common.entity.SquadDeployBeaconEntity;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RoomPlayerSessionService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeRepository;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeProvider;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RoomSquadBeaconService {
    private static final int SQUAD_BEACON_MAX_DEPLOY_COUNT = 6;
    private static final int BEACON_OWNER_KILL_WINDOW_TICKS = 30 * 20;
    private static final int ENEMY_BEACON_ACTIONBAR_INTERVAL_TICKS = 10;
    private static final double ENEMY_BEACON_ALERT_RANGE_SQR = 4.0D;
    private static final String ACTIONBAR_SOURCE_ENEMY_BEACON = "room_enemy_beacon";
    private static final String ACTIONBAR_SOURCE_BEACON_DEPLOY = "room_beacon_deploy";
    private static final String ACTIONBAR_SOURCE_BEACON_INVALID = "room_beacon_invalid";
    private static final ResourceLocation BEACON_DEPLOY_SOUND_ID =
        ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.beacon_deploy");

    private final MinecraftServer server;
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomPlayerSessionService playerSessionService;
    private final Map<String, Set<UUID>> squadBeaconIdsByRoom = new LinkedHashMap<>();

    public RoomSquadBeaconService(
        MinecraftServer server,
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
    }

    public boolean tryUseSquadBeaconItem(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() != ConquestItems.RADIO_BEACON.get()) {
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
        Vec3 spawnPos = resolveValidBeaconSpawnPosition(level, player.position());
        if (spawnPos == null) {
            ServerActionBarService.showText(player, ACTIONBAR_SOURCE_BEACON_INVALID, "当前信标放置位置不合法", 40);
            return true;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        spawnSquadBeaconAtFeet(level, room.roomId(), player, teamId, squadLabel.trim().toUpperCase(Locale.ROOT), spawnPos);
        player.swing(hand, true);
        return true;
    }

    public void tickGlobal(long gameTick) {
    }

    public void tickRoom(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        state.getBeaconOwnerKillWindowUntilTicksByDeployer().entrySet().removeIf(entry -> entry.getValue() < gameTick);
        state.getBeaconOwnerByDeployer().entrySet().removeIf(
            entry -> !state.getBeaconOwnerKillWindowUntilTicksByDeployer().containsKey(entry.getKey())
        );
        processEnemyBeaconNearbyActionbar(room, state, gameTick);
    }

    public String describeSelectionFailure(
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String targetIdRaw
    ) {
        return validateSelection(state, playerId, teamId, targetIdRaw).failureMessage();
    }

    public boolean canSelectDeploy(RunningRuntimeState state, UUID playerId, RoomTeamId teamId, String targetIdRaw) {
        return validateSelection(state, playerId, teamId, targetIdRaw).success();
    }

    public RoomPosition consumeDeploySpawn(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String targetIdRaw,
        long gameTick
    ) {
        BeaconSelectionResult validation = validateSelection(state, playerId, teamId, targetIdRaw);
        if (!validation.success()) {
            return null;
        }
        SquadDeployBeaconEntity beacon = validation.beacon();
        int left = beacon.consumeOneDeployAndGetRemaining();
        onBeaconDeployConsumed(room, state, playerId, teamId, beacon, left, gameTick);
        if (left <= 0) {
            beacon.discard();
            Set<UUID> tracked = squadBeaconIdsByRoom.get(beacon.getRoomId());
            if (tracked != null) {
                tracked.remove(beacon.getUUID());
                if (tracked.isEmpty()) {
                    squadBeaconIdsByRoom.remove(beacon.getRoomId());
                }
            }
        }
        return new RoomPosition(Mth.floor(beacon.getX()), Mth.floor(beacon.getY()), Mth.floor(beacon.getZ()));
    }

    public boolean shouldCancelDamageToSquadBeacon(ServerPlayer attacker, SquadDeployBeaconEntity beacon) {
        if (attacker == null || beacon == null || !beacon.isAlive()) {
            return false;
        }
        var roomIdOptional = playerSessionService.findRoomId(attacker.getUUID());
        if (roomIdOptional.isEmpty() || !roomIdOptional.get().equalsIgnoreCase(beacon.getRoomId())) {
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
        return resolveCaptureTeamToken(attackerTeam).equalsIgnoreCase(beacon.getTeamToken());
    }

    public void collectBeaconElements(java.util.List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        if (elements == null || context == null) {
            return;
        }
        RoomTeamId selfTeam = context.selfTeam();
        UUID selfId = context.playerId();
        if (selfTeam == null || selfId == null) {
            return;
        }
        String selfSquadLabel = context.state().getSquadLabelsByPlayer().get(selfId);
        if (selfSquadLabel == null || selfSquadLabel.isBlank()) {
            return;
        }
        Set<UUID> tracked = squadBeaconIdsByRoom.get(context.room().roomId());
        if (tracked == null || tracked.isEmpty()) {
            return;
        }
        Set<UUID> stale = new HashSet<>();
        String ownerToken = resolveCaptureTeamToken(selfTeam);
        for (UUID beaconId : tracked) {
            Entity raw = findEntityByUuid(beaconId);
            if (!(raw instanceof SquadDeployBeaconEntity beacon) || !beacon.isAlive()) {
                stale.add(beaconId);
                continue;
            }
            if (!ownerToken.equalsIgnoreCase(beacon.getTeamToken())) {
                continue;
            }
            if (!isSameSquadLabel(selfSquadLabel, beacon.getSquadLabel())) {
                continue;
            }
            if (beacon.getDeployRemaining() <= 0) {
                stale.add(beaconId);
                continue;
            }
            if (!RoomDeployBoundarySupport.allows(context.room(), context.state(), selfTeam,
                new RoomPosition(Mth.floor(beacon.getX()), beacon.getY(), Mth.floor(beacon.getZ())))) continue;
            elements.add(RoomDeployHudElement.beacon(
                beacon.getUUID(),
                new org.mods.gd656conquest.server.room.model.RoomPosition(
                    Mth.floor(beacon.getX()),
                    Mth.floor(beacon.getY()),
                    Mth.floor(beacon.getZ())
                ),
                selfTeam,
                buildBeaconNote(beacon)
            ));
        }
        if (!stale.isEmpty()) {
            tracked.removeAll(stale);
            if (tracked.isEmpty()) {
                squadBeaconIdsByRoom.remove(context.room().roomId());
            }
        }
    }

    private BeaconSelectionResult validateSelection(
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String targetIdRaw
    ) {
        UUID beaconId;
        try {
            beaconId = UUID.fromString(targetIdRaw == null ? "" : targetIdRaw.trim());
        } catch (Exception ignored) {
            return BeaconSelectionResult.failure("信标部署目标无效");
        }
        Entity raw = findEntityByUuid(beaconId);
        if (!(raw instanceof SquadDeployBeaconEntity beacon) || !beacon.isAlive()) {
            return BeaconSelectionResult.failure("该信标已失效");
        }
        if (state == null) {
            return BeaconSelectionResult.failure("当前房间状态异常");
        }
        if (!resolveCaptureTeamToken(teamId).equalsIgnoreCase(beacon.getTeamToken())) {
            return BeaconSelectionResult.failure("该信标不属于你的阵营");
        }
        String selfSquad = state.getSquadLabelsByPlayer().get(playerId);
        if (!isSameSquadLabel(selfSquad, beacon.getSquadLabel())) {
            return BeaconSelectionResult.failure("该信标不属于你的小队");
        }
        if (beacon.getDeployRemaining() <= 0) {
            return BeaconSelectionResult.failure("该信标的部署次数已用完");
        }
        RoomDefinition room = runtimeRepository.findRoom(beacon.getRoomId()).orElse(null);
        if (!RoomDeployBoundarySupport.allows(room, state, teamId,
            new RoomPosition(Mth.floor(beacon.getX()), beacon.getY(), Mth.floor(beacon.getZ()))))
            return BeaconSelectionResult.failure("该信标位于当前合法部署范围之外");
        return BeaconSelectionResult.success(beacon);
    }

    private void processEnemyBeaconNearbyActionbar(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room.lifecycleState() != RoomLifecycleState.RUNNING || gameTick % ENEMY_BEACON_ACTIONBAR_INTERVAL_TICKS != 0) {
            return;
        }
        Set<UUID> tracked = squadBeaconIdsByRoom.get(room.roomId());
        if (tracked == null || tracked.isEmpty()) {
            return;
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
            SquadDeployBeaconEntity nearestEnemyBeacon = null;
            double nearestDistanceSqr = Double.MAX_VALUE;
            for (UUID beaconId : tracked) {
                Entity raw = findEntityByUuid(beaconId);
                if (!(raw instanceof SquadDeployBeaconEntity beacon) || !beacon.isAlive()) {
                    stale.add(beaconId);
                    continue;
                }
                if (!room.roomId().equalsIgnoreCase(beacon.getRoomId())) {
                    stale.add(beaconId);
                    continue;
                }
                if (beacon.getDeployRemaining() <= 0) {
                    stale.add(beaconId);
                    continue;
                }
                if (resolveCaptureTeamToken(selfTeam).equalsIgnoreCase(beacon.getTeamToken())) {
                    continue;
                }
                double distanceSqr = player.distanceToSqr(beacon);
                if (distanceSqr > ENEMY_BEACON_ALERT_RANGE_SQR || distanceSqr >= nearestDistanceSqr) {
                    continue;
                }
                nearestDistanceSqr = distanceSqr;
                nearestEnemyBeacon = beacon;
            }
            if (nearestEnemyBeacon != null) {
                showEnemyBeaconNearbyActionBar(player, nearestEnemyBeacon);
            }
        }
        if (!stale.isEmpty()) {
            tracked.removeAll(stale);
            if (tracked.isEmpty()) {
                squadBeaconIdsByRoom.remove(room.roomId());
            }
        }
    }

    private void spawnSquadBeaconAtFeet(
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
        if (room.lifecycleState() != RoomLifecycleState.RUNNING && room.lifecycleState() != RoomLifecycleState.MATCHING) {
            return;
        }
        UUID ownerId = owner.getUUID();
        removeOwnerSquadBeacon(roomId, ownerId);
        SquadDeployBeaconEntity beacon = new SquadDeployBeaconEntity(ConquestEntities.SQUAD_DEPLOY_BEACON.get(), level);
        beacon.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        beacon.setYRot(owner.getYRot());
        beacon.setOwnerUuid(ownerId);
        beacon.setRoomId(roomId);
        beacon.setTeamToken(resolveCaptureTeamToken(teamId));
        beacon.setSquadLabel(squadLabel);
        beacon.setDeployRemaining(SQUAD_BEACON_MAX_DEPLOY_COUNT);
        beacon.setHealth(20.0F);
        level.addFreshEntity(beacon);
        level.playSound(
            null,
            beacon.getX(),
            beacon.getY(),
            beacon.getZ(),
            SoundEvent.createVariableRangeEvent(BEACON_DEPLOY_SOUND_ID),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
        squadBeaconIdsByRoom.computeIfAbsent(roomId, key -> new HashSet<>()).add(beacon.getUUID());
    }

    private Vec3 resolveValidBeaconSpawnPosition(ServerLevel level, Vec3 desired) {
        if (level == null || desired == null) {
            return null;
        }
        BlockPos feet = BlockPos.containing(desired.x, desired.y, desired.z);
        if (!isOpenForBeacon(level, feet) || !isOpenForBeacon(level, feet.above()) || !hasBeaconSupport(level, feet)) {
            return null;
        }
        return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
    }

    private boolean isOpenForBeacon(ServerLevel level, BlockPos pos) {
        return level != null
            && pos != null
            && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getFluidState(pos).isEmpty();
    }

    private boolean hasBeaconSupport(ServerLevel level, BlockPos feet) {
        if (level == null || feet == null) {
            return false;
        }
        BlockPos supportPos = feet.below();
        return !level.getBlockState(supportPos).getCollisionShape(level, supportPos).isEmpty()
            && level.getFluidState(supportPos).isEmpty();
    }

    private void removeOwnerSquadBeacon(String roomId, UUID ownerId) {
        if (roomId == null || roomId.isBlank() || ownerId == null) {
            return;
        }
        Set<UUID> tracked = squadBeaconIdsByRoom.get(roomId);
        if (tracked == null || tracked.isEmpty()) {
            return;
        }
        Set<UUID> stale = new HashSet<>();
        for (UUID beaconId : tracked) {
            Entity raw = findEntityByUuid(beaconId);
            if (!(raw instanceof SquadDeployBeaconEntity beacon) || !beacon.isAlive()) {
                stale.add(beaconId);
                continue;
            }
            if (!ownerId.equals(beacon.getOwnerUuid())) {
                continue;
            }
            beacon.discard();
            stale.add(beaconId);
        }
        tracked.removeAll(stale);
        if (tracked.isEmpty()) {
            squadBeaconIdsByRoom.remove(roomId);
        }
    }

    private void onBeaconDeployConsumed(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID deployerId,
        RoomTeamId selfTeam,
        SquadDeployBeaconEntity beacon,
        int leftAfterConsume,
        long gameTick
    ) {
        if (room == null || state == null || deployerId == null || beacon == null) {
            return;
        }
        UUID ownerId = beacon.getOwnerUuid();
        if (ownerId == null) {
            return;
        }
        if (!ownerId.equals(deployerId)) {
            state.getBeaconOwnerByDeployer().put(deployerId, ownerId);
            state.getBeaconOwnerKillWindowUntilTicksByDeployer().put(
                deployerId,
                gameTick + BEACON_OWNER_KILL_WINDOW_TICKS
            );
        }
        ServerPlayer deployer = server.getPlayerList().getPlayer(deployerId);
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (deployer != null) {
            showBeaconDeployActionBar(deployer, owner, leftAfterConsume);
        }
        if (owner != null) {
            KilliconScoreboardBridge.tryAwardConquestBonus(owner, "SQUAD_BEACON_DEPLOY", 1.0F, "");
            if (!ownerId.equals(deployerId) && isBeaconDeployInContestedObjective(room, state, selfTeam, beacon)) {
                KilliconScoreboardBridge.tryAwardConquestBonus(owner, "VALUE_OBJECTIVE_SUPPORT_BEACON_DEPLOY", 1.0F, "");
            }
        }
    }

    private boolean isBeaconDeployInContestedObjective(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId selfTeam,
        SquadDeployBeaconEntity beacon
    ) {
        if (room == null || state == null || beacon == null || (selfTeam != RoomTeamId.CAMP_A && selfTeam != RoomTeamId.CAMP_B)) {
            return false;
        }
        if (!(beacon.level() instanceof ServerLevel level)) {
            return false;
        }
        String captureCode = resolveCaptureCodeAtPosition(level, beacon.position());
        if (captureCode == null || captureCode.isBlank()) {
            return false;
        }
        return state.getGameplayRuntime()
            .filter(RoomObjectiveRuntimeProvider.class::isInstance)
            .map(RoomObjectiveRuntimeProvider.class::cast)
            .map(runtime -> !runtime.objectiveRuntimeView().isFullyControlledBy(captureCode, selfTeam))
            .orElse(false);
    }

    private String resolveCaptureCodeAtPosition(ServerLevel level, Vec3 position) {
        if (level == null || position == null) {
            return null;
        }
        int centerX = Mth.floor(position.x);
        int centerY = Mth.floor(position.y);
        int centerZ = Mth.floor(position.z);
        java.util.Map<String, Integer> hitCountByCode = new java.util.LinkedHashMap<>();
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int y = centerY - 1; y <= centerY + 2; y++) {
                for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                    var blockState = level.getBlockState(new BlockPos(x, y, z));
                    if (!ConquestBlocks.isCapturePoint(blockState)) {
                        continue;
                    }
                    ConquestBlocks.resolveCaptureCode(blockState)
                        .ifPresent(code -> hitCountByCode.merge(code, 1, Integer::sum));
                }
            }
        }
        String bestCode = null;
        int bestCount = 0;
        for (java.util.Map.Entry<String, Integer> entry : hitCountByCode.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCode = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return bestCode;
    }

    private void showBeaconDeployActionBar(ServerPlayer deployer, ServerPlayer owner, int leftAfterConsume) {
        if (deployer == null) {
            return;
        }
        String ownerName = owner == null ? "未知" : owner.getGameProfile().getName();
        ChatFormatting remainColor = leftAfterConsume <= 1
            ? ChatFormatting.RED
            : (leftAfterConsume <= 3 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
        String text = "信标所属者: "
            + ChatFormatting.GOLD + ownerName
            + ChatFormatting.DARK_GRAY + " | "
            + ChatFormatting.AQUA + "剩余重生次数: "
            + remainColor + Math.max(0, leftAfterConsume);
        ServerActionBarService.showText(deployer, ACTIONBAR_SOURCE_BEACON_DEPLOY, text, 50);
    }

    private void showEnemyBeaconNearbyActionBar(ServerPlayer viewer, SquadDeployBeaconEntity beacon) {
        if (viewer == null || beacon == null) {
            return;
        }
        UUID ownerId = beacon.getOwnerUuid();
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        String ownerName = owner == null ? "未知" : owner.getGameProfile().getName();
        ServerActionBarService.showText(
            viewer,
            ACTIONBAR_SOURCE_ENEMY_BEACON,
            "§c敌方部署信标 §8| §b所属者: §6" + ownerName,
            15
        );
    }

    private String buildBeaconNote(SquadDeployBeaconEntity beacon) {
        if (beacon == null) {
            return "小队信标";
        }
        UUID ownerId = beacon.getOwnerUuid();
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        String ownerName = owner == null ? "未知" : owner.getGameProfile().getName();
        return ownerName + " 的信标(" + beacon.getDeployRemaining() + ")";
    }

    private Entity findEntityByUuid(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
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

    private static boolean isSameSquadLabel(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private record BeaconSelectionResult(SquadDeployBeaconEntity beacon, String failureMessage) {
        private static BeaconSelectionResult success(SquadDeployBeaconEntity beacon) {
            return new BeaconSelectionResult(beacon, null);
        }

        private static BeaconSelectionResult failure(String failureMessage) {
            return new BeaconSelectionResult(null, failureMessage);
        }

        private boolean success() {
            return failureMessage == null;
        }
    }
}
