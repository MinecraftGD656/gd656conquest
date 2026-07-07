package org.mods.gd656conquest.server.social.party.application;

import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.infra.PartyRepository;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class PartyLeaderTransferService {
    private final PartyRepository repository;
    private final LongSupplier tickSupplier;

    public PartyLeaderTransferService(PartyRepository repository, LongSupplier tickSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "tickSupplier");
    }

    public PartyAggregate transferLeader(UUID operatorId, UUID nextLeaderId) {
        UUID resolvedOperatorId = requirePlayerId(operatorId);
        UUID resolvedNextLeaderId = requirePlayerId(nextLeaderId);
        PartyAggregate party = repository.findByPlayerId(resolvedOperatorId)
            .orElseThrow(() -> new PartyError("该玩家当前不在任何队伍中"));
        party.requireLeader(resolvedOperatorId);
        validateTransferTarget(party, resolvedNextLeaderId);
        party.changeLeader(resolvedNextLeaderId, currentTick());
        repository.save(party);
        return party;
    }

    public void validateTransferTarget(PartyAggregate party, UUID nextLeaderId) {
        if (party == null) {
            throw new PartyError("未找到对应的队伍");
        }
        UUID resolvedNextLeaderId = requirePlayerId(nextLeaderId);
        if (!party.isMember(resolvedNextLeaderId)) {
            throw new PartyError("只能把队长转移给当前队伍中的成员");
        }
        if (party.isLeader(resolvedNextLeaderId)) {
            throw new PartyError("目标玩家已经是当前队长");
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
}
