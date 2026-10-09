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
        UUID resolvedPlayerId = requirePlayerId(playerId);
        PartyAggregate party = repository.findByPlayerId(resolvedPlayerId)
            .orElseThrow(() -> new PartyError("该玩家当前不在任何队伍中"));
        party.ensureUnlockedForMembershipChange();
        if (party.isLeader(resolvedPlayerId)) {
            repository.delete(party.partyId());
            return new LeavePartyResult(party.partyId(), true);
        }
        LinkedHashSet<UUID> retainedMembers = new LinkedHashSet<>(party.memberIds());
        retainedMembers.remove(resolvedPlayerId);
        PartyAggregate updated = PartyAggregate.restore(
            party.partyId(),
            party.leaderId(),
            retainedMembers,
            party.createdTick(),
            currentTick(),
            party.version() + 1L,
            party.status(),
            party.lockState(),
            party.pendingRoomAction()
        );
        repository.save(updated);
        return new LeavePartyResult(updated.partyId(), false);
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
