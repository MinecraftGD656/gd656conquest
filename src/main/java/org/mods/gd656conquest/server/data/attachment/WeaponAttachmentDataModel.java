package org.mods.gd656conquest.server.data.attachment;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WeaponAttachmentDataModel {
    private Map<String, WeaponAttachmentEntry> attachmentById = new LinkedHashMap<>();

    public Map<String, WeaponAttachmentEntry> getAttachmentById() {
        if (attachmentById == null) {
            attachmentById = new LinkedHashMap<>();
        }
        return attachmentById;
    }

    public void setAttachmentById(Map<String, WeaponAttachmentEntry> attachmentById) {
        this.attachmentById = attachmentById;
    }

    public static final class WeaponAttachmentEntry {
        private String id;
        private String displayName;
        private String attachmentType;
        private int unlockLevel;
        private String itemSnbt;
        private String futureRequirement;

        public String getId() {
            return id == null ? "" : id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName == null ? "" : displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getAttachmentType() {
            return attachmentType == null ? "" : attachmentType;
        }

        public void setAttachmentType(String attachmentType) {
            this.attachmentType = attachmentType;
        }

        public int getUnlockLevel() {
            return Math.max(0, unlockLevel);
        }

        public void setUnlockLevel(int unlockLevel) {
            this.unlockLevel = Math.max(0, unlockLevel);
        }

        public String getItemSnbt() {
            return itemSnbt == null ? "" : itemSnbt;
        }

        public void setItemSnbt(String itemSnbt) {
            this.itemSnbt = itemSnbt;
        }

        public String getFutureRequirement() {
            return futureRequirement == null ? "" : futureRequirement;
        }

        public void setFutureRequirement(String futureRequirement) {
            this.futureRequirement = futureRequirement;
        }
    }
}
