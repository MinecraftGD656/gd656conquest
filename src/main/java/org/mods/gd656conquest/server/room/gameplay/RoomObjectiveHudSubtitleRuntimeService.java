package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomHudSubtitlePacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RoomObjectiveHudSubtitleRuntimeService {
    private final RoomHudSubtitleBroadcastService subtitleBroadcastService = new RoomHudSubtitleBroadcastService();

    public void initialize(
        RoomObjectiveHudSubtitleRuntimeState subtitleState,
        Map<String, RoomTeamId> ownerByCode,
        Map<String, RoomTeamId> drivingByCode
    ) {
        if (subtitleState == null) {
            return;
        }
        subtitleState.clear();
        if (ownerByCode == null || drivingByCode == null) {
            return;
        }
        for (Map.Entry<String, RoomTeamId> entry : ownerByCode.entrySet()) {
            String code = normalizeCode(entry.getKey());
            RoomTeamId owner = entry.getValue();
            subtitleState.getLastOwnerTeamByCode().put(code, owner);
            subtitleState.getLastDrivingTeamByCode().put(code, drivingByCode.get(code));
            if (owner == RoomTeamId.CAMP_A || owner == RoomTeamId.CAMP_B) {
                subtitleState.getLastNonNeutralOwnerTeamByCode().put(code, owner);
            }
        }
        subtitleState.setLastMajorityOwnerTeam(resolveMajorityOwnerTeam(ownerByCode));
        subtitleState.setLastAllOwnedTeam(resolveAllOwnedTeam(ownerByCode));
    }

    public void tickObjectiveEvents(
        RoomObjectiveHudSubtitleRuntimeState subtitleState,
        RunningRuntimeState runningState,
        List<RoomObjectiveSubtitleEntry> objectives,
        Map<String, RoomTeamId> ownerByCode,
        Map<String, RoomTeamId> drivingByCode
    ) {
        if (subtitleState == null || runningState == null || ownerByCode == null || drivingByCode == null) {
            return;
        }
        prunePlayerState(subtitleState, runningState);
        List<RoomObjectiveSubtitleEntry> orderedObjectives = orderedObjectives(objectives, ownerByCode);
        emitDrivingEvents(subtitleState, runningState, orderedObjectives, ownerByCode, drivingByCode);
        emitOwnershipEvents(subtitleState, runningState, orderedObjectives, ownerByCode);
        emitControlEvents(subtitleState, runningState, ownerByCode);
    }

    public void clearPlayer(RoomObjectiveHudSubtitleRuntimeState subtitleState, UUID playerId, ServerPlayer player) {
        if (subtitleState == null || playerId == null) {
            return;
        }
        subtitleState.getLastPersistentKeyByPlayer().remove(playerId);
        subtitleState.getLastPersistentTextByPlayer().remove(playerId);
        if (player != null) {
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.reset());
        }
    }

    public void resetAllPlayers(RoomObjectiveHudSubtitleRuntimeState subtitleState, RunningRuntimeState runningState) {
        if (subtitleState == null) {
            return;
        }
        if (runningState != null) {
            for (UUID playerId : List.copyOf(runningState.getRoomPlayers())) {
                clearPlayer(subtitleState, playerId, resolvePlayer(playerId));
            }
        }
        subtitleState.clear();
    }

    public void prunePlayerState(RoomObjectiveHudSubtitleRuntimeState subtitleState, RunningRuntimeState runningState) {
        if (subtitleState == null || runningState == null) {
            return;
        }
        Set<UUID> activePlayers = runningState.getRoomPlayers();
        subtitleState.getLastPersistentKeyByPlayer().keySet().removeIf(playerId -> !activePlayers.contains(playerId));
        subtitleState.getLastPersistentTextByPlayer().keySet().removeIf(playerId -> !activePlayers.contains(playerId));
    }

    private void emitDrivingEvents(
        RoomObjectiveHudSubtitleRuntimeState subtitleState,
        RunningRuntimeState runningState,
        List<RoomObjectiveSubtitleEntry> objectives,
        Map<String, RoomTeamId> ownerByCode,
        Map<String, RoomTeamId> drivingByCode
    ) {
        for (RoomObjectiveSubtitleEntry objective : objectives) {
            String code = normalizeCode(objective.code());
            RoomTeamId currentDriving = drivingByCode.get(code);
            RoomTeamId previousDriving = subtitleState.getLastDrivingTeamByCode().put(code, currentDriving);
            if (currentDriving == previousDriving || (currentDriving != RoomTeamId.CAMP_A && currentDriving != RoomTeamId.CAMP_B)) {
                continue;
            }
            RoomTeamId owner = ownerByCode.get(code);
            if (owner == currentDriving) {
                continue;
            }
            String displayName = objective.displayName();
            broadcastOnce(
                runningState,
                buildOnceKey("attack", code, currentDriving),
                currentDriving,
                "我军正在进攻据点 " + displayName,
                "敌军正在进攻据点 " + displayName
            );
        }
    }

    private void emitOwnershipEvents(
        RoomObjectiveHudSubtitleRuntimeState subtitleState,
        RunningRuntimeState runningState,
        List<RoomObjectiveSubtitleEntry> objectives,
        Map<String, RoomTeamId> ownerByCode
    ) {
        for (RoomObjectiveSubtitleEntry objective : objectives) {
            String code = normalizeCode(objective.code());
            RoomTeamId currentOwner = ownerByCode.get(code);
            RoomTeamId previousOwner = subtitleState.getLastOwnerTeamByCode().put(code, currentOwner);
            if (currentOwner == previousOwner) {
                continue;
            }
            if (currentOwner != RoomTeamId.CAMP_A && currentOwner != RoomTeamId.CAMP_B) {
                continue;
            }
            RoomTeamId previousNonNeutralOwner = subtitleState.getLastNonNeutralOwnerTeamByCode().get(code);
            subtitleState.getLastNonNeutralOwnerTeamByCode().put(code, currentOwner);
            String displayName = objective.displayName();
            if (previousNonNeutralOwner != null && previousNonNeutralOwner != currentOwner) {
                broadcastOnce(
                    runningState,
                    buildOnceKey("recapture", code, currentOwner),
                    currentOwner,
                    "我军已从敌军手中夺下据点 " + displayName,
                    "敌军从我军手中夺取了据点 " + displayName
                );
                continue;
            }
            broadcastOnce(
                runningState,
                buildOnceKey("capture", code, currentOwner),
                currentOwner,
                "我军已经占领了据点 " + displayName,
                "敌军已经占领了据点 " + displayName
            );
        }
    }

    private void emitControlEvents(
        RoomObjectiveHudSubtitleRuntimeState subtitleState,
        RunningRuntimeState runningState,
        Map<String, RoomTeamId> ownerByCode
    ) {
        RoomTeamId allOwnedTeam = resolveAllOwnedTeam(ownerByCode);
        if (allOwnedTeam != null && allOwnedTeam != subtitleState.getLastAllOwnedTeam()) {
            broadcastOnce(
                runningState,
                buildOnceKey("all_owned", "global", allOwnedTeam),
                allOwnedTeam,
                "我军控制了全部据点",
                "敌军控制了全部据点"
            );
        }
        subtitleState.setLastAllOwnedTeam(allOwnedTeam);

        RoomTeamId majorityOwnerTeam = allOwnedTeam == null ? resolveMajorityOwnerTeam(ownerByCode) : null;
        if (majorityOwnerTeam != null && majorityOwnerTeam != subtitleState.getLastMajorityOwnerTeam()) {
            broadcastOnce(
                runningState,
                buildOnceKey("majority", "global", majorityOwnerTeam),
                majorityOwnerTeam,
                "我军控制了更多的据点",
                "敌军控制了更多的据点"
            );
        }
        subtitleState.setLastMajorityOwnerTeam(majorityOwnerTeam);
    }

    private RoomTeamId resolveAllOwnedTeam(Map<String, RoomTeamId> ownerByCode) {
        int total = 0;
        int campAOwned = 0;
        int campBOwned = 0;
        for (RoomTeamId owner : ownerByCode.values()) {
            total++;
            if (owner == RoomTeamId.CAMP_A) {
                campAOwned++;
            } else if (owner == RoomTeamId.CAMP_B) {
                campBOwned++;
            }
        }
        if (total <= 0) {
            return null;
        }
        if (campAOwned == total) {
            return RoomTeamId.CAMP_A;
        }
        if (campBOwned == total) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private RoomTeamId resolveMajorityOwnerTeam(Map<String, RoomTeamId> ownerByCode) {
        int total = 0;
        int campAOwned = 0;
        int campBOwned = 0;
        for (RoomTeamId owner : ownerByCode.values()) {
            total++;
            if (owner == RoomTeamId.CAMP_A) {
                campAOwned++;
            } else if (owner == RoomTeamId.CAMP_B) {
                campBOwned++;
            }
        }
        if (total <= 0) {
            return null;
        }
        double campARatio = campAOwned / (double) total;
        double campBRatio = campBOwned / (double) total;
        if (campARatio >= 0.75D && campARatio > campBRatio) {
            return RoomTeamId.CAMP_A;
        }
        if (campBRatio >= 0.75D && campBRatio > campARatio) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private void broadcastOnce(
        RunningRuntimeState runningState,
        String key,
        RoomTeamId alliedTeam,
        String alliedText,
        String enemyText
    ) {
        subtitleBroadcastService.broadcastPerspectiveOnce(runningState, key, alliedTeam, alliedText, enemyText);
    }

    private List<RoomObjectiveSubtitleEntry> orderedObjectives(
        List<RoomObjectiveSubtitleEntry> objectives,
        Map<String, RoomTeamId> ownerByCode
    ) {
        List<RoomObjectiveSubtitleEntry> ordered = new ArrayList<>();
        if (objectives != null) {
            for (RoomObjectiveSubtitleEntry objective : objectives) {
                if (objective == null) {
                    continue;
                }
                String code = normalizeCode(objective.code());
                if (code.isEmpty()) {
                    continue;
                }
                ordered.add(new RoomObjectiveSubtitleEntry(code, objective.displayName()));
            }
        }
        for (String code : ownerByCode.keySet()) {
            String normalizedCode = normalizeCode(code);
            boolean exists = ordered.stream().anyMatch(entry -> entry.code().equals(normalizedCode));
            if (!exists) {
                ordered.add(new RoomObjectiveSubtitleEntry(normalizedCode, normalizedCode));
            }
        }
        return ordered;
    }

    private String buildOnceKey(String category, String suffix, RoomTeamId teamId) {
        return category + ":" + normalizeCode(suffix) + ":" + (teamId == null ? "-" : teamId.name());
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ServerPlayer resolvePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
    }

    public record RoomObjectiveSubtitleEntry(String code, String displayName) {
        public RoomObjectiveSubtitleEntry {
            code = code == null ? "" : code.trim();
            displayName = displayName == null || displayName.isBlank() ? code : displayName;
        }
    }
}
