package org.mods.gd656conquest.server.social.party;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.shared.text.ServerText;
import org.mods.gd656conquest.server.social.chat.ServerChatFormatService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerPartyManager {
    private static final long REQUEST_TTL_TICKS = 30L * 20L;
    private static final long REQUEST_PAIR_COOLDOWN_TICKS = 30L * 20L;
    private static final Map<MinecraftServer, PlayerPartyManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<String, PartyData> parties = new LinkedHashMap<>();
    private final Map<UUID, String> partyIdByPlayer = new HashMap<>();
    private final Map<UUID, Map<UUID, PendingRequest>> pendingByReceiver = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> pairCooldownUntilTick = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> autoRefuseUntilTick = new HashMap<>();
    private final Map<UUID, String> lastKnownNameByUuid = new HashMap<>();
    private final Map<String, UUID> lastKnownUuidByName = new HashMap<>();

    private PlayerPartyManager(MinecraftServer server) {
        this.server = server;
    }

    public static PlayerPartyManager of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, PlayerPartyManager::new);
        }
    }

    public void onServerTick(long gameTick) {
        refreshOnlineNames();
        pendingByReceiver.values().forEach(pending -> pending.entrySet().removeIf(entry -> entry.getValue().expireTick < gameTick));
        pendingByReceiver.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        pairCooldownUntilTick.values().forEach(map -> map.entrySet().removeIf(entry -> entry.getValue() < gameTick));
        pairCooldownUntilTick.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        autoRefuseUntilTick.values().forEach(map -> map.entrySet().removeIf(entry -> entry.getValue() < gameTick));
        autoRefuseUntilTick.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
    }

    public void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        lastKnownNameByUuid.put(player.getUUID(), player.getScoreboardName());
        lastKnownUuidByName.put(player.getScoreboardName().toLowerCase(Locale.ROOT), player.getUUID());
        removePendingWithPlayer(player.getUUID());
        PartyData party = partyByPlayer(player.getUUID());
        if (party == null) {
            return;
        }
        broadcastToOtherMembers(party, player.getUUID(), player.getScoreboardName() + " 鏆傛椂绂荤嚎", ChatFormatting.GRAY);
    }

    public void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        refreshOnlineNames();
        refreshHotbar(player.getUUID());
        PartyData party = partyByPlayer(player.getUUID());
        if (party == null) {
            return;
        }
        sendPartyTip(player, ChatFormatting.GREEN, "你已恢复到队伍 " + party.id);
        broadcastToOtherMembers(party, player.getUUID(), player.getScoreboardName() + " 已重新上线", ChatFormatting.GREEN);
    }

    public void clearAll() {
        parties.clear();
        partyIdByPlayer.clear();
        pendingByReceiver.clear();
        pairCooldownUntilTick.clear();
        autoRefuseUntilTick.clear();
    }

    public boolean isInParty(UUID playerId) {
        return playerId != null && partyIdByPlayer.containsKey(playerId);
    }

    public boolean isLeader(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PartyData party = partyByPlayer(playerId);
        return party != null && playerId.equals(party.leaderId);
    }

    public String operationDeniedMessageForNonLeader(UUID playerId) {
        PartyData party = partyByPlayer(playerId);
        if (party == null || playerId != null && playerId.equals(party.leaderId)) {
            return "";
        }
        return "你已加入组队，涉及进退房等完整性操作仅队长可执行（队长: " + resolveName(party.leaderId) + "）";
    }

    public List<UUID> getPartyMembers(UUID anyMember) {
        PartyData party = partyByPlayer(anyMember);
        if (party == null) {
            return List.of();
        }
        return new ArrayList<>(party.memberIds);
    }

    public Optional<String> getPartyId(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(partyIdByPlayer.get(playerId));
    }

    public List<String> getAllPartyIds() {
        return new ArrayList<>(parties.keySet());
    }

    public String createParty(ServerPlayer creator) {
        if (creator == null) {
            return "无法创建队伍";
        }
        UUID creatorId = creator.getUUID();
        if (isInParty(creatorId)) {
            return "你已在队伍中，请先离队";
        }
        refreshOnlineNames();
        String id = buildPartyId(creator.getScoreboardName());
        PartyData party = new PartyData(id, creatorId);
        party.memberIds.add(creatorId);
        parties.put(id, party);
        partyIdByPlayer.put(creatorId, id);
        refreshHotbar(creatorId);
        sendPartyTip(creator, ChatFormatting.GREEN, "队伍创建成功，编号: " + id);
        return "队伍已创建: " + id;
    }

    public String requestJoin(ServerPlayer applicant, String partyId) {
        if (applicant == null) {
            return "申请失败";
        }
        refreshOnlineNames();
        PartyData party = parties.get(normalizePartyId(partyId));
        if (party == null) {
            return "队伍不存在";
        }
        UUID applicantId = applicant.getUUID();
        if (isInParty(applicantId)) {
            return "你已在队伍中，请先离队";
        }
        int maxMembers = getPartyMaxMembers();
        if (party.memberIds.size() >= maxMembers) {
            return "目标队伍已满（最多" + maxMembers + "人）";
        }
        ServerPlayer leader = server.getPlayerList().getPlayer(party.leaderId);
        if (leader == null) {
            return "队长不在线，暂时无法申请";
        }
        long now = server.getTickCount();
        if (isAutoRefusing(leader.getUUID(), applicantId, now)) {
            return "对方当前拒收你的申请，请稍后再试";
        }
        long remainTicks = remainingPairCooldownTicks(applicantId, leader.getUUID(), now);
        if (remainTicks > 0) {
            return "请求发送过快，请 " + Math.max(1, (int) Math.ceil(remainTicks / 20.0D)) + " 秒后再试";
        }
        setPairCooldown(applicantId, leader.getUUID(), now + REQUEST_PAIR_COOLDOWN_TICKS);
        PendingRequest request = new PendingRequest(RequestType.JOIN_REQUEST, party.id, applicantId, leader.getUUID(), now + REQUEST_TTL_TICKS);
        pendingByReceiver.computeIfAbsent(leader.getUUID(), key -> new HashMap<>()).put(applicantId, request);
        sendJoinRequestPrompt(leader, applicant, party.id);
        return "申请已发送，30秒内等待队长处理";
    }

    public String invite(ServerPlayer leader, ServerPlayer target) {
        if (leader == null || target == null) {
            return "邀请失败";
        }
        refreshOnlineNames();
        PartyData party = partyByPlayer(leader.getUUID());
        if (party == null) {
            return "你当前不在队伍中";
        }
        if (!leader.getUUID().equals(party.leaderId)) {
            return "仅队长可发送邀请";
        }
        if (leader.getUUID().equals(target.getUUID())) {
            return "不能邀请自己";
        }
        int maxMembers = getPartyMaxMembers();
        if (party.memberIds.size() >= maxMembers) {
            return "队伍已满（最多" + maxMembers + "人）";
        }
        if (isInParty(target.getUUID())) {
            return "目标玩家已在其他队伍中";
        }
        long now = server.getTickCount();
        if (isAutoRefusing(target.getUUID(), leader.getUUID(), now)) {
            return "对方当前拒收你的邀请";
        }
        long remainTicks = remainingPairCooldownTicks(leader.getUUID(), target.getUUID(), now);
        if (remainTicks > 0) {
            return "邀请发送过快，请 " + Math.max(1, (int) Math.ceil(remainTicks / 20.0D)) + " 秒后再试";
        }
        setPairCooldown(leader.getUUID(), target.getUUID(), now + REQUEST_PAIR_COOLDOWN_TICKS);
        PendingRequest request = new PendingRequest(RequestType.INVITE, party.id, leader.getUUID(), target.getUUID(), now + REQUEST_TTL_TICKS);
        pendingByReceiver.computeIfAbsent(target.getUUID(), key -> new HashMap<>()).put(leader.getUUID(), request);
        sendInvitePrompt(target, leader, party.id);
        return "邀请已发送，30秒内有效";
    }

    public String requestLeaderTransfer(ServerPlayer applicant) {
        if (applicant == null) {
            return "申请失败";
        }
        PartyData party = partyByPlayer(applicant.getUUID());
        if (party == null) {
            return "你当前不在队伍中";
        }
        if (applicant.getUUID().equals(party.leaderId)) {
            return "你已经是队长";
        }
        ServerPlayer leader = server.getPlayerList().getPlayer(party.leaderId);
        if (leader == null) {
            return "队长不在线，暂时无法申请";
        }
        long now = server.getTickCount();
        long remainTicks = remainingPairCooldownTicks(applicant.getUUID(), leader.getUUID(), now);
        if (remainTicks > 0) {
            return "请求发送过快，请 " + Math.max(1, (int) Math.ceil(remainTicks / 20.0D)) + " 秒后再试";
        }
        setPairCooldown(applicant.getUUID(), leader.getUUID(), now + REQUEST_PAIR_COOLDOWN_TICKS);
        PendingRequest request = new PendingRequest(RequestType.LEADER_TRANSFER, party.id, applicant.getUUID(), leader.getUUID(), now + REQUEST_TTL_TICKS);
        pendingByReceiver.computeIfAbsent(leader.getUUID(), key -> new HashMap<>()).put(applicant.getUUID(), request);
        sendLeaderTransferPrompt(leader, applicant, party.id);
        return "已向队长发送申请，30秒内有效";
    }

    public String leave(ServerPlayer player) {
        if (player == null) {
            return "离队失败";
        }
        PartyData party = partyByPlayer(player.getUUID());
        if (party == null) {
            return "你当前不在队伍中";
        }
        if (player.getUUID().equals(party.leaderId)) {
            disbandParty(party, "队长离队，队伍已解散");
            return "你已离队并解散队伍";
        }
        party.memberIds.remove(player.getUUID());
        partyIdByPlayer.remove(player.getUUID());
        removePendingWithPlayer(player.getUUID());
        refreshHotbar(player.getUUID());
        sendPartyTip(player, ChatFormatting.YELLOW, "你已离开队伍 " + party.id);
        broadcastToParty(party, player.getScoreboardName() + " 离开了队伍", ChatFormatting.YELLOW);
        refreshPartyMembersHotbar(party);
        return "已离开队伍";
    }

    public String kick(ServerPlayer leader, String targetName, int rejectSeconds) {
        if (leader == null || targetName == null || targetName.isBlank()) {
            return "踢出失败";
        }
        PartyData party = partyByPlayer(leader.getUUID());
        if (party == null) {
            return "你当前不在队伍中";
        }
        if (!leader.getUUID().equals(party.leaderId)) {
            return "仅队长可踢人";
        }
        UUID targetId = resolvePlayerId(targetName);
        if (targetId == null || !party.memberIds.contains(targetId)) {
            return "目标不在你的队伍中";
        }
        if (targetId.equals(leader.getUUID())) {
            return "不能踢出自己";
        }
        party.memberIds.remove(targetId);
        partyIdByPlayer.remove(targetId);
        removePendingWithPlayer(targetId);
        if (rejectSeconds > 0) {
            autoRefuseUntilTick.computeIfAbsent(leader.getUUID(), key -> new HashMap<>())
                .put(targetId, server.getTickCount() + Math.max(1, rejectSeconds) * 20L);
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target != null) {
            sendPartyTip(target, ChatFormatting.RED, "你已被队长移出队伍 " + party.id);
        }
        refreshHotbar(targetId);
        refreshPartyMembersHotbar(party);
        broadcastToParty(party, resolveName(targetId) + " 已被移出队伍", ChatFormatting.YELLOW);
        return "已移出玩家 " + resolveName(targetId);
    }

    public String accept(ServerPlayer receiver, String senderName) {
        if (receiver == null) {
            return "处理失败";
        }
        UUID senderId = resolvePlayerId(senderName);
        if (senderId == null) {
            return "未找到对应玩家";
        }
        PendingRequest request = pendingByReceiver.getOrDefault(receiver.getUUID(), Map.of()).get(senderId);
        if (request == null || request.expireTick < server.getTickCount()) {
            clearPending(receiver.getUUID(), senderId);
            return "该邀请/申请已失效";
        }
        clearPending(receiver.getUUID(), senderId);
        return switch (request.type) {
            case JOIN_REQUEST -> acceptJoinRequest(request, receiver);
            case INVITE -> acceptInvite(request, receiver);
            case LEADER_TRANSFER -> acceptLeaderTransfer(request, receiver);
        };
    }

    public String refuse(ServerPlayer receiver, String senderName, int autoRejectSeconds) {
        if (receiver == null) {
            return "处理失败";
        }
        UUID senderId = resolvePlayerId(senderName);
        if (senderId == null) {
            return "未找到对应玩家";
        }
        clearPending(receiver.getUUID(), senderId);
        if (autoRejectSeconds > 0) {
            autoRefuseUntilTick.computeIfAbsent(receiver.getUUID(), key -> new HashMap<>())
                .put(senderId, server.getTickCount() + Math.max(1, autoRejectSeconds) * 20L);
        }
        return "已拒绝来自 " + resolveName(senderId) + " 的请求";
    }

    public String clearAutoRefuse(ServerPlayer receiver) {
        if (receiver == null) {
            return "清除失败";
        }
        autoRefuseUntilTick.remove(receiver.getUUID());
        return "已清除所有自动拒绝规则";
    }

    public String disbandByAdmin(String partyId) {
        PartyData party = parties.get(normalizePartyId(partyId));
        if (party == null) {
            return "队伍不存在";
        }
        disbandParty(party, "管理员强制解散队伍");
        return "已解散队伍 " + party.id;
    }

    public List<String> describeCurrentParty(ServerPlayer player) {
        PartyData party = player == null ? null : partyByPlayer(player.getUUID());
        if (party == null) {
            return List.of("你当前不在任何队伍中");
        }
        List<String> lines = new ArrayList<>();
        lines.add("队伍ID: " + party.id);
        lines.add("队长: " + resolveName(party.leaderId));
        int idx = 1;
        for (UUID memberId : party.memberIds) {
            lines.add(idx + ". " + resolveName(memberId) + (memberId.equals(party.leaderId) ? " (队长)" : ""));
            idx++;
        }
        return lines;
    }

    public List<String> describeAllParties() {
        if (parties.isEmpty()) {
            return List.of("当前没有任何队伍");
        }
        List<String> lines = new ArrayList<>();
        for (PartyData party : parties.values()) {
            lines.add(party.id + " | 队长=" + resolveName(party.leaderId) + " | 人数=" + party.memberIds.size() + "/" + getPartyMaxMembers());
        }
        return lines;
    }

    public List<String> suggestPartyMemberNames(UUID playerId, boolean includeSelf) {
        PartyData party = partyByPlayer(playerId);
        if (party == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID member : party.memberIds) {
            if (!includeSelf && member.equals(playerId)) {
                continue;
            }
            names.add(resolveName(member));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public void sendPartyChat(ServerPlayer sender, String message) {
        if (sender == null) {
            return;
        }
        PartyData party = partyByPlayer(sender.getUUID());
        if (party == null) {
            sendPartyTip(sender, ChatFormatting.RED, "你当前不在队伍中");
            return;
        }
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) {
            sendPartyTip(sender, ChatFormatting.RED, "消息内容不能为空");
            return;
        }
        Collection<ServerPlayer> viewers = new LinkedList<>();
        for (UUID memberId : party.memberIds) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                viewers.add(member);
            }
        }
        ServerChatFormatService.broadcastPartyFormatted(sender, text, viewers);
    }

    private String acceptJoinRequest(PendingRequest request, ServerPlayer leader) {
        PartyData party = parties.get(request.partyId);
        if (party == null) {
            return "队伍不存在";
        }
        if (!leader.getUUID().equals(party.leaderId)) {
            return "仅队长可处理该申请";
        }
        int maxMembers = getPartyMaxMembers();
        if (party.memberIds.size() >= maxMembers) {
            return "队伍已满（最多" + maxMembers + "人）";
        }
        if (partyIdByPlayer.containsKey(request.fromPlayerId)) {
            return "对方已加入其他队伍";
        }
        party.memberIds.add(request.fromPlayerId);
        partyIdByPlayer.put(request.fromPlayerId, party.id);
        ServerPlayer applicant = server.getPlayerList().getPlayer(request.fromPlayerId);
        if (applicant != null) {
            sendPartyTip(applicant, ChatFormatting.GREEN, "你的入队申请已通过，已加入队伍 " + party.id);
        }
        refreshHotbar(request.fromPlayerId);
        refreshPartyMembersHotbar(party);
        broadcastToParty(party, resolveName(request.fromPlayerId) + " 加入了队伍", ChatFormatting.GREEN);
        return "已同意入队申请";
    }

    private String acceptInvite(PendingRequest request, ServerPlayer invitedPlayer) {
        PartyData party = parties.get(request.partyId);
        if (party == null) {
            return "队伍不存在";
        }
        int maxMembers = getPartyMaxMembers();
        if (party.memberIds.size() >= maxMembers) {
            return "队伍已满（最多" + maxMembers + "人）";
        }
        if (partyIdByPlayer.containsKey(invitedPlayer.getUUID())) {
            return "你已加入其他队伍";
        }
        party.memberIds.add(invitedPlayer.getUUID());
        partyIdByPlayer.put(invitedPlayer.getUUID(), party.id);
        ServerPlayer leader = server.getPlayerList().getPlayer(party.leaderId);
        if (leader != null) {
            sendPartyTip(leader, ChatFormatting.GREEN, invitedPlayer.getScoreboardName() + " 已接受邀请并加入队伍");
        }
        refreshHotbar(invitedPlayer.getUUID());
        refreshPartyMembersHotbar(party);
        broadcastToParty(party, invitedPlayer.getScoreboardName() + " 加入了队伍", ChatFormatting.GREEN);
        return "已加入队伍 " + party.id;
    }

    private String acceptLeaderTransfer(PendingRequest request, ServerPlayer leader) {
        PartyData party = parties.get(request.partyId);
        if (party == null) {
            return "队伍不存在";
        }
        if (!leader.getUUID().equals(party.leaderId)) {
            return "仅当前队长可处理该申请";
        }
        if (!party.memberIds.contains(request.fromPlayerId)) {
            return "申请者已不在队伍中";
        }
        party.leaderId = request.fromPlayerId;
        broadcastToParty(party, "队长已变更为 " + resolveName(party.leaderId), ChatFormatting.GOLD);
        return "已转移队长";
    }

    private void disbandParty(PartyData party, String reason) {
        if (party == null) {
            return;
        }
        parties.remove(party.id);
        for (UUID memberId : new ArrayList<>(party.memberIds)) {
            partyIdByPlayer.remove(memberId);
            removePendingWithPlayer(memberId);
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                sendPartyTip(member, ChatFormatting.RED, reason);
            }
            refreshHotbar(memberId);
        }
    }

    private void sendJoinRequestPrompt(ServerPlayer leader, ServerPlayer applicant, String partyId) {
        String name = applicant.getScoreboardName();
        MutableComponent message = Component.empty()
            .append(buildPartyPrefix())
            .append(ServerText.colored(name + " ", ChatFormatting.WHITE))
            .append(ServerText.colored("申请加入你的队伍！", ChatFormatting.GOLD))
            .append(ServerText.action(new ClickActionSpec("[同意]", ChatFormatting.GREEN, "/gdc action acceptparty " + name, "同意 " + name + " 的申请")))
            .append(Component.literal(" "))
            .append(ServerText.action(new ClickActionSpec("[谢绝]", ChatFormatting.RED, "/gdc action refuseparty " + name, "拒绝 " + name + " 的申请")));
        ServerMessageSender.rawSystem(leader, message);
        sendPartyTip(leader, ChatFormatting.GRAY, "请求来源队伍ID: " + partyId + "（30秒内有效）");
    }

    private void sendInvitePrompt(ServerPlayer target, ServerPlayer leader, String partyId) {
        String name = leader.getScoreboardName();
        MutableComponent message = Component.empty()
            .append(buildPartyPrefix())
            .append(ServerText.colored(name + " ", ChatFormatting.WHITE))
            .append(ServerText.colored("邀请你加入他的队伍！", ChatFormatting.GOLD))
            .append(ServerText.action(new ClickActionSpec("[加入]", ChatFormatting.GREEN, "/gdc action acceptparty " + name, "加入队伍 " + partyId)))
            .append(Component.literal(" "))
            .append(ServerText.action(new ClickActionSpec("[谢绝]", ChatFormatting.RED, "/gdc action refuseparty " + name, "拒绝邀请")));
        ServerMessageSender.rawSystem(target, message);
        sendPartyTip(target, ChatFormatting.GRAY, "目标队伍ID: " + partyId + "（30秒内有效）");
    }

    private void sendLeaderTransferPrompt(ServerPlayer leader, ServerPlayer applicant, String partyId) {
        String name = applicant.getScoreboardName();
        MutableComponent message = Component.empty()
            .append(buildPartyPrefix())
            .append(ServerText.colored(name + " ", ChatFormatting.WHITE))
            .append(ServerText.colored("申请成为队长！", ChatFormatting.GOLD))
            .append(ServerText.action(new ClickActionSpec("[同意]", ChatFormatting.GREEN, "/gdc action acceptparty " + name, "转移队长给 " + name)))
            .append(Component.literal(" "))
            .append(ServerText.action(new ClickActionSpec("[谢绝]", ChatFormatting.RED, "/gdc action refuseparty " + name, "拒绝申请")));
        ServerMessageSender.rawSystem(leader, message);
        sendPartyTip(leader, ChatFormatting.GRAY, "队伍ID: " + partyId + "（30秒内有效）");
    }

    private void broadcastToParty(PartyData party, String text, ChatFormatting color) {
        if (party == null) {
            return;
        }
        MutableComponent msg = Component.empty().append(buildPartyPrefix()).append(Component.literal(text).withStyle(color));
        for (UUID memberId : party.memberIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                ServerMessageSender.rawSystem(player, msg.copy());
            }
        }
    }

    private void broadcastToOtherMembers(PartyData party, UUID excludedPlayerId, String text, ChatFormatting color) {
        if (party == null) {
            return;
        }
        MutableComponent msg = Component.empty().append(buildPartyPrefix()).append(Component.literal(text).withStyle(color));
        for (UUID memberId : party.memberIds) {
            if (memberId.equals(excludedPlayerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                ServerMessageSender.rawSystem(player, msg.copy());
            }
        }
    }

    private void sendPartyTip(ServerPlayer player, ChatFormatting color, String text) {
        if (player == null) {
            return;
        }
        ServerMessageSender.rawSystem(player, Component.empty().append(buildPartyPrefix()).append(Component.literal(text).withStyle(color)));
    }

    private MutableComponent buildPartyPrefix() {
        return ServerText.sectionPrefix("组队");
    }

    private void refreshOnlineNames() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            lastKnownNameByUuid.put(player.getUUID(), player.getScoreboardName());
            lastKnownUuidByName.put(player.getScoreboardName().toLowerCase(Locale.ROOT), player.getUUID());
        }
    }

    private UUID resolvePlayerId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        refreshOnlineNames();
        return lastKnownUuidByName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private String resolveName(UUID playerId) {
        if (playerId == null) {
            return "?";
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getScoreboardName();
        }
        return lastKnownNameByUuid.getOrDefault(playerId, playerId.toString());
    }

    private PartyData partyByPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        String partyId = partyIdByPlayer.get(playerId);
        if (partyId == null) {
            return null;
        }
        return parties.get(partyId);
    }

    private boolean isAutoRefusing(UUID receiver, UUID sender, long nowTick) {
        if (receiver == null || sender == null) {
            return false;
        }
        Long until = autoRefuseUntilTick.getOrDefault(receiver, Map.of()).get(sender);
        return until != null && until >= nowTick;
    }

    private long remainingPairCooldownTicks(UUID sender, UUID receiver, long nowTick) {
        if (sender == null || receiver == null) {
            return 0L;
        }
        Long until = pairCooldownUntilTick.getOrDefault(sender, Map.of()).get(receiver);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - nowTick);
    }

    private void setPairCooldown(UUID sender, UUID receiver, long untilTick) {
        pairCooldownUntilTick.computeIfAbsent(sender, key -> new HashMap<>()).put(receiver, untilTick);
    }

    private void clearPending(UUID receiver, UUID sender) {
        Map<UUID, PendingRequest> map = pendingByReceiver.get(receiver);
        if (map == null) {
            return;
        }
        map.remove(sender);
        if (map.isEmpty()) {
            pendingByReceiver.remove(receiver);
        }
    }

    private void removePendingWithPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        pendingByReceiver.remove(playerId);
        for (Map<UUID, PendingRequest> pending : pendingByReceiver.values()) {
            pending.remove(playerId);
        }
        pendingByReceiver.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
    }

    private String normalizePartyId(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private int getPartyMaxMembers() {
        try {
            return Math.max(1, ConquestDataManager.of(server).serverConfig().getPartyMaxMembers());
        } catch (Exception ignored) {
            return 4;
        }
    }

    private String buildPartyId(String leaderName) {
        String safeName = leaderName == null ? "Player" : leaderName.trim();
        for (int i = 0; i < 16; i++) {
            int random = ThreadLocalRandom.current().nextInt(10000, 100000);
            String id = random + "_" + safeName;
            if (!parties.containsKey(id)) {
                return id;
            }
        }
        return System.currentTimeMillis() % 100000 + "_" + safeName;
    }

    private void refreshHotbar(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            QuickAccessItemService.refreshHotbar(player);
        }
    }

    private void refreshPartyMembersHotbar(PartyData party) {
        if (party == null) {
            return;
        }
        for (UUID memberId : party.memberIds) {
            refreshHotbar(memberId);
        }
    }

    public static final class PartyData {
        private final String id;
        private UUID leaderId;
        private final LinkedHashSet<UUID> memberIds = new LinkedHashSet<>();

        private PartyData(String id, UUID leaderId) {
            this.id = id;
            this.leaderId = leaderId;
        }

        public String getId() {
            return id;
        }

        public UUID getLeaderId() {
            return leaderId;
        }

        public Set<UUID> getMemberIds() {
            return Collections.unmodifiableSet(memberIds);
        }
    }

    private enum RequestType {
        JOIN_REQUEST,
        INVITE,
        LEADER_TRANSFER
    }

    private static final class PendingRequest {
        private final RequestType type;
        private final String partyId;
        private final UUID fromPlayerId;
        private final UUID toPlayerId;
        private final long expireTick;

        private PendingRequest(RequestType type, String partyId, UUID fromPlayerId, UUID toPlayerId, long expireTick) {
            this.type = type;
            this.partyId = partyId;
            this.fromPlayerId = fromPlayerId;
            this.toPlayerId = toPlayerId;
            this.expireTick = expireTick;
        }
    }
}
