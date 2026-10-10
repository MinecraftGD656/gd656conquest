package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.compat.ragdoll.DownedRagdollBridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.registries.ForgeRegistries;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.compat.tacz.TaczServerPoseCompat;
import org.mods.gd656conquest.server.facility.support.HeroKitSupportService;
import org.mods.gd656conquest.network.packet.PlayerAnimatorDebugPacket;
import org.mods.gd656conquest.network.packet.RoomDownedStatePacket;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;
import org.mods.gd656conquest.server.loadout.TaczDisplayNameResolver;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationSupport;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployViewKind;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployViewRequest;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleDeployExecutionService;
import org.mods.gd656conquest.server.room.vehicle.VehicleDeployExecutionResult;
import org.mods.gd656conquest.server.room.vehicle.VehicleDeployResolution;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class RoomPlayerRespawnService {
    private static final int DEATH_NO_KILLER_OPEN_DEPLOY_TICKS = 50;
    private static final int DEATH_WITH_KILLER_OPEN_DEPLOY_TICKS = 120;
    private static final int DOWNED_PREVIEW_TICKS = 8 + 4 + 8 + (2 * 20);
    private static final int DEPLOY_HOLDING_HOTBAR_SLOT = 6;
    private static final int POST_DEPLOY_VEHICLE_HOTBAR_SLOT = 6;
    private static final int DOWNED_DURATION_TICKS = 30 * 20;
    private static final int DOWNED_SYNC_INTERVAL_TICKS = 10;
    private static final int DOWNED_ANIMATOR_REFRESH_BUFFER_TICKS = 30;
    private static final int REVIVE_ASSAULT_HOLD_TICKS = 30;
    private static final int REVIVE_SQUAD_HOLD_TICKS = 70;
    private static final int REVIVE_LRTACTICAL_BLIND_TICKS = 30;
    private static final int RESPAWN_TRAVEL_TICKS = 40;
    private static final int INITIAL_DEPLOY_DELAY_SECONDS = 30;
    private static final int INITIAL_MOVEMENT_LOCK_SECONDS = 5;
    private static final int TITLE_FADE_IN_TICKS = 5;
    private static final int TITLE_STAY_TICKS = 20;
    private static final int TITLE_FADE_OUT_TICKS = 10;
    private static final int TITLE_REFRESH_FADE_IN_TICKS = 0;
    private static final int TITLE_REFRESH_STAY_TICKS = 22;
    private static final int TITLE_REFRESH_FADE_OUT_TICKS = 4;
    private static final int COUNTDOWN_NORMAL_COLOR = 0x55FFFF;
    private static final int COUNTDOWN_GREEN_COLOR = 0x55FF55;
    private static final int COUNTDOWN_YELLOW_COLOR = 0xFFFF55;
    private static final ResourceLocation LRTACTICAL_BLIND_ID = ResourceLocation.fromNamespaceAndPath("lrtactical", "blinded");
    private static final int COUNTDOWN_ORANGE_COLOR = 0xFF9900;
    private static final int COUNTDOWN_RED_COLOR = 0xFF5555;
    private static final int INITIAL_SUBTITLE_COLOR = 0xC6CCD1;
    private static final int TEAMMATE_DEPLOY_KILL_WINDOW_TICKS = 10 * 20;
    private static final double DOWNED_RESCUE_HINT_DISTANCE_SQR = 3.5D * 3.5D;
    private static final String ACTIONBAR_SOURCE_DEPLOY_WAIT = "room_deploy_wait";
    private static final String ACTIONBAR_SOURCE_DEPLOY_NOTICE = "room_deploy_notice";
    private static final String ACTIONBAR_SOURCE_DEPLOY_HINT = "room_deploy_hint";
    private static final String ACTIONBAR_SOURCE_DOWNED = "room_downed_wait";
    private static final String ACTIONBAR_SOURCE_DOWNED_RESCUE_TARGET = "room_downed_rescue_target";
    private static final String ACTIONBAR_SOURCE_DOWNED_RESCUE_PROMPT = "room_downed_rescue_prompt";
    private static final String ACTIONBAR_SOURCE_DOWNED_RESCUING = "room_downed_rescuing";
    private static final String ACTIONBAR_SOURCE_DOWNED_BEING_RESCUED = "room_downed_being_rescued";
    private static final String ACTIONBAR_SOURCE_DOWNED_KILLER_INFO = "room_downed_killer_info";
    private static final int DOWNED_KILLER_INFO_TICKS = 3 * 20;
    private static final int DOWNED_KILLER_MARKER_TICKS = 3 * 20;
    private static final String DOWNED_KILLER_MARKER_PREFIX = "downed_killer:";
    private static final String REDEPLOY_TRIGGER_REQUIREMENTS = "触发条件：仅限对局进行中、玩家当前存活且未处于死亡/部署流程时可用";

    private final MinecraftServer server;
    private final RoomPlayerLaunchService playerLaunchService;
    private final RoomPlayerStateService playerStateService;
    private final RoomPlayerTransferQueueService playerTransferQueueService;
    private final RoomDeploySelectionService deploySelectionService;
    private final RoomPlayerLoadoutService loadoutService;
    private final RoomGunmasterLoadoutService gunmasterLoadoutService;
    private final RoomAudioRuntimeService audioRuntimeService;
    private final RoomVehicleDeployExecutionService vehicleDeployExecutionService;
    private final RoomMarkerRuntimeService markerRuntimeService;

    public RoomPlayerRespawnService(
        MinecraftServer server,
        RoomPlayerLaunchService playerLaunchService,
        RoomPlayerStateService playerStateService,
        RoomPlayerTransferQueueService playerTransferQueueService,
        RoomDeploySelectionService deploySelectionService,
        RoomPlayerLoadoutService loadoutService,
        RoomGunmasterLoadoutService gunmasterLoadoutService,
        RoomAudioRuntimeService audioRuntimeService,
        RoomVehicleDeployExecutionService vehicleDeployExecutionService,
        RoomMarkerRuntimeService markerRuntimeService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerLaunchService = Objects.requireNonNull(playerLaunchService, "playerLaunchService");
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
        this.playerTransferQueueService = Objects.requireNonNull(playerTransferQueueService, "playerTransferQueueService");
        this.deploySelectionService = Objects.requireNonNull(deploySelectionService, "deploySelectionService");
        this.loadoutService = Objects.requireNonNull(loadoutService, "loadoutService");
        this.gunmasterLoadoutService = Objects.requireNonNull(gunmasterLoadoutService, "gunmasterLoadoutService");
        this.audioRuntimeService = Objects.requireNonNull(audioRuntimeService, "audioRuntimeService");
        this.vehicleDeployExecutionService = Objects.requireNonNull(vehicleDeployExecutionService, "vehicleDeployExecutionService");
        this.markerRuntimeService = Objects.requireNonNull(markerRuntimeService, "markerRuntimeService");
    }

    public void onPlayerDeath(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomPosition deathPosition,
        int killerEntityId,
        long gameTick
    ) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (playerId == null || deathPosition == null) {
            return;
        }
        boolean deathmatch = isArenaGameplay(room);
        int timingKillerId = org.mods.gd656conquest.server.room.entry.RoomDeathCauseService.legacyKillerEntityId(playerId, killerEntityId);
        long countdownStartTick = deathmatch
            ? gameTick
            : gameTick + (timingKillerId >= 0
                ? DEATH_WITH_KILLER_OPEN_DEPLOY_TICKS
                : DEATH_NO_KILLER_OPEN_DEPLOY_TICKS);
        HeroKitSupportService.endHeroKitState(server, state, playerId, gameTick);
        state.markPlayerDead(playerId, deathPosition, countdownStartTick, killerEntityId);
        notifyNearbyTeammatesManDown(state, playerId, deathPosition, killerEntityId, false);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            prepareRespawnAnchor(room, state, player);
            if (deathmatch) {
                ServerPlayer killerPlayer = findPlayerByEntityId(killerEntityId);
                if (killerPlayer != null && isEnemyCause(state, playerId, killerEntityId)) {
                    showKillerActionbar(player, killerPlayer, "击杀者: ");
                    markDownedKiller(room, state, player, killerPlayer, gameTick);
                }
                beginDeathmatchRespawnHold(room, state, player, gameTick, resolveDeployDelayTicks(room, state, 1), false, true);
                return;
            }
            NetworkManager.sendToPlayer(
                player,
                RoomDeathFlowPacket.startDeath(deathPosition.x(), deathPosition.y(), deathPosition.z(), killerEntityId)
            );
            org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.startDeath(server, state, playerId, deathPosition);
        }
    }

    public void emitNearbyTeammatesManDown(
        RunningRuntimeState state,
        UUID targetPlayerId,
        RoomPosition deathPosition,
        int killerEntityId,
        boolean requireEnemyCause
    ) {
        notifyNearbyTeammatesManDown(state, targetPlayerId, deathPosition, killerEntityId, requireEnemyCause);
    }

    public boolean tryEnterDownedState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomPosition deathPosition,
        int killerEntityId,
        long gameTick
    ) {
        if (org.mods.gd656conquest.server.room.entry.RoomDeathCauseService.skipDowned(player)) return false;
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (player == null || deathPosition == null || !room.commonConfig().enablePlayerDowned()) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (room.lifecycleState() != org.mods.gd656conquest.server.room.model.RoomLifecycleState.RUNNING
            || !state.getRoomPlayers().contains(playerId)
            || state.isInRespawnFlow(playerId)) {
            return false;
        }
        HeroKitSupportService.endHeroKitState(server, state, playerId, gameTick);
        long expireTick = gameTick + DOWNED_DURATION_TICKS;
        state.markPlayerDowned(playerId, deathPosition, gameTick, expireTick, killerEntityId);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.start(player, state);
        DownedRagdollBridge.start(player, state);
        notifyNearbyTeammatesManDown(state, playerId, deathPosition, killerEntityId, true);
        prepareRespawnAnchor(room, state, player);
        playerStateService.applyDownedState(room, player);
        NetworkManager.sendToPlayer(
            player,
            RoomDeathFlowPacket.startDeath(deathPosition.x(), deathPosition.y(), deathPosition.z(), killerEntityId)
        );
        sendDownedStart(player, state, deathPosition, killerEntityId, expireTick - gameTick);
        presentDownedKillerIntel(room, state, player, killerEntityId, gameTick);
        showDownedCountdown(player, (int) Math.ceil((expireTick - gameTick) / 20.0D));
        PlayerAnimatorDebugDimensionsState.startPreview(player, (int) Math.max(1000L, DOWNED_DURATION_TICKS * 50L));
        broadcastPlayerAnimatorState(state, playerId, (int) Math.max(1000L, DOWNED_DURATION_TICKS * 50L), true);
        return true;
    }

    public boolean handleDownedAction(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, String action, long gameTick) {
        return handleDownedAction(room, state, player, action, null, gameTick);
    }

    public boolean handleDownedAction(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, String action, UUID requestedTargetId, long gameTick) {
        if (room == null || state == null || player == null || action == null || action.isBlank()) {
            return false;
        }
        String normalized = action.trim();
        if ("surrender".equalsIgnoreCase(normalized)) {
            if (!state.isPlayerDowned(player.getUUID())) {
                return false;
            }
            if (state.isPlayerBeingRevived(player.getUUID())) {
                return false;
            }
            finishDownedState(room, state, player.getUUID(), gameTick);
            return true;
        }
        if ("redeploy".equalsIgnoreCase(normalized)) {
            return requestRedeploy(room, state, player).startsWith("已");
        }
        if ("revive_start".equalsIgnoreCase(normalized)) {
            return startReviveAttempt(room, state, player, requestedTargetId, gameTick);
        }
        if ("revive_stop".equalsIgnoreCase(normalized)) {
            return stopReviveAttempt(state, player.getUUID());
        }
        return false;
    }

    public boolean prepareRespawnAnchor(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (!isInRespawnFlow(state, player)) {
            return false;
        }
        ServerLevel level = playerLaunchService.findRoomLevel(room).orElse(null);
        if (level == null) {
            return false;
        }
        RoomPosition anchorPosition = RoomGameplayDeployPresentationSupport.resolveRespawnAnchorPosition(room, state, player);
        BlockPos anchor = resolveSafeRespawnAnchor(level, anchorPosition);
        if (anchor == null) {
            return false;
        }
        player.setRespawnPosition(level.dimension(), anchor, player.getYRot(), true, false);
        return true;
    }

    public boolean applyRespawnPostProcess(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (!isInRespawnFlow(state, player)) {
            return false;
        }
        playerStateService.applyDeployHoldingState(player);
        applyDeployHoldingSelectedHotbarSlot(player);
        RoomPosition holdingPosition = RoomGameplayDeployPresentationSupport.resolveRespawnHoldingPosition(room, state, player);
        if (holdingPosition != null) {
            playerLaunchService.teleportToRoomPosition(player, room, holdingPosition);
        }
        return true;
    }

    public String requestRedeploy(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (room == null || state == null || player == null) {
            return "当前不在任何房间";
        }
        if (room.lifecycleState() != org.mods.gd656conquest.server.room.model.RoomLifecycleState.RUNNING) {
            return "仅在对局进行中可用。 " + REDEPLOY_TRIGGER_REQUIREMENTS;
        }
        UUID playerId = player.getUUID();
        if (!state.getRoomPlayers().contains(playerId)) {
            return "你不在该房间内";
        }
        if (!player.isAlive()) {
            return "当前已死亡，不能再次触发重新部署。 " + REDEPLOY_TRIGGER_REQUIREMENTS;
        }
        if (player.isSpectator()) {
            return "当前处于旁观/部署视角，不能触发重新部署。 " + REDEPLOY_TRIGGER_REQUIREMENTS;
        }
        if (state.isPlayerBeingRevived(playerId)) {
            return "当前正在被救援，暂时不能放弃或重新部署";
        }
        if (state.isPlayerPendingDowned(playerId)) {
            skipDownedStateForRedeploy(room, state, player, server.getTickCount());
            return "已跳过当前倒地前观察，并进入重新部署";
        }
        if (state.isPlayerDowned(playerId)) {
            skipDownedStateForRedeploy(room, state, player, server.getTickCount());
            return "已跳过当前倒地状态，并进入重新部署";
        }
        if (state.isInRespawnFlow(playerId)) {
            return "当前已在死亡或部署流程中，不能重复触发。 " + REDEPLOY_TRIGGER_REQUIREMENTS;
        }
        state.clearRespawnFlow(playerId);
        state.getDoubledRespawnPending().add(playerId);
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        if (forceRedeployDeath(player)) {
            state.getRoomTeams().computeIfPresent(playerId, (id, teamId) -> {
                state.getGameplayRuntime().ifPresent(runtime -> {
                    runtime.onPlayerInvalidated(
                        room,
                        playerId,
                        teamId,
                        org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason.REDEPLOY,
                        -1,
                        server.getTickCount()
                    );
                });
                return teamId;
            });
            return "已触发重新部署：本次会立即自杀，并按双倍等待时长进入部署";
        }
        state.getDoubledRespawnPending().remove(playerId);
        return "重新部署触发失败，请稍后重试";
    }

    private boolean forceRedeployDeath(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.setShiftKeyDown(false);
        player.setInvulnerable(false);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        if (!RoomDeathEventDispatcher.isStillAliveOutsideDeployment(player)) {
            return true;
        }
        player.kill();
        return !RoomDeathEventDispatcher.isStillAliveOutsideDeployment(player);
    }

    public void beginInitialDeploy(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (isArenaGameplay(room)) {
            int deployDelayTicks = INITIAL_DEPLOY_DELAY_SECONDS * 20;
            state.setInitialMovementUnlockTick(gameTick + initialCombatUnlockDelayTicks(room));
            state.clearRespawnFlow(playerId);
            state.getDeployReadyTicks().put(playerId, gameTick + deployDelayTicks);
            state.getDeployAutoStartOnReadyPlayers().add(playerId);
            state.getDeployAllowFallbackOnMissingSelectionPlayers().add(playerId);
            RoomTeamId teamId = state.getRoomTeams().get(playerId);
            if (teamId == RoomTeamId.CAMP_A || teamId == RoomTeamId.CAMP_B) {
                deploySelectionService.applyInitialDefaultSelection(room, state, player, teamId);
            }
            enterDeployHoldingState(room, state, player, false);
            openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
                RoomGameplayDeployViewKind.INITIAL,
                gameTick,
                deployDelayTicks,
                false
            ));
            return;
        }
        int deployDelayTicks = INITIAL_DEPLOY_DELAY_SECONDS * 20;
        state.setInitialMovementUnlockTick(gameTick + deployDelayTicks + RESPAWN_TRAVEL_TICKS + INITIAL_MOVEMENT_LOCK_SECONDS * 20L);
        state.clearRespawnFlow(playerId);
        state.getDeployReadyTicks().put(playerId, gameTick + deployDelayTicks);
        state.getDeployAutoStartOnReadyPlayers().add(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().add(playerId);
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (teamId == RoomTeamId.CAMP_A || teamId == RoomTeamId.CAMP_B) {
            deploySelectionService.applyInitialDefaultSelection(room, state, player, teamId);
        }
        enterDeployHoldingState(room, state, player, false);
        openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
            RoomGameplayDeployViewKind.INITIAL,
            gameTick,
            deployDelayTicks,
            false
        ));
    }

    public void beginReconnectDeploy(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        playerStateService.clearPlayerState(playerId);
        TaczServerPoseCompat.clearCrawlState(player);
        PlayerAnimatorDebugDimensionsState.clearPlayer(player);
        NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        NetworkManager.sendToPlayer(player, PlayerAnimatorDebugPacket.stop(playerId));
        int deployDelayTicks = resolveDeployDelayTicks(room, state, 1);
        state.clearRespawnFlow(playerId);
        state.getDeployReadyTicks().put(playerId, gameTick + deployDelayTicks);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().add(playerId);
        state.getDeployExplicitConfirmRequiredPlayers().add(playerId);
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (teamId == RoomTeamId.CAMP_A || teamId == RoomTeamId.CAMP_B) {
            deploySelectionService.applyInitialDefaultSelection(room, state, player, teamId);
        }
        if (tryBeginAlignedOpeningJoinDeploy(room, state, player, gameTick, RoomGameplayDeployViewKind.RECONNECT)) {
            return;
        }
        if (isArenaGameplay(room)) {
            enterDeployHoldingState(room, state, player, false);
            openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
                RoomGameplayDeployViewKind.RECONNECT,
                gameTick,
                deployDelayTicks,
                false
            ));
            return;
        }
        enterDeployHoldingState(room, state, player, false);
        openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
            RoomGameplayDeployViewKind.RECONNECT,
            gameTick,
            deployDelayTicks,
            false
        ));
    }

    public void beginMidJoinDeploy(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        int deployDelayTicks = resolveDeployDelayTicks(room, state, 1);
        state.clearRespawnFlow(playerId);
        state.getDeployReadyTicks().put(playerId, gameTick + deployDelayTicks);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().add(playerId);
        state.getDeployExplicitConfirmRequiredPlayers().add(playerId);
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        if (tryBeginAlignedOpeningJoinDeploy(room, state, player, gameTick, RoomGameplayDeployViewKind.MIDJOIN)) {
            return;
        }
        deploySelectionService.clearSelection(state, playerId);
        if (isArenaGameplay(room)) {
            RoomTeamId teamId = state.getRoomTeams().get(playerId);
            if (teamId == RoomTeamId.CAMP_A || teamId == RoomTeamId.CAMP_B) {
                deploySelectionService.applyInitialDefaultSelection(room, state, player, teamId);
            }
            enterDeployHoldingState(room, state, player, false);
            openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
                RoomGameplayDeployViewKind.MIDJOIN,
                gameTick,
                deployDelayTicks,
                true
            ));
            return;
        }
        enterDeployHoldingState(room, state, player, false);
        openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
            RoomGameplayDeployViewKind.MIDJOIN,
            gameTick,
            deployDelayTicks,
            true
        ));
    }

    public boolean handleDeploySelection(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        String action,
        String kind,
        String targetId,
        long gameTick
    ) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (player == null || !player.isAlive()) {
            return false;
        }
        UUID playerId = player.getUUID();
        Long deployReadyTick = state.getDeployReadyTicks().get(playerId);
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (deployReadyTick == null || teamId == null) {
            return false;
        }
        String previousSelectionKind = state.getDeploySelectionKinds().get(playerId);
        String previousSelectionTargetId = state.getDeploySelectionTargetIds().get(playerId);
        boolean explicitConfirmRequired = state.getDeployExplicitConfirmRequiredPlayers().contains(playerId);
        if (!deploySelectionService.updateSelection(room, state, playerId, teamId, kind, targetId)) {
            showDeployNotice(player, deploySelectionService.describeSelectionFailure(room, state, playerId, teamId, kind, targetId));
            return false;
        }
        if (!"confirm".equalsIgnoreCase(action)) {
            if (explicitConfirmRequired || !state.getDeployAllowFallbackOnMissingSelectionPlayers().contains(playerId)) {
                state.getDeployAutoStartOnReadyPlayers().remove(playerId);
            }
            LoadoutUiService.sendSnapshot(player, false, true);
            return true;
        }
        String currentSelectionKind = state.getDeploySelectionKinds().get(playerId);
        String currentSelectionTargetId = state.getDeploySelectionTargetIds().get(playerId);
        boolean selectionAlreadyChosen = equalsDeploySelection(
            previousSelectionKind,
            previousSelectionTargetId,
            currentSelectionKind,
            currentSelectionTargetId
        );
        if (explicitConfirmRequired && !selectionAlreadyChosen) {
            state.getDeployAutoStartOnReadyPlayers().remove(playerId);
            showDeployNotice(player, "已选定部署点，请再次确认后部署");
            LoadoutUiService.sendSnapshot(player, false, true);
            return true;
        }
        state.getDeployExplicitConfirmRequiredPlayers().remove(playerId);
        if (gameTick < deployReadyTick) {
            state.getDeployAutoStartOnReadyPlayers().add(playerId);
            LoadoutUiService.sendSnapshot(player, false, true);
            return true;
        }
        boolean started = tryStartSelectedDeploy(room, state, player, gameTick, true);
        LoadoutUiService.sendSnapshot(player, false, true);
        return started;
    }

    public boolean requestDeathmatchDeployView(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        long gameTick
    ) {
        if (!isArenaGameplay(room) || state == null || player == null || !player.isAlive()) {
            return false;
        }
        UUID playerId = player.getUUID();
        Long deployReadyTick = state.getDeployReadyTicks().get(playerId);
        if (deployReadyTick == null || state.getRespawnFinalizeTicks().containsKey(playerId)) {
            return false;
        }
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            return false;
        }
        long remainTicks = Math.max(0L, deployReadyTick - gameTick);
        deploySelectionService.applyInitialDefaultSelection(room, state, player, teamId);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().add(playerId);
        state.getDeployExplicitConfirmRequiredPlayers().add(playerId);
        state.getRespawnCountdownLastShownSeconds().remove(playerId);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_HINT);
        clearInitialCombatCountdownTitle(player);
        enterDeployHoldingState(room, state, player, false);
        openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
            RoomGameplayDeployViewKind.RESPAWN,
            gameTick,
            (int) remainTicks,
            false
        ));
        LoadoutUiService.sendSnapshot(player, false, true);
        return true;
    }

    public void tick(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");

        tickPendingDownedPlayers(room, state, gameTick);
        tickActiveRevives(room, state, gameTick);
        tickDownedPlayers(room, state, gameTick);
        tickDownedRescueHints(state);
        ensureRespawnFlowPresentation(room, state);

        List<UUID> countdownStarted = new ArrayList<>();
        for (var entry : state.getRespawnCountdownStartTicks().entrySet()) {
            if (gameTick < entry.getValue()) {
                continue;
            }
            UUID playerId = entry.getKey();
            int deployDelayTicks = resolveDeployDelayTicks(room, state, 1);
            if (state.getDoubledRespawnPending().remove(playerId)) {
                deployDelayTicks *= 2;
            }
            state.getDeployReadyTicks().put(playerId, gameTick + deployDelayTicks);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                if (isArenaGameplay(room)) {
                    beginDeathmatchRespawnHold(room, state, player, gameTick, deployDelayTicks, false, true);
                } else {
                    enterDeployHoldingState(room, state, player, false);
                    openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
                        RoomGameplayDeployViewKind.RESPAWN,
                        gameTick,
                        deployDelayTicks,
                        false
                    ));
                }
            }
            countdownStarted.add(playerId);
        }
        for (UUID playerId : countdownStarted) {
            state.getRespawnCountdownStartTicks().remove(playerId);
            state.getDeathKillerEntityIds().remove(playerId);
        }

        for (var entry : new ArrayList<>(state.getDeployReadyTicks().entrySet())) {
            UUID playerId = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            long remainTicks = entry.getValue() - gameTick;
            if (remainTicks > 0 && player != null) {
                playerStateService.setSpectator(player);
                int remainSeconds = (int) Math.ceil(remainTicks / 20.0D);
                if (isArenaGameplay(room)) {
                    if (state.getDeployViewActivePlayers().contains(playerId)) {
                        clearInitialCombatCountdownTitle(player);
                        state.getRespawnCountdownLastShownSeconds().remove(playerId);
                        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_HINT);
                        if (!state.getDeployExplicitConfirmRequiredPlayers().contains(playerId)) {
                            showDeployCountdown(player, remainSeconds);
                        } else {
                            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_WAIT);
                        }
                    } else {
                        int lastShown = state.getRespawnCountdownLastShownSeconds().getOrDefault(playerId, -1);
                        if (lastShown != remainSeconds) {
                            sendRespawnCountdownTitle(player, remainSeconds, lastShown < 0);
                            state.getRespawnCountdownLastShownSeconds().put(playerId, remainSeconds);
                        }
                        showDeathmatchDeployHint(player);
                    }
                } else {
                    showDeployCountdown(player, remainSeconds);
                }
            }
            if (gameTick < entry.getValue()) {
                continue;
            }
            if (player != null) {
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_WAIT);
                playerStateService.setSpectator(player);
                if (isArenaGameplay(room)) {
                    clearInitialCombatCountdownTitle(player);
                    state.getRespawnCountdownLastShownSeconds().remove(playerId);
                    ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_HINT);
                    if (!state.getDeployViewActivePlayers().contains(playerId)
                        && startDeathmatchRespawnTransition(room, state, player, gameTick, true)) {
                        continue;
                    }
                }
                boolean allowFallbackOnMissingSelection = state.getDeployAllowFallbackOnMissingSelectionPlayers().contains(playerId);
                boolean shouldAutoStart = state.getDeployAutoStartOnReadyPlayers().contains(playerId);
                if (!shouldAutoStart) {
                    continue;
                }
                boolean started = tryStartSelectedDeploy(room, state, player, gameTick, allowFallbackOnMissingSelection);
                if (!started && !allowFallbackOnMissingSelection) {
                    state.getDeployAutoStartOnReadyPlayers().remove(playerId);
                }
            }
        }

        List<UUID> finalizeDone = new ArrayList<>();
        for (var entry : new ArrayList<>(state.getRespawnFinalizeTicks().entrySet())) {
            UUID playerId = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (gameTick < entry.getValue()) {
                if (player != null && player.isAlive()) {
                    playerStateService.setSpectator(player);
                    RoomPosition targetPosition = state.getRespawnTargetPositions().get(playerId);
                    if (targetPosition != null) {
                        playerLaunchService.teleportToRoomPosition(player, room, targetPosition);
                    }
                    player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                }
                continue;
            }
            if (player != null && player.isAlive()) {
                if (finalizeRespawn(room, state, player, gameTick)) {
                    finalizeDone.add(playerId);
                    refreshVehicleLoadoutSnapshots(state);
                }
                continue;
            }
            finalizeDone.add(playerId);
        }
        for (UUID playerId : finalizeDone) {
            state.clearRespawnFlow(playerId);
        }

        syncInitialMovementLock(room, state, gameTick);
    }

    public boolean isInRespawnFlow(RunningRuntimeState state, ServerPlayer player) {
        return player != null && state != null && state.isInRespawnFlow(player.getUUID());
    }

    public boolean isPlayerDowned(RunningRuntimeState state, ServerPlayer player) {
        return player != null && state != null && state.isPlayerDowned(player.getUUID());
    }

    private BlockPos resolveSafeRespawnAnchor(ServerLevel level, RoomPosition anchorPosition) {
        if (level == null) {
            return null;
        }
        BlockPos base = anchorPosition == null
            ? level.getSharedSpawnPos()
            : new BlockPos(anchorPosition.x(), (int) Math.floor(anchorPosition.y()), anchorPosition.z());
        if (isSafeRespawnFootPosition(level, base)) {
            return base;
        }
        for (int radius = 0; radius <= 4; radius++) {
            for (int yOffset = -4; yOffset <= 4; yOffset++) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                            continue;
                        }
                        BlockPos candidate = base.offset(xOffset, yOffset, zOffset);
                        if (isSafeRespawnFootPosition(level, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        BlockPos sharedSpawn = level.getSharedSpawnPos();
        return isSafeRespawnFootPosition(level, sharedSpawn) ? sharedSpawn : base;
    }

    private boolean isSafeRespawnFootPosition(ServerLevel level, BlockPos feetPos) {
        if (level == null || feetPos == null) {
            return false;
        }
        BlockPos headPos = feetPos.above();
        BlockPos belowPos = feetPos.below();
        var feetState = level.getBlockState(feetPos);
        var headState = level.getBlockState(headPos);
        var belowState = level.getBlockState(belowPos);
        if (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
            return false;
        }
        if (!feetState.getCollisionShape(level, feetPos).isEmpty() || !headState.getCollisionShape(level, headPos).isEmpty()) {
            return false;
        }
        if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
            return false;
        }
        double centerX = feetPos.getX() + 0.5D;
        double centerY = feetPos.getY();
        double centerZ = feetPos.getZ() + 0.5D;
        var bounds = EntityType.PLAYER.getDimensions().makeBoundingBox(centerX, centerY, centerZ);
        return level.noCollision(bounds);
    }

    private void ensureRespawnFlowPresentation(RoomDefinition room, RunningRuntimeState state) {
        List<UUID> trackedPlayers = new ArrayList<>();
        trackedPlayers.addAll(state.getRespawnCountdownStartTicks().keySet());
        trackedPlayers.addAll(state.getDeployReadyTicks().keySet());
        trackedPlayers.addAll(state.getRespawnFinalizeTicks().keySet());
        for (UUID playerId : trackedPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (!player.isAlive()) {
                ServerPlayer respawned = server.getPlayerList().respawn(player, false);
                if (respawned != null) {
                    applyRespawnPostProcess(room, state, respawned);
                }
                continue;
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                applyRespawnPostProcess(room, state, player);
            }
        }
    }

    private void tickDownedPlayers(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || state.getDownedExpireTicks().isEmpty()) {
            return;
        }
        for (var entry : new ArrayList<>(state.getDownedExpireTicks().entrySet())) {
            UUID playerId = entry.getKey();
            long remainTicks = entry.getValue() - gameTick;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (remainTicks <= 0L) {
                finishDownedState(room, state, playerId, gameTick);
                continue;
            }
            if (player == null) {
                continue;
            }
            playerStateService.applyDownedState(room, player);
            if (state.isPlayerBeingRevived(playerId)) {
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED);
            } else {
                showDownedCountdown(player, (int) Math.ceil(remainTicks / 20.0D));
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED_BEING_RESCUED);
            }
            if (gameTick == state.getDownedStartTicks().getOrDefault(playerId, Long.MIN_VALUE)
                || gameTick % DOWNED_SYNC_INTERVAL_TICKS == 0L) {
                sendDownedUpdate(player, state, (int) remainTicks);
                broadcastPlayerAnimatorRefresh(state, playerId, remainTicks);
            }
        }
    }

    private void tickDownedRescueHints(RunningRuntimeState state) {
        if (state == null) {
            return;
        }
        for (UUID viewerId : new ArrayList<>(state.getRoomPlayers())) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null || !viewer.isAlive() || viewer.isSpectator() || state.isInRespawnFlow(viewerId) || state.isPlayerDowned(viewerId)) {
                clearDownedRescueHint(viewer);
                continue;
            }
            if (state.getReviveTargetByReviver().containsKey(viewerId)) {
                clearDownedRescueHint(viewer);
                continue;
            }
            DownedRescueHint hint = resolveNearestDownedRescueHint(state, viewer);
            if (hint == null) {
                clearDownedRescueHint(viewer);
                continue;
            }
            ServerActionBarService.showComponent(
                viewer,
                ACTIONBAR_SOURCE_DOWNED_RESCUE_TARGET,
                ServerActionBarService.Templates.subjectAction("倒地的 ", ChatFormatting.RED, hint.targetName(), ChatFormatting.WHITE, "", ChatFormatting.WHITE),
                25
            );
            ServerActionBarService.showText(viewer, ACTIONBAR_SOURCE_DOWNED_RESCUE_PROMPT, hint.prompt(), 25);
        }
    }

    private void tickPendingDownedPlayers(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || state.getPendingDownedStartTicks().isEmpty()) {
            return;
        }
        for (var entry : new ArrayList<>(state.getPendingDownedStartTicks().entrySet())) {
            UUID playerId = entry.getKey();
            long startTick = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            playerStateService.applyDownedState(room, player);
            if (gameTick < startTick) {
                continue;
            }
            activateDownedState(room, state, player, gameTick);
        }
    }

    private void tickActiveRevives(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || state.getReviveTargetByReviver().isEmpty()) {
            return;
        }
        for (var entry : new ArrayList<>(state.getReviveTargetByReviver().entrySet())) {
            UUID reviverId = entry.getKey();
            UUID targetId = entry.getValue();
            ServerPlayer reviver = server.getPlayerList().getPlayer(reviverId);
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            ActiveReviveContext context = resolveActiveReviveContext(state, reviver, target);
            if (context == null) {
                stopReviveAttempt(state, reviverId);
                continue;
            }
            if (context.forceCrouch()) {
                context.reviver().setShiftKeyDown(true);
                context.reviver().setForcedPose(Pose.CROUCHING);
                context.reviver().refreshDimensions();
                context.reviver().setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            state.getDownedExpireTicks().computeIfPresent(context.target().getUUID(), (id, expireTick) -> expireTick + 1L);
            int requiredTicks = context.requiredTicks();
            long startTick = state.getReviveStartTicksByReviver().getOrDefault(reviverId, gameTick);
            int elapsedTicks = (int) Math.max(0L, gameTick - startTick + 1L);
            int remainTicks = Math.max(0, requiredTicks - elapsedTicks);
            showReviveProgress(context.reviver(), remainTicks);
            showBeingRevived(context.target(), context.reviver());
            if (elapsedTicks >= requiredTicks) {
                completeRevive(room, state, context.reviver(), context.target(), gameTick);
            }
        }
    }

    private boolean startReviveAttempt(RoomDefinition room, RunningRuntimeState state, ServerPlayer reviver, UUID requestedTargetId, long gameTick) {
        if (room == null || state == null || reviver == null || !reviver.isAlive() || reviver.isSpectator() || state.isPlayerDowned(reviver.getUUID()) || state.isInRespawnFlow(reviver.getUUID())) {
            return false;
        }
        UUID targetId = ReviveTargetSelection.select(requestedTargetId,
            () -> {
                DownedRescueHint hint = resolveNearestDownedRescueHint(state, reviver);
                return hint != null && hint.canRevive() ? hint.targetId() : null;
            },
            id -> resolveActiveReviveContext(state, reviver, server.getPlayerList().getPlayer(id)) != null
                && !state.isPlayerBeingRevived(id));
        if (targetId == null) {
            return false;
        }
        clearDownedRescueHint(reviver);
        state.startRevive(reviver.getUUID(), targetId, gameTick);
        
        boolean isAssault = resolvePlayerClassType(reviver) == PlayerClassType.ASSAULT;
        if (isAssault) {
            audioRuntimeService.emitPlayerThirdPersonCue(room, state, reviver, RoomThirdPersonAudioCueRegistry.PLAYER_REVIVING);
            FrontlineVoiceBridge.playCue(reviver, "REVIVE_ASSAULT_START");
            FrontlineVoiceBridge.playCue(reviver, "REVIVE_ASSAULT_CHARGE");
        } else {
            audioRuntimeService.emitPlayerThirdPersonCue(room, state, reviver, RoomThirdPersonAudioCueRegistry.PLAYER_REVIVING);
            FrontlineVoiceBridge.playCue(reviver, "REVIVE_NORMAL_START");
        }
        
        return true;
    }

    private boolean stopReviveAttempt(RunningRuntimeState state, UUID reviverId) {
        if (state == null || reviverId == null) {
            return false;
        }
        UUID targetId = state.getReviveTargetByReviver().get(reviverId);
        if (targetId == null) {
            return false;
        }
        ServerPlayer reviver = server.getPlayerList().getPlayer(reviverId);
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        clearRevivePresentation(reviver, target);
        releaseForcedReviveCrouch(reviver);
        state.clearReviveState(reviverId);
        return true;
    }

    private ActiveReviveContext resolveActiveReviveContext(RunningRuntimeState state, ServerPlayer reviver, ServerPlayer target) {
        if (state == null || reviver == null || target == null || !reviver.isAlive() || !target.isAlive() || reviver.isSpectator() || target.isSpectator()) {
            return null;
        }
        UUID reviverId = reviver.getUUID();
        UUID targetId = target.getUUID();
        if (!state.isPlayerDowned(targetId) || state.isInRespawnFlow(reviverId)) {
            return null;
        }
        RoomTeamId reviverTeam = state.getRoomTeams().get(reviverId);
        if (reviverTeam == null || reviverTeam != state.getRoomTeams().get(targetId)) {
            return null;
        }
        boolean sameSquad = isSameSquad(state.getSquadLabelsByPlayer().get(reviverId), state.getSquadLabelsByPlayer().get(targetId));
        boolean assault = resolvePlayerClassType(reviver) == PlayerClassType.ASSAULT;
        if (!assault && !sameSquad) {
            return null;
        }
        if (DownedRagdollBridge.distanceSquared(reviver, target) > DOWNED_RESCUE_HINT_DISTANCE_SQR) {
            return null;
        }
        return new ActiveReviveContext(reviver, target, sameSquad, assault ? REVIVE_ASSAULT_HOLD_TICKS : REVIVE_SQUAD_HOLD_TICKS, !assault && sameSquad);
    }

    private void activateDownedState(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        if (room == null || state == null || player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (!state.isPlayerPendingDowned(playerId)) {
            return;
        }
        RoomPosition deathPosition = state.getDeathPositions().get(playerId);
        int killerEntityId = state.getDeathKillerEntityIdOrDefault(playerId);
        long expireTick = gameTick + DOWNED_DURATION_TICKS;
        state.markPlayerDowned(playerId, deathPosition, gameTick, expireTick, killerEntityId);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.start(player, state);
        DownedRagdollBridge.start(player, state);
        playerStateService.applyDownedState(room, player);
        sendDownedStart(player, state, deathPosition, killerEntityId, expireTick - gameTick);
        presentDownedKillerIntel(room, state, player, killerEntityId, gameTick);
        showDownedCountdown(player, (int) Math.ceil((expireTick - gameTick) / 20.0D));
    }

    private boolean tryBeginAlignedOpeningJoinDeploy(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        long gameTick,
        RoomGameplayDeployViewKind viewKind
    ) {
        if (room == null || state == null || player == null || viewKind == null) {
            return false;
        }
        long unlockTick = state.getInitialMovementUnlockTick();
        if (unlockTick <= gameTick) {
            return false;
        }
        long openingReadyTick = unlockTick - RESPAWN_TRAVEL_TICKS - INITIAL_MOVEMENT_LOCK_SECONDS * 20L;
        long openingLockTicks = resolveInitialMovementLockTicks(room);
        openingReadyTick = unlockTick - RESPAWN_TRAVEL_TICKS - openingLockTicks;
        if (openingReadyTick <= 0L) {
            return false;
        }
        UUID playerId = player.getUUID();
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (teamId == null) {
            return false;
        }
        boolean midJoinForcedDeploy = viewKind == RoomGameplayDeployViewKind.MIDJOIN;
        if (midJoinForcedDeploy) {
            deploySelectionService.applyInitialDefaultSelection(room, state, player, teamId);
            state.getDeployAutoStartOnReadyPlayers().add(playerId);
            state.getDeployExplicitConfirmRequiredPlayers().remove(playerId);
        }
        if (gameTick < openingReadyTick) {
            enterDeployHoldingState(room, state, player, false);
            int remainTicks = (int) Math.max(0L, openingReadyTick - gameTick);
            state.getDeployReadyTicks().put(playerId, openingReadyTick);
            openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
                viewKind,
                gameTick,
                remainTicks,
                midJoinForcedDeploy
            ));
            return true;
        }
        if (viewKind == RoomGameplayDeployViewKind.RECONNECT) {
            return false;
        }
        RoomPosition spawn = deploySelectionService.resolveSelectedSpawn(
            room,
            state,
            player,
            teamId,
            state.getDeploySelectionKinds().get(playerId),
            state.getDeploySelectionTargetIds().get(playerId),
            true
        );
        if (spawn == null) {
            return false;
        }
        long openingFinalizeTick = openingReadyTick + RESPAWN_TRAVEL_TICKS;
        if (gameTick < openingFinalizeTick) {
            state.getDeployReadyTicks().remove(playerId);
            state.getDeployAutoStartOnReadyPlayers().remove(playerId);
            state.getDeployAllowFallbackOnMissingSelectionPlayers().remove(playerId);
            state.getRespawnTargetPositions().put(playerId, spawn);
            state.getRespawnFinalizeTicks().put(playerId, openingFinalizeTick);
            enterDeployHoldingState(room, state, player, false);
            playerLaunchService.teleportToRoomPosition(player, room, spawn);
            NetworkManager.sendToPlayer(
                player,
                RoomDeathFlowPacket.startRespawnTransition(
                    spawn.x() + 0.5D,
                    spawn.y() + player.getEyeHeight(),
                    spawn.z() + 0.5D,
                    player.getYRot(),
                    0.0F,
                    (int) Math.max(0L, openingFinalizeTick - gameTick)
                )
            );
            return true;
        }
        state.getDeployReadyTicks().remove(playerId);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().remove(playerId);
        state.getRespawnFinalizeTicks().remove(playerId);
        state.getRespawnTargetPositions().put(playerId, spawn);
        playerLaunchService.teleportToRoomPosition(player, room, spawn);
        HeroKitSupportService.endHeroKitState(server, state, playerId, gameTick);
        state.getHeroKitLoadoutResetPendingPlayers().remove(playerId);
        applyRespawnLoadout(room, state, player);
        markInitialMovementLocked(state, playerId);
        playerStateService.applyRespawnState(room, playerId, gameTick);
        applyPostDeploySelectedHotbarSlot(room, player, false);
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        state.clearRespawnFlow(playerId);
        return true;
    }

    private void finishDownedState(RoomDefinition room, RunningRuntimeState state, UUID playerId, long gameTick) {
        if (room == null || state == null || playerId == null || !state.isPlayerDowned(playerId)) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        UUID reviverId = state.getReviveReviverByTarget().get(playerId);
        ServerPlayer reviver = reviverId == null ? null : server.getPlayerList().getPlayer(reviverId);
        int killerEntityId = state.getDeathKillerEntityIdOrDefault(playerId);
        int deployDelayTicks = resolveDeployDelayTicksFromDowned(room, state, playerId, gameTick);
        DownedRagdollBridge.stop(player, false);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.end(player);
        clearRevivePresentation(reviver, player);
        releaseForcedReviveCrouch(reviver);
        state.clearRespawnFlow(playerId);
        if (player != null) {
            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED);
            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED_BEING_RESCUED);
            PlayerAnimatorDebugDimensionsState.stopPreview(player);
            NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
            FrontlineVoiceBridge.playConfirmedDeath(player);
            beginDeployFromDowned(room, state, player, killerEntityId, gameTick, deployDelayTicks);
        }
        state.clearReviveState(playerId);
        broadcastPlayerAnimatorState(state, playerId, 0, false);
    }

    private void skipDownedStateForRedeploy(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        if (room == null || state == null || player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        UUID reviverId = state.getReviveReviverByTarget().get(playerId);
        ServerPlayer reviver = reviverId == null ? null : server.getPlayerList().getPlayer(reviverId);
        clearRevivePresentation(reviver, player);
        releaseForcedReviveCrouch(reviver);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED_BEING_RESCUED);
        NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        PlayerAnimatorDebugDimensionsState.stopPreview(player);
        int killerEntityId = state.getDeathKillerEntityIdOrDefault(playerId);
        int deployDelayTicks = resolveDeployDelayTicksFromDowned(room, state, playerId, gameTick);
        DownedRagdollBridge.stop(player, false);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.end(player);
        state.clearReviveState(playerId);
        state.clearRespawnFlow(playerId);
        broadcastPlayerAnimatorState(state, playerId, 0, false);
        FrontlineVoiceBridge.playConfirmedDeath(player);
        beginDeployFromDowned(room, state, player, killerEntityId, gameTick, deployDelayTicks);
    }

    private void beginDeployFromDowned(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, int killerEntityId, long gameTick, int deployDelayTicks) {
        if (room == null || state == null || player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        notifyGameplayConfirmedDeath(room, state, playerId, killerEntityId, gameTick);
        if (state.getDoubledRespawnPending().remove(playerId)) {
            deployDelayTicks *= 2;
        }
        if (isArenaGameplay(room)) {
            beginDeathmatchRespawnHold(room, state, player, gameTick, deployDelayTicks, false, true);
            return;
        }
        state.getDeployReadyTicks().put(playerId, gameTick + deployDelayTicks);
        enterDeployHoldingState(room, state, player, false);
        openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
            RoomGameplayDeployViewKind.RESPAWN,
            gameTick,
            deployDelayTicks,
            false
        ));
    }

    private int resolveDeployDelayTicksFromDowned(RoomDefinition room, RunningRuntimeState state, UUID playerId, long gameTick) {
        int deployDelayTicks = resolveDeployDelayTicks(room, state, 1);
        if (room == null || state == null || playerId == null || !room.commonConfig().enableRespawnCountdownWhileDowned()) {
            return deployDelayTicks;
        }
        Long downedStartTick = state.getDownedStartTicks().get(playerId);
        if (downedStartTick == null) {
            return deployDelayTicks;
        }
        long elapsedTicks = Math.max(0L, gameTick - downedStartTick);
        return (int) Math.max(0L, deployDelayTicks - elapsedTicks);
    }

    private void notifyNearbyTeammatesManDown(
        RunningRuntimeState state,
        UUID targetPlayerId,
        RoomPosition deathPosition,
        int killerEntityId,
        boolean requireEnemyCause
    ) {
        if (state == null || targetPlayerId == null || deathPosition == null) {
            return;
        }
        RoomTeamId targetTeam = state.getRoomTeams().get(targetPlayerId);
        if (targetTeam == null) {
            return;
        }
        if (requireEnemyCause && !isEnemyCause(state, targetPlayerId, killerEntityId)) {
            return;
        }
        double maxDistanceSqr = 5.0D * 5.0D;
        for (UUID candidateId : state.getRoomPlayers()) {
            if (targetPlayerId.equals(candidateId) || state.isInRespawnFlow(candidateId)) {
                continue;
            }
            if (state.getRoomTeams().get(candidateId) != targetTeam) {
                continue;
            }
            ServerPlayer candidate = server.getPlayerList().getPlayer(candidateId);
            if (candidate == null) {
                continue;
            }
            double dx = candidate.getX() - deathPosition.x();
            double dy = candidate.getY() - deathPosition.y();
            double dz = candidate.getZ() - deathPosition.z();
            if ((dx * dx) + (dy * dy) + (dz * dz) > maxDistanceSqr) {
                continue;
            }
            FrontlineVoiceBridge.playCue(candidate, RoomThirdPersonAudioCueRegistry.PLAYER_TEAMMATE_DOWNED);
        }
    }

    private boolean isEnemyCause(RunningRuntimeState state, UUID targetPlayerId, int killerEntityId) {
        if (state == null || targetPlayerId == null || killerEntityId < 0) {
            return false;
        }
        ServerPlayer killerPlayer = findPlayerByEntityId(killerEntityId);
        if (killerPlayer == null) {
            return true;
        }
        RoomTeamId targetTeam = state.getRoomTeams().get(targetPlayerId);
        RoomTeamId killerTeam = state.getRoomTeams().get(killerPlayer.getUUID());
        return targetTeam != null && killerTeam != null && killerTeam != targetTeam;
    }

    private ServerPlayer findPlayerByEntityId(int entityId) {
        if (entityId < 0) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getId() == entityId) {
                return player;
            }
        }
        return null;
    }

    private void presentDownedKillerIntel(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer downedPlayer,
        int killerEntityId,
        long gameTick
    ) {
        if (room == null || state == null || downedPlayer == null || killerEntityId < 0) {
            return;
        }
        ServerPlayer killerPlayer = findPlayerByEntityId(killerEntityId);
        if (killerPlayer == null || !isEnemyCause(state, downedPlayer.getUUID(), killerEntityId)) {
            return;
        }
        showKillerActionbar(downedPlayer, killerPlayer, "击倒者: ");
        markDownedKiller(room, state, downedPlayer, killerPlayer, gameTick);
    }

    private void showKillerActionbar(ServerPlayer targetPlayer, ServerPlayer killerPlayer, String killerLabel) {
        if (targetPlayer == null || killerPlayer == null) {
            return;
        }
        String killerName = killerPlayer.getScoreboardName();
        if (killerName == null || killerName.isBlank()) {
            killerName = "未知玩家";
        }
        String weaponName = "同时死亡";
        ItemStack heldStack = killerPlayer.getMainHandItem();
        if (heldStack != null && !heldStack.isEmpty()) {
            weaponName = resolveDownedKillerWeaponName(killerPlayer, heldStack);
        }
        ServerActionBarService.showBuilder(
            targetPlayer,
            ACTIONBAR_SOURCE_DOWNED_KILLER_INFO,
            ServerActionBarService.builder()
                .labelValue(killerLabel, killerName, ChatFormatting.RED, ChatFormatting.GOLD)
                .separator()
                .labelValue("武器: ", weaponName, ChatFormatting.AQUA, ChatFormatting.WHITE),
            DOWNED_KILLER_INFO_TICKS
        );
    }

    private String resolveDownedKillerWeaponName(ServerPlayer killerPlayer, ItemStack heldStack) {
        if (killerPlayer != null
            && killerPlayer.server != null
            && TaczDisplayNameResolver.isTaczGun(heldStack)) {
            String localized = TaczDisplayNameResolver.resolve(killerPlayer.server, heldStack);
            if (localized != null && !localized.isBlank()) {
                return localized;
            }
        }
        return heldStack.getHoverName().getString();
    }

    private void markDownedKiller(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer downedPlayer,
        ServerPlayer killerPlayer,
        long gameTick
    ) {
        if (room == null || state == null || downedPlayer == null || killerPlayer == null) {
            return;
        }
        RoomTeamId visibleTeam = state.getRoomTeams().get(downedPlayer.getUUID());
        if (visibleTeam != RoomTeamId.CAMP_A && visibleTeam != RoomTeamId.CAMP_B) {
            return;
        }
        boolean changed = markerRuntimeService.putGameplayTrackedMarker(
            state,
            DOWNED_KILLER_MARKER_PREFIX + downedPlayer.getUUID(),
            visibleTeam,
            killerPlayer.getUUID(),
            "击倒敌军",
            gameTick,
            gameTick + DOWNED_KILLER_MARKER_TICKS
        );
        if (changed) {
            markerRuntimeService.syncRunningRoom(room, state, gameTick);
        }
    }

    private void notifyGameplayConfirmedDeath(RoomDefinition room, RunningRuntimeState state, UUID playerId, int killerEntityId, long gameTick) {
        if (room == null || state == null || playerId == null) {
            return;
        }
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        state.getGameplayRuntime().ifPresent(runtime ->
            runtime.onPlayerInvalidated(room, playerId, teamId, org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason.DEATH, killerEntityId, gameTick)
        );
    }

    private boolean tryStartSelectedDeploy(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        long gameTick,
        boolean allowFallbackOnMissingSelection
    ) {
        UUID playerId = player.getUUID();
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (teamId == null) {
            return false;
        }
        String selectedKind = state.getDeploySelectionKinds().get(playerId);
        String selectedTargetId = state.getDeploySelectionTargetIds().get(playerId);
        if ("vehicle".equalsIgnoreCase(selectedKind)) {
            return tryStartVehicleDeploy(room, state, player, teamId, selectedTargetId, gameTick);
        }
        String deployFallbackReason = deploySelectionService.describeSelectionFailure(
            room,
            state,
            playerId,
            teamId,
            selectedKind,
            selectedTargetId
        );
        boolean shouldResetSelectionToTeam = deployFallbackReason != null
            && ("teammate".equalsIgnoreCase(selectedKind) || "beacon".equalsIgnoreCase(selectedKind));
        RoomPosition spawn = deploySelectionService.resolveSelectedSpawn(
            room,
            state,
            player,
            teamId,
            selectedKind,
            selectedTargetId,
            allowFallbackOnMissingSelection
        );
        if (spawn == null) {
            if (shouldResetSelectionToTeam) {
                if (deploySelectionService.hasTeamFallbackSpawn(room, state, player, teamId)) {
                    deploySelectionService.replaceSelectionWithTeamFallback(state, playerId, teamId);
                } else {
                    deploySelectionService.clearSelection(state, playerId);
                }
            }
            showDeployNotice(player, deployFallbackReason);
            return false;
        }
        if (shouldResetSelectionToTeam) {
            deploySelectionService.replaceSelectionWithTeamFallback(state, playerId, teamId);
            showDeployNotice(player, deployFallbackReason + "，已回退到阵营出生点");
        }
        applyTeammateDeployBonusWindow(state, playerId, teamId, selectedKind, selectedTargetId, gameTick);
        state.getDeployReadyTicks().remove(playerId);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().remove(playerId);
        state.getDeployExplicitConfirmRequiredPlayers().remove(playerId);
        state.getDeployViewActivePlayers().remove(playerId);
        state.getRespawnTargetPositions().put(playerId, spawn);
        state.getRespawnFinalizeTicks().put(playerId, gameTick + RESPAWN_TRAVEL_TICKS);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_WAIT);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_HINT);
        playerStateService.setSpectator(player);
        playerLaunchService.teleportToRoomPosition(player, room, spawn);
        Float transitionYRot = state.getRespawnTargetYRot().get(playerId);
        Float transitionXRot = state.getRespawnTargetXRot().get(playerId);
        NetworkManager.sendToPlayer(
            player,
            RoomDeathFlowPacket.startRespawnTransition(
                spawn.x() + 0.5D,
                spawn.y() + player.getEyeHeight(),
                spawn.z() + 0.5D,
                transitionYRot != null ? transitionYRot : player.getYRot(),
                transitionXRot != null ? transitionXRot : 0.0F,
                RESPAWN_TRAVEL_TICKS
            )
        );
        return true;
    }

    private boolean tryStartVehicleDeploy(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        String entryId,
        long gameTick
    ) {
        UUID playerId = player.getUUID();
        VehicleDeployResolution resolution = deploySelectionService.resolveVehicleSelection(room, state, playerId, teamId, entryId);
        if (!resolution.success()) {
            showDeployNotice(player, resolution.message());
            return false;
        }
        RoomPosition transitionPosition = vehicleDeployExecutionService.resolveTransitionPosition(room, resolution);
        if (transitionPosition == null) {
            showDeployNotice(player, "未能解析载具部署过渡位置");
            return false;
        }
        state.getDeployReadyTicks().remove(playerId);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().remove(playerId);
        state.getDeployExplicitConfirmRequiredPlayers().remove(playerId);
        state.getDeployViewActivePlayers().remove(playerId);
        state.getRespawnTargetPositions().put(playerId, transitionPosition);
        state.getPendingVehicleDeployResolutions().put(playerId, resolution);
        state.getRespawnFinalizeTicks().put(playerId, gameTick + RESPAWN_TRAVEL_TICKS);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_WAIT);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DEPLOY_HINT);
        playerStateService.setSpectator(player);
        playerLaunchService.teleportToRoomPosition(player, room, transitionPosition);
        float transitionYaw = vehicleDeployExecutionService.resolveTransitionYaw(room, resolution, player);
        NetworkManager.sendToPlayer(
            player,
            RoomDeathFlowPacket.startRespawnTransition(
                transitionPosition.x() + 0.5D,
                transitionPosition.y() + player.getEyeHeight(),
                transitionPosition.z() + 0.5D,
                transitionYaw,
                0.0F,
                RESPAWN_TRAVEL_TICKS
            )
        );
        return true;
    }

    private boolean equalsDeploySelection(String leftKind, String leftTargetId, String rightKind, String rightTargetId) {
        return Objects.equals(normalizeDeploySelectionToken(leftKind), normalizeDeploySelectionToken(rightKind))
            && Objects.equals(normalizeDeploySelectionToken(leftTargetId), normalizeDeploySelectionToken(rightTargetId));
    }

    private String normalizeDeploySelectionToken(String value) {
        return value == null ? "" : value.trim();
    }

    private void enterDeployHoldingState(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, boolean preserveWorldPosition) {
        if (player == null) {
            return;
        }
        if (!preserveWorldPosition) {
            applyRespawnPostProcess(room, state, player);
            return;
        }
        playerStateService.applyDeployHoldingState(player);
        applyDeployHoldingSelectedHotbarSlot(player);
    }

    private void sendDownedStart(ServerPlayer player, RunningRuntimeState state, RoomPosition deathPosition, int killerEntityId, long remainTicks) {
        if (player == null || deathPosition == null) {
            return;
        }
        NetworkManager.sendToPlayer(
            player,
            RoomDownedStatePacket.start(
                deathPosition.x(),
                deathPosition.y(),
                deathPosition.z(),
                killerEntityId,
                (int) Math.max(0L, remainTicks),
                buildNearbyRescuerEntries(state, player)
            )
        );
    }

    private void sendDownedUpdate(ServerPlayer player, RunningRuntimeState state, int remainTicks) {
        if (player == null) {
            return;
        }
        NetworkManager.sendToPlayer(
            player,
            RoomDownedStatePacket.update(remainTicks, buildNearbyRescuerEntries(state, player))
        );
    }

    private void broadcastPlayerAnimatorState(RunningRuntimeState state, UUID playerId, int durationMs, boolean active) {
        if (state == null || playerId == null) {
            return;
        }
        PlayerAnimatorDebugPacket packet = active
            ? PlayerAnimatorDebugPacket.start(playerId, durationMs)
            : PlayerAnimatorDebugPacket.stop(playerId);
        for (UUID viewerId : state.getRoomPlayers()) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer != null) {
                NetworkManager.sendToPlayer(viewer, packet);
            }
        }
    }

    private void broadcastPlayerAnimatorRefresh(RunningRuntimeState state, UUID playerId, long remainTicks) {
        if (remainTicks <= 0L) {
            return;
        }
        long refreshTicks = Math.max(20L, remainTicks + DOWNED_ANIMATOR_REFRESH_BUFFER_TICKS);
        broadcastPlayerAnimatorState(state, playerId, (int) Math.max(1000L, refreshTicks * 50L), true);
    }

    private List<RoomDownedStatePacket.RescuerEntry> buildNearbyRescuerEntries(RunningRuntimeState state, ServerPlayer downedPlayer) {
        if (state == null || downedPlayer == null) {
            return List.of();
        }
        UUID selfId = downedPlayer.getUUID();
        RoomTeamId teamId = state.getRoomTeams().get(selfId);
        String selfSquad = state.getSquadLabelsByPlayer().get(selfId);
        if (teamId == null) {
            return List.of();
        }
        record Candidate(ServerPlayer player, double distanceSqr, boolean squadMate, boolean assault) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (UUID teammateId : state.getRoomPlayers()) {
            if (teammateId == null || teammateId.equals(selfId) || state.getRoomTeams().get(teammateId) != teamId || state.isInRespawnFlow(teammateId)) {
                continue;
            }
            ServerPlayer teammate = server.getPlayerList().getPlayer(teammateId);
            if (teammate == null || !teammate.isAlive() || teammate.isSpectator()) {
                continue;
            }
            double distanceSqr = DownedRagdollBridge.distanceSquared(teammate, downedPlayer);
            if (distanceSqr > 30.0D * 30.0D) {
                continue;
            }
            boolean squadMate = isSameSquad(selfSquad, state.getSquadLabelsByPlayer().get(teammateId));
            boolean assault = resolvePlayerClassType(teammate) == PlayerClassType.ASSAULT;
            candidates.add(new Candidate(teammate, distanceSqr, squadMate, assault));
        }
        candidates.sort(
            Comparator.<Candidate>comparingInt(candidate -> candidate.assault() ? 0 : 1)
                .thenComparingInt(candidate -> candidate.squadMate() ? 0 : 1)
                .thenComparingDouble(Candidate::distanceSqr)
                .thenComparing(candidate -> candidate.player().getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER)
        );
        List<RoomDownedStatePacket.RescuerEntry> entries = new ArrayList<>();
        for (Candidate candidate : candidates) {
            entries.add(new RoomDownedStatePacket.RescuerEntry(
                candidate.player().getGameProfile().getName(),
                Math.max(1, (int) Math.round(Math.sqrt(candidate.distanceSqr()))),
                candidate.squadMate()
            ));
            if (entries.size() >= 4) {
                break;
            }
        }
        return entries;
    }

    private PlayerClassType resolvePlayerClassType(ServerPlayer player) {
        if (player == null || player.server == null) {
            return PlayerClassType.defaultType();
        }
        RunningRuntimeState state = RoomCoreRuntimeManager.of(player.server)
            .findRunningState(RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).orElse(""))
            .orElse(null);
        return CustomLoadoutService.resolveCurrentClass(
            ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID()),
            state,
            player.getUUID()
        );
    }

    private DownedRescueHint resolveNearestDownedRescueHint(RunningRuntimeState state, ServerPlayer viewer) {
        if (state == null || viewer == null) {
            return null;
        }
        UUID viewerId = viewer.getUUID();
        RoomTeamId viewerTeam = state.getRoomTeams().get(viewerId);
        if (viewerTeam == null) {
            return null;
        }
        String viewerSquad = state.getSquadLabelsByPlayer().get(viewerId);
        boolean viewerIsAssault = resolvePlayerClassType(viewer) == PlayerClassType.ASSAULT;
        ServerPlayer bestTarget = null;
        boolean bestSquadMate = false;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (UUID targetId : state.getDownedExpireTicks().keySet()) {
            if (targetId == null || targetId.equals(viewerId) || state.getRoomTeams().get(targetId) != viewerTeam) {
                continue;
            }
            if (state.isPlayerBeingRevived(targetId)) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target == null || !target.isAlive() || target.isSpectator()) {
                continue;
            }
            double distanceSqr = DownedRagdollBridge.distanceSquared(viewer, target);
            if (distanceSqr > DOWNED_RESCUE_HINT_DISTANCE_SQR || distanceSqr >= bestDistanceSqr) {
                continue;
            }
            bestTarget = target;
            bestSquadMate = isSameSquad(viewerSquad, state.getSquadLabelsByPlayer().get(targetId));
            bestDistanceSqr = distanceSqr;
        }
        if (bestTarget == null) {
            return null;
        }
        boolean canRevive = viewerIsAssault || bestSquadMate;
        String prompt = canRevive
            ? "§6长按[F]急救"
            : "§7需要 §6突击 §7兵种得以救援";
        return new DownedRescueHint(bestTarget.getUUID(), bestTarget.getScoreboardName(), prompt, canRevive, viewerIsAssault);
    }

    private void clearDownedRescueHint(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED_RESCUE_TARGET);
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_DOWNED_RESCUE_PROMPT);
    }

    private void showReviveProgress(ServerPlayer reviver, int remainTicks) {
        if (reviver == null) {
            return;
        }
        String remainSecondsText = String.format(Locale.ROOT, "%.1f", Math.max(0.0D, remainTicks / 20.0D));
        ServerActionBarService.showComponent(
            reviver,
            ACTIONBAR_SOURCE_DOWNED_RESCUING,
            ServerActionBarService.Templates.subjectAction("正在救援 ", ChatFormatting.GRAY, remainSecondsText, ChatFormatting.RED, "", ChatFormatting.GRAY),
            6
        );
    }

    private void showBeingRevived(ServerPlayer target, ServerPlayer reviver) {
        if (target == null || reviver == null) {
            return;
        }
        ServerActionBarService.showComponent(
            target,
            ACTIONBAR_SOURCE_DOWNED_BEING_RESCUED,
            ServerActionBarService.Templates.subjectAction("正在被 ", ChatFormatting.GRAY, reviver.getScoreboardName(), ChatFormatting.RED, " 救援", ChatFormatting.GRAY),
            6
        );
    }

    private void clearRevivePresentation(ServerPlayer reviver, ServerPlayer target) {
        if (reviver != null) {
            ServerActionBarService.clearSource(reviver, ACTIONBAR_SOURCE_DOWNED_RESCUING);
        }
        if (target != null) {
            ServerActionBarService.clearSource(target, ACTIONBAR_SOURCE_DOWNED_BEING_RESCUED);
        }
    }

    private void releaseForcedReviveCrouch(ServerPlayer reviver) {
        if (reviver == null) {
            return;
        }
        reviver.setShiftKeyDown(false);
        reviver.setForcedPose(null);
        reviver.refreshDimensions();
    }

    private void completeRevive(RoomDefinition room, RunningRuntimeState state, ServerPlayer reviver, ServerPlayer target, long gameTick) {
        if (room == null || state == null || reviver == null || target == null) {
            return;
        }
        UUID targetId = target.getUUID();
        boolean revivedByAssault = resolvePlayerClassType(reviver) == PlayerClassType.ASSAULT;
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.prepareRevive(target);
        DownedRagdollBridge.stop(target, true);
        clearRevivePresentation(reviver, target);
        releaseForcedReviveCrouch(reviver);
        state.clearReviveState(targetId);
        state.clearRespawnFlow(targetId);
        PlayerAnimatorDebugDimensionsState.stopPreview(target);
        playerStateService.applyRevivedState(room, target, gameTick, revivedByAssault);
        if (state.getHeroKitLoadoutResetPendingPlayers().remove(targetId)) {
            applyRespawnLoadout(room, state, target);
        }
        target.setDeltaMovement(0.0D, 0.0D, 0.0D);
        boolean revivedCuePlayed = FrontlineVoiceBridge.playCue(target, "REVIVED");
        if (!revivedCuePlayed) {
            NetworkManager.sendToPlayer(target, ClientShortSoundPacket.oneShot("gd656conquest:sfx.revive_first_person", 1.0F, 1.0F));
        }
        NetworkManager.sendToPlayer(target, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(target, RoomDeathFlowPacket.clear());
        ServerActionBarService.clearSource(target, ACTIONBAR_SOURCE_DOWNED);
        
        if (revivedByAssault) {
            FrontlineVoiceBridge.playCue(reviver, "REVIVE_ASSAULT_FINISH");
        } else {
            FrontlineVoiceBridge.playCue(reviver, "REVIVE_NORMAL_FINISH");
        }
        if (!revivedCuePlayed) {
            audioRuntimeService.emitPlayerThirdPersonCue(room, state, target, RoomThirdPersonAudioCueRegistry.PLAYER_REVIVED);
        }
        broadcastPlayerAnimatorState(state, targetId, 0, false);
        org.mods.gd656conquest.server.compat.ragdoll.DownedViewService.completeRevive(target);
        KilliconScoreboardBridge.tryAddReviveStat(reviver, 1);
        KilliconScoreboardBridge.tryAwardConquestBonus(reviver, "REVIVE", 1.0F, "");
    }

    private void applyReviveBlindEffect(ServerPlayer target) {
        if (target == null) {
            return;
        }
        MobEffect blindEffect = ForgeRegistries.MOB_EFFECTS.getValue(LRTACTICAL_BLIND_ID);
        if (blindEffect == null) {
            return;
        }
        target.addEffect(new MobEffectInstance(blindEffect, REVIVE_LRTACTICAL_BLIND_TICKS, 0, false, false));
    }

    private void applyTeammateDeployBonusWindow(
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String selectedKind,
        String selectedTargetId,
        long gameTick
    ) {
        if (state == null || playerId == null) {
            return;
        }
        state.getTeammateDeployKillWindowUntilTicks().remove(playerId);
        if (!"teammate".equalsIgnoreCase(selectedKind) || selectedTargetId == null || selectedTargetId.isBlank()) {
            return;
        }
        UUID anchorPlayerId;
        try {
            anchorPlayerId = UUID.fromString(selectedTargetId.trim());
        } catch (Exception ignored) {
            return;
        }
        if (anchorPlayerId.equals(playerId) || state.getRoomTeams().get(anchorPlayerId) != teamId) {
            return;
        }
        if (!isSameSquad(state.getSquadLabelsByPlayer().get(playerId), state.getSquadLabelsByPlayer().get(anchorPlayerId))) {
            return;
        }
        state.getTeammateDeployKillWindowUntilTicks().put(playerId, gameTick + TEAMMATE_DEPLOY_KILL_WINDOW_TICKS);
        ServerPlayer anchorPlayer = server.getPlayerList().getPlayer(anchorPlayerId);
        if (anchorPlayer != null) {
            KilliconScoreboardBridge.tryAwardConquestBonus(anchorPlayer, "SQUAD_DEPLOY_ON_YOU", 1.0F, "");
        }
    }

    private boolean isSameSquad(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equalsIgnoreCase(right);
    }

    private boolean finalizeRespawn(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        UUID playerId = player.getUUID();
        boolean deathmatchRespawn = isArenaGameplay(room) && state.getDeathPositions().containsKey(playerId);
        PlayerAnimatorDebugDimensionsState.clearPlayer(player);
        RoomPosition spawn = state.getRespawnTargetPositions().get(playerId);
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (spawn == null || teamId == null) {
            playerStateService.setSpectator(player);
            return true;
        }
        VehicleDeployResolution pendingVehicleDeploy = state.getPendingVehicleDeployResolutions().get(playerId);
        Float targetYRot = state.getRespawnTargetYRot().remove(playerId);
        Float targetXRot = state.getRespawnTargetXRot().remove(playerId);
        net.minecraft.world.entity.Pose targetPose = state.getRespawnTargetPose().remove(playerId);
        if (targetYRot != null) {
            player.setYRot(targetYRot);
            player.setYBodyRot(targetYRot);
            player.setYHeadRot(targetYRot);
        }
        player.setXRot(targetXRot != null ? targetXRot : 0.0F);
        if (targetPose != null) {
            player.setPose(targetPose);
        }
        playerLaunchService.teleportToRoomPosition(player, room, spawn);
        clearVanillaPotionEffects(player);
        HeroKitSupportService.endHeroKitState(server, state, playerId, gameTick);
        state.getHeroKitLoadoutResetPendingPlayers().remove(playerId);
        applyRespawnLoadout(room, state, player);
        if (pendingVehicleDeploy != null) {
            playerStateService.applyRespawnState(room, playerId, gameTick);
            VehicleDeployExecutionResult executionResult = vehicleDeployExecutionService.execute(room, state, player, pendingVehicleDeploy, gameTick);
            if (executionResult.retry()) {
                state.getRespawnFinalizeTicks().put(playerId, gameTick + 1L);
                return false;
            }
            if (!executionResult.success()) {
                return returnPlayerToDeployAfterVehicleFailure(room, state, player, gameTick, executionResult.message());
            }
            applyPostDeploySelectedHotbarSlot(room, player, true);
            if (deathmatchRespawn) {
                applyReviveBlindEffect(player);
            }
            if (state.getInitialMovementUnlockTick() > gameTick) {
                markInitialMovementLocked(state, playerId);
                NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
                return true;
            }
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
            return true;
        }
        playerStateService.applyRespawnState(room, playerId, gameTick);
        applyPostDeploySelectedHotbarSlot(room, player, false);
        if (deathmatchRespawn) {
            applyReviveBlindEffect(player);
        }
        if (state.getInitialMovementUnlockTick() > gameTick) {
            markInitialMovementLocked(state, playerId);
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
            return true;
        }
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        return true;
    }

    private boolean returnPlayerToDeployAfterVehicleFailure(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        long gameTick,
        String failureMessage
    ) {
        if (room == null || state == null || player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        state.getRespawnFinalizeTicks().remove(playerId);
        state.getRespawnTargetPositions().remove(playerId);
        state.getPendingVehicleDeployResolutions().remove(playerId);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().remove(playerId);
        state.getDeployReadyTicks().put(playerId, gameTick);
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        enterDeployHoldingState(room, state, player, false);
        openDeployView(room, state, player, new RoomGameplayDeployViewRequest(
            RoomGameplayDeployViewKind.RESPAWN,
            gameTick,
            0,
            false
        ));
        showDeployNotice(player, failureMessage);
        return false;
    }

    // #endregion

    private void syncInitialMovementLock(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (state == null) {
            return;
        }
        long unlockTick = state.getInitialMovementUnlockTick();
        if (unlockTick <= 0L) {
            return;
        }
        int remainingSeconds = (int) Math.max(0L, (unlockTick - gameTick + 19L) / 20L);
        if (remainingSeconds > 0) {
            for (UUID playerId : new ArrayList<>(state.getInitialMovementLockedPlayers())) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive()) {
                    state.getInitialMovementLockLastShownSeconds().remove(playerId);
                    continue;
                }
                int lastShown = state.getInitialMovementLockLastShownSeconds().getOrDefault(playerId, -1);
                if (lastShown != remainingSeconds) {
                    sendInitialCombatCountdownTitle(room, player, state.getRoomTeams().get(playerId), remainingSeconds, lastShown < 0);
                    state.getInitialMovementLockLastShownSeconds().put(playerId, remainingSeconds);
                }
            }
            return;
        }
        for (UUID playerId : new ArrayList<>(state.getInitialMovementLockedPlayers())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.isAlive()) {
                clearInitialCombatCountdownTitle(player);
                audioRuntimeService.emitPlayerThirdPersonCue(room, state, player, RoomThirdPersonAudioCueRegistry.PLAYER_GOGOGO);
            }
            state.getInitialMovementLockLastShownSeconds().remove(playerId);
            state.getInitialMovementLockedPlayers().remove(playerId);
        }
        state.setInitialMovementUnlockTick(-1L);
    }

    private void markInitialMovementLocked(RunningRuntimeState state, UUID playerId) {
        if (state == null || playerId == null) {
            return;
        }
        state.getInitialMovementLockedPlayers().add(playerId);
        state.getInitialMovementLockLastShownSeconds().remove(playerId);
    }

    private boolean isArenaGameplay(RoomDefinition room) {
        return room != null && room.isArenaGameplay();
    }

    private void beginDeathmatchRespawnHold(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        long gameTick,
        int deployDelayTicks,
        boolean applyBlindEffect,
        boolean showRespawnWaitVisuals
    ) {
        if (room == null || state == null || player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        state.getDeployViewActivePlayers().remove(playerId);
        state.getRespawnCountdownStartTicks().remove(playerId);
        state.getDeployReadyTicks().put(playerId, gameTick + Math.max(0, deployDelayTicks));
        state.getDeployAutoStartOnReadyPlayers().add(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().remove(playerId);
        state.getDeployExplicitConfirmRequiredPlayers().remove(playerId);
        state.getRespawnCountdownLastShownSeconds().remove(playerId);
        RoomPosition lockedRespawnTarget = resolveLockedDeathmatchRespawnTarget(room, state, player);
        enterDeployHoldingState(room, state, player, false);
        if (applyBlindEffect) {
            applyReviveBlindEffect(player);
        }
        if (lockedRespawnTarget != null) {
            playerLaunchService.teleportToRoomPosition(player, room, lockedRespawnTarget);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
        if (showRespawnWaitVisuals) {
            RoomPosition holdingPosition = lockedRespawnTarget == null
                ? RoomGameplayDeployPresentationSupport.resolveRespawnHoldingPosition(room, state, player)
                : lockedRespawnTarget;
            if (holdingPosition != null) {
                int killerEntityId = state.getDeathKillerEntityIdOrDefault(playerId);
                NetworkManager.sendToPlayer(
                    player,
                    RoomDeathFlowPacket.openRespawnWaitInstant(
                        holdingPosition.x() + 0.5D,
                        holdingPosition.y() + player.getEyeHeight(),
                        holdingPosition.z() + 0.5D,
                        player.getYRot(),
                        0.0F,
                        Math.max(0, deployDelayTicks),
                        killerEntityId
                    )
                );
                return;
            }
        }
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
    }

    private boolean startDeathmatchRespawnTransition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        long gameTick,
        boolean instant
    ) {
        if (room == null || state == null || player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        RoomPosition spawn = resolveLockedDeathmatchRespawnTarget(room, state, player);
        if (spawn == null) {
            return false;
        }
        state.getDeployReadyTicks().remove(playerId);
        state.getDeployAutoStartOnReadyPlayers().remove(playerId);
        state.getDeployAllowFallbackOnMissingSelectionPlayers().remove(playerId);
        state.getDeployExplicitConfirmRequiredPlayers().remove(playerId);
        state.getDeployViewActivePlayers().remove(playerId);
        state.getRespawnCountdownLastShownSeconds().remove(playerId);
        state.getRespawnTargetPositions().put(playerId, spawn);
        
        if (instant) {
            playerLaunchService.teleportToRoomPosition(player, room, spawn);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (finalizeRespawn(room, state, player, gameTick)) {
                state.clearRespawnFlow(playerId);
                refreshVehicleLoadoutSnapshots(state);
            }
            return true;
        }

        int travelTicks = RESPAWN_TRAVEL_TICKS;
        state.getRespawnFinalizeTicks().put(playerId, gameTick + travelTicks);
        playerStateService.setSpectator(player);
        NetworkManager.sendToPlayer(
            player,
            RoomDeathFlowPacket.startRespawnTransition(
                spawn.x() + 0.5D,
                spawn.y() + player.getEyeHeight(),
                spawn.z() + 0.5D,
                player.getYRot(),
                0.0F,
                travelTicks
            )
        );
        return true;
    }

    private RoomPosition resolveLockedDeathmatchRespawnTarget(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        if (room == null || state == null || player == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        RoomPosition existing = state.getRespawnTargetPositions().get(playerId);
        if (existing != null) {
            return existing;
        }
        RoomPosition resolved = RoomGameplayDeployPresentationSupport.resolveRespawnAnchorPosition(room, state, player);
        if (resolved != null) {
            state.getRespawnTargetPositions().put(playerId, resolved);
        }
        return resolved;
    }

    private void applyPostDeploySelectedHotbarSlot(RoomDefinition room, ServerPlayer player, boolean vehicleDeploy) {
        if (player == null) {
            return;
        }
        int selectedSlot = vehicleDeploy
            ? POST_DEPLOY_VEHICLE_HOTBAR_SLOT
            : resolvePostDeployInfantryHotbarSlot(player);
        player.getInventory().selected = selectedSlot;
        if (player.connection != null) {
            player.connection.send(new ClientboundSetCarriedItemPacket(selectedSlot));
        }
    }

    private void applyRespawnLoadout(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (gunmasterLoadoutService.isGunmasterRoom(room)) {
            gunmasterLoadoutService.applySpawnLoadout(room, state, player);
            return;
        }
        loadoutService.applySpawnLoadout(state, player);
    }

    private int resolvePostDeployInfantryHotbarSlot(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        for (int slot = 0; slot < 9 && slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                return slot;
            }
        }
        return 0;
    }

    private void applyDeployHoldingSelectedHotbarSlot(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.getInventory().selected = DEPLOY_HOLDING_HOTBAR_SLOT;
        if (player.connection != null) {
            player.connection.send(new ClientboundSetCarriedItemPacket(DEPLOY_HOLDING_HOTBAR_SLOT));
        }
    }

    private void clearVanillaPotionEffects(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.removeAllEffects();
    }

    private void sendInitialCombatCountdownTitle(RoomDefinition room, ServerPlayer player, RoomTeamId teamId, int remainingSeconds, boolean firstShow) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
            firstShow ? TITLE_FADE_IN_TICKS : TITLE_REFRESH_FADE_IN_TICKS,
            firstShow ? TITLE_STAY_TICKS : TITLE_REFRESH_STAY_TICKS,
            firstShow ? TITLE_FADE_OUT_TICKS : TITLE_REFRESH_FADE_OUT_TICKS
        ));
        player.connection.send(new ClientboundSetTitleTextPacket(buildInitialCombatCountdownTitle(remainingSeconds)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(buildInitialCombatCountdownSubtitle(room, teamId)));
    }

    private void sendRespawnCountdownTitle(ServerPlayer player, int remainingSeconds, boolean firstShow) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
            firstShow ? TITLE_FADE_IN_TICKS : TITLE_REFRESH_FADE_IN_TICKS,
            firstShow ? TITLE_STAY_TICKS : TITLE_REFRESH_STAY_TICKS,
            firstShow ? TITLE_FADE_OUT_TICKS : TITLE_REFRESH_FADE_OUT_TICKS
        ));
        player.connection.send(new ClientboundSetTitleTextPacket(buildInitialCombatCountdownTitle(remainingSeconds)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("等待重新部署")
                .withStyle(style -> style.withColor(INITIAL_SUBTITLE_COLOR).withBold(true))
        ));
    }

    private Component buildInitialCombatCountdownTitle(int remainingSeconds) {
        return Component.literal(String.valueOf(remainingSeconds))
            .withStyle(style -> style.withBold(true).withColor(resolveInitialCountdownColor(remainingSeconds)));
    }

    private Component buildInitialCombatCountdownSubtitle(RoomDefinition room, RoomTeamId teamId) {
        if (room != null && RoomGameplayId.GUNMASTER.equals(room.gameplayId())) {
            return Component.literal("击杀敌军升级枪械")
                .withStyle(style -> style.withColor(INITIAL_SUBTITLE_COLOR).withBold(true));
        }
        if (isArenaGameplay(room)) {
            return Component.literal("击杀敌人并完成竞技模式目标")
                .withStyle(style -> style.withColor(INITIAL_SUBTITLE_COLOR).withBold(true));
        }
        boolean attacker = teamId == RoomTeamId.CAMP_A;
        MutableComponent subtitle = Component.literal(attacker ? "逐个占领全部据点" : "准备防守全部区域");
        return subtitle.withStyle(style -> style.withColor(INITIAL_SUBTITLE_COLOR).withBold(true));
    }

    private int resolveInitialCountdownColor(int remainingSeconds) {
        if (remainingSeconds <= 1) {
            return COUNTDOWN_RED_COLOR;
        }
        if (remainingSeconds == 2) {
            return COUNTDOWN_ORANGE_COLOR;
        }
        if (remainingSeconds == 3) {
            return COUNTDOWN_YELLOW_COLOR;
        }
        if (remainingSeconds <= 5) {
            return COUNTDOWN_GREEN_COLOR;
        }
        return COUNTDOWN_NORMAL_COLOR;
    }

    private void clearInitialCombatCountdownTitle(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundClearTitlesPacket(true));
    }

    private void openDeployView(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomGameplayDeployViewRequest request
    ) {
        if (player == null || request == null) {
            return;
        }
        RoomViewPosition view = RoomGameplayDeployPresentationSupport.resolveDeployViewPosition(room, state, player, request);
        if (view == null) {
            return;
        }
        if (state != null) {
            state.getDeployViewActivePlayers().add(player.getUUID());
        }
        long gameTick = request.gameTick() > 0L ? request.gameTick() : server.getTickCount();
        if (request.instantView()) {
            NetworkManager.sendToPlayer(
                player,
                RoomDeathFlowPacket.openDeployInstant(
                    view.x() + 0.5D,
                    view.y(),
                    view.z() + 0.5D,
                    view.yaw(),
                    view.pitch(),
                    request.deployDelayTicks()
                )
            );
        } else {
            NetworkManager.sendToPlayer(
                player,
                RoomDeathFlowPacket.openDeployFrom(
                    player.getX(),
                    player.getEyeY(),
                    player.getZ(),
                    view.x() + 0.5D,
                    view.y(),
                    view.z() + 0.5D,
                    view.yaw(),
                    view.pitch(),
                    request.deployDelayTicks()
                )
            );
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        playerTransferQueueService.enqueueDeployViewTeleport(player, room, view, gameTick);
    }

    private void showDeployCountdown(ServerPlayer player, int remainSeconds) {
        if (player == null || remainSeconds <= 0) {
            return;
        }
        ServerActionBarService.showText(
            player,
            ACTIONBAR_SOURCE_DEPLOY_WAIT,
            "§7将在 " + resolveCountdownColor(remainSeconds) + remainSeconds + "秒§7 后开放重新部署",
            30
        );
    }

    private void showDeployNotice(ServerPlayer player, String text) {
        if (player == null || text == null || text.isBlank()) {
            return;
        }
        ServerActionBarService.showText(player, ACTIONBAR_SOURCE_DEPLOY_NOTICE, "§6" + text, 50);
    }

    private void showDeathmatchDeployHint(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_DEPLOY_HINT,
            ServerActionBarService.builder()
                .text("按下", ChatFormatting.WHITE)
                .key("空格")
                .text(" 以进入部署界面修改配装", ChatFormatting.WHITE),
            30
        );
    }

    private void refreshVehicleLoadoutSnapshots(RunningRuntimeState state) {
        if (state == null) {
            return;
        }
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer roomPlayer = server.getPlayerList().getPlayer(playerId);
            if (roomPlayer != null) {
                LoadoutUiService.sendSnapshot(roomPlayer, false, true);
            }
        }
    }

    private void showDownedCountdown(ServerPlayer player, int remainSeconds) {
        if (player == null || remainSeconds <= 0) {
            return;
        }
        ServerActionBarService.showText(
            player,
            ACTIONBAR_SOURCE_DOWNED,
            "§4你将在 §c" + remainSeconds + " §4秒后彻底死亡",
            30
        );
    }

    private String resolveCountdownColor(int remainSeconds) {
        if (remainSeconds <= 1) {
            return "§c";
        }
        if (remainSeconds <= 2) {
            return "§6";
        }
        if (remainSeconds <= 3) {
            return "§e";
        }
        if (remainSeconds <= 5) {
            return "§a";
        }
        return "§b";
    }

    private int resolveDeployDelayTicks(RoomDefinition room, RunningRuntimeState state, int multiplier) {
        RoomGameplayDeployPresentationProvider provider = RoomGameplayDeployPresentationProvider.resolve(state);
        Integer overrideSeconds = provider == null ? null : provider.resolveRespawnDelaySeconds(room, state);
        int seconds = overrideSeconds != null
            ? Math.max(0, overrideSeconds)
            : room == null ? 1 : Math.max(1, room.gameplayRespawnDelaySeconds());
        return (int) (seconds * 20L) * Math.max(1, multiplier);
    }

    public static int initialCombatUnlockDelayTicks(RoomDefinition room) {
        if (room != null && room.isArenaGameplay()) {
            return INITIAL_DEPLOY_DELAY_SECONDS * 20 + RESPAWN_TRAVEL_TICKS + resolveInitialMovementLockTicks(room);
        }
        return initialCombatUnlockDelayTicks();
    }

    public static int initialCombatUnlockDelayTicks() {
        return INITIAL_DEPLOY_DELAY_SECONDS * 20 + RESPAWN_TRAVEL_TICKS + INITIAL_MOVEMENT_LOCK_SECONDS * 20;
    }

    private static int resolveInitialMovementLockTicks(RoomDefinition room) {
        if (room != null && room.isArenaGameplay()) {
            return Math.max(1, room.arenaConfig().startCountdownSeconds()) * 20;
        }
        return INITIAL_MOVEMENT_LOCK_SECONDS * 20;
    }

    private record DownedRescueHint(UUID targetId, String targetName, String prompt, boolean canRevive, boolean assault) {
    }

    private record ActiveReviveContext(ServerPlayer reviver, ServerPlayer target, boolean sameSquad, int requiredTicks, boolean forceCrouch) {
    }
}
