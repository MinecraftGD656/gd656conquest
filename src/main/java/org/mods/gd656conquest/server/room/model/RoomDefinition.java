package org.mods.gd656conquest.server.room.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record RoomDefinition(
    String roomId,
    String displayName,
    RoomGameplayId gameplayId,
    RoomLifecycleState lifecycleState,
    RoomCommonConfig commonConfig,
    RoomConquestConfig conquestConfig,
    RoomBreakthroughConfig breakthroughConfig
) {
    public RoomDefinition {
        Objects.requireNonNull(roomId, "roomId");
        displayName = normalizeDisplayName(displayName, roomId);
        Objects.requireNonNull(gameplayId, "gameplayId");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        commonConfig = commonConfig == null ? RoomCommonConfig.defaults() : commonConfig;
        conquestConfig = conquestConfig == null ? RoomConquestConfig.defaults() : conquestConfig;
        breakthroughConfig = breakthroughConfig == null ? RoomBreakthroughConfig.defaults() : breakthroughConfig;
        roomId = roomId.trim().toLowerCase(Locale.ROOT);
        if (roomId.isEmpty()) {
            throw new IllegalArgumentException("房间ID不能为空");
        }
    }

    public RoomDefinition(String roomId, RoomGameplayId gameplayId, RoomLifecycleState lifecycleState) {
        this(
            roomId,
            roomId,
            gameplayId,
            lifecycleState,
            RoomCommonConfig.defaults(),
            RoomConquestConfig.defaults(),
            RoomBreakthroughConfig.defaults()
        );
    }

    public RoomDefinition withLifecycleState(RoomLifecycleState nextState) {
        return new RoomDefinition(
            roomId,
            displayName,
            gameplayId,
            nextState,
            commonConfig,
            conquestConfig,
            breakthroughConfig
        );
    }

    public String gameplayMapId() {
        if (RoomGameplayId.BREAKTHROUGH.equals(gameplayId)) {
            return breakthroughConfig.mapId();
        }
        return conquestConfig.captureConfig().mapId();
    }

    public String gameplayTeamDisplayName(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return RoomGameplayId.BREAKTHROUGH.equals(gameplayId)
                ? breakthroughConfig.campADisplayName()
                : commonConfig.campADisplayName();
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return RoomGameplayId.BREAKTHROUGH.equals(gameplayId)
                ? breakthroughConfig.campBDisplayName()
                : commonConfig.campBDisplayName();
        }
        return "";
    }

    public int gameplayBoundaryPenaltySeconds() {
        return commonConfig.outsideBoundaryDeathSeconds();
    }

    public int gameplayAircraftBoundaryCleanupSeconds() {
        return commonConfig.aircraftOutsideBoundaryCleanupSeconds();
    }

    public int gameplayRespawnDelaySeconds() {
        return commonConfig.respawnDelaySeconds();
    }

    public List<RoomCapturePointDefinition> configuredObjectivePoints() {
        return definedObjectivePoints().stream()
            .filter(RoomCapturePointDefinition::isRuntimeActive)
            .toList();
    }

    public List<RoomCapturePointDefinition> definedObjectivePoints() {
        List<RoomCapturePointDefinition> points = RoomGameplayId.BREAKTHROUGH.equals(gameplayId)
            ? new ArrayList<>(breakthroughConfig.points().values())
            : new ArrayList<>(conquestConfig.captureConfig().points().values());
        return points.stream()
            .filter(Objects::nonNull)
            .toList();
    }

    public Optional<RoomCapturePointDefinition> findConfiguredObjectivePoint(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return definedObjectivePoints().stream()
            .filter(point -> normalized.equals(point.code()))
            .findFirst();
    }

    public boolean hasConfiguredObjectivePoints() {
        return !configuredObjectivePoints().isEmpty();
    }

    private static String normalizeDisplayName(String displayName, String roomId) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return roomId == null ? "" : roomId.trim();
    }

}
