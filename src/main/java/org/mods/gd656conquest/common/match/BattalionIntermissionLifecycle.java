package org.mods.gd656conquest.common.match;

/** Once-only room effects at server-owned presentation boundaries, including skipped ticks. */
public final class BattalionIntermissionLifecycle {
    private long sequence = -1;
    private boolean begun;
    private boolean cleaned;
    private boolean deployed;

    public record Actions(boolean begin, boolean cleanup, boolean deploy) {}

    public static boolean preservesScene(BattalionSequence.Phase phase) {
        return phase == BattalionSequence.Phase.RESULT || phase == BattalionSequence.Phase.TRAVEL_TO_BRIEFING;
    }

    public Actions advance(long id, BattalionSequence.Phase phase) {
        if (id != sequence) {
            sequence = id;
            begun = cleaned = deployed = false;
        }
        if (phase == BattalionSequence.Phase.COMBAT && !begun) return new Actions(false, false, false);
        boolean begin = !begun;
        boolean cleanup = !cleaned && !preservesScene(phase);
        boolean deploy = !deployed && (phase == BattalionSequence.Phase.PREPARATION || phase == BattalionSequence.Phase.COMBAT);
        begun = true;
        cleaned |= cleanup;
        deployed |= deploy;
        return new Actions(begin, cleanup, deploy);
    }
}
