package org.mods.gd656conquest.server.social.party.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PartyAggregate {
    public static final int MAX_MEMBER_COUNT = 4;

    private final String partyId;
    private UUID leaderId;
    private final LinkedHashSet<UUID> memberIds;
    private final long createdTick;
    private long updatedTick;
    private long version;
    private PartyStatus status;
    private PartyLockState lockState;
    private PartyRoomActionState pendingRoomAction;

    private PartyAggregate(
        String partyId,
        UUID leaderId,
        LinkedHashSet<UUID> memberIds,
        long createdTick,
        long updatedTick,
        long version,
        PartyStatus status,
        PartyLockState lockState,
        PartyRoomActionState pendingRoomAction
    ) {
        this.partyId = validatePartyId(partyId);
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        this.memberIds = new LinkedHashSet<>(Objects.requireNonNull(memberIds, "memberIds"));
        this.createdTick = createdTick;
        this.updatedTick = updatedTick;
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
        this.lockState = Objects.requireNonNull(lockState, "lockState");
        this.pendingRoomAction = Objects.requireNonNull(pendingRoomAction, "pendingRoomAction");
        if (this.memberIds.isEmpty() || !this.memberIds.contains(this.leaderId)) {
            throw new PartyError("队长必须存在于队伍成员列表中");
        }
    }

    public static PartyAggregate createNew(String partyId, UUID leaderId, long gameTick) {
        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(Objects.requireNonNull(leaderId, "leaderId"));
        return new PartyAggregate(
            partyId,
            leaderId,
            members,
            gameTick,
            gameTick,
            1L,
            PartyStatus.IDLE,
            PartyLockState.NONE,
            PartyRoomActionState.NONE
        );
    }

    public static PartyAggregate restore(
        String partyId,
        UUID leaderId,
        Set<UUID> memberIds,
        long createdTick,
        long updatedTick,
        long version,
        PartyStatus status,
        PartyLockState lockState,
        PartyRoomActionState pendingRoomAction
    ) {
        return new PartyAggregate(
            partyId,
            leaderId,
            new LinkedHashSet<>(Objects.requireNonNull(memberIds, "memberIds")),
            createdTick,
            updatedTick,
            Math.max(1L, version),
            status == null ? PartyStatus.IDLE : status,
            lockState == null ? PartyLockState.NONE : lockState,
            pendingRoomAction == null ? PartyRoomActionState.NONE : pendingRoomAction
        );
    }

    public boolean isLeader(UUID playerId) {
        return playerId != null && playerId.equals(leaderId);
    }

    public boolean isMember(UUID playerId) {
        return playerId != null && memberIds.contains(playerId);
    }

    public int memberCount() {
        return memberIds.size();
    }

    public boolean isFull() {
        return memberCount() >= MAX_MEMBER_COUNT;
    }

    public void requireLeader(UUID operatorId) {
        if (!isLeader(operatorId)) {
            throw new PartyError("只有当前队长可以执行该操作");
        }
    }

    public void ensureUnlockedForMembershipChange() {
        if (lockState != PartyLockState.NONE) {
            throw new PartyError("当前队伍成员结构已锁定，暂时不能修改");
        }
    }

    public void lockMembership(long gameTick) {
        if (lockState == PartyLockState.MEMBERSHIP_LOCKED) {
            return;
        }
        lockState = PartyLockState.MEMBERSHIP_LOCKED;
        touch(gameTick);
    }

    public void unlockMembership(long gameTick) {
        if (lockState != PartyLockState.MEMBERSHIP_LOCKED) {
            return;
        }
        lockState = PartyLockState.NONE;
        touch(gameTick);
    }

    public void lockRoomTransition(long gameTick) {
        if (lockState == PartyLockState.ROOM_TRANSITION_LOCKED) {
            return;
        }
        lockState = PartyLockState.ROOM_TRANSITION_LOCKED;
        touch(gameTick);
    }

    public void unlockRoomTransition(long gameTick) {
        if (lockState != PartyLockState.ROOM_TRANSITION_LOCKED) {
            return;
        }
        lockState = PartyLockState.NONE;
        touch(gameTick);
    }

    public void changeLeader(UUID nextLeaderId, long gameTick) {
        UUID resolvedNextLeaderId = Objects.requireNonNull(nextLeaderId, "nextLeaderId");
        if (!isMember(resolvedNextLeaderId)) {
            throw new PartyError("新的队长必须是当前队伍成员");
        }
        if (resolvedNextLeaderId.equals(leaderId)) {
            return;
        }
        leaderId = resolvedNextLeaderId;
        touch(gameTick);
    }

    public String partyId() {
        return partyId;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public Set<UUID> memberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    public long createdTick() {
        return createdTick;
    }

    public long updatedTick() {
        return updatedTick;
    }

    public long version() {
        return version;
    }

    public PartyStatus status() {
        return status;
    }

    public PartyLockState lockState() {
        return lockState;
    }

    public PartyRoomActionState pendingRoomAction() {
        return pendingRoomAction;
    }

    private void touch(long gameTick) {
        updatedTick = Math.max(updatedTick, gameTick);
        version = Math.max(1L, version + 1L);
    }

    private static String validatePartyId(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            throw new PartyError("队伍 ID 不能为空");
        }
        return partyId.trim();
    }
}
