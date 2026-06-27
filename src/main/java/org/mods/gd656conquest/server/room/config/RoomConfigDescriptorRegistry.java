package org.mods.gd656conquest.server.room.config;

import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.server.room.model.RoomVoicePack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class RoomConfigDescriptorRegistry {
    private static final List<RoomConfigDescriptor> DESCRIPTORS = buildDescriptors();
    private static final Map<String, RoomConfigDescriptor> BY_ID = indexDescriptors(DESCRIPTORS);

    private RoomConfigDescriptorRegistry() {
    }

    public static List<RoomConfigDescriptor> enabled() {
        return DESCRIPTORS.stream().filter(RoomConfigDescriptor::enabled).toList();
    }

    public static List<String> legacyCommonCommandIds() {
        return legacyCommandIds(RoomConfigScope.COMMON);
    }

    public static List<String> legacyConquestCommandIds() {
        return legacyCommandIds(RoomConfigScope.CONQUEST);
    }

    public static List<String> legacyBreakthroughCommandIds() {
        return legacyCommandIds(RoomConfigScope.BREAKTHROUGH);
    }

    public static List<RoomConfigScope> guiScopes() {
        return List.of(RoomConfigScope.COMMON, RoomConfigScope.CONQUEST, RoomConfigScope.BREAKTHROUGH);
    }

    public static RoomConfigScope scopeOf(RoomConfigDescriptor descriptor) {
        return descriptor == null ? RoomConfigScope.COMMON : descriptor.scope();
    }

    public static List<RoomConfigDescriptor> guiDescriptorsForScope(RoomConfigScope scope, Collection<String> configuredCaptureCodes) {
        if (scope == null) {
            return List.of();
        }
        return enabled().stream().filter(descriptor -> descriptor.scope() == scope).toList();
    }

    public static Optional<RoomConfigDescriptor> find(String configIdOrAlias) {
        String normalized = RoomConfigDescriptor.normalizeKey(configIdOrAlias);
        if (normalized == null) {
            return Optional.empty();
        }
        RoomConfigDescriptor descriptor = BY_ID.get(normalized);
        if (descriptor != null) {
            return Optional.of(descriptor);
        }
        return findDynamicDescriptor(normalized);
    }

    public static List<String> suggestionValues(String configIdOrAlias) {
        return find(configIdOrAlias).map(RoomConfigDescriptor::suggestionValues).orElse(List.of());
    }

    private static List<RoomConfigDescriptor> buildDescriptors() {
        List<RoomConfigDescriptor> descriptors = new ArrayList<>();

        common(descriptors, RoomConfigCategory.ROOM, "gameplayId", "玩法类型", "gameplayId", RoomConfigValueType.STRING, List.of("CONQUEST", "BREAKTHROUGH"), "gameplayType");
        common(descriptors, RoomConfigCategory.ROOM, "displayName", "房间名称", "displayName", RoomConfigValueType.STRING, List.of());
        common(descriptors, RoomConfigCategory.MATCHING, "minStartPlayers", "最低开局人数", "commonConfig.matchingConfig.minStartPlayers", RoomConfigValueType.NUMBER, List.of(), "queueMinStartPlayers");
        common(descriptors, RoomConfigCategory.MATCHING, "queueMaxPlayers", "匹配最大人数", "commonConfig.matchingConfig.totalMaxPlayers", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.MATCHING, "waitAfterMinSeconds", "最低人数后最短等待", "commonConfig.matchingConfig.waitAfterMinSeconds", RoomConfigValueType.NUMBER, List.of(), "queueWaitAfterMinSeconds");
        common(descriptors, RoomConfigCategory.MATCHING, "waitAfterMaxSeconds", "最低人数后最长等待", "commonConfig.matchingConfig.waitAfterMaxSeconds", RoomConfigValueType.NUMBER, List.of(), "queueWaitAfterMaxSeconds");
        common(descriptors, RoomConfigCategory.TRANSPORT, "queueSpawn", "排队出生点", "commonConfig.queueSpawn", RoomConfigValueType.POSITION, List.of("here"));
        common(descriptors, RoomConfigCategory.PLAYER_STATE, "respawnDelaySeconds", "重生等待秒数", "commonConfig.respawnDelaySeconds", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.RULES, "progressSpeed", "占领速度", "commonConfig.progressSpeed", RoomConfigValueType.NUMBER, List.of(), "captureProgressSpeed");
        common(descriptors, RoomConfigCategory.RULES, "enablePlayerDowned", "启用倒地", "commonConfig.enablePlayerDowned", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnablePlayerDowned");
        common(descriptors, RoomConfigCategory.RESULT, "endViewPosition", "结算视角", "commonConfig.resultConfig.endViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"), "matchEndViewPosition");

        common(descriptors, RoomConfigCategory.PLAYER_STATE, "gameMode", "玩家游戏模式", "commonConfig.playerStateConfig.gameMode", RoomConfigValueType.STRING, List.of("SURVIVAL", "ADVENTURE", "CREATIVE", "SPECTATOR"), "playerGameMode");
        common(descriptors, RoomConfigCategory.PLAYER_STATE, "invulnerableSeconds", "重生无敌秒数", "commonConfig.playerStateConfig.invulnerableSeconds", RoomConfigValueType.NUMBER, List.of(), "respawnInvulnerableSeconds");
        common(descriptors, RoomConfigCategory.CAPTURE, "campADisplayName", "阵营A名称", "commonConfig.campADisplayName", RoomConfigValueType.STRING, List.of());
        common(descriptors, RoomConfigCategory.CAPTURE, "campBDisplayName", "阵营B名称", "commonConfig.campBDisplayName", RoomConfigValueType.STRING, List.of());
        common(descriptors, RoomConfigCategory.RULES, "campAVoicePack", "campA语音包绑定", "commonConfig.campAVoicePack", RoomConfigValueType.STRING, RoomVoicePack.suggestions());
        common(descriptors, RoomConfigCategory.RULES, "campBVoicePack", "campB语音包绑定", "commonConfig.campBVoicePack", RoomConfigValueType.STRING, RoomVoicePack.suggestions());
        common(descriptors, RoomConfigCategory.RULES, "disableHunger", "禁用饥饿", "commonConfig.disableHunger", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolDisableHunger");
        common(descriptors, RoomConfigCategory.RULES, "enableAutoTeamSwitchPrompt", "自动换边提示", "commonConfig.enableAutoTeamSwitchPrompt", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnableAutoTeamSwitchPrompt", "enableAutoTeamSwitch");
        common(descriptors, RoomConfigCategory.RULES, "enableGoatHorn", "是否启用山羊角", "commonConfig.enableGoatHorn", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnableGoatHorn");
        common(
            descriptors,
            RoomConfigCategory.RULES,
            "enableRoomInventoryLock",
            "是否启用房间背包锁定",
            "commonConfig.disableInventoryChange",
            RoomConfigValueType.BOOLEAN,
            List.of("true", "false"),
            "enableRoomInventoryLock",
            "boolEnableRoomInventoryLock",
            "disableInventoryChange",
            "boolDisableInventoryChange",
            "disableInventoryEdit"
        );
        common(descriptors, RoomConfigCategory.RULES, "outsideBoundaryDeathSeconds", "出界死亡秒数", "commonConfig.outsideBoundaryDeathSeconds", RoomConfigValueType.NUMBER, List.of(), "outOfBoundaryDeathSeconds", "btOutOfBoundaryDeathSeconds");
        common(descriptors, RoomConfigCategory.RULES, "aircraftOutsideBoundaryCleanupSeconds", "空中载具出界清理秒数", "commonConfig.aircraftOutsideBoundaryCleanupSeconds", RoomConfigValueType.NUMBER, List.of(), "airVehicleOutOfBoundaryCleanupSeconds", "aircraftOutOfBoundaryCleanupSeconds");
        conquest(descriptors, RoomConfigCategory.TRANSPORT, "deployViewPosition", "部署视角", "conquestConfig.deployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"), "deployPagePosition");
        conquest(descriptors, RoomConfigCategory.TRANSPORT, "teamDeployIconCampA", "阵营A部署图标", "conquestConfig.teamDeployIcons[CAMP_A]", RoomConfigValueType.POSITION, List.of("here"), "campADeployIconPosition");
        conquest(descriptors, RoomConfigCategory.TRANSPORT, "teamDeployIconCampB", "阵营B部署图标", "conquestConfig.teamDeployIcons[CAMP_B]", RoomConfigValueType.POSITION, List.of("here"), "campBDeployIconPosition");
        conquestGroup(descriptors, RoomConfigCategory.RULES, "sharedBoundary", "公共边界", "conquestConfig.sharedBoundary", RoomConfigValueType.POSITION_GROUP, "gameBoundary");
        conquestGroup(descriptors, RoomConfigCategory.RULES, "campABoundary", "阵营A边界", "conquestConfig.campABoundary", RoomConfigValueType.POSITION_GROUP, "campAGameBoundary");
        conquestGroup(descriptors, RoomConfigCategory.RULES, "campBBoundary", "阵营B边界", "conquestConfig.campBBoundary", RoomConfigValueType.POSITION_GROUP, "campBGameBoundary");
        conquestSpecial(descriptors, RoomConfigCategory.EQUIPMENT, "campAEquipmentSlots", "阵营A配装槽位", "conquestConfig.equipmentConfig.campAEquipmentSlots", RoomConfigValueType.DATA_GROUP, List.of("now"), "capture-current-equipment-camp-a");
        conquestSpecial(descriptors, RoomConfigCategory.EQUIPMENT, "campBEquipmentSlots", "阵营B配装槽位", "conquestConfig.equipmentConfig.campBEquipmentSlots", RoomConfigValueType.DATA_GROUP, List.of("now"), "capture-current-equipment-camp-b");
        conquest(descriptors, RoomConfigCategory.CAPTURE, "mapId", "征服地图ID", "conquestConfig.captureConfig.mapId", RoomConfigValueType.STRING, List.of());
        conquestGroup(descriptors, RoomConfigCategory.TRANSPORT, "campASpawnGroup", "阵营A出生组", "conquestConfig.teamSpawnGroups[CAMP_A]", RoomConfigValueType.DATA_GROUP, "campASpawns", "campACoalition");
        conquestGroup(descriptors, RoomConfigCategory.TRANSPORT, "campBSpawnGroup", "阵营B出生组", "conquestConfig.teamSpawnGroups[CAMP_B]", RoomConfigValueType.DATA_GROUP, "campBSpawns", "campBMilitia");
        conquest(descriptors, RoomConfigCategory.CAPTURE, "drawDurationMinutes", "平局持续分钟", "conquestConfig.captureConfig.drawDurationMinutes", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.CAPTURE, "fullCaptureEndSeconds", "全点占领结束秒数", "conquestConfig.captureConfig.fullCaptureEndSeconds", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.CAPTURE, "targetScore", "目标分数", "conquestConfig.captureConfig.targetScore", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.CAPTURE, "captureScoreIntervalSeconds", "据点计分间隔秒数", "conquestConfig.captureConfig.captureScoreIntervalSeconds", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.CAPTURE, "scoreMethod", "计分方式", "conquestConfig.captureConfig.scoreMethod", RoomConfigValueType.STRING, List.of("SCORE_TARGET", "TROOP_DEPLETION"));
        conquest(descriptors, RoomConfigCategory.CAPTURE, "campAInitialTroops", "阵营A初始兵力", "conquestConfig.captureConfig.campAInitialTroops", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.CAPTURE, "campBInitialTroops", "阵营B初始兵力", "conquestConfig.captureConfig.campBInitialTroops", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.CAPTURE, "initialCaptureOwnership", "初始据点归属", "conquestConfig.captureConfig.initialCaptureOwnership", RoomConfigValueType.STRING, List.of("EMPTY", "CAMP_A", "CAMP_B"));
        descriptors.add(new RoomConfigDescriptor("capturePoints", RoomConfigScope.CONQUEST, RoomConfigCategory.CAPTURE, "征服据点列表", "conquestConfig.captureConfig.points", RoomConfigValueType.DATA_GROUP, List.of(), List.of(), RoomConfigGroupOperationMode.ADD_DEL_LIST, null, false));

        breakthrough(descriptors, RoomConfigCategory.CAPTURE, "btMapID", "突破地图ID", "breakthroughConfig.mapId", RoomConfigValueType.STRING, List.of());
        breakthrough(descriptors, RoomConfigCategory.CAPTURE, "btCampADisplayName", "进攻方名称", "breakthroughConfig.campADisplayName", RoomConfigValueType.STRING, List.of());
        breakthrough(descriptors, RoomConfigCategory.CAPTURE, "btCampBDisplayName", "防守方名称", "breakthroughConfig.campBDisplayName", RoomConfigValueType.STRING, List.of());
        breakthrough(descriptors, RoomConfigCategory.ROOM, "btStageCount", "阶段数量", "breakthroughConfig.stageCount", RoomConfigValueType.NUMBER, List.of());
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btCampABaseBoundary", "进攻方大本营边界", "breakthroughConfig.campABaseBoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btCampBBaseBoundary", "防守方大本营边界", "breakthroughConfig.campBBaseBoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btStageABoundary", "A阶段边界", "breakthroughConfig.stageABoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btStageBBoundary", "B阶段边界", "breakthroughConfig.stageBBoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btStageCBoundary", "C阶段边界", "breakthroughConfig.stageCBoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btStageDBoundary", "D阶段边界", "breakthroughConfig.stageDBoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btStageEBoundary", "E阶段边界", "breakthroughConfig.stageEBoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.RULES, "btStageFBoundary", "F阶段边界", "breakthroughConfig.stageFBoundary", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.TRANSPORT, "btCampABaseSpawnGroup", "进攻方大本营出生组", "breakthroughConfig.campABaseSpawnGroup", RoomConfigValueType.DATA_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.TRANSPORT, "btCampBBaseSpawnGroup", "防守方大本营出生组", "breakthroughConfig.campBBaseSpawnGroup", RoomConfigValueType.DATA_GROUP);
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btCampABaseDeployIconPosition", "进攻方大本营图标点", "breakthroughConfig.campABaseDeployIconPosition", RoomConfigValueType.POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btCampBBaseDeployIconPosition", "防守方大本营图标点", "breakthroughConfig.campBBaseDeployIconPosition", RoomConfigValueType.POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btStageDeployViewA", "A阶段部署视角", "breakthroughConfig.stageADeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btStageDeployViewB", "B阶段部署视角", "breakthroughConfig.stageBDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btStageDeployViewC", "C阶段部署视角", "breakthroughConfig.stageCDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btStageDeployViewD", "D阶段部署视角", "breakthroughConfig.stageDDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btStageDeployViewE", "E阶段部署视角", "breakthroughConfig.stageEDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.TRANSPORT, "btStageDeployViewF", "F阶段部署视角", "breakthroughConfig.stageFDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        breakthrough(descriptors, RoomConfigCategory.RULES, "btStageDurationMinutes", "阶段持续分钟", "breakthroughConfig.stageDurationMinutes", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btDefenderRetreatSeconds", "防守方撤退秒数", "breakthroughConfig.defenderRetreatSeconds", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btTroopGainPerDefenderKill", "守方击杀补充兵力", "breakthroughConfig.troopGainPerDefenderKill", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btMaxAttackTroopsAt12Players", "12人进攻方最大兵力", "breakthroughConfig.maxAttackTroopsAt12Players", RoomConfigValueType.NUMBER, List.of());

        return List.copyOf(descriptors);
    }

    private static void common(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String... aliases) {
        out.add(descriptor(RoomConfigScope.COMMON, category, id, name, path, type, suggestions, true, RoomConfigGroupOperationMode.NONE, null, aliases));
    }

    private static void conquest(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String... aliases) {
        out.add(descriptor(RoomConfigScope.CONQUEST, category, id, name, path, type, suggestions, true, RoomConfigGroupOperationMode.NONE, null, aliases));
    }

    private static void breakthrough(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String... aliases) {
        out.add(descriptor(RoomConfigScope.BREAKTHROUGH, category, id, name, path, type, suggestions, true, RoomConfigGroupOperationMode.NONE, null, aliases));
    }

    private static void conquestGroup(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, String... aliases) {
        out.add(descriptor(RoomConfigScope.CONQUEST, category, id, name, path, type, List.of(), true, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, aliases));
    }

    private static void breakthroughGroup(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, String... aliases) {
        out.add(descriptor(RoomConfigScope.BREAKTHROUGH, category, id, name, path, type, List.of(), true, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, aliases));
    }

    private static void conquestSpecial(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String handlerId, String... aliases) {
        out.add(descriptor(RoomConfigScope.CONQUEST, category, id, name, path, type, suggestions, false, RoomConfigGroupOperationMode.NONE, handlerId, aliases));
    }

    private static RoomConfigDescriptor descriptor(
        RoomConfigScope scope,
        RoomConfigCategory category,
        String id,
        String name,
        String path,
        RoomConfigValueType type,
        List<String> suggestions,
        boolean enabled,
        RoomConfigGroupOperationMode groupMode,
        String specialHandlerId,
        String... aliases
    ) {
        return new RoomConfigDescriptor(
            id,
            scope,
            category,
            name,
            path,
            type,
            suggestions,
            List.of(aliases),
            groupMode,
            specialHandlerId,
            enabled
        );
    }

    private static Map<String, RoomConfigDescriptor> indexDescriptors(List<RoomConfigDescriptor> descriptors) {
        Map<String, RoomConfigDescriptor> index = new LinkedHashMap<>();
        for (RoomConfigDescriptor descriptor : descriptors) {
            indexSingle(index, descriptor.configId(), descriptor);
            for (String alias : descriptor.legacyAliases()) {
                indexSingle(index, alias, descriptor);
            }
        }
        return Map.copyOf(index);
    }

    private static void indexSingle(Map<String, RoomConfigDescriptor> index, String rawKey, RoomConfigDescriptor descriptor) {
        String normalized = RoomConfigDescriptor.normalizeKey(rawKey);
        if (normalized == null) {
            return;
        }
        RoomConfigDescriptor previous = index.putIfAbsent(normalized, descriptor);
        if (previous != null && previous != descriptor) {
            throw new IllegalStateException("重复的配置描述标识: " + rawKey);
        }
    }

    private static List<String> legacyCommandIds(RoomConfigScope scope) {
        List<String> values = DESCRIPTORS.stream()
            .filter(descriptor -> descriptor.scope() == scope)
            .map(RoomConfigDescriptor::preferredLegacyId)
            .toList();
        if (scope == RoomConfigScope.CONQUEST) {
            List<String> merged = new ArrayList<>(values);
            merged.addAll(dynamicLegacyIds(RoomConfigScope.CONQUEST));
            return List.copyOf(merged);
        }
        if (scope == RoomConfigScope.BREAKTHROUGH) {
            List<String> merged = new ArrayList<>(values);
            merged.addAll(dynamicLegacyIds(RoomConfigScope.BREAKTHROUGH));
            return List.copyOf(merged);
        }
        return List.copyOf(values);
    }

    private static List<String> dynamicLegacyIds(RoomConfigScope scope) {
        List<String> values = new ArrayList<>();
        for (String code : captureCodes()) {
            RoomConfigDescriptor display = dynamicDescriptor(scope, code, "display");
            RoomConfigDescriptor center = dynamicDescriptor(scope, code, "center");
            RoomConfigDescriptor inner = dynamicDescriptor(scope, code, "inner");
            values.add(display.preferredLegacyId());
            values.add(center.preferredLegacyId());
            values.add(inner.preferredLegacyId());
            if (scope == RoomConfigScope.CONQUEST) {
                values.add(dynamicDescriptor(scope, code, "outer").preferredLegacyId());
            } else {
                values.add(dynamicDescriptor(scope, code, "outerA").preferredLegacyId());
                values.add(dynamicDescriptor(scope, code, "outerB").preferredLegacyId());
            }
        }
        return List.copyOf(values);
    }

    private static Optional<RoomConfigDescriptor> findDynamicDescriptor(String normalizedId) {
        for (String code : captureCodes()) {
            for (String kind : dynamicKinds(RoomConfigScope.CONQUEST)) {
                RoomConfigDescriptor descriptor = dynamicDescriptor(RoomConfigScope.CONQUEST, code, kind);
                if (descriptor.matches(normalizedId)) {
                    return Optional.of(descriptor);
                }
            }
            for (String kind : dynamicKinds(RoomConfigScope.BREAKTHROUGH)) {
                RoomConfigDescriptor descriptor = dynamicDescriptor(RoomConfigScope.BREAKTHROUGH, code, kind);
                if (descriptor.matches(normalizedId)) {
                    return Optional.of(descriptor);
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> dynamicKinds(RoomConfigScope scope) {
        return scope == RoomConfigScope.CONQUEST
            ? List.of("display", "center", "inner", "outer")
            : List.of("display", "center", "inner", "outerA", "outerB");
    }

    private static RoomConfigDescriptor dynamicDescriptor(RoomConfigScope scope, String captureCode, String kind) {
        String code = captureCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (scope == RoomConfigScope.CONQUEST) {
            return switch (kind) {
                case "display" -> descriptor(scope, RoomConfigCategory.CAPTURE, "captureDisplayName_" + code, "据点 " + code + " 名称", "conquestConfig.captureConfig.points[" + code + "].displayName", RoomConfigValueType.STRING, List.of("ALPHA", "BRAVO", "CHARLIE"), false, RoomConfigGroupOperationMode.NONE, null, "captureName_" + code);
                case "center" -> descriptor(scope, RoomConfigCategory.CAPTURE, "captureCenterPosition_" + code, "据点 " + code + " 中心点", "conquestConfig.captureConfig.points[" + code + "].centerPosition", RoomConfigValueType.POSITION, List.of("here"), false, RoomConfigGroupOperationMode.NONE, null, "captureCenter_" + code);
                case "inner" -> descriptor(scope, RoomConfigCategory.CAPTURE, "captureInnerSpawnGroup_" + code, "据点 " + code + " 内圈出生组", "conquestConfig.captureConfig.points[" + code + "].innerSpawnGroup", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "captureSpawns_" + code, "captureRespawn_" + code);
                case "outer" -> descriptor(scope, RoomConfigCategory.CAPTURE, "captureOuterSpawnGroup_" + code, "据点 " + code + " 外圈出生组", "conquestConfig.captureConfig.points[" + code + "].outerSpawnGroup", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "captureOuterSpawns_" + code, code + "OuterSpawns");
                default -> throw new IllegalArgumentException("未知动态征服配置类型: " + kind);
            };
        }
        return switch (kind) {
            case "display" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureDisplayName_" + code, "突破据点 " + code + " 名称", "breakthroughConfig.points[" + code + "].displayName", RoomConfigValueType.STRING, List.of("ALPHA", "BRAVO", "CHARLIE"), false, RoomConfigGroupOperationMode.NONE, null, "btCaptureName_" + code);
            case "center" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureCenterPosition_" + code, "突破据点 " + code + " 中心点", "breakthroughConfig.points[" + code + "].centerPosition", RoomConfigValueType.POSITION, List.of("here"), false, RoomConfigGroupOperationMode.NONE, null, "btCaptureCenter_" + code);
            case "inner" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureInnerSpawnGroup_" + code, "突破据点 " + code + " 内圈出生组", "breakthroughConfig.points[" + code + "].innerSpawnGroup", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "btCaptureSpawns_" + code, "btCaptureRespawn_" + code);
            case "outerA" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureCampAOuterSpawnGroup_" + code, "突破据点 " + code + " 攻方外圈出生组", "breakthroughConfig.points[" + code + "].teamOuterSpawnGroups[CAMP_A]", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "btCaptureCampAOuterSpawns_" + code, "btCaptureAttackerSpawns_" + code);
            case "outerB" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureCampBOuterSpawnGroup_" + code, "突破据点 " + code + " 守方外圈出生组", "breakthroughConfig.points[" + code + "].teamOuterSpawnGroups[CAMP_B]", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "btCaptureCampBOuterSpawns_" + code, "btCaptureDefenderSpawns_" + code);
            default -> throw new IllegalArgumentException("未知动态突破配置类型: " + kind);
        };
    }

    private static Collection<String> captureCodes() {
        return ConquestBlocks.captureCodes();
    }
}
