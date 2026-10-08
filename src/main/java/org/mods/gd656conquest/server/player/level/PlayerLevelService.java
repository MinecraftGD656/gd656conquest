package org.mods.gd656conquest.server.player.level;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLevelService {
    /** GD656Mods: 未配置时的等级上限(保持原有 100 级行为)。 */
    public static final int DEFAULT_MAX_LEVEL = 100;
    /** GD656Mods: 等级上限允许配置到的最大值。 */
    public static final int ABSOLUTE_MAX_LEVEL = 1024;
    private static final Set<UUID> XP_GRANT_BYPASS = ConcurrentHashMap.newKeySet();

    private PlayerLevelService() {
    }

    /** GD656Mods: 当前服务端配置的玩家等级上限(默认 100, 最高 1024)。 */
    public static int resolveMaxLevel(MinecraftServer server) {
        if (server == null) {
            return DEFAULT_MAX_LEVEL;
        }
        return ConquestDataManager.of(server).serverConfig().getMaxPlayerLevel();
    }

    public static int getEffectiveLevel(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.max(0, Math.min(resolveMaxLevel(player.server), player.experienceLevel));
    }

    public static void clampPlayerLevel(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        int maxLevel = resolveMaxLevel(player.server);
        PlayerDataStore handler = ConquestDataManager.of(player.server).playerData();
        PlayerDataModel data = handler.getPlayerData(player.getUUID());
        int maxTotal = getTotalExperienceForLevel(maxLevel, maxLevel);
        int total = Math.max(0, Math.min(maxTotal, data.getLevelExperience()));
        int level = Math.max(0, Math.min(maxLevel, getLevelForTotalExperience(total, maxLevel)));
        boolean changed = false;
        if (data.getLevelExperience() != total) {
            data.setLevelExperience(total);
            changed = true;
        }
        if (data.getLevel() != level) {
            data.setLevel(level);
            changed = true;
        }
        if (changed) {
            handler.savePlayerData(player.getUUID(), data);
        }
        applyVanillaExperienceBar(player, total);
    }

    public static void setPlayerLevel(ServerPlayer player, int level) {
        if (player == null || player.server == null) {
            return;
        }
        int maxLevel = resolveMaxLevel(player.server);
        int clamped = Math.max(0, Math.min(maxLevel, level));
        int total = getTotalExperienceForLevel(clamped, maxLevel);
        PlayerDataStore handler = ConquestDataManager.of(player.server).playerData();
        PlayerDataModel data = handler.getPlayerData(player.getUUID());
        data.setLevel(clamped);
        data.setLevelExperience(total);
        handler.savePlayerData(player.getUUID(), data);
        applyVanillaExperienceBar(player, total);
    }

    public static AwardResult awardMatchExperience(ServerPlayer player, MatchStats stats, boolean victory) {
        return awardMatchExperience(player, stats, victory ? MatchOutcome.WIN : MatchOutcome.LOSE);
    }

    public static AwardResult awardMatchExperience(ServerPlayer player, MatchStats stats, MatchOutcome outcome) {
        if (player == null || player.server == null || stats == null) {
            return AwardResult.empty();
        }
        PlayerDataStore handler = ConquestDataManager.of(player.server).playerData();
        PlayerDataModel data = handler.getPlayerData(player.getUUID());
        int maxLevel = resolveMaxLevel(player.server);
        int maxTotal = getTotalExperienceForLevel(maxLevel, maxLevel);
        int beforeTotal = Math.max(0, Math.min(maxTotal, data.getLevelExperience()));
        int beforeLevel = Math.max(0, Math.min(maxLevel, getLevelForTotalExperience(beforeTotal, maxLevel)));
        Formula formula = computeFormula(stats, outcome, resolveExperienceMultiplier(player));
        int delta = formula.total();
        int afterTotal = Math.max(0, Math.min(maxTotal, beforeTotal + delta));
        int gained = afterTotal - beforeTotal;
        int afterLevel = Math.max(0, Math.min(maxLevel, getLevelForTotalExperience(afterTotal, maxLevel)));
        if (afterTotal != beforeTotal || afterLevel != beforeLevel) {
            data.setLevel(afterLevel);
            data.setLevelExperience(afterTotal);
            handler.savePlayerData(player.getUUID(), data);
            applyVanillaExperienceBar(player, afterTotal);
        }
        return new AwardResult(gained, beforeLevel, afterLevel, formula);
    }

    public static AwardResult awardAndNotifyMatchExperience(ServerPlayer player, MatchStats stats, MatchOutcome outcome) {
        AwardResult result = awardMatchExperience(player, stats, outcome);
        if (player == null || result.formula() == null) {
            return result;
        }
        ServerMessageSender.system(player, "等级", Component.literal("本局经验提示: " + result.formula().toPlayerHintString()).withStyle(ChatFormatting.WHITE));
        ChatFormatting resultColor = result.levelAfter() > result.levelBefore() ? ChatFormatting.GREEN : ChatFormatting.AQUA;
        ServerMessageSender.system(player, "等级", Component.literal(String.format(
            Locale.ROOT,
            "本局经验 +%d，等级 %d -> %d",
            Math.max(0, result.gainedXp()),
            result.levelBefore(),
            result.levelAfter()
        )).withStyle(resultColor));
        return result;
    }

    public static Formula previewFormula(MatchStats stats, boolean victory) {
        return previewFormula(stats, victory ? MatchOutcome.WIN : MatchOutcome.LOSE);
    }

    /** GD656Mods: 带经验加成倍率的预览 —— 与 awardMatchExperience 用同一倍率, 保证预览与实际发放一致。 */
    public static Formula previewFormula(MatchStats stats, MatchOutcome outcome, double multiplier) {
        return computeFormula(stats, outcome, multiplier);
    }

    /** GD656Mods: 服务器配置的"玩家经验加成倍率"(默认 1.0)。 */
    private static double resolveExperienceMultiplier(ServerPlayer player) {
        if (player == null || player.server == null) {
            return 1.0D;
        }
        return ConquestDataManager.of(player.server).serverConfig().getPlayerExperienceMultiplier();
    }

    public static Formula previewFormula(MatchStats stats, MatchOutcome outcome) {
        if (stats == null) {
            return null;
        }
        // GD656Mods: 无倍率信息的旧入口按 1.0 处理; 需要与实际发放一致请用带 multiplier 的重载
        return computeFormula(stats, outcome, 1.0D);
    }

    public static boolean shouldBlockVanillaExperience(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (XP_GRANT_BYPASS.contains(player.getUUID())) {
            return false;
        }
        return player.experienceLevel >= 0;
    }

    public static int getCurrentTotalExperience(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.max(0, player.totalExperience);
    }

    public static void applyRawVanillaExperience(ServerPlayer player, int totalExp) {
        if (player == null) {
            return;
        }
        int total = Math.max(0, totalExp);
        int level = getUncappedLevelForTotalExperience(total);
        int intoLevel = Math.max(0, total - getTotalExperienceForLevelUncapped(level));
        int needed = Math.max(1, getXpNeededForNextLevelUncapped(level));
        float progress = Math.min(1.0F, intoLevel / (float) needed);
        runWithGrantBypass(player.getUUID(), () -> {
            player.totalExperience = total;
            player.experienceLevel = level;
            player.experienceProgress = progress;
            player.setExperiencePoints(intoLevel);
        });
    }

    public static int resolveLevelColorRgb(int level) {
        if (level >= 100) {
            return 0x8A2BE2;
        }
        if (level >= 75) {
            return 0xFF00FF;
        }
        if (level >= 65) {
            return 0xFF69B4;
        }
        if (level >= 55) {
            return 0xFF4040;
        }
        if (level >= 45) {
            return 0xFFD700;
        }
        if (level >= 35) {
            return 0xFF9A00;
        }
        if (level >= 25) {
            return 0x00D084;
        }
        if (level >= 15) {
            return 0x00C8FF;
        }
        if (level >= 6) {
            return 0x7A7A7A;
        }
        return 0x4A4A4A;
    }

    private static void applyVanillaExperienceBar(ServerPlayer player, int totalExp) {
        if (player == null) {
            return;
        }
        int maxLevel = resolveMaxLevel(player.server);
        int maxTotal = getTotalExperienceForLevel(maxLevel, maxLevel);
        int total = Math.max(0, Math.min(maxTotal, totalExp));
        int level = Math.max(0, Math.min(maxLevel, getLevelForTotalExperience(total, maxLevel)));
        int intoLevel = Math.max(0, total - getTotalExperienceForLevel(level, maxLevel));
        int needed = Math.max(1, getXpNeededForNextLevel(level, maxLevel));
        float progress = level >= maxLevel ? 0.0F : Math.min(1.0F, intoLevel / (float) needed);
        runWithGrantBypass(player.getUUID(), () -> {
            player.totalExperience = total;
            player.experienceLevel = level;
            player.experienceProgress = progress;
            player.setExperiencePoints(intoLevel);
        });
    }

    /**
     * GD656Mods: 结算公式。multiplier = 服务器配置的"玩家经验加成倍率"(默认 1.0)。
     *
     * <p>倍率只作用于最终结算值 —— 显示出来的 base / extra 保持原样,
     * 由结束总结里追加的「(&lt;活动介绍&gt; ×y)」后缀说明, 因此 total 与等级变化会按倍率缩放。
     */
    private static Formula computeFormula(MatchStats stats, MatchOutcome outcome, double multiplier) {
        int score = Math.max(0, stats.score());
        double base = score * 0.005D;
        // GD656Mods: 结果额外分(胜7/败4/平4) + 对局运行器提供的附加额外分(如枪神名次加成)
        int outcomeExtra = switch (outcome) {
            case WIN -> 7;
            case DRAW, LOSE -> 4;
        };
        int extra = outcomeExtra + Math.max(0, stats.extraScore());
        double factor = multiplier > 0.0D && Double.isFinite(multiplier) ? multiplier : 1.0D;
        double rawTotal = (base + extra) * factor;
        int total = Math.max(0, (int) Math.round(rawTotal));
        return new Formula(score, base, extra, rawTotal, total, outcome);
    }

    private static int getLevelForTotalExperience(int totalExp, int maxLevel) {
        int total = Math.max(0, totalExp);
        int level = 0;
        while (level < maxLevel && getTotalExperienceForLevel(level + 1, maxLevel) <= total) {
            level++;
        }
        return level;
    }

    private static int getXpNeededForNextLevel(int level, int maxLevel) {
        if (level >= maxLevel) {
            return 1;
        }
        return getXpNeededForNextLevelUncapped(level);
    }

    private static int getXpNeededForNextLevelUncapped(int level) {
        if (level >= 30) {
            return 9 * level - 158;
        }
        if (level >= 15) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }

    private static int getTotalExperienceForLevel(int level, int maxLevel) {
        int lv = Math.max(0, level);
        if (lv > maxLevel) {
            lv = maxLevel;
        }
        return getTotalExperienceForLevelUncapped(lv);
    }

    private static int getTotalExperienceForLevelUncapped(int level) {
        int lv = Math.max(0, level);
        if (lv <= 16) {
            return lv * lv + 6 * lv;
        }
        if (lv <= 31) {
            return (int) (2.5D * lv * lv - 40.5D * lv + 360.0D);
        }
        return (int) (4.5D * lv * lv - 162.5D * lv + 2220.0D);
    }

    private static int getUncappedLevelForTotalExperience(int totalExp) {
        int total = Math.max(0, totalExp);
        int level = 0;
        while (getTotalExperienceForLevelUncapped(level + 1) <= total) {
            level++;
        }
        return level;
    }

    private static void runWithGrantBypass(UUID uuid, Runnable action) {
        if (uuid == null || action == null) {
            return;
        }
        XP_GRANT_BYPASS.add(uuid);
        try {
            action.run();
        } finally {
            XP_GRANT_BYPASS.remove(uuid);
        }
    }

    public record MatchStats(int score, int kill, int assist, int death, int durationSeconds, int extraScore) {
        public MatchStats(int score, int kill, int assist, int death, int durationSeconds) {
            this(score, kill, assist, death, durationSeconds, 0);
        }
    }

    public record Formula(
        int score,
        double scoreFactorPart,
        int extraPart,
        double rawTotal,
        int total,
        MatchOutcome outcomeType
    ) {
        public String toPlayerHintString() {
            return String.format(
                "本局经验计算: 基础=%.3f, 额外=%d, 合计=%.3f, 结算经验=%+d",
                scoreFactorPart,
                extraPart,
                rawTotal,
                total
            );
        }

        public String toExpressionString() {
            return String.format(
                "score=%d -> base=score*0.005=%.3f, outcome=%s extra=%d, rawTotal=%.3f, finalRounded=%d",
                score,
                scoreFactorPart,
                outcomeType.name().toLowerCase(),
                extraPart,
                rawTotal,
                total
            );
        }
    }

    public enum MatchOutcome {
        WIN,
        DRAW,
        LOSE
    }

    public record AwardResult(int gainedXp, int levelBefore, int levelAfter, Formula formula) {
        public static AwardResult empty() {
            return new AwardResult(0, 0, 0, null);
        }
    }
}
