package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RoomKilliconService {
    private final MinecraftServer server;

    public RoomKilliconService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public KilliconScoreboardBridge.Payload buildPayload(RoomDefinition room, RoomRuntimeState runtimeState, UUID requester) {
        if (room == null || runtimeState == null || requester == null) {
            return null;
        }
        if (!shouldUseConquestPayload(room.lifecycleState())) {
            return null;
        }
        List<RoomTeamId> enabledTeams = new ArrayList<>(room.commonConfig().matchingConfig().enabledTeams());
        if (enabledTeams.size() < 2) {
            return null;
        }
        boolean isSolo = org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(room.gameplayId()) 
            && room.gunmasterConfig() != null 
            && room.gunmasterConfig().isSoloGrouping();

        int columns = isSolo ? 1 : 2;
        String[] panelTeams = new String[]{"", "", "", ""};
        
        if (isSolo) {
            panelTeams[0] = "__all__";
        } else {
            RoomTeamId requesterTeam = normalizeRequesterTeam(resolveTeam(room, runtimeState, requester), enabledTeams);
            RoomTeamId enemyTeam = resolveEnemyTeam(enabledTeams, requesterTeam);
            panelTeams[0] = buildScoreboardPanelBinding(room, requesterTeam);
            panelTeams[1] = buildScoreboardPanelBinding(room, enemyTeam);
        }

        Set<UUID> roomPlayers = resolveVisiblePlayers(room, runtimeState);
        if (roomPlayers.isEmpty()) {
            return null;
        }

        List<KilliconScoreboardBridge.Entry> entries = new ArrayList<>();
        for (UUID playerId : roomPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            String displayName = player != null ? player.getScoreboardName() : playerId.toString();
            RoomTeamId playerTeam = resolveTeam(room, runtimeState, playerId);
            String squadLabel = runtimeState.getSquadLabelsByPlayer().getOrDefault(playerId, "");
            KilliconScoreboardBridge.TempSnapshot snapshot = resolveDisplayedSnapshot(room, runtimeState, playerId);
            entries.add(new KilliconScoreboardBridge.Entry(
                playerId,
                displayName,
                displayName,
                playerTeam == null ? "" : buildScoreboardTeamName(room, playerTeam),
                squadLabel,
                snapshot.score(),
                snapshot.kill(),
                snapshot.death(),
                snapshot.assist(),
                snapshot.revive(),
                player != null ? player.latency : -1,
                player != null,
                player != null && player.isSpectator()
            ));
        }
        entries.sort((left, right) -> {
            int scoreCompare = Integer.compare(right.score(), left.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.uuid().compareTo(right.uuid());
        });
        return new KilliconScoreboardBridge.Payload(entries, columns, panelTeams);
    }

    private KilliconScoreboardBridge.TempSnapshot resolveDisplayedSnapshot(RoomDefinition room, RoomRuntimeState runtimeState, UUID playerId) {
        if (room == null || playerId == null) {
            return KilliconScoreboardBridge.TempSnapshot.EMPTY;
        }
        if (room.lifecycleState() == RoomLifecycleState.RUNNING || room.lifecycleState() == RoomLifecycleState.ENDING) {
            return KilliconScoreboardBridge.getPlayerSnapshot(runtimeState == null ? null : runtimeState.getRunningState(), playerId);
        }
        return KilliconScoreboardBridge.getGlobalSnapshot(playerId);
    }

    private Set<UUID> resolveVisiblePlayers(RoomDefinition room, RoomRuntimeState runtimeState) {
        if (room.lifecycleState() == RoomLifecycleState.MATCHING) {
            return runtimeState.getMatchingState().getQueuePlayers();
        }
        if (room.lifecycleState() == RoomLifecycleState.RUNNING || room.lifecycleState() == RoomLifecycleState.ENDING) {
            return runtimeState.getRunningState().getRoomPlayers();
        }
        return java.util.Set.of();
    }

    private boolean shouldUseConquestPayload(RoomLifecycleState lifecycleState) {
        return lifecycleState == RoomLifecycleState.MATCHING
            || lifecycleState == RoomLifecycleState.RUNNING
            || lifecycleState == RoomLifecycleState.ENDING;
    }

    private RoomTeamId resolveTeam(RoomDefinition room, RoomRuntimeState runtimeState, UUID playerId) {
        if (room.lifecycleState() == RoomLifecycleState.RUNNING || room.lifecycleState() == RoomLifecycleState.ENDING) {
            return runtimeState.getRunningState().getRoomTeams().get(playerId);
        }
        return runtimeState.getMatchingState().getQueueTeams().get(playerId);
    }

    private String buildScoreboardTeamName(RoomDefinition room, RoomTeamId teamId) {
        String normalizedRoom = room.roomId().toLowerCase(java.util.Locale.ROOT);
        String teamToken = teamId == RoomTeamId.CAMP_A ? "camp_a" : teamId == RoomTeamId.CAMP_B ? "camp_b" : "spectator";
        return normalizedRoom + "_" + teamToken;
    }

    private String buildScoreboardPanelBinding(RoomDefinition room, RoomTeamId teamId) {
        String teamName = buildScoreboardTeamName(room, teamId);
        int maxPlayers = room == null ? 0 : room.commonConfig().matchingConfig().maxPlayersForTeam(teamId);
        if (maxPlayers <= 0) {
            return teamName;
        }
        return teamName + "|cap=" + maxPlayers;
    }

    private RoomTeamId normalizeRequesterTeam(RoomTeamId requesterTeam, List<RoomTeamId> enabledTeams) {
        if (requesterTeam != null && enabledTeams.contains(requesterTeam)) {
            return requesterTeam;
        }
        return enabledTeams.get(0);
    }

    private RoomTeamId resolveEnemyTeam(List<RoomTeamId> enabledTeams, RoomTeamId requesterTeam) {
        for (RoomTeamId teamId : enabledTeams) {
            if (teamId != null && teamId != requesterTeam) {
                return teamId;
            }
        }
        return requesterTeam;
    }
}
