package org.mods.gd656conquest.server.social.party.sync;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PartySnapshot(
    String snapshotId,
    String partyId,
    UUID leaderId,
    List<UUID> memberIds,
    long createdTick,
    String targetRoomId
) {
    public PartySnapshot {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId");
        }
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("partyId");
        }
        leaderId = Objects.requireNonNull(leaderId, "leaderId");
        memberIds = List.copyOf(Objects.requireNonNull(memberIds, "memberIds"));
        if (memberIds.isEmpty() || !memberIds.contains(leaderId)) {
            throw new IllegalArgumentException("leader must be inside memberIds");
        }
        targetRoomId = targetRoomId == null ? "" : targetRoomId.trim();
    }
}
