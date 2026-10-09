package org.mods.gd656conquest.server.social.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * Temporary bridge that renders selected room notices in action-bar style.
 * This class is intentionally simple and can be removed at any time.
 */
public final class TemporarySayNoticeService {
    private TemporarySayNoticeService() {
    }

    public static void broadcastActionBarToPlayers(MinecraftServer server, Collection<UUID> playerIds, String message) {
        if (server == null || playerIds == null || message == null || message.isBlank()) {
            return;
        }
        Component payload = Component.literal(message).withStyle(ChatFormatting.WHITE);
        for (UUID uuid : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ServerMessageSender.actionBar(player, "房间", payload);
            }
        }
    }

    public static void broadcastCountdownActionBarToPlayers(MinecraftServer server, Collection<UUID> playerIds, String messageTemplate, int remainSeconds) {
        if (server == null || playerIds == null || messageTemplate == null || messageTemplate.isBlank()) {
            return;
        }
        ChatFormatting color = resolveCountdownColor(remainSeconds);
        String message = String.format(Locale.ROOT, messageTemplate, remainSeconds);
        Component payload = Component.literal(message).withStyle(color);
        for (UUID uuid : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ServerMessageSender.actionBar(player, "房间", payload);
            }
        }
    }

    public static void broadcastQueueStatusActionBarToPlayers(MinecraftServer server, Collection<UUID> playerIds, String message) {
        if (server == null || playerIds == null || message == null || message.isBlank()) {
            return;
        }
        Component payload = Component.literal(message).withStyle(ChatFormatting.AQUA);
        for (UUID uuid : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ServerMessageSender.actionBar(player, "房间", payload);
            }
        }
    }

    public static void broadcastQueueCountdownActionBarToPlayers(MinecraftServer server, Collection<UUID> playerIds, String queueStatusMessage, String countdownTemplate, int remainSeconds) {
        if (server == null || playerIds == null || queueStatusMessage == null || queueStatusMessage.isBlank() || countdownTemplate == null || countdownTemplate.isBlank()) {
            return;
        }
        ChatFormatting countdownColor = resolveCountdownColor(remainSeconds);
        String countdownMessage = String.format(Locale.ROOT, countdownTemplate, remainSeconds);
        Component payload = Component.literal(queueStatusMessage).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(countdownMessage).withStyle(countdownColor));
        for (UUID uuid : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ServerMessageSender.actionBar(player, "房间", payload);
            }
        }
    }

    public static void sendCountdownActionBar(ServerPlayer player, String messageTemplate, int remainSeconds) {
        if (player == null || messageTemplate == null || messageTemplate.isBlank()) {
            return;
        }
        ChatFormatting color = resolveCountdownColor(remainSeconds);
        String message = String.format(Locale.ROOT, messageTemplate, remainSeconds);
        ServerMessageSender.actionBar(player, "房间", Component.literal(message).withStyle(color));
    }

    private static ChatFormatting resolveCountdownColor(int remainSeconds) {
        if (remainSeconds <= 1) {
            return ChatFormatting.RED;
        }
        if (remainSeconds == 2) {
            return ChatFormatting.GOLD;
        }
        if (remainSeconds <= 5) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.WHITE;
    }
}
