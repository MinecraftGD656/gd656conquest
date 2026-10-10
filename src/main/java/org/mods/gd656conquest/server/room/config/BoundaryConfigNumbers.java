package org.mods.gd656conquest.server.room.config;

import java.util.Locale;

/** Return null for existing numeric settings, leaving their native parsing untouched. */
public final class BoundaryConfigNumbers {
    private BoundaryConfigNumbers() {}
    public static Object parse(String id, String raw) {
        if ("passiveregenhealamount".equals(id.toLowerCase(Locale.ROOT))) {
            try {
                double amount = Double.parseDouble(raw.trim());
                if (!Double.isFinite(amount) || amount < 0 || amount > 1024) throw new NumberFormatException();
                return amount;
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("自然回血回复量必须为 0–1024 HP 的有限数值", invalid);
            }
        }
        boolean medical = "medicalboxdamagecooldownseconds".equals(id.toLowerCase(Locale.ROOT));
        if (!medical && !"flightboundarypaddingblocks".equals(id.toLowerCase(Locale.ROOT))) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < 0 || (medical && value > 60)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("配置值必须为有限的非负数", invalid);
        }
    }
}
