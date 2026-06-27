package org.mods.gd656conquest.server.facility.range;

import java.util.Objects;

public record RangePlayerSession(String inventorySnapshot) {
    public RangePlayerSession {
        Objects.requireNonNull(inventorySnapshot, "inventorySnapshot");
    }
}
