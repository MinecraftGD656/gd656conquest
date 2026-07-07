package org.mods.gd656conquest.server.social.party.domain;

import java.util.Objects;
import java.util.UUID;

public final class PartyRequest {
    private final String requestId;
    private final PartyRequestType type;
    private final String partyId;
    private final UUID fromPlayerId;
    private final UUID toPlayerId;
    private final long createdTick;
    private final long expireTick;
    private final PartyRequestStatus status;

    private PartyRequest(
        String requestId,
        PartyRequestType type,
        String partyId,
        UUID fromPlayerId,
        UUID toPlayerId,
        long createdTick,
        long expireTick,
        PartyRequestStatus status
    ) {
        this.requestId = validateRequestId(requestId);
        this.type = Objects.requireNonNull(type, "type");
        this.partyId = validatePartyId(partyId);
        this.fromPlayerId = Objects.requireNonNull(fromPlayerId, "fromPlayerId");
        this.toPlayerId = Objects.requireNonNull(toPlayerId, "toPlayerId");
        this.createdTick = Math.max(0L, createdTick);
        this.expireTick = Math.max(this.createdTick, expireTick);
        this.status = Objects.requireNonNull(status, "status");
    }

    public static PartyRequest createPending(
        String requestId,
        PartyRequestType type,
        String partyId,
        UUID fromPlayerId,
        UUID toPlayerId,
        long createdTick,
        long expireTick
    ) {
        return new PartyRequest(
            requestId,
            type,
            partyId,
            fromPlayerId,
            toPlayerId,
            createdTick,
            expireTick,
            PartyRequestStatus.PENDING
        );
    }

    public boolean isPending() {
        return status == PartyRequestStatus.PENDING;
    }

    public boolean isExpiredAt(long currentTick) {
        return currentTick >= expireTick;
    }

    public PartyRequest accept() {
        requirePending();
        return withStatus(PartyRequestStatus.ACCEPTED);
    }

    public PartyRequest refuse() {
        requirePending();
        return withStatus(PartyRequestStatus.REFUSED);
    }

    public PartyRequest expire() {
        if (status == PartyRequestStatus.EXPIRED) {
            return this;
        }
        requirePending();
        return withStatus(PartyRequestStatus.EXPIRED);
    }

    private void requirePending() {
        if (!isPending()) {
            throw new PartyError("该请求当前不是待处理状态");
        }
    }

    private PartyRequest withStatus(PartyRequestStatus nextStatus) {
        return new PartyRequest(
            requestId,
            type,
            partyId,
            fromPlayerId,
            toPlayerId,
            createdTick,
            expireTick,
            nextStatus
        );
    }

    public String requestId() {
        return requestId;
    }

    public PartyRequestType type() {
        return type;
    }

    public String partyId() {
        return partyId;
    }

    public UUID fromPlayerId() {
        return fromPlayerId;
    }

    public UUID toPlayerId() {
        return toPlayerId;
    }

    public long createdTick() {
        return createdTick;
    }

    public long expireTick() {
        return expireTick;
    }

    public PartyRequestStatus status() {
        return status;
    }

    private static String validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new PartyError("请求 ID 不能为空");
        }
        return requestId.trim();
    }

    private static String validatePartyId(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            throw new PartyError("队伍 ID 不能为空");
        }
        return partyId.trim();
    }
}
