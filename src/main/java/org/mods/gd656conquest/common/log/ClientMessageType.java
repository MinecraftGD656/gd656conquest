package org.mods.gd656conquest.common.log;

import net.minecraft.ChatFormatting;

public enum ClientMessageType {
    SUCCESS(ChatFormatting.GREEN),
    FAILURE(ChatFormatting.RED),
    WARNING(ChatFormatting.GOLD),
    INFO(ChatFormatting.WHITE);

    private final ChatFormatting color;

    ClientMessageType(ChatFormatting color) {
        this.color = color;
    }

    public ChatFormatting getColor() {
        return color;
    }
}
