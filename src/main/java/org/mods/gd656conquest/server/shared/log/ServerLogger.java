package org.mods.gd656conquest.server.shared.log;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.log.ClientMessageType;
import org.mods.gd656conquest.common.log.ServerLogType;
import org.slf4j.Logger;

import java.util.Locale;

public final class ServerLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SERVER_PREFIX = "[六五六大战场] ";

    private ServerLogger() {
    }

    public static void success(String message, Object... args) {
        log(ServerLogType.SUCCESS, message, args);
    }

    public static void failure(String message, Object... args) {
        log(ServerLogType.FAILURE, message, args);
    }

    public static void info(String message, Object... args) {
        log(ServerLogType.INFO, message, args);
    }

    public static void sendToClient(ServerPlayer player, ClientMessageType type, String message, Object... args) {
        if (player == null) {
            return;
        }
        String finalMessage = format(message, args);
        MutableComponent prefix = Component.literal(SERVER_PREFIX).withStyle(ChatFormatting.GRAY);
        Component content = Component.literal(finalMessage).withStyle(type.getColor());
        player.sendSystemMessage(prefix.append(content));
        if (type == ClientMessageType.FAILURE) {
            LOGGER.info("{}[SEND][{}][{}] {}", SERVER_PREFIX, type.name(), player.getGameProfile().getName(), finalMessage);
        }
    }

    private static void log(ServerLogType type, String message, Object... args) {
        LOGGER.info("{}[{}] {}", SERVER_PREFIX, type.name(), format(message, args));
    }

    private static String format(String message, Object... args) {
        return args == null || args.length == 0 ? message : String.format(Locale.ROOT, message, args);
    }
}
