package org.mods.gd656conquest.server.social.party.infra;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.player.identity.PlayerIdentityService;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyMemberState;
import org.mods.gd656conquest.server.social.party.domain.PartyReadyState;
import org.mods.gd656conquest.server.social.party.domain.PartyRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class InMemoryPartyRepository implements PartyRepository {
    private static final Map<MinecraftServer, InMemoryPartyRepository> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final PartyRuntimeCleaner runtimeCleaner = new PartyRuntimeCleaner();
    private final Map<String, PartyAggregate> partiesById = new LinkedHashMap<>();
    private final Map<UUID, String> partyIdByPlayerId = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<UUID, PartyMemberState>> memberStatesByPartyId = new LinkedHashMap<>();
    private boolean initialized;

    private InMemoryPartyRepository(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static InMemoryPartyRepository of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, InMemoryPartyRepository::new);
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

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        rebuildIndexes();
    }

    public synchronized void shutdown() {
        partiesById.clear();
        partyIdByPlayerId.clear();
        memberStatesByPartyId.clear();
        initialized = false;
    }

    public synchronized void rebuildIndexes() {
        partyIdByPlayerId.clear();
        PlayerIdentityService identities = PlayerIdentityService.of(server);
        for (PartyAggregate party : partiesById.values()) {
            if (party == null) {
                continue;
            }
            LinkedHashMap<UUID, PartyMemberState> memberStates = memberStatesByPartyId.computeIfAbsent(
                party.partyId(),
                ignored -> new LinkedHashMap<>()
            );
            for (UUID memberId : party.memberIds()) {
                if (memberId == null) {
                    continue;
                }
                partyIdByPlayerId.put(memberId, party.partyId());
                PartyMemberState previous = memberStates.get(memberId);
                PartyRole role = party.isLeader(memberId) ? PartyRole.LEADER : PartyRole.MEMBER;
                memberStates.put(memberId, new PartyMemberState(
                    memberId,
                    identities.resolvePlayerNameOrUuid(memberId),
                    previous == null ? party.createdTick() : previous.joinedTick(),
                    true,
                    role,
                    previous == null ? PartyReadyState.UNKNOWN : previous.readyState()
                ));
            }
            memberStates.keySet().removeIf(memberId -> memberId == null || !party.isMember(memberId));
        }
        memberStatesByPartyId.keySet().removeIf(partyId -> !partiesById.containsKey(partyId));
    }

    public synchronized boolean pruneEmptyParties() {
        boolean changed = false;
        for (String partyId : runtimeCleaner.findEmptyPartyIds(partiesById.values())) {
            deleteInternal(partyId);
            changed = true;
        }
        return changed;
    }

    public synchronized void handlePlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        initialize();
        String partyId = partyIdByPlayerId.get(player.getUUID());
        if (partyId == null || partyId.isBlank()) {
            return;
        }
        PartyAggregate party = partiesById.get(partyId);
        if (party == null || !party.isMember(player.getUUID())) {
            return;
        }
        if (party.isLeader(player.getUUID())) {
            deleteInternal(partyId);
            return;
        }
        LinkedHashSet<UUID> retainedMembers = new LinkedHashSet<>(party.memberIds());
        retainedMembers.remove(player.getUUID());
        if (retainedMembers.isEmpty()) {
            deleteInternal(partyId);
            return;
        }
        PartyAggregate updated = PartyAggregate.restore(
            party.partyId(),
            party.leaderId(),
            retainedMembers,
            party.createdTick(),
            server.getTickCount(),
            party.version() + 1L,
            party.status(),
            party.lockState(),
            party.pendingRoomAction()
        );
        partiesById.put(updated.partyId(), updated);
        LinkedHashMap<UUID, PartyMemberState> memberStates = memberStatesByPartyId.computeIfAbsent(updated.partyId(), ignored -> new LinkedHashMap<>());
        memberStates.remove(player.getUUID());
        rebuildIndexes();
    }

    public synchronized Optional<PartyMemberState> findMemberState(String partyId, UUID playerId) {
        initialize();
        if (partyId == null || partyId.isBlank() || playerId == null) {
            return Optional.empty();
        }
        Map<UUID, PartyMemberState> memberStates = memberStatesByPartyId.get(partyId.trim());
        if (memberStates == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(memberStates.get(playerId));
    }

    @Override
    public synchronized Optional<PartyAggregate> findByPartyId(String partyId) {
        initialize();
        if (partyId == null || partyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(partiesById.get(partyId.trim()));
    }

    @Override
    public synchronized Optional<PartyAggregate> findByPlayerId(UUID playerId) {
        initialize();
        if (playerId == null) {
            return Optional.empty();
        }
        String partyId = partyIdByPlayerId.get(playerId);
        if (partyId == null || partyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(partiesById.get(partyId));
    }

    @Override
    public synchronized void save(PartyAggregate party) {
        initialize();
        if (party == null) {
            return;
        }
        partiesById.put(party.partyId(), party);
        rebuildIndexes();
    }

    @Override
    public synchronized void delete(String partyId) {
        initialize();
        deleteInternal(partyId);
    }

    @Override
    public synchronized Collection<PartyAggregate> findAll() {
        initialize();
        return new ArrayList<>(partiesById.values());
    }

    private void deleteInternal(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return;
        }
        String normalizedPartyId = partyId.trim();
        PartyAggregate removed = partiesById.remove(normalizedPartyId);
        memberStatesByPartyId.remove(normalizedPartyId);
        if (removed != null) {
            for (UUID memberId : removed.memberIds()) {
                partyIdByPlayerId.remove(memberId);
            }
            return;
        }
        partyIdByPlayerId.entrySet().removeIf(entry -> normalizedPartyId.equals(entry.getValue()));
    }
}
