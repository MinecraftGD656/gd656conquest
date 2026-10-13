package org.mods.gd656conquest.common.audio;

/** One crossing per retreat. Short retreats have no ten-second announcement. */
public final class OperationsRetreatVoiceCue {
    private long previousRemaining;
    private boolean announced;
    public void begin(int seconds) { previousRemaining = Math.max(0, seconds) * 20L; announced = seconds <= 10; }
    public boolean update(long remainingTicks, int pendingDefenders) {
        boolean play = !announced && pendingDefenders > 0 && remainingTicks > 0
            && previousRemaining > 200 && remainingTicks <= 200;
        previousRemaining = remainingTicks;
        if (play) announced = true;
        return play;
    }
}
