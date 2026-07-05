package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RoomNametagVisibilityService {
    private final MinecraftServer server;

    public RoomNametagVisibilityService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void syncRunningRoom(RoomDefinition room, RunningRuntimeState runningState) {
        if (room == null || runningState == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam campATeam = ensureTeam(scoreboard, room, runningState, RoomTeamId.CAMP_A);
        PlayerTeam campBTeam = ensureTeam(scoreboard, room, runningState, RoomTeamId.CAMP_B);
        Set<String> desiredCampAPlayers = new LinkedHashSet<>();
        Set<String> desiredCampBPlayers = new LinkedHashSet<>();
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
            if (teamId == RoomTeamId.CAMP_A) {
                desiredCampAPlayers.add(player.getScoreboardName());
            } else if (teamId == RoomTeamId.CAMP_B) {
                desiredCampBPlayers.add(player.getScoreboardName());
            }
        }
        syncTeamMembers(scoreboard, campATeam, desiredCampAPlayers);
        syncTeamMembers(scoreboard, campBTeam, desiredCampBPlayers);
    }

    public void forceResyncRunningRoom(RoomDefinition room, RunningRuntimeState runningState) {
        if (room == null || runningState == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        removeTeamIfPresent(scoreboard, buildTeamName(room, RoomTeamId.CAMP_A));
        removeTeamIfPresent(scoreboard, buildTeamName(room, RoomTeamId.CAMP_B));
        syncRunningRoom(room, runningState);
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        Scoreboard scoreboard = player.server.getScoreboard();
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (currentTeam == null || !isRoomCampTeam(currentTeam.getName())) {
            return;
        }
        scoreboard.removePlayerFromTeam(player.getScoreboardName());
    }

    public void resyncPlayer(RoomDefinition room, RunningRuntimeState runningState, ServerPlayer player) {
        if (room == null || runningState == null || player == null || player.server == null) {
            return;
        }
        Scoreboard scoreboard = player.server.getScoreboard();
        RoomTeamId teamId = runningState.getRoomTeams().get(player.getUUID());
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            clearPlayer(player);
            return;
        }
        PlayerTeam targetTeam = ensureTeam(scoreboard, room, runningState, teamId);
        String playerName = player.getScoreboardName();
        scoreboard.removePlayerFromTeam(playerName);
        scoreboard.addPlayerToTeam(playerName, targetTeam);
    }

    public void clearRoom(RoomDefinition room) {
        if (room == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        removeTeamIfPresent(scoreboard, buildTeamName(room, RoomTeamId.CAMP_A));
        removeTeamIfPresent(scoreboard, buildTeamName(room, RoomTeamId.CAMP_B));
    }

    private PlayerTeam ensureTeam(Scoreboard scoreboard, RoomDefinition room, RunningRuntimeState runningState, RoomTeamId teamId) {
        String teamName = buildTeamName(room, teamId);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        // Room scoreboard teams are presentation-only. Combat rules are handled by RoomServerModule.
        team.setAllowFriendlyFire(true);
        team.setColor(resolveTeamColor(teamId));
        team.setDisplayName(Component.literal(RoomTeamPresentationSupport.resolveTeamDisplayName(room, runningState, teamId)));
        team.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        team.setCollisionRule(Team.CollisionRule.ALWAYS);
        return team;
    }

    private void syncTeamMembers(Scoreboard scoreboard, PlayerTeam team, Set<String> desiredPlayers) {
        Set<String> currentPlayers = new LinkedHashSet<>(team.getPlayers());
        for (String playerName : currentPlayers) {
            if (!desiredPlayers.contains(playerName)) {
                scoreboard.removePlayerFromTeam(playerName);
            }
        }
        for (String playerName : desiredPlayers) {
            if (!team.getPlayers().contains(playerName)) {
                scoreboard.removePlayerFromTeam(playerName);
                scoreboard.addPlayerToTeam(playerName, team);
            }
        }
    }

    private void removeTeamIfPresent(Scoreboard scoreboard, String teamName) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    private String buildTeamName(RoomDefinition room, RoomTeamId teamId) {
        String normalizedRoom = room.roomId().toLowerCase(Locale.ROOT);
        return normalizedRoom + "_" + (teamId == RoomTeamId.CAMP_A ? "camp_a" : "camp_b");
    }

    private boolean isRoomCampTeam(String teamName) {
        if (teamName == null) {
            return false;
        }
        String normalized = teamName.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith("_camp_a") || normalized.endsWith("_camp_b");
    }

    private ChatFormatting resolveTeamColor(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return ChatFormatting.RED;
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return ChatFormatting.BLUE;
        }
        return ChatFormatting.WHITE;
    }
}
