package org.mods.gd656conquest.server.social.party.application;

import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.domain.PartyRequest;
import org.mods.gd656conquest.server.social.party.domain.PartyRequestStatus;
import org.mods.gd656conquest.server.social.party.domain.PartyRequestType;
import org.mods.gd656conquest.server.social.party.infra.PartyRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class PartyInviteService {
    private final PartyRepository repository;
    private final LongSupplier tickSupplier;
    private final long requestLifetimeTicks;
    private final Map<String, PartyRequest> requestsById = new LinkedHashMap<>();

    public PartyInviteService(PartyRepository repository, LongSupplier tickSupplier, long requestLifetimeTicks) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "tickSupplier");
        this.requestLifetimeTicks = Math.max(1L, requestLifetimeTicks);
    }

    public PartyRequest createInvite(UUID leaderId, UUID targetPlayerId) {
        UUID resolvedLeaderId = requirePlayerId(leaderId);
        UUID resolvedTargetPlayerId = requirePlayerId(targetPlayerId);
        if (resolvedLeaderId.equals(resolvedTargetPlayerId)) {
            throw new PartyError("不能邀请自己加入自己的队伍");
        }
        expireRequests();
        PartyAggregate party = repository.findByPlayerId(resolvedLeaderId)
            .orElseThrow(() -> new PartyError("当前队长不在任何队伍中"));
        party.requireLeader(resolvedLeaderId);
        party.ensureUnlockedForMembershipChange();
        ensurePartyHasCapacity(party);
        if (repository.findByPlayerId(resolvedTargetPlayerId).isPresent()) {
            throw new PartyError("目标玩家当前已经在其他队伍中");
        }
        PartyRequest request = PartyRequest.createPending(
            generateRequestId(),
            PartyRequestType.INVITE,
            party.partyId(),
            resolvedLeaderId,
            resolvedTargetPlayerId,
            currentTick(),
            currentTick() + requestLifetimeTicks
        );
        requestsById.put(request.requestId(), request);
        return request;
    }

    public PartyRequest createJoinApplication(UUID applicantId, String partyId) {
        UUID resolvedApplicantId = requirePlayerId(applicantId);
        String resolvedPartyId = requirePartyId(partyId);
        expireRequests();
        if (repository.findByPlayerId(resolvedApplicantId).isPresent()) {
            throw new PartyError("申请加入前必须先退出当前队伍");
        }
        PartyAggregate party = repository.findByPartyId(resolvedPartyId)
            .orElseThrow(() -> new PartyError("目标队伍不存在"));
        party.ensureUnlockedForMembershipChange();
        ensurePartyHasCapacity(party);
        if (party.isMember(resolvedApplicantId)) {
            throw new PartyError("该玩家已经在目标队伍中");
        }
        PartyRequest request = PartyRequest.createPending(
            generateRequestId(),
            PartyRequestType.JOIN_APPLICATION,
            party.partyId(),
            resolvedApplicantId,
            party.leaderId(),
            currentTick(),
            currentTick() + requestLifetimeTicks
        );
        requestsById.put(request.requestId(), request);
        return request;
    }

    public PartyRequest acceptRequest(UUID operatorId, String requestId) {
        UUID resolvedOperatorId = requirePlayerId(operatorId);
        expireRequests();
        PartyRequest request = requirePendingRequest(requestId);
        requireRequestTarget(request, resolvedOperatorId);
        PartyRequest accepted = switch (request.type()) {
            case INVITE -> acceptInvite(request, resolvedOperatorId);
            case JOIN_APPLICATION -> acceptJoinApplication(request, resolvedOperatorId);
        };
        requestsById.put(accepted.requestId(), accepted);
        return accepted;
    }

    public PartyRequest refuseRequest(UUID operatorId, String requestId) {
        UUID resolvedOperatorId = requirePlayerId(operatorId);
        expireRequests();
        PartyRequest request = requirePendingRequest(requestId);
        requireRequestTarget(request, resolvedOperatorId);
        if (request.type() == PartyRequestType.JOIN_APPLICATION) {
            PartyAggregate party = repository.findByPartyId(request.partyId()).orElse(null);
            if (party == null || !party.isLeader(resolvedOperatorId)) {
                PartyRequest expired = expireRequestWithMessage(request, "该申请对应的队伍状态已变化，无法处理");
                requestsById.put(expired.requestId(), expired);
                throw new PartyError("该申请对应的队伍状态已变化，无法处理");
            }
        }
        PartyRequest refused = request.refuse();
        requestsById.put(refused.requestId(), refused);
        return refused;
    }

    public List<PartyRequest> listPendingRequestsForPlayer(UUID playerId) {
        UUID resolvedPlayerId = requirePlayerId(playerId);
        expireRequests();
        List<PartyRequest> result = new ArrayList<>();
        for (Map.Entry<String, PartyRequest> entry : new ArrayList<>(requestsById.entrySet())) {
            PartyRequest request = refreshRequestState(entry.getValue());
            entry.setValue(request);
            if (request != null && request.isPending() && resolvedPlayerId.equals(request.toPlayerId())) {
                result.add(request);
            }
        }
        result.sort((left, right) -> Long.compare(left.createdTick(), right.createdTick()));
        return result;
    }

    public int expireRequests() {
        int expiredCount = 0;
        long currentTick = currentTick();
        for (Map.Entry<String, PartyRequest> entry : new ArrayList<>(requestsById.entrySet())) {
            PartyRequest request = entry.getValue();
            if (request == null || !request.isPending() || !request.isExpiredAt(currentTick)) {
                continue;
            }
            entry.setValue(request.expire());
            expiredCount++;
        }
        return expiredCount;
    }

    private PartyRequest acceptInvite(PartyRequest request, UUID operatorId) {
        PartyAggregate party = repository.findByPartyId(request.partyId()).orElse(null);
        if (party == null) {
            PartyRequest expired = expireRequestWithMessage(request, "邀请对应的队伍已不存在");
            requestsById.put(expired.requestId(), expired);
            throw new PartyError("邀请对应的队伍已不存在");
        }
        if (!party.isLeader(request.fromPlayerId())) {
            PartyRequest expired = expireRequestWithMessage(request, "该邀请已失效，原邀请人已不是当前队长");
            requestsById.put(expired.requestId(), expired);
            throw new PartyError("该邀请已失效，原邀请人已不是当前队长");
        }
        party.ensureUnlockedForMembershipChange();
        if (repository.findByPlayerId(operatorId).isPresent()) {
            PartyRequest expired = expireRequestWithMessage(request, "你当前已经在其他队伍中，无法接受邀请");
            requestsById.put(expired.requestId(), expired);
            throw new PartyError("你当前已经在其他队伍中，无法接受邀请");
        }
        ensurePartyHasCapacity(party);
        savePartyWithNewMember(party, operatorId);
        return request.accept();
    }

    private PartyRequest acceptJoinApplication(PartyRequest request, UUID operatorId) {
        PartyAggregate party = repository.findByPartyId(request.partyId()).orElse(null);
        if (party == null) {
            PartyRequest expired = expireRequestWithMessage(request, "申请对应的队伍已不存在");
            requestsById.put(expired.requestId(), expired);
            throw new PartyError("申请对应的队伍已不存在");
        }
        if (!party.isLeader(operatorId) || !party.isLeader(request.toPlayerId())) {
            PartyRequest expired = expireRequestWithMessage(request, "该申请已失效，审批人已不是当前队长");
            requestsById.put(expired.requestId(), expired);
            throw new PartyError("该申请已失效，审批人已不是当前队长");
        }
        party.ensureUnlockedForMembershipChange();
        if (repository.findByPlayerId(request.fromPlayerId()).isPresent()) {
            PartyRequest expired = expireRequestWithMessage(request, "申请玩家当前已经在其他队伍中");
            requestsById.put(expired.requestId(), expired);
            throw new PartyError("申请玩家当前已经在其他队伍中");
        }
        ensurePartyHasCapacity(party);
        savePartyWithNewMember(party, request.fromPlayerId());
        return request.accept();
    }

    private void savePartyWithNewMember(PartyAggregate party, UUID memberId) {
        LinkedHashSet<UUID> nextMembers = new LinkedHashSet<>(party.memberIds());
        nextMembers.add(memberId);
        if (nextMembers.size() > PartyAggregate.MAX_MEMBER_COUNT) {
            throw new PartyError("当前队伍人数已达上限 4 人");
        }
        PartyAggregate updated = PartyAggregate.restore(
            party.partyId(),
            party.leaderId(),
            nextMembers,
            party.createdTick(),
            currentTick(),
            party.version() + 1L,
            party.status(),
            party.lockState(),
            party.pendingRoomAction()
        );
        repository.save(updated);
    }

    private void ensurePartyHasCapacity(PartyAggregate party) {
        if (party != null && party.isFull()) {
            throw new PartyError("当前队伍人数已达上限 4 人");
        }
    }

    private PartyRequest requirePendingRequest(String requestId) {
        String resolvedRequestId = requireRequestId(requestId);
        PartyRequest request = refreshRequestState(requestsById.get(resolvedRequestId));
        if (request != null) {
            requestsById.put(resolvedRequestId, request);
        }
        if (request == null) {
            throw new PartyError("未找到对应的请求");
        }
        if (request.status() == PartyRequestStatus.EXPIRED) {
            throw new PartyError("该请求已过期");
        }
        if (request.status() == PartyRequestStatus.ACCEPTED) {
            throw new PartyError("该请求已经处理为接受状态");
        }
        if (request.status() == PartyRequestStatus.REFUSED) {
            throw new PartyError("该请求已经被拒绝");
        }
        return request;
    }

    private PartyRequest expireRequestWithMessage(PartyRequest request, String ignoredMessage) {
        return request.expire();
    }

    private PartyRequest refreshRequestState(PartyRequest request) {
        if (request == null || !request.isPending()) {
            return request;
        }
        if (request.isExpiredAt(currentTick())) {
            return request.expire();
        }
        PartyAggregate party = repository.findByPartyId(request.partyId()).orElse(null);
        if (party == null) {
            return request.expire();
        }
        if (request.type() == PartyRequestType.INVITE && !party.isLeader(request.fromPlayerId())) {
            return request.expire();
        }
        if (request.type() == PartyRequestType.JOIN_APPLICATION && !party.isLeader(request.toPlayerId())) {
            return request.expire();
        }
        return request;
    }

    private void requireRequestTarget(PartyRequest request, UUID operatorId) {
        if (!operatorId.equals(request.toPlayerId())) {
            throw new PartyError("只有请求接收方可以处理该请求");
        }
    }

    private long currentTick() {
        return Math.max(0L, tickSupplier.getAsLong());
    }

    private UUID requirePlayerId(UUID playerId) {
        if (playerId == null) {
            throw new PartyError("玩家 ID 不能为空");
        }
        return playerId;
    }

    private String requirePartyId(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            throw new PartyError("队伍 ID 不能为空");
        }
        return partyId.trim();
    }

    private String requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new PartyError("请求 ID 不能为空");
        }
        return requestId.trim();
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
