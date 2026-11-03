package org.mods.gd656conquest.server.social.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.presentation.PartyUiFacade;
import org.mods.gd656conquest.server.title.PlayerTitleService;

import java.util.Collection;

public final class ServerChatFormatService {
    private static final String GLOBAL_CHANNEL_PREFIX = "@";
    private static final String ROOM_CHANNEL_PREFIX_ASCII = "!";
    private static final String ROOM_CHANNEL_PREFIX_FULLWIDTH = "！";

    private ServerChatFormatService() {
    }

    public static void broadcastFormatted(ServerPlayer sender, String rawText) {
        if (sender == null || sender.server == null) {
            return;
        }
        String input = rawText == null ? "" : rawText.trim();
        if (input.isBlank()) {
            return;
        }
        if (startsWithToken(input, "@t")) {
            String content = input.length() <= 2 ? "" : input.substring(2).trim();
            try {
                PartyUiFacade.of(sender.server).sendPartyChat(sender, content);
            } catch (PartyError error) {
                String message = error.getMessage() == null || error.getMessage().isBlank()
                    ? "当前无法发送队伍聊天消息"
                    : error.getMessage();
                ServerMessageSender.system(sender, "组队", message, ChatFormatting.RED);
            }
            return;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(sender.server);
        String roomId = resolveSenderRoomId(sender, coreManager);
        String senderTeamKey = resolvePlayerTeamKey(sender, coreManager);
        ParsedChatMessage parsed = parseChatMessage(input, roomId, senderTeamKey);
        ChatChannel channel = parsed.channel();
        String messageBody = parsed.messageBody();
        if (messageBody.isBlank()) {
            return;
        }
        logChatMessage(sender, coreManager, roomId, senderTeamKey, channel, messageBody);
        for (ServerPlayer viewer : sender.server.getPlayerList().getPlayers()) {
            if (!isViewerEligible(channel.scope, coreManager, viewer, roomId, senderTeamKey)) {
                continue;
            }
            ServerMessageSender.rawSystem(viewer, buildFormattedLine(sender, viewer, coreManager, roomId, senderTeamKey, channel, messageBody));
        }
        if (channel == ChatChannel.TEAM) {
            ChatChannelNoticeService.handleTeamChannelMessage(sender);
        }
    }

    public static void broadcastPartyFormatted(ServerPlayer sender, String rawText, Collection<ServerPlayer> viewers) {
        if (sender == null || sender.server == null || viewers == null || viewers.isEmpty()) {
            return;
        }
        String messageBody = rawText == null ? "" : rawText.trim();
        if (messageBody.isBlank()) {
            return;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(sender.server);
        String roomId = resolveSenderRoomId(sender, coreManager);
        String senderTeamKey = resolvePlayerTeamKey(sender, coreManager);
        for (ServerPlayer viewer : viewers) {
            if (viewer == null) {
                continue;
            }
            ServerMessageSender.rawSystem(viewer, buildFormattedLine(sender, viewer, coreManager, roomId, senderTeamKey, ChatChannel.PARTY, messageBody));
        }
    }

    public static MutableComponent buildPartyFormattedLine(ServerPlayer sender, ServerPlayer viewer, String rawText) {
        if (sender == null || sender.server == null) {
            return Component.empty();
        }
        String messageBody = rawText == null ? "" : rawText.trim();
        if (messageBody.isBlank()) {
            return Component.empty();
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(sender.server);
        String roomId = resolveSenderRoomId(sender, coreManager);
        String senderTeamKey = resolvePlayerTeamKey(sender, coreManager);
        return buildFormattedLine(sender, viewer, coreManager, roomId, senderTeamKey, ChatChannel.PARTY, messageBody);
    }

    private static String resolveSenderRoomId(ServerPlayer sender, RoomCoreRuntimeManager coreManager) {
        return coreManager.findPlayerRoomId(sender.getUUID()).orElse("");
    }

    private static ParsedChatMessage parseChatMessage(String text, String roomId, String senderTeamKey) {
        return new ParsedChatMessage(resolveChannel(text, roomId, senderTeamKey), stripScopePrefix(text));
    }

    private static ChatChannel resolveChannel(String text, String roomId, String senderTeamKey) {
        if (startsWithToken(text, "@a")) {
            return ChatChannel.GLOBAL;
        }
        if (startsWithToken(text, "@r") || startsWithRoomChannelPrefix(text)) {
            return roomId.isBlank() ? ChatChannel.GLOBAL : ChatChannel.ROOM;
        }
        if (startsWithGlobalChannelPrefix(text)) {
            return ChatChannel.GLOBAL;
        }
        if (!roomId.isBlank() && !senderTeamKey.isBlank()) {
            return ChatChannel.TEAM;
        }
        return ChatChannel.GLOBAL;
    }

    private static String stripScopePrefix(String text) {
        if (text == null) {
            return "";
        }
        if (startsWithToken(text, "@a") || startsWithToken(text, "@r")) {
            return text.length() <= 2 ? "" : text.substring(2).trim();
        }
        if (startsWithRoomChannelPrefix(text)) {
            return text.length() <= 1 ? "" : text.substring(1).trim();
        }
        if (startsWithGlobalChannelPrefix(text)) {
            return text.length() <= 1 ? "" : text.substring(1).trim();
        }
        return text;
    }

    private static boolean startsWithGlobalChannelPrefix(String text) {
        return startsWithToken(text, GLOBAL_CHANNEL_PREFIX);
    }

    private static boolean startsWithRoomChannelPrefix(String text) {
        return startsWithToken(text, ROOM_CHANNEL_PREFIX_ASCII) || startsWithToken(text, ROOM_CHANNEL_PREFIX_FULLWIDTH);
    }

    private static boolean startsWithToken(String text, String token) {
        if (text == null || token == null || token.isBlank()) {
            return false;
        }
        return text.regionMatches(true, 0, token, 0, token.length());
    }

    private static boolean isViewerEligible(
            ChatScope scope,
            RoomCoreRuntimeManager coreManager,
            ServerPlayer viewer,
            String senderRoomId,
            String senderTeamKey
    ) {
        if (scope == ChatScope.GLOBAL) {
            return true;
        }
        String viewerRoom = coreManager.findPlayerRoomId(viewer.getUUID()).orElse("");
        if (!senderRoomId.equalsIgnoreCase(viewerRoom)) {
            return false;
        }
        if (scope == ChatScope.ROOM) {
            return true;
        }
        String viewerTeamKey = resolvePlayerTeamKey(viewer, coreManager);
        return !viewerTeamKey.isBlank() && viewerTeamKey.equalsIgnoreCase(senderTeamKey);
    }

    private static ChatFormatting resolveRelationColor(
            RoomCoreRuntimeManager coreManager,
            ServerPlayer viewer,
            String senderRoomId,
            String senderTeamKey
    ) {
        if (viewer == null || senderRoomId == null || senderRoomId.isBlank() || senderTeamKey == null || senderTeamKey.isBlank()) {
            return ChatFormatting.GRAY;
        }
        String viewerRoom = coreManager.findPlayerRoomId(viewer.getUUID()).orElse("");
        if (!senderRoomId.equalsIgnoreCase(viewerRoom)) {
            return ChatFormatting.GRAY;
        }
        String viewerTeamKey = resolvePlayerTeamKey(viewer, coreManager);
        if (viewerTeamKey.isBlank()) {
            return ChatFormatting.GRAY;
        }
        return viewerTeamKey.equalsIgnoreCase(senderTeamKey) ? ChatFormatting.BLUE : ChatFormatting.RED;
    }

    private static MutableComponent buildFormattedLine(
            ServerPlayer sender,
            ServerPlayer viewer,
            RoomCoreRuntimeManager coreManager,
            String senderRoomId,
            String senderTeamKey,
            ChatChannel channel,
            String messageBody
    ) {
        String roomToken = resolveRoomToken(coreManager, sender, senderRoomId);
        int senderLevel = PlayerLevelService.getEffectiveLevel(sender);
        MutableComponent line = Component.empty()
                // GD656Mods: 频道字段提到最前; 玩家聊天不再带 [六五六大战场] 前缀(由发送方 rawSystem 保证)
                .append(Component.literal("<" + channel.label + "> ").withStyle(channel.color))
                .append(Component.literal("[" + roomToken + "] ").withStyle(style -> style.withColor(0x5FB4FF)))
                .append(PlayerTitleService.of(sender.server).buildChatLevelPrefix(sender, senderLevel));
        String senderTeamName = resolveTeamDisplayName(sender, coreManager);
        boolean mixedMode = "混战模式".equals(senderTeamName);
        if (!senderRoomId.isBlank() && (!senderTeamKey.isBlank() || mixedMode)) {
            ChatFormatting relationColor = mixedMode
                ? ChatFormatting.LIGHT_PURPLE
                : resolveRelationColor(coreManager, viewer, senderRoomId, senderTeamKey);
            String teamText = senderTeamName == null || senderTeamName.isBlank() || "-".equals(senderTeamName) ? "中立" : senderTeamName;
            line.append(Component.literal(teamText + " ").withStyle(relationColor));
        }
        ChatFormatting messageColor = sender.hasPermissions(2) ? ChatFormatting.RED : channel.color;
        line.append(Component.literal(sender.getScoreboardName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(">> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(messageBody).withStyle(messageColor));
        return line;
    }

    private static String resolveRoomToken(RoomCoreRuntimeManager coreManager, ServerPlayer sender, String senderRoomId) {
        if (senderRoomId == null || senderRoomId.isBlank()) {
            return "主城";
        }
        String squad = coreManager.findPlayerSquadLabel(sender.getUUID()).orElse("");
        if (squad.isBlank()) {
            return senderRoomId;
        }
        return senderRoomId + "-Team" + squad;
    }

    private static String resolveTeamDisplayName(ServerPlayer player, RoomCoreRuntimeManager coreManager) {
        String displayName = coreManager.getPlayerTeamDisplayName(player.getUUID());
        return displayName == null ? "" : displayName;
    }

    private static String resolvePlayerTeamKey(ServerPlayer player, RoomCoreRuntimeManager coreManager) {
        RoomTeamId newTeam = coreManager.findCurrentTeam(player.getUUID()).orElse(null);
        if (newTeam == RoomTeamId.CAMP_A) {
            return "campa";
        }
        if (newTeam == RoomTeamId.CAMP_B) {
            return "campb";
        }
        return "";
    }

    private static void logChatMessage(
        ServerPlayer sender,
        RoomCoreRuntimeManager coreManager,
        String senderRoomId,
        String senderTeamKey,
        ChatChannel channel,
        String messageBody
    ) {
        if (sender == null || channel == null || messageBody == null || messageBody.isBlank()) {
            return;
        }
        String roomToken = resolveRoomToken(coreManager, sender, senderRoomId);
        String teamName = resolveTeamDisplayName(sender, coreManager);
        String resolvedTeamName = teamName == null || teamName.isBlank() || "-".equals(teamName) ? "中立" : teamName;
        String resolvedRoomToken = roomToken == null || roomToken.isBlank() ? "主城" : roomToken;
        ServerLogger.info(
            "[CHAT][%s][房间:%s][队伍:%s][玩家:%s] %s",
            channel.label,
            resolvedRoomToken,
            resolvedTeamName,
            sender.getScoreboardName(),
            messageBody
        );
    }

    private enum ChatScope {
        PARTY,
        TEAM,
        ROOM,
        GLOBAL
    }

    private record ParsedChatMessage(ChatChannel channel, String messageBody) {
    }

    private enum ChatChannel {
        PARTY(ChatScope.PARTY, "队伍频道", ChatFormatting.LIGHT_PURPLE),
        GLOBAL(ChatScope.GLOBAL, "全服频道", ChatFormatting.GRAY),
        ROOM(ChatScope.ROOM, "房间频道", ChatFormatting.YELLOW),
        TEAM(ChatScope.TEAM, "阵营频道", ChatFormatting.GREEN);

        private final ChatScope scope;
        private final String label;
        private final ChatFormatting color;

        ChatChannel(ChatScope scope, String label, ChatFormatting color) {
            this.scope = scope;
            this.label = label;
            this.color = color;
        }
    }
}
