package org.mods.gd656conquest.server.room.core;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepository;
import org.mods.gd656conquest.server.room.config.RoomConfigDescriptor;
import org.mods.gd656conquest.server.room.config.RoomConfigDescriptorRegistry;
import org.mods.gd656conquest.server.room.config.RoomConfigValueResolver;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomResultConfig;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeProvider;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.runtime.RoomHudPayloadCodec;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RoomAdminDebugOverlayService {
    private static final int MAX_PAYLOAD_BYTES = 32767;
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomDefinitionRepository roomDefinitionRepository;

    public RoomAdminDebugOverlayService(
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomDefinitionRepository roomDefinitionRepository
    ) {
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.roomDefinitionRepository = Objects.requireNonNull(roomDefinitionRepository, "roomDefinitionRepository");
    }

    public Optional<String> buildPayloadForPlayer(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        String dimensionId = player.level().dimension().location().toString();
        if (dimensionId == null || dimensionId.isBlank()) {
            return Optional.empty();
        }
        return runtimeRepository.list().stream()
            .filter(slot -> dimensionId.equalsIgnoreCase(slot.room().commonConfig().dimensionId()))
            .findFirst()
            .map(slot -> buildPayload(
                roomDefinitionRepository.findByDimensionId(dimensionId).orElse(slot.room()),
                slot.runtime()
            ));
    }

    public String buildPayload(RoomDefinition room, RoomRuntimeState runtimeState) {
        if (room == null || runtimeState == null) {
            return "-";
        }
        String roomId = room.roomId();
        if (roomId == null || roomId.isBlank()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        RoomObjectiveRuntimeView captureState = resolveCaptureState(runtimeState);
        // GD656Mods: 战斗区域边界(区域/阶段边界、公共边界、campA/B大本营边界、campA/B活动范围边界)
        // 是最重要的信息, 必须最先 append —— payload 超长截断(MAX_PAYLOAD_BYTES=32767)发生在字符串
        // 末尾, 若边界点排在最后, 标记点一多就会被截掉导致"渐渐不显示边界"。
        appendBoundaryPoints(builder, room);
        appendCaptureCenters(builder, room, captureState);
        appendConfiguredCoordinateMarkers(builder, roomId, room);
        String payload = builder.length() == 0 ? "-" : builder.toString();
        if (!"-".equals(payload) && payload.length() > MAX_PAYLOAD_BYTES) {
            int lastValidSemi = payload.lastIndexOf(';', MAX_PAYLOAD_BYTES);
            if (lastValidSemi > 0) {
                payload = payload.substring(0, lastValidSemi);
            } else {
                payload = payload.substring(0, MAX_PAYLOAD_BYTES);
            }
        }
        return payload;
    }

    private RoomObjectiveRuntimeView resolveCaptureState(RoomRuntimeState runtimeState) {
        if (runtimeState == null) {
            return null;
        }
        IRoomGameplayRuntime runtime = runtimeState.getRunningState().getGameplayRuntime().orElse(null);
        if (runtime instanceof RoomObjectiveRuntimeProvider objectiveRuntimeProvider) {
            return objectiveRuntimeProvider.objectiveRuntimeView();
        }
        return null;
    }

    private void appendConfiguredCoordinateMarkers(StringBuilder builder, String roomId, RoomDefinition room) {
        if (builder == null || roomId == null || roomId.isBlank() || room == null) {
            return;
        }
        Map<String, RoomConfigDescriptor> descriptorsByPath = new LinkedHashMap<>();
        collectDescriptors(descriptorsByPath, RoomConfigDescriptorRegistry.legacyCommonCommandIds());
        collectDescriptors(descriptorsByPath, gameplayDescriptorIds(room.gameplayId()));
        for (RoomConfigDescriptor descriptor : descriptorsByPath.values()) {
            if (!supportsCoordinateOverlay(descriptor) || shouldSkipAutoOverlay(descriptor)) {
                continue;
            }
            Object resolved;
            try {
                resolved = RoomConfigValueResolver.resolve(room, descriptor);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            appendResolvedCoordinateValue(builder, roomId, descriptor, resolved);
        }
    }

    private void collectDescriptors(Map<String, RoomConfigDescriptor> descriptorsByPath, List<String> descriptorIds) {
        if (descriptorsByPath == null || descriptorIds == null || descriptorIds.isEmpty()) {
            return;
        }
        for (String descriptorId : descriptorIds) {
            RoomConfigDescriptorRegistry.find(descriptorId)
                .ifPresent(descriptor -> descriptorsByPath.putIfAbsent(descriptor.newPath(), descriptor));
        }
    }

    private List<String> gameplayDescriptorIds(RoomGameplayId gameplayId) {
        if (RoomGameplayId.BREAKTHROUGH.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyBreakthroughCommandIds();
        }
        if (RoomGameplayId.RUSH.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyRushCommandIds();
        }
        if (RoomGameplayId.DEATHMATCH.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyDeathmatchCommandIds();
        }
        if (RoomGameplayId.GUNMASTER.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyGunmasterCommandIds();
        }
        return RoomConfigDescriptorRegistry.legacyConquestCommandIds();
    }

    private boolean supportsCoordinateOverlay(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        return switch (descriptor.valueType()) {
            case POSITION, VIEW_POSITION, POSITION_GROUP, DATA_GROUP -> true;
            default -> false;
        };
    }

    private boolean shouldSkipAutoOverlay(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return true;
        }
        String path = descriptor.newPath().toLowerCase(Locale.ROOT);
        if (path.contains("boundary")) {
            return true;
        }
        if (path.contains("captureconfig.points[") && path.endsWith("].centerposition")) {
            return true;
        }
        if (path.contains("breakthroughconfig.points[") && path.endsWith("].centerposition")) {
            return true;
        }
        return false;
    }

    private void appendResolvedCoordinateValue(StringBuilder builder, String roomId, RoomConfigDescriptor descriptor, Object resolved) {
        if (resolved == null || descriptor == null) {
            return;
        }
        if (resolved instanceof RoomPosition position) {
            appendDebugPoint(
                builder,
                "D",
                autoCode(roomId, descriptor.configId(), 0),
                position.x(),
                position.y(),
                position.z(),
                debugTeamTokenForDescriptor(descriptor),
                "N",
                100.0F,
                descriptor.displayName()
            );
            return;
        }
        if (resolved instanceof RoomViewPosition viewPosition) {
            appendDebugPoint(
                builder,
                "D",
                autoCode(roomId, descriptor.configId(), 0),
                viewPosition.x(),
                viewPosition.y(),
                viewPosition.z(),
                debugTeamTokenForDescriptor(descriptor),
                "N",
                100.0F,
                descriptor.displayName() + String.format(Locale.ROOT, " yaw=%.1f pitch=%.1f", viewPosition.yaw(), viewPosition.pitch())
            );
            return;
        }
        if (resolved instanceof RoomSpawnGroupConfig spawnGroupConfig) {
            appendSpawnGroupMarkers(builder, roomId, descriptor, spawnGroupConfig);
            return;
        }
        if (resolved instanceof List<?> list) {
            appendPositionListMarkers(builder, roomId, descriptor, list);
        }
    }

    private void appendSpawnGroupMarkers(StringBuilder builder, String roomId, RoomConfigDescriptor descriptor, RoomSpawnGroupConfig spawnGroupConfig) {
        if (spawnGroupConfig == null || !spawnGroupConfig.enabled()) {
            return;
        }
        appendPositionListMarkers(builder, roomId, descriptor, spawnGroupConfig.spawns());
    }

    private void appendPositionListMarkers(StringBuilder builder, String roomId, RoomConfigDescriptor descriptor, List<?> values) {
        if (values == null || values.isEmpty() || descriptor == null) {
            return;
        }
        int index = 1;
        for (Object value : values) {
            if (!(value instanceof RoomPosition position)) {
                continue;
            }
            appendDebugPoint(
                builder,
                "D",
                autoCode(roomId, descriptor.configId(), index),
                position.x(),
                position.y(),
                position.z(),
                debugTeamTokenForDescriptor(descriptor),
                "N",
                100.0F,
                descriptor.displayName() + "#" + index
            );
            index++;
        }
    }

    private String debugTeamTokenForDescriptor(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return "N";
        }
        String key = (descriptor.configId() + "|" + descriptor.newPath()).toLowerCase(Locale.ROOT);
        if (key.contains("campa") || key.contains("[camp_a]")) {
            return "T";
        }
        if (key.contains("campb") || key.contains("[camp_b]")) {
            return "S";
        }
        return "N";
    }

    private String autoCode(String roomId, String configId, int index) {
        String normalizedRoomId = roomId == null ? "ROOM" : roomId.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        String normalizedConfigId = configId == null ? "CONFIG" : configId.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return index <= 0
            ? "CFG_" + normalizedRoomId + "_" + normalizedConfigId
            : "CFG_" + normalizedRoomId + "_" + normalizedConfigId + "_" + index;
    }

    private void appendCaptureCenters(StringBuilder builder, RoomDefinition room, RoomObjectiveRuntimeView captureState) {
        if (room == null || room.configuredObjectivePoints().isEmpty()) {
            return;
        }
        for (RoomCapturePointDefinition point : room.configuredObjectivePoints()) {
            String captureCode = point.code();
            String ownerToken = toDebugTeamToken(captureState == null ? null : captureState.ownerOf(captureCode));
            String drivingToken = toDebugTeamToken(captureState == null ? null : captureState.drivingTeamOf(captureCode));
            float progress = captureState == null
                ? 50.0F
                : (float) Math.max(0.0D, Math.min(100.0D, captureState.progressOf(captureCode)));
            appendDebugPoint(
                builder,
                "C",
                captureCode,
                point.centerPosition().x(),
                point.centerPosition().y(),
                point.centerPosition().z(),
                ownerToken,
                drivingToken,
                progress,
                "据点 " + captureCode
            );
        }
    }

    private void appendTeamSpawnPoints(StringBuilder builder, String roomId, RoomSpawnGroupConfig group, String teamToken, String codePrefix, String labelPrefix) {
        if (group == null || !group.enabled()) {
            return;
        }
        int index = 1;
        for (RoomPosition spawn : group.spawns()) {
            if (spawn == null) {
                continue;
            }
            appendDebugPoint(builder, "D", codePrefix + "_" + roomId + "_" + index, spawn.x(), spawn.y(), spawn.z(), teamToken, "N", 100.0F, labelPrefix + "#" + index);
            index++;
        }
    }

    private void appendCaptureSpawnGroups(
        StringBuilder builder,
        String roomId,
        List<RoomCapturePointDefinition> points,
        RoomObjectiveRuntimeView captureState,
        boolean inner,
        RoomTeamId outerTeamId
    ) {
        if (points == null || points.isEmpty()) {
            return;
        }
        for (RoomCapturePointDefinition point : points) {
            if (point == null) {
                continue;
            }
            List<RoomPosition> spawns = inner
                ? point.innerSpawnGroup().spawns()
                : point.outerSpawnGroup(outerTeamId).spawns();
            if (spawns.isEmpty()) {
                continue;
            }
            String captureCode = point.code();
            String ownerToken = toDebugTeamToken(captureState == null ? null : captureState.ownerOf(captureCode));
            String codePrefix = inner ? "IS" : outerTeamId == RoomTeamId.CAMP_A ? "OSA" : outerTeamId == RoomTeamId.CAMP_B ? "OSB" : "OS";
            String labelPrefix = inner
                ? "据点内重生 "
                : outerTeamId == RoomTeamId.CAMP_A ? "据点外进攻方部署 " : outerTeamId == RoomTeamId.CAMP_B ? "据点外防守方部署 " : "据点外重生 ";
            int index = 1;
            for (RoomPosition spawn : spawns) {
                if (spawn == null) {
                    continue;
                }
                appendDebugPoint(
                    builder,
                    "D",
                    codePrefix + "_" + roomId + "_" + captureCode + "_" + index,
                    spawn.x(),
                    spawn.y(),
                    spawn.z(),
                    ownerToken,
                    "N",
                    100.0F,
                    labelPrefix + captureCode + "#" + index
                );
                index++;
            }
        }
    }

    private void appendBreakthroughViewPositions(StringBuilder builder, String roomId, RoomDefinition room) {
        if (room == null) {
            return;
        }
        for (int stageIndex = 1; stageIndex <= 6; stageIndex++) {
            RoomViewPosition position = room.breakthroughConfig().stageDeployViewPosition(stageIndex);
            if (position == null) {
                continue;
            }
            appendViewPosition(builder, roomId, "BV" + stageIndex, "N", position, "阶段" + stageLabel(stageIndex) + "部署视角");
        }
    }

    private void appendRushViewPositions(StringBuilder builder, String roomId, RoomDefinition room) {
        if (room == null) {
            return;
        }
        for (int stageIndex = 1; stageIndex <= room.rushConfig().stageCount(); stageIndex++) {
            RoomViewPosition position = room.rushConfig().stageDeployViewPosition(stageIndex);
            if (position == null) {
                continue;
            }
            appendViewPosition(builder, roomId, "RV" + stageIndex, "N", position, "阶段" + stageLabel(stageIndex) + "部署视角");
        }
    }

    private void appendRushBombPositions(StringBuilder builder, String roomId, RoomDefinition room) {
        if (room == null || room.rushConfig().bombPositions().isEmpty()) {
            return;
        }
        room.rushConfig().bombPositions().entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .filter(entry -> entry.getValue() != null)
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> appendDebugPoint(
                builder,
                "D",
                "RSB_" + roomId + "_" + entry.getKey().trim().toUpperCase(Locale.ROOT),
                entry.getValue().x(),
                entry.getValue().y(),
                entry.getValue().z(),
                "N",
                "N",
                100.0F,
                "突袭炸弹 " + entry.getKey().trim().toUpperCase(Locale.ROOT)
            ));
    }

    private void appendNamedPosition(StringBuilder builder, String roomId, String codePrefix, String teamToken, RoomPosition position, String label) {
        if (position == null) {
            return;
        }
        appendDebugPoint(builder, "D", codePrefix + "_" + roomId, position.x(), position.y(), position.z(), teamToken, "N", 100.0F, label);
    }

    private void appendViewPosition(
        StringBuilder builder,
        String roomId,
        String codePrefix,
        String teamToken,
        RoomViewPosition position,
        String title
    ) {
        if (position == null) {
            return;
        }
        appendDebugPoint(
            builder,
            "D",
            codePrefix + "_" + roomId,
            position.x(),
            position.y(),
            position.z(),
            teamToken,
            "N",
            100.0F,
            title + String.format(Locale.ROOT, " yaw=%.1f pitch=%.1f", position.yaw(), position.pitch())
        );
    }

    private void appendEndViewPosition(StringBuilder builder, String roomId, RoomResultConfig resultConfig) {
        if (resultConfig == null) {
            return;
        }
        appendViewPosition(builder, roomId, "EP", "N", resultConfig.endViewPosition(), "结束展示位");
    }

    private void appendBoundaryPoints(StringBuilder builder, RoomDefinition room) {
        if (room == null) {
            return;
        }
        if (org.mods.gd656conquest.server.room.model.RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            appendBoundaryRows(builder, room.breakthroughConfig().campABaseBoundary(), "T", "bt_debug_camp_a_base");
            appendBoundaryRows(builder, room.breakthroughConfig().campBBaseBoundary(), "S", "bt_debug_camp_b_base");
            for (int stageIndex = 1; stageIndex <= 6; stageIndex++) {
                appendBoundaryRows(builder, room.breakthroughConfig().stageBoundary(stageIndex), "N", "bt_debug_stage_" + stageIndex);
            }
            return;
        }
        if (org.mods.gd656conquest.server.room.model.RoomGameplayId.RUSH.equals(room.gameplayId())) {
            appendBoundaryRows(builder, room.rushConfig().campABaseBoundary(), "T", "rs_debug_camp_a_base");
            appendBoundaryRows(builder, room.rushConfig().campBBaseBoundary(), "S", "rs_debug_camp_b_base");
            for (int stageIndex = 1; stageIndex <= room.rushConfig().stageCount(); stageIndex++) {
                appendBoundaryRows(builder, room.rushConfig().stageBoundary(stageIndex), "N", "rs_debug_stage_" + stageIndex);
            }
            return;
        }
        if (room.isArenaGameplay()) {
            appendBoundaryRows(builder, room.arenaConfig().campABoundary(), "T", "arena_debug_camp_a");
            appendBoundaryRows(builder, room.arenaConfig().campBBoundary(), "S", "arena_debug_camp_b");
            return;
        }
        appendBoundaryRows(builder, room.conquestConfig().sharedBoundary(), "N", "cq_debug_shared");
        appendBoundaryRows(builder, room.conquestConfig().campABoundary(), "T", "cq_debug_camp_a");
        appendBoundaryRows(builder, room.conquestConfig().campBBoundary(), "S", "cq_debug_camp_b");
    }

    private void appendBoundaryRows(StringBuilder builder, List<RoomPosition> positions, String teamToken, String polygonId) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        for (RoomPosition position : positions) {
            if (position == null) {
                continue;
            }
            appendDebugPoint(builder, "B", teamToken, position.x(), position.y(), position.z(), "N", "N", 0.0F, polygonId);
        }
    }

    private String toDebugTeamToken(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "T";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "S";
        }
        return "N";
    }

    private String stageLabel(int stageIndex) {
        return switch (stageIndex) {
            case 1 -> "A";
            case 2 -> "B";
            case 3 -> "C";
            case 4 -> "D";
            case 5 -> "E";
            case 6 -> "F";
            default -> String.valueOf(stageIndex);
        };
    }

    private void appendDebugPoint(StringBuilder builder, String kind, String code, double x, double y, double z, String owner, String driving, float progress, String label) {
        if (builder.length() > 0) {
            builder.append(';');
        }
        builder.append(kind).append(',')
            .append(code).append(',')
            .append((int) Math.round(x)).append(',')
            .append((int) Math.round(y)).append(',')
            .append((int) Math.round(z)).append(',')
            .append(owner).append(',')
            .append(driving).append(',')
            .append(progress).append(',')
            .append(RoomHudPayloadCodec.encodeText(label));
    }
}
