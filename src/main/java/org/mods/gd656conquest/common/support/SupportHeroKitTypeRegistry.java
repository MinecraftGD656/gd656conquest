package org.mods.gd656conquest.common.support;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class SupportHeroKitTypeRegistry {
    private SupportHeroKitTypeRegistry() {
    }

    public static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    public static String displayName(String type) {
        return switch (normalizeType(type)) {
            case "antivehicle" -> "反载具";
            case "flametrooper" -> "喷火兵";
            case "runner" -> "突击兵";
            case "sentry" -> "哨戒兵";
            case "trenchraider" -> "堑壕兵";
            default -> {
                String normalized = normalizeType(type);
                yield normalized.isBlank() ? "未分类" : normalized;
            }
        };
    }

    public static ResourceLocation texture(String type) {
        String normalized = normalizeType(type);
        if (normalized.isBlank()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath("gd656conquest", "textures/gui/support/herokit_" + normalized + ".png");
    }

    public static String soundId(String type) {
        return switch (normalizeType(type)) {
            case "antivehicle" -> "gd656conquest:voice.support.herokit_available_antivehicle";
            case "flametrooper" -> "gd656conquest:voice.support.herokit_available_flametrooper";
            case "runner" -> "gd656conquest:voice.support.herokit_available_runner";
            case "sentry" -> "gd656conquest:voice.support.herokit_available_sentry";
            case "trenchraider" -> "gd656conquest:voice.support.herokit_available_trenchraider";
            default -> "";
        };
    }
}
