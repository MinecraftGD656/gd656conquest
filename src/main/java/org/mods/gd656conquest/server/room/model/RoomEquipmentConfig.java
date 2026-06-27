package org.mods.gd656conquest.server.room.model;

import java.util.List;
import java.util.Objects;

public record RoomEquipmentConfig(List<String> campAEquipmentSlots, List<String> campBEquipmentSlots) {
    public RoomEquipmentConfig {
        Objects.requireNonNull(campAEquipmentSlots, "campAEquipmentSlots");
        Objects.requireNonNull(campBEquipmentSlots, "campBEquipmentSlots");
        campAEquipmentSlots = List.copyOf(campAEquipmentSlots);
        campBEquipmentSlots = List.copyOf(campBEquipmentSlots);
    }

    public static RoomEquipmentConfig defaults() {
        return new RoomEquipmentConfig(List.of(), List.of());
    }
}
