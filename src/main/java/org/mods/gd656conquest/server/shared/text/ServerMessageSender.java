package org.mods.gd656conquest.server.shared.text;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ServerMessageSender {
    private ServerMessageSender() {
    }

    public static int info(CommandSourceStack source, String message) {
        sendToCommandSource(source, prefixed(null, ServerText.colored(message, ChatFormatting.AQUA)), false);
        return 1;
    }

    public static int failure(CommandSourceStack source, String message) {
        sendToCommandSource(source, prefixed(null, ServerText.colored(message, ChatFormatting.RED)), true);
        return 0;
    }

    public static void line(CommandSourceStack source, Component line) {
        sendToCommandSource(source, safeCopy(line), false);
    }

    public static void system(ServerPlayer player, String section, String message, ChatFormatting color) {
        system(player, section, ServerText.colored(message, color));
    }

    public static void system(ServerPlayer player, String section, Component content) {
        rawSystem(player, prefixed(section, content));
    }

    public static void rawSystem(ServerPlayer player, Component content) {
        if (player == null) {
            return;
        }
        player.sendSystemMessage(safeCopy(content));
    }

    public static void actionBar(ServerPlayer player, String section, String message, ChatFormatting color) {
        actionBar(player, section, ServerText.colored(message, color));
    }

    public static void actionBar(ServerPlayer player, String section, Component content) {
        rawActionBar(player, prefixed(section, content));
    }

    public static void rawActionBar(ServerPlayer player, Component content) {
        if (player == null) {
            return;
        }
        player.displayClientMessage(safeCopy(content), true);
    }

    private static void sendToCommandSource(CommandSourceStack source, Component message, boolean failure) {
        ServerPlayer player = source == null ? null : source.getPlayer();
        if (player != null) {
            rawSystem(player, message);
            return;
        }
        if (source == null) {
            return;
        }
        if (failure) {
            source.sendFailure(safeCopy(message));
        } else {
            source.sendSuccess(() -> safeCopy(message), false);
        }
    }

    private static Component prefixed(String section, Component content) {
        Component body = safeCopy(content);
        if (section == null || section.isBlank()) {
            return ServerText.conquestPrefix().append(body);
        }
        return ServerText.sectionPrefix(section).append(body);
    }

    private static Component safeCopy(Component content) {
        return content == null ? Component.empty() : content.copy();
    }
}
