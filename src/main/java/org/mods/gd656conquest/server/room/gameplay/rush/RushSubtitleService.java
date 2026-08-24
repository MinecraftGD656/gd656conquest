package org.mods.gd656conquest.server.room.gameplay.rush;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomHudSubtitleBroadcastService;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;
import java.util.Objects;

final class RushSubtitleService {
    private final RunningRuntimeState runningState;
    private final RoomHudSubtitleBroadcastService subtitleBroadcastService = new RoomHudSubtitleBroadcastService();

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

    void onBackToWallStart(long gameTick) {
        // 同时请求两条独立字幕(与 Gunmaster 开局字幕一致): 客户端按 2 秒队列间隔依次显示
        broadcastPerspective(
            "rush_back_to_wall_1_" + gameTick,
            RoomTeamId.CAMP_A,
            "支援抵达，掌握机会，摧毁全部通讯设施！",
            "我们受到了敌军大规模还击，必须死守全部通讯设施！"
        );
        broadcastPerspective(
            "rush_back_to_wall_2_" + gameTick,
            RoomTeamId.CAMP_A,
            "我们的支援兵力只够维持两分钟，务必全力推进！",
            "在最后两分钟内保证炸弹安全"
        );
    }

    void onBurnBoatsStart(long gameTick) {
        broadcastPerspective(
            "rush_burn_boats_1_" + gameTick,
            RoomTeamId.CAMP_A,
            "我军已没有退路，与炸弹同归于尽！",
            "敌军的进攻仍然猛烈！全力拆除炸弹！"
        );
        broadcastPerspective(
            "rush_burn_boats_2_" + gameTick,
            RoomTeamId.CAMP_A,
            "坚守全部炸弹，我们已无力再次安放！",
            "拆毁任意一个炸弹，粉碎敌军攻势！"
        );
    }

    void onBurnBoatsSucceed(long gameTick) {
        broadcastPerspective(
            "rush_burn_boats_success_" + gameTick,
            RoomTeamId.CAMP_B,
            "敌军破釜沉舟成功",
            "破釜沉舟成功！进攻方获胜"
        );
    }

    void onDesperateFail(long gameTick) {
        broadcastPerspective(
            "rush_desperate_fail_" + gameTick,
            RoomTeamId.CAMP_B,
            "成功守住防线！",
            "背水一战失败"
        );
    }

    private void broadcastPerspective(String key, RoomTeamId alliedTeam, String alliedText, String enemyText) {
        subtitleBroadcastService.broadcastPerspectiveOnce(runningState, key, alliedTeam, alliedText, enemyText);
    }

    private void broadcastGlobal(String key, String text) {
        subtitleBroadcastService.broadcastGlobalOnce(runningState, key, text);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String stageLabel(int stageIndex) {
        return stageIndex < 1 || stageIndex > 6 ? "?" : String.valueOf((char) ('A' + stageIndex - 1));
    }
}
