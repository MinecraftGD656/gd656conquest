package org.mods.gd656conquest.client.room;

import org.mods.gd656conquest.common.player.DeathCause;

/** Small presentation state; independent of damage classification and of the camera solver. */
public final class DeathCauseBanner {
    private long latestId;
    private DeathCause cause = DeathCause.KILLED_IN_ACTION;
    private boolean visible;
    private float alpha;
    public record Layout(float left, float top, float width, float height, float scale, float spacing) {}

    public boolean begin(long id, DeathCause value) {
        if (id <= latestId) return false;
        latestId = id;
        cause = value == null ? DeathCause.KILLED_IN_ACTION : value;
        visible = cause != DeathCause.PLAYER_KILL;
        alpha = 0;
        return true;
    }
    public void hide() { visible = false; }
    public void reset() { latestId = 0; visible = false; alpha = 0; cause = DeathCause.KILLED_IN_ACTION; }
    public void advance(float seconds, boolean deathObservation) {
        if (!Float.isFinite(seconds) || seconds < 0) return;
        float step = Math.min(seconds, .2F) / .2F;
        alpha = visible && deathObservation ? Math.min(1, alpha + step) : Math.max(0, alpha - step);
    }
    public float alpha() { return alpha; }
    public DeathCause cause() { return cause; }
    public static Layout layout(int screenWidth, int screenHeight, float textWidth, int characters) {
        float scale = 1.5F, spacing = .8F;
        float measured = Math.max(0, textWidth) + Math.max(0, characters - 1) * spacing;
        float available = Math.max(1, screenWidth - 16);
        scale = Math.min(scale, available / Math.max(1, measured + 12));
        float width = (measured + 12) * scale, height = 13 * scale;
        float top = Math.min(Math.max(36, screenHeight * .11F), Math.max(0, screenHeight - height - 8));
        return new Layout((screenWidth - width) / 2, top, width, height, scale, spacing);
    }
}
