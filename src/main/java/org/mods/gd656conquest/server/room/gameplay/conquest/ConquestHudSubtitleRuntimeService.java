package org.mods.gd656conquest.server.room.gameplay.conquest;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomHudSubtitlePacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomHudSubtitleBroadcastService;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveHudSubtitleRuntimeService;
import org.mods.gd656conquest.server.room.gameplay.RoomObjectiveHudSubtitleRuntimeService.RoomObjectiveSubtitleEntry;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ConquestHudSubtitleRuntimeService {
    private final RoomObjectiveHudSubtitleRuntimeService objectiveSubtitleRuntimeService = new RoomObjectiveHudSubtitleRuntimeService();
    private final RoomHudSubtitleBroadcastService subtitleBroadcastService = new RoomHudSubtitleBroadcastService();

    public void initialize(
        ConquestHudSubtitleRuntimeState subtitleState,
        RoomDefinition room,
        ConquestCaptureRuntimeState captureState,
        ConquestScoreRuntimeState scoreState
    ) {
        if (subtitleState == null) {
            return;
        }
        subtitleState.clear();
        if (room == null || captureState == null || scoreState == null) {
            return;
        }
        objectiveSubtitleRuntimeService.initialize(
            subtitleState,
            captureState.getOwnerByCode(),
            captureState.getDrivingTeamByCode()
        );
        subtitleState.getLastScoreStageByTeam().put(RoomTeamId.CAMP_A, resolveScoreStage(room, scoreState, RoomTeamId.CAMP_A));
        subtitleState.getLastScoreStageByTeam().put(RoomTeamId.CAMP_B, resolveScoreStage(room, scoreState, RoomTeamId.CAMP_B));
        subtitleState.getLastTroopStageByTeam().put(RoomTeamId.CAMP_A, resolveTroopStage(room, scoreState, RoomTeamId.CAMP_A));
        subtitleState.getLastTroopStageByTeam().put(RoomTeamId.CAMP_B, resolveTroopStage(room, scoreState, RoomTeamId.CAMP_B));
    }

    public void tickRunning(
        ConquestHudSubtitleRuntimeState subtitleState,
        RoomDefinition room,
        RunningRuntimeState runningState,
        ConquestCaptureRuntimeState captureState,
        ConquestScoreRuntimeState scoreState,
        long gameTick
    ) {
        if (subtitleState == null || room == null || runningState == null || captureState == null || scoreState == null) {
            return;
        }
        objectiveSubtitleRuntimeService.tickObjectiveEvents(
            subtitleState,
            runningState,
            orderedActiveObjectives(room, captureState),
            captureState.getOwnerByCode(),
            captureState.getDrivingTeamByCode()
        );
        emitThresholdEvents(subtitleState, room, runningState, scoreState);
        syncPersistentState(subtitleState, room, runningState, scoreState, gameTick, false);
    }

    public void syncEnding(
        ConquestHudSubtitleRuntimeState subtitleState,
        RoomDefinition room,
        RunningRuntimeState runningState,
        ConquestScoreRuntimeState scoreState,
        long gameTick
    ) {
        if (subtitleState == null || room == null || runningState == null || scoreState == null) {
            return;
        }
        objectiveSubtitleRuntimeService.prunePlayerState(subtitleState, runningState);
        syncPersistentState(subtitleState, room, runningState, scoreState, gameTick, true);
    }

    public void clearPlayer(ConquestHudSubtitleRuntimeState subtitleState, UUID playerId, ServerPlayer player) {
        objectiveSubtitleRuntimeService.clearPlayer(subtitleState, playerId, player);
    }

    public void resetAllPlayers(ConquestHudSubtitleRuntimeState subtitleState, RunningRuntimeState runningState) {
        objectiveSubtitleRuntimeService.resetAllPlayers(subtitleState, runningState);
    }

    private void emitThresholdEvents(
        ConquestHudSubtitleRuntimeState subtitleState,
        RoomDefinition room,
        RunningRuntimeState runningState,
        ConquestScoreRuntimeState scoreState
    ) {
        if (isScoreTargetMode(room)) {
            emitScoreThresholdEvents(subtitleState, room, runningState, scoreState, RoomTeamId.CAMP_A);
            emitScoreThresholdEvents(subtitleState, room, runningState, scoreState, RoomTeamId.CAMP_B);
            return;
        }
        if (isTroopMode(room)) {
            emitTroopThresholdEvents(subtitleState, room, runningState, scoreState, RoomTeamId.CAMP_A);
            emitTroopThresholdEvents(subtitleState, room, runningState, scoreState, RoomTeamId.CAMP_B);
        }
    }

    private void emitScoreThresholdEvents(
        ConquestHudSubtitleRuntimeState subtitleState,
        RoomDefinition room,
        RunningRuntimeState runningState,
        ConquestScoreRuntimeState scoreState,
        RoomTeamId thresholdTeam
    ) {
        int previousStage = subtitleState.getLastScoreStageByTeam().getOrDefault(thresholdTeam, 0);
        int currentStage = resolveScoreStage(room, scoreState, thresholdTeam);
        if (currentStage <= previousStage) {
            subtitleState.getLastScoreStageByTeam().put(thresholdTeam, currentStage);
            return;
        }
        for (int stage = previousStage + 1; stage <= currentStage; stage++) {
            if (stage == 1) {
                broadcastOnce(
                    runningState,
                    buildOnceKey("score_50", thresholdTeam.name().toLowerCase(Locale.ROOT), thresholdTeam),
                    thresholdTeam,
                    "我方剩余50%分数",
                    "敌方剩余50%分数"
                );
            } else if (stage == 2) {
                broadcastOnce(
                    runningState,
                    buildOnceKey("score_20", thresholdTeam.name().toLowerCase(Locale.ROOT), thresholdTeam),
                    thresholdTeam,
                    "我方仅剩20%分数",
                    "敌方仅剩20%分数"
                );
            }
        }
        subtitleState.getLastScoreStageByTeam().put(thresholdTeam, currentStage);
    }

    private void emitTroopThresholdEvents(
        ConquestHudSubtitleRuntimeState subtitleState,
        RoomDefinition room,
        RunningRuntimeState runningState,
        ConquestScoreRuntimeState scoreState,
        RoomTeamId thresholdTeam
    ) {
        int previousStage = subtitleState.getLastTroopStageByTeam().getOrDefault(thresholdTeam, 0);
        int currentStage = resolveTroopStage(room, scoreState, thresholdTeam);
        if (currentStage <= previousStage) {
            subtitleState.getLastTroopStageByTeam().put(thresholdTeam, currentStage);
            return;
        }
        for (int stage = previousStage + 1; stage <= currentStage; stage++) {
            if (stage == 1) {
                broadcastOnce(
                    runningState,
                    buildOnceKey("troop_50", thresholdTeam.name().toLowerCase(Locale.ROOT), thresholdTeam),
                    thresholdTeam,
                    "我方兵力还剩50%",
                    "敌方兵力还剩50%"
                );
            } else if (stage == 2) {
                broadcastOnce(
                    runningState,
                    buildOnceKey("troop_20", thresholdTeam.name().toLowerCase(Locale.ROOT), thresholdTeam),
                    thresholdTeam,
                    "我方兵力仅剩20%",
                    "敌方兵力仅剩20%"
                );
            }
        }
        subtitleState.getLastTroopStageByTeam().put(thresholdTeam, currentStage);
    }

    private void syncPersistentState(
        ConquestHudSubtitleRuntimeState subtitleState,
        RoomDefinition room,
        RunningRuntimeState runningState,
        ConquestScoreRuntimeState scoreState,
        long gameTick,
        boolean forceMatchEnded
    ) {
        for (UUID playerId : List.copyOf(runningState.getRoomPlayers())) {
            ServerPlayer player = resolvePlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomTeamId selfTeam = runningState.getRoomTeams().get(playerId);
            PersistentSubtitle desired = resolvePersistentSubtitle(room, scoreState, selfTeam, gameTick, forceMatchEnded);
            String previousKey = subtitleState.getLastPersistentKeyByPlayer().getOrDefault(playerId, "");
            String previousText = subtitleState.getLastPersistentTextByPlayer().getOrDefault(playerId, "");
            if (desired == null) {
                if (!previousKey.isEmpty() || !previousText.isEmpty()) {
                    NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.clearPersistent(previousKey));
                }
                subtitleState.getLastPersistentKeyByPlayer().remove(playerId);
                subtitleState.getLastPersistentTextByPlayer().remove(playerId);
                continue;
            }
            if (desired.key().equals(previousKey) && desired.text().equals(previousText)) {
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.setPersistent(desired.key(), desired.text()));
            subtitleState.getLastPersistentKeyByPlayer().put(playerId, desired.key());
            subtitleState.getLastPersistentTextByPlayer().put(playerId, desired.text());
        }
    }

    private PersistentSubtitle resolvePersistentSubtitle(
        RoomDefinition room,
        ConquestScoreRuntimeState scoreState,
        RoomTeamId selfTeam,
        long gameTick,
        boolean forceMatchEnded
    ) {
        if (selfTeam != RoomTeamId.CAMP_A && selfTeam != RoomTeamId.CAMP_B) {
            if (forceMatchEnded) {
                return new PersistentSubtitle("match_end", "对局已结束");
            }
            return null;
        }
        if (forceMatchEnded) {
            return new PersistentSubtitle("match_end", "对局已结束");
        }
        if (scoreState.getFullCaptureEndTick() > gameTick && scoreState.getFullCaptureOwnerTeam() != null) {
            RoomTeamId ownerTeam = scoreState.getFullCaptureOwnerTeam();
            if (selfTeam == ownerTeam) {
                return new PersistentSubtitle("full_capture_hold", "坚守所有据点");
            }
            return new PersistentSubtitle("full_capture_break", "夺回敌军手中的任一据点");
        }
        RoomTeamId scoreNearEndTeam = resolveScoreNearEndTeam(room, scoreState);
        if (scoreNearEndTeam != null) {
            if (selfTeam == scoreNearEndTeam) {
                return new PersistentSubtitle("score_final_self", "我军即将达到目标分数");
            }
            return new PersistentSubtitle("score_final_enemy", "敌军即将完成目标分数");
        }
        RoomTeamId troopNearEndTeam = resolveTroopNearEndTeam(room, scoreState);
        if (troopNearEndTeam != null) {
            if (selfTeam == troopNearEndTeam) {
                return new PersistentSubtitle("troop_final_self", "我军兵力即将耗尽");
            }
            return new PersistentSubtitle("troop_final_enemy", "敌军兵力即将耗尽");
        }
        return null;
    }

    private RoomTeamId resolveScoreNearEndTeam(RoomDefinition room, ConquestScoreRuntimeState scoreState) {
        if (!isScoreTargetMode(room)) {
            return null;
        }
        double campAProgress = resolveScoreProgress(room, scoreState, RoomTeamId.CAMP_A);
        double campBProgress = resolveScoreProgress(room, scoreState, RoomTeamId.CAMP_B);
        if (campAProgress < 0.8D && campBProgress < 0.8D) {
            return null;
        }
        if (campAProgress > campBProgress && campAProgress >= 0.8D) {
            return RoomTeamId.CAMP_A;
        }
        if (campBProgress > campAProgress && campBProgress >= 0.8D) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private RoomTeamId resolveTroopNearEndTeam(RoomDefinition room, ConquestScoreRuntimeState scoreState) {
        if (!isTroopMode(room)) {
            return null;
        }
        double campARemainRatio = resolveTroopRemainRatio(room, scoreState, RoomTeamId.CAMP_A);
        double campBRemainRatio = resolveTroopRemainRatio(room, scoreState, RoomTeamId.CAMP_B);
        boolean campANearEnd = campARemainRatio <= 0.2D;
        boolean campBNearEnd = campBRemainRatio <= 0.2D;
        if (!campANearEnd && !campBNearEnd) {
            return null;
        }
        if (campANearEnd && !campBNearEnd) {
            return RoomTeamId.CAMP_A;
        }
        if (campBNearEnd && !campANearEnd) {
            return RoomTeamId.CAMP_B;
        }
        if (campARemainRatio < campBRemainRatio) {
            return RoomTeamId.CAMP_A;
        }
        if (campBRemainRatio < campARemainRatio) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private int resolveScoreStage(RoomDefinition room, ConquestScoreRuntimeState scoreState, RoomTeamId teamId) {
        double progress = resolveScoreProgress(room, scoreState, teamId);
        if (progress >= 0.8D) {
            return 2;
        }
        if (progress >= 0.5D) {
            return 1;
        }
        return 0;
    }

    private int resolveTroopStage(RoomDefinition room, ConquestScoreRuntimeState scoreState, RoomTeamId teamId) {
        double remainRatio = resolveTroopRemainRatio(room, scoreState, teamId);
        if (remainRatio <= 0.2D) {
            return 2;
        }
        if (remainRatio <= 0.5D) {
            return 1;
        }
        return 0;
    }

    private double resolveScoreProgress(RoomDefinition room, ConquestScoreRuntimeState scoreState, RoomTeamId teamId) {
        int goal = Math.max(1, captureConfig(room).targetScore());
        int score = teamId == RoomTeamId.CAMP_A ? scoreState.getCampAScore() : scoreState.getCampBScore();
        return score / (double) goal;
    }

    private double resolveTroopRemainRatio(RoomDefinition room, ConquestScoreRuntimeState scoreState, RoomTeamId teamId) {
        int goal = Math.max(1, teamId == RoomTeamId.CAMP_A ? captureConfig(room).campAInitialTroops() : captureConfig(room).campBInitialTroops());
        int remain = teamId == RoomTeamId.CAMP_A ? scoreState.getCampAScore() : scoreState.getCampBScore();
        return remain / (double) goal;
    }

    private boolean isScoreTargetMode(RoomDefinition room) {
        return room != null && "SCORE_TARGET".equalsIgnoreCase(captureConfig(room).scoreMethod());
    }

    private boolean isTroopMode(RoomDefinition room) {
        return room != null && "TROOP_DEPLETION".equalsIgnoreCase(captureConfig(room).scoreMethod());
    }

    private List<RoomObjectiveSubtitleEntry> orderedActiveObjectives(RoomDefinition room, ConquestCaptureRuntimeState captureState) {
        List<RoomObjectiveSubtitleEntry> ordered = new ArrayList<>();
        if (room != null) {
            for (RoomCapturePointDefinition point : RoomObjectiveSupport.activePoints(captureConfig(room))) {
                String code = RoomObjectiveSupport.normalizeCode(point.code());
                if (!code.isEmpty() && captureState.getOwnerByCode().containsKey(code)) {
                    ordered.add(new RoomObjectiveSubtitleEntry(code, resolveCaptureDisplayName(room, code)));
                }
            }
        }
        for (String code : captureState.getOwnerByCode().keySet()) {
            boolean exists = ordered.stream().anyMatch(entry -> entry.code().equals(code));
            if (!exists) {
                ordered.add(new RoomObjectiveSubtitleEntry(code, resolveCaptureDisplayName(room, code)));
            }
        }
        return ordered;
    }

    private String resolveCaptureDisplayName(RoomDefinition room, String code) {
        if (room == null) {
            return code;
        }
        return captureConfig(room).findPoint(code)
            .map(RoomCapturePointDefinition::displayName)
            .filter(value -> value != null && !value.isBlank())
            .orElse(code);
    }

    private ServerPlayer resolvePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
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

    private RoomCaptureConfig captureConfig(RoomDefinition room) {
        return room.conquestConfig().captureConfig();
    }

    private String buildOnceKey(String prefix, String teamToken, RoomTeamId teamId) {
        String safePrefix = prefix == null ? "event" : prefix;
        String safeTeamToken = teamToken == null || teamToken.isBlank()
            ? (teamId == null ? "neutral" : teamId.name().toLowerCase(Locale.ROOT))
            : teamToken;
        return safePrefix + "_" + safeTeamToken;
    }

    private record PersistentSubtitle(String key, String text) {
    }
}
