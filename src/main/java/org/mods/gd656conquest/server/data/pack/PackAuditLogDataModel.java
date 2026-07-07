package org.mods.gd656conquest.server.data.pack;

import java.util.ArrayList;
import java.util.List;

public final class PackAuditLogDataModel {
    private List<PackAuditLogEntry> entries = new ArrayList<>();

    public List<PackAuditLogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<PackAuditLogEntry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }
}
