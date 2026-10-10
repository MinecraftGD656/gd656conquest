package org.mods.gd656conquest.client.room;

/** Shared render, input and hit-test coordinates, in scaled GUI units. */
public record RoomChatLayout(int left, int top, int width, int capacity, float baseline, int inputY) {
    public static RoomChatLayout calculate(int width, int height, double scale, int lineHeight,
                                           boolean focused, int visibleLines) {
        scale = Math.max(.1, scale); lineHeight = Math.max(1, lineHeight);
        int left = Math.max(6, Math.round(width * .015F));
        int chatWidth = Math.max(40, Math.round(Math.min(width * .40F, Math.max(160, width * .33F))));
        int top = Math.round(Math.min(Math.max(72, height * .20F), height * .28F));
        float footerTop = KillerCardLayout.calculate(width, height, false, 0, 0, 0, 0).top();
        int bottom = Math.max(top + 24, Math.min(height - 54, (int)footerTop - 24));
        int capacity = Math.max(1, Math.min(focused ? 14 : 8, (int)((bottom - top - 20) / (lineHeight * scale))));
        int rows = Math.max(1, Math.min(capacity, visibleLines));
        float baseline = (float)(Math.floor((top + rows * lineHeight * scale) / scale) * scale);
        int inputY = (int)Math.ceil(top + capacity * lineHeight * scale) + 7;
        return new RoomChatLayout(left, top, chatWidth, capacity, baseline, inputY);
    }
    public double chatX(double mouseX, double scale) { return (mouseX - left) / scale; }
    public double chatY(double mouseY, double scale, int lineHeight) { return (baseline - mouseY) / (scale * lineHeight); }
}
