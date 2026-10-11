package org.mods.gd656conquest.common.match;

/** Server-owned battalion timeline. Animation durations never scale with briefing length. */
public final class BattalionSequence {
    public static final int RESULT_TICKS = 60;
    public static final int BRIEFING_TRAVEL_TICKS = 140;
    public static final int DEPLOY_TRAVEL_TICKS = 40;
    public static final int MASK_FADE_TICKS = 20;
    public static final int MASK_FADE_OUT_TICKS = 20;
    public static final int ASSAULT_ANNOUNCEMENT_TICKS = 100;
    public static final int TEXT_FADE_IN_TICKS = 10;
    public static final int RESULT_FADE_OUT_TICKS = 20;
    public static final int ANNOUNCEMENT_FADE_OUT_TICKS = 10;
    public static final int SECTOR_CHANGE_TICKS = 10;
    public static final int MIN_BRIEFING_SECONDS = 8;

    public enum Phase {
        COMBAT, RESULT, TRAVEL_TO_BRIEFING, BRIEFING, BRIEFING_FADE_OUT, NEXT_ASSAULT,
        TRAVEL_TO_DEPLOY, PREPARATION, FINISHED;

        /** Within one conclusion sequence, COMBAT is the handoff after preparation. */
        public int presentationOrder() {
            return switch (this) {
                case COMBAT -> FINISHED.ordinal();
                case FINISHED -> FINISHED.ordinal() + 1;
                default -> ordinal();
            };
        }
    }

    private int battalion = 1;
    private int total = 3;
    private Phase phase = Phase.COMBAT;
    private long sequenceId;
    private long phaseStart;
    private int phaseDuration;
    private int briefingTicks;
    private int preparationTicks;
    private boolean terminal;

    public void initialize(int count) {
        battalion = 1;
        total = Math.max(1, count);
        phase = Phase.COMBAT;
        sequenceId = 0;
        phaseStart = 0;
        phaseDuration = 0;
        terminal = false;
    }

    public boolean finish(long tick, boolean attackersWon, int briefingSeconds, int preparationSeconds) {
        if (phase != Phase.COMBAT) return false;
        sequenceId = Math.max(sequenceId + 1, tick);
        terminal = attackersWon || battalion >= total;
        briefingTicks = Math.max(MIN_BRIEFING_SECONDS, briefingSeconds) * 20;
        preparationTicks = Math.max(1, preparationSeconds) * 20;
        enter(Phase.RESULT, tick, RESULT_TICKS);
        return true;
    }

    public void tick(long tick) {
        while (phase != Phase.COMBAT && phase != Phase.FINISHED && tick >= deadline()) {
            long nextStart = deadline();
            switch (phase) {
                case RESULT -> enter(Phase.TRAVEL_TO_BRIEFING, nextStart, BRIEFING_TRAVEL_TICKS);
                case TRAVEL_TO_BRIEFING -> enter(Phase.BRIEFING, nextStart, briefingTicks);
                case BRIEFING -> enter(Phase.BRIEFING_FADE_OUT, nextStart, MASK_FADE_OUT_TICKS);
                case BRIEFING_FADE_OUT -> enter(Phase.NEXT_ASSAULT, nextStart, ASSAULT_ANNOUNCEMENT_TICKS);
                case NEXT_ASSAULT -> enter(terminal ? Phase.FINISHED : Phase.TRAVEL_TO_DEPLOY,
                    nextStart, terminal ? 0 : DEPLOY_TRAVEL_TICKS);
                case TRAVEL_TO_DEPLOY -> {
                    battalion++;
                    enter(Phase.PREPARATION, nextStart, preparationTicks);
                }
                case PREPARATION -> enter(Phase.COMBAT, nextStart, 0);
                default -> { return; }
            }
        }
    }

    private void enter(Phase next, long tick, int duration) {
        phase = next;
        phaseStart = tick;
        phaseDuration = duration;
    }

    public boolean terminal() { return terminal; }
    public boolean active() { return phase != Phase.COMBAT; }
    public int battalion() { return battalion; }
    public int total() { return total; }
    public Phase phase() { return phase; }
    public long sequenceId() { return sequenceId; }
    public long phaseStart() { return phaseStart; }
    public int phaseDuration() { return phaseDuration; }
    public long deadline() { return phaseStart + phaseDuration; }
    public int elapsed(long tick) { return (int) Math.min(phaseDuration, Math.max(0, tick - phaseStart)); }
    public int remaining(long tick) { return Math.max(0, phaseDuration - elapsed(tick)); }

    public static float maskAlpha(double elapsedTicks) {
        return clamp(elapsedTicks / MASK_FADE_TICKS);
    }

    public static float briefingMaskAlpha(Phase phase, double elapsedTicks) {
        return switch (phase) {
            case BRIEFING -> maskAlpha(elapsedTicks);
            case BRIEFING_FADE_OUT -> clamp(1 - elapsedTicks / MASK_FADE_OUT_TICKS);
            default -> 0;
        };
    }

    public static float resultAlpha(Phase phase, double elapsedTicks) {
        return switch (phase) {
            case RESULT -> clamp(elapsedTicks / TEXT_FADE_IN_TICKS);
            case TRAVEL_TO_BRIEFING -> clamp((BRIEFING_TRAVEL_TICKS - elapsedTicks) / RESULT_FADE_OUT_TICKS);
            default -> 0;
        };
    }

    public static float announcementAlpha(double elapsedTicks) {
        return Math.min(clamp(elapsedTicks / TEXT_FADE_IN_TICKS),
            clamp((ASSAULT_ANNOUNCEMENT_TICKS - elapsedTicks) / ANNOUNCEMENT_FADE_OUT_TICKS));
    }

    public static String conclusionText(int battalion, int total, boolean selfAttacker, boolean attackersWon) {
        if (attackersWon || battalion >= total) return selfAttacker == attackersWon ? "行动胜利" : "行动失败";
        return remainingAssaultText(battalion, total);
    }

    public static String remainingAssaultText(int battalion, int total) {
        int remaining = Math.max(0, total - battalion);
        return remaining == 0 ? "" : remaining == 1 ? "最后攻势" : "还有" + remaining + "波攻势";
    }

    /** Sector numbers are one-based; only fully secured sectors change ownership. */
    public static float capturedBlend(int sector, int capturedSectors, double elapsedTicks) {
        if (sector < 1 || sector > capturedSectors) return 0;
        return clamp((elapsedTicks - MASK_FADE_TICKS - (sector - 1) * SECTOR_CHANGE_TICKS) / SECTOR_CHANGE_TICKS);
    }

    private static float clamp(double value) { return (float) Math.max(0, Math.min(1, value)); }
}
