package org.mods.gd656conquest.server.social.party.presentation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.ServerText;
import org.mods.gd656conquest.server.social.party.application.PartyQueryService;

import java.util.List;

public final class PartyMessageFactory {
    private static final String SECTION = "组队";

    private PartyMessageFactory() {
    }

    public static Component buildHeader(String title) {
        return ServerText.sectionPrefix(SECTION).append(ServerText.colored(title, ChatFormatting.GOLD));
    }

    public static Component buildPartyInfoLine(PartyQueryService.PartyDescription description) {
        return prefixed(
            Component.literal("队伍 ID: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(description.partyId()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | 队长: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(description.leaderName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | 人数: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(description.memberCount() + "/" + org.mods.gd656conquest.server.social.party.domain.PartyAggregate.MAX_MEMBER_COUNT).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | 锁定: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatLockState(description.lockState().name())).withStyle(ChatFormatting.YELLOW))
        );
    }

    public static Component buildPartyMemberLine(PartyQueryService.PartyDescription description, PartyQueryService.PartyMemberDescription member) {
        boolean isLeader = description.leaderId().equals(member.playerId());
        return prefixed(
            Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(member.displayName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(isLeader ? " [队长]" : " [成员]").withStyle(isLeader ? ChatFormatting.GOLD : ChatFormatting.GRAY))
        );
    }

    public static Component buildPartyListLine(PartyQueryService.PartyDescription description, boolean allowApplyAction) {
        MutableComponent line = Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(description.partyId()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" | 队长: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(description.leaderName()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" | 人数: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(description.memberCount() + "/" + org.mods.gd656conquest.server.social.party.domain.PartyAggregate.MAX_MEMBER_COUNT).withStyle(ChatFormatting.WHITE));
        if (allowApplyAction) {
            line.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(ServerText.action(new ClickActionSpec(
                    "[申请加入]",
                    ChatFormatting.GOLD,
                    "/gdc party apply " + description.partyId(),
                    "点击向该队伍发起入队申请"
                )));
        }
        return prefixed(line);
    }

    public static Component buildPendingRequestLine(PartyUiFacade.PartyRequestView requestView, long currentTick) {
        MutableComponent line = Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(requestView.typeText()).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" | 请求ID: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(requestView.requestId()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" | 队伍: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(requestView.partyId()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" | 来自: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(requestView.fromPlayerName()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" | 剩余: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(requestView.remainingSecondsText(currentTick) + " 秒").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(ServerText.action(new ClickActionSpec(
                acceptanceLabel(requestView),
                ChatFormatting.GOLD,
                "/gdc action party accept " + requestView.requestId(),
                "点击确认处理该请求"
            )))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(ServerText.action(new ClickActionSpec(
                refusalLabel(requestView),
                ChatFormatting.RED,
                "/gdc action party refuse " + requestView.requestId(),
                "点击拒绝该请求"
            )));
        return prefixed(line);
    }

    public static Component buildInviteNotification(PartyUiFacade.PartyRequestView requestView, long currentTick) {
        return prefixed(
            Component.literal("收到新的队伍邀请，来自 ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(requestView.fromPlayerName()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("，队伍 ID=").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(requestView.partyId()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("，剩余 ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(requestView.remainingSecondsText(currentTick) + " 秒").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                .append(ServerText.action(new ClickActionSpec(
                    "[同意加入]",
                    ChatFormatting.GOLD,
                    "/gdc action party accept " + requestView.requestId(),
                    "点击加入该队伍"
                )))
                .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                .append(ServerText.action(new ClickActionSpec(
                    "[拒绝邀请]",
                    ChatFormatting.RED,
                    "/gdc action party refuse " + requestView.requestId(),
                    "点击拒绝该邀请"
                )))
        );
    }

    public static Component buildJoinApplicationNotification(PartyUiFacade.PartyRequestView requestView, long currentTick) {
        return prefixed(
            Component.literal("收到新的入队申请，玩家 ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(requestView.fromPlayerName()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" 想加入你的队伍，剩余 ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(requestView.remainingSecondsText(currentTick) + " 秒").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                .append(ServerText.action(new ClickActionSpec(
                    "[同意加入]",
                    ChatFormatting.GOLD,
                    "/gdc action party accept " + requestView.requestId(),
                    "点击同意该玩家加入你的队伍"
                )))
                .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                .append(ServerText.action(new ClickActionSpec(
                    "[拒绝加入]",
                    ChatFormatting.RED,
                    "/gdc action party refuse " + requestView.requestId(),
                    "点击拒绝该入队申请"
                )))
        );
    }

    public static Component buildPendingEmptyLine() {
        return prefixed(Component.literal("当前没有待处理的组队请求").withStyle(ChatFormatting.GRAY));
    }

    public static Component buildPartyListEmptyLine() {
        return prefixed(Component.literal("当前没有可加入的队伍").withStyle(ChatFormatting.GRAY));
    }

    public static Component buildPartyInfoEmptyLine() {
        return prefixed(Component.literal("你当前不在任何队伍中").withStyle(ChatFormatting.GRAY));
    }

    public static List<Component> buildPartyDetails(PartyQueryService.PartyDescription description) {
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(buildHeader("当前队伍信息"));
        lines.add(buildPartyInfoLine(description));
        lines.add(prefixed(Component.literal("成员列表:").withStyle(ChatFormatting.AQUA)));
        for (PartyQueryService.PartyMemberDescription member : description.members()) {
            lines.add(buildPartyMemberLine(description, member));
        }
        return lines;
    }

    public static List<Component> buildHelpLines() {
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(buildHeader("组队指令帮助"));
        lines.add(prefixed(Component.literal("/gdc party help").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 查看这份组队帮助").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party create").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 创建一个新的临时队伍").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party info").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 查看你当前所在队伍的信息").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party list").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 查看当前可加入的队伍列表").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party invite <玩家>").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 邀请在线玩家加入你的队伍").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party apply <队伍ID>").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 向目标队伍发起入队申请").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party pending").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 查看当前待处理的邀请和申请").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party accept <请求ID>").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 手动接受一条组队请求").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party refuse <请求ID>").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 手动拒绝一条组队请求").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party leave").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 离开当前队伍，若你是队长则会解散队伍").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party disband").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 主动解散你当前带领的队伍").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("/gdc party leader transfer <玩家>").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - 将队长转移给当前队伍中的在线成员").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("@t <消息>").withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(" - 向当前队伍频道发送聊天消息").withStyle(ChatFormatting.WHITE))));
        lines.add(prefixed(Component.literal("提示:").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(" 邀请和申请一般都可直接点击聊天里的 [同意加入] / [拒绝邀请] 处理。").withStyle(ChatFormatting.GRAY))));
        return lines;
    }

    private static String acceptanceLabel(PartyUiFacade.PartyRequestView requestView) {
        return requestView.type() == org.mods.gd656conquest.server.social.party.domain.PartyRequestType.INVITE ? "[同意加入]" : "[同意加入]";
    }

    private static String refusalLabel(PartyUiFacade.PartyRequestView requestView) {
        return requestView.type() == org.mods.gd656conquest.server.social.party.domain.PartyRequestType.INVITE ? "[拒绝邀请]" : "[拒绝加入]";
    }

    private static String formatLockState(String lockState) {
        return switch (lockState) {
            case "NONE" -> "未锁定";
            case "MEMBERSHIP_LOCKED" -> "成员锁定";
            case "ROOM_TRANSITION_LOCKED" -> "房间切换锁定";
            default -> lockState;
        };
    }

    private static Component prefixed(Component body) {
        return ServerText.sectionPrefix(SECTION).append(body);
    }
}
