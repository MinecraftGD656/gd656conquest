package org.mods.gd656conquest.common.audio;

/** Stage identity survives deploy/death HUD updates, so one start only plays once. */
public final class StageBeginMusicCue {
    private static final String PREFIX = "BT_STAGE_BEGIN|";
    private String lastStage = "";
    private String previousAudioState = "";

    public static boolean isOpeningWindow(long tick, long startTick) {
        return tick >= startTick && tick - startTick < 100L;
    }

    public static String state(long match, int battalion, int stage, long startTick) {
        return PREFIX + match + "|" + battalion + "|" + stage + "|" + startTick;
    }

    public boolean update(String audioState) {
        String next = audioState == null ? "" : audioState;
        boolean play;
        if (next.startsWith(PREFIX)) {
            play = !next.equals(lastStage);
            lastStage = next;
        } else {
            play = "INITIAL_STAGE_BEGIN".equals(next) && !next.equals(previousAudioState);
        }
        previousAudioState = next;
        return play;
    }

    public void reset() { lastStage = ""; previousAudioState = ""; }
}
