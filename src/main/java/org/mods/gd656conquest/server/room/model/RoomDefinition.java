package org.mods.gd656conquest.server.room.model;

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
    IRoomGameplayConfig gameplayConfig
) {
    public RoomDefinition {
        Objects.requireNonNull(roomId, "roomId");
        displayName = normalizeDisplayName(displayName, roomId);
        Objects.requireNonNull(gameplayId, "gameplayId");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        commonConfig = commonConfig == null ? RoomCommonConfig.defaults() : commonConfig;
        gameplayConfig = gameplayConfig == null ? RoomConquestConfig.defaults() : gameplayConfig;
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
            defaultGameplayConfig(gameplayId)
        );
    }

    public RoomDefinition withLifecycleState(RoomLifecycleState nextState) {
        return new RoomDefinition(
            roomId,
            displayName,
            gameplayId,
            nextState,
            commonConfig,
            gameplayConfig
        );
    }

    public String gameplayMapId() {
        return gameplayConfig.mapId();
    }

    public String gameplayTeamDisplayName(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return gameplayConfig.campADisplayName(commonConfig);
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return gameplayConfig.campBDisplayName(commonConfig);
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
        return gameplayConfig.configuredObjectivePoints();
    }

    public List<RoomCapturePointDefinition> definedObjectivePoints() {
        return gameplayConfig.definedObjectivePoints();
    }

    public Optional<RoomCapturePointDefinition> findConfiguredObjectivePoint(String code) {
        return gameplayConfig.findConfiguredObjectivePoint(code);
    }

    public boolean hasConfiguredObjectivePoints() {
        return gameplayConfig.hasConfiguredObjectivePoints();
    }

    public RoomConquestConfig conquestConfig() {
        return gameplayConfig instanceof RoomConquestConfig cq ? cq : RoomConquestConfig.defaults();
    }

    public RoomBreakthroughConfig breakthroughConfig() {
        return gameplayConfig instanceof RoomBreakthroughConfig bt ? bt : RoomBreakthroughConfig.defaults();
    }

    public RoomDeathmatchConfig deathmatchConfig() {
        return gameplayConfig instanceof RoomDeathmatchConfig dm ? dm : RoomDeathmatchConfig.defaults();
    }

    private static IRoomGameplayConfig defaultGameplayConfig(RoomGameplayId gameplayId) {
        if (RoomGameplayId.BREAKTHROUGH.equals(gameplayId)) {
            return RoomBreakthroughConfig.defaults();
        }
        if (RoomGameplayId.DEATHMATCH.equals(gameplayId)) {
            return RoomDeathmatchConfig.defaults();
        }
        return RoomConquestConfig.defaults();
    }

    private static String normalizeDisplayName(String displayName, String roomId) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return roomId == null ? "" : roomId.trim();
    }

}
