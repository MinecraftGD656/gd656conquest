package org.mods.gd656conquest.server.social.party.infra;

import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PartyRepository {
    Optional<PartyAggregate> findByPartyId(String partyId);

    Optional<PartyAggregate> findByPlayerId(UUID playerId);

    void save(PartyAggregate party);

    void delete(String partyId);

    Collection<PartyAggregate> findAll();
}
