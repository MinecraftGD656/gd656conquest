package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.mods.gd656conquest.network.packet.RoomTeamMirrorSyncPacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public record SquadMember(String playerName, String classKey) {}
    public record SquadSnapshot(String roomId, String squadLabel, List<SquadMember> members) {
        public SquadSnapshot { members = List.copyOf(members); }
    }
    public record TeamSnapshot(String roomId, String campADisplayName, String campBDisplayName, int campAPlayerCount, int campBPlayerCount) {}

    public static TeamSnapshot teamSnapshot() {
        return new TeamSnapshot(lastRoomId, campADisplayName, campBDisplayName, campAPlayerCount, campBPlayerCount);
    }

    /** Immutable roster scoped to both camp and squad, including members outside entity range. */
    public static SquadSnapshot squadSnapshot(String playerName) {
        String self = normalizePlayerName(playerName);
        TeamToken team = TEAM_BY_PLAYER.getOrDefault(self, TeamToken.UNKNOWN);
        String label = SQUAD_BY_PLAYER.getOrDefault(self, "");
        if (lastRoomId.isBlank() || team == TeamToken.UNKNOWN || label.isBlank()) {
            return new SquadSnapshot(lastRoomId, "", List.of());
        }
        List<SquadMember> members = TEAM_BY_PLAYER.entrySet().stream()
            .filter(entry -> entry.getValue() == team && label.equals(SQUAD_BY_PLAYER.get(entry.getKey())))
            .map(entry -> new SquadMember(entry.getKey(), CLASS_BY_PLAYER.getOrDefault(entry.getKey(), "")))
            .sorted(java.util.Comparator.comparing(SquadMember::playerName))
            .toList();
        return new SquadSnapshot(lastRoomId, label, members);
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
        // Room snapshots describe UI relations. Native team packets own Scoreboard teams and membership.
        if (!active || roomId == null || roomId.isBlank() || "-".equals(roomId)) {
            reset();
            return;
        }
        String normalizedRoomId = roomId.trim().toLowerCase(Locale.ROOT);
        ClientRoomTeamMirror.campADisplayName = campADisplayName == null || campADisplayName.isBlank() ? "CampA" : campADisplayName;
        ClientRoomTeamMirror.campBDisplayName = campBDisplayName == null || campBDisplayName.isBlank() ? "CampB" : campBDisplayName;
        ClientRoomTeamMirror.campAPlayerCount = campAPlayers == null ? 0 : campAPlayers.size();
        ClientRoomTeamMirror.campBPlayerCount = campBPlayers == null ? 0 : campBPlayers.size();
        rebuildMirrorState(campAPlayers == null ? List.of() : campAPlayers, campBPlayers == null ? List.of() : campBPlayers, squadEntries, classEntries);
        lastRoomId = normalizedRoomId;
    }

    public static void reset() {
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
