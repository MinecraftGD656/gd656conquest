package org.mods.gd656conquest.server.social.party.application;

import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.infra.PartyRepository;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class PartyMembershipService {
    private final PartyRepository repository;
    private final LongSupplier tickSupplier;

    public PartyMembershipService(PartyRepository repository, LongSupplier tickSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "tickSupplier");
    }

    public PartyAggregate createParty(UUID leaderId) {
        UUID resolvedLeaderId = requirePlayerId(leaderId);
        if (repository.findByPlayerId(resolvedLeaderId).isPresent()) {
            throw new PartyError("该玩家当前已经在队伍中");
        }
        PartyAggregate party = PartyAggregate.createNew(generatePartyId(), resolvedLeaderId, currentTick());
        repository.save(party);
        return party;
    }

    public LeavePartyResult leaveParty(UUID playerId) {
        return removeMember(playerId,false);
    }

    public LeavePartyResult leaveDisconnected(UUID playerId) {
        return removeMember(playerId,true);
    }

    private LeavePartyResult removeMember(UUID playerId,boolean disconnected) {
        UUID resolvedPlayerId = requirePlayerId(playerId);
        PartyAggregate party = repository.findByPlayerId(resolvedPlayerId)
            .orElseThrow(() -> new PartyError("该玩家当前不在任何队伍中"));
        if(!disconnected)party.ensureUnlockedForMembershipChange();
        boolean empty=party.removeMember(resolvedPlayerId,currentTick());
        if(empty)repository.delete(party.partyId());else repository.save(party);
        return new LeavePartyResult(party.partyId(),empty);
    }

    public void kickMember(UUID operatorId,UUID targetId) {
        PartyAggregate party=repository.findByPlayerId(requirePlayerId(operatorId)).orElseThrow(()->new PartyError("你当前不在队伍中"));
        party.requireLeader(operatorId);party.ensureUnlockedForMembershipChange();
        if(operatorId.equals(targetId))throw new PartyError("退出自己的队伍请使用离开");
        party.removeMember(requirePlayerId(targetId),currentTick());repository.save(party);
    }

    public void setOpen(UUID operatorId,boolean open) {
        PartyAggregate party=repository.findByPlayerId(requirePlayerId(operatorId)).orElseThrow(()->new PartyError("你当前不在队伍中"));
        party.requireLeader(operatorId);party.setOpen(open,currentTick());repository.save(party);
    }

    public void joinOpen(UUID playerId,String partyId) {
        requirePlayerId(playerId);
        if(repository.findByPlayerId(playerId).isPresent())throw new PartyError("请先离开当前好友队伍");
        PartyAggregate party=repository.findByPartyId(partyId).orElseThrow(()->new PartyError("队伍已解散"));
        if(!party.open())throw new PartyError("该队伍需要邀请或申请");
        party.addMember(playerId,currentTick());repository.save(party);
    }

    public void disbandParty(UUID operatorId) {
        UUID resolvedOperatorId = requirePlayerId(operatorId);
        PartyAggregate party = repository.findByPlayerId(resolvedOperatorId)
            .orElseThrow(() -> new PartyError("该玩家当前不在任何队伍中"));
        party.ensureUnlockedForMembershipChange();
        party.requireLeader(resolvedOperatorId);
        repository.delete(party.partyId());
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

    private String generatePartyId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record LeavePartyResult(String partyId, boolean disbanded) {
    }
}
