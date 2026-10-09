package org.mods.gd656conquest.server.room.runtime.shelling;

import org.mods.gd656conquest.server.data.shelling.ShellingDataModel;
import org.mods.gd656conquest.server.data.shelling.ShellingImpactType;

public record TimedShellingConfigSnapshot(
    String gunId,
    String gunItemSnbt,
    String displayName,
    double durationSeconds,
    double intervalSeconds,
    double radius,
    double shellHeight,
    float pitch,
    float yaw,
    String impactTypeId
) {
    public static TimedShellingConfigSnapshot fromEntry(ShellingDataModel.ShellingEntry entry) {
        return new TimedShellingConfigSnapshot(
            entry == null ? "" : entry.getGunId(),
            entry == null ? "" : entry.getGunItemSnbt(),
            entry == null ? "" : entry.getDisplayName(),
            entry == null ? 0.0D : entry.getDurationSeconds(),
            entry == null ? 0.0D : entry.getIntervalSeconds(),
            entry == null ? 0.0D : entry.getRadius(),
            entry == null ? 0.0D : entry.getShellHeight(),
            entry == null ? 0.0F : entry.getPitch(),
            entry == null ? 0.0F : entry.getYaw(),
            entry == null ? ShellingImpactType.MISSILE.id() : entry.getImpactType()
        );
    }

    public long durationTicks() {
        return Math.max(1L, Math.round(durationSeconds * 20.0D));
    }

    public long intervalTicks() {
        return Math.max(1L, Math.round(intervalSeconds * 20.0D));
    }

    public ShellingDataModel.ShellingEntry toEntry() {
        ShellingDataModel.ShellingEntry entry = new ShellingDataModel.ShellingEntry();
        entry.setGunId(gunId);
        entry.setGunItemSnbt(gunItemSnbt);
        entry.setDisplayName(displayName);
        entry.setDurationSeconds(durationSeconds);
        entry.setIntervalSeconds(intervalSeconds);
        entry.setRadius(radius);
        entry.setShellHeight(shellHeight);
        entry.setPitch(pitch);
        entry.setYaw(yaw);
        entry.setImpactType(impactTypeId);
        return entry;
    }
}
