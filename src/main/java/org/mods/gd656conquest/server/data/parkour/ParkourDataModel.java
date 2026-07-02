package org.mods.gd656conquest.server.data.parkour;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ParkourDataModel {
    private Map<String, PlayerRecord> playerRecords = new LinkedHashMap<>();

    public Map<String, PlayerRecord> getPlayerRecords() {
        return playerRecords;
    }

    public void setPlayerRecords(Map<String, PlayerRecord> playerRecords) {
        this.playerRecords = playerRecords == null ? new LinkedHashMap<>() : new LinkedHashMap<>(playerRecords);
    }

    public static final class PlayerRecord {
        private String playerName = "";
        private Long bestTimeMillis = 0L;
        private Long lastTimeMillis = 0L;
        private Long bestUpdatedAtEpochMs = 0L;
        private Long lastUpdatedAtEpochMs = 0L;

        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName == null ? "" : playerName;
        }

        public Long getBestTimeMillis() {
            return bestTimeMillis;
        }

        public void setBestTimeMillis(Long bestTimeMillis) {
            this.bestTimeMillis = bestTimeMillis == null ? 0L : bestTimeMillis;
        }

        public Long getLastTimeMillis() {
            return lastTimeMillis;
        }

        public void setLastTimeMillis(Long lastTimeMillis) {
            this.lastTimeMillis = lastTimeMillis == null ? 0L : lastTimeMillis;
        }

        public Long getBestUpdatedAtEpochMs() {
            return bestUpdatedAtEpochMs;
        }

        public void setBestUpdatedAtEpochMs(Long bestUpdatedAtEpochMs) {
            this.bestUpdatedAtEpochMs = bestUpdatedAtEpochMs == null ? 0L : bestUpdatedAtEpochMs;
        }

        public Long getLastUpdatedAtEpochMs() {
            return lastUpdatedAtEpochMs;
        }

        public void setLastUpdatedAtEpochMs(Long lastUpdatedAtEpochMs) {
            this.lastUpdatedAtEpochMs = lastUpdatedAtEpochMs == null ? 0L : lastUpdatedAtEpochMs;
        }
    }
}
