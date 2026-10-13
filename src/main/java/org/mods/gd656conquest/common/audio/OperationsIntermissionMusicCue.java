package org.mods.gd656conquest.common.audio;

/** A local result track plays once, then hands off to deployment until the player deploys. */
public final class OperationsIntermissionMusicCue {
    public enum Music { NONE, VICTORY, DEFEAT, DEPLOY }

    private Music music = Music.NONE;
    private boolean resultObserved;
    private boolean terminalConclusion;

    public void start(boolean selfAttacker, boolean attackersWon) {
        start(selfAttacker, attackersWon, false);
    }

    public void start(boolean selfAttacker, boolean attackersWon, boolean terminal) {
        music = selfAttacker == attackersWon ? Music.VICTORY : Music.DEFEAT;
        resultObserved = false;
        terminalConclusion = terminal;
    }

    public boolean resultFinished(boolean resultStillManaged) {
        if (!isResultPlaying()) return false;
        if (resultStillManaged) {
            resultObserved = true;
            return false;
        }
        if (!resultObserved) return false;
        music = terminalConclusion ? Music.NONE : Music.DEPLOY;
        return !terminalConclusion;
    }

    public Music music() { return music; }
    public boolean isActive() { return music != Music.NONE; }
    public boolean isResultPlaying() { return music == Music.VICTORY || music == Music.DEFEAT; }
    public boolean isTerminalConclusion() { return terminalConclusion; }
    public float volume(boolean timelineSoundPlaying) {
        return isResultPlaying() && timelineSoundPlaying ? 0.25F : 1.0F;
    }
    public void reset() { music = Music.NONE; resultObserved = false; terminalConclusion = false; }
}
