package org.mods.gd656conquest.server.data.herokit;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HeroKitDataModel {
    private Integer version = 2;
    private Map<String, HeroKitEntry> heroKitsById = new LinkedHashMap<>();

    public Integer getVersion() {
        return version == null || version <= 0 ? 2 : version;
    }

    public void setVersion(Integer version) {
        this.version = version == null || version <= 0 ? 2 : version;
    }

    public Map<String, HeroKitEntry> getHeroKitsById() {
        if (heroKitsById == null) {
            heroKitsById = new LinkedHashMap<>();
        }
        return heroKitsById;
    }

    public void setHeroKitsById(Map<String, HeroKitEntry> heroKitsById) {
        this.heroKitsById = heroKitsById == null ? new LinkedHashMap<>() : new LinkedHashMap<>(heroKitsById);
    }

    public static final class HeroKitEntry {
        private String id = "";
        private String displayName = "";
        private Integer cost = 0;
        private String type = "";
        private Integer cooldownSeconds = 0;
        private Map<String, String> hotbarBySlot = new LinkedHashMap<>();
        private Map<String, String> inventoryBySlot = new LinkedHashMap<>();
        private String offhandItemSnbt = "";
        private ArmorLoadout armor = new ArmorLoadout();

        public String getId() {
            return id == null ? "" : id;
        }

        public void setId(String id) {
            this.id = id == null ? "" : id.trim();
        }

        public String getDisplayName() {
            return displayName == null ? "" : displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName == null ? "" : displayName.trim();
        }

        public Integer getCost() {
            return cost == null || cost < 0 ? 0 : cost;
        }

        public void setCost(Integer cost) {
            this.cost = cost == null || cost < 0 ? 0 : cost;
        }

        public String getType() {
            return type == null ? "" : type;
        }

        public void setType(String type) {
            this.type = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
        }

        public Integer getCooldownSeconds() {
            return cooldownSeconds == null || cooldownSeconds < 0 ? 0 : cooldownSeconds;
        }

        public void setCooldownSeconds(Integer cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds == null || cooldownSeconds < 0 ? 0 : cooldownSeconds;
        }

        public Map<String, String> getHotbarBySlot() {
            if (hotbarBySlot == null) {
                hotbarBySlot = new LinkedHashMap<>();
            }
            return hotbarBySlot;
        }

        public void setHotbarBySlot(Map<String, String> hotbarBySlot) {
            this.hotbarBySlot = hotbarBySlot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(hotbarBySlot);
        }

        public Map<String, String> getInventoryBySlot() {
            if (inventoryBySlot == null) {
                inventoryBySlot = new LinkedHashMap<>();
            }
            return inventoryBySlot;
        }

        public void setInventoryBySlot(Map<String, String> inventoryBySlot) {
            this.inventoryBySlot = inventoryBySlot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inventoryBySlot);
        }

        public String getOffhandItemSnbt() {
            return offhandItemSnbt == null ? "" : offhandItemSnbt;
        }

        public void setOffhandItemSnbt(String offhandItemSnbt) {
            this.offhandItemSnbt = offhandItemSnbt == null ? "" : offhandItemSnbt.trim();
        }

        public ArmorLoadout getArmor() {
            if (armor == null) {
                armor = new ArmorLoadout();
            }
            return armor;
        }

        public void setArmor(ArmorLoadout armor) {
            this.armor = armor == null ? new ArmorLoadout() : armor;
        }
    }

    public static final class ArmorLoadout {
        private String headItemSnbt = "";
        private String chestItemSnbt = "";
        private String legsItemSnbt = "";
        private String feetItemSnbt = "";

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
