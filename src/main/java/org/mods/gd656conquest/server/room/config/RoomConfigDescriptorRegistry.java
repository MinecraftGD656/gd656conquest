package org.mods.gd656conquest.server.room.config;

import org.mods.gd656conquest.server.room.model.ConquestFullCaptureResponseMode;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.room.model.RoomTroopBalanceReference;
import org.mods.gd656conquest.server.room.model.RoomVoicePack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class RoomConfigDescriptorRegistry {
    private static final List<String> CAPTURE_CODES = List.of(
        "A", "A1", "A2", "A3",
        "B", "B1", "B2", "B3",
        "C", "C1", "C2", "C3",
        "D", "D1", "D2", "D3",
        "E", "E1", "E2", "E3",
        "F", "F1", "F2", "F3"
    );
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

    public static List<String> legacyDeathmatchCommandIds() {
        return legacyCommandIds(RoomConfigScope.DEATHMATCH);
    }

    public static List<String> legacyGunmasterCommandIds() {
        return legacyCommandIds(RoomConfigScope.GUNMASTER);
    }

    public static List<String> legacyRushCommandIds() {
        return legacyCommandIds(RoomConfigScope.RUSH);
    }

    public static List<RoomConfigScope> guiScopes() {
        return List.of(RoomConfigScope.COMMON, RoomConfigScope.CONQUEST, RoomConfigScope.BREAKTHROUGH, RoomConfigScope.DEATHMATCH, RoomConfigScope.GUNMASTER, RoomConfigScope.RUSH);
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

        common(descriptors, RoomConfigCategory.ROOM, "gameplayId", "玩法类型", "gameplayId", RoomConfigValueType.STRING, List.of("CONQUEST", "BREAKTHROUGH", "DEATHMATCH", "GUNMASTER", "RUSH"), "gameplayType");
        common(descriptors, RoomConfigCategory.ROOM, "displayName", "房间名称", "displayName", RoomConfigValueType.STRING, List.of());
        common(descriptors, RoomConfigCategory.ROOM, "dimensionId", "房间维度ID", "commonConfig.dimensionId", RoomConfigValueType.STRING, List.of());
        common(descriptors, RoomConfigCategory.ROOM, "mapName", "地图名称", "commonConfig.mapName", RoomConfigValueType.STRING, List.of());
        common(descriptors, RoomConfigCategory.ROOM, "roomDescription", "房间描述", "commonConfig.roomDescription", RoomConfigValueType.STRING, List.of());
        common(descriptors, RoomConfigCategory.MATCHING, "minStartPlayers", "最低开局人数", "commonConfig.matchingConfig.minStartPlayers", RoomConfigValueType.NUMBER, List.of(), "queueMinStartPlayers");
        common(descriptors, RoomConfigCategory.MATCHING, "queueMaxPlayers", "匹配最大人数", "commonConfig.matchingConfig.totalMaxPlayers", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.MATCHING, "waitAfterMinSeconds", "最低人数后最短等待", "commonConfig.matchingConfig.waitAfterMinSeconds", RoomConfigValueType.NUMBER, List.of(), "queueWaitAfterMinSeconds");
        common(descriptors, RoomConfigCategory.MATCHING, "waitAfterMaxSeconds", "最低人数后最长等待", "commonConfig.matchingConfig.waitAfterMaxSeconds", RoomConfigValueType.NUMBER, List.of(), "queueWaitAfterMaxSeconds");
        common(descriptors, RoomConfigCategory.TRANSPORT, "queueSpawn", "排队出生点", "commonConfig.queueSpawn", RoomConfigValueType.POSITION, List.of("here"));
        common(descriptors, RoomConfigCategory.TRANSPORT, "matchLoadViewPosition", "对局加载展示位置", "commonConfig.matchLoadViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        common(descriptors, RoomConfigCategory.TRANSPORT, "vehicleBackend", "载具后端", "commonConfig.vehicleBackend", RoomConfigValueType.STRING, RoomVehicleBackend.suggestions(), "backend");
        common(descriptors, RoomConfigCategory.PLAYER_STATE, "respawnDelaySeconds", "重生等待秒数", "commonConfig.respawnDelaySeconds", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.RULES, "progressSpeed", "占领速度", "commonConfig.progressSpeed", RoomConfigValueType.NUMBER, List.of(), "captureProgressSpeed");
        common(descriptors, RoomConfigCategory.RULES, "enablePlayerDowned", "启用倒地", "commonConfig.enablePlayerDowned", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnablePlayerDowned");
        common(
            descriptors,
            RoomConfigCategory.PLAYER_STATE,
            "enableRespawnCountdownWhileDowned",
            "倒地时开始部署倒计时",
            "commonConfig.enableRespawnCountdownWhileDowned",
            RoomConfigValueType.BOOLEAN,
            List.of("true", "false")
        );
        common(descriptors, RoomConfigCategory.RULES, "enableBackAgainstWall", "启用背水一战", "commonConfig.enableBackAgainstWall", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnableBackAgainstWall");
        common(descriptors, RoomConfigCategory.RULES, "enableRandomLoadout", "启用随机配备", "commonConfig.enableRandomLoadout", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnableRandomLoadout");
        common(descriptors, RoomConfigCategory.RULES, "enableRandomAttachments", "启用房间随机配件", "commonConfig.enableRandomAttachments", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnableRandomAttachments");
        common(descriptors, RoomConfigCategory.RULES, "disableUndyingTotem", "是否禁用不死图腾", "commonConfig.disableUndyingTotem", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolDisableUndyingTotem");
        common(descriptors, RoomConfigCategory.RULES, "allowPlayerCampSwitch", "是否允许玩家切换阵营", "commonConfig.allowPlayerCampSwitch", RoomConfigValueType.BOOLEAN, List.of("true", "false"));
        // GD656Mods: 限定槽位是否忽略兵种限制(默认 true = 保持"限定后全兵种可用"的原行为)
        common(descriptors, RoomConfigCategory.RULES, "ignoreClassLimitOnRestriction", "限定配装是否忽略兵种限制", "commonConfig.ignoreClassLimitOnRestriction", RoomConfigValueType.BOOLEAN, List.of("true", "false"));
        // GD656Mods: 房间武器是否忽略枪械等级(默认 false); 开启后本房间可选用未解锁武器, 离开房间自动回退兵种默认武器
        common(descriptors, RoomConfigCategory.RULES, "ignoreGunLevel", "房间所用武器忽略枪械等级", "commonConfig.ignoreGunLevel", RoomConfigValueType.BOOLEAN, List.of("true", "false"));
        common(descriptors, RoomConfigCategory.RULES, "enableParachute", "启用对局降落伞", "commonConfig.enableParachute", RoomConfigValueType.BOOLEAN, List.of("true", "false"), "boolEnableParachute");
        common(descriptors, RoomConfigCategory.RULES, "enableDeathSupportDrop", "启用死亡掉落补给包", "commonConfig.enableDeathSupportDrop", RoomConfigValueType.BOOLEAN, List.of("true", "false"));
        common(descriptors, RoomConfigCategory.RULES, "enableGiveArrow", "是否给予玩家箭矢", "commonConfig.enableGiveArrow", RoomConfigValueType.BOOLEAN, List.of("true", "false"));
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "supportCampAHeroKits", "阵地支援-campA可用精英兵", "commonConfig.supportCampAHeroKits", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "supportCampBHeroKits", "阵地支援-campB可用精英兵", "commonConfig.supportCampBHeroKits", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "supportCampAShellings", "阵地支援-campA可用炮击", "commonConfig.supportCampAShellings", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "supportCampBShellings", "阵地支援-campB可用炮击", "commonConfig.supportCampBShellings", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campAPrimaryWeaponIds", "campA限定主武器ID数据组", "commonConfig.campAPrimaryWeaponIds", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campASecondaryWeaponIds", "campA限定副武器ID数据组", "commonConfig.campASecondaryWeaponIds", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campAMeleeWeaponIds", "campA限定近战武器ID数据组", "commonConfig.campAMeleeWeaponIds", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campAGadget1Ids", "campA限定战术道具一ID数据组", "commonConfig.campAGadget1Ids", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campAGadget2Ids", "campA限定战术道具二ID数据组", "commonConfig.campAGadget2Ids", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campAThrowableIds", "campA限定投掷物ID数据组", "commonConfig.campAThrowableIds", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campBPrimaryWeaponIds", "campB限定主武器ID数据组", "commonConfig.campBPrimaryWeaponIds", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campBSecondaryWeaponIds", "campB限定副武器ID数据组", "commonConfig.campBSecondaryWeaponIds", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campBMeleeWeaponIds", "campB限定近战武器ID数据组", "commonConfig.campBMeleeWeaponIds", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campBGadget1Ids", "campB限定战术道具一ID数据组", "commonConfig.campBGadget1Ids", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campBGadget2Ids", "campB限定战术道具二ID数据组", "commonConfig.campBGadget2Ids", RoomConfigValueType.DATA_GROUP);
        commonGroup(descriptors, RoomConfigCategory.EQUIPMENT, "campBThrowableIds", "campB限定投掷物ID数据组", "commonConfig.campBThrowableIds", RoomConfigValueType.DATA_GROUP);
        common(descriptors, RoomConfigCategory.EQUIPMENT, "campAEquipmentSetId", "campA穿戴护甲", "commonConfig.campAEquipmentSetId", RoomConfigValueType.STRING, List.of(), "campAEquipment");
        common(descriptors, RoomConfigCategory.EQUIPMENT, "campBEquipmentSetId", "campB穿戴护甲", "commonConfig.campBEquipmentSetId", RoomConfigValueType.STRING, List.of(), "campBEquipment");
        common(descriptors, RoomConfigCategory.RESULT, "endViewPosition", "结算视角", "commonConfig.resultConfig.endViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"), "matchEndViewPosition");

        common(descriptors, RoomConfigCategory.PLAYER_STATE, "gameMode", "玩家游戏模式", "commonConfig.playerStateConfig.gameMode", RoomConfigValueType.STRING, List.of("SURVIVAL", "ADVENTURE", "CREATIVE", "SPECTATOR"), "playerGameMode");
        common(descriptors, RoomConfigCategory.PLAYER_STATE, "invulnerableSeconds", "重生无敌秒数", "commonConfig.playerStateConfig.invulnerableSeconds", RoomConfigValueType.NUMBER, List.of(), "respawnInvulnerableSeconds");
        common(descriptors, RoomConfigCategory.PLAYER_STATE, "revivedInvulnerableSeconds", "救起无敌秒数", "commonConfig.playerStateConfig.revivedInvulnerableSeconds", RoomConfigValueType.NUMBER, List.of());
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
        common(descriptors, RoomConfigCategory.RULES, "outsideBoundaryDeathSeconds", "玩家出界死亡秒数", "commonConfig.outsideBoundaryDeathSeconds", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.RULES, "aircraftOutsideBoundaryCleanupSeconds", "载具出界清理秒数", "commonConfig.aircraftOutsideBoundaryCleanupSeconds", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.RULES, "playerMaxY", "玩家最大Y轴", "commonConfig.playerMaxY", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.RULES, "aircraftPassengerMaxY", "空中载具乘员最大Y轴", "commonConfig.aircraftPassengerMaxY", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.TRANSPORT, "teamDeployIconCampA", "阵营A部署图标", "conquestConfig.teamDeployIcons[CAMP_A]", RoomConfigValueType.POSITION, List.of("here"), "campADeployIconPosition");
        conquest(descriptors, RoomConfigCategory.TRANSPORT, "teamDeployIconCampB", "阵营B部署图标", "conquestConfig.teamDeployIcons[CAMP_B]", RoomConfigValueType.POSITION, List.of("here"), "campBDeployIconPosition");
        conquest(descriptors, RoomConfigCategory.TRANSPORT, "deployViewPosition", "部署界面视角", "conquestConfig.deployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"), "deployPagePosition");
        conquestGroup(descriptors, RoomConfigCategory.TRANSPORT, "ammoBoxPositions", "征服对局弹药箱数据组", "conquestConfig.ammoBoxPositions", RoomConfigValueType.POSITION_GROUP, "conquestAmmoBoxPositions");
        conquestGroup(descriptors, RoomConfigCategory.TRANSPORT, "medicalBoxPositions", "征服对局医疗箱数据组", "conquestConfig.medicalBoxPositions", RoomConfigValueType.POSITION_GROUP, "conquestMedicalBoxPositions");
        conquestGroup(descriptors, RoomConfigCategory.RULES, "sharedBoundary", "公共边界", "conquestConfig.sharedBoundary", RoomConfigValueType.POSITION_GROUP, "gameBoundary");
        conquestGroup(descriptors, RoomConfigCategory.RULES, "campABoundary", "阵营A边界", "conquestConfig.campABoundary", RoomConfigValueType.POSITION_GROUP, "campAGameBoundary");
        conquestGroup(descriptors, RoomConfigCategory.RULES, "campBBoundary", "阵营B边界", "conquestConfig.campBBoundary", RoomConfigValueType.POSITION_GROUP, "campBGameBoundary");
        conquest(descriptors, RoomConfigCategory.CAPTURE, "mapId", "征服地图ID", "conquestConfig.captureConfig.mapId", RoomConfigValueType.STRING, List.of());
        conquestGroup(descriptors, RoomConfigCategory.TRANSPORT, "campASpawnGroup", "阵营A出生组", "conquestConfig.teamSpawnGroups[CAMP_A]", RoomConfigValueType.DATA_GROUP, "campASpawns", "campACoalition");
        conquestGroup(descriptors, RoomConfigCategory.TRANSPORT, "campBSpawnGroup", "阵营B出生组", "conquestConfig.teamSpawnGroups[CAMP_B]", RoomConfigValueType.DATA_GROUP, "campBSpawns", "campBMilitia");
        conquest(descriptors, RoomConfigCategory.CAPTURE, "drawDurationMinutes", "平局持续分钟", "conquestConfig.captureConfig.drawDurationMinutes", RoomConfigValueType.NUMBER, List.of());
        conquest(descriptors, RoomConfigCategory.CAPTURE, "fullCaptureEndSeconds", "全点占领结束秒数", "conquestConfig.captureConfig.fullCaptureEndSeconds", RoomConfigValueType.NUMBER, List.of());
        conquest(
            descriptors,
            RoomConfigCategory.CAPTURE,
            "fullCaptureResponseMode",
            "对局面对压家的处理方式",
            "conquestConfig.captureConfig.fullCaptureResponseMode",
            RoomConfigValueType.STRING,
            ConquestFullCaptureResponseMode.suggestions()
        );
        conquest(
            descriptors,
            RoomConfigCategory.CAPTURE,
            "antiBaseNukeTriggerSeconds",
            "触发防压家核弹的所需时长",
            "conquestConfig.captureConfig.antiBaseNukeTriggerSeconds",
            RoomConfigValueType.NUMBER,
            List.of()
        );
        conquest(
            descriptors,
            RoomConfigCategory.CAPTURE,
            "antiBaseNukeRespawnDelaySeconds",
            "玩家被防压家核弹炸死后的重新部署时长",
            "conquestConfig.captureConfig.antiBaseNukeRespawnDelaySeconds",
            RoomConfigValueType.NUMBER,
            List.of()
        );
        conquest(
            descriptors,
            RoomConfigCategory.CAPTURE,
            "antiBaseNukeTriggerCooldownSeconds",
            "防压家核弹触发间隔CD",
            "conquestConfig.captureConfig.antiBaseNukeTriggerCooldownSeconds",
            RoomConfigValueType.NUMBER,
            List.of()
        );
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
        breakthroughGroup(descriptors, RoomConfigCategory.TRANSPORT, "btAmmoBoxPositions", "突破对局弹药箱数据组", "breakthroughConfig.ammoBoxPositions", RoomConfigValueType.POSITION_GROUP);
        breakthroughGroup(descriptors, RoomConfigCategory.TRANSPORT, "btMedicalBoxPositions", "突破对局医疗箱数据组", "breakthroughConfig.medicalBoxPositions", RoomConfigValueType.POSITION_GROUP);
        breakthrough(descriptors, RoomConfigCategory.RULES, "btStageDurationMinutes", "阶段持续分钟", "breakthroughConfig.stageDurationMinutes", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btDefenderRetreatSeconds", "防守方撤退秒数", "breakthroughConfig.defenderRetreatSeconds", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btTroopGainPerDefenderKill", "守方击杀补充兵力", "breakthroughConfig.troopGainPerDefenderKill", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btMaxAttackTroopsAt12Players", "12人进攻方最大兵力", "breakthroughConfig.maxAttackTroopsAt12Players", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btStageBTroopRestoreRatio", "突破B阶段开始时恢复兵力比值", "breakthroughConfig.stageBTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btStageCTroopRestoreRatio", "突破C阶段开始时恢复兵力比值", "breakthroughConfig.stageCTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btStageDTroopRestoreRatio", "突破D阶段开始时恢复兵力比值", "breakthroughConfig.stageDTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btStageETroopRestoreRatio", "突破E阶段开始时恢复兵力比值", "breakthroughConfig.stageETroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btStageFTroopRestoreRatio", "突破F阶段开始时恢复兵力比值", "breakthroughConfig.stageFTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        breakthrough(descriptors, RoomConfigCategory.RULES, "btTroopBalanceReference", "突破兵力计算基准", "breakthroughConfig.troopBalanceReference", RoomConfigValueType.STRING, RoomTroopBalanceReference.suggestions(), "troopBalanceReference");

        deathmatch(descriptors, RoomConfigCategory.CAPTURE, "dmMapID", "团队死斗地图ID", "deathmatchConfig.mapId", RoomConfigValueType.STRING, List.of());
        deathmatch(descriptors, RoomConfigCategory.RULES, "dmMatchDurationMinutes", "团队死斗对局时长(分钟)", "deathmatchConfig.matchDurationMinutes", RoomConfigValueType.NUMBER, List.of());
        deathmatch(descriptors, RoomConfigCategory.RULES, "dmTargetScore", "团队死斗目标分数", "deathmatchConfig.targetScore", RoomConfigValueType.NUMBER, List.of());
        deathmatch(descriptors, RoomConfigCategory.RULES, "dmStartCountdownSeconds", "团队死斗开局倒计时秒数", "deathmatchConfig.startCountdownSeconds", RoomConfigValueType.NUMBER, List.of());
        deathmatch(descriptors, RoomConfigCategory.PLAYER_STATE, "dmRespawnSeconds", "团队死斗玩家重生秒数", "deathmatchConfig.respawnSeconds", RoomConfigValueType.NUMBER, List.of());
        deathmatch(descriptors, RoomConfigCategory.TRANSPORT, "dmDeployViewPosition", "团队死斗部署界面视角", "deathmatchConfig.deployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        deathmatchGroup(descriptors, RoomConfigCategory.TRANSPORT, "dmAmmoBoxPositions", "团队死斗对局弹药箱数据组", "deathmatchConfig.ammoBoxPositions", RoomConfigValueType.POSITION_GROUP);
        deathmatchGroup(descriptors, RoomConfigCategory.TRANSPORT, "dmMedicalBoxPositions", "团队死斗对局医疗箱数据组", "deathmatchConfig.medicalBoxPositions", RoomConfigValueType.POSITION_GROUP);
        deathmatchGroup(descriptors, RoomConfigCategory.RULES, "dmCampABoundary", "团队A活动范围", "deathmatchConfig.campABoundary", RoomConfigValueType.POSITION_GROUP);
        deathmatchGroup(descriptors, RoomConfigCategory.RULES, "dmCampBBoundary", "团队B活动范围", "deathmatchConfig.campBBoundary", RoomConfigValueType.POSITION_GROUP);
        deathmatchGroup(descriptors, RoomConfigCategory.TRANSPORT, "dmCampASpawnGroup", "团队A出生组", "deathmatchConfig.campASpawnGroup", RoomConfigValueType.DATA_GROUP);
        deathmatchGroup(descriptors, RoomConfigCategory.TRANSPORT, "dmCampBSpawnGroup", "团队B出生组", "deathmatchConfig.campBSpawnGroup", RoomConfigValueType.DATA_GROUP);
        gunmaster(descriptors, RoomConfigCategory.CAPTURE, "gmMapID", "枪王模式地图ID", "gunmasterConfig.mapId", RoomConfigValueType.STRING, List.of());
        gunmaster(descriptors, RoomConfigCategory.RULES, "gmMatchDurationMinutes", "枪王模式对局时长(分钟)", "gunmasterConfig.matchDurationMinutes", RoomConfigValueType.NUMBER, List.of());
        gunmaster(descriptors, RoomConfigCategory.RULES, "gmTargetScore", "枪王模式目标分数", "gunmasterConfig.targetScore", RoomConfigValueType.NUMBER, List.of());
        gunmaster(descriptors, RoomConfigCategory.RULES, "gmStartCountdownSeconds", "枪王模式开局倒计时秒数", "gunmasterConfig.startCountdownSeconds", RoomConfigValueType.NUMBER, List.of());
        gunmaster(descriptors, RoomConfigCategory.PLAYER_STATE, "gmRespawnSeconds", "枪王模式玩家重生秒数", "gunmasterConfig.respawnSeconds", RoomConfigValueType.NUMBER, List.of());
        gunmaster(descriptors, RoomConfigCategory.RULES, "gmPlayerGroupingMode", "枪王模式玩家编组模式", "gunmasterConfig.groupingMode", RoomConfigValueType.STRING, org.mods.gd656conquest.server.room.model.RoomGunmasterGroupingMode.suggestions());
        gunmaster(descriptors, RoomConfigCategory.RULES, "gmEnabledMiddleWeaponCount", "枪王模式启用的中段武器数量", "gunmasterConfig.enabledMiddleWeaponCount", RoomConfigValueType.NUMBER, List.of());
        gunmaster(descriptors, RoomConfigCategory.RULES, "gmKillsPerWeaponSwitch", "枪王模式玩家切换武器所需击杀数", "gunmasterConfig.killsPerWeaponSwitch", RoomConfigValueType.NUMBER, List.of());
        gunmaster(descriptors, RoomConfigCategory.TRANSPORT, "gmDeployViewPosition", "枪王模式部署界面视角", "gunmasterConfig.deployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        gunmasterGroup(descriptors, RoomConfigCategory.TRANSPORT, "gmSoloSpawnPositions", "枪王模式个人战出生点数据组", "gunmasterConfig.soloSpawnPositions", RoomConfigValueType.POSITION_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.RULES, "gmSoloBoundary", "枪王模式个人战活动范围", "gunmasterConfig.soloBoundary", RoomConfigValueType.POSITION_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.EQUIPMENT, "gmInitialWeaponPool", "枪王模式初始武器池", "gunmasterConfig.initialWeaponPool", RoomConfigValueType.DATA_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.EQUIPMENT, "gmMiddleWeaponPool", "枪王模式中段武器池", "gunmasterConfig.middleWeaponPool", RoomConfigValueType.DATA_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.EQUIPMENT, "gmFinalWeaponPool", "枪王模式末段武器池", "gunmasterConfig.finalWeaponPool", RoomConfigValueType.DATA_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.TRANSPORT, "gmMedicalBoxPositions", "枪王模式对局医疗箱数据组", "gunmasterConfig.medicalBoxPositions", RoomConfigValueType.POSITION_GROUP);
        // GD656Mods: 枪王模式此前没有弹药箱配置入口(字段存在但无 descriptor), 补上以与其它模式一致
        gunmasterGroup(descriptors, RoomConfigCategory.TRANSPORT, "gmAmmoBoxPositions", "枪王模式对局弹药箱数据组", "gunmasterConfig.ammoBoxPositions", RoomConfigValueType.POSITION_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.RULES, "gmCampABoundary", "枪王模式团队A活动范围", "gunmasterConfig.campABoundary", RoomConfigValueType.POSITION_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.RULES, "gmCampBBoundary", "枪王模式团队B活动范围", "gunmasterConfig.campBBoundary", RoomConfigValueType.POSITION_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.TRANSPORT, "gmCampASpawnGroup", "枪王模式团队A出生组", "gunmasterConfig.campASpawnGroup", RoomConfigValueType.DATA_GROUP);
        gunmasterGroup(descriptors, RoomConfigCategory.TRANSPORT, "gmCampBSpawnGroup", "枪王模式团队B出生组", "gunmasterConfig.campBSpawnGroup", RoomConfigValueType.DATA_GROUP);

        rush(descriptors, RoomConfigCategory.CAPTURE, "rsMapID", "突袭模式地图ID", "rushConfig.mapId", RoomConfigValueType.STRING, List.of());
        rush(descriptors, RoomConfigCategory.CAPTURE, "rsCampADisplayName", "进攻方名称", "rushConfig.campADisplayName", RoomConfigValueType.STRING, List.of());
        rush(descriptors, RoomConfigCategory.CAPTURE, "rsCampBDisplayName", "防守方名称", "rushConfig.campBDisplayName", RoomConfigValueType.STRING, List.of());
        rush(descriptors, RoomConfigCategory.ROOM, "rsStageCount", "阶段数量", "rushConfig.stageCount", RoomConfigValueType.NUMBER, List.of());
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsCampABaseBoundary", "进攻方大本营边界", "rushConfig.campABaseBoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsCampBBaseBoundary", "防守方大本营边界", "rushConfig.campBBaseBoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsStageABoundary", "A阶段边界", "rushConfig.stageABoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsStageBBoundary", "B阶段边界", "rushConfig.stageBBoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsStageCBoundary", "C阶段边界", "rushConfig.stageCBoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsStageDBoundary", "D阶段边界", "rushConfig.stageDBoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsStageEBoundary", "E阶段边界", "rushConfig.stageEBoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.RULES, "rsStageFBoundary", "F阶段边界", "rushConfig.stageFBoundary", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.TRANSPORT, "rsCampABaseSpawnGroup", "进攻方大本营出生组", "rushConfig.campABaseSpawnGroup", RoomConfigValueType.DATA_GROUP);
        rushGroup(descriptors, RoomConfigCategory.TRANSPORT, "rsCampBBaseSpawnGroup", "防守方大本营出生组", "rushConfig.campBBaseSpawnGroup", RoomConfigValueType.DATA_GROUP);
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsCampABaseDeployIconPosition", "进攻方大本营图标点", "rushConfig.campABaseDeployIconPosition", RoomConfigValueType.POSITION, List.of("here"));
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsCampBBaseDeployIconPosition", "防守方大本营图标点", "rushConfig.campBBaseDeployIconPosition", RoomConfigValueType.POSITION, List.of("here"));
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsStageDeployViewA", "A阶段部署视角", "rushConfig.stageADeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsStageDeployViewB", "B阶段部署视角", "rushConfig.stageBDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsStageDeployViewC", "C阶段部署视角", "rushConfig.stageCDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsStageDeployViewD", "D阶段部署视角", "rushConfig.stageDDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsStageDeployViewE", "E阶段部署视角", "rushConfig.stageEDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        rush(descriptors, RoomConfigCategory.TRANSPORT, "rsStageDeployViewF", "F阶段部署视角", "rushConfig.stageFDeployViewPosition", RoomConfigValueType.VIEW_POSITION, List.of("here"));
        rushGroup(descriptors, RoomConfigCategory.TRANSPORT, "rsAmmoBoxPositions", "突袭模式弹药箱数据组", "rushConfig.ammoBoxPositions", RoomConfigValueType.POSITION_GROUP);
        rushGroup(descriptors, RoomConfigCategory.TRANSPORT, "rsMedicalBoxPositions", "突袭模式医疗箱数据组", "rushConfig.medicalBoxPositions", RoomConfigValueType.POSITION_GROUP);
        rush(descriptors, RoomConfigCategory.RULES, "rsStageDurationMinutes", "阶段持续分钟", "rushConfig.stageDurationMinutes", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsDefenderRetreatSeconds", "防守方撤退秒数", "rushConfig.defenderRetreatSeconds", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsTroopGainPerDefenderKill", "撤退阶段进攻方击杀回兵", "rushConfig.troopGainPerDefenderKill", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsMaxAttackTroopsAt12Players", "12人进攻方最大兵力", "rushConfig.maxAttackTroopsAt12Players", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsStageBTroopRestoreRatio", "突袭B阶段开始时恢复兵力比值", "rushConfig.stageBTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsStageCTroopRestoreRatio", "突袭C阶段开始时恢复兵力比值", "rushConfig.stageCTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsStageDTroopRestoreRatio", "突袭D阶段开始时恢复兵力比值", "rushConfig.stageDTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsStageETroopRestoreRatio", "突袭E阶段开始时恢复兵力比值", "rushConfig.stageETroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsStageFTroopRestoreRatio", "突袭F阶段开始时恢复兵力比值", "rushConfig.stageFTroopRestoreRatio", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsTroopBalanceReference", "突袭模式兵力计算基准", "rushConfig.troopBalanceReference", RoomConfigValueType.STRING, RoomTroopBalanceReference.suggestions());
        rush(descriptors, RoomConfigCategory.RULES, "rsBombPlantTimeSeconds", "炸弹安装时间(秒)", "rushConfig.bombPlantTimeSeconds", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsBombDefuseTimeSeconds", "炸弹拆除时间(秒)", "rushConfig.bombDefuseTimeSeconds", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsBombFuseTimeSeconds", "炸弹引信时长(秒)", "rushConfig.bombFuseTimeSeconds", RoomConfigValueType.NUMBER, List.of());
        rush(descriptors, RoomConfigCategory.RULES, "rsBombExplosionRadius", "炸弹爆炸半径", "rushConfig.bombExplosionRadius", RoomConfigValueType.NUMBER, List.of());

        return List.copyOf(descriptors);
    }

    private static void common(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String... aliases) {
        out.add(descriptor(RoomConfigScope.COMMON, category, id, name, path, type, suggestions, true, RoomConfigGroupOperationMode.NONE, null, aliases));
    }

    private static void commonGroup(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, String... aliases) {
        out.add(descriptor(RoomConfigScope.COMMON, category, id, name, path, type, List.of(), true, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, aliases));
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

    private static void deathmatch(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String... aliases) {
        out.add(descriptor(RoomConfigScope.DEATHMATCH, category, id, name, path, type, suggestions, true, RoomConfigGroupOperationMode.NONE, null, aliases));
    }

    private static void deathmatchGroup(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, String... aliases) {
        out.add(descriptor(RoomConfigScope.DEATHMATCH, category, id, name, path, type, List.of(), true, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, aliases));
    }

    private static void gunmaster(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String... aliases) {
        out.add(descriptor(RoomConfigScope.GUNMASTER, category, id, name, path, type, suggestions, true, RoomConfigGroupOperationMode.NONE, null, aliases));
    }

    private static void gunmasterGroup(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, String... aliases) {
        out.add(descriptor(RoomConfigScope.GUNMASTER, category, id, name, path, type, List.of(), true, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, aliases));
    }

    private static void rush(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, List<String> suggestions, String... aliases) {
        out.add(descriptor(RoomConfigScope.RUSH, category, id, name, path, type, suggestions, true, RoomConfigGroupOperationMode.NONE, null, aliases));
    }

    private static void rushGroup(List<RoomConfigDescriptor> out, RoomConfigCategory category, String id, String name, String path, RoomConfigValueType type, String... aliases) {
        out.add(descriptor(RoomConfigScope.RUSH, category, id, name, path, type, List.of(), true, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, aliases));
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
        if (scope == RoomConfigScope.RUSH) {
            List<String> merged = new ArrayList<>(values);
            merged.addAll(dynamicLegacyIds(RoomConfigScope.RUSH));
            return List.copyOf(merged);
        }
        return List.copyOf(values);
    }

    private static List<String> dynamicLegacyIds(RoomConfigScope scope) {
        List<String> values = new ArrayList<>();
        for (String code : captureCodes(scope)) {
            if (scope == RoomConfigScope.RUSH) {
                values.add(dynamicDescriptor(scope, code, "bomb").preferredLegacyId());
                values.add(dynamicDescriptor(scope, code, "spawnA").preferredLegacyId());
                values.add(dynamicDescriptor(scope, code, "spawnB").preferredLegacyId());
                continue;
            }
            RoomConfigDescriptor display = dynamicDescriptor(scope, code, "display");
            RoomConfigDescriptor center = dynamicDescriptor(scope, code, "center");
            RoomConfigDescriptor inner = dynamicDescriptor(scope, code, "inner");
            values.add(display.preferredLegacyId());
            values.add(center.preferredLegacyId());
            values.add(inner.preferredLegacyId());
            if (scope == RoomConfigScope.CONQUEST) {
                values.add(dynamicDescriptor(scope, code, "outer").preferredLegacyId());
            } else {
                values.add(dynamicDescriptor(scope, code, "stage").preferredLegacyId());
                values.add(dynamicDescriptor(scope, code, "outerA").preferredLegacyId());
                values.add(dynamicDescriptor(scope, code, "outerB").preferredLegacyId());
            }
        }
        return List.copyOf(values);
    }

    private static Optional<RoomConfigDescriptor> findDynamicDescriptor(String normalizedId) {
        for (String code : captureCodes(RoomConfigScope.CONQUEST)) {
            for (String kind : dynamicKinds(RoomConfigScope.CONQUEST)) {
                RoomConfigDescriptor descriptor = dynamicDescriptor(RoomConfigScope.CONQUEST, code, kind);
                if (descriptor.matches(normalizedId)) {
                    return Optional.of(descriptor);
                }
            }
        }
        for (String code : captureCodes(RoomConfigScope.BREAKTHROUGH)) {
            for (String kind : dynamicKinds(RoomConfigScope.BREAKTHROUGH)) {
                RoomConfigDescriptor descriptor = dynamicDescriptor(RoomConfigScope.BREAKTHROUGH, code, kind);
                if (descriptor.matches(normalizedId)) {
                    return Optional.of(descriptor);
                }
            }
        }
        for (String code : captureCodes(RoomConfigScope.RUSH)) {
            for (String kind : dynamicKinds(RoomConfigScope.RUSH)) {
                RoomConfigDescriptor descriptor = dynamicDescriptor(RoomConfigScope.RUSH, code, kind);
                if (descriptor.matches(normalizedId)) {
                    return Optional.of(descriptor);
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> dynamicKinds(RoomConfigScope scope) {
        if (scope == RoomConfigScope.CONQUEST) {
            return List.of("display", "center", "inner", "outer");
        } else if (scope == RoomConfigScope.BREAKTHROUGH) {
            return List.of("display", "center", "inner", "stage", "outerA", "outerB");
        } else if (scope == RoomConfigScope.RUSH) {
            return List.of("bomb", "spawnA", "spawnB");
        }
        return List.of();
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
        if (scope == RoomConfigScope.RUSH) {
            return switch (kind) {
                case "bomb" -> descriptor(scope, RoomConfigCategory.CAPTURE, "rsBombPosition_" + code, "炸弹点 " + code + " 坐标", "rushConfig.bombPositions[" + code + "]", RoomConfigValueType.POSITION, List.of("here"), false, RoomConfigGroupOperationMode.NONE, null, "rsBombCenter_" + code);
                // 注意：spawnA 对应内部字段 bombCampASpawnGroup<CODE>（炸弹点内部出生组），显示名为"内部出生点数据组"
                case "spawnA" -> descriptor(scope, RoomConfigCategory.TRANSPORT, "rsBombInnerSpawnGroup_" + code, "炸弹点 " + code + " 内部出生点数据组", "rushConfig.bombCampASpawnGroup" + code, RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "rsBombInnerSpawns_" + code);
                // 注意：spawnB 对应内部字段 bombCampBSpawnGroup<CODE>（靠防守方内部出生组），显示名为"靠防守方内部出生点组"
                case "spawnB" -> descriptor(scope, RoomConfigCategory.TRANSPORT, "rsBombDefenderInnerSpawnGroup_" + code, "炸弹点 " + code + " 靠防守方内部出生点组", "rushConfig.bombCampBSpawnGroup" + code, RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "rsBombDefenderInnerSpawns_" + code);
                default -> throw new IllegalArgumentException("未知动态突袭配置类型: " + kind);
            };
        }
        return switch (kind) {
            case "display" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureDisplayName_" + code, "突破据点 " + code + " 名称", "breakthroughConfig.points[" + code + "].displayName", RoomConfigValueType.STRING, List.of("ALPHA", "BRAVO", "CHARLIE"), false, RoomConfigGroupOperationMode.NONE, null, "btCaptureName_" + code);
            case "center" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureCenterPosition_" + code, "突破据点 " + code + " 中心点", "breakthroughConfig.points[" + code + "].centerPosition", RoomConfigValueType.POSITION, List.of("here"), false, RoomConfigGroupOperationMode.NONE, null, "btCaptureCenter_" + code);
            case "inner" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureInnerSpawnGroup_" + code, "突破据点 " + code + " 内圈出生组", "breakthroughConfig.points[" + code + "].innerSpawnGroup", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "btCaptureSpawns_" + code, "btCaptureRespawn_" + code);
            case "stage" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureStageIndex_" + code, "突破据点 " + code + " 阶段", "breakthroughConfig.points[" + code + "].stageIndex", RoomConfigValueType.NUMBER, List.of("1", "2", "3", "4", "5", "6"), false, RoomConfigGroupOperationMode.NONE, null, "btCaptureStage_" + code);
            case "outerA" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureCampAOuterSpawnGroup_" + code, "突破据点 " + code + " 攻方外圈出生组", "breakthroughConfig.points[" + code + "].teamOuterSpawnGroups[CAMP_A]", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "btCaptureCampAOuterSpawns_" + code, "btCaptureAttackerSpawns_" + code);
            case "outerB" -> descriptor(scope, RoomConfigCategory.CAPTURE, "btCaptureCampBOuterSpawnGroup_" + code, "突破据点 " + code + " 守方外圈出生组", "breakthroughConfig.points[" + code + "].teamOuterSpawnGroups[CAMP_B]", RoomConfigValueType.DATA_GROUP, List.of(), false, RoomConfigGroupOperationMode.ADD_DEL_LIST, null, "btCaptureCampBOuterSpawns_" + code, "btCaptureDefenderSpawns_" + code);
            default -> throw new IllegalArgumentException("未知动态突破配置类型: " + kind);
        };
    }

    private static Collection<String> captureCodes(RoomConfigScope scope) {
        return CAPTURE_CODES;
    }
}
