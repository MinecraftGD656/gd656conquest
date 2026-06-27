package org.mods.gd656conquest.client.log;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.mods.gd656conquest.common.log.ClientMessageType;
import org.slf4j.Logger;

import java.util.Locale;

public final class ClientLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CLIENT_PREFIX = "[GD656Conquest-Client] ";

    private ClientLogger() {
    }

    public static void success(String message, Object... args) {
        log(ClientMessageType.SUCCESS, message, args);
    }

    public static void failure(String message, Object... args) {
        log(ClientMessageType.FAILURE, message, args);
    }

    public static void warning(String message, Object... args) {
        log(ClientMessageType.WARNING, message, args);
    }

    public static void info(String message, Object... args) {
        log(ClientMessageType.INFO, message, args);
    }

    public static void sendLocal(ClientMessageType type, String message, Object... args) {
        String finalMessage = format(message, args);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            MutableComponent prefix = Component.literal(CLIENT_PREFIX).withStyle(ChatFormatting.GOLD);
            Component content = Component.literal(finalMessage).withStyle(type.getColor());
            minecraft.player.sendSystemMessage(prefix.append(content));
        }
        LOGGER.info("{}[{}] {}", CLIENT_PREFIX, type.name(), finalMessage);
    }

    private static void log(ClientMessageType type, String message, Object... args) {
        LOGGER.info("{}[{}] {}", CLIENT_PREFIX, type.name(), format(message, args));
    }

    private static String format(String message, Object... args) {
        return args == null || args.length == 0 ? message : String.format(Locale.ROOT, message, args);
    }
}
