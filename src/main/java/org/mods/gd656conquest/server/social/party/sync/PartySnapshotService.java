package org.mods.gd656conquest.server.social.party.sync;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PartySnapshotService {
    private static final Map<MinecraftServer, PartySnapshotService> INSTANCES = new WeakHashMap<>();

    private final Map<String, PartySnapshot> snapshotsById = new LinkedHashMap<>();
    private final Map<String, PartySnapshot> boundSnapshotsByPartyId = new LinkedHashMap<>();
    private final Map<String, PartyRoomTransitionRecord> transitionRecordsByPartyId = new LinkedHashMap<>();

    private PartySnapshotService() {
    }

    public static PartySnapshotService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, ignored -> new PartySnapshotService());
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

    public synchronized PartySnapshot createSnapshot(PartyAggregate party, String targetRoomId, long gameTick) {
        if (party == null) {
            throw new IllegalArgumentException("party");
        }
        List<UUID> members = new ArrayList<>(party.memberIds());
        PartySnapshot snapshot = new PartySnapshot(
            UUID.randomUUID().toString().replace("-", ""),
            party.partyId(),
            party.leaderId(),
            members,
            Math.max(0L, gameTick),
            targetRoomId
        );
        snapshotsById.put(snapshot.snapshotId(), snapshot);
        return snapshot;
    }

    public synchronized Optional<PartySnapshot> findSnapshot(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshotsById.get(snapshotId.trim()));
    }

    public synchronized void bindSnapshot(PartySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshotsById.put(snapshot.snapshotId(), snapshot);
        boundSnapshotsByPartyId.put(snapshot.partyId(), snapshot);
    }

    public synchronized Optional<PartySnapshot> findBoundSnapshot(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(boundSnapshotsByPartyId.get(partyId.trim()));
    }

    public synchronized void clearBoundSnapshot(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return;
        }
        boundSnapshotsByPartyId.remove(partyId.trim());
    }

    public synchronized void saveTransitionRecord(PartyRoomTransitionRecord record) {
        if (record == null) {
            return;
        }
        snapshotsById.put(record.snapshot().snapshotId(), record.snapshot());
        transitionRecordsByPartyId.put(record.partyId(), record);
    }

    public synchronized Optional<PartyRoomTransitionRecord> findTransitionRecord(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(transitionRecordsByPartyId.get(partyId.trim()));
    }

    public synchronized void clearTransitionRecord(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return;
        }
        transitionRecordsByPartyId.remove(partyId.trim());
    }

    public synchronized void clearPartyState(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return;
        }
        clearTransitionRecord(partyId);
        clearBoundSnapshot(partyId);
    }
}
