package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

public final class RoomObjectiveStatusTextService {
    public String buildFocusStatusText(RoomObjectivePresentationContext context) {
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
            return "正在争夺据点 " + label;
        }
        if (owner == null) {
            return "中立据点 " + label;
        }
        if (owner == selfTeam) {
            return "我方控制据点 " + label;
        }
        return "敌方控制据点 " + label;
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
