package org.mods.gd656conquest.server.room.runtime.shelling;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveShellingSequence {
    public org.mods.gd656conquest.common.match.ShellingScopeLedger.Scope scope;
    public net.minecraft.server.level.ServerPlayer sourceOwner;
    public final UUID sequenceId;
    public final ShellingSequenceKind kind;
    public final String roomId;
    public final String shellingId;
    public final UUID ownerId;
    public final String ownerName;
    public final RoomTeamId teamId;
    public final ResourceKey<Level> dimension;
    public final Vec3 targetCenter;
    public final TimedShellingConfigSnapshot shellingConfig;
    public final ShellingSequenceProfile profile;
    public final long startTick;
    public final long nominalFinishTick;
    public final long completionTick;
    public final long intervalTicks;
    public final int totalShellCount;
    public final Set<UUID> loopRecipients = new LinkedHashSet<>();
    public final Set<TrackedProjectile> trackedProjectiles = new LinkedHashSet<>();
    public long nextEventTick;
    public long nextShellSpawnTick;
    public int alarmsPlayed;
    public int startedEventCount;
    public int spawnedShellCount;
    public int lastCountdownAnnouncedSecond = -1;

    private ActiveShellingSequence(
        UUID sequenceId,
        ShellingSequenceKind kind,
        String roomId,
        String shellingId,
        UUID ownerId,
        String ownerName,
        RoomTeamId teamId,
        ResourceKey<Level> dimension,
        Vec3 targetCenter,
        TimedShellingConfigSnapshot shellingConfig,
        ShellingSequenceProfile profile,
        long startTick,
        long nominalFinishTick,
        long completionTick,
        long intervalTicks,
        int totalShellCount,
        long nextEventTick,
        long nextShellSpawnTick
    ) {
        this.sequenceId = sequenceId == null ? UUID.randomUUID() : sequenceId;
        this.kind = kind == null ? ShellingSequenceKind.MISSILE : kind;
        this.roomId = roomId == null ? "" : roomId;
        this.shellingId = shellingId == null ? "" : shellingId;
        this.ownerId = ownerId;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.teamId = teamId;
        this.dimension = dimension;
        this.targetCenter = targetCenter == null ? Vec3.ZERO : targetCenter;
        this.shellingConfig = shellingConfig;
        this.profile = profile;
        this.startTick = startTick;
        this.nominalFinishTick = nominalFinishTick;
        this.completionTick = completionTick;
        this.intervalTicks = Math.max(1L, intervalTicks);
        this.totalShellCount = Math.max(1, totalShellCount);
        this.nextEventTick = nextEventTick;
        this.nextShellSpawnTick = nextShellSpawnTick;
    }

    public static ActiveShellingSequence create(
        String roomId,
        String shellingId,
        UUID ownerId,
        String ownerName,
        RoomTeamId teamId,
        ResourceKey<Level> dimension,
        Vec3 targetCenter,
        TimedShellingConfigSnapshot shellingConfig,
        ShellingSequenceProfile profile,
        long startTick,
        int totalShellCount
    ) {
        long intervalTicks = shellingConfig == null ? 20L : shellingConfig.intervalTicks();
        long spawnDelayTicks = profile == null ? 0L : Math.max(0L, profile.spawnDelayTicks());
        long minimumDurationTicks = profile == null ? 0L : Math.max(0L, profile.minimumDurationTicks());
        long nominalFinishTick = startTick + minimumDurationTicks;
        long configuredEndTick = startTick + (shellingConfig == null ? 0L : shellingConfig.durationTicks());
        long lastSpawnTick = startTick + spawnDelayTicks + Math.max(0L, totalShellCount - 1L) * intervalTicks;
        long completionTick = Math.max(Math.max(nominalFinishTick, configuredEndTick), lastSpawnTick);
        return new ActiveShellingSequence(
            UUID.randomUUID(),
            profile == null ? ShellingSequenceKind.MISSILE : profile.kind(),
            roomId,
            shellingId,
            ownerId,
            ownerName,
            teamId,
            dimension,
            targetCenter,
            shellingConfig,
            profile,
            startTick,
            nominalFinishTick,
            completionTick,
            intervalTicks,
            totalShellCount,
            startTick,
            startTick + spawnDelayTicks
        );
    }

    public record TrackedProjectile(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, UUID projectileId) {
    }
}
