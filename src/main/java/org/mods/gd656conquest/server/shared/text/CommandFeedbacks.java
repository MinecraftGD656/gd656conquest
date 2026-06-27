package org.mods.gd656conquest.server.shared.text;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CommandFeedbacks {
    private CommandFeedbacks() {
    }

    public static int info(CommandSourceStack source, String message) {
        return ServerMessageSender.info(source, message);
    }

    public static int failure(CommandSourceStack source, String message) {
        return ServerMessageSender.failure(source, message);
    }

    public static void line(CommandSourceStack source, Component line) {
        ServerMessageSender.line(source, line);
    }
}
