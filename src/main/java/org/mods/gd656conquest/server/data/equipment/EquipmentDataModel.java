package org.mods.gd656conquest.server.data.equipment;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EquipmentDataModel {
    private Map<String, EquipmentEntry> equipmentById = new LinkedHashMap<>();
    private Map<String, String> defaultIdBySlot = new LinkedHashMap<>();
    private Map<String, ArmorEntry> armorByCampClass = new LinkedHashMap<>();

    public Map<String, EquipmentEntry> getEquipmentById() {
        if (equipmentById == null) {
            equipmentById = new LinkedHashMap<>();
        }
        return equipmentById;
    }

    public void setEquipmentById(Map<String, EquipmentEntry> equipmentById) {
        this.equipmentById = equipmentById;
    }

    public Map<String, String> getDefaultIdBySlot() {
        if (defaultIdBySlot == null) {
            defaultIdBySlot = new LinkedHashMap<>();
        }
        return defaultIdBySlot;
    }

    public void setDefaultIdBySlot(Map<String, String> defaultIdBySlot) {
        this.defaultIdBySlot = defaultIdBySlot;
    }

    public Map<String, ArmorEntry> getArmorByCampClass() {
        if (armorByCampClass == null) {
            armorByCampClass = new LinkedHashMap<>();
        }
        return armorByCampClass;
    }

    public void setArmorByCampClass(Map<String, ArmorEntry> armorByCampClass) {
        this.armorByCampClass = armorByCampClass;
    }

    public static final class EquipmentEntry {
        private String id;
        private int slot;
        private String slotName;
        private String displayName;
        private int extraData;
        private int unlockLevel;
        private int availableClasses = 1234;
        private String itemSnbt;
        private int replenishSeconds;
        private Boolean ammoBoxRefillEnabled = Boolean.TRUE;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getSlot() {
            return slot;
        }

        public void setSlot(int slot) {
            this.slot = slot;
        }

        public String getSlotName() {
            return slotName;
        }

        public void setSlotName(String slotName) {
            this.slotName = slotName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getExtraData() {
            return extraData;
        }

        public void setExtraData(int extraData) {
            this.extraData = extraData;
        }

        public int getUnlockLevel() {
            return unlockLevel;
        }

        public void setUnlockLevel(int unlockLevel) {
            this.unlockLevel = unlockLevel;
        }

        public int getAvailableClasses() {
            if (availableClasses <= 0) {
                availableClasses = 1234;
            }
            return availableClasses;
        }

        public void setAvailableClasses(int availableClasses) {
            this.availableClasses = availableClasses;
        }

        public String getItemSnbt() {
            return itemSnbt;
        }

        public void setItemSnbt(String itemSnbt) {
            this.itemSnbt = itemSnbt;
        }

        public int getReplenishSeconds() {
            return Math.max(0, replenishSeconds);
        }

        public void setReplenishSeconds(int replenishSeconds) {
            this.replenishSeconds = Math.max(0, replenishSeconds);
        }

        public boolean isAmmoBoxRefillEnabled() {
            return ammoBoxRefillEnabled == null || ammoBoxRefillEnabled;
        }

        public void setAmmoBoxRefillEnabled(Boolean ammoBoxRefillEnabled) {
            this.ammoBoxRefillEnabled = ammoBoxRefillEnabled == null ? Boolean.TRUE : ammoBoxRefillEnabled;
        }
    }

    public static final class ArmorEntry {
        private String helmetSnbt;
        private String chestplateSnbt;
        private String leggingsSnbt;
        private String bootsSnbt;

        public String getHelmetSnbt() {
            return helmetSnbt;
        }

        public void setHelmetSnbt(String helmetSnbt) {
            this.helmetSnbt = helmetSnbt;
        }

        public String getChestplateSnbt() {
            return chestplateSnbt;
        }

        public void setChestplateSnbt(String chestplateSnbt) {
            this.chestplateSnbt = chestplateSnbt;
        }

        public String getLeggingsSnbt() {
            return leggingsSnbt;
        }

        public void setLeggingsSnbt(String leggingsSnbt) {
            this.leggingsSnbt = leggingsSnbt;
        }

        public String getBootsSnbt() {
            return bootsSnbt;
        }

        public void setBootsSnbt(String bootsSnbt) {
            this.bootsSnbt = bootsSnbt;
        }
    }
}
