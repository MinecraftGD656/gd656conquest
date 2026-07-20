package org.mods.gd656conquest.server.data.equipmentset;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EquipmentSetDataModel {
    public static final String EMPTY_ID = "EMPTY";

    private Integer version = 1;
    private Map<String, EquipmentSetEntry> entriesById = new LinkedHashMap<>();

    public Integer getVersion() {
        return version == null || version <= 0 ? 1 : version;
    }

    public void setVersion(Integer version) {
        this.version = version == null || version <= 0 ? 1 : version;
    }

    public Map<String, EquipmentSetEntry> getEntriesById() {
        if (entriesById == null) {
            entriesById = new LinkedHashMap<>();
        }
        return entriesById;
    }

    public void setEntriesById(Map<String, EquipmentSetEntry> entriesById) {
        this.entriesById = entriesById == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entriesById);
    }

    public static EquipmentSetEntry emptyEntry() {
        EquipmentSetEntry entry = new EquipmentSetEntry();
        entry.setId(EMPTY_ID);
        return entry;
    }

    public static final class EquipmentSetEntry {
        private String id = "";
        private String headItemSnbt = "";
        private String chestItemSnbt = "";
        private String legsItemSnbt = "";
        private String feetItemSnbt = "";

        public String getId() {
            return id == null ? "" : id;
        }

        public void setId(String id) {
            this.id = id == null ? "" : id.trim();
        }

        public String getHeadItemSnbt() {
            return headItemSnbt == null ? "" : headItemSnbt;
        }

        public void setHeadItemSnbt(String headItemSnbt) {
            this.headItemSnbt = headItemSnbt == null ? "" : headItemSnbt.trim();
        }

        public String getChestItemSnbt() {
            return chestItemSnbt == null ? "" : chestItemSnbt;
        }

        public void setChestItemSnbt(String chestItemSnbt) {
            this.chestItemSnbt = chestItemSnbt == null ? "" : chestItemSnbt.trim();
        }

        public String getLegsItemSnbt() {
            return legsItemSnbt == null ? "" : legsItemSnbt;
        }

        public void setLegsItemSnbt(String legsItemSnbt) {
            this.legsItemSnbt = legsItemSnbt == null ? "" : legsItemSnbt.trim();
        }

        public String getFeetItemSnbt() {
            return feetItemSnbt == null ? "" : feetItemSnbt;
        }

        public void setFeetItemSnbt(String feetItemSnbt) {
            this.feetItemSnbt = feetItemSnbt == null ? "" : feetItemSnbt.trim();
        }
    }
}
