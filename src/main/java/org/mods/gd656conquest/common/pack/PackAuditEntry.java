package org.mods.gd656conquest.common.pack;

import java.util.Locale;
import java.util.Objects;

public final class PackAuditEntry {
    private String type = "";
    private String fileName = "";
    private String sha256 = "";
    private long size;
    private String sourceKind = "";

    public PackAuditEntry() {
    }

    public PackAuditEntry(String type, String fileName, String sha256, long size, String sourceKind) {
        this.type = safe(type);
        this.fileName = safe(fileName);
        this.sha256 = safe(sha256);
        this.size = Math.max(0L, size);
        this.sourceKind = safe(sourceKind);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = safe(type);
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = safe(fileName);
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = safe(sha256);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = Math.max(0L, size);
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = safe(sourceKind);
    }

    public boolean isValid() {
        return !type.isBlank()
            && !fileName.isBlank()
            && !sha256.isBlank()
            && !sourceKind.isBlank();
    }

    public String identityKey() {
        return normalize(type) + "|" + normalize(fileName) + "|" + normalize(sha256);
    }

    public String displayKey() {
        return "[" + safe(type) + "] " + safe(fileName) + " [" + safe(sha256) + "]";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PackAuditEntry other)) {
            return false;
        }
        return size == other.size
            && Objects.equals(type, other.type)
            && Objects.equals(fileName, other.fileName)
            && Objects.equals(sha256, other.sha256)
            && Objects.equals(sourceKind, other.sourceKind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fileName, sha256, size, sourceKind);
    }
}
