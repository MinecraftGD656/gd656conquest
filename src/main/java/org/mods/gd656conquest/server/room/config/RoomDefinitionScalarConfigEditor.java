package org.mods.gd656conquest.server.room.config;

import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomGunmasterGroupingMode;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomTroopBalanceReference;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.model.RoomVoicePack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RoomDefinitionScalarConfigEditor {
    private static final Gson GSON = new Gson();
    private static final String DELETE_TOKEN = "del";
    private static final String MATCHING_CONFIG_PATH = "commonConfig.matchingConfig";

    private RoomDefinitionScalarConfigEditor() {
    }

    public static RoomDefinition apply(RoomDefinition definition, RoomConfigDescriptor descriptor, String rawValue, ServerPlayer editor) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(descriptor, "descriptor");
        if (isDeleteToken(rawValue)) {
            return clear(definition, descriptor);
        }

        String configId = normalizedConfigId(descriptor);
        if ("queuemaxplayers".equals(configId)) {
            RoomMatchingConfig updated = splitQueueMaxPlayers(definition.commonConfig().matchingConfig(), parsePositiveInt(rawValue));
            return RoomConfigBindingAccess.write(definition, MATCHING_CONFIG_PATH, updated);
        }
        if (isDynamicConquestPoint(configId)) {
            return applyDynamicConquestPoint(definition, configId, rawValue, editor);
        }
        if (isDynamicBreakthroughPoint(configId)) {
            return applyDynamicBreakthroughPoint(definition, configId, rawValue, editor);
        }

        Object parsedValue = parseValue(descriptor, rawValue, editor);
        return RoomConfigBindingAccess.write(definition, descriptor.newPath(), parsedValue);
    }

    private static RoomDefinition clear(RoomDefinition definition, RoomConfigDescriptor descriptor) {
        String configId = normalizedConfigId(descriptor);
        if ("queuemaxplayers".equals(configId)) {
            RoomDefinition defaults = defaultDefinition(definition);
            int totalPlayers = (Integer) RoomConfigBindingAccess.resolve(defaults, "commonConfig.matchingConfig.totalMaxPlayers");
            RoomMatchingConfig updated = splitQueueMaxPlayers(definition.commonConfig().matchingConfig(), totalPlayers);
            return RoomConfigBindingAccess.write(definition, MATCHING_CONFIG_PATH, updated);
        }
        if (isDynamicConquestPoint(configId)) {
            return clearDynamicConquestPoint(definition, configId);
        }
        if (isDynamicBreakthroughPoint(configId)) {
            return clearDynamicBreakthroughPoint(definition, configId);
        }

        Object defaultValue = RoomConfigBindingAccess.resolve(defaultDefinition(definition), descriptor.newPath());
        return RoomConfigBindingAccess.write(definition, descriptor.newPath(), defaultValue);
    }

    private static RoomDefinition applyDynamicConquestPoint(RoomDefinition definition, String configId, String rawValue, ServerPlayer editor) {
        String displayCode = extractCode(configId, "capturedisplayname_");
        if (displayCode != null) {
            return updateConquestPoint(definition, displayCode, point -> point.withDisplayName(parseQuotedString(rawValue)));
        }
        String centerCode = extractCode(configId, "capturecenterposition_");
        if (centerCode != null) {
            return updateConquestPoint(definition, centerCode, point -> point.withCenterPosition(parsePosition(rawValue, editor)));
        }
        throw new IllegalArgumentException("不支持的动态征服配置项: " + configId);
    }

    private static RoomDefinition clearDynamicConquestPoint(RoomDefinition definition, String configId) {
        String displayCode = extractCode(configId, "capturedisplayname_");
        if (displayCode != null) {
            RoomCapturePointDefinition defaults = defaultConquestPoint(displayCode);
            return updateConquestPoint(definition, displayCode, point -> point.withDisplayName(defaults.displayName()));
        }
        String centerCode = extractCode(configId, "capturecenterposition_");
        if (centerCode != null) {
            RoomCapturePointDefinition defaults = defaultConquestPoint(centerCode);
            return updateConquestPoint(definition, centerCode, point -> point.withCenterPosition(defaults.centerPosition()));
        }
        throw new IllegalArgumentException("不支持的动态征服配置项: " + configId);
    }

    private static RoomDefinition applyDynamicBreakthroughPoint(RoomDefinition definition, String configId, String rawValue, ServerPlayer editor) {
        String displayCode = extractCode(configId, "btcapturedisplayname_");
        if (displayCode != null) {
            return updateBreakthroughPoint(definition, displayCode, point -> point.withDisplayName(parseQuotedString(rawValue)));
        }
        String centerCode = extractCode(configId, "btcapturecenterposition_");
        if (centerCode != null) {
            return updateBreakthroughPoint(definition, centerCode, point -> point.withCenterPosition(parsePosition(rawValue, editor)));
        }
        String stageCode = extractCode(configId, "btcapturestageindex_");
        if (stageCode != null) {
            return updateBreakthroughPoint(definition, stageCode, point -> point.withStageIndex(parsePositiveInt(rawValue)));
        }
        throw new IllegalArgumentException("不支持的动态突破配置项: " + configId);
    }

    private static RoomDefinition clearDynamicBreakthroughPoint(RoomDefinition definition, String configId) {
        String displayCode = extractCode(configId, "btcapturedisplayname_");
        if (displayCode != null) {
            RoomCapturePointDefinition defaults = defaultBreakthroughPoint(displayCode);
            return updateBreakthroughPoint(definition, displayCode, point -> point.withDisplayName(defaults.displayName()));
        }
        String centerCode = extractCode(configId, "btcapturecenterposition_");
        if (centerCode != null) {
            RoomCapturePointDefinition defaults = defaultBreakthroughPoint(centerCode);
            return updateBreakthroughPoint(definition, centerCode, point -> point.withCenterPosition(defaults.centerPosition()));
        }
        String stageCode = extractCode(configId, "btcapturestageindex_");
        if (stageCode != null) {
            RoomCapturePointDefinition defaults = defaultBreakthroughPoint(stageCode);
            return updateBreakthroughPoint(definition, stageCode, point -> point.withStageIndex(defaults.stageIndex()));
        }
        throw new IllegalArgumentException("不支持的动态突破配置项: " + configId);
    }

    private static RoomDefinition updateConquestPoint(
        RoomDefinition definition,
        String code,
        java.util.function.UnaryOperator<RoomCapturePointDefinition> updater
    ) {
        RoomCapturePointDefinition updated = updater.apply(resolveConquestPoint(definition, code));
        return RoomConfigBindingAccess.write(definition, "conquestConfig.captureConfig.points[" + code + "]", updated);
    }

    private static RoomDefinition updateBreakthroughPoint(
        RoomDefinition definition,
        String code,
        java.util.function.UnaryOperator<RoomCapturePointDefinition> updater
    ) {
        RoomCapturePointDefinition updated = updater.apply(resolveBreakthroughPoint(definition, code));
        return RoomConfigBindingAccess.write(definition, "breakthroughConfig.points[" + code + "]", updated);
    }

    private static RoomCapturePointDefinition resolveConquestPoint(RoomDefinition definition, String code) {
        Object current = RoomConfigBindingAccess.resolve(definition, "conquestConfig.captureConfig.points[" + code + "]");
        return current instanceof RoomCapturePointDefinition point ? point : defaultConquestPoint(code);
    }

    private static RoomCapturePointDefinition resolveBreakthroughPoint(RoomDefinition definition, String code) {
        Object current = RoomConfigBindingAccess.resolve(definition, "breakthroughConfig.points[" + code + "]");
        return current instanceof RoomCapturePointDefinition point ? point : defaultBreakthroughPoint(code);
    }

    private static RoomCapturePointDefinition defaultConquestPoint(String code) {
        return new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults());
    }

    private static RoomCapturePointDefinition defaultBreakthroughPoint(String code) {
        return new RoomCapturePointDefinition(code, code, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults(), Map.of(), 0);
    }

    private static RoomDefinition defaultDefinition(RoomDefinition definition) {
        return new RoomDefinition(definition.roomId(), definition.gameplayId(), definition.lifecycleState());
    }

    private static Object parseValue(RoomConfigDescriptor descriptor, String rawValue, ServerPlayer editor) {
        return switch (descriptor.valueType()) {
            case NUMBER -> parseNumberValue(descriptor, rawValue);
            case BOOLEAN -> parseBoolean(rawValue);
            case STRING -> parseStringValue(descriptor, rawValue);
            case POSITION -> parsePosition(rawValue, editor);
            case VIEW_POSITION -> parseViewPosition(rawValue, editor);
            case POSITION_GROUP, DATA_GROUP -> throw new IllegalArgumentException("分组配置应使用列表编辑: " + descriptor.configId());
        };
    }

    private static Object parseNumberValue(RoomConfigDescriptor descriptor, String rawValue) {
        Object boundaryValue = BoundaryConfigNumbers.parse(descriptor.configId(), rawValue);
        if (boundaryValue != null) return boundaryValue;
        String configId = normalizedConfigId(descriptor);
        if ("btbattalioncount".equals(configId) || "btbriefingseconds".equals(configId) || "btpreparationseconds".equals(configId)) {
            int value = parsePositiveInt(rawValue);
            int min = "btbriefingseconds".equals(configId) ? 8 : 1;
            int max = "btbattalioncount".equals(configId) ? 10 : "btbriefingseconds".equals(configId) ? 600 : 300;
            if (value < min || value > max) throw new IllegalArgumentException("配置范围: " + min + "-" + max);
            return value;
        }
        if ("progressspeed".equals(configId)) {
            return parsePositiveDouble(rawValue);
        }
        if ("invulnerableseconds".equals(configId) || "revivedinvulnerableseconds".equals(configId)) {
            return parseInvulnerableSeconds(rawValue);
        }
        if ("fullcaptureendseconds".equals(configId)) {
            return parseNonNegativeOrDisable(rawValue);
        }
        if ("passiveregenintervalticks".equals(configId) || requiresPositiveInt(configId)) {
            return parsePositiveInt(rawValue);
        }
        return parseNonNegativeInt(rawValue);
    }

    private static boolean requiresPositiveInt(String configId) {
        return switch (configId) {
            case "minstartplayers", "queuemaxplayers", "outsideboundarydeathseconds",
                "aircraftoutsideboundarycleanupseconds", "playermaxy", "aircraftpassengermaxy",
                "dmmatchdurationminutes", "dmtargetscore", "dmstartcountdownseconds", "dmrespawnseconds",
                "gmmatchdurationminutes", "gmtargetscore", "gmstartcountdownseconds", "gmrespawnseconds",
                "gmkillsperweaponswitch" -> true;
            default -> false;
        };
    }

    private static Object parseStringValue(RoomConfigDescriptor descriptor, String rawValue) {
        return switch (normalizedConfigId(descriptor)) {
            case "gameplayid" -> parseGameplayId(rawValue);
            case "gamemode" -> parseGameMode(rawValue);
            case "vehiclebackend" -> RoomVehicleBackend.of(rawValue);
            case "campavoicepack", "campbvoicepack" -> RoomVoicePack.normalizeOrThrow(rawValue);
            case "bttroopbalancereference" -> RoomTroopBalanceReference.of(rawValue);
            case "gmplayergroupingmode" -> RoomGunmasterGroupingMode.of(rawValue);
            case "scoremethod" -> parseScoreMethod(rawValue);
            case "initialcaptureownership" -> parseInitialCaptureOwnership(rawValue);
            default -> parseQuotedString(rawValue);
        };
    }

    private static boolean isDeleteToken(String rawValue) {
        return DELETE_TOKEN.equalsIgnoreCase(normalizeRawValue(rawValue));
    }

    private static boolean isDynamicConquestPoint(String configId) {
        return configId.startsWith("capturedisplayname_") || configId.startsWith("capturecenterposition_");
    }

    private static boolean isDynamicBreakthroughPoint(String configId) {
        return configId.startsWith("btcapturedisplayname_")
            || configId.startsWith("btcapturecenterposition_")
            || configId.startsWith("btcapturestageindex_");
    }

    private static String extractCode(String configId, String prefix) {
        if (!configId.startsWith(prefix)) {
            return null;
        }
        return configId.substring(prefix.length()).toUpperCase(Locale.ROOT);
    }

    private static String normalizedConfigId(RoomConfigDescriptor descriptor) {
        return descriptor.configId().trim().toLowerCase(Locale.ROOT);
    }

    private static RoomMatchingConfig splitQueueMaxPlayers(RoomMatchingConfig current, int totalPlayers) {
        Set<RoomTeamId> enabledTeams = current.enabledTeams();
        int campA = 0;
        int campB = 0;
        if (enabledTeams.contains(RoomTeamId.CAMP_A) && enabledTeams.contains(RoomTeamId.CAMP_B)) {
            campA = Math.max(1, (totalPlayers + 1) / 2);
            campB = Math.max(1, totalPlayers / 2);
        } else if (enabledTeams.contains(RoomTeamId.CAMP_A)) {
            campA = Math.max(1, totalPlayers);
        } else if (enabledTeams.contains(RoomTeamId.CAMP_B)) {
            campB = Math.max(1, totalPlayers);
        }
        return new RoomMatchingConfig(
            current.minStartPlayers(),
            campA,
            campB,
            current.waitAfterMinSeconds(),
            current.waitAfterMaxSeconds(),
            enabledTeams
        );
    }

    private static RoomGameplayId parseGameplayId(String rawValue) {
        String normalized = normalizeRawValue(rawValue).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("玩法不能为空");
        }
        return switch (normalized) {
            case "conquest" -> RoomGameplayId.CONQUEST;
            case "breakthrough" -> RoomGameplayId.BREAKTHROUGH;
            case "deathmatch" -> RoomGameplayId.DEATHMATCH;
            case "gunmaster" -> RoomGameplayId.GUNMASTER;
            default -> throw new IllegalArgumentException("当前仅支持 CONQUEST / BREAKTHROUGH / DEATHMATCH / GUNMASTER");
        };
    }

    private static String parseGameMode(String rawValue) {
        String normalized = normalizeRawValue(rawValue).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "survival", "adventure", "creative", "spectator" -> normalized;
            default -> throw new IllegalArgumentException("无效游戏模式: " + rawValue);
        };
    }

    private static boolean parseBoolean(String rawValue) {
        String normalized = normalizeRawValue(rawValue).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "true", "yes", "y" -> true;
            case "0", "false", "no", "n" -> false;
            default -> throw new IllegalArgumentException("布尔值格式错误: " + rawValue);
        };
    }

    private static int parsePositiveInt(String rawValue) {
        int parsed = parseInt(rawValue);
        if (parsed <= 0) {
            throw new IllegalArgumentException("必须大于 0: " + rawValue);
        }
        return parsed;
    }

    private static int parseNonNegativeInt(String rawValue) {
        int parsed = parseInt(rawValue);
        if (parsed < 0) {
            throw new IllegalArgumentException("不能小于 0: " + rawValue);
        }
        return parsed;
    }

    private static int parseNonNegativeOrDisable(String rawValue) {
        int parsed = parseInt(rawValue);
        if (parsed < -1) {
            throw new IllegalArgumentException("只能为 -1 或非负数: " + rawValue);
        }
        return parsed;
    }

    private static int parseInt(String rawValue) {
        try {
            return Integer.parseInt(normalizeRawValue(rawValue));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数字格式错误: " + rawValue, exception);
        }
    }

    private static double parsePositiveDouble(String rawValue) {
        try {
            double parsed = Double.parseDouble(normalizeRawValue(rawValue));
            if (!Double.isFinite(parsed) || parsed <= 0.0D) {
                throw new IllegalArgumentException("必须大于 0: " + rawValue);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数字格式错误: " + rawValue, exception);
        }
    }

    private static double parseInvulnerableSeconds(String rawValue) {
        try {
            double parsed = Double.parseDouble(normalizeRawValue(rawValue));
            if (!Double.isFinite(parsed) || parsed < -1.0D) {
                throw new IllegalArgumentException("只能为 -1 或大于等于 0 的数字: " + rawValue);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数字格式错误: " + rawValue, exception);
        }
    }

    private static String parseScoreMethod(String rawValue) {
        String normalized = normalizeRawValue(rawValue).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "使分数达标", "TARGET", "TARGET_SCORE", "SCORE", "SCORE_TARGET" -> "SCORE_TARGET";
            case "使兵力耗尽", "TROOPS", "TROOP", "TROOP_DEPLETION", "DEPLETION" -> "TROOP_DEPLETION";
            default -> throw new IllegalArgumentException("无效 scoreMethod: " + rawValue);
        };
    }

    private static String parseInitialCaptureOwnership(String rawValue) {
        String normalized = normalizeRawValue(rawValue).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "空", "EMPTY", "NONE", "NEUTRAL" -> "EMPTY";
            case "CAMP_A", "阵营A" -> "CAMP_A";
            case "CAMP_B", "阵营B" -> "CAMP_B";
            default -> throw new IllegalArgumentException("无效 initialCaptureOwnership: " + rawValue);
        };
    }

    private static RoomPosition parsePosition(String rawValue, ServerPlayer editor) {
        String value = normalizeRawValue(rawValue);
        if (value.isBlank() || "null".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value) || "-".equals(value)) {
            return null;
        }
        if ("here".equalsIgnoreCase(value)) {
            if (editor == null) {
                throw new IllegalArgumentException("无法解析 here 坐标");
            }
            return new RoomPosition(editor.getBlockX(), editor.getY(), editor.getBlockZ());
        }
        if (value.startsWith("{")) {
            return GSON.fromJson(value, RoomPosition.class);
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

    private static RoomViewPosition parseViewPosition(String rawValue, ServerPlayer editor) {
        String value = normalizeRawValue(rawValue);
        if (value.isBlank() || "null".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value) || "-".equals(value)) {
            return null;
        }
        if ("here".equalsIgnoreCase(value)) {
            if (editor == null) {
                throw new IllegalArgumentException("无法解析 here 朝向坐标");
            }
            return new RoomViewPosition(editor.getBlockX(), editor.getY(), editor.getBlockZ(), editor.getYRot(), editor.getXRot());
        }
        if (value.startsWith("{")) {
            return GSON.fromJson(value, RoomViewPosition.class);
        }
        String[] split = value.split(",");
        if (split.length != 5) {
            throw new IllegalArgumentException("朝向坐标格式错误，应为 x,y,z,yaw,pitch 或 here");
        }
        try {
            int x = Integer.parseInt(split[0].trim());
            double y = Double.parseDouble(split[1].trim());
            int z = Integer.parseInt(split[2].trim());
            float yaw = Float.parseFloat(split[3].trim());
            float pitch = Float.parseFloat(split[4].trim());
            return new RoomViewPosition(x, y, z, yaw, pitch);
        } catch (Exception exception) {
            throw new IllegalArgumentException("朝向坐标格式错误，应为 x,y,z,yaw,pitch 或 here", exception);
        }
    }

    private static String parseQuotedString(String rawValue) {
        String normalized = normalizeRawValue(rawValue);
        if (normalized.length() >= 2) {
            boolean asciiQuoted = normalized.startsWith("\"") && normalized.endsWith("\"");
            boolean cnQuoted = normalized.startsWith("“") && normalized.endsWith("”");
            if (asciiQuoted || cnQuoted) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
        }
        return normalized
            .replace("\\\"", "\"")
            .replace("\\“", "“")
            .replace("\\”", "”");
    }

    private static String normalizeRawValue(String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
    }
}
