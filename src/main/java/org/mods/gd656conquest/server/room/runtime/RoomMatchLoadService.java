package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.player.lock.PlayerLockStrength;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomMatchLoadPacket;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages the "match-loading display" phase that every player goes through when entering a room.
 * <p>
 * Flow:
 * <ol>
 *   <li>Player enters room → teleported to {@code RoomCommonConfig#matchLoadViewPosition()}</li>
 *   <li>Client shows a 7-second loading overlay ({@code RoomMatchLoadPacket})</li>
 *   <li>After 7 seconds → determines the player's actual destination based on current room state:</li>
 *   <ul>
 *     <li>{@code MATCHING / IDLE} → queue spawn position + matching action bars</li>
 *     <li>{@code RUNNING (rejoin)} → reconnect deploy</li>
 *     <li>{@code RUNNING (mid-join)} → mid-join deploy (no auto-deploy if within last 10s)</li>
 *     <li>{@code ENDING} → result view sync</li>
 *   </ul>
 * </ol>
 * <p>
 * Edge cases:
 * <ul>
 *   <li>If &lt;5s remain until state transition (matching→deploy, deploy→forced, ending),
 *       the destination is determined after the 5s loading ends, so the player always
 *       routes to the correct post-load state.</li>
 *   <li>If a player enters within the last 10s before forced initial deploy,
 *       they are NOT auto-deployed after loading; they stay in manual deploy.</li>
 * </ul>
 */
public final class RoomMatchLoadService {

    public static final int MATCH_LOAD_DURATION_TICKS = 140; // 7 seconds
    private static final int INITIAL_DEPLOY_GRACE_TICKS = 10 * 20; // 10 seconds
    private static final int RESPAWN_TRAVEL_TICKS = 40;
    private static final int INITIAL_MOVEMENT_LOCK_TICKS = 5 * 20; // 100 ticks

    private final MinecraftServer server;
    private final RoomPlayerLaunchService playerLaunchService;
    private final RoomPlayerStateService playerStateService;
    private final RoomPlayerRespawnService playerRespawnService;
    private final RoomResultRuntimeService resultRuntimeService;

    private final Map<UUID, PendingLoadEntry> pending = new LinkedHashMap<>();

    /**
     * Describes how the player entered the room.
     */
    public enum EntryKind {
        /** Normal matchmaking join (room in MATCHING/IDLE state). */
        MATCHING,
        /** Rejoin with a valid rejoin ticket. */
        REJOIN,
        /** Mid-join without a rejoin ticket. */
        MIDJOIN
    }

    private record PendingLoadEntry(
        String roomId,
        RoomDefinition room,
        long endTick,
        EntryKind kind,
        RoomTeamId teamId,
        boolean wasInForceDeployWindow
    ) {
    }

    public RoomMatchLoadService(
        MinecraftServer server,
        RoomPlayerLaunchService playerLaunchService,
        RoomPlayerStateService playerStateService,
        RoomPlayerRespawnService playerRespawnService,
        RoomResultRuntimeService resultRuntimeService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerLaunchService = Objects.requireNonNull(playerLaunchService, "playerLaunchService");
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
        this.playerRespawnService = Objects.requireNonNull(playerRespawnService, "playerRespawnService");
        this.resultRuntimeService = Objects.requireNonNull(resultRuntimeService, "resultRuntimeService");
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Whether this player is currently in the match-loading phase. */
    public boolean isLoading(UUID playerId) {
        return pending.containsKey(playerId);
    }

    /**
     * Cancels an in-flight match-loading phase without routing the player back into the room.
     * Used when the player leaves the room before the loading countdown completes.
     */
    public void cancel(UUID playerId, long gameTick) {
        if (playerId == null) {
            return;
        }
        PendingLoadEntry removed = pending.remove(playerId);
        if (removed == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        NetworkManager.sendToPlayer(
            player,
            new RoomMatchLoadPacket(false, 0, "", "", "", "", "", "", "")
        );
        ServerPlayerLockService.of(server).applyDebugLock(player, PlayerLockStrength.NONE, gameTick);
    }

    /**
     * Whether the match-loading phase should be used for this room.
     * Returns {@code true} only if the room has a configured {@code matchLoadViewPosition}.
     */
    public static boolean isEnabled(RoomDefinition room) {
        return room != null && room.commonConfig().matchLoadViewPosition() != null;
    }

    /**
     * Resolves whether the player entered while the initial deploy force-deploy window
     * is active (i.e., within the last 10 seconds before forced deploy).
     * <p>
     * If true, after loading the player should NOT be auto-deployed.
     */
    public static boolean isInForceDeployWindow(RunningRuntimeState runningState, long gameTick) {
        if (runningState == null || runningState.getInitialMovementUnlockTick() <= 0L) {
            return false;
        }
        long openingReadyTick = runningState.getInitialMovementUnlockTick()
            - RESPAWN_TRAVEL_TICKS
            - INITIAL_MOVEMENT_LOCK_TICKS;
        if (openingReadyTick <= 0L) {
            return false;
        }
        return (openingReadyTick - gameTick) <= INITIAL_DEPLOY_GRACE_TICKS
            && gameTick < openingReadyTick;
    }

    /**
     * Begin the match-loading sequence for a player entering a room.
     *
     * @return {@code true} if match loading was started, {@code false} if skipped (no config)
     */
    public boolean beginMatchLoad(
        ServerPlayer player,
        RoomDefinition room,
        RunningRuntimeState runningState,
        long gameTick,
        EntryKind kind,
        RoomTeamId teamId
    ) {
        if (player == null || room == null) {
            return false;
        }
        RoomViewPosition loadView = room.commonConfig().matchLoadViewPosition();
        if (loadView == null) {
            return false;
        }

        // Teleport to loading view position
        playerLaunchService.teleportToViewPosition(player, room, loadView);
        playerStateService.setSpectator(player);
        player.setInvulnerable(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);

        // Apply lock level 4 (UI_CURSOR) for the entire loading phase
        ServerPlayerLockService.of(server).applyDebugLock(player, PlayerLockStrength.UI_CURSOR, gameTick);

        // Send loading overlay and subtitle copy to client
        NetworkManager.sendToPlayer(
            player,
            new RoomMatchLoadPacket(
                true,
                MATCH_LOAD_DURATION_TICKS,
                room.displayName(),
                resolveMapName(room),
                room.commonConfig().dimensionId(),
                room.commonConfig().roomDescription(),
                resolveTipLine(room, teamId, 0),
                resolveTipLine(room, teamId, 1),
                resolveTipLine(room, teamId, 2)
            )
        );

        // Determine if player entered during the force-deploy grace window
        boolean inForceDeployWindow = false;
        if (kind == EntryKind.MIDJOIN && runningState != null) {
            inForceDeployWindow = isInForceDeployWindow(runningState, gameTick);
        }

        pending.put(player.getUUID(), new PendingLoadEntry(
            room.roomId(),
            room,
            gameTick + MATCH_LOAD_DURATION_TICKS,
            kind,
            teamId,
            inForceDeployWindow
        ));

        return true;
    }

    /** Called every server tick to process completed match loads. */
    public void tick(long gameTick) {
        if (pending.isEmpty()) {
            return;
        }
        var toRemove = pending.entrySet().stream()
            .filter(e -> gameTick >= e.getValue().endTick)
            .map(Map.Entry::getKey)
            .toList();
        for (UUID id : toRemove) {
            PendingLoadEntry entry = pending.remove(id);
            if (entry == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            completeMatchLoad(player, entry, gameTick);
        }
    }

    // -----------------------------------------------------------------------
    // Completion logic
    // -----------------------------------------------------------------------

    private void completeMatchLoad(ServerPlayer player, PendingLoadEntry entry, long gameTick) {
        // Stop loading overlay on client (triggers 1-second post-fade)
        NetworkManager.sendToPlayer(
            player,
            new RoomMatchLoadPacket(false, 0, "", "", "", "", "", "", "")
        );

        // Release match-load lock level 4
        ServerPlayerLockService.of(server).applyDebugLock(player, PlayerLockStrength.NONE, gameTick);
        player.setInvulnerable(false);
        playerStateService.clearPlayerState(player.getUUID());

        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(server);
        RoomDefinition currentRoom = runtimeManager.findRoom(entry.roomId).orElse(entry.room);
        if (!isStillAssignedToRoom(runtimeManager, entry, player.getUUID())) {
            return;
        }
        RoomLifecycleState lifecycle = currentRoom.lifecycleState();

        switch (lifecycle) {
            case IDLE, MATCHING -> {
                MatchingRuntimeState matchingState = runtimeManager.findMatchingState(entry.roomId).orElse(null);
                handleCompleteToMatching(player, currentRoom, entry, matchingState, gameTick);
            }
            case RUNNING -> {
                RunningRuntimeState runningState = runtimeManager.findRunningState(entry.roomId).orElse(null);
                handleCompleteToRunning(player, currentRoom, entry, runningState, gameTick);
            }
            case ENDING -> {
                RunningRuntimeState runningState = runtimeManager.findRunningState(entry.roomId).orElse(null);
                handleCompleteToEnding(player, currentRoom, entry, runningState, gameTick);
            }
            default -> playerLaunchService.teleportToQueueSpawn(player, currentRoom);
        }
    }

    private void handleCompleteToMatching(
        ServerPlayer player,
        RoomDefinition currentRoom,
        PendingLoadEntry entry,
        MatchingRuntimeState matchingState,
        long gameTick
    ) {
        playerLaunchService.teleportToQueueSpawn(player, currentRoom);
        playerStateService.applyMatchingState(currentRoom, player);
        if (matchingState != null && matchingState.getQueuePlayers().contains(player.getUUID())) {
            RoomMatchingActionBarService.showQueueStatus(
                player, currentRoom, currentRoom.commonConfig().matchingConfig(), matchingState);
            RoomMatchingActionBarService.showWaitingStatus(player);
        }
    }

    private void handleCompleteToRunning(
        ServerPlayer player,
        RoomDefinition currentRoom,
        PendingLoadEntry entry,
        RunningRuntimeState runningState,
        long gameTick
    ) {
        if (runningState == null) {
            playerLaunchService.teleportToQueueSpawn(player, currentRoom);
            return;
        }
        // Ensure death flow is clean before entering deploy
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());

        // If the player entered within the last 10s of the initial force-deploy window,
        // use reconnect-style deploy (no auto-deploy; stays in manual deploy screen).
        boolean forceDeployWindow = entry.wasInForceDeployWindow
            || isInForceDeployWindow(runningState, gameTick);

        switch (entry.kind) {
            case REJOIN -> playerRespawnService.beginReconnectDeploy(
                currentRoom, runningState, player, gameTick);
            case MIDJOIN -> {
                if (forceDeployWindow) {
                    playerRespawnService.beginReconnectDeploy(
                        currentRoom, runningState, player, gameTick);
                } else {
                    playerRespawnService.beginMidJoinDeploy(
                        currentRoom, runningState, player, gameTick);
                }
            }
            case MATCHING -> {
                if (runningState.getRoomPlayers().contains(player.getUUID())) {
                    playerRespawnService.resumeDeferredInitialDeploy(
                        currentRoom, runningState, player, gameTick);
                    return;
                }
                if (forceDeployWindow) {
                    playerRespawnService.beginReconnectDeploy(
                        currentRoom, runningState, player, gameTick);
                } else {
                    playerRespawnService.beginMidJoinDeploy(
                        currentRoom, runningState, player, gameTick);
                }
            }
        }
    }

    private void handleCompleteToEnding(
        ServerPlayer player,
        RoomDefinition currentRoom,
        PendingLoadEntry entry,
        RunningRuntimeState runningState,
        long gameTick
    ) {
        if (runningState == null) {
            playerLaunchService.teleportToQueueSpawn(player, currentRoom);
            return;
        }
        if (entry.kind == EntryKind.REJOIN) {
            resultRuntimeService.syncRejoinedPlayer(currentRoom, runningState, player, gameTick);
        } else {
            resultRuntimeService.syncLateJoinedPlayer(currentRoom, runningState, player, gameTick);
        }
    }

    private String resolveMapName(RoomDefinition room) {
        if (room == null) {
            return "";
        }
        String configured = room.commonConfig().mapName();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String fallback = room.gameplayMapId();
        return fallback == null ? "" : fallback;
    }

    private String resolveTipLine(RoomDefinition room, RoomTeamId teamId, int lineIndex) {
        if (room == null || lineIndex < 0 || lineIndex > 2) {
            return "";
        }
        if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            boolean attacker = RoomTeamId.CAMP_A.equals(teamId);
            String[] lines = attacker
                ? new String[] {
                    "依次占领各个区域的所有据点推进战线",
                    "每次死亡会扣除我方的兵力",
                    "在兵力耗尽前夺下最终据点"
                }
                : new String[] {
                    "利用地形死守各个区域",
                    "每次击杀敌军可降低敌军的兵力",
                    "耗尽敌方兵力夺得胜利"
                };
            return lines[lineIndex];
        }
        if (RoomGameplayId.RUSH.equals(room.gameplayId())) {
            boolean attacker = RoomTeamId.CAMP_A.equals(teamId);
            String[] lines = attacker
                ? new String[] {
                    "依次摧毁各个区域的通信基站",
                    "每次死亡会扣除我方的兵力",
                    "在兵力耗尽前摧毁所有基站"
                }
                : new String[] {
                    "死守各个区域的通信基站",
                    "每次击杀敌军可降低敌军的兵力",
                    "耗尽敌方兵力夺得胜利"
                };
            return lines[lineIndex];
        }
        if (RoomGameplayId.DEATHMATCH.equals(room.gameplayId())) {
            return switch (lineIndex) {
                case 0 -> "获得枪械击杀敌军";
                case 1 -> "每击杀敌军可为我方获得分数";
                default -> "分数率先达到目标值即为胜利";
            };
        }
        if (RoomGameplayId.GUNMASTER.equals(room.gameplayId())) {
            return switch (lineIndex) {
                case 0 -> "使用手中的枪械击杀敌军";
                case 1 -> "使用某武器达到目标累计击杀数可升级武器";
                default -> "率先使用最终武器击杀敌军的一方胜利";
            };
        }
        return switch (lineIndex) {
            case 0 -> "占领多数的目标为我军夺得更多的分数";
            case 1 -> "每次击杀均可为我军加分";
            default -> "率先达到目标兵力值赢得胜利";
        };
    }

    private boolean isStillAssignedToRoom(RoomCoreRuntimeManager runtimeManager, PendingLoadEntry entry, UUID playerId) {
        if (runtimeManager == null || entry == null || playerId == null) {
            return false;
        }
        if (runtimeManager.findMatchingState(entry.roomId)
            .map(state -> state.getQueuePlayers().contains(playerId))
            .orElse(false)) {
            return true;
        }
        return runtimeManager.findRunningState(entry.roomId)
            .map(state -> state.getRoomPlayers().contains(playerId))
            .orElse(false);
    }
}
