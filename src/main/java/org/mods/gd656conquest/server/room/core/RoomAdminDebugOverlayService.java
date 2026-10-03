package org.mods.gd656conquest.server.room.core;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.overlay.AdminDebugOverlayRowTable;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * GD656Mods: debug overlay 点表构建。
 *
 * <p>产出的是<strong>行表</strong>({@code 键 -> 旧格式点文本})而不是拼好的整串 payload:
 * <ul>
 *     <li>键用于增量协议定位(新增/变更/删除), 与客户端 {@code ClientAdminDebugOverlayStore} 一致;</li>
 *     <li>值是该键对应的一个或多个 {@code ;} 分隔的点文本, 与原 payload 单行格式完全相同,
 *         因此客户端拼串后下游解析器无需改动;</li>
 *     <li>边界点以"整条多边形"(键含队别与多边形 id)为键, 顶点顺序由值内部顺序保持。</li>
 * </ul>
 *
 * <p>旧实现把整张表拼成一个字符串并在单包内发送, 超长时静默截断丢点; 现在由
 * {@code RoomAdminDebugOverlaySyncService} 负责分片/增量传输, 本类只管"权威点集是什么"。
 */
public final class RoomAdminDebugOverlayService {
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomDefinitionRepository roomDefinitionRepository;

    public RoomAdminDebugOverlayService(
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomDefinitionRepository roomDefinitionRepository
    ) {
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.roomDefinitionRepository = Objects.requireNonNull(roomDefinitionRepository, "roomDefinitionRepository");
    }

    /**
     * GD656Mods: 返回该玩家当前维度的调试覆盖层行表; 玩家所在维度没有房间时返回 empty。
     * 每次调用都从房间定义与运行时重新构建, 因此房间配置更新后下一次同步自然带上新点集。
     */
    public Optional<Map<String, String>> buildRowTableForPlayer(ServerPlayer player) {
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
            .map(slot -> buildRowTable(
                roomDefinitionRepository.findByDimensionId(dimensionId).orElse(slot.room()),
                slot.runtime()
            ));
    }

    public Map<String, String> buildRowTable(RoomDefinition room, RoomRuntimeState runtimeState) {
        Map<String, String> table = new LinkedHashMap<>();
        if (room == null || runtimeState == null) {
            return table;
        }
        String roomId = room.roomId();
        if (roomId == null || roomId.isBlank()) {
            return table;
        }
        RoomObjectiveRuntimeView captureState = resolveCaptureState(runtimeState);
        // GD656Mods: 构建顺序即客户端行表顺序(边界 → 据点中心 → 配置坐标), 与原实现保持一致。
        appendBoundaryPoints(table, room);
        appendCaptureCenters(table, room, captureState);
        appendConfiguredCoordinateMarkers(table, roomId, room);
        return table;
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

    private void appendConfiguredCoordinateMarkers(Map<String, String> table, String roomId, RoomDefinition room) {
        if (table == null || roomId == null || roomId.isBlank() || room == null) {
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
            appendResolvedCoordinateValue(table, roomId, descriptor, resolved);
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

    private void appendResolvedCoordinateValue(Map<String, String> table, String roomId, RoomConfigDescriptor descriptor, Object resolved) {
        if (resolved == null || descriptor == null) {
            return;
        }
        if (resolved instanceof RoomPosition position) {
            appendDebugPoint(
                table,
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
                table,
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
            appendSpawnGroupMarkers(table, roomId, descriptor, spawnGroupConfig);
            return;
        }
        if (resolved instanceof List<?> list) {
            appendPositionListMarkers(table, roomId, descriptor, list);
        }
    }

    private void appendSpawnGroupMarkers(Map<String, String> table, String roomId, RoomConfigDescriptor descriptor, RoomSpawnGroupConfig spawnGroupConfig) {
        if (spawnGroupConfig == null || !spawnGroupConfig.enabled()) {
            return;
        }
        appendPositionListMarkers(table, roomId, descriptor, spawnGroupConfig.spawns());
    }

    private void appendPositionListMarkers(Map<String, String> table, String roomId, RoomConfigDescriptor descriptor, List<?> values) {
        if (values == null || values.isEmpty() || descriptor == null) {
            return;
        }
        int index = 1;
        for (Object value : values) {
            if (!(value instanceof RoomPosition position)) {
                continue;
            }
            appendDebugPoint(
                table,
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

    private void appendCaptureCenters(Map<String, String> table, RoomDefinition room, RoomObjectiveRuntimeView captureState) {
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
                table,
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

    private void appendTeamSpawnPoints(Map<String, String> table, String roomId, RoomSpawnGroupConfig group, String teamToken, String codePrefix, String labelPrefix) {
        if (group == null || !group.enabled()) {
            return;
        }
        int index = 1;
        for (RoomPosition spawn : group.spawns()) {
            if (spawn == null) {
                continue;
            }
            appendDebugPoint(table, "D", codePrefix + "_" + roomId + "_" + index, spawn.x(), spawn.y(), spawn.z(), teamToken, "N", 100.0F, labelPrefix + "#" + index);
            index++;
        }
    }

    private void appendCaptureSpawnGroups(
        Map<String, String> table,
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
                    table,
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

    private void appendBreakthroughViewPositions(Map<String, String> table, String roomId, RoomDefinition room) {
        if (room == null) {
            return;
        }
        for (int stageIndex = 1; stageIndex <= 6; stageIndex++) {
            RoomViewPosition position = room.breakthroughConfig().stageDeployViewPosition(stageIndex);
            if (position == null) {
                continue;
            }
            appendViewPosition(table, roomId, "BV" + stageIndex, "N", position, "阶段" + stageLabel(stageIndex) + "部署视角");
        }
    }

    private void appendRushViewPositions(Map<String, String> table, String roomId, RoomDefinition room) {
        if (room == null) {
            return;
        }
        for (int stageIndex = 1; stageIndex <= room.rushConfig().stageCount(); stageIndex++) {
            RoomViewPosition position = room.rushConfig().stageDeployViewPosition(stageIndex);
            if (position == null) {
                continue;
            }
            appendViewPosition(table, roomId, "RV" + stageIndex, "N", position, "阶段" + stageLabel(stageIndex) + "部署视角");
        }
    }

    private void appendRushBombPositions(Map<String, String> table, String roomId, RoomDefinition room) {
        if (room == null || room.rushConfig().bombPositions().isEmpty()) {
            return;
        }
        room.rushConfig().bombPositions().entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .filter(entry -> entry.getValue() != null)
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> appendDebugPoint(
                table,
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

    private void appendNamedPosition(Map<String, String> table, String roomId, String codePrefix, String teamToken, RoomPosition position, String label) {
        if (position == null) {
            return;
        }
        appendDebugPoint(table, "D", codePrefix + "_" + roomId, position.x(), position.y(), position.z(), teamToken, "N", 100.0F, label);
    }

    private void appendViewPosition(
        Map<String, String> table,
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
            table,
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

    private void appendEndViewPosition(Map<String, String> table, String roomId, RoomResultConfig resultConfig) {
        if (resultConfig == null) {
            return;
        }
        appendViewPosition(table, roomId, "EP", "N", resultConfig.endViewPosition(), "结束展示位");
    }

    private void appendBoundaryPoints(Map<String, String> table, RoomDefinition room) {
        if (room == null) {
            return;
        }
        if (org.mods.gd656conquest.server.room.model.RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            appendBoundaryRows(table, room.breakthroughConfig().campABaseBoundary(), "T", "bt_debug_camp_a_base");
            appendBoundaryRows(table, room.breakthroughConfig().campBBaseBoundary(), "S", "bt_debug_camp_b_base");
            for (int stageIndex = 1; stageIndex <= 6; stageIndex++) {
                appendBoundaryRows(table, room.breakthroughConfig().stageBoundary(stageIndex), "N", "bt_debug_stage_" + stageIndex);
            }
            return;
        }
        if (org.mods.gd656conquest.server.room.model.RoomGameplayId.RUSH.equals(room.gameplayId())) {
            appendBoundaryRows(table, room.rushConfig().campABaseBoundary(), "T", "rs_debug_camp_a_base");
            appendBoundaryRows(table, room.rushConfig().campBBaseBoundary(), "S", "rs_debug_camp_b_base");
            for (int stageIndex = 1; stageIndex <= room.rushConfig().stageCount(); stageIndex++) {
                appendBoundaryRows(table, room.rushConfig().stageBoundary(stageIndex), "N", "rs_debug_stage_" + stageIndex);
            }
            return;
        }
        if (room.isArenaGameplay()) {
            appendBoundaryRows(table, room.arenaConfig().campABoundary(), "T", "arena_debug_camp_a");
            appendBoundaryRows(table, room.arenaConfig().campBBoundary(), "S", "arena_debug_camp_b");
            return;
        }
        appendBoundaryRows(table, room.conquestConfig().sharedBoundary(), "N", "cq_debug_shared");
        appendBoundaryRows(table, room.conquestConfig().campABoundary(), "T", "cq_debug_camp_a");
        appendBoundaryRows(table, room.conquestConfig().campBBoundary(), "S", "cq_debug_camp_b");
    }

    private void appendBoundaryRows(Map<String, String> table, List<RoomPosition> positions, String teamToken, String polygonId) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        StringBuilder polygon = new StringBuilder();
        for (RoomPosition position : positions) {
            if (position == null) {
                continue;
            }
            if (polygon.length() > 0) {
                polygon.append(AdminDebugOverlayRowTable.ROW_SEPARATOR);
            }
            polygon.append(buildDebugPointRow("B", teamToken, position.x(), position.y(), position.z(), "N", "N", 0.0F, polygonId));
        }
        if (polygon.length() == 0) {
            return;
        }
        // GD656Mods: 边界以"整条多边形"为键 —— 顶点顺序由值内部顺序保持, 顶点数增减
        // 也能通过一次 upsert 正确表达(逐点 diff 无法表达顺序与数量的变化)。
        AdminDebugOverlayRowTable.putRow(table, "B|" + teamToken + "|" + polygonId, polygon.toString());
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

    private void appendDebugPoint(Map<String, String> table, String kind, String code, double x, double y, double z, String owner, String driving, float progress, String label) {
        AdminDebugOverlayRowTable.putRow(table, kind + "|" + code, buildDebugPointRow(kind, code, x, y, z, owner, driving, progress, label));
    }

    private String buildDebugPointRow(String kind, String code, double x, double y, double z, String owner, String driving, float progress, String label) {
        return kind + ','
            + code + ','
            + (int) Math.round(x) + ','
            + (int) Math.round(y) + ','
            + (int) Math.round(z) + ','
            + owner + ','
            + driving + ','
            + progress + ','
            + RoomHudPayloadCodec.encodeText(label);
    }
}
