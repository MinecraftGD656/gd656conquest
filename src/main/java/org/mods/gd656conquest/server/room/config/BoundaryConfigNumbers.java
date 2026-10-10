package org.mods.gd656conquest.server.room.config;

import java.util.Locale;

/** Return null for existing numeric settings, leaving their native parsing untouched. */
public final class BoundaryConfigNumbers {
    private BoundaryConfigNumbers() {}
    public static Object parse(String id, String raw) {
        if (!"flightboundarypaddingblocks".equals(id.toLowerCase(Locale.ROOT))) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("配置值必须为有限的非负数", invalid);
        }
    }
}
