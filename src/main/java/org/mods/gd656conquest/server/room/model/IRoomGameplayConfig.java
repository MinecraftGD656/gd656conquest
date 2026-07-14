package org.mods.gd656conquest.server.room.model;

import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public interface IRoomGameplayConfig {
    String mapId();

    String campADisplayName(RoomCommonConfig commonConfig);

    String campBDisplayName(RoomCommonConfig commonConfig);

    List<RoomCapturePointDefinition> definedObjectivePoints();

    default List<RoomCapturePointDefinition> configuredObjectivePoints() {
        return definedObjectivePoints().stream()
            .filter(RoomCapturePointDefinition::isRuntimeActive)
            .toList();
    }

    default Optional<RoomCapturePointDefinition> findConfiguredObjectivePoint(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return definedObjectivePoints().stream()
            .filter(point -> normalized.equals(point.code()))
            .findFirst();
    }

    default boolean hasConfiguredObjectivePoints() {
        return !configuredObjectivePoints().isEmpty();
    }

    void validate(RoomConfigValidationResult result, RoomDefinition definition);
}
