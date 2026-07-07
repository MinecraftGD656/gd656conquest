package org.mods.gd656conquest.server.social.party.domain;

import java.util.Objects;
import java.util.UUID;

public final class PartyMemberState {
    private final UUID playerId;
    private final String displayNameCache;
    private final long joinedTick;
    private final boolean online;
    private final PartyRole role;
    private final PartyReadyState readyState;

    public PartyMemberState(
        UUID playerId,
        String displayNameCache,
        long joinedTick,
        boolean online,
        PartyRole role,
        PartyReadyState readyState
    ) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.displayNameCache = displayNameCache == null ? "" : displayNameCache;
        this.joinedTick = joinedTick;
        this.online = online;
        this.role = Objects.requireNonNull(role, "role");
        this.readyState = Objects.requireNonNull(readyState, "readyState");
    }

    public UUID playerId() {
        return playerId;
    }

    public String displayNameCache() {
        return displayNameCache;
    }

    public long joinedTick() {
        return joinedTick;
    }

    public boolean online() {
        return online;
    }

    public PartyRole role() {
        return role;
    }

    public PartyReadyState readyState() {
        return readyState;
    }
}
