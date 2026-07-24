package org.mods.gd656conquest.client.hud;

final class ClientHudStyledText {
    private static final String PREFIX = "<hud:";

    private ClientHudStyledText() {
    }

    static Descriptor parse(String raw, int fallbackColor) {
        if (raw == null) {
            return new Descriptor("", fallbackColor, fallbackColor, false);
        }
        String text = raw.trim();
        if (text.isEmpty() || "-".equals(text)) {
            return new Descriptor("", fallbackColor, fallbackColor, false);
        }
        if (!text.startsWith(PREFIX)) {
            return new Descriptor(text, fallbackColor, fallbackColor, false);
        }
        int suffixIndex = text.indexOf('>');
        if (suffixIndex <= PREFIX.length()) {
            return new Descriptor(text, fallbackColor, fallbackColor, false);
        }
        String style = text.substring(PREFIX.length(), suffixIndex).trim();
        String content = text.substring(suffixIndex + 1).trim();
        if (content.isEmpty()) {
            return new Descriptor("", fallbackColor, fallbackColor, false);
        }
        return switch (style) {
            case "BT_LAST_STAND_STATUS", "BT_LAST_STAND_PERSISTENT" ->
                new Descriptor(content, 0xFFA0D0F0, 0xFFA0D0F0, false);
            case "BT_BURN_BOATS_STATUS", "BT_BURN_BOATS_PERSISTENT" ->
                new Descriptor(content, 0xFFFF5555, 0xFFFF5555, false);
            default -> new Descriptor(content, fallbackColor, fallbackColor, false);
        };
    }

    record Descriptor(String text, int primaryColor, int secondaryColor, boolean flashing) {
        int currentColor() {
            if (!flashing || primaryColor == secondaryColor) {
                return primaryColor;
            }
            long phase = (System.currentTimeMillis() / 500L) & 1L;
            return phase == 0L ? primaryColor : secondaryColor;
        }

        boolean visible() {
            return text != null && !text.isBlank();
        }
    }
}
