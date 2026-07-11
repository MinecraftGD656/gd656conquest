package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;
import org.mods.gd656conquest.server.compat.tacz.TaczShellingTestService;
import org.mods.gd656conquest.server.data.shelling.ShellingDataModel;
import org.mods.gd656conquest.server.data.shelling.ShellingImpactType;
import org.mods.gd656conquest.server.facility.support.SupportHudSubtitleService;
import org.mods.gd656conquest.server.facility.support.SupportScoreService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.audio.RoomDistanceAudioService;
import org.mods.gd656conquest.server.room.runtime.shelling.ActiveShellingSequence;
import org.mods.gd656conquest.server.room.runtime.shelling.ShellingSequenceKind;
import org.mods.gd656conquest.server.room.runtime.shelling.ShellingSequenceProfile;
import org.mods.gd656conquest.server.room.runtime.shelling.ShellingSequenceProfiles;
import org.mods.gd656conquest.server.room.runtime.shelling.TimedShellingConfigSnapshot;
import org.mods.gd656conquest.server.shared.inventory.PlayerInventorySanitizer;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.shared.util.ItemStackSnbtCodec;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RoomShellingRuntimeService {
    public static final long DEFAULT_REQUEST_TIMEOUT_TICKS = 30L * 20L;
    public static final int SHELLING_INDICATOR_HOTBAR_SLOT = 8;
    public static final String ACTIONBAR_SOURCE_SHELLING_REQUEST = "shelling_request";
    public static final String ACTIONBAR_SOURCE_SHELLING_COUNTDOWN = "shelling_request_countdown";
    private static final int INDICATOR_OPEN_PROMPT_TICKS = 60;
    private static final long MISSILE_ALARM_INTERVAL_TICKS = 10L * 20L;
    private static final long CONFIRMED_SLOT_RESTORE_DELAY_TICKS = 5L * 20L;
    private static final int MISSILE_COUNTDOWN_INTERVAL_SECONDS = 5;
    private static final int MISSILE_ALARM_COUNT = 2;
    private static final int LOOP_FADE_IN_TICKS = 6;
    private static final int LOOP_FADE_OUT_TICKS = 20;

    private final MinecraftServer server;
    private final RoomAudioRuntimeService audioRuntimeService;
    private final RoomDistanceAudioService distanceAudioService;
    private final LinkedHashMap<UUID, ActiveShellingSequence> activeSequences = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, ActiveShellingExecutionLock> activeExecutionLocks = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, PendingSlotRestore> pendingSlotRestores = new LinkedHashMap<>();

    public RoomShellingRuntimeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.audioRuntimeService = new RoomAudioRuntimeService(server);
        this.distanceAudioService = new RoomDistanceAudioService(server);
    }

    public StartRequestResult startShellingRequest(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        String shellingId,
        int reservedCost,
        String originalSlot9ItemSnbt,
        long gameTick
    ) {
        if (room == null || state == null || player == null || shellingId == null || shellingId.isBlank()) {
            return StartRequestResult.failure(StartFailureReason.INVALID_CONTEXT, null, null);
        }
        if (room.lifecycleState() != RoomLifecycleState.RUNNING) {
            return StartRequestResult.failure(StartFailureReason.INVALID_CONTEXT, null, null);
        }
        UUID playerId = player.getUUID();
        if (!state.getRoomPlayers().contains(playerId) || !player.isAlive() || player.isSpectator()) {
            return StartRequestResult.failure(StartFailureReason.PLAYER_NOT_ELIGIBLE, null, null);
        }
        if (state.isInRespawnFlow(playerId) || state.isPlayerDowned(playerId)) {
            return StartRequestResult.failure(StartFailureReason.PLAYER_NOT_ELIGIBLE, null, null);
        }
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            return StartRequestResult.failure(StartFailureReason.INVALID_TEAM, null, null);
        }
        RunningRuntimeState.ShellingRequestState existing = state.getActiveShellingRequest(playerId);
        if (existing != null) {
            return StartRequestResult.failure(StartFailureReason.ALREADY_ACTIVE, existing, existing.playerId());
        }
        pendingSlotRestores.remove(playerId);
        RunningRuntimeState.ShellingRequestState occupiedRequest = state.findActiveShellingRequest(teamId, shellingId);
        if (occupiedRequest != null && !playerId.equals(occupiedRequest.playerId())) {
            return StartRequestResult.failure(StartFailureReason.SHELLING_BUSY, occupiedRequest, occupiedRequest.playerId(), false);
        }
        ActiveShellingExecutionLock occupiedExecution = findActiveExecutionLock(room.roomId(), teamId, shellingId, playerId, gameTick);
        if (occupiedExecution != null) {
            return StartRequestResult.failure(StartFailureReason.SHELLING_BUSY, null, occupiedExecution.ownerId(), true);
        }
        RunningRuntimeState.ShellingRequestState request = new RunningRuntimeState.ShellingRequestState(
            playerId,
            teamId,
            room.roomId(),
            shellingId,
            Math.max(0, reservedCost),
            originalSlot9ItemSnbt,
            gameTick,
            gameTick + DEFAULT_REQUEST_TIMEOUT_TICKS,
            -1L,
            -1L,
            null,
            false
        );
        state.putShellingRequest(request);
        return StartRequestResult.success(request);
    }

    public boolean confirmShellingTarget(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        BlockPos targetBlockPos,
        long gameTick
    ) {
        if (room == null || state == null || playerId == null || targetBlockPos == null) {
            return false;
        }
        RunningRuntimeState.ShellingRequestState existing = state.getActiveShellingRequest(playerId);
        if (existing == null) {
            return false;
        }
        state.putShellingRequest(new RunningRuntimeState.ShellingRequestState(
            existing.playerId(),
            existing.teamId(),
            existing.roomId(),
            existing.shellingId(),
            existing.reservedCost(),
            existing.originalSlot9ItemSnbt(),
            existing.indicatorIssuedTick(),
            existing.timeoutTick(),
            gameTick,
            existing.returnTick(),
            targetBlockPos,
            true
        ));
        return true;
    }

    public ConfirmExecutionResult executeConfirmedShelling(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        ShellingDataModel.ShellingEntry shellingEntry,
        RoomMarkerRuntimeService markerRuntimeService,
        long gameTick
    ) {
        if (room == null || state == null || player == null || shellingEntry == null) {
            return ConfirmExecutionResult.failure("当前无法确认炮击");
        }
        RunningRuntimeState.ShellingRequestState existing = state.getActiveShellingRequest(player.getUUID());
        if (existing == null || existing.targetBlockPos() == null) {
            return ConfirmExecutionResult.failure("请先标定炮击坐标");
        }
        if (isSequencedShelling(shellingEntry)) {
            return startSequencedShelling(room, state, player, shellingEntry, markerRuntimeService, existing.targetBlockPos(), gameTick);
        }
        return executeImmediateShelling(room, state, player, shellingEntry, markerRuntimeService, gameTick, existing);
    }

    private ConfirmExecutionResult executeImmediateShelling(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        ShellingDataModel.ShellingEntry shellingEntry,
        RoomMarkerRuntimeService markerRuntimeService,
        long gameTick,
        RunningRuntimeState.ShellingRequestState existing
    ) {
        TaczShellingTestService.StartResult startResult = TaczShellingTestService.of(server)
            .startShelling(player, shellingEntry, Vec3.atCenterOf(existing.targetBlockPos()));
        if (!startResult.success()) {
            return ConfirmExecutionResult.failure(startResult.message());
        }
        if (markerRuntimeService != null) {
            markerRuntimeService.placeMarker(
                room,
                state,
                player,
                RunningRuntimeState.MarkerKind.REQUEST_STRIKE,
                existing.targetBlockPos(),
                null,
                gameTick
            );
        }
        trackExecutionLock(
            UUID.randomUUID(),
            room.roomId(),
            existing.shellingId(),
            existing.teamId(),
            player.getUUID(),
            resolveImmediateExecutionExpireTick(shellingEntry, gameTick)
        );
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_SHELLING_COUNTDOWN);
        scheduleSupportSlotRestore(existing, gameTick + CONFIRMED_SLOT_RESTORE_DELAY_TICKS);
        state.clearShellingRequest(existing.playerId());
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_SHELLING_REQUEST,
            ServerActionBarService.builder().text("炮击即将抵达", ChatFormatting.GOLD),
            60
        );
        return ConfirmExecutionResult.success(startResult.message());
    }

    private ConfirmExecutionResult startSequencedShelling(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        ShellingDataModel.ShellingEntry shellingEntry,
        RoomMarkerRuntimeService markerRuntimeService,
        BlockPos targetBlockPos,
        long gameTick
    ) {
        ShellingSequenceProfile profile = resolveSequenceProfile(shellingEntry == null ? null : shellingEntry.resolveImpactType());
        if (profile == null) {
            return ConfirmExecutionResult.failure("当前炮击类型尚未接入阶段化时序");
        }
        TimedShellingConfigSnapshot shellingConfig = TimedShellingConfigSnapshot.fromEntry(shellingEntry);
        if (shellingConfig.gunId().isBlank()) {
            return ConfirmExecutionResult.failure(profile.displayName() + "模板缺少 TACZ 枪械 ID");
        }
        int shellCount = Math.max(1, TaczShellingTestService.estimateShellCount(shellingEntry));
        ActiveShellingSequence sequence = ActiveShellingSequence.create(
            room.roomId(),
            shellingEntry.getId(),
            player.getUUID(),
            player.getScoreboardName(),
            state.getRoomTeams().get(player.getUUID()),
            player.serverLevel().dimension(),
            Vec3.atCenterOf(targetBlockPos),
            shellingConfig,
            profile,
            gameTick,
            shellCount
        );
        activeSequences.put(sequence.sequenceId, sequence);
        trackExecutionLock(
            sequence.sequenceId,
            room.roomId(),
            shellingEntry.getId(),
            sequence.teamId,
            sequence.ownerId,
            sequence.completionTick
        );
        ServerLogger.info("已成功创建%s序列: sequenceId=%s, gunId=%s", profile.displayName(), sequence.sequenceId, sequence.shellingConfig.gunId());
        playSequenceOpeningAudio(state, sequence, gameTick);
        if (markerRuntimeService != null) {
            markerRuntimeService.placeMarker(
                room,
                state,
                player,
                RunningRuntimeState.MarkerKind.REQUEST_STRIKE,
                targetBlockPos,
                null,
                gameTick
            );
        }
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_SHELLING_COUNTDOWN);
        scheduleSupportSlotRestore(state.getActiveShellingRequest(player.getUUID()), gameTick + CONFIRMED_SLOT_RESTORE_DELAY_TICKS);
        state.clearShellingRequest(player.getUUID());
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_SHELLING_REQUEST,
            ServerActionBarService.builder().text(profile.actionbarText(), ChatFormatting.GOLD),
            60
        );
        return ConfirmExecutionResult.success(profile.resultMessagePrefix() + shellCount + " " + profile.projectileNoun());
    }

    public boolean updateShellingTarget(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        BlockPos targetBlockPos
    ) {
        if (room == null || state == null || playerId == null || targetBlockPos == null) {
            return false;
        }
        RunningRuntimeState.ShellingRequestState existing = state.getActiveShellingRequest(playerId);
        if (existing == null) {
            return false;
        }
        state.putShellingRequest(new RunningRuntimeState.ShellingRequestState(
            existing.playerId(),
            existing.teamId(),
            existing.roomId(),
            existing.shellingId(),
            existing.reservedCost(),
            existing.originalSlot9ItemSnbt(),
            existing.indicatorIssuedTick(),
            existing.timeoutTick(),
            existing.confirmedTick(),
            existing.returnTick(),
            targetBlockPos,
            existing.confirmed()
        ));
        return true;
    }

    public RunningRuntimeState.ShellingRequestState cancelShellingRequest(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId
    ) {
        if (room == null || state == null || playerId == null) {
            return null;
        }
        RunningRuntimeState.ShellingRequestState removed = state.clearShellingRequest(playerId);
        clearShellingActionBar(playerId);
        return removed;
    }

    public RunningRuntimeState.ShellingRequestState abortShellingRequest(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        UUID playerId,
        AbortReason reason
    ) {
        if (room == null || state == null || playerId == null) {
            return null;
        }
        RunningRuntimeState.ShellingRequestState request = state.getActiveShellingRequest(playerId);
        if (request == null) {
            return null;
        }
        boolean refundScore = !request.confirmed() && reason.shouldRefundScore();
        boolean restoreSlot = player != null && reason.shouldRestoreSlot();
        clearRequestState(state, player, request, restoreSlot, refundScore);
        if (player != null && reason.feedbackMessage() != null && !reason.feedbackMessage().isBlank()) {
            ServerMessageSender.system(
                player,
                "阵地支援",
                reason.feedbackMessage(),
                reason.feedbackColor() == null ? ChatFormatting.YELLOW : reason.feedbackColor()
            );
        }
        return request;
    }

    public void abortAllShellingRequests(
        RoomDefinition room,
        RunningRuntimeState state,
        AbortReason reason
    ) {
        if (room == null || state == null) {
            return;
        }
        for (RunningRuntimeState.ShellingRequestState request : List.copyOf(state.getActiveShellingsByPlayer().values())) {
            if (request == null || request.playerId() == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(request.playerId());
            abortShellingRequest(room, state, player, request.playerId(), reason);
        }
        stopActiveSequencesForRoom(room.roomId());
        stopExecutionLocksForRoom(room.roomId());
    }

    public boolean issueShellingIndicator(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RunningRuntimeState.ShellingRequestState request
    ) {
        if (room == null || state == null || player == null || request == null || request.playerId() == null) {
            return false;
        }
        if (!request.playerId().equals(player.getUUID())) {
            return false;
        }
        ItemStack indicator = SuperbWarfareCompatBridge.createManagedArtilleryIndicator(room.roomId(), player.getUUID(), request.shellingId());
        if (indicator.isEmpty()) {
            return false;
        }
        String originalSlotSnbt = request.originalSlot9ItemSnbt();
        if (originalSlotSnbt == null || originalSlotSnbt.isBlank()) {
            originalSlotSnbt = ItemStackSnbtCodec.serialize(player.getInventory().getItem(SHELLING_INDICATOR_HOTBAR_SLOT));
        }
        player.getInventory().setItem(SHELLING_INDICATOR_HOTBAR_SLOT, indicator);
        player.getInventory().selected = SHELLING_INDICATOR_HOTBAR_SLOT;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        PlayerInventorySanitizer.flush(player);
        state.putShellingRequest(new RunningRuntimeState.ShellingRequestState(
            request.playerId(),
            request.teamId(),
            request.roomId(),
            request.shellingId(),
            request.reservedCost(),
            originalSlotSnbt,
            request.indicatorIssuedTick(),
            request.timeoutTick(),
            request.confirmedTick(),
            request.returnTick(),
            request.targetBlockPos(),
            request.confirmed()
        ));
        showIndicatorDeploymentPrompt(player);
        return true;
    }

    public void restoreSupportSlot(ServerPlayer player, RunningRuntimeState.ShellingRequestState request) {
        restoreSupportSlot(player, request == null ? "" : request.originalSlot9ItemSnbt());
    }

    private void restoreSupportSlot(ServerPlayer player, String originalSlot9ItemSnbt) {
        if (player == null) {
            return;
        }
        ItemStack restoreStack = ItemStackSnbtCodec.deserialize(originalSlot9ItemSnbt == null ? "" : originalSlot9ItemSnbt);
        if (restoreStack.isEmpty() && ConquestItems.SUPPORT_ITEM.isPresent()) {
            restoreStack = new ItemStack(ConquestItems.SUPPORT_ITEM.get());
        }
        player.getInventory().setItem(SHELLING_INDICATOR_HOTBAR_SLOT, restoreStack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        PlayerInventorySanitizer.flush(player);
    }

    public void tickRoom(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        tickActiveExecutionLocks(room, gameTick);
        tickPendingSlotRestores(room, gameTick);
        for (RunningRuntimeState.ShellingRequestState request : List.copyOf(state.getActiveShellingsByPlayer().values())) {
            if (request == null || request.playerId() == null) {
                continue;
            }
            UUID playerId = request.playerId();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (!room.roomId().equals(request.roomId()) || room.lifecycleState() != RoomLifecycleState.RUNNING) {
                abortShellingRequest(room, state, player, playerId, AbortReason.ROOM_UNAVAILABLE);
                continue;
            }
            if (!state.getRoomPlayers().contains(playerId)) {
                abortShellingRequest(room, state, player, playerId, AbortReason.PLAYER_LEFT_ROOM);
                continue;
            }
            RoomTeamId currentTeam = state.getRoomTeams().get(playerId);
            if (currentTeam != request.teamId()) {
                abortShellingRequest(room, state, player, playerId, AbortReason.TEAM_CHANGED);
                continue;
            }
            if (player == null) {
                abortShellingRequest(room, state, null, playerId, AbortReason.PLAYER_OFFLINE);
                continue;
            }
            if (!player.isAlive() || player.isSpectator() || state.isInRespawnFlow(playerId)) {
                abortShellingRequest(room, state, player, playerId, AbortReason.PLAYER_DEATH);
                continue;
            }
            if (!request.confirmed() && request.timeoutTick() > gameTick) {
                showDeploymentCountdown(player, request.timeoutTick() - gameTick);
            }
            if (!request.confirmed() && request.timeoutTick() > 0L && gameTick >= request.timeoutTick()) {
                abortShellingRequest(room, state, player, playerId, AbortReason.TIMEOUT);
                continue;
            }
        }
        tickActiveSequences(room, state, gameTick);
    }

    private void tickActiveSequences(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || activeSequences.isEmpty()) {
            return;
        }
        Iterator<ActiveShellingSequence> iterator = activeSequences.values().iterator();
        while (iterator.hasNext()) {
            ActiveShellingSequence sequence = iterator.next();
            if (sequence == null || !room.roomId().equals(sequence.roomId)) {
                continue;
            }
            if (isRoomShuttingDown(room.lifecycleState())) {
                stopSequenceLoop(sequence);
                iterator.remove();
                continue;
            }
            syncSequenceLoopRecipients(sequence, state);
            if (sequence.kind == ShellingSequenceKind.MISSILE) {
                while (sequence.alarmsPlayed < MISSILE_ALARM_COUNT
                    && gameTick >= sequence.startTick + (long) sequence.alarmsPlayed * MISSILE_ALARM_INTERVAL_TICKS) {
                    playMissileAlarm(state);
                    sequence.alarmsPlayed++;
                    ServerLogger.info("正在播放导弹警报: sequenceId=%s, alarmsPlayed=%d", sequence.sequenceId, sequence.alarmsPlayed);
                }
                broadcastMissileCountdownIfNeeded(state, sequence, gameTick);
            }
            playDueSequenceLaunches(state, sequence, gameTick);
            while (sequence.spawnedShellCount < sequence.totalShellCount && gameTick >= sequence.nextShellSpawnTick) {
                if (!spawnSequenceShell(state, sequence)) {
                    stopSequenceLoop(sequence);
                    iterator.remove();
                    break;
                }
                sequence.spawnedShellCount++;
                sequence.nextShellSpawnTick += sequence.intervalTicks;
            }
            pruneTrackedProjectiles(sequence);
            if (sequence.spawnedShellCount >= sequence.totalShellCount
                && gameTick >= sequence.completionTick
                && sequence.trackedProjectiles.isEmpty()) {
                stopSequenceLoop(sequence);
                iterator.remove();
            }
        }
    }

    private void broadcastMissileCountdownIfNeeded(RunningRuntimeState state, ActiveShellingSequence sequence, long gameTick) {
        if (state == null || sequence == null || sequence.kind != ShellingSequenceKind.MISSILE || sequence.teamId == null) {
            return;
        }
        long remainingTicks = Math.max(0L, sequence.nominalFinishTick - gameTick);
        if (remainingTicks <= 0L) {
            return;
        }
        int remainingSeconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0D));
        if (remainingSeconds == sequence.lastCountdownAnnouncedSecond) {
            return;
        }
        if (remainingSeconds % MISSILE_COUNTDOWN_INTERVAL_SECONDS != 0) {
            return;
        }
        sequence.lastCountdownAnnouncedSecond = remainingSeconds;
        SupportHudSubtitleService.broadcastMissileCountdown(
            state,
            sequence.teamId,
            remainingSeconds,
            BlockPos.containing(sequence.targetCenter)
        );
    }

    private boolean spawnSequenceShell(RunningRuntimeState state, ActiveShellingSequence sequence) {
        ServerPlayer owner = server.getPlayerList().getPlayer(sequence.ownerId);
        if (owner == null || owner.isRemoved()) {
            ServerLogger.failure("%s序列已中断：触发者离线 sequence=%s owner=%s", sequence.profile.displayName(), sequence.sequenceId, sequence.ownerName);
            return false;
        }
        TaczShellingTestService.SpawnResult spawnResult = TaczShellingTestService.of(server)
            .spawnConfiguredShell(owner, sequence.shellingConfig.toEntry(), sequence.targetCenter);
        if (!spawnResult.success()) {
            ServerLogger.failure("%s序列生成失败 sequence=%s owner=%s error=%s", sequence.profile.displayName(), sequence.sequenceId, sequence.ownerName, spawnResult.message());
            return false;
        }
        if (spawnResult.projectileId() != null && spawnResult.dimension() != null) {
            sequence.trackedProjectiles.add(new ActiveShellingSequence.TrackedProjectile(spawnResult.dimension(), spawnResult.projectileId()));
        }
        if (spawnResult.dimension() != null && spawnResult.spawnPosition() != null) {
            String soundId = randomSoundId(sequence.profile.incomingSoundIds());
            if (!soundId.isBlank()) {
                distanceAudioService.playHorizontalRadiusSound(
                    state,
                    spawnResult.dimension(),
                    spawnResult.spawnPosition(),
                    soundId,
                    sequence.profile.incomingRadius(),
                    sequence.profile.incomingMaxVolume(),
                    1.0F
                );
            }
        }
        return true;
    }

    private void pruneTrackedProjectiles(ActiveShellingSequence sequence) {
        Iterator<ActiveShellingSequence.TrackedProjectile> iterator = sequence.trackedProjectiles.iterator();
        while (iterator.hasNext()) {
            ActiveShellingSequence.TrackedProjectile tracked = iterator.next();
            if (tracked == null || tracked.dimension() == null || tracked.projectileId() == null) {
                iterator.remove();
                continue;
            }
            if (server.getLevel(tracked.dimension()) == null || server.getLevel(tracked.dimension()).getEntity(tracked.projectileId()) == null) {
                iterator.remove();
            }
        }
    }

    private void playSequenceOpeningAudio(RunningRuntimeState state, ActiveShellingSequence sequence, long gameTick) {
        if (state == null || sequence == null) {
            return;
        }
        String openingSoundId = randomSoundId(sequence.profile.openingSoundIds());
        if (!openingSoundId.isBlank()) {
            audioRuntimeService.playOneShotShortSoundToRoomPlayers(state, openingSoundId, sequence.profile.openingVolume(), 1.0F);
        }
        syncSequenceLoopRecipients(sequence, state);
        playDueSequenceLaunches(state, sequence, gameTick);
    }

    private void playMissileAlarm(RunningRuntimeState state) {
        audioRuntimeService.playOneShotShortSoundToRoomPlayers(state, ShellingSequenceProfiles.missileAlarmSoundId(), 1.0F, 1.0F);
    }

    private void playDueSequenceLaunches(RunningRuntimeState state, ActiveShellingSequence sequence, long gameTick) {
        if (state == null || sequence == null || sequence.profile.eventLaunchSoundIds().isEmpty()) {
            return;
        }
        while (sequence.startedEventCount < sequence.totalShellCount && gameTick >= sequence.nextEventTick) {
            String soundId = randomSoundId(sequence.profile.eventLaunchSoundIds());
            if (!soundId.isBlank()) {
                audioRuntimeService.playOneShotShortSoundToRoomPlayers(state, soundId, sequence.profile.eventLaunchVolume(), 1.0F);
            }
            sequence.startedEventCount++;
            sequence.nextEventTick += sequence.intervalTicks;
        }
    }

    private void syncSequenceLoopRecipients(ActiveShellingSequence sequence, RunningRuntimeState state) {
        if (sequence == null || state == null) {
            return;
        }
        Set<UUID> activeRoomPlayers = new LinkedHashSet<>(state.getRoomPlayers());
        for (UUID playerId : activeRoomPlayers) {
            if (playerId == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            float loopVolume = resolveLoopVolume(sequence, player);
            if (loopVolume > 0.0F) {
                boolean newlyAdded = sequence.loopRecipients.add(playerId);
                audioRuntimeService.playLoopSoundToPlayer(player, sequence.profile.loopSoundId(), loopVolume, newlyAdded ? LOOP_FADE_IN_TICKS : 0);
                continue;
            }
            if (sequence.loopRecipients.contains(playerId)) {
                audioRuntimeService.stopLoopSoundToPlayer(player, sequence.profile.loopSoundId(), LOOP_FADE_OUT_TICKS);
                sequence.loopRecipients.remove(playerId);
            }
        }
        Iterator<UUID> iterator = sequence.loopRecipients.iterator();
        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            if (playerId == null) {
                iterator.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (!activeRoomPlayers.contains(playerId) || player == null || resolveLoopVolume(sequence, player) <= 0.0F) {
                if (player != null) {
                    audioRuntimeService.stopLoopSoundToPlayer(player, sequence.profile.loopSoundId(), LOOP_FADE_OUT_TICKS);
                }
                iterator.remove();
            }
        }
    }

    private float resolveLoopVolume(ActiveShellingSequence sequence, ServerPlayer player) {
        if (sequence == null || player == null || sequence.profile.loopSoundId() == null || sequence.profile.loopSoundId().isBlank()) {
            return 0.0F;
        }
        double loopRadius = sequence.profile.loopRadius();
        float loopMaxVolume = sequence.profile.loopMaxVolume();
        if (loopRadius <= 0.0D) {
            return loopMaxVolume <= 0.0F ? 0.0F : loopMaxVolume;
        }
        if (sequence.dimension == null) {
            return 0.0F;
        }
        return distanceAudioService.resolveHorizontalVolume(
            player,
            sequence.dimension,
            sequence.targetCenter,
            loopRadius,
            loopMaxVolume
        );
    }

    private void stopSequenceLoop(ActiveShellingSequence sequence) {
        if (sequence == null) {
            return;
        }
        for (UUID playerId : List.copyOf(sequence.loopRecipients)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                audioRuntimeService.stopLoopSoundToPlayer(player, sequence.profile.loopSoundId(), LOOP_FADE_OUT_TICKS);
            }
        }
        sequence.loopRecipients.clear();
        sequence.trackedProjectiles.clear();
        activeExecutionLocks.remove(sequence.sequenceId);
    }

    private void stopActiveSequencesForRoom(String roomId) {
        if (roomId == null || roomId.isBlank() || activeSequences.isEmpty()) {
            return;
        }
        Iterator<ActiveShellingSequence> iterator = activeSequences.values().iterator();
        while (iterator.hasNext()) {
            ActiveShellingSequence sequence = iterator.next();
            if (sequence == null || !roomId.equals(sequence.roomId)) {
                continue;
            }
            stopSequenceLoop(sequence);
            iterator.remove();
        }
    }

    public ShellingOccupancy resolveShellingOccupancy(
        String roomId,
        RunningRuntimeState state,
        RoomTeamId teamId,
        String shellingId,
        UUID requesterId
    ) {
        if (roomId == null || roomId.isBlank() || state == null || teamId == null || shellingId == null || shellingId.isBlank()) {
            return ShellingOccupancy.idle();
        }
        RunningRuntimeState.ShellingRequestState activeRequest = state.findActiveShellingRequest(teamId, shellingId);
        if (activeRequest != null) {
            return new ShellingOccupancy(true, activeRequest.playerId(), true, false);
        }
        ActiveShellingExecutionLock activeExecution = findActiveExecutionLock(roomId, teamId, shellingId, requesterId, server.getTickCount());
        if (activeExecution != null) {
            return new ShellingOccupancy(true, activeExecution.ownerId(), false, true);
        }
        return ShellingOccupancy.idle();
    }

    private ActiveShellingExecutionLock findActiveExecutionLock(
        String roomId,
        RoomTeamId teamId,
        String shellingId,
        UUID requesterId,
        long gameTick
    ) {
        if (roomId == null || roomId.isBlank() || teamId == null || shellingId == null || shellingId.isBlank() || activeExecutionLocks.isEmpty()) {
            return null;
        }
        for (ActiveShellingExecutionLock executionLock : activeExecutionLocks.values()) {
            if (executionLock == null) {
                continue;
            }
            if (executionLock.expireTick() > 0L && gameTick >= executionLock.expireTick()) {
                continue;
            }
            if (!roomId.equals(executionLock.roomId())
                || teamId != executionLock.teamId()
                || !shellingId.equals(executionLock.shellingId())) {
                continue;
            }
            if (requesterId != null && requesterId.equals(executionLock.ownerId())) {
                continue;
            }
            return executionLock;
        }
        return null;
    }

    private void trackExecutionLock(
        UUID lockId,
        String roomId,
        String shellingId,
        RoomTeamId teamId,
        UUID ownerId,
        long expireTick
    ) {
        if (lockId == null || roomId == null || roomId.isBlank() || shellingId == null || shellingId.isBlank() || teamId == null || ownerId == null) {
            return;
        }
        activeExecutionLocks.put(lockId, new ActiveShellingExecutionLock(
            lockId,
            roomId,
            shellingId,
            teamId,
            ownerId,
            Math.max(0L, expireTick)
        ));
    }

    private void tickActiveExecutionLocks(RoomDefinition room, long gameTick) {
        if (room == null || activeExecutionLocks.isEmpty()) {
            return;
        }
        Iterator<ActiveShellingExecutionLock> iterator = activeExecutionLocks.values().iterator();
        while (iterator.hasNext()) {
            ActiveShellingExecutionLock executionLock = iterator.next();
            if (executionLock == null) {
                iterator.remove();
                continue;
            }
            if (!room.roomId().equals(executionLock.roomId())) {
                continue;
            }
            if (gameTick >= executionLock.expireTick()) {
                iterator.remove();
            }
        }
    }

    private void stopExecutionLocksForRoom(String roomId) {
        if (roomId == null || roomId.isBlank() || activeExecutionLocks.isEmpty()) {
            return;
        }
        activeExecutionLocks.entrySet().removeIf(entry -> entry.getValue() == null || roomId.equals(entry.getValue().roomId()));
    }

    private long resolveImmediateExecutionExpireTick(ShellingDataModel.ShellingEntry shellingEntry, long gameTick) {
        long durationTicks = shellingEntry == null ? 0L : Math.round(Math.max(0.0D, shellingEntry.getDurationSeconds()) * 20.0D);
        return gameTick + Math.max(20L, durationTicks);
    }

    private boolean isSequencedShelling(ShellingDataModel.ShellingEntry shellingEntry) {
        if (shellingEntry == null) {
            return false;
        }
        return ShellingSequenceProfiles.isSequenced(shellingEntry.resolveImpactType());
    }

    /**
     * 判定房间是否已进入强制收尾阶段（房间关闭、保存、清理、禁用等）。
     * 对局进行中（RUNNING）与对局结算阶段（ENDING）视为允许已发起的炮击事件自然播完。
     */
    private static boolean isRoomShuttingDown(RoomLifecycleState state) {
        if (state == null) {
            return true;
        }
        return state != RoomLifecycleState.RUNNING && state != RoomLifecycleState.ENDING;
    }

    private ShellingSequenceProfile resolveSequenceProfile(ShellingImpactType impactType) {
        return ShellingSequenceProfiles.resolve(impactType);
    }

    private String randomSoundId(List<String> soundIds) {
        if (soundIds == null || soundIds.isEmpty()) {
            return "";
        }
        return soundIds.get(ThreadLocalRandom.current().nextInt(soundIds.size()));
    }

    private void clearRequestState(
        RunningRuntimeState state,
        ServerPlayer player,
        RunningRuntimeState.ShellingRequestState request,
        boolean restoreSlot,
        boolean refundScore
    ) {
        if (state == null || request == null || request.playerId() == null) {
            return;
        }
        pendingSlotRestores.remove(request.playerId());
        clearShellingActionBar(request.playerId());
        if (refundScore && request.reservedCost() > 0) {
            int currentSpent = SupportScoreService.getSpentScore(state, request.playerId());
            SupportScoreService.setSpentScore(state, request.playerId(), currentSpent - request.reservedCost());
        }
        if (restoreSlot && player != null) {
            restoreSupportSlot(player, request);
        }
        state.clearShellingRequest(request.playerId());
    }

    private void scheduleSupportSlotRestore(RunningRuntimeState.ShellingRequestState request, long dueTick) {
        if (request == null || request.playerId() == null) {
            return;
        }
        pendingSlotRestores.put(
            request.playerId(),
            new PendingSlotRestore(
                request.playerId(),
                request.roomId(),
                request.originalSlot9ItemSnbt(),
                Math.max(0L, dueTick)
            )
        );
    }

    private void tickPendingSlotRestores(RoomDefinition room, long gameTick) {
        if (room == null || pendingSlotRestores.isEmpty()) {
            return;
        }
        Iterator<PendingSlotRestore> iterator = pendingSlotRestores.values().iterator();
        while (iterator.hasNext()) {
            PendingSlotRestore pending = iterator.next();
            if (pending == null || pending.playerId() == null) {
                iterator.remove();
                continue;
            }
            if (!room.roomId().equals(pending.roomId())) {
                continue;
            }
            if (gameTick < pending.dueTick()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (player == null) {
                continue;
            }
            restoreSupportSlot(player, pending.originalSlot9ItemSnbt());
            iterator.remove();
        }
    }

    public void showIndicatorDeploymentPrompt(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_SHELLING_REQUEST,
            ServerActionBarService.builder()
                .text("长按选定目标点呼叫空中支援", ChatFormatting.GOLD)
                .text("  |  ", ChatFormatting.DARK_GRAY)
                .text("点按右键以取消部署", ChatFormatting.GOLD),
            INDICATOR_OPEN_PROMPT_TICKS
        );
    }

    private void showDeploymentCountdown(ServerPlayer player, long remainingTicks) {
        if (player == null || remainingTicks <= 0L) {
            return;
        }
        int remainingSeconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0D));
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_SHELLING_COUNTDOWN,
            ServerActionBarService.builder()
                .text("请在", ChatFormatting.RED)
                .text(String.valueOf(remainingSeconds), ChatFormatting.GOLD)
                .text("秒内完成炮击部署", ChatFormatting.RED),
            25
        );
    }

    private void clearShellingActionBar(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_SHELLING_REQUEST);
            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_SHELLING_COUNTDOWN);
        }
    }

    public enum StartFailureReason {
        INVALID_CONTEXT,
        PLAYER_NOT_ELIGIBLE,
        INVALID_TEAM,
        ALREADY_ACTIVE,
        SHELLING_BUSY
    }

    public record StartRequestResult(
        boolean success,
        StartFailureReason failureReason,
        RunningRuntimeState.ShellingRequestState request,
        UUID occupiedByPlayerId,
        boolean occupiedByActiveSequence
    ) {
        public static StartRequestResult success(RunningRuntimeState.ShellingRequestState request) {
            return new StartRequestResult(true, null, request, null, false);
        }

        public static StartRequestResult failure(
            StartFailureReason failureReason,
            RunningRuntimeState.ShellingRequestState request,
            UUID occupiedByPlayerId
        ) {
            return new StartRequestResult(false, failureReason, request, occupiedByPlayerId, false);
        }

        public static StartRequestResult failure(
            StartFailureReason failureReason,
            RunningRuntimeState.ShellingRequestState request,
            UUID occupiedByPlayerId,
            boolean occupiedByActiveSequence
        ) {
            return new StartRequestResult(false, failureReason, request, occupiedByPlayerId, occupiedByActiveSequence);
        }
    }

    public record ShellingOccupancy(
        boolean occupied,
        UUID occupiedByPlayerId,
        boolean requestInProgress,
        boolean sequenceRunning
    ) {
        public static ShellingOccupancy idle() {
            return new ShellingOccupancy(false, null, false, false);
        }
    }

    public record ConfirmExecutionResult(boolean success, String message) {
        public static ConfirmExecutionResult success(String message) {
            return new ConfirmExecutionResult(true, message == null ? "" : message);
        }

        public static ConfirmExecutionResult failure(String message) {
            return new ConfirmExecutionResult(false, message == null ? "" : message);
        }
    }

    public enum AbortReason {
        TIMEOUT(true, true, "炮击释放流程已超时", ChatFormatting.YELLOW),
        PLAYER_DEATH(true, true, "炮击释放流程已中断", ChatFormatting.YELLOW),
        PLAYER_OFFLINE(true, false, "", ChatFormatting.YELLOW),
        PLAYER_LEFT_ROOM(true, true, "炮击释放流程已退出", ChatFormatting.YELLOW),
        PLAYER_CANCELLED(true, true, "炮击部署已取消", ChatFormatting.YELLOW),
        TEAM_CHANGED(true, true, "炮击释放流程已取消", ChatFormatting.YELLOW),
        ROOM_UNAVAILABLE(true, true, "炮击释放流程已取消", ChatFormatting.YELLOW);

        private final boolean refundScore;
        private final boolean restoreSlot;
        private final String feedbackMessage;
        private final ChatFormatting feedbackColor;

        AbortReason(boolean refundScore, boolean restoreSlot, String feedbackMessage, ChatFormatting feedbackColor) {
            this.refundScore = refundScore;
            this.restoreSlot = restoreSlot;
            this.feedbackMessage = feedbackMessage == null ? "" : feedbackMessage;
            this.feedbackColor = feedbackColor;
        }

        public boolean shouldRefundScore() {
            return refundScore;
        }

        public boolean shouldRestoreSlot() {
            return restoreSlot;
        }

        public String feedbackMessage() {
            return feedbackMessage;
        }

        public ChatFormatting feedbackColor() {
            return feedbackColor;
        }
    }

    private record PendingSlotRestore(
        UUID playerId,
        String roomId,
        String originalSlot9ItemSnbt,
        long dueTick
    ) {
    }

    private record ActiveShellingExecutionLock(
        UUID lockId,
        String roomId,
        String shellingId,
        RoomTeamId teamId,
        UUID ownerId,
        long expireTick
    ) {
    }
}
