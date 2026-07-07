package org.mods.gd656conquest.server.data.pack;

import org.mods.gd656conquest.common.pack.PackAuditEntry;

import java.util.ArrayList;
import java.util.List;

public final class PackWhitelistDataModel {
    private List<PackAuditEntry> entries = new ArrayList<>();

    public List<PackAuditEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<PackAuditEntry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }
}
