package org.mods.gd656conquest.server.shared.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;

public final class ServerText {
    private static final int DEFAULT_PREFIX_COLOR = 0xD0D4D7;
    private static final String APP_NAME = "六五六大战场";

    private ServerText() {
    }

    public static MutableComponent conquestPrefix() {
        return tagPrefix(APP_NAME, DEFAULT_PREFIX_COLOR);
    }

    public static MutableComponent sectionPrefix(String section) {
        MutableComponent base = conquestPrefix();
        if (section == null || section.isBlank()) {
            return base;
        }
        return base.append(Component.literal("[" + section.trim() + "] ").withStyle(style -> style.withColor(DEFAULT_PREFIX_COLOR)));
    }

    public static MutableComponent tagPrefix(String tag, int color) {
        return Component.literal("[" + (tag == null ? "" : tag.trim()) + "] ").withStyle(style -> style.withColor(color));
    }

    public static MutableComponent colored(String text, ChatFormatting color) {
        return Component.literal(text == null ? "" : text).withStyle(color == null ? ChatFormatting.WHITE : color);
    }

    public static MutableComponent prefixed(String section, String text, ChatFormatting color) {
        return sectionPrefix(section).append(colored(text, color));
    }

    public static MutableComponent action(ClickActionSpec spec) {
        if (spec == null) {
            return Component.empty();
        }
        return Component.literal(spec.label()).withStyle(style -> {
            if (spec.color() != null) {
                style = style.withColor(spec.color());
            }
            if (spec.command() != null && !spec.command().isBlank()) {
                style = style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, spec.command()));
            }
            if (spec.hoverText() != null && !spec.hoverText().isBlank()) {
                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(spec.hoverText())));
            }
            return style;
        });
    }
}
