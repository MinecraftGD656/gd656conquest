package org.mods.gd656conquest.server.room.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public record RoomConquestConfig(
    Map<RoomTeamId, RoomSpawnGroupConfig> teamSpawnGroups,
    Map<RoomTeamId, RoomPosition> teamDeployIcons,
    RoomViewPosition deployViewPosition,
    List<RoomPosition> sharedBoundary,
    List<RoomPosition> campABoundary,
    List<RoomPosition> campBBoundary,
    RoomCaptureConfig captureConfig,
    List<RoomPosition> ammoBoxPositions,
    List<RoomPosition> medicalBoxPositions
) implements IRoomGameplayConfig {
    public RoomConquestConfig {
        captureConfig = captureConfig == null ? RoomCaptureConfig.defaults() : captureConfig;
        teamSpawnGroups = normalizeSpawnGroups(teamSpawnGroups);
        teamDeployIcons = normalizeDeployIcons(teamDeployIcons);
        sharedBoundary = copyBoundary(sharedBoundary);
        campABoundary = copyBoundary(campABoundary);
        campBBoundary = copyBoundary(campBBoundary);
        ammoBoxPositions = copyBoundary(ammoBoxPositions);
        medicalBoxPositions = copyBoundary(medicalBoxPositions);
    }
    
    @Override
    public void validate(org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult result, RoomDefinition definition) {
        if (definition == null) {
            return;
        }
        var conquest = this;
        var capture = conquest.captureConfig();
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomViewPosition(result, "conquest.deployViewPosition", "deployViewPosition", conquest.deployViewPosition());
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(result, "conquest.teamDeployIconCampA", "teamDeployIconCampA", conquest.teamDeployIcons().get(RoomTeamId.CAMP_A));
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomPosition(result, "conquest.teamDeployIconCampB", "teamDeployIconCampB", conquest.teamDeployIcons().get(RoomTeamId.CAMP_B));
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "conquest.campASpawnGroup", "campASpawnGroup", conquest.teamSpawnGroups().get(RoomTeamId.CAMP_A), true);
        org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.validateRoomSpawnGroup(result, "conquest.campBSpawnGroup", "campBSpawnGroup", conquest.teamSpawnGroups().get(RoomTeamId.CAMP_B), true);
        if (conquest.sharedBoundary() == null) {
            result.addMissing("capture.gameBoundary", "sharedBoundary");
        }
        if (conquest.campABoundary() == null) {
            result.addMissing("capture.campAGameBoundary", "campABoundary");
        }
        if (conquest.campBBoundary() == null) {
            result.addMissing("capture.campBGameBoundary", "campBBoundary");
        }
        if (capture.mapId() == null || capture.mapId().isBlank() || !org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.MAP_ID_PATTERN.matcher(capture.mapId()).matches()) {
            result.addMissing("capture.mapId", "mapId");
        }
        if (capture.drawDurationMinutes() <= 0) {
            result.addMissing("capture.drawDurationMinutes", "drawDurationMinutes");
        }
        if (capture.fullCaptureEndSeconds() < -1) {
            result.addMissing("capture.fullCaptureEndSeconds", "fullCaptureEndSeconds");
        }
        if (capture.fullCaptureResponseMode() == null) {
            result.addMissing("capture.fullCaptureResponseMode", "fullCaptureResponseMode");
        }
        if (capture.antiBaseNukeTriggerSeconds() <= 0) {
            result.addMissing("capture.antiBaseNukeTriggerSeconds", "antiBaseNukeTriggerSeconds");
        }
        if (capture.antiBaseNukeRespawnDelaySeconds() <= 0) {
            result.addMissing("capture.antiBaseNukeRespawnDelaySeconds", "antiBaseNukeRespawnDelaySeconds");
        }
        if (capture.antiBaseNukeTriggerCooldownSeconds() <= 0) {
            result.addMissing("capture.antiBaseNukeTriggerCooldownSeconds", "antiBaseNukeTriggerCooldownSeconds");
        }
        if (capture.captureScoreIntervalSeconds() <= 0) {
            result.addMissing("capture.captureScoreIntervalSeconds", "captureScoreIntervalSeconds");
        }
        if (capture.scoreMethod() == null || capture.scoreMethod().isBlank()) {
            result.addMissing("capture.scoreMethod", "scoreMethod");
        } else if ("SCORE_TARGET".equals(capture.scoreMethod())) {
            if (capture.targetScore() <= 0) {
                result.addMissing("capture.targetScore", "targetScore");
            }
        } else if ("TROOP_DEPLETION".equals(capture.scoreMethod())) {
            if (capture.campAInitialTroops() <= 0) {
                result.addMissing("capture.campAInitialTroops", "campAInitialTroops");
            }
            if (capture.campBInitialTroops() <= 0) {
                result.addMissing("capture.campBInitialTroops", "campBInitialTroops");
            }
        } else {
            result.addMissing("capture.scoreMethod", "scoreMethod");
        }
        if (capture.initialCaptureOwnership() == null || capture.initialCaptureOwnership().isBlank()) {
            result.addMissing("capture.initialCaptureOwnership", "initialCaptureOwnership");
        }
        if (!org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport.hasActivePoints(capture)) {
            result.addMissing("capture.activePoints", "capturePoints");
        }
    }



    public static RoomConquestConfig defaults() {
        return new RoomConquestConfig(
            Map.of(),
            Map.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            RoomCaptureConfig.defaults(),
            List.of(),
            List.of()
        );
    }

    @Override
    public String mapId() {
        return captureConfig.mapId();
    }

    @Override
    public String campADisplayName(RoomCommonConfig commonConfig) {
        return commonConfig.campADisplayName();
    }

    @Override
    public String campBDisplayName(RoomCommonConfig commonConfig) {
        return commonConfig.campBDisplayName();
    }

    @Override
    public List<RoomCapturePointDefinition> definedObjectivePoints() {
        return new java.util.ArrayList<>(captureConfig.points().values()).stream()
            .filter(Objects::nonNull)
            .toList();
    }

    public Optional<RoomPosition> findTeamSpawn(RoomTeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        RoomSpawnGroupConfig group = teamSpawnGroups.get(teamId);
        if (group != null) {
            for (RoomPosition position : group.spawns()) {
                if (position != null) {
                    return Optional.of(position);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<RoomPosition> pickRandomTeamSpawn(RoomTeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        RoomSpawnGroupConfig group = teamSpawnGroups.get(teamId);
        if (group != null && group.enabled() && !group.spawns().isEmpty()) {
            List<RoomPosition> candidates = group.spawns().stream()
                .filter(Objects::nonNull)
                .toList();
            if (!candidates.isEmpty()) {
                return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
            }
        }
        return findTeamSpawn(teamId);
    }

    public Optional<RoomPosition> findTeamDeployIcon(RoomTeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(teamDeployIcons.get(teamId));
    }

    public List<RoomPosition> boundaryForTeam(RoomTeamId teamId) {
        if (hasDedicatedTeamBoundariesConfigured() && teamId == RoomTeamId.CAMP_A) {
            return campABoundary;
        }
        if (hasDedicatedTeamBoundariesConfigured() && teamId == RoomTeamId.CAMP_B) {
            return campBBoundary;
        }
        return sharedBoundary;
    }

    public List<List<RoomPosition>> allowedBoundariesForTeam(RoomTeamId teamId) {
        if (hasDedicatedTeamBoundariesConfigured()) {
            if (teamId == RoomTeamId.CAMP_A) {
                return List.of(campABoundary);
            }
            if (teamId == RoomTeamId.CAMP_B) {
                return List.of(campBBoundary);
            }
            return List.of(campABoundary, campBBoundary);
        }
        return hasSharedBoundaryConfigured() ? List.of(sharedBoundary) : List.of();
    }

    public boolean hasSharedBoundaryConfigured() {
        return sharedBoundary.size() >= 3;
    }

    public boolean hasDedicatedTeamBoundariesConfigured() {
        return campABoundary.size() >= 3 && campBBoundary.size() >= 3;
    }

    public boolean hasAnyBoundaryConfigured() {
        return hasSharedBoundaryConfigured() || hasDedicatedTeamBoundariesConfigured();
    }

    public RoomConquestConfig withCaptureConfig(RoomCaptureConfig value) {
        return new RoomConquestConfig(
            teamSpawnGroups,
            teamDeployIcons,
            deployViewPosition,
            sharedBoundary,
            campABoundary,
            campBBoundary,
            value,
            ammoBoxPositions,
            medicalBoxPositions
        );
    }

    private static Map<RoomTeamId, RoomSpawnGroupConfig> normalizeSpawnGroups(Map<RoomTeamId, RoomSpawnGroupConfig> values) {
        Map<RoomTeamId, RoomSpawnGroupConfig> normalized = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<RoomTeamId, RoomSpawnGroupConfig> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Map.copyOf(normalized);
    }

    private static Map<RoomTeamId, RoomPosition> normalizeDeployIcons(Map<RoomTeamId, RoomPosition> values) {
        Map<RoomTeamId, RoomPosition> normalized = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<RoomTeamId, RoomPosition> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Map.copyOf(normalized);
    }

    private static List<RoomPosition> copyBoundary(List<RoomPosition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
