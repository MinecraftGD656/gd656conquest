package org.mods.gd656conquest.server.social.party.presentation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.player.identity.PlayerIdentityService;
import org.mods.gd656conquest.server.social.party.application.PartyInviteService;
import org.mods.gd656conquest.server.social.party.application.PartyLeaderTransferService;
import org.mods.gd656conquest.server.social.party.application.PartyMembershipService;
import org.mods.gd656conquest.server.social.party.application.PartyQueryService;
import org.mods.gd656conquest.server.social.party.application.PartyChatService;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyRequest;
import org.mods.gd656conquest.server.social.party.domain.PartyRequestType;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;
import org.mods.gd656conquest.server.social.party.infra.PartyRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;

public final class PartyUiFacade {
    private static final long REQUEST_LIFETIME_TICKS = 20L * 30L;
    private static final Map<MinecraftServer, PartyUiFacade> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final PartyRepository repository;
    private final PlayerIdentityService identityService;
    private final PartyMembershipService membershipService;
    private final PartyInviteService inviteService;
    private final PartyLeaderTransferService leaderTransferService;
    private final PartyQueryService queryService;
    private final PartyChatService chatService;

    private PartyUiFacade(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.repository = InMemoryPartyRepository.of(server);
        this.identityService = PlayerIdentityService.of(server);
        this.membershipService = new PartyMembershipService(repository, server::getTickCount);
        this.inviteService = new PartyInviteService(repository, server::getTickCount, REQUEST_LIFETIME_TICKS);
        this.leaderTransferService = new PartyLeaderTransferService(repository, server::getTickCount);
        this.queryService = new PartyQueryService(repository, identityService::resolvePlayerNameOrUuid);
        this.chatService = new PartyChatService(server, repository);
    }

    public static PartyUiFacade of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, PartyUiFacade::new);
        }
    }

    public static void forget(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public PartyAggregate createParty(ServerPlayer player) {
        return membershipService.createParty(requirePlayer(player).getUUID());
    }

    public PartyMembershipService.LeavePartyResult leaveParty(ServerPlayer player) {
        return membershipService.leaveParty(requirePlayer(player).getUUID());
    }

    public void disbandParty(ServerPlayer player) {
        membershipService.disbandParty(requirePlayer(player).getUUID());
    }

    public PartyRequest createInvite(ServerPlayer operator, String targetPlayerName) {
        ServerPlayer player = requirePlayer(operator);
        ServerPlayer target = resolveOnlinePlayer(targetPlayerName);
        return inviteService.createInvite(player.getUUID(), target.getUUID());
    }

    public PartyRequest createJoinApplication(ServerPlayer operator, String partyId) {
        return inviteService.createJoinApplication(requirePlayer(operator).getUUID(), partyId);
    }

    public PartyRequest acceptRequest(ServerPlayer operator, String requestId) {
        return inviteService.acceptRequest(requirePlayer(operator).getUUID(), requestId);
    }

    public PartyRequest refuseRequest(ServerPlayer operator, String requestId) {
        return inviteService.refuseRequest(requirePlayer(operator).getUUID(), requestId);
    }

    public PartyAggregate transferLeader(ServerPlayer operator, String nextLeaderName) {
        ServerPlayer player = requirePlayer(operator);
        PartyAggregate currentParty = repository.findByPlayerId(player.getUUID()).orElse(null);
        if (currentParty == null) {
            throw new org.mods.gd656conquest.server.social.party.domain.PartyError("该玩家当前不在任何队伍中");
        }
        ServerPlayer target = resolveCurrentPartyOnlineMember(currentParty, nextLeaderName);
        return leaderTransferService.transferLeader(player.getUUID(), target.getUUID());
    }

    public Optional<PartyQueryService.PartyDescription> findCurrentParty(ServerPlayer player) {
        ServerPlayer resolvedPlayer = requirePlayer(player);
        return queryService.findPartyByPlayer(resolvedPlayer.getUUID()).map(party -> queryService.describeParty(party.partyId()));
    }

    public List<PartyQueryService.PartyDescription> listAllParties() {
        List<PartyQueryService.PartyDescription> result = new ArrayList<>();
        for (PartyAggregate party : repository.findAll()) {
            if (party == null) {
                continue;
            }
            result.add(queryService.describeParty(party.partyId()));
        }
        result.sort(Comparator.comparingLong(PartyQueryService.PartyDescription::createdTick));
        return result;
    }

    public List<PartyRequestView> listPendingRequests(ServerPlayer player) {
        List<PartyRequestView> result = new ArrayList<>();
        for (PartyRequest request : inviteService.listPendingRequestsForPlayer(requirePlayer(player).getUUID())) {
            result.add(toRequestView(request));
        }
        return result;
    }

    public Optional<PartyRequestView> getRequestView(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        String normalizedRequestId = requestId.trim();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<PartyRequestView> request = findPendingRequestForPlayer(player, normalizedRequestId);
            if (request.isPresent()) {
                return request;
            }
        }
        return Optional.empty();
    }

    public void expireRequests() {
        inviteService.expireRequests();
    }

    public List<String> suggestKnownPlayerNames() {
        return identityService.suggestKnownPlayerNames();
    }

    public List<String> suggestPartyIds() {
        List<String> result = new ArrayList<>();
        for (PartyAggregate party : repository.findAll()) {
            if (party != null) {
                result.add(party.partyId());
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public List<String> suggestPendingRequestIds(ServerPlayer player) {
        List<String> result = new ArrayList<>();
        for (PartyRequestView request : listPendingRequests(player)) {
            result.add(request.requestId());
        }
        return result;
    }

    public Optional<PartyRequestView> findPendingRequestForPlayer(ServerPlayer player, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        String normalizedRequestId = requestId.trim();
        for (PartyRequestView request : listPendingRequests(player)) {
            if (normalizedRequestId.equals(request.requestId())) {
                return Optional.of(request);
            }
        }
        return Optional.empty();
    }

    public void sendPartyChat(ServerPlayer player, String message) {
        chatService.sendPartyChat(requirePlayer(player), message);
    }

    public Optional<ServerPlayer> findOnlinePlayer(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayer(playerId));
    }

    public String resolvePlayerName(UUID playerId) {
        return identityService.resolvePlayerNameOrUuid(playerId);
    }

    private PartyRequestView toRequestView(PartyRequest request) {
        String requestTypeText = request.type() == PartyRequestType.INVITE ? "队伍邀请" : "入队申请";
        return new PartyRequestView(
            request.requestId(),
            request.type(),
            requestTypeText,
            request.partyId(),
            request.fromPlayerId(),
            resolvePlayerName(request.fromPlayerId()),
            request.toPlayerId(),
            resolvePlayerName(request.toPlayerId()),
            request.createdTick(),
            request.expireTick()
        );
    }

    private ServerPlayer resolveOnlinePlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new org.mods.gd656conquest.server.social.party.domain.PartyError("目标玩家名称不能为空");
        }
        ServerPlayer exact = server.getPlayerList().getPlayerByName(playerName.trim());
        if (exact != null) {
            return exact;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.getScoreboardName() != null && player.getScoreboardName().equalsIgnoreCase(playerName.trim())) {
                return player;
            }
        }
        throw new org.mods.gd656conquest.server.social.party.domain.PartyError("目标玩家当前不在线，无法处理临时组队请求");
    }

    private ServerPlayer resolveCurrentPartyOnlineMember(PartyAggregate party, String playerName) {
        ServerPlayer target = resolveOnlinePlayer(playerName);
        if (!party.isMember(target.getUUID())) {
            throw new org.mods.gd656conquest.server.social.party.domain.PartyError("只能把队长转移给当前队伍中的成员");
        }
        return target;
    }

    private ServerPlayer requirePlayer(ServerPlayer player) {
        if (player == null) {
            throw new org.mods.gd656conquest.server.social.party.domain.PartyError("该操作只能由在线玩家执行");
        }
        return player;
    }

    public record PartyRequestView(
        String requestId,
        PartyRequestType type,
        String typeText,
        String partyId,
        UUID fromPlayerId,
        String fromPlayerName,
        UUID toPlayerId,
        String toPlayerName,
        long createdTick,
        long expireTick
    ) {
        public long remainingTicks(long currentTick) {
            return Math.max(0L, expireTick - Math.max(0L, currentTick));
        }

        public String remainingSecondsText(long currentTick) {
            double seconds = remainingTicks(currentTick) / 20.0D;
            return String.format(Locale.ROOT, "%.1f", seconds);
        }
    }
}
