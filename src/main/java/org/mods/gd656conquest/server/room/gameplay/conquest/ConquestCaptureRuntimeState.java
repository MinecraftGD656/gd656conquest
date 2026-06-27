package org.mods.gd656conquest.server.room.gameplay.conquest;

import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConquestCaptureRuntimeState {
    private final Map<String, Double> progressByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> ownerByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> drivingTeamByCode = new LinkedHashMap<>();
    private final Map<String, Integer> campACountByCode = new LinkedHashMap<>();
    private final Map<String, Integer> campBCountByCode = new LinkedHashMap<>();
    private final Map<String, Long> lastScoreTickByCode = new LinkedHashMap<>();
    private final Map<String, Long> lastProgressBonusTickByPlayerCapture = new LinkedHashMap<>();
    private final Map<String, Long> lastNeutralizeBonusTickByPlayerCapture = new LinkedHashMap<>();
    private final Map<String, Long> lastControlBonusTickByPlayerCapture = new LinkedHashMap<>();

    public void initialize(RoomCaptureConfig config) {
        progressByCode.clear();
        ownerByCode.clear();
        drivingTeamByCode.clear();
        campACountByCode.clear();
        campBCountByCode.clear();
        lastScoreTickByCode.clear();
        lastProgressBonusTickByPlayerCapture.clear();
        lastNeutralizeBonusTickByPlayerCapture.clear();
        lastControlBonusTickByPlayerCapture.clear();
        synchronizeWithConfig(config);
    }

    public void synchronizeWithConfig(RoomCaptureConfig config) {
        if (config == null || config.points().isEmpty()) {
            progressByCode.clear();
            ownerByCode.clear();
            drivingTeamByCode.clear();
            campACountByCode.clear();
            campBCountByCode.clear();
            lastScoreTickByCode.clear();
            lastProgressBonusTickByPlayerCapture.clear();
            lastNeutralizeBonusTickByPlayerCapture.clear();
            lastControlBonusTickByPlayerCapture.clear();
            return;
        }
        RoomTeamId initialOwner = resolveInitialOwner(config);
        Set<String> activeCodes = new LinkedHashSet<>();
        for (RoomCapturePointDefinition point : RoomObjectiveSupport.activePoints(config)) {
            String code = RoomObjectiveSupport.normalizeCode(point.code());
            activeCodes.add(code);
            progressByCode.putIfAbsent(code, initialOwner == RoomTeamId.CAMP_A ? 100.0D : initialOwner == RoomTeamId.CAMP_B ? 0.0D : 50.0D);
            ownerByCode.putIfAbsent(code, initialOwner);
            drivingTeamByCode.putIfAbsent(code, null);
            campACountByCode.putIfAbsent(code, 0);
            campBCountByCode.putIfAbsent(code, 0);
            lastScoreTickByCode.putIfAbsent(code, -1L);
        }
        progressByCode.keySet().removeIf(code -> !activeCodes.contains(code));
        ownerByCode.keySet().removeIf(code -> !activeCodes.contains(code));
        drivingTeamByCode.keySet().removeIf(code -> !activeCodes.contains(code));
        campACountByCode.keySet().removeIf(code -> !activeCodes.contains(code));
        campBCountByCode.keySet().removeIf(code -> !activeCodes.contains(code));
        lastScoreTickByCode.keySet().removeIf(code -> !activeCodes.contains(code));
        lastProgressBonusTickByPlayerCapture.keySet().removeIf(key -> !matchesActiveCode(key, activeCodes));
        lastNeutralizeBonusTickByPlayerCapture.keySet().removeIf(key -> !matchesActiveCode(key, activeCodes));
        lastControlBonusTickByPlayerCapture.keySet().removeIf(key -> !matchesActiveCode(key, activeCodes));
    }

    public Map<String, Double> getProgressByCode() {
        return progressByCode;
    }

    public Map<String, RoomTeamId> getOwnerByCode() {
        return ownerByCode;
    }

    public Map<String, RoomTeamId> getDrivingTeamByCode() {
        return drivingTeamByCode;
    }

    public Map<String, Integer> getCampACountByCode() {
        return campACountByCode;
    }

    public Map<String, Integer> getCampBCountByCode() {
        return campBCountByCode;
    }

    public Map<String, Long> getLastScoreTickByCode() {
        return lastScoreTickByCode;
    }

    public Map<String, Long> getLastProgressBonusTickByPlayerCapture() {
        return lastProgressBonusTickByPlayerCapture;
    }

    public Map<String, Long> getLastNeutralizeBonusTickByPlayerCapture() {
        return lastNeutralizeBonusTickByPlayerCapture;
    }

    public Map<String, Long> getLastControlBonusTickByPlayerCapture() {
        return lastControlBonusTickByPlayerCapture;
    }

    public boolean isFullyControlledBy(String code, RoomTeamId teamId) {
        double progress = progressByCode.getOrDefault(code, 50.0D);
        if (teamId == RoomTeamId.CAMP_A) {
            return progress >= 99.999D;
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return progress <= 0.001D;
        }
        return false;
    }

    private RoomTeamId resolveInitialOwner(RoomCaptureConfig config) {
        if (config == null) {
            return null;
        }
        return switch (config.initialCaptureOwnership()) {
            case "CAMP_A" -> RoomTeamId.CAMP_A;
            case "CAMP_B" -> RoomTeamId.CAMP_B;
            default -> null;
        };
    }

    private boolean matchesActiveCode(String key, Set<String> activeCodes) {
        if (key == null || activeCodes == null || activeCodes.isEmpty()) {
            return false;
        }
        int separator = key.indexOf('|');
        if (separator < 0 || separator >= key.length() - 1) {
            return false;
        }
        return activeCodes.contains(RoomObjectiveSupport.normalizeCode(key.substring(separator + 1)));
    }
}
