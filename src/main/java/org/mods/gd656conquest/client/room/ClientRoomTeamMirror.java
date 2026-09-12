package org.mods.gd656conquest.client.room;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.mods.gd656conquest.network.packet.RoomTeamMirrorSyncPacket;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClientRoomTeamMirror {
    private static String lastRoomId = "";
    private static String campADisplayName = "CampA";
    private static String campBDisplayName = "CampB";
    private static int campAPlayerCount;
    private static int campBPlayerCount;
    private static final Map<String, TeamToken> TEAM_BY_PLAYER = new LinkedHashMap<>();
    private static final Map<String, String> SQUAD_BY_PLAYER = new LinkedHashMap<>();

    private static final Map<String, String> CLASS_BY_PLAYER = new LinkedHashMap<>();

    private ClientRoomTeamMirror() {
    }

    public static void apply(
        boolean active,
        String roomId,
        String campADisplayName,
        String campBDisplayName,
        List<String> campAPlayers,
        List<String> campBPlayers,
        List<RoomTeamMirrorSyncPacket.PlayerSquadEntry> squadEntries,
        List<RoomTeamMirrorSyncPacket.PlayerClassEntry> classEntries
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            if (!active) {
                lastRoomId = "";
                TEAM_BY_PLAYER.clear();
                SQUAD_BY_PLAYER.clear();
                CLASS_BY_PLAYER.clear();
            }
            return;
        }
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        if (scoreboard == null) {
            return;
        }
        if (!active || roomId == null || roomId.isBlank() || "-".equals(roomId)) {
            clearRoom(scoreboard, lastRoomId);
            lastRoomId = "";
            TEAM_BY_PLAYER.clear();
            SQUAD_BY_PLAYER.clear();
            CLASS_BY_PLAYER.clear();
            return;
        }
        String normalizedRoomId = roomId.toLowerCase(Locale.ROOT);
        if (!lastRoomId.isBlank() && !lastRoomId.equals(normalizedRoomId)) {
            clearRoom(scoreboard, lastRoomId);
        }
        String campATeamName = buildTeamName(normalizedRoomId, "camp_a");
        String campBTeamName = buildTeamName(normalizedRoomId, "camp_b");
        ClientRoomTeamMirror.campADisplayName = campADisplayName == null || campADisplayName.isBlank() ? "CampA" : campADisplayName;
        ClientRoomTeamMirror.campBDisplayName = campBDisplayName == null || campBDisplayName.isBlank() ? "CampB" : campBDisplayName;
        ClientRoomTeamMirror.campAPlayerCount = campAPlayers == null ? 0 : campAPlayers.size();
        ClientRoomTeamMirror.campBPlayerCount = campBPlayers == null ? 0 : campBPlayers.size();
        PlayerTeam campATeam = ensureTeam(scoreboard, campATeamName, ClientRoomTeamMirror.campADisplayName, ChatFormatting.WHITE);
        PlayerTeam campBTeam = ensureTeam(scoreboard, campBTeamName, ClientRoomTeamMirror.campBDisplayName, ChatFormatting.WHITE);
        syncTeamMembers(scoreboard, campATeam, toNameSet(campAPlayers));
        syncTeamMembers(scoreboard, campBTeam, toNameSet(campBPlayers));
        rebuildMirrorState(campAPlayers, campBPlayers, squadEntries, classEntries);
        lastRoomId = normalizedRoomId;
    }

    public static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            lastRoomId = "";
            TEAM_BY_PLAYER.clear();
            SQUAD_BY_PLAYER.clear();
            CLASS_BY_PLAYER.clear();
            return;
        }
        clearRoom(minecraft.level.getScoreboard(), lastRoomId);
        lastRoomId = "";
            campADisplayName = "CampA";
            campBDisplayName = "CampB";
            campAPlayerCount = 0;
            campBPlayerCount = 0;
        TEAM_BY_PLAYER.clear();
        SQUAD_BY_PLAYER.clear();
        CLASS_BY_PLAYER.clear();
    }

    public static boolean isTeammate(Player targetPlayer) {
        if (targetPlayer == null) {
            return false;
        }
        return isTeammateName(targetPlayer.getScoreboardName());
    }

    public static boolean isSquadMate(Player targetPlayer) {
        return targetPlayer != null && isSquadMateName(targetPlayer.getScoreboardName());
    }

    public static boolean isTeammateName(String targetPlayerName) {
        String selfName = resolveSelfPlayerName();
        String targetName = normalizePlayerName(targetPlayerName);
        if (selfName.isBlank() || targetName.isBlank() || selfName.equals(targetName)) {
            return false;
        }
        TeamToken selfTeam = TEAM_BY_PLAYER.getOrDefault(selfName, TeamToken.UNKNOWN);
        TeamToken targetTeam = TEAM_BY_PLAYER.getOrDefault(targetName, TeamToken.UNKNOWN);
        return selfTeam != TeamToken.UNKNOWN && selfTeam == targetTeam;
    }

    public static boolean isSquadMateName(String targetPlayerName) {
        if (!isTeammateName(targetPlayerName)) {
            return false;
        }
        String selfSquad = resolveSelfSquadLabel();
        String targetSquad = SQUAD_BY_PLAYER.getOrDefault(normalizePlayerName(targetPlayerName), "");
        return !selfSquad.isBlank() && selfSquad.equals(targetSquad);
    }

    public static String getPlayerClass(Player targetPlayer) {
        if (targetPlayer == null) {
            return "";
        }
        return CLASS_BY_PLAYER.getOrDefault(normalizePlayerName(targetPlayer.getScoreboardName()), "");
    }

    public static int getSelfTeamPlayerCount() {
        return switch (resolveSelfTeamToken()) {
            case CAMP_A -> campAPlayerCount;
            case CAMP_B -> campBPlayerCount;
            case UNKNOWN -> 0;
        };
    }

    public static int getOppositeTeamPlayerCount() {
        return switch (resolveSelfTeamToken()) {
            case CAMP_A -> campBPlayerCount;
            case CAMP_B -> campAPlayerCount;
            case UNKNOWN -> 0;
        };
    }

    public static String getOppositeTeamDisplayNameForSelf() {
        return switch (resolveSelfTeamToken()) {
            case CAMP_A -> campBDisplayName;
            case CAMP_B -> campADisplayName;
            case UNKNOWN -> "";
        };
    }

    private static PlayerTeam ensureTeam(Scoreboard scoreboard, String teamName, String displayName, ChatFormatting color) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        team.setAllowFriendlyFire(true);
        team.setSeeFriendlyInvisibles(false);
        team.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        team.setCollisionRule(Team.CollisionRule.ALWAYS);
        team.setDisplayName(Component.literal(displayName == null || displayName.isBlank() ? teamName : displayName));
        team.setColor(color == null ? ChatFormatting.WHITE : color);
        return team;
    }

    private static void syncTeamMembers(Scoreboard scoreboard, PlayerTeam team, Set<String> desiredPlayers) {
        Set<String> currentPlayers = new LinkedHashSet<>(team.getPlayers());
        for (String playerName : currentPlayers) {
            if (!desiredPlayers.contains(playerName)) {
                scoreboard.removePlayerFromTeam(playerName);
            }
        }
        for (String playerName : desiredPlayers) {
            if (playerName == null || playerName.isBlank()) {
                continue;
            }
            if (!team.getPlayers().contains(playerName)) {
                scoreboard.removePlayerFromTeam(playerName);
                scoreboard.addPlayerToTeam(playerName, team);
            }
        }
    }

    private static void clearRoom(Scoreboard scoreboard, String roomId) {
        if (scoreboard == null || roomId == null || roomId.isBlank()) {
            return;
        }
        removeTeam(scoreboard, buildTeamName(roomId, "camp_a"));
        removeTeam(scoreboard, buildTeamName(roomId, "camp_b"));
    }

    private static void rebuildMirrorState(
        List<String> campAPlayers,
        List<String> campBPlayers,
        List<RoomTeamMirrorSyncPacket.PlayerSquadEntry> squadEntries,
        List<RoomTeamMirrorSyncPacket.PlayerClassEntry> classEntries
    ) {
        TEAM_BY_PLAYER.clear();
        SQUAD_BY_PLAYER.clear();
        CLASS_BY_PLAYER.clear();
        for (String playerName : campAPlayers) {
            String normalized = normalizePlayerName(playerName);
            if (!normalized.isBlank()) {
                TEAM_BY_PLAYER.put(normalized, TeamToken.CAMP_A);
            }
        }
        for (String playerName : campBPlayers) {
            String normalized = normalizePlayerName(playerName);
            if (!normalized.isBlank()) {
                TEAM_BY_PLAYER.put(normalized, TeamToken.CAMP_B);
            }
        }
        if (squadEntries != null) {
            for (RoomTeamMirrorSyncPacket.PlayerSquadEntry squadEntry : squadEntries) {
                if (squadEntry == null) {
                    continue;
                }
                String normalized = normalizePlayerName(squadEntry.playerName());
                if (!normalized.isBlank()) {
                    SQUAD_BY_PLAYER.put(normalized, squadEntry.squadLabel() == null ? "" : squadEntry.squadLabel().trim());
                }
            }
        }
        if (classEntries != null) {
            for (RoomTeamMirrorSyncPacket.PlayerClassEntry classEntry : classEntries) {
                if (classEntry == null) {
                    continue;
                }
                String normalized = normalizePlayerName(classEntry.playerName());
                if (!normalized.isBlank()) {
                    CLASS_BY_PLAYER.put(normalized, classEntry.classKey() == null ? "" : classEntry.classKey().trim());
                }
            }
        }
    }

    private static void removeTeam(Scoreboard scoreboard, String teamName) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    private static Set<String> toNameSet(List<String> players) {
        Set<String> names = new LinkedHashSet<>();
        if (players == null) {
            return names;
        }
        for (String playerName : players) {
            if (playerName != null && !playerName.isBlank()) {
                names.add(playerName);
            }
        }
        return names;
    }

    private static String buildTeamName(String roomId, String suffix) {
        return roomId + "_" + suffix;
    }

    private static String resolveSelfPlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return "";
        }
        return normalizePlayerName(minecraft.player.getScoreboardName());
    }

    private static String resolveSelfSquadLabel() {
        String selfName = resolveSelfPlayerName();
        if (selfName.isBlank()) {
            return "";
        }
        return SQUAD_BY_PLAYER.getOrDefault(selfName, "");
    }

    private static TeamToken resolveSelfTeamToken() {
        String selfName = resolveSelfPlayerName();
        if (selfName.isBlank()) {
            return TeamToken.UNKNOWN;
        }
        return TEAM_BY_PLAYER.getOrDefault(selfName, TeamToken.UNKNOWN);
    }

    private static String normalizePlayerName(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
    }

    private enum TeamToken {
        CAMP_A,
        CAMP_B,
        UNKNOWN
    }
}
