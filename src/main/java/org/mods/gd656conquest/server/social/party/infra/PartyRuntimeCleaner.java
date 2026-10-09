package org.mods.gd656conquest.server.social.party.infra;

import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PartyRuntimeCleaner {
    public List<String> findEmptyPartyIds(Collection<PartyAggregate> parties) {
        List<String> result = new ArrayList<>();
        if (parties == null) {
            return result;
        }
        for (PartyAggregate party : parties) {
            if (party == null) {
                continue;
            }
            if (party.memberCount() <= 0) {
                result.add(party.partyId());
            }
        }
        return result;
    }
}
