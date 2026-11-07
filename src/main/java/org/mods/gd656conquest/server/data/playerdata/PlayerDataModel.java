package org.mods.gd656conquest.server.data.playerdata;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerDataModel {
    private int level;
    private int levelExperience;
    private Map<String, String> customLoadoutSelectionBySlot = new HashMap<>();
    private Map<String, Map<String, String>> attachmentSelectionByLoadoutEntryId = new HashMap<>();
    private Map<String, Map<String, Map<String, String>>> attachmentSelectionByClass = new HashMap<>();
    private String currentClassType = "assault";
    private boolean teamChannelTipAcknowledged;
    private int unacknowledgedTeamChannelMessageCount;
    private Set<String> ownedTitleIds = new LinkedHashSet<>();
    private String equippedTitleId = "";
    private String lastKnownPlayerName = "";
    private List<PendingTitleNotice> pendingTitleNotices = new ArrayList<>();
    private MatchStatistics matchStatistics = new MatchStatistics();
    private Map<String, RoomRatingRecord> roomRatings = new HashMap<>();

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLevelExperience() {
        return levelExperience;
    }

    public void setLevelExperience(int levelExperience) {
        this.levelExperience = levelExperience;
    }

    public Map<String, String> getCustomLoadoutSelectionBySlot() {
        if (customLoadoutSelectionBySlot == null) {
            customLoadoutSelectionBySlot = new HashMap<>();
        }
        return customLoadoutSelectionBySlot;
    }

    public void setCustomLoadoutSelectionBySlot(Map<String, String> customLoadoutSelectionBySlot) {
        this.customLoadoutSelectionBySlot = customLoadoutSelectionBySlot;
    }

    public Map<String, Map<String, String>> getAttachmentSelectionByLoadoutEntryId() {
        return getAttachmentSelectionForClass(getCurrentClassType());
    }

    /** Each class inherits the legacy configuration once, then owns an independent mutable copy. */
    public Map<String, Map<String, String>> getAttachmentSelectionForClass(String classKey) {
        if (attachmentSelectionByClass == null) attachmentSelectionByClass = new HashMap<>();
        String key = classKey == null || classKey.isBlank() ? "assault" : classKey.trim().toLowerCase(java.util.Locale.ROOT);
        return attachmentSelectionByClass.computeIfAbsent(key, ignored -> {
            Map<String, Map<String, String>> copy = new HashMap<>();
            legacyAttachmentSelection().forEach((id, selection) -> copy.put(id, selection == null ? new HashMap<>() : new HashMap<>(selection)));
            return copy;
        });
    }

    private Map<String, Map<String, String>> legacyAttachmentSelection() {
        if (attachmentSelectionByLoadoutEntryId == null) {
            attachmentSelectionByLoadoutEntryId = new HashMap<>();
        }
        return attachmentSelectionByLoadoutEntryId;
    }

    public void setAttachmentSelectionByLoadoutEntryId(Map<String, Map<String, String>> attachmentSelectionByLoadoutEntryId) {
        this.attachmentSelectionByLoadoutEntryId = attachmentSelectionByLoadoutEntryId;
        this.attachmentSelectionByClass = new HashMap<>();
    }

    /** Administrative equipment renames/removals cover the migration seed and every saved class. */
    public List<Map<String, Map<String, String>>> getAllAttachmentSelectionGroups() {
        List<Map<String, Map<String, String>>> groups = new ArrayList<>();
        groups.add(legacyAttachmentSelection());
        if (attachmentSelectionByClass != null) for (var group : attachmentSelectionByClass.values()) if (group != null) groups.add(group);
        return groups;
    }

    public String getCurrentClassType() {
        if (currentClassType == null || currentClassType.isBlank()) {
            currentClassType = "assault";
        }
        return currentClassType;
    }

    public void setCurrentClassType(String currentClassType) {
        this.currentClassType = currentClassType;
    }

    public boolean isTeamChannelTipAcknowledged() {
        return teamChannelTipAcknowledged;
    }

    public void setTeamChannelTipAcknowledged(boolean teamChannelTipAcknowledged) {
        this.teamChannelTipAcknowledged = teamChannelTipAcknowledged;
    }

    public int getUnacknowledgedTeamChannelMessageCount() {
        return Math.max(0, unacknowledgedTeamChannelMessageCount);
    }

    public void setUnacknowledgedTeamChannelMessageCount(int unacknowledgedTeamChannelMessageCount) {
        this.unacknowledgedTeamChannelMessageCount = Math.max(0, unacknowledgedTeamChannelMessageCount);
    }

    public Set<String> getOwnedTitleIds() {
        if (ownedTitleIds == null) {
            ownedTitleIds = new LinkedHashSet<>();
        }
        return ownedTitleIds;
    }

    public void setOwnedTitleIds(Set<String> ownedTitleIds) {
        this.ownedTitleIds = ownedTitleIds;
    }

    public String getEquippedTitleId() {
        return equippedTitleId == null ? "" : equippedTitleId;
    }

    public void setEquippedTitleId(String equippedTitleId) {
        this.equippedTitleId = equippedTitleId;
    }

    public String getLastKnownPlayerName() {
        return lastKnownPlayerName == null ? "" : lastKnownPlayerName;
    }

    public void setLastKnownPlayerName(String lastKnownPlayerName) {
        this.lastKnownPlayerName = lastKnownPlayerName;
    }

    public List<PendingTitleNotice> getPendingTitleNotices() {
        if (pendingTitleNotices == null) {
            pendingTitleNotices = new ArrayList<>();
        }
        return pendingTitleNotices;
    }

    public void setPendingTitleNotices(List<PendingTitleNotice> pendingTitleNotices) {
        this.pendingTitleNotices = pendingTitleNotices;
    }

    public MatchStatistics getMatchStatistics() {
        if (matchStatistics == null) {
            matchStatistics = new MatchStatistics();
        }
        return matchStatistics;
    }

    public void setMatchStatistics(MatchStatistics matchStatistics) {
        this.matchStatistics = matchStatistics;
    }

    public Map<String, RoomRatingRecord> getRoomRatings() {
        if (roomRatings == null) {
            roomRatings = new HashMap<>();
        }
        return roomRatings;
    }

    public void setRoomRatings(Map<String, RoomRatingRecord> roomRatings) {
        this.roomRatings = roomRatings;
    }

    public static final class PendingTitleNotice {
        private String giverName = "";
        private String titleId = "";
        private String titleText = "";

        public String getGiverName() {
            return giverName == null ? "" : giverName;
        }

        public void setGiverName(String giverName) {
            this.giverName = giverName;
        }

        public String getTitleId() {
            return titleId == null ? "" : titleId;
        }

        public void setTitleId(String titleId) {
            this.titleId = titleId;
        }

        public String getTitleText() {
            return titleText == null ? "" : titleText;
        }

        public void setTitleText(String titleText) {
            this.titleText = titleText;
        }
    }

    public static final class MatchStatistics {
        private double matchActiveMinutes;
        private int matchesPlayed;
        private int wins;
        private int losses;
        private int draws;

        public boolean normalize() {
            boolean changed = false;
            if (matchActiveMinutes < 0.0D) {
                matchActiveMinutes = 0.0D;
                changed = true;
            }
            if (matchesPlayed < 0) {
                matchesPlayed = 0;
                changed = true;
            }
            if (wins < 0) {
                wins = 0;
                changed = true;
            }
            if (losses < 0) {
                losses = 0;
                changed = true;
            }
            if (draws < 0) {
                draws = 0;
                changed = true;
            }
            return changed;
        }

        public double getMatchActiveMinutes() {
            if (matchActiveMinutes < 0.0D) {
                matchActiveMinutes = 0.0D;
            }
            return matchActiveMinutes;
        }

        public void setMatchActiveMinutes(double matchActiveMinutes) {
            this.matchActiveMinutes = Math.max(0.0D, matchActiveMinutes);
        }

        public int getMatchesPlayed() {
            if (matchesPlayed < 0) {
                matchesPlayed = 0;
            }
            return matchesPlayed;
        }

        public void setMatchesPlayed(int matchesPlayed) {
            this.matchesPlayed = Math.max(0, matchesPlayed);
        }

        public int getWins() {
            if (wins < 0) {
                wins = 0;
            }
            return wins;
        }

        public void setWins(int wins) {
            this.wins = Math.max(0, wins);
        }

        public int getLosses() {
            if (losses < 0) {
                losses = 0;
            }
            return losses;
        }

        public void setLosses(int losses) {
            this.losses = Math.max(0, losses);
        }

        public int getDraws() {
            if (draws < 0) {
                draws = 0;
            }
            return draws;
        }

        public void setDraws(int draws) {
            this.draws = Math.max(0, draws);
        }
    }

    public static final class RoomRatingRecord {
        private String roomId = "";
        private int rating;
        private long updatedAtEpochMillis;

        public String getRoomId() {
            return roomId == null ? "" : roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public int getRating() {
            if (rating < 1 || rating > 5) {
                rating = 0;
            }
            return rating;
        }

        public void setRating(int rating) {
            this.rating = rating >= 1 && rating <= 5 ? rating : 0;
        }

        public long getUpdatedAtEpochMillis() {
            if (updatedAtEpochMillis < 0L) {
                updatedAtEpochMillis = 0L;
            }
            return updatedAtEpochMillis;
        }

        public void setUpdatedAtEpochMillis(long updatedAtEpochMillis) {
            this.updatedAtEpochMillis = Math.max(0L, updatedAtEpochMillis);
        }
    }

}
