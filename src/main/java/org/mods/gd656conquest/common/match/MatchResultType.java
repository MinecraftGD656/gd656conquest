package org.mods.gd656conquest.common.match;

public enum MatchResultType {
    VICTORY(1),
    DEFEAT(2),
    DRAW(3);

    private final int networkId;

    MatchResultType(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static MatchResultType fromNetworkId(int networkId) {
        for (MatchResultType value : values()) {
            if (value.networkId == networkId) {
                return value;
            }
        }
        return DRAW;
    }
}
