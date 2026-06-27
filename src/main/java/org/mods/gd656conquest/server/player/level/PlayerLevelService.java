package org.mods.gd656conquest.server.player.level;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
    public static final int MAX_LEVEL = 100;
    private static final Set<UUID> XP_GRANT_BYPASS = ConcurrentHashMap.newKeySet();

    private PlayerLevelService() {
    }

    public static int getEffectiveLevel(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_LEVEL, player.experienceLevel));
    }

    public static void clampPlayerLevel(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        PlayerDataStore handler = ConquestDataManager.of(player.server).playerData();
        PlayerDataModel data = handler.getPlayerData(player.getUUID());
        int maxTotal = getTotalExperienceForLevel(MAX_LEVEL);
        int total = Math.max(0, Math.min(maxTotal, data.getLevelExperience()));
        int level = Math.max(0, Math.min(MAX_LEVEL, getLevelForTotalExperience(total)));
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
        int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
        int total = getTotalExperienceForLevel(clamped);
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
        int maxTotal = getTotalExperienceForLevel(MAX_LEVEL);
        int beforeTotal = Math.max(0, Math.min(maxTotal, data.getLevelExperience()));
        int beforeLevel = Math.max(0, Math.min(MAX_LEVEL, getLevelForTotalExperience(beforeTotal)));
        Formula formula = computeFormula(stats, outcome);
        int delta = formula.total();
        int afterTotal = Math.max(0, Math.min(maxTotal, beforeTotal + delta));
        int gained = afterTotal - beforeTotal;
        int afterLevel = Math.max(0, Math.min(MAX_LEVEL, getLevelForTotalExperience(afterTotal)));
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

    public static Formula previewFormula(MatchStats stats, MatchOutcome outcome) {
        if (stats == null) {
            return null;
        }
        return computeFormula(stats, outcome);
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
        int maxTotal = getTotalExperienceForLevel(MAX_LEVEL);
        int total = Math.max(0, Math.min(maxTotal, totalExp));
        int level = Math.max(0, Math.min(MAX_LEVEL, getLevelForTotalExperience(total)));
        int intoLevel = Math.max(0, total - getTotalExperienceForLevel(level));
        int needed = Math.max(1, getXpNeededForNextLevel(level));
        float progress = level >= MAX_LEVEL ? 0.0F : Math.min(1.0F, intoLevel / (float) needed);
        runWithGrantBypass(player.getUUID(), () -> {
            player.totalExperience = total;
            player.experienceLevel = level;
            player.experienceProgress = progress;
            player.setExperiencePoints(intoLevel);
        });
    }

    private static Formula computeFormula(MatchStats stats, MatchOutcome outcome) {
        int score = Math.max(0, stats.score());
        double base = score * 0.005D;
        int extra = switch (outcome) {
            case WIN -> 7;
            case DRAW, LOSE -> 4;
        };
        double rawTotal = base + extra;
        int total = Math.max(0, (int) Math.round(rawTotal));
        return new Formula(score, base, extra, rawTotal, total, outcome);
    }

    private static int getLevelForTotalExperience(int totalExp) {
        int total = Math.max(0, totalExp);
        int level = 0;
        while (level < MAX_LEVEL && getTotalExperienceForLevel(level + 1) <= total) {
            level++;
        }
        return level;
    }

    private static int getXpNeededForNextLevel(int level) {
        if (level >= MAX_LEVEL) {
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

    private static int getTotalExperienceForLevel(int level) {
        int lv = Math.max(0, level);
        if (lv > MAX_LEVEL) {
            lv = MAX_LEVEL;
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

    public record MatchStats(int score, int kill, int assist, int death, int durationSeconds) {
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
