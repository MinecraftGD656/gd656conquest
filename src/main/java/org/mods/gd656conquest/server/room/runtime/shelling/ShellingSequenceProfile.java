package org.mods.gd656conquest.server.room.runtime.shelling;

import java.util.List;

public record ShellingSequenceProfile(
    ShellingSequenceKind kind,
    String displayName,
    String loopSoundId,
    double loopRadius,
    float loopMaxVolume,
    List<String> openingSoundIds,
    float openingVolume,
    List<String> eventLaunchSoundIds,
    float eventLaunchVolume,
    long spawnDelayTicks,
    long minimumDurationTicks,
    double incomingRadius,
    float incomingMaxVolume,
    List<String> incomingSoundIds,
    String actionbarText,
    String resultMessagePrefix,
    String projectileNoun
) {
}
