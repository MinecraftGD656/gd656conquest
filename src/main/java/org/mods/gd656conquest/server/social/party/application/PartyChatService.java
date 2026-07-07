package org.mods.gd656conquest.server.social.party.application;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.social.chat.ServerChatFormatService;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.infra.PartyRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PartyChatService {
    private final MinecraftServer server;
    private final PartyRepository repository;

    public PartyChatService(MinecraftServer server, PartyRepository repository) {
        this.server = Objects.requireNonNull(server, "server");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void sendPartyChat(ServerPlayer sender, String rawMessage) {
        ServerPlayer resolvedSender = requireSender(sender);
        String message = normalizeMessage(rawMessage);
        PartyAggregate party = repository.findByPlayerId(resolvedSender.getUUID())
            .orElseThrow(() -> new PartyError("你当前不在任何队伍中，无法发送组队消息"));
        List<ServerPlayer> recipients = listOnlineRecipients(resolvedSender, party);
        ServerChatFormatService.broadcastPartyFormatted(resolvedSender, message, recipients);
        logPartyChat(resolvedSender, party, message, recipients.size());
    }

    public List<ServerPlayer> listOnlineRecipients(ServerPlayer sender) {
        ServerPlayer resolvedSender = requireSender(sender);
        PartyAggregate party = repository.findByPlayerId(resolvedSender.getUUID())
            .orElseThrow(() -> new PartyError("你当前不在任何队伍中，无法发送组队消息"));
        return listOnlineRecipients(resolvedSender, party);
    }

    public MutableComponent formatPartyChatLine(ServerPlayer sender, ServerPlayer viewer, PartyAggregate party, String messageBody) {
        ServerPlayer resolvedSender = requireSender(sender);
        String message = normalizeMessage(messageBody);
        if (viewer == null) {
            throw new PartyError("队伍聊天接收者不能为空");
        }
        if (party == null) {
            repository.findByPlayerId(resolvedSender.getUUID()).orElseThrow(() -> new PartyError("未找到对应的队伍"));
        }
        return ServerChatFormatService.buildPartyFormattedLine(resolvedSender, viewer, message);
    }

    private List<ServerPlayer> listOnlineRecipients(ServerPlayer sender, PartyAggregate party) {
        List<ServerPlayer> recipients = new ArrayList<>();
        for (var memberId : party.memberIds()) {
            ServerPlayer recipient = server.getPlayerList().getPlayer(memberId);
            if (recipient != null) {
                recipients.add(recipient);
            }
        }
        if (recipients.isEmpty()) {
            recipients.add(sender);
        }
        return recipients;
    }

    private void logPartyChat(ServerPlayer sender, PartyAggregate party, String message, int recipientCount) {
        ServerLogger.info(
            "[CHAT][Party][队伍:%s][玩家:%s][接收人数:%d] %s",
            party.partyId(),
            sender.getScoreboardName(),
            recipientCount,
            message
        );
    }

    private ServerPlayer requireSender(ServerPlayer sender) {
        if (sender == null || sender.server == null) {
            throw new PartyError("该操作只能由在线玩家执行");
        }
        return sender;
    }

    private String normalizeMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            throw new PartyError("组队聊天内容不能为空");
        }
        return message;
    }
}
