package org.mods.gd656conquest.client.room;

/** Full-width background with a centered content area shared by every death state. */
public record KillerCardLayout(float left, float top, float scale, int width, int height,
                               int avatarWidth, int playerWidth, int weaponWidth, int actionWidth, int rescueWidth) {
    public static KillerCardLayout calculate(int screenWidth, int screenHeight, boolean playerKill, int rescuers,
                                             float chatRight, float chatTop, float chatBottom) {
        float scale = Math.min(1F, Math.max(1, screenWidth) / 722F);
        int width = Math.round(screenWidth / scale);
        int height = Math.max(80, Math.min(112, Math.round(screenHeight * .16F / scale)));
        int content = width - 2 * Math.round(width * .08F);
        int killer = Math.round(content * .34F);
        int avatar = Math.min(height, Math.round(content * .14F));
        int tail = content - killer, weapon = tail / 3, action = tail / 3, rescue = tail - weapon - action;
        return new KillerCardLayout(0, Math.max(0, screenHeight - height * scale), scale, width, height,
            avatar, killer - avatar, weapon, action, rescue);
    }
    public int contentLeft() { return Math.round(width * .08F); }
    public int contentRight() { return width - contentLeft(); }
    public int playerLeft() { return contentLeft() + avatarWidth; }
    public int weaponLeft() { return playerLeft() + playerWidth; }
    public int actionLeft() { return weaponLeft() + weaponWidth; }
    public int rescueLeft() { return actionLeft() + actionWidth; }
}
