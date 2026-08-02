package org.mods.gd656conquest.server.room.gameplay.conquest;

import org.mods.gd656conquest.server.room.model.ConquestFullCaptureResponseMode;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.EnumMap;
import java.util.Map;

public final class ConquestScoreRuntimeState {
    private int campAScore;
    private int campBScore;
    private RoomTeamId fullCaptureOwnerTeam;
    private RoomTeamId fullCaptureEnemyTeam;
    private long fullCaptureEndTick = -1L;
    private ConquestFullCaptureResponseMode fullCaptureResponseMode = ConquestFullCaptureResponseMode.defaultValue();
    private long fullCaptureNextAnnouncementTick = -1L;
    private long antiBaseNukeCooldownUntilTick = -1L;
    private final Map<RoomTeamId, String> eventByTeam = new EnumMap<>(RoomTeamId.class);
    private final Map<RoomTeamId, Long> eventExpireTickByTeam = new EnumMap<>(RoomTeamId.class);

    public int getCampAScore() {
        return campAScore;
    }

    public void setCampAScore(int campAScore) {
        this.campAScore = Math.max(0, campAScore);
    }

    public int getCampBScore() {
        return campBScore;
    }

    public void setCampBScore(int campBScore) {
        this.campBScore = Math.max(0, campBScore);
    }

    public RoomTeamId getFullCaptureOwnerTeam() {
        return fullCaptureOwnerTeam;
    }

    public void setFullCaptureOwnerTeam(RoomTeamId fullCaptureOwnerTeam) {
        this.fullCaptureOwnerTeam = fullCaptureOwnerTeam;
    }

    public RoomTeamId getFullCaptureEnemyTeam() {
        return fullCaptureEnemyTeam;
    }

    public void setFullCaptureEnemyTeam(RoomTeamId fullCaptureEnemyTeam) {
        this.fullCaptureEnemyTeam = fullCaptureEnemyTeam;
    }

    public long getFullCaptureEndTick() {
        return fullCaptureEndTick;
    }

    public void setFullCaptureEndTick(long fullCaptureEndTick) {
        this.fullCaptureEndTick = fullCaptureEndTick;
    }

    public ConquestFullCaptureResponseMode getFullCaptureResponseMode() {
        return fullCaptureResponseMode;
    }

    public void setFullCaptureResponseMode(ConquestFullCaptureResponseMode fullCaptureResponseMode) {
        this.fullCaptureResponseMode = fullCaptureResponseMode == null
            ? ConquestFullCaptureResponseMode.defaultValue()
            : fullCaptureResponseMode;
    }

    public long getFullCaptureNextAnnouncementTick() {
        return fullCaptureNextAnnouncementTick;
    }

    public void setFullCaptureNextAnnouncementTick(long fullCaptureNextAnnouncementTick) {
        this.fullCaptureNextAnnouncementTick = fullCaptureNextAnnouncementTick;
    }

    public long getAntiBaseNukeCooldownUntilTick() {
        return antiBaseNukeCooldownUntilTick;
    }

    public void setAntiBaseNukeCooldownUntilTick(long antiBaseNukeCooldownUntilTick) {
        this.antiBaseNukeCooldownUntilTick = antiBaseNukeCooldownUntilTick;
    }

    public boolean isFullCaptureCountdownActive(long gameTick) {
        return fullCaptureResponseMode == ConquestFullCaptureResponseMode.COUNTDOWN_END
            && fullCaptureOwnerTeam != null
            && fullCaptureEndTick > gameTick;
    }

    public boolean isFullCapturePressureActive(long gameTick) {
        return fullCaptureOwnerTeam != null
            && fullCaptureEndTick > gameTick;
    }

    public void setTeamEvent(RoomTeamId teamId, String eventMessage, long expireTick) {
        if (teamId == null) {
            return;
        }
        String normalized = eventMessage == null || eventMessage.isBlank() ? "-" : eventMessage;
        eventByTeam.put(teamId, normalized);
        eventExpireTickByTeam.put(teamId, expireTick);
    }

    public String resolveTeamEvent(RoomTeamId teamId, long gameTick) {
        if (teamId == null) {
            return "-";
        }
        Long expireTick = eventExpireTickByTeam.get(teamId);
        if (expireTick == null || expireTick <= gameTick) {
            return "-";
        }
        return eventByTeam.getOrDefault(teamId, "-");
    }

    public void clearExpiredEvents(long gameTick) {
        java.util.Iterator<Map.Entry<RoomTeamId, Long>> iterator = eventExpireTickByTeam.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RoomTeamId, Long> entry = iterator.next();
            if (entry.getValue() > gameTick) {
                continue;
            }
            RoomTeamId teamId = entry.getKey();
            iterator.remove();
            eventByTeam.remove(teamId);
        }
    }

    public void clearTeamEvents() {
        eventByTeam.clear();
        eventExpireTickByTeam.clear();
    }

    public void clearFullCaptureState() {
        fullCaptureOwnerTeam = null;
        fullCaptureEnemyTeam = null;
        fullCaptureEndTick = -1L;
        fullCaptureResponseMode = ConquestFullCaptureResponseMode.defaultValue();
        fullCaptureNextAnnouncementTick = -1L;
    }
}
