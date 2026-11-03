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
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.gameplay.RoomSoloGameplaySupport;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

public final class RoomHudSyncService {
    private final MinecraftServer server;
    private final java.util.List<RoomRunningHudRenderer> runningHudRenderers;
    private final Map<UUID, String> lastHudPacketKeys = new LinkedHashMap<>();
    private final Map<UUID, String> lastTeamMirrorPacketKeys = new LinkedHashMap<>();

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
            sendHudPacketIfChanged(player, buildMatchingPacket(room, state, gameTick, player, selfTeam));
            sendTeamMirrorIfChanged(player, mirrorSnapshot);
        }
    }

    public void syncRunningRoom(RoomDefinition room, RunningRuntimeState state) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        RoomRunningHudRenderer renderer = resolveRunningHudRenderer(room);
        if (renderer == null) {
            return;
        }
        renderer.prepareRoomSnapshot(room, state);
        TeamMirrorSnapshot mirrorSnapshot = buildRunningTeamMirrorPacket(room, state);

        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomHudSyncPacket packet = renderer.buildPacket(room, state, player);
            if (packet == null) {
                continue;
            }
            sendHudPacketIfChanged(player, packet);
            sendTeamMirrorIfChanged(player, mirrorSnapshot);
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
        renderer.prepareRoomSnapshot(room, state);
        RoomHudSyncPacket packet = renderer.buildPacket(room, state, player);
        if (packet == null) {
            return false;
        }
        sendHudPacketIfChanged(player, packet);
        sendTeamMirrorIfChanged(player, buildRunningTeamMirrorPacket(room, state));
        return true;
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        lastHudPacketKeys.remove(playerId);
        lastTeamMirrorPacketKeys.remove(playerId);
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
            0,
            0,
            true,
            "-",
            "-",
            "-",
            "-",
            "-"
        ));
        ServerPlayerLockService.of(server).clearPlayer(player);
        NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.reset());
        NetworkManager.sendToPlayer(player, RoomTeamMirrorSyncPacket.clear());
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastHudPacketKeys.remove(playerId);
        lastTeamMirrorPacketKeys.remove(playerId);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        clearPlayer(player);
    }

    private RoomHudSyncPacket buildMatchingPacket(RoomDefinition room, RoomRuntimeState state, long gameTick, ServerPlayer player, RoomTeamId selfTeam) {
        RoomMatchingConfig config = room.commonConfig().matchingConfig();
        boolean soloDeathmatch = RoomSoloGameplaySupport.isDeathmatchSolo(room);
        int queueCampA = soloDeathmatch ? state.getMatchingState().getQueuePlayers().size() : countPlayersInTeam(state, RoomTeamId.CAMP_A);
        int queueCampB = soloDeathmatch ? 0 : countPlayersInTeam(state, RoomTeamId.CAMP_B);
        int remainSeconds = resolveMatchingRemainSeconds(state, gameTick);
        String queueStatus = buildMatchingStatusText(room, config, state, remainSeconds);
        String captureIcons = java.util.Optional.ofNullable(resolveRunningHudRenderer(room))
            .map(renderer -> renderer.buildMatchingCaptureIcons(room, state, player))
            .orElse("-");
        boolean allowTeamSwitch = room.commonConfig().allowPlayerCampSwitch();
        if (RoomSoloGameplaySupport.isSoloFreeForAll(room)) {
            allowTeamSwitch = false;
        }

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
            soloDeathmatch ? config.totalMaxPlayers() : config.campAMaxPlayers(),
            soloDeathmatch ? 0 : config.campBMaxPlayers(),
            remainSeconds,
            room.commonConfig().playerMaxY(),
            room.commonConfig().aircraftPassengerMaxY(),
            allowTeamSwitch,
            queueStatus,
            captureIcons,
            "-",
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

    private String buildMatchingStatusText(RoomDefinition room, RoomMatchingConfig config, RoomRuntimeState state, int remainSeconds) {
        if (RoomSoloGameplaySupport.isDeathmatchSolo(room)) {
            int queuedPlayers = state.getMatchingState().getQueuePlayers().size();
            if (queuedPlayers < config.minStartPlayers()) {
                return "等待玩家 " + queuedPlayers + "/" + config.minStartPlayers();
            }
            if (remainSeconds > 0) {
                return "部署倒计时 " + remainSeconds + "秒";
            }
            return "等待开始";
        }
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
        RunningRuntimeState state = RoomCoreRuntimeManager.of(player.server)
            .findRunningState(RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).orElse(""))
            .orElse(null);
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(
            ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID()),
            state,
            player.getUUID()
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

    private record TeamMirrorSnapshot(RoomTeamMirrorSyncPacket packet) {
    }


    private TeamMirrorSnapshot buildRunningTeamMirrorPacket(RoomDefinition room, RunningRuntimeState state) {
        List<String> campAPlayers = new ArrayList<>();
        List<String> campBPlayers = new ArrayList<>();
        List<RoomTeamMirrorSyncPacket.PlayerSquadEntry> squadEntries = new ArrayList<>();
        List<RoomTeamMirrorSyncPacket.PlayerClassEntry> classEntries = new ArrayList<>();
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
            classEntries.add(new RoomTeamMirrorSyncPacket.PlayerClassEntry(
                playerName,
                resolveHudClassKey(player)
            ));
        }
        String campADisplayName = RoomTeamPresentationSupport.resolveTeamDisplayName(room, state, RoomTeamId.CAMP_A);
        String campBDisplayName = RoomTeamPresentationSupport.resolveTeamDisplayName(room, state, RoomTeamId.CAMP_B);
        return new TeamMirrorSnapshot(
            new RoomTeamMirrorSyncPacket(
                true,
                room.roomId(),
                campADisplayName,
                campBDisplayName,
                campAPlayers,
                campBPlayers,
                squadEntries,
                classEntries
            )
        );
    }

    private TeamMirrorSnapshot buildMatchingTeamMirrorPacket(RoomDefinition room, RoomRuntimeState state) {
        List<String> campAPlayers = new ArrayList<>();
        List<String> campBPlayers = new ArrayList<>();
        List<RoomTeamMirrorSyncPacket.PlayerSquadEntry> squadEntries = new ArrayList<>();
        List<RoomTeamMirrorSyncPacket.PlayerClassEntry> classEntries = new ArrayList<>();
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
            classEntries.add(new RoomTeamMirrorSyncPacket.PlayerClassEntry(
                playerName,
                resolveHudClassKey(player)
            ));
        }
        String campADisplayName = RoomTeamPresentationSupport.resolveConfiguredDisplayName(room, RoomTeamId.CAMP_A);
        String campBDisplayName = RoomTeamPresentationSupport.resolveConfiguredDisplayName(room, RoomTeamId.CAMP_B);
        return new TeamMirrorSnapshot(
            new RoomTeamMirrorSyncPacket(
                true,
                room.roomId(),
                campADisplayName,
                campBDisplayName,
                campAPlayers,
                campBPlayers,
                squadEntries,
                classEntries
            )
        );
    }

    private void sendHudPacketIfChanged(ServerPlayer player, RoomHudSyncPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        String nextKey = packet.cacheKey();
        UUID playerId = player.getUUID();
        if (nextKey.equals(lastHudPacketKeys.get(playerId))) {
            return;
        }
        lastHudPacketKeys.put(playerId, nextKey);
        NetworkManager.sendToPlayer(player, packet);
    }

    private void sendTeamMirrorIfChanged(ServerPlayer player, TeamMirrorSnapshot snapshot) {
        if (player == null || snapshot == null || snapshot.packet() == null) {
            return;
        }
        String nextKey = snapshot.packet().cacheKey();
        UUID playerId = player.getUUID();
        if (nextKey.equals(lastTeamMirrorPacketKeys.get(playerId))) {
            return;
        }
        lastTeamMirrorPacketKeys.put(playerId, nextKey);
        NetworkManager.sendToPlayer(player, snapshot.packet());
    }
}
