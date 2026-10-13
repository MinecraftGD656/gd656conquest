package org.mods.gd656conquest.common.audio;

/** A local result track plays once, then hands off to deployment until the player deploys. */
public final class OperationsIntermissionMusicCue {
    public enum Music { NONE, VICTORY, DEFEAT, DEPLOY }

    private Music music = Music.NONE;
    private boolean resultObserved;

    public void start(boolean selfAttacker, boolean attackersWon) {
        music = selfAttacker == attackersWon ? Music.VICTORY : Music.DEFEAT;
        resultObserved = false;
    }

    public boolean resultFinished(boolean resultStillManaged) {
        if (!isResultPlaying()) return false;
        if (resultStillManaged) {
            resultObserved = true;
            return false;
        }
        if (!resultObserved) return false;
        music = Music.DEPLOY;
        return true;
    }

    public Music music() { return music; }
    public boolean isActive() { return music != Music.NONE; }
    public boolean isResultPlaying() { return music == Music.VICTORY || music == Music.DEFEAT; }
    public void reset() { music = Music.NONE; resultObserved = false; }
}
