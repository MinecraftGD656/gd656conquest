package org.mods.gd656conquest.server.room.gameplay.rush;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomHudSubtitleBroadcastService;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;
import java.util.Objects;

final class RushSubtitleService {
    private final RunningRuntimeState runningState;
    private final RoomHudSubtitleBroadcastService broadcastService = new RoomHudSubtitleBroadcastService();

    private int lastTrackedStageIndex = -1;
    private String lastRemainingCode = "";
    private int lastArmedCount = -1;
    private boolean lowTroopsWarningSent;

    RushSubtitleService(RunningRuntimeState runningState) {
        this.runningState = Objects.requireNonNull(runningState, "runningState");
    }

    void reset() {
        lastTrackedStageIndex = -1;
        lastRemainingCode = "";
        lastArmedCount = -1;
        lowTroopsWarningSent = false;
    }

    void onBombPlantStarted(String code, long gameTick) {
        broadcastPerspective(
            "rush_plant_started_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_A,
            "我军正在敌军通讯设施 " + normalize(code) + " 安放炸弹",
            "敌军正在我军通讯设施 " + normalize(code) + " 安放炸弹"
        );
    }

    void onBombPlantInterrupted(String code, long gameTick) {
        broadcastPerspective(
            "rush_plant_interrupted_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_A,
            "我军已中断在通讯设施 " + normalize(code) + " 的安放",
            "敌军已中断在通讯设施 " + normalize(code) + " 的安放"
        );
    }

    void onBombDefuseStarted(String code, long gameTick) {
        broadcastPerspective(
            "rush_defuse_started_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_B,
            "我军正在拆除通讯设施 " + normalize(code) + " 上的炸弹",
            "敌军正在拆除通讯设施 " + normalize(code) + " 上的炸弹"
        );
    }

    void onBombDefuseInterrupted(String code, long gameTick) {
        broadcastPerspective(
            "rush_defuse_interrupted_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_B,
            "我军已中断对通讯设施 " + normalize(code) + " 的拆除",
            "敌军已中断对通讯设施 " + normalize(code) + " 的拆除"
        );
    }

    void onBombPlanted(String code, long gameTick) {
        broadcastPerspective(
            "rush_planted_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_A,
            "我军已在敌军通讯设施 " + normalize(code) + " 安放炸弹",
            "敌军已在我军通讯设施 " + normalize(code) + " 安放炸弹"
        );
    }

    void onBombDefused(String code, long gameTick) {
        broadcastPerspective(
            "rush_defused_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_B,
            "我军已拆除通讯设施 " + normalize(code) + " 上的炸弹",
            "敌军已拆除通讯设施 " + normalize(code) + " 上的炸弹"
        );
    }

    void onBombFinalCountdown(String code, long gameTick) {
        broadcastPerspective(
            "rush_final_countdown_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_A,
            "我军需死守通讯设施 " + normalize(code) + " 的炸弹",
            "我军需立刻拆除通讯设施 " + normalize(code) + " 的炸弹"
        );
    }

    void onBombExploded(String code, long gameTick) {
        broadcastPerspective(
            "rush_exploded_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_A,
            "我军已炸毁敌军通讯设施 " + normalize(code),
            "敌军已炸毁我军通讯设施 " + normalize(code)
        );
    }

    void onStageDestroyed(int stageIndex, long gameTick) {
        String stageLabel = stageLabel(stageIndex);
        broadcastGlobal("rush_stage_destroyed_" + stageLabel + "_" + gameTick, "阶段 " + stageLabel + " 的全部通讯设施已被摧毁");
    }

    void onStageTimeout(long gameTick) {
        broadcastGlobal("rush_stage_timeout_" + gameTick, "当前阶段时间结束，本局平局");
    }

    void onRetreat(int stageIndex, String code, long gameTick) {
        String stageLabel = stageLabel(stageIndex);
        broadcastPerspective(
            "rush_retreat_" + normalize(code) + "_" + gameTick,
            RoomTeamId.CAMP_A,
            "阶段 " + stageLabel + " 已完成爆破，请清理区域内残余守军",
            "阶段 " + stageLabel + " 已失守，请立即撤离当前区域"
        );
    }

    void onAdvanceCountdown(int targetStageIndex, long gameTick) {
        String stageLabel = stageLabel(targetStageIndex);
        broadcastPerspective(
            "rush_stage_advance_" + targetStageIndex + "_" + gameTick,
            RoomTeamId.CAMP_A,
            "我军即将推进至阶段 " + stageLabel,
            "敌军即将推进至阶段 " + stageLabel
        );
    }

    void onAttackCompletedAllStages(long gameTick) {
        broadcastPerspective(
            "rush_match_end_attack_" + gameTick,
            RoomTeamId.CAMP_A,
            "我方已完成全部爆破目标",
            "敌军已完成全部爆破目标"
        );
    }

    void syncStagePressure(int stageIndex, List<String> remainingCodes, int armedCount, long gameTick) {
        if (stageIndex <= 0) {
            return;
        }
        if (lastTrackedStageIndex != stageIndex) {
            lastTrackedStageIndex = stageIndex;
            lastRemainingCode = "";
            lastArmedCount = armedCount;
            return;
        }
        if (remainingCodes != null && remainingCodes.size() == 1) {
            String onlyCode = normalize(remainingCodes.get(0));
            if (!onlyCode.equals(lastRemainingCode)) {
                broadcastPerspective(
                    "rush_last_objective_" + onlyCode + "_" + gameTick,
                    RoomTeamId.CAMP_A,
                    "当前阶段仅剩最后一处通讯设施 " + onlyCode,
                    "我军仅剩最后一处通讯设施 " + onlyCode
                );
                lastRemainingCode = onlyCode;
            }
        } else {
            lastRemainingCode = "";
        }
        if (armedCount >= 2 && lastArmedCount < 2) {
            broadcastPerspective(
                "rush_multi_armed_" + stageIndex + "_" + gameTick,
                RoomTeamId.CAMP_A,
                "当前阶段已有多处通讯设施被安装炸弹",
                "当前阶段已有多处我军通讯设施被安装炸弹"
            );
        }
        if (lastArmedCount > 0 && armedCount == 0 && remainingCodes != null && !remainingCodes.isEmpty()) {
            broadcastPerspective(
                "rush_all_safe_" + stageIndex + "_" + gameTick,
                RoomTeamId.CAMP_B,
                "当前阶段全部炸弹均处于安全状态",
                "当前阶段全部炸弹均已被拆除"
            );
        }
        lastArmedCount = armedCount;
    }

    void onAttackTroopsChanged(int attackTroops, int attackTroopCap, long gameTick) {
        if (lowTroopsWarningSent || attackTroopCap <= 0 || attackTroops <= 0) {
            return;
        }
        int threshold = Math.max(1, attackTroopCap / 5);
        if (attackTroops > threshold) {
            return;
        }
        lowTroopsWarningSent = true;
        broadcastPerspective(
            "rush_attack_troops_low_" + gameTick,
            RoomTeamId.CAMP_B,
            "敌军兵力即将耗尽",
            "我军兵力即将耗尽"
        );
    }

    private void broadcastPerspective(String key, RoomTeamId alliedTeam, String alliedText, String enemyText) {
        broadcastService.broadcastPerspectiveOnce(runningState, key, alliedTeam, alliedText, enemyText);
    }

    private void broadcastGlobal(String key, String text) {
        broadcastService.broadcastGlobalOnce(runningState, key, text);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String stageLabel(int stageIndex) {
        return stageIndex < 1 || stageIndex > 6 ? "?" : String.valueOf((char) ('A' + stageIndex - 1));
    }
}
