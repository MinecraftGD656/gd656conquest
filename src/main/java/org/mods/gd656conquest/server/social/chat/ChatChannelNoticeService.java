package org.mods.gd656conquest.server.social.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.shared.text.ServerText;

public final class ChatChannelNoticeService {
    private static final String ACKNOWLEDGE_COMMAND = "/gdc action acceptchat";

    private ChatChannelNoticeService() {
    }

    public static void handleTeamChannelMessage(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        PlayerDataModel data = ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID());
        if (data.isTeamChannelTipAcknowledged()) {
            return;
        }
        int nextCount = data.getUnacknowledgedTeamChannelMessageCount() + 1;
        data.setUnacknowledgedTeamChannelMessageCount(nextCount);
        ConquestDataManager.of(player.server).playerData().savePlayerData(player.getUUID(), data);
        if (shouldSendTip(nextCount)) {
            ServerMessageSender.system(player, "聊天", buildTeamChannelTip());
        }
    }

    public static boolean acknowledgeTeamChannelTip(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        PlayerDataModel data = ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID());
        boolean changed = !data.isTeamChannelTipAcknowledged() || data.getUnacknowledgedTeamChannelMessageCount() != 0;
        data.setTeamChannelTipAcknowledged(true);
        data.setUnacknowledgedTeamChannelMessageCount(0);
        ConquestDataManager.of(player.server).playerData().savePlayerData(player.getUUID(), data);
        return changed;
    }

    private static boolean shouldSendTip(int messageCount) {
        return messageCount == 1 || (messageCount > 1 && (messageCount - 1) % 3 == 0);
    }

    private static Component buildTeamChannelTip() {
        MutableComponent content = Component.empty()
            .append(Component.literal("当前聊天频道为").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("阵营频道").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("，聊天消息前加前缀“").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("@").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("”或“").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("！").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("”，可分别发送").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("全服广播").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("或").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("房间消息").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
            .append(ServerText.action(new ClickActionSpec("[我已熟知]", ChatFormatting.GOLD, ACKNOWLEDGE_COMMAND, "点击后不再重复提醒")));
        return content;
    }
}
