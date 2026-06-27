package org.mods.gd656conquest.server.room.core;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepository;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        appendCaptureCenters(builder, room, captureState);
        appendNamedPosition(builder, roomId, "QS", "N", room.commonConfig().queueSpawn(), "匹配队列重生点");
        appendEndViewPosition(builder, roomId, room.commonConfig().resultConfig());
        if (org.mods.gd656conquest.server.room.model.RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            appendTeamSpawnPoints(builder, roomId, room.breakthroughConfig().campABaseSpawnGroup(), "T", "BTA", "进攻方大本营重生");
            appendTeamSpawnPoints(builder, roomId, room.breakthroughConfig().campBBaseSpawnGroup(), "S", "BTB", "防守方大本营重生");
            appendBreakthroughViewPositions(builder, roomId, room);
            appendCaptureSpawnGroups(builder, roomId, room.configuredObjectivePoints(), captureState, true, null);
            appendCaptureSpawnGroups(builder, roomId, room.configuredObjectivePoints(), captureState, false, RoomTeamId.CAMP_A);
            appendCaptureSpawnGroups(builder, roomId, room.configuredObjectivePoints(), captureState, false, RoomTeamId.CAMP_B);
        } else {
            appendTeamSpawnPoints(builder, roomId, room.conquestConfig().teamSpawnGroups().get(RoomTeamId.CAMP_A), "T", "TS", "阵营A重生");
            appendTeamSpawnPoints(builder, roomId, room.conquestConfig().teamSpawnGroups().get(RoomTeamId.CAMP_B), "S", "SS", "阵营B重生");
            appendNamedPosition(builder, roomId, "AT", "T", room.conquestConfig().teamDeployIcons().get(RoomTeamId.CAMP_A), "阵营A图标点");
            appendNamedPosition(builder, roomId, "BT", "S", room.conquestConfig().teamDeployIcons().get(RoomTeamId.CAMP_B), "阵营B图标点");
            appendViewPosition(builder, roomId, "DP", "N", room.conquestConfig().deployViewPosition(), "匹配展示位");
            appendCaptureSpawnGroups(builder, roomId, room.configuredObjectivePoints(), captureState, true, null);
            appendCaptureSpawnGroups(builder, roomId, room.configuredObjectivePoints(), captureState, false, null);
        }
        appendBoundaryPoints(builder, room);
        return builder.length() == 0 ? "-" : builder.toString();
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
