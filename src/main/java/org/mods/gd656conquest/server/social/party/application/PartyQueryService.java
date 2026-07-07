package org.mods.gd656conquest.server.social.party.application;

import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.domain.PartyLockState;
import org.mods.gd656conquest.server.social.party.domain.PartyRole;
import org.mods.gd656conquest.server.social.party.domain.PartyStatus;
import org.mods.gd656conquest.server.social.party.infra.PartyRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class PartyQueryService {
    private final PartyRepository repository;
    private final Function<UUID, String> playerNameResolver;

    public PartyQueryService(PartyRepository repository, Function<UUID, String> playerNameResolver) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.playerNameResolver = Objects.requireNonNull(playerNameResolver, "playerNameResolver");
    }

    public Optional<PartyAggregate> findPartyByPlayer(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return repository.findByPlayerId(playerId);
    }

    public Optional<PartyAggregate> findPartyById(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByPartyId(partyId);
    }

    public PartyDescription describeParty(String partyId) {
        PartyAggregate party = repository.findByPartyId(partyId)
            .orElseThrow(() -> new PartyError("未找到对应的队伍"));
        List<PartyMemberDescription> members = new ArrayList<>();
        for (UUID memberId : party.memberIds()) {
            PartyRole role = party.isLeader(memberId) ? PartyRole.LEADER : PartyRole.MEMBER;
            members.add(new PartyMemberDescription(
                memberId,
                resolvePlayerName(memberId),
                role
            ));
        }
        return new PartyDescription(
            party.partyId(),
            party.leaderId(),
            resolvePlayerName(party.leaderId()),
            party.memberCount(),
            party.status(),
            party.lockState(),
            party.createdTick(),
            party.updatedTick(),
            party.version(),
            members
        );
    }

    private String resolvePlayerName(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        String resolved = playerNameResolver.apply(playerId);
        if (resolved == null || resolved.isBlank()) {
            return playerId.toString();
        }
        return resolved.trim();
    }

    public record PartyDescription(
        String partyId,
        UUID leaderId,
        String leaderName,
        int memberCount,
        PartyStatus status,
        PartyLockState lockState,
        long createdTick,
        long updatedTick,
        long version,
        List<PartyMemberDescription> members
    ) {
    }

    public record PartyMemberDescription(
        UUID playerId,
        String displayName,
        PartyRole role
    ) {
    }
}
