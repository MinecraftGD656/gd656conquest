package org.mods.gd656conquest.server.room.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomSoloGameplaySupport;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

public final class RoomKilliconService {
    private final MinecraftServer server;

    public RoomKilliconService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public KilliconScoreboardBridge.Payload buildPayload(RoomDefinition room, RoomRuntimeState runtimeState, UUID requester) {
        if (room == null || runtimeState == null || requester == null) {
            return null;
        }
        if (!this.shouldUseConquestPayload(room.lifecycleState())) {
            return null;
        }
        ArrayList<RoomTeamId> enabledTeams = new ArrayList<RoomTeamId>(room.commonConfig().matchingConfig().enabledTeams());
        boolean isSolo = RoomSoloGameplaySupport.isSoloFreeForAll(room);
        if (!isSolo && enabledTeams.size() < 2) {
            return null;
        }
        int columns = isSolo ? 1 : 2;
        String[] panelTeams = new String[]{"", "", "", ""};
        if (isSolo) {
            panelTeams[0] = "__conquest_solo__";
        } else {
            RoomTeamId requesterTeam = this.normalizeRequesterTeam(this.resolveTeam(room, runtimeState, requester), enabledTeams);
            RoomTeamId enemyTeam = this.resolveEnemyTeam(enabledTeams, requesterTeam);
            panelTeams[0] = this.buildScoreboardPanelBinding(room, requesterTeam);
            panelTeams[1] = this.buildScoreboardPanelBinding(room, enemyTeam);
        }
        Set<UUID> roomPlayers = this.resolveVisiblePlayers(room, runtimeState);
        if (roomPlayers.isEmpty()) {
            return null;
        }
        ArrayList<KilliconScoreboardBridge.Entry> entries = new ArrayList<KilliconScoreboardBridge.Entry>();
        for (UUID playerId : roomPlayers) {
            ServerPlayer player = this.server.getPlayerList().getPlayer(playerId);
            String displayName = player != null ? player.getScoreboardName() : playerId.toString();
            RoomTeamId playerTeam = this.resolveTeam(room, runtimeState, playerId);
            String squadLabel = runtimeState.getSquadLabelsByPlayer().getOrDefault(playerId, "");
            KilliconScoreboardBridge.TempSnapshot snapshot = this.resolveDisplayedSnapshot(room, runtimeState, playerId);
            entries.add(new KilliconScoreboardBridge.Entry(playerId, displayName, displayName, playerTeam == null ? "" : this.buildScoreboardTeamName(room, playerTeam), squadLabel, snapshot.score(), snapshot.kill(), snapshot.death(), snapshot.assist(), snapshot.revive(), player != null ? player.latency : -1, player != null, player != null && player.isSpectator()));
        }
        entries.sort((left, right) -> {
            int primaryCompare = isSolo
                ? Integer.compare(right.kill(), left.kill())
                : Integer.compare(right.score(), left.score());
            if (primaryCompare != 0) {
                return primaryCompare;
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
            return KilliconScoreboardBridge.getPlayerSnapshot((RunningRuntimeState)(runtimeState == null ? null : runtimeState.getRunningState()), (UUID)playerId);
        }
        // The matching queue belongs to the upcoming match, whose statistics begin at zero.
        return KilliconScoreboardBridge.TempSnapshot.EMPTY;
    }

    private Set<UUID> resolveVisiblePlayers(RoomDefinition room, RoomRuntimeState runtimeState) {
        if (room.lifecycleState() == RoomLifecycleState.MATCHING) {
            return runtimeState.getMatchingState().getQueuePlayers();
        }
        if (room.lifecycleState() == RoomLifecycleState.RUNNING || room.lifecycleState() == RoomLifecycleState.ENDING) {
            return runtimeState.getRunningState().getRoomPlayers();
        }
        return Set.of();
    }

    private boolean shouldUseConquestPayload(RoomLifecycleState lifecycleState) {
        return lifecycleState == RoomLifecycleState.MATCHING || lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING;
    }

    private RoomTeamId resolveTeam(RoomDefinition room, RoomRuntimeState runtimeState, UUID playerId) {
        if (room.lifecycleState() == RoomLifecycleState.RUNNING || room.lifecycleState() == RoomLifecycleState.ENDING) {
            return (RoomTeamId)runtimeState.getRunningState().getRoomTeams().get(playerId);
        }
        return (RoomTeamId)runtimeState.getMatchingState().getQueueTeams().get(playerId);
    }

    private String buildScoreboardTeamName(RoomDefinition room, RoomTeamId teamId) {
        String normalizedRoom = room.roomId().toLowerCase(Locale.ROOT);
        String teamToken = teamId == RoomTeamId.CAMP_A ? "camp_a" : (teamId == RoomTeamId.CAMP_B ? "camp_b" : "spectator");
        return normalizedRoom + "_" + teamToken;
    }

    private String buildScoreboardPanelBinding(RoomDefinition room, RoomTeamId teamId) {
        int maxPlayers;
        String teamName = this.buildScoreboardTeamName(room, teamId);
        int n = maxPlayers = room == null ? 0 : room.commonConfig().matchingConfig().maxPlayersForTeam(teamId);
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
            if (teamId == null || teamId == requesterTeam) continue;
            return teamId;
        }
        return requesterTeam;
    }
}
