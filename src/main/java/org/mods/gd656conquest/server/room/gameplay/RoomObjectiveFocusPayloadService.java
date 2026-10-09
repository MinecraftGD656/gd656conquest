package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;
import org.mods.gd656conquest.server.room.runtime.RoomHudPayloadCodec;

import java.util.Locale;

public final class RoomObjectiveFocusPayloadService {
    public String buildFocusPayload(RoomObjectivePresentationContext context) {
        if (context == null) {
            return "-";
        }
        var activePoints = context.activePoints();
        var runtimeView = context.runtimeView();
        String focusCode = context.focusCode();
        RoomTeamId selfTeam = context.selfTeam();
        if (activePoints == null || runtimeView == null || focusCode == null || focusCode.isBlank() || selfTeam == null) {
            return "-";
        }
        String normalizedCode = RoomObjectiveSupport.normalizeCode(focusCode);
        RoomCapturePointDefinition point = activePoints.stream()
            .filter(candidate -> RoomObjectiveSupport.normalizeCode(candidate.code()).equals(normalizedCode))
            .findFirst()
            .orElse(null);
        if (point == null) {
            return "-";
        }
        int ally = runtimeView.teamCount(normalizedCode, selfTeam);
        RoomTeamId enemyTeam = selfTeam == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        int enemy = runtimeView.teamCount(normalizedCode, enemyTeam);
        double progress = Math.round(runtimeView.progressOf(normalizedCode) * 10.0D) / 10.0D;
        RoomTeamId owner = runtimeView.ownerOf(normalizedCode);
        RoomTeamId driving = runtimeView.drivingTeamOf(normalizedCode);
        String displayName = RoomHudPayloadCodec.encodeText(point.displayName());
        return normalizedCode
            + "," + displayName
            + "," + ally
            + "," + enemy
            + "," + String.format(Locale.ROOT, "%.1f", progress)
            + "," + toTeamToken(owner)
            + "," + toTeamToken(driving);
    }

    private String toTeamToken(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "T";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "S";
        }
        return "N";
    }
}
