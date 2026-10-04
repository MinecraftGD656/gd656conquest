package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record RoomDeployHudElement(
    RoomDeployHudElementKind kind,
    String code,
    double x,
    double y,
    double z,
    String ownerToken,
    String drivingToken,
    double progress,
    String note,
    List<String> extraColumns
) {
    public RoomDeployHudElement {
        kind = kind == null ? RoomDeployHudElementKind.CAPTURE : kind;
        code = code == null ? "" : code;
        ownerToken = normalizeToken(ownerToken);
        drivingToken = normalizeToken(drivingToken);
        note = note == null ? "" : note;
        extraColumns = extraColumns == null ? List.of() : List.copyOf(extraColumns);
    }

    public static RoomDeployHudElement capture(
        String code,
        RoomPosition position,
        RoomTeamId owner,
        RoomTeamId driving,
        double progress,
        String note,
        int campACount,
        int campBCount
    ) {
        return new RoomDeployHudElement(
            RoomDeployHudElementKind.CAPTURE,
            code,
            position.x(),
            floorY(position),
            position.z(),
            toTeamToken(owner),
            toTeamToken(driving),
            progress,
            note,
            List.of(Integer.toString(campACount), Integer.toString(campBCount))
        );
    }

    public static RoomDeployHudElement boundary(String teamToken, RoomPosition position, String polygonId) {
        return new RoomDeployHudElement(
            RoomDeployHudElementKind.BOUNDARY,
            normalizeToken(teamToken),
            position.x(),
            floorY(position),
            position.z(),
            normalizeToken(teamToken),
            "N",
            50.0D,
            polygonId,
            List.of()
        );
    }

    public static RoomDeployHudElement spawn(String code, RoomPosition position, RoomTeamId ownerTeam, String note) {
        return spawn(code, position, ownerTeam, note, List.of());
    }

    public static RoomDeployHudElement spawn(String code, RoomPosition position, RoomTeamId ownerTeam, String note, List<String> extraColumns) {
        return new RoomDeployHudElement(
            RoomDeployHudElementKind.SPAWN,
            code,
            position.x(),
            floorY(position),
            position.z(),
            toTeamToken(ownerTeam),
            toTeamToken(ownerTeam),
            50.0D,
            note,
            extraColumns
        );
    }

    public static RoomDeployHudElement teammate(UUID teammateId, RoomPosition position, RoomTeamId ownerTeam, float yaw, String note, boolean teammateCombat) {
        return new RoomDeployHudElement(
            RoomDeployHudElementKind.TEAMMATE,
            teammateId == null ? "" : teammateId.toString(),
            position.x(),
            floorY(position),
            position.z(),
            toTeamToken(ownerTeam),
            "N",
            yaw,
            note,
            List.of(teammateCombat ? "1" : "0")
        );
    }

    public static RoomDeployHudElement beacon(UUID beaconId, RoomPosition position, RoomTeamId ownerTeam, String note) {
        return new RoomDeployHudElement(
            RoomDeployHudElementKind.BEACON,
            beaconId == null ? "" : beaconId.toString(),
            position.x(),
            floorY(position),
            position.z(),
            toTeamToken(ownerTeam),
            "N",
            50.0D,
            note,
            List.of("0")
        );
    }

    public static RoomDeployHudElement vehicle(
        String entryId,
        double x,
        double y,
        double z,
        RoomTeamId ownerTeam,
        RoomTeamId drivingTeam,
        double healthPercent,
        String note,
        String stateToken,
        boolean lowHealth,
        boolean deployable
    ) {
        return new RoomDeployHudElement(
            RoomDeployHudElementKind.VEHICLE,
            entryId,
            x,
            y,
            z,
            toTeamToken(ownerTeam),
            toTeamToken(drivingTeam),
            healthPercent,
            note,
            // GD656Mods: 第 3 列 = 服务端权威的"该玩家能否部署到这台载具"。
            // 不能只靠 stateToken 判断: TEAM(蓝色)既可能是"阵营友军驾驶但允许全员部署"(可部署),
            // 也可能是"阵营友军驾驶且只允许小队"(不可部署), 两者颜色相同。
            List.of(stateToken == null ? "" : stateToken, lowHealth ? "1" : "0", deployable ? "1" : "0")
        );
    }

    private static double floorY(RoomPosition position) {
        return position == null ? 0.0D : Math.floor(position.y());
    }

    public static String toTeamToken(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "T";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "S";
        }
        return "N";
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "N";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "T", "S", "N" -> normalized;
            default -> "N";
        };
    }
}
