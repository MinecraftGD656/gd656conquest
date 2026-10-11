package org.mods.gd656conquest.common.match;

import org.mods.gd656conquest.common.match.BattalionSequence.Phase;

/** Frozen at the final battle end, independently of settlement and client presentation. */
public record OperationsConclusion(long battleEndTick, int briefingSeconds,
                                   OperationsSettlementTiming timing, boolean attackersWon) {
    public OperationsConclusion {
        if (battleEndTick < 0 || briefingSeconds < 8 || briefingSeconds > 600 || timing == null)
            throw new IllegalArgumentException("Invalid operations conclusion");
    }
    public long settlementTick() {
        return battleEndTick + (timing == OperationsSettlementTiming.MATCH_END ? 0
            : BriefingTimeline.stageStart(Phase.NEXT_ASSAULT, briefingSeconds));
    }
    public long presentationEndTick() { return battleEndTick + BriefingTimeline.length("attacker_won", briefingSeconds); }
    public long closeTick(int waitTicks) { return presentationEndTick() + Math.max(0, waitTicks); }
    public boolean settlementDue(long tick) { return tick >= settlementTick(); }
    public boolean presenting(long tick) { return tick < presentationEndTick(); }
}
