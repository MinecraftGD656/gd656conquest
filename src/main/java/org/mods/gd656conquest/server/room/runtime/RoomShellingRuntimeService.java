package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
    private static final String MISSILE_LOOP_SOUND_ID = "gd656conquest:sfx.shelling.loop_missile";
    private static final String MISSILE_ALARM_SOUND_ID = "gd656conquest:sfx.shelling.bombraid_sfx";
    private static final List<String> MISSILE_LAUNCH_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.launch_missile_1",
        "gd656conquest:sfx.shelling.launch_missile_2",
        "gd656conquest:sfx.shelling.launch_missile_3",
        "gd656conquest:sfx.shelling.launch_missile_4",
        "gd656conquest:sfx.shelling.launch_missile_5"
    );
    private static final List<String> MISSILE_INCOMING_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.incoming_missile_1",
        "gd656conquest:sfx.shelling.incoming_missile_2",
        "gd656conquest:sfx.shelling.incoming_missile_3",
        "gd656conquest:sfx.shelling.incoming_missile_4"
    );
    private static final long MISSILE_WARNING_DURATION_TICKS = 30L * 20L;
    private static final long MISSILE_APPROACH_START_TICKS = 28L * 20L;
    private static final long MISSILE_ALARM_INTERVAL_TICKS = 10L * 20L;
    private static final int MISSILE_COUNTDOWN_INTERVAL_SECONDS = 5;
    private static final int MISSILE_ALARM_COUNT = 3;
    private static final double MISSILE_INCOMING_RADIUS = 100.0D;
    private static final float MISSILE_INCOMING_MAX_VOLUME = 1.5F;
    private static final float MISSILE_LAUNCH_VOLUME = 0.6F;

    private final MinecraftServer server;
    private final RoomAudioRuntimeService audioRuntimeService;
    private final RoomDistanceAudioService distanceAudioService;
    private final LinkedHashMap<UUID, ActiveShellingSequence> activeSequences = new LinkedHashMap<>();

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
        UUID occupiedBy = state.getActiveShellingPlayerByTeam().get(teamId);
        if (occupiedBy != null && !occupiedBy.equals(playerId)) {
            RunningRuntimeState.ShellingRequestState occupiedRequest = state.getActiveShellingRequest(occupiedBy);
            return StartRequestResult.failure(StartFailureReason.TEAM_BUSY, occupiedRequest, occupiedBy);
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
        if (isMissileShelling(shellingEntry)) {
            return startMissileSequence(room, state, player, shellingEntry, markerRuntimeService, existing.targetBlockPos(), gameTick);
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
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_SHELLING_COUNTDOWN);
        restoreSupportSlot(player, existing);
        state.clearShellingRequest(existing.playerId());
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_SHELLING_REQUEST,
            ServerActionBarService.builder().text("炮击即将抵达", ChatFormatting.GOLD),
            60
        );
        return ConfirmExecutionResult.success(startResult.message());
    }

    private ConfirmExecutionResult startMissileSequence(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        ShellingDataModel.ShellingEntry shellingEntry,
        RoomMarkerRuntimeService markerRuntimeService,
        BlockPos targetBlockPos,
        long gameTick
    ) {
        MissileConfigSnapshot missileConfig = MissileConfigSnapshot.fromEntry(shellingEntry);
        if (missileConfig.gunId().isBlank()) {
            return ConfirmExecutionResult.failure("导弹炮击模板缺少 TACZ 枪械 ID");
        }
        int missileCount = Math.max(1, TaczShellingTestService.estimateShellCount(shellingEntry));
        ActiveShellingSequence sequence = ActiveShellingSequence.createMissile(
            room.roomId(),
            player.getUUID(),
            player.getScoreboardName(),
            state.getRoomTeams().get(player.getUUID()),
            Vec3.atCenterOf(targetBlockPos),
            missileConfig,
            gameTick,
            missileCount
        );
        activeSequences.put(sequence.sequenceId, sequence);
        ServerLogger.info("已成功创建导弹炮击序列: sequenceId=%s, gunId=%s", sequence.sequenceId, sequence.missileConfig.gunId());
        playMissileLaunchAndLoop(state, sequence);
        
        // Let tickActiveSequences handle alarms, so we don't duplicate the first alarm
        sequence.alarmsPlayed = 0;
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
        restoreSupportSlot(player, state.getActiveShellingRequest(player.getUUID()));
        state.clearShellingRequest(player.getUUID());
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_SHELLING_REQUEST,
            ServerActionBarService.builder().text("导弹已发射，正在接近目标区", ChatFormatting.GOLD),
            60
        );
        return ConfirmExecutionResult.success("已发起导弹打击，预计来袭 " + missileCount + " 枚导弹");
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
        showIndicatorOpenPrompt(player);
        return true;
    }

    public void restoreSupportSlot(ServerPlayer player, RunningRuntimeState.ShellingRequestState request) {
        if (player == null) {
            return;
        }
        ItemStack restoreStack = ItemStackSnbtCodec.deserialize(request == null ? "" : request.originalSlot9ItemSnbt());
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
            if (room.lifecycleState() != RoomLifecycleState.RUNNING) {
                stopSequenceLoop(sequence);
                iterator.remove();
                continue;
            }
            if (sequence.kind != SequenceKind.MISSILE) {
                iterator.remove();
                continue;
            }
            syncMissileLoopRecipients(sequence, state);
            while (sequence.alarmsPlayed < MISSILE_ALARM_COUNT
            && gameTick >= sequence.startTick + (long) sequence.alarmsPlayed * MISSILE_ALARM_INTERVAL_TICKS) {
            playMissileAlarm(state);
            sequence.alarmsPlayed++;
            ServerLogger.info("正在播放导弹警报: sequenceId=%s, alarmsPlayed=%d", sequence.sequenceId, sequence.alarmsPlayed);
        }
            broadcastMissileCountdownIfNeeded(state, sequence, gameTick);
            while (sequence.spawnedShellCount < sequence.totalShellCount && gameTick >= sequence.nextShellSpawnTick) {
                if (!spawnMissileShell(state, sequence)) {
                    stopSequenceLoop(sequence);
                    iterator.remove();
                    break;
                }
                sequence.spawnedShellCount++;
                sequence.nextShellSpawnTick += sequence.intervalTicks;
            }
            pruneTrackedProjectiles(sequence);
            if (sequence.spawnedShellCount >= sequence.totalShellCount
                && gameTick >= sequence.nominalFinishTick
                && sequence.trackedProjectiles.isEmpty()) {
                stopSequenceLoop(sequence);
                iterator.remove();
            }
        }
    }

    private void broadcastMissileCountdownIfNeeded(RunningRuntimeState state, ActiveShellingSequence sequence, long gameTick) {
        if (state == null || sequence == null || sequence.kind != SequenceKind.MISSILE || sequence.teamId == null) {
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

    private boolean spawnMissileShell(RunningRuntimeState state, ActiveShellingSequence sequence) {
        ServerPlayer owner = server.getPlayerList().getPlayer(sequence.ownerId);
        if (owner == null || owner.isRemoved()) {
            ServerLogger.failure("导弹序列已中断：触发者离线 sequence=%s owner=%s", sequence.sequenceId, sequence.ownerName);
            return false;
        }
        TaczShellingTestService.SpawnResult spawnResult = TaczShellingTestService.of(server)
            .spawnConfiguredShell(owner, sequence.missileConfig.toEntry(), sequence.targetCenter);
        if (!spawnResult.success()) {
            ServerLogger.failure("导弹序列生成失败 sequence=%s owner=%s error=%s", sequence.sequenceId, sequence.ownerName, spawnResult.message());
            return false;
        }
        if (spawnResult.projectileId() != null && spawnResult.dimension() != null) {
            sequence.trackedProjectiles.add(new TrackedProjectile(spawnResult.dimension(), spawnResult.projectileId()));
        }
        if (spawnResult.dimension() != null && spawnResult.spawnPosition() != null) {
            distanceAudioService.playHorizontalRadiusSound(
                state,
                spawnResult.dimension(),
                spawnResult.spawnPosition(),
                randomSoundId(MISSILE_INCOMING_SOUND_IDS),
                MISSILE_INCOMING_RADIUS,
                MISSILE_INCOMING_MAX_VOLUME,
                1.0F
            );
        }
        return true;
    }

    private void pruneTrackedProjectiles(ActiveShellingSequence sequence) {
        Iterator<TrackedProjectile> iterator = sequence.trackedProjectiles.iterator();
        while (iterator.hasNext()) {
            TrackedProjectile tracked = iterator.next();
            if (tracked == null || tracked.dimension == null || tracked.projectileId == null) {
                iterator.remove();
                continue;
            }
            if (server.getLevel(tracked.dimension) == null || server.getLevel(tracked.dimension).getEntity(tracked.projectileId) == null) {
                iterator.remove();
            }
        }
    }

    private void playMissileLaunchAndLoop(RunningRuntimeState state, ActiveShellingSequence sequence) {
        audioRuntimeService.playOneShotShortSoundToRoomPlayers(state, randomSoundId(MISSILE_LAUNCH_SOUND_IDS), MISSILE_LAUNCH_VOLUME, 1.0F);
        syncMissileLoopRecipients(sequence, state);
        ServerLogger.info("发射阶段音效及循环音效请求已发送 sequenceId=%s", sequence.sequenceId);
    }

    private void playMissileAlarm(RunningRuntimeState state) {
        audioRuntimeService.playOneShotShortSoundToRoomPlayers(state, MISSILE_ALARM_SOUND_ID, 1.0F, 1.0F);
        ServerLogger.info("单次警报音效请求已发送");
    }

    private void syncMissileLoopRecipients(ActiveShellingSequence sequence, RunningRuntimeState state) {
        if (sequence == null || state == null) {
            return;
        }
        Set<UUID> activeRoomPlayers = new LinkedHashSet<>(state.getRoomPlayers());
        for (UUID playerId : activeRoomPlayers) {
            if (playerId == null || sequence.loopRecipients.contains(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            audioRuntimeService.playLoopSoundToPlayer(player, MISSILE_LOOP_SOUND_ID, 1.0F, 0);
            sequence.loopRecipients.add(playerId);
        }
        Iterator<UUID> iterator = sequence.loopRecipients.iterator();
        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            if (playerId == null || activeRoomPlayers.contains(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                audioRuntimeService.stopLoopSoundToPlayer(player, MISSILE_LOOP_SOUND_ID, 0);
            }
            iterator.remove();
        }
    }

    private void stopSequenceLoop(ActiveShellingSequence sequence) {
        if (sequence == null) {
            return;
        }
        for (UUID playerId : List.copyOf(sequence.loopRecipients)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                audioRuntimeService.stopLoopSoundToPlayer(player, MISSILE_LOOP_SOUND_ID, 0);
            }
        }
        sequence.loopRecipients.clear();
        sequence.trackedProjectiles.clear();
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

    private boolean isMissileShelling(ShellingDataModel.ShellingEntry shellingEntry) {
        if (shellingEntry == null) {
            return false;
        }
        return shellingEntry.resolveImpactType() == ShellingImpactType.MISSILE;
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

    public void showIndicatorOpenPrompt(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_SHELLING_REQUEST,
            ServerActionBarService.builder()
                .text("长按选定目标点呼叫空中支援", ChatFormatting.GOLD),
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
        TEAM_BUSY
    }

    public record StartRequestResult(
        boolean success,
        StartFailureReason failureReason,
        RunningRuntimeState.ShellingRequestState request,
        UUID occupiedByPlayerId
    ) {
        public static StartRequestResult success(RunningRuntimeState.ShellingRequestState request) {
            return new StartRequestResult(true, null, request, null);
        }

        public static StartRequestResult failure(
            StartFailureReason failureReason,
            RunningRuntimeState.ShellingRequestState request,
            UUID occupiedByPlayerId
        ) {
            return new StartRequestResult(false, failureReason, request, occupiedByPlayerId);
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

    private enum SequenceKind {
        MISSILE
    }

    private static final class ActiveShellingSequence {
        private final UUID sequenceId;
        private final SequenceKind kind;
        private final String roomId;
        private final UUID ownerId;
        private final String ownerName;
        private final RoomTeamId teamId;
        private final Vec3 targetCenter;
        private final MissileConfigSnapshot missileConfig;
        private final long startTick;
        private final long nominalFinishTick;
        private final long intervalTicks;
        private final int totalShellCount;
        private final Set<UUID> loopRecipients = new LinkedHashSet<>();
        private final Set<TrackedProjectile> trackedProjectiles = new LinkedHashSet<>();
        private long nextShellSpawnTick;
        private int alarmsPlayed;
        private int spawnedShellCount;
        private int lastCountdownAnnouncedSecond = -1;

        private ActiveShellingSequence(
            UUID sequenceId,
            SequenceKind kind,
            String roomId,
            UUID ownerId,
            String ownerName,
            RoomTeamId teamId,
            Vec3 targetCenter,
            MissileConfigSnapshot missileConfig,
            long startTick,
            long nominalFinishTick,
            long intervalTicks,
            int totalShellCount
        ) {
            this.sequenceId = sequenceId == null ? UUID.randomUUID() : sequenceId;
            this.kind = kind == null ? SequenceKind.MISSILE : kind;
            this.roomId = roomId == null ? "" : roomId;
            this.ownerId = ownerId;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.teamId = teamId;
            this.targetCenter = targetCenter == null ? Vec3.ZERO : targetCenter;
            this.missileConfig = missileConfig;
            this.startTick = startTick;
            this.nominalFinishTick = nominalFinishTick;
            this.intervalTicks = Math.max(1L, intervalTicks);
            this.totalShellCount = Math.max(1, totalShellCount);
            this.nextShellSpawnTick = startTick + MISSILE_APPROACH_START_TICKS;
        }

        private static ActiveShellingSequence createMissile(
            String roomId,
            UUID ownerId,
            String ownerName,
            RoomTeamId teamId,
            Vec3 targetCenter,
            MissileConfigSnapshot missileConfig,
            long startTick,
            int totalShellCount
        ) {
            return new ActiveShellingSequence(
                UUID.randomUUID(),
                SequenceKind.MISSILE,
                roomId,
                ownerId,
                ownerName,
                teamId,
                targetCenter,
                missileConfig,
                startTick,
                startTick + MISSILE_WARNING_DURATION_TICKS,
                missileConfig == null ? 20L : missileConfig.intervalTicks(),
                totalShellCount
            );
        }
    }

    private record MissileConfigSnapshot(
        String gunId,
        String gunItemSnbt,
        String displayName,
        double durationSeconds,
        double intervalSeconds,
        double radius,
        double shellHeight,
        float pitch,
        float yaw,
        String impactTypeId
    ) {
        private static MissileConfigSnapshot fromEntry(ShellingDataModel.ShellingEntry entry) {
            return new MissileConfigSnapshot(
                entry == null ? "" : entry.getGunId(),
                entry == null ? "" : entry.getGunItemSnbt(),
                entry == null ? "" : entry.getDisplayName(),
                entry == null ? 0.0D : entry.getDurationSeconds(),
                entry == null ? 0.0D : entry.getIntervalSeconds(),
                entry == null ? 0.0D : entry.getRadius(),
                entry == null ? 0.0D : entry.getShellHeight(),
                entry == null ? 0.0F : entry.getPitch(),
                entry == null ? 0.0F : entry.getYaw(),
                entry == null ? ShellingImpactType.MISSILE.id() : entry.getImpactType()
            );
        }

        private long intervalTicks() {
            return Math.max(1L, Math.round(intervalSeconds * 20.0D));
        }

        private ShellingDataModel.ShellingEntry toEntry() {
            ShellingDataModel.ShellingEntry entry = new ShellingDataModel.ShellingEntry();
            entry.setGunId(gunId);
            entry.setGunItemSnbt(gunItemSnbt);
            entry.setDisplayName(displayName);
            entry.setDurationSeconds(durationSeconds);
            entry.setIntervalSeconds(intervalSeconds);
            entry.setRadius(radius);
            entry.setShellHeight(shellHeight);
            entry.setPitch(pitch);
            entry.setYaw(yaw);
            entry.setImpactType(impactTypeId);
            return entry;
        }
    }

    private record TrackedProjectile(ResourceKey<Level> dimension, UUID projectileId) {
    }
}
