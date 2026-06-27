package org.mods.gd656conquest.server.shared.contract;

import net.minecraft.ChatFormatting;

public record ClickActionSpec(
    String label,
    ChatFormatting color,
    String command,
    String hoverText
) {
}
