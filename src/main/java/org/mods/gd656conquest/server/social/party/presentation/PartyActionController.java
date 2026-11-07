package org.mods.gd656conquest.server.social.party.presentation;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.domain.PartyRequest;

import java.util.List;

public final class PartyActionController {
    private PartyActionController() {
    }

    public static int executeCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            PartyAggregate party = ui(context).createParty(player);
            return CommandFeedbacks.info(context.getSource(), "已创建新的队伍，队伍 ID=" + party.partyId());
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static int executeHelp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        sendHelpLines(player);
        return 1;
    }

    public static int executeLeave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            LeaveOperationResult result = leavePartyInternal(ui(context), player);
            return CommandFeedbacks.info(context.getSource(), result.selfMessage());
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static int executeDisband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            ServerPlayer leader = context.getSource().getPlayerOrException();
            PartyUiFacade facade = ui(context);
            var partyOpt = facade.findCurrentParty(leader);
            facade.disbandParty(leader);
            
            if (partyOpt.isPresent()) {
                for (var member : partyOpt.get().members()) {
                    if (!member.playerId().equals(leader.getUUID())) {
                        facade.findOnlinePlayer(member.playerId()).ifPresent(p -> 
                            ServerMessageSender.rawSystem(p, Component.literal("队长已解散当前队伍").withStyle(ChatFormatting.YELLOW))
                        );
                    }
                }
            }

            return CommandFeedbacks.info(context.getSource(), "已解散当前队伍");
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static int executeInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PartyUiFacade facade = ui(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        return facade.findCurrentParty(player).map(description -> {
            for (var line : PartyMessageFactory.buildPartyDetails(description)) {
                CommandFeedbacks.line(context.getSource(), line);
            }
            return 1;
        }).orElseGet(() -> {
            CommandFeedbacks.line(context.getSource(), PartyMessageFactory.buildPartyInfoEmptyLine());
            return 1;
        });
    }

    public static int executeList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PartyUiFacade facade = ui(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<org.mods.gd656conquest.server.social.party.application.PartyQueryService.PartyDescription> parties = facade.listAllParties();
        CommandFeedbacks.line(context.getSource(), PartyMessageFactory.buildHeader("当前队伍列表"));
        if (parties.isEmpty()) {
            CommandFeedbacks.line(context.getSource(), PartyMessageFactory.buildPartyListEmptyLine());
            return 1;
        }
        boolean allowApply = facade.findCurrentParty(player).isEmpty();
        for (var party : parties) {
            CommandFeedbacks.line(context.getSource(), PartyMessageFactory.buildPartyListLine(party, allowApply));
        }
        return 1;
    }

    public static int executeInvite(CommandContext<CommandSourceStack> context, String targetPlayerName) throws CommandSyntaxException {
        try {
            PartyUiFacade facade = ui(context);
            ServerPlayer player = context.getSource().getPlayerOrException();
            PartyRequest request = facade.createInvite(player, targetPlayerName);
            PartyUiFacade.PartyRequestView requestView = facade.getRequestView(request.requestId())
                .orElseThrow(() -> new PartyError("邀请创建成功，但未能读取请求详情"));
            facade.findOnlinePlayer(request.toPlayerId()).ifPresent(target ->
                ServerMessageSender.rawSystem(target, PartyMessageFactory.buildInviteNotification(requestView, context.getSource().getServer().getTickCount()))
            );
            return CommandFeedbacks.info(
                context.getSource(),
                "已向玩家 " + requestView.toPlayerName() + " 发送队伍邀请，请求 ID=" + request.requestId()
            );
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static int executeApply(CommandContext<CommandSourceStack> context, String partyId) throws CommandSyntaxException {
        try {
            PartyUiFacade facade = ui(context);
            ServerPlayer player = context.getSource().getPlayerOrException();
            PartyRequest request = facade.createJoinApplication(player, partyId);
            PartyUiFacade.PartyRequestView requestView = facade.getRequestView(request.requestId())
                .orElseThrow(() -> new PartyError("申请创建成功，但未能读取请求详情"));
            facade.findOnlinePlayer(request.toPlayerId()).ifPresent(target ->
                ServerMessageSender.rawSystem(target, PartyMessageFactory.buildJoinApplicationNotification(requestView, context.getSource().getServer().getTickCount()))
            );
            return CommandFeedbacks.info(
                context.getSource(),
                "已向队伍 " + request.partyId() + " 发起入队申请，请等待队长确认"
            );
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static int executeAccept(CommandContext<CommandSourceStack> context, String requestId) throws CommandSyntaxException {
        try {
            PartyUiFacade facade = ui(context);
            ServerPlayer player = context.getSource().getPlayerOrException();
            PartyRequest request = facade.acceptRequest(player, requestId);
            
            if (request.type() == org.mods.gd656conquest.server.social.party.domain.PartyRequestType.INVITE) {
                facade.findOnlinePlayer(request.fromPlayerId()).ifPresent(target ->
                    ServerMessageSender.rawSystem(target, Component.literal("玩家 " + player.getScoreboardName() + " 已同意你的邀请，加入队伍").withStyle(ChatFormatting.GREEN))
                );
            } else {
                facade.findOnlinePlayer(request.fromPlayerId()).ifPresent(target ->
                    ServerMessageSender.rawSystem(target, Component.literal("队长 " + player.getScoreboardName() + " 已同意你的申请，你已加入队伍").withStyle(ChatFormatting.GREEN))
                );
            }

            return CommandFeedbacks.info(
                context.getSource(),
                request.type() == org.mods.gd656conquest.server.social.party.domain.PartyRequestType.INVITE
                    ? "已接受邀请并加入目标队伍"
                    : "已同意该入队申请"
            );
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static int executeRefuse(CommandContext<CommandSourceStack> context, String requestId) throws CommandSyntaxException {
        try {
            PartyUiFacade facade = ui(context);
            ServerPlayer player = context.getSource().getPlayerOrException();
            PartyRequest request = facade.refuseRequest(player, requestId);
            
            if (request.type() == org.mods.gd656conquest.server.social.party.domain.PartyRequestType.INVITE) {
                facade.findOnlinePlayer(request.fromPlayerId()).ifPresent(target ->
                    ServerMessageSender.rawSystem(target, Component.literal("玩家 " + player.getScoreboardName() + " 已拒绝你的邀请").withStyle(ChatFormatting.RED))
                );
            } else {
                facade.findOnlinePlayer(request.fromPlayerId()).ifPresent(target ->
                    ServerMessageSender.rawSystem(target, Component.literal("队长 " + player.getScoreboardName() + " 已拒绝你的申请").withStyle(ChatFormatting.RED))
                );
            }

            return CommandFeedbacks.info(
                context.getSource(),
                request.type() == org.mods.gd656conquest.server.social.party.domain.PartyRequestType.INVITE
                    ? "已拒绝该队伍邀请"
                    : "已拒绝该入队申请"
            );
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static int executePending(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PartyUiFacade facade = ui(context);
        List<PartyUiFacade.PartyRequestView> requests = facade.listPendingRequests(context.getSource().getPlayerOrException());
        CommandFeedbacks.line(context.getSource(), PartyMessageFactory.buildHeader("待处理组队请求"));
        if (requests.isEmpty()) {
            CommandFeedbacks.line(context.getSource(), PartyMessageFactory.buildPendingEmptyLine());
            return 1;
        }
        long currentTick = context.getSource().getServer().getTickCount();
        for (PartyUiFacade.PartyRequestView request : requests) {
            CommandFeedbacks.line(context.getSource(), PartyMessageFactory.buildPendingRequestLine(request, currentTick));
        }
        return 1;
    }

    public static int executeLeaderTransfer(CommandContext<CommandSourceStack> context, String nextLeaderName) throws CommandSyntaxException {
        try {
            PartyUiFacade facade = ui(context);
            ServerPlayer player = context.getSource().getPlayerOrException();
            PartyAggregate party = facade.transferLeader(player, nextLeaderName);
            
            String targetName = facade.resolvePlayerName(party.leaderId());
            var partyOpt = facade.findCurrentParty(player);
            if (partyOpt.isPresent()) {
                for (var member : partyOpt.get().members()) {
                    if (!member.playerId().equals(player.getUUID())) {
                        facade.findOnlinePlayer(member.playerId()).ifPresent(p -> 
                            ServerMessageSender.rawSystem(p, Component.literal("队长已移交给 " + targetName).withStyle(ChatFormatting.YELLOW))
                        );
                    }
                }
            }

            return CommandFeedbacks.info(context.getSource(), "已将队长转移给 " + targetName);
        } catch (PartyError error) {
            return CommandFeedbacks.failure(context.getSource(), error.getMessage());
        }
    }

    public static boolean openHelp(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if(org.mods.gd656conquest.social.SocialNetwork.supported(player.connection.connection)){
            org.mods.gd656conquest.social.SocialNetwork.open(player);return true;
        }
        sendHelpLines(player);
        return true;
    }

    public static boolean leaveFromQuickItem(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        try {
            LeaveOperationResult result = leavePartyInternal(ui(player), player);
            ServerMessageSender.system(player, "组队", result.selfMessage(), ChatFormatting.AQUA);
            return true;
        } catch (PartyError error) {
            ServerMessageSender.system(player, "组队", error.getMessage(), ChatFormatting.RED);
            return false;
        }
    }

    private static PartyUiFacade ui(CommandContext<CommandSourceStack> context) {
        return PartyUiFacade.of(context.getSource().getServer());
    }

    private static PartyUiFacade ui(ServerPlayer player) {
        return PartyUiFacade.of(player.server);
    }

    private static void sendHelpLines(ServerPlayer player) {
        for (Component line : PartyMessageFactory.buildHelpLines()) {
            ServerMessageSender.rawSystem(player, line);
        }
    }

    private static LeaveOperationResult leavePartyInternal(PartyUiFacade facade, ServerPlayer player) {
        var partyOpt = facade.findCurrentParty(player);
        var result = facade.leaveParty(player);

        if (partyOpt.isPresent()) {
            for (var member : partyOpt.get().members()) {
                if (!member.playerId().equals(player.getUUID())) {
                    facade.findOnlinePlayer(member.playerId()).ifPresent(p -> {
                        if (result.disbanded()) {
                            ServerMessageSender.rawSystem(p, Component.literal("队长已离开队伍，当前队伍已自动解散").withStyle(ChatFormatting.YELLOW));
                        } else {
                            ServerMessageSender.rawSystem(p, Component.literal("玩家 " + player.getScoreboardName() + " 已离开队伍").withStyle(ChatFormatting.YELLOW));
                        }
                    });
                }
            }
        }
        return new LeaveOperationResult(result.disbanded() ? "你已离开队伍，因你是队长，队伍已自动解散" : "你已离开当前队伍");
    }

    private record LeaveOperationResult(String selfMessage) {
    }
}
