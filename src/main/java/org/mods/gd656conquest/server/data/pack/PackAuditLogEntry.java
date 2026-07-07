package org.mods.gd656conquest.server.data.pack;

import org.mods.gd656conquest.common.pack.PackAuditEntry;

import java.util.ArrayList;
import java.util.List;

public final class PackAuditLogEntry {
    private String playerName = "";
    private String playerUuid = "";
    private String kickTime = "";
    private String trigger = "";
    private String reason = "";
    private String disconnectMessage = "";
    private List<PackAuditEntry> illegalEntries = new ArrayList<>();
    private List<PackAuditEntry> missingWhitelistEntries = new ArrayList<>();

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName == null ? "" : playerName;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid == null ? "" : playerUuid;
    }

    public String getKickTime() {
        return kickTime;
    }

    public void setKickTime(String kickTime) {
        this.kickTime = kickTime == null ? "" : kickTime;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger == null ? "" : trigger;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public String getDisconnectMessage() {
        return disconnectMessage;
    }

    public void setDisconnectMessage(String disconnectMessage) {
        this.disconnectMessage = disconnectMessage == null ? "" : disconnectMessage;
    }

    public List<PackAuditEntry> getIllegalEntries() {
        return illegalEntries;
    }

    public void setIllegalEntries(List<PackAuditEntry> illegalEntries) {
        this.illegalEntries = illegalEntries == null ? new ArrayList<>() : new ArrayList<>(illegalEntries);
    }

    public List<PackAuditEntry> getMissingWhitelistEntries() {
        return missingWhitelistEntries;
    }

    public void setMissingWhitelistEntries(List<PackAuditEntry> missingWhitelistEntries) {
        this.missingWhitelistEntries = missingWhitelistEntries == null ? new ArrayList<>() : new ArrayList<>(missingWhitelistEntries);
    }
}
