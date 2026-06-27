package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

public final class RoomObjectiveEventTextService {
    public String buildFocusEventText(RoomObjectivePresentationContext context) {
        if (context == null) {
            return "-";
        }
        var activePoints = context.activePoints();
        var runtimeView = context.runtimeView();
        String focusCode = context.focusCode();
        RoomTeamId selfTeam = context.selfTeam();
        if (runtimeView == null || selfTeam == null) {
            return "-";
        }
        String label = resolveObjectiveLabel(activePoints, focusCode);
        if (label == null) {
            return "-";
        }
        String normalizedCode = RoomObjectiveSupport.normalizeCode(focusCode);
        RoomTeamId owner = runtimeView.ownerOf(normalizedCode);
        RoomTeamId driving = runtimeView.drivingTeamOf(normalizedCode);
        if (driving == selfTeam && owner != selfTeam) {
            return "我军正在进攻据点 " + label;
        }
        RoomTeamId enemy = selfTeam == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        if (driving == enemy && owner != enemy) {
            return "敌军正在进攻据点 " + label;
        }
        return "-";
    }

    private String resolveObjectiveLabel(java.util.List<org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition> activePoints, String focusCode) {
        String normalizedCode = RoomObjectiveSupport.normalizeCode(focusCode);
        if (normalizedCode.isBlank()) {
            return null;
        }
        if (activePoints != null) {
            for (RoomCapturePointDefinition point : activePoints) {
                if (point == null) {
                    continue;
                }
                if (RoomObjectiveSupport.normalizeCode(point.code()).equals(normalizedCode)) {
                    return normalizedCode;
                }
            }
        }
        return normalizedCode;
    }
}
