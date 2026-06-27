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
    private String currentClassType = "assault";
    private boolean teamChannelTipAcknowledged;
    private int unacknowledgedTeamChannelMessageCount;
    private Set<String> ownedTitleIds = new LinkedHashSet<>();
    private String equippedTitleId = "";
    private String lastKnownPlayerName = "";
    private List<PendingTitleNotice> pendingTitleNotices = new ArrayList<>();

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

}
