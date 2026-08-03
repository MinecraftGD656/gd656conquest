package org.mods.gd656conquest.server.room.config;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomCommonConfig;
import org.mods.gd656conquest.server.room.model.RoomConquestConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class RoomDefinitionGroupConfigEditor {
    private RoomDefinitionGroupConfigEditor() {
    }

    public static RoomDefinition add(RoomDefinition definition, String configId, String rawValue, ServerPlayer editor) {
        Objects.requireNonNull(definition, "definition");
        String canonical = normalizeConfigId(configId);
        if (isCommonStringGroup(canonical)) {
            return replaceCommonStringGroup(definition, canonical, addCommonGroupValue(resolveCommonStringGroup(definition, canonical), canonical, rawValue, editor));
        }
        if (isGunmasterWeaponPoolGroup(canonical)) {
            return RoomConfigBindingAccess.write(
                definition,
                gunmasterWeaponPoolPath(canonical),
                addGunmasterWeaponPoolValue(resolveGunmasterWeaponPool(definition, canonical), canonical, rawValue, editor)
            );
        }
        if (isBoundaryGroup(canonical)) {
            RoomPosition point = parseBoundaryPosition(rawValue, editor);
            return replaceBoundary(definition, canonical, point, false, false);
        }
        if (isBreakthroughBoundaryGroup(canonical)) {
            RoomPosition point = parseBoundaryPosition(rawValue, editor);
            return replaceBreakthroughBoundary(definition, canonical, point, false, false);
        }
        if (isRushBoundaryGroup(canonical)) {
            RoomPosition point = parseBoundaryPosition(rawValue, editor);
            return replaceRushBoundary(definition, canonical, point, false, false);
        }
        if (isArenaBoundaryGroup(canonical)) {
            RoomPosition point = parseBoundaryPosition(rawValue, editor);
            return replaceArenaBoundary(definition, canonical, point, false, false);
        }
        if (isSupportPositionGroup(canonical)) {
            RoomPosition point = parsePosition(rawValue, editor);
            return replaceSupportPositionGroup(definition, canonical, point, false, false);
        }
        RoomPosition position = parsePosition(rawValue, editor);
        return replaceSpawnGroup(definition, canonical, group -> addSpawn(group, position));
    }

    public static RoomDefinition delete(RoomDefinition definition, String configId, String rawValue, ServerPlayer editor) {
        Objects.requireNonNull(definition, "definition");
        String canonical = normalizeConfigId(configId);
        boolean clearAll = "all".equalsIgnoreCase(rawValue == null ? "" : rawValue.trim());
        if (isCommonStringGroup(canonical)) {
            List<String> updatedValues = clearAll
                ? List.of()
                : removeCommonGroupValue(resolveCommonStringGroup(definition, canonical), canonical, rawValue);
            return replaceCommonStringGroup(definition, canonical, updatedValues);
        }
        if (isGunmasterWeaponPoolGroup(canonical)) {
            List<String> updatedValues = clearAll
                ? List.of()
                : removeGunmasterWeaponPoolValue(resolveGunmasterWeaponPool(definition, canonical), canonical, rawValue);
            return RoomConfigBindingAccess.write(definition, gunmasterWeaponPoolPath(canonical), updatedValues);
        }
        if (isBoundaryGroup(canonical)) {
            return clearAll
                ? replaceBoundary(definition, canonical, null, true, false)
                : replaceBoundary(definition, canonical, parseBoundaryPosition(rawValue, editor), false, true);
        }
        if (isBreakthroughBoundaryGroup(canonical)) {
            return clearAll
                ? replaceBreakthroughBoundary(definition, canonical, null, true, false)
                : replaceBreakthroughBoundary(definition, canonical, parseBoundaryPosition(rawValue, editor), false, true);
        }
        if (isRushBoundaryGroup(canonical)) {
            return clearAll
                ? replaceRushBoundary(definition, canonical, null, true, false)
                : replaceRushBoundary(definition, canonical, parseBoundaryPosition(rawValue, editor), false, true);
        }
        if (isArenaBoundaryGroup(canonical)) {
            return clearAll
                ? replaceArenaBoundary(definition, canonical, null, true, false)
                : replaceArenaBoundary(definition, canonical, parseBoundaryPosition(rawValue, editor), false, true);
        }
        if (isSupportPositionGroup(canonical)) {
            return clearAll
                ? replaceSupportPositionGroup(definition, canonical, null, true, false)
                : replaceSupportPositionGroup(definition, canonical, parsePosition(rawValue, editor), false, true);
        }
        RoomSpawnGroupConfig updatedGroup = clearAll
            ? new RoomSpawnGroupConfig(resolveSpawnGroup(definition, canonical).enabled(), List.of())
            : removeSpawn(resolveSpawnGroup(definition, canonical), parsePosition(rawValue, editor));
        return replaceResolvedSpawnGroup(definition, canonical, updatedGroup);
    }

    public static List<String> list(RoomDefinition definition, String configId) {
        Objects.requireNonNull(definition, "definition");
        String canonical = normalizeConfigId(configId);
        if (isCommonStringGroup(canonical)) {
            return resolveCommonStringGroup(definition, canonical);
        }
        if (isGunmasterWeaponPoolGroup(canonical)) {
            return resolveGunmasterWeaponPool(definition, canonical);
        }
        if (isBoundaryGroup(canonical)) {
            List<RoomPosition> boundary = switch (canonical) {
                case "sharedboundary" -> definition.conquestConfig().sharedBoundary();
                case "campaboundary" -> definition.conquestConfig().campABoundary();
                case "campbboundary" -> definition.conquestConfig().campBBoundary();
                default -> List.of();
            };
            List<String> lines = new ArrayList<>();
            for (RoomPosition position : boundary) {
                lines.add(formatPosition(position));
            }
            return lines;
        }
        if (isBreakthroughBoundaryGroup(canonical)) {
            List<RoomPosition> boundary = resolveBreakthroughBoundary(definition.breakthroughConfig(), canonical);
            List<String> lines = new ArrayList<>();
            for (RoomPosition position : boundary) {
                lines.add(formatPosition(position));
            }
            return lines;
        }
        if (isRushBoundaryGroup(canonical)) {
            List<String> lines = new ArrayList<>();
            for (RoomPosition position : resolveRushBoundary(definition, canonical)) {
                lines.add(formatPosition(position));
            }
            return lines;
        }
        if (isArenaBoundaryGroup(canonical)) {
            List<String> lines = new ArrayList<>();
            for (RoomPosition position : resolveArenaBoundary(definition, canonical)) {
                lines.add(formatPosition(position));
            }
            return lines;
        }
        if (isSupportPositionGroup(canonical)) {
            List<String> lines = new ArrayList<>();
            for (RoomPosition position : resolveSupportPositionGroup(definition, canonical)) {
                lines.add(formatPosition(position));
            }
            return lines;
        }
        List<String> lines = new ArrayList<>();
        for (RoomPosition position : resolveSpawnGroup(definition, canonical).spawns()) {
            lines.add(formatPosition(position));
        }
        return lines;
    }

    private static RoomDefinition replaceBoundary(
        RoomDefinition definition,
        String canonical,
        RoomPosition point,
        boolean clearAll,
        boolean remove
    ) {
        List<RoomPosition> shared = new ArrayList<>(definition.conquestConfig().sharedBoundary());
        List<RoomPosition> campA = new ArrayList<>(definition.conquestConfig().campABoundary());
        List<RoomPosition> campB = new ArrayList<>(definition.conquestConfig().campBBoundary());
        List<RoomPosition> target = switch (canonical) {
            case "sharedboundary" -> shared;
            case "campaboundary" -> campA;
            case "campbboundary" -> campB;
            default -> throw new IllegalArgumentException("未知边界: " + canonical);
        };
        if (clearAll) {
            target.clear();
        } else if (remove) {
            target.removeIf(existing -> sameBoundaryPoint(existing, point));
        } else if (target.stream().noneMatch(existing -> sameBoundaryPoint(existing, point))) {
            target.add(point);
        }
        RoomConquestConfig conquest = new RoomConquestConfig(
            definition.conquestConfig().teamSpawnGroups(),
            definition.conquestConfig().teamDeployIcons(),
            definition.conquestConfig().deployViewPosition(),
            shared,
            campA,
            campB,
            definition.conquestConfig().captureConfig(),
            definition.conquestConfig().ammoBoxPositions(),
            definition.conquestConfig().medicalBoxPositions()
        );
        return rebuild(definition, definition.commonConfig(), conquest);
    }

    private static RoomDefinition replaceCommonStringGroup(RoomDefinition definition, String canonical, List<String> updatedValues) {
        RoomCommonConfig common = definition.commonConfig();
        RoomCommonConfig updatedCommon = new RoomCommonConfig(
            common.matchingConfig(),
            common.dimensionId(),
            common.queueSpawn(),
            common.respawnDelaySeconds(),
            common.vehicleBackend(),
            common.playerStateConfig(),
            common.outsideBoundaryDeathSeconds(),
            common.aircraftOutsideBoundaryCleanupSeconds(),
            common.playerMaxY(),
            common.aircraftPassengerMaxY(),
            common.campADisplayName(),
            common.campBDisplayName(),
            common.campAVoicePack(),
            common.campBVoicePack(),
            common.progressSpeed(),
            common.matchLoadViewPosition(),
            common.resultConfig(),
            common.disableHunger(),
            common.enableAutoTeamSwitchPrompt(),
            common.disableInventoryChange(),
            common.enableGoatHorn(),
            common.enablePlayerDowned(),
            common.enableRespawnCountdownWhileDowned(),
            common.enableBackAgainstWall(),
            common.enableRandomLoadout(),
            common.enableRandomAttachments(),
            common.enableParachute(),
            common.enableDeathSupportDrop(),
            common.enableGiveArrow(),
            common.disableUndyingTotem(),
            common.allowPlayerCampSwitch(),
            "supportcampaherokits".equals(canonical) ? updatedValues : common.supportCampAHeroKits(),
            "supportcampbherokits".equals(canonical) ? updatedValues : common.supportCampBHeroKits(),
            "supportcampashellings".equals(canonical) ? updatedValues : common.supportCampAShellings(),
            "supportcampbshellings".equals(canonical) ? updatedValues : common.supportCampBShellings(),
            "campaprimaryweaponids".equals(canonical) ? updatedValues : common.campAPrimaryWeaponIds(),
            "campasecondaryweaponids".equals(canonical) ? updatedValues : common.campASecondaryWeaponIds(),
            "campameleeweaponids".equals(canonical) ? updatedValues : common.campAMeleeWeaponIds(),
            "campagadget1ids".equals(canonical) ? updatedValues : common.campAGadget1Ids(),
            "campagadget2ids".equals(canonical) ? updatedValues : common.campAGadget2Ids(),
            "campathrowableids".equals(canonical) ? updatedValues : common.campAThrowableIds(),
            "campbprimaryweaponids".equals(canonical) ? updatedValues : common.campBPrimaryWeaponIds(),
            "campbsecondaryweaponids".equals(canonical) ? updatedValues : common.campBSecondaryWeaponIds(),
            "campbmeleeweaponids".equals(canonical) ? updatedValues : common.campBMeleeWeaponIds(),
            "campbgadget1ids".equals(canonical) ? updatedValues : common.campBGadget1Ids(),
            "campbgadget2ids".equals(canonical) ? updatedValues : common.campBGadget2Ids(),
            "campbthrowableids".equals(canonical) ? updatedValues : common.campBThrowableIds(),
            common.campAEquipmentSetId(),
            common.campBEquipmentSetId(),
            common.mapName(),
            common.roomDescription()
        );
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            updatedCommon,
            definition.gameplayConfig()
        );
    }

    private static List<String> resolveCommonStringGroup(RoomDefinition definition, String canonical) {
        RoomCommonConfig common = definition.commonConfig();
        if ("supportcampaherokits".equals(canonical)) {
            return new ArrayList<>(common.supportCampAHeroKits());
        }
        if ("supportcampbherokits".equals(canonical)) {
            return new ArrayList<>(common.supportCampBHeroKits());
        }
        if ("supportcampashellings".equals(canonical)) {
            return new ArrayList<>(common.supportCampAShellings());
        }
        if ("supportcampbshellings".equals(canonical)) {
            return new ArrayList<>(common.supportCampBShellings());
        }
        if ("campaprimaryweaponids".equals(canonical)) {
            return new ArrayList<>(common.campAPrimaryWeaponIds());
        }
        if ("campasecondaryweaponids".equals(canonical)) {
            return new ArrayList<>(common.campASecondaryWeaponIds());
        }
        if ("campameleeweaponids".equals(canonical)) {
            return new ArrayList<>(common.campAMeleeWeaponIds());
        }
        if ("campagadget1ids".equals(canonical)) {
            return new ArrayList<>(common.campAGadget1Ids());
        }
        if ("campagadget2ids".equals(canonical)) {
            return new ArrayList<>(common.campAGadget2Ids());
        }
        if ("campathrowableids".equals(canonical)) {
            return new ArrayList<>(common.campAThrowableIds());
        }
        if ("campbprimaryweaponids".equals(canonical)) {
            return new ArrayList<>(common.campBPrimaryWeaponIds());
        }
        if ("campbsecondaryweaponids".equals(canonical)) {
            return new ArrayList<>(common.campBSecondaryWeaponIds());
        }
        if ("campbmeleeweaponids".equals(canonical)) {
            return new ArrayList<>(common.campBMeleeWeaponIds());
        }
        if ("campbgadget1ids".equals(canonical)) {
            return new ArrayList<>(common.campBGadget1Ids());
        }
        if ("campbgadget2ids".equals(canonical)) {
            return new ArrayList<>(common.campBGadget2Ids());
        }
        if ("campbthrowableids".equals(canonical)) {
            return new ArrayList<>(common.campBThrowableIds());
        }
        throw new IllegalArgumentException("未知 common 组配置: " + canonical);
    }

    private static List<String> addCommonGroupValue(List<String> currentValues, String canonical, String rawValue, ServerPlayer editor) {
        String normalizedInput = normalizeCommonGroupValue(rawValue);
        List<String> normalizedValues = RoomCommonGroupPatternResolver.resolveAddValues(canonical, normalizedInput, editor);
        List<String> updated = new ArrayList<>(currentValues == null ? List.of() : currentValues);
        for (String normalized : normalizedValues) {
            if (updated.stream().noneMatch(normalized::equals)) {
                updated.add(normalized);
            }
        }
        return updated;
    }

    private static List<String> removeCommonGroupValue(List<String> currentValues, String canonical, String rawValue) {
        String normalizedInput = normalizeCommonGroupValue(rawValue);
        List<String> removingValues = RoomCommonGroupPatternResolver.resolveDeleteValues(canonical, normalizedInput, currentValues);
        List<String> updated = new ArrayList<>(currentValues == null ? List.of() : currentValues);
        updated.removeIf(value -> removingValues.stream().anyMatch(value::equals));
        return updated;
    }

    private static List<String> addGunmasterWeaponPoolValue(List<String> currentValues, String canonical, String rawValue, ServerPlayer editor) {
        String normalizedInput = normalizeCommonGroupValue(rawValue);
        List<String> normalizedValues = RoomCommonGroupPatternResolver.resolveAddValues(canonical, normalizedInput, editor);
        List<String> updated = new ArrayList<>(currentValues == null ? List.of() : currentValues);
        for (String normalized : normalizedValues) {
            if (updated.stream().noneMatch(normalized::equals)) {
                updated.add(normalized);
            }
        }
        return updated;
    }

    private static List<String> removeGunmasterWeaponPoolValue(List<String> currentValues, String canonical, String rawValue) {
        String normalizedInput = normalizeCommonGroupValue(rawValue);
        List<String> removingValues = RoomCommonGroupPatternResolver.resolveDeleteValues(canonical, normalizedInput, currentValues);
        List<String> updated = new ArrayList<>(currentValues == null ? List.of() : currentValues);
        updated.removeIf(value -> removingValues.stream().anyMatch(value::equals));
        return updated;
    }

    private static String normalizeCommonGroupValue(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.length() >= 2) {
            boolean asciiQuoted = normalized.startsWith("\"") && normalized.endsWith("\"");
            boolean cnQuoted = normalized.startsWith("“") && normalized.endsWith("”");
            if (asciiQuoted || cnQuoted) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
        }
        normalized = normalized.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("组配置值不能为空");
        }
        return normalized;
    }

    private static RoomSpawnGroupConfig addSpawn(RoomSpawnGroupConfig group, RoomPosition position) {
        List<RoomPosition> spawns = new ArrayList<>(group.spawns());
        if (spawns.stream().noneMatch(existing -> samePosition(existing, position))) {
            spawns.add(position);
        }
        return new RoomSpawnGroupConfig(group.enabled(), spawns);
    }

    private static RoomSpawnGroupConfig removeSpawn(RoomSpawnGroupConfig group, RoomPosition position) {
        List<RoomPosition> spawns = new ArrayList<>(group.spawns());
        spawns.removeIf(existing -> samePosition(existing, position));
        return new RoomSpawnGroupConfig(group.enabled(), spawns);
    }

    private static RoomDefinition replaceSpawnGroup(RoomDefinition definition, String canonical, java.util.function.UnaryOperator<RoomSpawnGroupConfig> updater) {
        RoomSpawnGroupConfig updated = updater.apply(resolveSpawnGroup(definition, canonical));
        return replaceResolvedSpawnGroup(definition, canonical, updated);
    }

    private static RoomDefinition replaceResolvedSpawnGroup(RoomDefinition definition, String canonical, RoomSpawnGroupConfig updatedGroup) {
        if ("campaspawngroup".equals(canonical) || "campaspawns".equals(canonical)) {
            Map<RoomTeamId, RoomSpawnGroupConfig> groups = new LinkedHashMap<>(definition.conquestConfig().teamSpawnGroups());
            groups.put(RoomTeamId.CAMP_A, updatedGroup);
            RoomConquestConfig conquest = new RoomConquestConfig(
                groups,
                definition.conquestConfig().teamDeployIcons(),
                definition.conquestConfig().deployViewPosition(),
                definition.conquestConfig().sharedBoundary(),
                definition.conquestConfig().campABoundary(),
                definition.conquestConfig().campBBoundary(),
                definition.conquestConfig().captureConfig(),
                definition.conquestConfig().ammoBoxPositions(),
                definition.conquestConfig().medicalBoxPositions()
            );
            return rebuild(definition, definition.commonConfig(), conquest);
        }
        if ("campbspawngroup".equals(canonical) || "campbspawns".equals(canonical)) {
            Map<RoomTeamId, RoomSpawnGroupConfig> groups = new LinkedHashMap<>(definition.conquestConfig().teamSpawnGroups());
            groups.put(RoomTeamId.CAMP_B, updatedGroup);
            RoomConquestConfig conquest = new RoomConquestConfig(
                groups,
                definition.conquestConfig().teamDeployIcons(),
                definition.conquestConfig().deployViewPosition(),
                definition.conquestConfig().sharedBoundary(),
                definition.conquestConfig().campABoundary(),
                definition.conquestConfig().campBBoundary(),
                definition.conquestConfig().captureConfig(),
                definition.conquestConfig().ammoBoxPositions(),
                definition.conquestConfig().medicalBoxPositions()
            );
            return rebuild(definition, definition.commonConfig(), conquest);
        }
        if ("btcampabasespawngroup".equals(canonical)) {
            RoomBreakthroughConfig breakthrough = definition.breakthroughConfig();
            return replaceBreakthrough(definition, new RoomBreakthroughConfig(
                breakthrough.points(),
                breakthrough.mapId(),
                breakthrough.campADisplayName(),
                breakthrough.campBDisplayName(),
                breakthrough.stageCount(),
                breakthrough.campABaseBoundary(),
                breakthrough.campBBaseBoundary(),
                breakthrough.stageABoundary(),
                breakthrough.stageBBoundary(),
                breakthrough.stageCBoundary(),
                breakthrough.stageDBoundary(),
                breakthrough.stageEBoundary(),
                breakthrough.stageFBoundary(),
                updatedGroup,
                breakthrough.campBBaseSpawnGroup(),
                breakthrough.campABaseDeployIconPosition(),
                breakthrough.campBBaseDeployIconPosition(),
                breakthrough.stageADeployViewPosition(),
                breakthrough.stageBDeployViewPosition(),
                breakthrough.stageCDeployViewPosition(),
                breakthrough.stageDDeployViewPosition(),
                breakthrough.stageEDeployViewPosition(),
                breakthrough.stageFDeployViewPosition(),
                breakthrough.stageDurationMinutes(),
                breakthrough.defenderRetreatSeconds(),
                breakthrough.troopGainPerDefenderKill(),
                breakthrough.maxAttackTroopsAt12Players(),
                breakthrough.troopBalanceReference(),
                breakthrough.ammoBoxPositions(),
                breakthrough.medicalBoxPositions()
            ));
        }
        if ("btcampbbasespawngroup".equals(canonical)) {
            RoomBreakthroughConfig breakthrough = definition.breakthroughConfig();
            return replaceBreakthrough(definition, new RoomBreakthroughConfig(
                breakthrough.points(),
                breakthrough.mapId(),
                breakthrough.campADisplayName(),
                breakthrough.campBDisplayName(),
                breakthrough.stageCount(),
                breakthrough.campABaseBoundary(),
                breakthrough.campBBaseBoundary(),
                breakthrough.stageABoundary(),
                breakthrough.stageBBoundary(),
                breakthrough.stageCBoundary(),
                breakthrough.stageDBoundary(),
                breakthrough.stageEBoundary(),
                breakthrough.stageFBoundary(),
                breakthrough.campABaseSpawnGroup(),
                updatedGroup,
                breakthrough.campABaseDeployIconPosition(),
                breakthrough.campBBaseDeployIconPosition(),
                breakthrough.stageADeployViewPosition(),
                breakthrough.stageBDeployViewPosition(),
                breakthrough.stageCDeployViewPosition(),
                breakthrough.stageDDeployViewPosition(),
                breakthrough.stageEDeployViewPosition(),
                breakthrough.stageFDeployViewPosition(),
                breakthrough.stageDurationMinutes(),
                breakthrough.defenderRetreatSeconds(),
                breakthrough.troopGainPerDefenderKill(),
                breakthrough.maxAttackTroopsAt12Players(),
                breakthrough.troopBalanceReference(),
                breakthrough.ammoBoxPositions(),
                breakthrough.medicalBoxPositions()
            ));
        }
        if (canonical.startsWith("captureinnerspawngroup_")) {
            String code = canonical.substring("captureinnerspawngroup_".length()).toUpperCase(Locale.ROOT);
            return replaceCapturePointSpawnGroup(definition, code, true, updatedGroup);
        }
        if (canonical.startsWith("captureouterspawngroup_")) {
            String code = canonical.substring("captureouterspawngroup_".length()).toUpperCase(Locale.ROOT);
            return replaceCapturePointSpawnGroup(definition, code, false, updatedGroup);
        }
        if (canonical.startsWith("btcaptureinnerspawngroup_")) {
            String code = canonical.substring("btcaptureinnerspawngroup_".length()).toUpperCase(Locale.ROOT);
            return replaceBreakthroughCapturePointSpawnGroup(definition, code, null, updatedGroup);
        }
        if (canonical.startsWith("btcapturecampaouterspawngroup_")) {
            String code = canonical.substring("btcapturecampaouterspawngroup_".length()).toUpperCase(Locale.ROOT);
            return replaceBreakthroughCapturePointSpawnGroup(definition, code, RoomTeamId.CAMP_A, updatedGroup);
        }
        if (canonical.startsWith("btcapturecampbouterspawngroup_")) {
            String code = canonical.substring("btcapturecampbouterspawngroup_".length()).toUpperCase(Locale.ROOT);
            return replaceBreakthroughCapturePointSpawnGroup(definition, code, RoomTeamId.CAMP_B, updatedGroup);
        }
        if (isRushSpawnGroup(canonical)) {
            return RoomConfigBindingAccess.write(definition, rushSpawnGroupPath(canonical), updatedGroup);
        }
        if (isArenaSpawnGroup(canonical)) {
            return RoomConfigBindingAccess.write(definition, arenaSpawnGroupPath(canonical), updatedGroup);
        }
        throw new IllegalArgumentException("未知出生组配置: " + canonical);
    }

    private static RoomDefinition replaceCapturePointSpawnGroup(RoomDefinition definition, String code, boolean inner, RoomSpawnGroupConfig updatedGroup) {
        Map<String, RoomCapturePointDefinition> points = new LinkedHashMap<>(definition.conquestConfig().captureConfig().points());
        RoomCapturePointDefinition current = points.getOrDefault(
            code,
            new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults())
        );
        RoomCapturePointDefinition updated = inner
            ? current.withInnerSpawnGroup(updatedGroup)
            : current.withOuterSpawnGroup(updatedGroup);
        points.put(code, updated);
        RoomCaptureConfig capture = definition.conquestConfig().captureConfig();
        return replaceCapture(definition, new RoomCaptureConfig(
            points,
            capture.mapId(),
            capture.campADisplayName(),
            capture.campBDisplayName(),
            capture.drawDurationMinutes(),
            capture.fullCaptureEndSeconds(),
            capture.fullCaptureResponseMode(),
            capture.antiBaseNukeTriggerSeconds(),
            capture.antiBaseNukeRespawnDelaySeconds(),
            capture.antiBaseNukeTriggerCooldownSeconds(),
            capture.targetScore(),
            capture.captureScoreIntervalSeconds(),
            capture.scoreMethod(),
            capture.campAInitialTroops(),
            capture.campBInitialTroops(),
            capture.initialCaptureOwnership()
        ));
    }

    private static RoomDefinition replaceBreakthroughCapturePointSpawnGroup(
        RoomDefinition definition,
        String code,
        RoomTeamId outerTeamId,
        RoomSpawnGroupConfig updatedGroup
    ) {
        RoomBreakthroughConfig breakthrough = definition.breakthroughConfig();
        Map<String, RoomCapturePointDefinition> points = new LinkedHashMap<>(breakthrough.points());
        RoomCapturePointDefinition current = points.getOrDefault(
            code,
            new RoomCapturePointDefinition(
                code,
                code,
                null,
                RoomSpawnGroupConfig.defaults(),
                RoomSpawnGroupConfig.defaults(),
                Map.of(),
                0
            )
        );
        RoomCapturePointDefinition updated;
        if (outerTeamId == null) {
            updated = current.withInnerSpawnGroup(updatedGroup);
        } else {
            Map<RoomTeamId, RoomSpawnGroupConfig> teamGroups = new LinkedHashMap<>(current.teamOuterSpawnGroups());
            teamGroups.put(outerTeamId, updatedGroup);
            updated = current.withTeamOuterSpawnGroups(teamGroups);
        }
        points.put(code, updated);
        return replaceBreakthrough(definition, new RoomBreakthroughConfig(
            points,
            breakthrough.mapId(),
            breakthrough.campADisplayName(),
            breakthrough.campBDisplayName(),
            breakthrough.stageCount(),
            breakthrough.campABaseBoundary(),
            breakthrough.campBBaseBoundary(),
            breakthrough.stageABoundary(),
            breakthrough.stageBBoundary(),
            breakthrough.stageCBoundary(),
            breakthrough.stageDBoundary(),
            breakthrough.stageEBoundary(),
            breakthrough.stageFBoundary(),
            breakthrough.campABaseSpawnGroup(),
            breakthrough.campBBaseSpawnGroup(),
            breakthrough.campABaseDeployIconPosition(),
            breakthrough.campBBaseDeployIconPosition(),
            breakthrough.stageADeployViewPosition(),
            breakthrough.stageBDeployViewPosition(),
            breakthrough.stageCDeployViewPosition(),
            breakthrough.stageDDeployViewPosition(),
            breakthrough.stageEDeployViewPosition(),
            breakthrough.stageFDeployViewPosition(),
            breakthrough.stageDurationMinutes(),
            breakthrough.defenderRetreatSeconds(),
            breakthrough.troopGainPerDefenderKill(),
            breakthrough.maxAttackTroopsAt12Players(),
            breakthrough.troopBalanceReference(),
            breakthrough.ammoBoxPositions(),
            breakthrough.medicalBoxPositions()
        ));
    }

    private static RoomSpawnGroupConfig resolveSpawnGroup(RoomDefinition definition, String canonical) {
        if ("btcampabasespawngroup".equals(canonical)) {
            return definition.breakthroughConfig().campABaseSpawnGroup();
        }
        if ("btcampbbasespawngroup".equals(canonical)) {
            return definition.breakthroughConfig().campBBaseSpawnGroup();
        }
        if ("campaspawngroup".equals(canonical) || "campaspawns".equals(canonical)) {
            return definition.conquestConfig().teamSpawnGroups().getOrDefault(RoomTeamId.CAMP_A, RoomSpawnGroupConfig.defaults());
        }
        if ("campbspawngroup".equals(canonical) || "campbspawns".equals(canonical)) {
            return definition.conquestConfig().teamSpawnGroups().getOrDefault(RoomTeamId.CAMP_B, RoomSpawnGroupConfig.defaults());
        }
        if (canonical.startsWith("captureinnerspawngroup_")) {
            String code = canonical.substring("captureinnerspawngroup_".length()).toUpperCase(Locale.ROOT);
            return definition.conquestConfig().captureConfig().points().getOrDefault(
                code,
                new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults())
            ).innerSpawnGroup();
        }
        if (canonical.startsWith("captureouterspawngroup_")) {
            String code = canonical.substring("captureouterspawngroup_".length()).toUpperCase(Locale.ROOT);
            return definition.conquestConfig().captureConfig().points().getOrDefault(
                code,
                new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults())
            ).outerSpawnGroup();
        }
        if (canonical.startsWith("btcaptureinnerspawngroup_")) {
            String code = canonical.substring("btcaptureinnerspawngroup_".length()).toUpperCase(Locale.ROOT);
            return definition.breakthroughConfig().points().getOrDefault(
                code,
                new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults(), Map.of(), 0)
            ).innerSpawnGroup();
        }
        if (canonical.startsWith("btcapturecampaouterspawngroup_")) {
            String code = canonical.substring("btcapturecampaouterspawngroup_".length()).toUpperCase(Locale.ROOT);
            return definition.breakthroughConfig().points().getOrDefault(
                code,
                new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults(), Map.of(), 0)
            ).outerSpawnGroup(RoomTeamId.CAMP_A);
        }
        if (canonical.startsWith("btcapturecampbouterspawngroup_")) {
            String code = canonical.substring("btcapturecampbouterspawngroup_".length()).toUpperCase(Locale.ROOT);
            return definition.breakthroughConfig().points().getOrDefault(
                code,
                new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults(), Map.of(), 0)
            ).outerSpawnGroup(RoomTeamId.CAMP_B);
        }
        if (isRushSpawnGroup(canonical)) {
            Object current = RoomConfigBindingAccess.resolve(definition, rushSpawnGroupPath(canonical));
            return current instanceof RoomSpawnGroupConfig group ? group : RoomSpawnGroupConfig.defaults();
        }
        if (isArenaSpawnGroup(canonical)) {
            Object current = RoomConfigBindingAccess.resolve(definition, arenaSpawnGroupPath(canonical));
            return current instanceof RoomSpawnGroupConfig group ? group : RoomSpawnGroupConfig.defaults();
        }
        throw new IllegalArgumentException("未知出生组配�? " + canonical);
    }

    private static RoomDefinition replaceCapture(RoomDefinition definition, RoomCaptureConfig captureConfig) {
        return rebuild(definition, definition.commonConfig(), definition.conquestConfig().withCaptureConfig(captureConfig));
    }

    private static RoomDefinition replaceBreakthroughBoundary(
        RoomDefinition definition,
        String canonical,
        RoomPosition point,
        boolean clearAll,
        boolean remove
    ) {
        RoomBreakthroughConfig breakthrough = definition.breakthroughConfig();
        List<RoomPosition> campA = new ArrayList<>(breakthrough.campABaseBoundary());
        List<RoomPosition> campB = new ArrayList<>(breakthrough.campBBaseBoundary());
        List<RoomPosition> stageA = new ArrayList<>(breakthrough.stageABoundary());
        List<RoomPosition> stageB = new ArrayList<>(breakthrough.stageBBoundary());
        List<RoomPosition> stageC = new ArrayList<>(breakthrough.stageCBoundary());
        List<RoomPosition> stageD = new ArrayList<>(breakthrough.stageDBoundary());
        List<RoomPosition> stageE = new ArrayList<>(breakthrough.stageEBoundary());
        List<RoomPosition> stageF = new ArrayList<>(breakthrough.stageFBoundary());
        List<RoomPosition> target = switch (canonical) {
            case "btcampabaseboundary" -> campA;
            case "btcampbbaseboundary" -> campB;
            case "btstageaboundary" -> stageA;
            case "btstagebboundary" -> stageB;
            case "btstagecboundary" -> stageC;
            case "btstagedboundary" -> stageD;
            case "btstageeboundary" -> stageE;
            case "btstagefboundary" -> stageF;
            default -> throw new IllegalArgumentException("未知突破边界: " + canonical);
        };
        if (clearAll) {
            target.clear();
        } else if (remove) {
            target.removeIf(existing -> sameBoundaryPoint(existing, point));
        } else if (target.stream().noneMatch(existing -> sameBoundaryPoint(existing, point))) {
            target.add(point);
        }
        return replaceBreakthrough(definition, new RoomBreakthroughConfig(
            breakthrough.points(),
            breakthrough.mapId(),
            breakthrough.campADisplayName(),
            breakthrough.campBDisplayName(),
            breakthrough.stageCount(),
            campA,
            campB,
            stageA,
            stageB,
            stageC,
            stageD,
            stageE,
            stageF,
            breakthrough.campABaseSpawnGroup(),
            breakthrough.campBBaseSpawnGroup(),
            breakthrough.campABaseDeployIconPosition(),
            breakthrough.campBBaseDeployIconPosition(),
            breakthrough.stageADeployViewPosition(),
            breakthrough.stageBDeployViewPosition(),
            breakthrough.stageCDeployViewPosition(),
            breakthrough.stageDDeployViewPosition(),
            breakthrough.stageEDeployViewPosition(),
            breakthrough.stageFDeployViewPosition(),
            breakthrough.stageDurationMinutes(),
            breakthrough.defenderRetreatSeconds(),
            breakthrough.troopGainPerDefenderKill(),
            breakthrough.maxAttackTroopsAt12Players(),
            breakthrough.troopBalanceReference(),
            breakthrough.ammoBoxPositions(),
            breakthrough.medicalBoxPositions()
        ));
    }

    private static List<RoomPosition> resolveBreakthroughBoundary(RoomBreakthroughConfig breakthrough, String canonical) {
        return switch (canonical) {
            case "btcampabaseboundary" -> breakthrough.campABaseBoundary();
            case "btcampbbaseboundary" -> breakthrough.campBBaseBoundary();
            case "btstageaboundary" -> breakthrough.stageABoundary();
            case "btstagebboundary" -> breakthrough.stageBBoundary();
            case "btstagecboundary" -> breakthrough.stageCBoundary();
            case "btstagedboundary" -> breakthrough.stageDBoundary();
            case "btstageeboundary" -> breakthrough.stageEBoundary();
            case "btstagefboundary" -> breakthrough.stageFBoundary();
            default -> throw new IllegalArgumentException("未知突破边界: " + canonical);
        };
    }

    private static RoomDefinition replaceBreakthrough(RoomDefinition definition, RoomBreakthroughConfig breakthroughConfig) {
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            definition.commonConfig(),
            breakthroughConfig
        );
    }

    private static RoomDefinition rebuild(RoomDefinition definition, RoomCommonConfig commonConfig, RoomConquestConfig conquestConfig) {
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            commonConfig,
            conquestConfig
        );
    }

    private static boolean isBoundaryGroup(String canonical) {
        return "sharedboundary".equals(canonical) || "campaboundary".equals(canonical) || "campbboundary".equals(canonical);
    }

    private static boolean isCommonStringGroup(String canonical) {
        return "supportcampaherokits".equals(canonical)
            || "supportcampbherokits".equals(canonical)
            || "supportcampashellings".equals(canonical)
            || "supportcampbshellings".equals(canonical)
            || "campaprimaryweaponids".equals(canonical)
            || "campasecondaryweaponids".equals(canonical)
            || "campameleeweaponids".equals(canonical)
            || "campagadget1ids".equals(canonical)
            || "campagadget2ids".equals(canonical)
            || "campathrowableids".equals(canonical)
            || "campbprimaryweaponids".equals(canonical)
            || "campbsecondaryweaponids".equals(canonical)
            || "campbmeleeweaponids".equals(canonical)
            || "campbgadget1ids".equals(canonical)
            || "campbgadget2ids".equals(canonical)
            || "campbthrowableids".equals(canonical);
    }

    private static boolean isGunmasterWeaponPoolGroup(String canonical) {
        return "gminitialweaponpool".equals(canonical)
            || "gmmiddleweaponpool".equals(canonical)
            || "gmfinalweaponpool".equals(canonical);
    }

    private static boolean isRoomFixedLoadoutGroup(String canonical) {
        return "roomprimaryweaponids".equals(canonical)
            || "roomsecondaryweaponids".equals(canonical)
            || "roommeleeweaponids".equals(canonical)
            || "roomgadget1ids".equals(canonical)
            || "roomgadget2ids".equals(canonical)
            || "roomthrowableids".equals(canonical);
    }

    private static boolean isBreakthroughBoundaryGroup(String canonical) {
        return "btcampabaseboundary".equals(canonical)
            || "btcampbbaseboundary".equals(canonical)
            || "btstageaboundary".equals(canonical)
            || "btstagebboundary".equals(canonical)
            || "btstagecboundary".equals(canonical)
            || "btstagedboundary".equals(canonical)
            || "btstageeboundary".equals(canonical)
            || "btstagefboundary".equals(canonical);
    }

    private static boolean isRushBoundaryGroup(String canonical) {
        return "rscampabaseboundary".equals(canonical)
            || "rscampbbaseboundary".equals(canonical)
            || "rsstageaboundary".equals(canonical)
            || "rsstagebboundary".equals(canonical)
            || "rsstagecboundary".equals(canonical)
            || "rsstagedboundary".equals(canonical)
            || "rsstageeboundary".equals(canonical)
            || "rsstagefboundary".equals(canonical);
    }

    private static boolean isArenaBoundaryGroup(String canonical) {
        return canonical.startsWith("dmcamp") && canonical.endsWith("boundary")
            || canonical.startsWith("gmcamp") && canonical.endsWith("boundary")
            || "gmsoloboundary".equals(canonical);
    }

    private static boolean isRushSpawnGroup(String canonical) {
        return "rscampabasespawngroup".equals(canonical)
            || "rscampbbasespawngroup".equals(canonical)
            || canonical.startsWith("rsbombcampaspawngroup_")
            || canonical.startsWith("rsbombcampbspawngroup_");
    }

    private static boolean isArenaSpawnGroup(String canonical) {
        return canonical.startsWith("dmcamp") && canonical.endsWith("spawngroup")
            || canonical.startsWith("gmcamp") && canonical.endsWith("spawngroup");
    }

    private static boolean isSupportPositionGroup(String canonical) {
        return "ammoboxpositions".equals(canonical)
            || "conquestammoboxpositions".equals(canonical)
            || "medicalboxpositions".equals(canonical)
            || "conquestmedicalboxpositions".equals(canonical)
            || "btammoboxpositions".equals(canonical)
            || "btmedicalboxpositions".equals(canonical)
            || "rsammoboxpositions".equals(canonical)
            || "rsmedicalboxpositions".equals(canonical)
            || "dmammoboxpositions".equals(canonical)
            || "dmmedicalboxpositions".equals(canonical)
            || "gmsolospawnpositions".equals(canonical)
            || "gmmedicalboxpositions".equals(canonical);
    }

    private static RoomDefinition replaceArenaBoundary(
        RoomDefinition definition,
        String canonical,
        RoomPosition point,
        boolean clearAll,
        boolean remove
    ) {
        String path = arenaBoundaryPath(canonical);
        Object current = RoomConfigBindingAccess.resolve(definition, path);
        List<RoomPosition> target = new ArrayList<>(current instanceof List<?> list ? castBoundaryList(list) : List.of());
        if (clearAll) {
            target.clear();
        } else if (remove) {
            target.removeIf(existing -> sameBoundaryPoint(existing, point));
        } else if (target.stream().noneMatch(existing -> sameBoundaryPoint(existing, point))) {
            target.add(point);
        }
        return RoomConfigBindingAccess.write(definition, path, List.copyOf(target));
    }

    private static RoomDefinition replaceSupportPositionGroup(
        RoomDefinition definition,
        String canonical,
        RoomPosition point,
        boolean clearAll,
        boolean remove
    ) {
        String path = supportPositionGroupPath(canonical);
        List<RoomPosition> target = new ArrayList<>(resolveSupportPositionGroup(definition, canonical));
        if (clearAll) {
            target.clear();
        } else if (remove) {
            target.removeIf(existing -> samePosition(existing, point));
        } else if (target.stream().noneMatch(existing -> samePosition(existing, point))) {
            target.add(point);
        }
        return RoomConfigBindingAccess.write(definition, path, List.copyOf(target));
    }

    private static RoomDefinition replaceRushBoundary(
        RoomDefinition definition,
        String canonical,
        RoomPosition point,
        boolean clearAll,
        boolean remove
    ) {
        String path = rushBoundaryPath(canonical);
        List<RoomPosition> target = new ArrayList<>(resolveRushBoundary(definition, canonical));
        if (clearAll) {
            target.clear();
        } else if (remove) {
            target.removeIf(existing -> sameBoundaryPoint(existing, point));
        } else if (target.stream().noneMatch(existing -> sameBoundaryPoint(existing, point))) {
            target.add(point);
        }
        return RoomConfigBindingAccess.write(definition, path, List.copyOf(target));
    }

    private static List<RoomPosition> resolveArenaBoundary(RoomDefinition definition, String canonical) {
        Object current = RoomConfigBindingAccess.resolve(definition, arenaBoundaryPath(canonical));
        return current instanceof List<?> list ? castBoundaryList(list) : List.of();
    }

    private static List<RoomPosition> resolveRushBoundary(RoomDefinition definition, String canonical) {
        Object current = RoomConfigBindingAccess.resolve(definition, rushBoundaryPath(canonical));
        return current instanceof List<?> list ? castBoundaryList(list) : List.of();
    }

    private static List<RoomPosition> resolveSupportPositionGroup(RoomDefinition definition, String canonical) {
        Object current = RoomConfigBindingAccess.resolve(definition, supportPositionGroupPath(canonical));
        return current instanceof List<?> list ? castBoundaryList(list) : List.of();
    }

    private static List<RoomPosition> castBoundaryList(List<?> raw) {
        List<RoomPosition> values = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof RoomPosition position) {
                values.add(position);
            }
        }
        return values;
    }

    private static String arenaBoundaryPath(String canonical) {
        return switch (canonical) {
            case "dmcampaboundary" -> "deathmatchConfig.campABoundary";
            case "dmcampbboundary" -> "deathmatchConfig.campBBoundary";
            case "gmcampaboundary" -> "gunmasterConfig.campABoundary";
            case "gmcampbboundary" -> "gunmasterConfig.campBBoundary";
            case "gmsoloboundary" -> "gunmasterConfig.soloBoundary";
            default -> throw new IllegalArgumentException("未知团队死斗边界: " + canonical);
        };
    }

    private static String rushBoundaryPath(String canonical) {
        return switch (canonical) {
            case "rscampabaseboundary" -> "rushConfig.campABaseBoundary";
            case "rscampbbaseboundary" -> "rushConfig.campBBaseBoundary";
            case "rsstageaboundary" -> "rushConfig.stageABoundary";
            case "rsstagebboundary" -> "rushConfig.stageBBoundary";
            case "rsstagecboundary" -> "rushConfig.stageCBoundary";
            case "rsstagedboundary" -> "rushConfig.stageDBoundary";
            case "rsstageeboundary" -> "rushConfig.stageEBoundary";
            case "rsstagefboundary" -> "rushConfig.stageFBoundary";
            default -> throw new IllegalArgumentException("未知突袭边界: " + canonical);
        };
    }

    private static String arenaSpawnGroupPath(String canonical) {
        return switch (canonical) {
            case "dmcampaspawngroup" -> "deathmatchConfig.campASpawnGroup";
            case "dmcampbspawngroup" -> "deathmatchConfig.campBSpawnGroup";
            case "gmcampaspawngroup" -> "gunmasterConfig.campASpawnGroup";
            case "gmcampbspawngroup" -> "gunmasterConfig.campBSpawnGroup";
            default -> throw new IllegalArgumentException("未知团队死斗出生组: " + canonical);
        };
    }

    private static String rushSpawnGroupPath(String canonical) {
        if ("rscampabasespawngroup".equals(canonical)) {
            return "rushConfig.campABaseSpawnGroup";
        }
        if ("rscampbbasespawngroup".equals(canonical)) {
            return "rushConfig.campBBaseSpawnGroup";
        }
        if (canonical.startsWith("rsbombcampaspawngroup_")) {
            String stageCode = canonical.substring("rsbombcampaspawngroup_".length()).toUpperCase(Locale.ROOT);
            return "rushConfig.bombInnerSpawnGroup" + stageCode;
        }
        if (canonical.startsWith("rsbombcampbspawngroup_")) {
            String stageCode = canonical.substring("rsbombcampbspawngroup_".length()).toUpperCase(Locale.ROOT);
            return "rushConfig.bombDefenderInnerSpawnGroup" + stageCode;
        }
        throw new IllegalArgumentException("未知突袭出生组: " + canonical);
    }

    private static String supportPositionGroupPath(String canonical) {
        return switch (canonical) {
            case "ammoboxpositions" -> "conquestConfig.ammoBoxPositions";
            case "conquestammoboxpositions" -> "conquestConfig.ammoBoxPositions";
            case "medicalboxpositions" -> "conquestConfig.medicalBoxPositions";
            case "conquestmedicalboxpositions" -> "conquestConfig.medicalBoxPositions";
            case "btammoboxpositions" -> "breakthroughConfig.ammoBoxPositions";
            case "btmedicalboxpositions" -> "breakthroughConfig.medicalBoxPositions";
            case "rsammoboxpositions" -> "rushConfig.ammoBoxPositions";
            case "rsmedicalboxpositions" -> "rushConfig.medicalBoxPositions";
            case "dmammoboxpositions" -> "deathmatchConfig.ammoBoxPositions";
            case "dmmedicalboxpositions" -> "deathmatchConfig.medicalBoxPositions";
            case "gmsolospawnpositions" -> "gunmasterConfig.soloSpawnPositions";
            case "gmmedicalboxpositions" -> "gunmasterConfig.medicalBoxPositions";
            default -> throw new IllegalArgumentException("未知支援箱数据组: " + canonical);
        };
    }

    private static List<String> resolveGunmasterWeaponPool(RoomDefinition definition, String canonical) {
        Object current = RoomConfigBindingAccess.resolve(definition, gunmasterWeaponPoolPath(canonical));
        if (!(current instanceof List<?> raw)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof String text && !text.isBlank()) {
                values.add(text.trim());
            }
        }
        return values;
    }

    private static String gunmasterWeaponPoolPath(String canonical) {
        return switch (canonical) {
            case "gminitialweaponpool" -> "gunmasterConfig.initialWeaponPool";
            case "gmmiddleweaponpool" -> "gunmasterConfig.middleWeaponPool";
            case "gmfinalweaponpool" -> "gunmasterConfig.finalWeaponPool";
            default -> throw new IllegalArgumentException("未知枪神武器池配置: " + canonical);
        };
    }

    private static String normalizeConfigId(String configId) {
        if (configId == null || configId.isBlank()) {
            throw new IllegalArgumentException("配置项不能为空");
        }
        return configId.trim().toLowerCase(Locale.ROOT);
    }

    private static RoomPosition parseBoundaryPosition(String rawValue, ServerPlayer editor) {
        String value = rawValue == null ? "" : rawValue.trim();
        if ("here".equalsIgnoreCase(value)) {
            if (editor == null) {
                throw new IllegalArgumentException("无法解析 here 坐标");
            }
            return new RoomPosition(editor.getBlockX(), editor.getY(), editor.getBlockZ());
        }
        String[] split = value.split(",");
        if (split.length == 2) {
            try {
                int x = Integer.parseInt(split[0].trim());
                int z = Integer.parseInt(split[1].trim());
                return new RoomPosition(x, 0, z);
            } catch (Exception exception) {
                throw new IllegalArgumentException("边界坐标格式错误，应为 x,z / x,y,z / here", exception);
            }
        }
        if (split.length == 3) {
            return parsePosition(rawValue, editor);
        }
        throw new IllegalArgumentException("边界坐标格式错误，应为 x,z / x,y,z / here");
    }

    private static RoomPosition parsePosition(String rawValue, ServerPlayer editor) {
        String value = rawValue == null ? "" : rawValue.trim();
        if ("here".equalsIgnoreCase(value)) {
            if (editor == null) {
                throw new IllegalArgumentException("无法解析 here 坐标");
            }
            return new RoomPosition(editor.getBlockX(), editor.getY(), editor.getBlockZ());
        }
        String[] split = value.split(",");
        if (split.length != 3) {
            throw new IllegalArgumentException("坐标格式错误，应为 x,y,z 或 here");
        }
        try {
            int x = Integer.parseInt(split[0].trim());
            double y = Double.parseDouble(split[1].trim());
            int z = Integer.parseInt(split[2].trim());
            return new RoomPosition(x, y, z);
        } catch (Exception exception) {
            throw new IllegalArgumentException("坐标格式错误，应为 x,y,z 或 here", exception);
        }
    }

    private static boolean samePosition(RoomPosition a, RoomPosition b) {
        return a != null && b != null && a.x() == b.x() && Math.abs(a.y() - b.y()) < 0.0001D && a.z() == b.z();
    }

    private static boolean sameBoundaryPoint(RoomPosition a, RoomPosition b) {
        return a != null && b != null && a.x() == b.x() && a.z() == b.z();
    }

    private static String formatPosition(RoomPosition position) {
        return position.x() + "," + String.format(Locale.ROOT, "%.1f", position.y()) + "," + position.z();
    }
}
