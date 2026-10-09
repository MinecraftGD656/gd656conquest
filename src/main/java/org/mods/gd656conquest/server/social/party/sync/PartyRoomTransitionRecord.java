package org.mods.gd656conquest.server.social.party.sync;

import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.social.party.domain.PartyLockState;
import org.mods.gd656conquest.server.social.party.domain.PartyRoomActionState;
import org.mods.gd656conquest.server.social.party.domain.PartyStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PartyRoomTransitionRecord(
    PartySnapshot snapshot,
    PartyRoomActionState action,
    long partyVersion,
    PartyStatus previousStatus,
    PartyLockState previousLockState,
    PartyRoomActionState previousPendingRoomAction,
    Map<UUID, MemberOriginState> memberOrigins
) {
    public PartyRoomTransitionRecord {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        action = Objects.requireNonNull(action, "action");
        previousStatus = Objects.requireNonNull(previousStatus, "previousStatus");
        previousLockState = Objects.requireNonNull(previousLockState, "previousLockState");
        previousPendingRoomAction = Objects.requireNonNull(previousPendingRoomAction, "previousPendingRoomAction");
        if (partyVersion <= 0L) {
            throw new IllegalArgumentException("partyVersion");
        }
        memberOrigins = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(memberOrigins, "memberOrigins")));
    }

    public String partyId() {
        return snapshot.partyId();
    }

    public UUID leaderId() {
        return snapshot.leaderId();
    }

    public record MemberOriginState(
        UUID playerId,
        String roomId,
        boolean inMatchingQueue,
        boolean inRange,
        boolean inParkour,
        RoomTeamId teamId,
        String squadLabel
    ) {
        public MemberOriginState {
            playerId = Objects.requireNonNull(playerId, "playerId");
            roomId = roomId == null ? "" : roomId.trim();
            squadLabel = squadLabel == null ? "" : squadLabel.trim();
        }

        public boolean hasRoomSession() {
            return !roomId.isBlank();
        }
    }
}
