package org.mods.gd656conquest.common.match;

import java.util.function.BiFunction;
import net.minecraft.world.phys.Vec3;

/** A short, elevated third-person shot aimed at the retained actor. */
public final class BattalionIntroCamera {
    public record Shot(Vec3 position, float yaw, float pitch) {}

    private BattalionIntroCamera() {}

    public static Shot resolve(Vec3 focus, float yaw, BiFunction<Vec3, Vec3, Vec3> clip) {
        double angle = Math.toRadians(yaw);
        Vec3 desired = focus.add(Math.sin(angle) * 3.5, .85, -Math.cos(angle) * 3.5);
        Vec3 position = clip.apply(focus, desired);
        Vec3 direction = focus.subtract(position);
        double horizontal = Math.hypot(direction.x, direction.z);
        float pitch = (float)-Math.toDegrees(Math.atan2(direction.y, horizontal));
        return new Shot(position, yaw, pitch);
    }
}
