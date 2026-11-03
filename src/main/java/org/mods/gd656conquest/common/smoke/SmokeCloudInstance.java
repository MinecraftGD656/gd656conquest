package org.mods.gd656conquest.common.smoke;

import java.util.UUID;

/**
 * Server-authoritative description of a stationary LR-style smoke cloud.
 */
public record SmokeCloudInstance(
    UUID id,
    String dimensionId,
    double x,
    double y,
    double z,
    double radiusXZ,
    double radiusY,
    long seed,
    long startTick,
    long endTick
) {
}
