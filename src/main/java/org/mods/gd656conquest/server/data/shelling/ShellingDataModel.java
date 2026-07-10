package org.mods.gd656conquest.server.data.shelling;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ShellingDataModel {
    private Integer version = 2;
    private Map<String, ShellingEntry> shellingsById = new LinkedHashMap<>();

    public Integer getVersion() {
        return version == null || version <= 0 ? 2 : version;
    }

    public void setVersion(Integer version) {
        this.version = version == null || version <= 0 ? 2 : version;
    }

    public Map<String, ShellingEntry> getShellingsById() {
        if (shellingsById == null) {
            shellingsById = new LinkedHashMap<>();
        }
        return shellingsById;
    }

    public void setShellingsById(Map<String, ShellingEntry> shellingsById) {
        this.shellingsById = shellingsById == null ? new LinkedHashMap<>() : new LinkedHashMap<>(shellingsById);
    }

    public static final class ShellingEntry {
        private String id = "";
        private Double shellHeight = 0.0D;
        private Double durationSeconds = 0.0D;
        private Double intervalSeconds = 0.0D;
        private Double radius = 0.0D;
        private Integer cost = 0;
        private Float pitch = 0.0F;
        private Float yaw = 0.0F;
        private String gunItemSnbt = "";
        private String gunId = "";
        private String gunDisplayId = "";
        private String gunDisplayName = "";
        private String displayName = "";
        private String impactType = ShellingImpactType.EMPTY.id();

        public String getId() {
            return id == null ? "" : id;
        }

        public void setId(String id) {
            this.id = id == null ? "" : id.trim();
        }

        public Double getShellHeight() {
            return sanitizeNonNegativeDouble(shellHeight);
        }

        public void setShellHeight(Double shellHeight) {
            this.shellHeight = sanitizeNonNegativeDouble(shellHeight);
        }

        public Double getDurationSeconds() {
            return sanitizePositiveDouble(durationSeconds);
        }

        public void setDurationSeconds(Double durationSeconds) {
            this.durationSeconds = sanitizePositiveDouble(durationSeconds);
        }

        public Double getIntervalSeconds() {
            return sanitizePositiveDouble(intervalSeconds);
        }

        public void setIntervalSeconds(Double intervalSeconds) {
            this.intervalSeconds = sanitizePositiveDouble(intervalSeconds);
        }

        public Double getRadius() {
            return sanitizeNonNegativeDouble(radius);
        }

        public void setRadius(Double radius) {
            this.radius = sanitizeNonNegativeDouble(radius);
        }

        public Integer getCost() {
            return cost == null || cost < 0 ? 0 : cost;
        }

        public void setCost(Integer cost) {
            this.cost = cost == null || cost < 0 ? 0 : cost;
        }

        public Float getPitch() {
            return sanitizeFiniteFloat(pitch);
        }

        public void setPitch(Float pitch) {
            this.pitch = sanitizeFiniteFloat(pitch);
        }

        public Float getYaw() {
            return sanitizeFiniteFloat(yaw);
        }

        public void setYaw(Float yaw) {
            this.yaw = sanitizeFiniteFloat(yaw);
        }

        public String getGunItemSnbt() {
            return gunItemSnbt == null ? "" : gunItemSnbt;
        }

        public void setGunItemSnbt(String gunItemSnbt) {
            this.gunItemSnbt = gunItemSnbt == null ? "" : gunItemSnbt.trim();
        }

        public String getGunId() {
            return gunId == null ? "" : gunId;
        }

        public void setGunId(String gunId) {
            this.gunId = gunId == null ? "" : gunId.trim();
        }

        public String getGunDisplayId() {
            return gunDisplayId == null ? "" : gunDisplayId;
        }

        public void setGunDisplayId(String gunDisplayId) {
            this.gunDisplayId = gunDisplayId == null ? "" : gunDisplayId.trim();
        }

        public String getGunDisplayName() {
            return gunDisplayName == null ? "" : gunDisplayName;
        }

        public void setGunDisplayName(String gunDisplayName) {
            this.gunDisplayName = gunDisplayName == null ? "" : gunDisplayName.trim();
        }

        public String getDisplayName() {
            return displayName == null ? "" : displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName == null ? "" : displayName.trim();
        }

        public String getImpactType() {
            return normalizeImpactType(impactType);
        }

        public void setImpactType(String impactType) {
            this.impactType = normalizeImpactType(impactType);
        }

        public ShellingImpactType resolveImpactType() {
            String normalized = getImpactType();
            if (normalized.isBlank()) {
                return ShellingImpactType.EMPTY;
            }
            return ShellingImpactType.require(normalized);
        }

        private static double sanitizePositiveDouble(Double value) {
            return value != null && Double.isFinite(value) && value > 0.0D ? value : 0.0D;
        }

        private static double sanitizeNonNegativeDouble(Double value) {
            return value != null && Double.isFinite(value) && value >= 0.0D ? value : 0.0D;
        }

        private static float sanitizeFiniteFloat(Float value) {
            return value != null && Float.isFinite(value) ? value : 0.0F;
        }

        private static String normalizeImpactType(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
