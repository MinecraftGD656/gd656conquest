package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomHudSubtitlePacket;
import org.mods.gd656conquest.network.packet.RoomHudSyncPacket;
import org.mods.gd656conquest.network.packet.RoomTeamMirrorSyncPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RoomHudSyncService {
    private final MinecraftServer server;
    private final java.util.List<RoomRunningHudRenderer> runningHudRenderers;
    private final java.util.Map<UUID, String> lastTeamMirrorSignatureByPlayer = new java.util.HashMap<>();

    public RoomHudSyncService(MinecraftServer server, RoomSquadBeaconService squadBeaconService) {
        this.server = Objects.requireNonNull(server, "server");
        this.runningHudRenderers = java.util.List.of(
            new StandardRoomRunningHudRenderer(server, Objects.requireNonNull(squadBeaconService, "squadBeaconService"))
        );
    }

    public void syncMatchingRoom(RoomDefinition room, RoomRuntimeState state, long gameTick) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        TeamMirrorSnapshot mirrorSnapshot = buildMatchingTeamMirrorPacket(room, state);

        for (UUID playerId : state.getMatchingState().getQueuePlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomTeamId selfTeam = state.getMatchingState().getQueueTeams().get(playerId);
            NetworkManager.sendToPlayer(player, buildMatchingPacket(room, state, gameTick, player, selfTeam));
            sendTeamMirrorIfChanged(player, mirrorSnapshot);
            ServerPlayerLockService.of(server).syncPlayer(player);
        }
    }

    public void syncRunningRoom(RoomDefinition room, RunningRuntimeState state) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        RoomRunningHudRenderer renderer = resolveRunningHudRenderer(room);
        if (renderer == null) {
            return;
        }
        StandardRoomRunningHudRenderer.RunningRoomBatch renderBatch = renderer instanceof StandardRoomRunningHudRenderer standardRenderer
            ? standardRenderer.beginRoomBatch(room, state)
            : null;
        TeamMirrorSnapshot mirrorSnapshot = buildRunningTeamMirrorPacket(room, state);

        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomHudSyncPacket packet = renderBatch != null
                ? ((StandardRoomRunningHudRenderer) renderer).buildPacket(renderBatch, player)
                : renderer.buildPacket(room, state, player);
            if (packet == null) {
                continue;
            }
            NetworkManager.sendToPlayer(player, packet);
            sendTeamMirrorIfChanged(player, mirrorSnapshot);
            ServerPlayerLockService.of(server).syncPlayer(player);
        }
    }

    public boolean syncRunningPlayer(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (player == null) {
            return false;
        }
        RoomRunningHudRenderer renderer = resolveRunningHudRenderer(room);
        if (renderer == null) {
            return false;
        }
        RoomHudSyncPacket packet = renderer.buildPacket(room, state, player);
        if (packet == null) {
            return false;
        }
        NetworkManager.sendToPlayer(player, packet);
        sendTeamMirrorIfChanged(player, buildRunningTeamMirrorPacket(room, state));
        ServerPlayerLockService.of(server).syncPlayer(player);
        return true;
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        NetworkManager.sendToPlayer(player, new RoomHudSyncPacket(
            "-",
            "未加入房间",
            "-",
            "-",
            "-",
            0,
            0,
            0,
            "SCORE_TARGET",
            0,
            0,
            0,
            "-",
            "-",
            "-",
            "-"
        ));
        if (player.getUUID() != null) {
            lastTeamMirrorSignatureByPlayer.remove(player.getUUID());
        }
        ServerPlayerLockService.of(server).clearPlayer(player);
        NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.reset());
        NetworkManager.sendToPlayer(player, RoomTeamMirrorSyncPacket.clear());
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        clearPlayer(server.getPlayerList().getPlayer(playerId));
    }

    private RoomHudSyncPacket buildMatchingPacket(RoomDefinition room, RoomRuntimeState state, long gameTick, ServerPlayer player, RoomTeamId selfTeam) {
        RoomMatchingConfig config = room.commonConfig().matchingConfig();
        int queueCampA = countPlayersInTeam(state, RoomTeamId.CAMP_A);
        int queueCampB = countPlayersInTeam(state, RoomTeamId.CAMP_B);
        int remainSeconds = resolveMatchingRemainSeconds(state, gameTick);
        String queueStatus = buildMatchingStatusText(config, state, remainSeconds);
        String captureIcons = java.util.Optional.ofNullable(resolveRunningHudRenderer(room))
            .map(renderer -> renderer.buildMatchingCaptureIcons(room, state, player))
            .orElse("-");
        return new RoomHudSyncPacket(
            room.roomId(),
            "匹配中",
            "MATCHING",
            toHudTeamName(selfTeam),
            resolveHudClassKey(player),
            queueCampA,
            queueCampB,
            0,
            "QUEUE",
            config.campAMaxPlayers(),
            config.campBMaxPlayers(),
            remainSeconds,
            queueStatus,
            captureIcons,
            "-",
            "-"
        );
    }

    private int countPlayersInTeam(RoomRuntimeState state, RoomTeamId teamId) {
        int count = 0;
        for (RoomTeamId assigned : state.getMatchingState().getQueueTeams().values()) {
            if (assigned == teamId) {
                count++;
            }
        }
        return count;
    }

    private int resolveMatchingRemainSeconds(RoomRuntimeState state, long gameTick) {
        long deadline = state.getMatchingState().getQueueDeadlineTick();
        if (deadline <= gameTick) {
            return 0;
        }
        return (int) Math.max(0L, (deadline - gameTick + 19L) / 20L);
    }

    private String buildMatchingStatusText(RoomMatchingConfig config, RoomRuntimeState state, int remainSeconds) {
        int queuedPlayers = state.getMatchingState().getQueuePlayers().size();
        if (queuedPlayers < config.minStartPlayers()) {
            return "等待玩家 " + queuedPlayers + "/" + config.minStartPlayers();
        }
        for (RoomTeamId enabledTeam : config.enabledTeams()) {
            if (!state.getMatchingState().getQueueTeams().containsValue(enabledTeam)) {
                return "等待" + toHudTeamName(enabledTeam) + "阵营";
            }
        }
        if (countPlayersInTeam(state, RoomTeamId.CAMP_A) < config.campAMaxPlayers()
            || countPlayersInTeam(state, RoomTeamId.CAMP_B) < config.campBMaxPlayers()) {
            return "等待阵营人数补齐";
        }
        if (remainSeconds > 0) {
            return "部署倒计时 " + remainSeconds + "秒";
        }
        return "等待开始";
    }

    private String toHudTeamName(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "CampA";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "CampB";
        }
        return "-";
    }

    private String resolveHudClassKey(ServerPlayer player) {
        if (player == null || player.server == null) {
            return PlayerClassType.defaultType().getKey();
        }
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(
            ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID())
        );
        return classType == null ? PlayerClassType.defaultType().getKey() : classType.getKey();
    }

    private RoomRunningHudRenderer resolveRunningHudRenderer(RoomDefinition room) {
        if (room == null) {
            return null;
        }
        for (RoomRunningHudRenderer renderer : runningHudRenderers) {
            if (renderer.supports(room)) {
                return renderer;
            }
        }
        return null;
    }

    private record TeamMirrorSnapshot(String signature, RoomTeamMirrorSyncPacket packet) {
    }


    private TeamMirrorSnapshot buildRunningTeamMirrorPacket(RoomDefinition room, RunningRuntimeState state) {
        List<String> campAPlayers = new ArrayList<>();
        List<String> campBPlayers = new ArrayList<>();
        List<RoomTeamMirrorSyncPacket.PlayerSquadEntry> squadEntries = new ArrayList<>();
        StringBuilder signature = new StringBuilder("running|");
        signature.append(room == null ? "-" : room.roomId()).append('|');
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomTeamId teamId = state.getRoomTeams().get(playerId);
            String playerName = player.getScoreboardName();
            String squadLabel = state.getSquadLabelsByPlayer().getOrDefault(playerId, "");
            if (teamId == RoomTeamId.CAMP_A) {
                campAPlayers.add(playerName);
            } else if (teamId == RoomTeamId.CAMP_B) {
                campBPlayers.add(playerName);
            }
            squadEntries.add(new RoomTeamMirrorSyncPacket.PlayerSquadEntry(
                playerName,
                squadLabel
            ));
            signature.append(teamId == null ? "-" : teamId.name())
                .append(':')
                .append(playerName)
                .append(':')
                .append(squadLabel)
                .append(';');
        }
        String campADisplayName = RoomTeamPresentationSupport.resolveTeamDisplayName(room, state, RoomTeamId.CAMP_A);
        String campBDisplayName = RoomTeamPresentationSupport.resolveTeamDisplayName(room, state, RoomTeamId.CAMP_B);
        signature.append('|').append(campADisplayName).append('|').append(campBDisplayName);
        return new TeamMirrorSnapshot(
            signature.toString(),
            new RoomTeamMirrorSyncPacket(
                true,
                room.roomId(),
                campADisplayName,
                campBDisplayName,
                campAPlayers,
                campBPlayers,
                squadEntries
            )
        );
    }

    private TeamMirrorSnapshot buildMatchingTeamMirrorPacket(RoomDefinition room, RoomRuntimeState state) {
        List<String> campAPlayers = new ArrayList<>();
        List<String> campBPlayers = new ArrayList<>();
        List<RoomTeamMirrorSyncPacket.PlayerSquadEntry> squadEntries = new ArrayList<>();
        StringBuilder signature = new StringBuilder("matching|");
        signature.append(room == null ? "-" : room.roomId()).append('|');
        for (UUID playerId : state.getMatchingState().getQueuePlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomTeamId teamId = state.getMatchingState().getQueueTeams().get(playerId);
            String playerName = player.getScoreboardName();
            String squadLabel = state.getSquadLabelsByPlayer().getOrDefault(playerId, "");
            if (teamId == RoomTeamId.CAMP_A) {
                campAPlayers.add(playerName);
            } else if (teamId == RoomTeamId.CAMP_B) {
                campBPlayers.add(playerName);
            }
            squadEntries.add(new RoomTeamMirrorSyncPacket.PlayerSquadEntry(
                playerName,
                squadLabel
            ));
            signature.append(teamId == null ? "-" : teamId.name())
                .append(':')
                .append(playerName)
                .append(':')
                .append(squadLabel)
                .append(';');
        }
        String campADisplayName = RoomTeamPresentationSupport.resolveConfiguredDisplayName(room, RoomTeamId.CAMP_A);
        String campBDisplayName = RoomTeamPresentationSupport.resolveConfiguredDisplayName(room, RoomTeamId.CAMP_B);
        signature.append('|').append(campADisplayName).append('|').append(campBDisplayName);
        return new TeamMirrorSnapshot(
            signature.toString(),
            new RoomTeamMirrorSyncPacket(
                true,
                room.roomId(),
                campADisplayName,
                campBDisplayName,
                campAPlayers,
                campBPlayers,
                squadEntries
            )
        );
    }

    private void sendTeamMirrorIfChanged(ServerPlayer player, TeamMirrorSnapshot snapshot) {
        if (player == null || snapshot == null || snapshot.packet() == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (playerId == null) {
            NetworkManager.sendToPlayer(player, snapshot.packet());
            return;
        }
        String lastSignature = lastTeamMirrorSignatureByPlayer.get(playerId);
        if (snapshot.signature().equals(lastSignature)) {
            return;
        }
        lastTeamMirrorSignatureByPlayer.put(playerId, snapshot.signature());
        NetworkManager.sendToPlayer(player, snapshot.packet());
    }
}
