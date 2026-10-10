package org.mods.gd656conquest.server.compat.ragdoll;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collision-safe return from a body; this stage retains the original standing revive mechanism. */
public final class ReviveBodyPlacement {
    private ReviveBodyPlacement() {
    }

    public static Vec3 safePosition(ServerPlayer player, Vec3 anchor, Vec3 fallback) {
        if (safe(player, anchor)) return anchor;
        BlockPos base = BlockPos.containing(anchor.x, anchor.y, anchor.z);
        for (int radius = 0; radius <= 3; radius++) for (int dy = 1; dy >= -3; dy--)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                Vec3 point = new Vec3(base.getX() + dx + .5, base.getY() + dy, base.getZ() + dz + .5);
                if (safe(player, point)) return point;
            }
        if (safe(player, fallback)) return fallback;
        BlockPos origin = BlockPos.containing(fallback.x, fallback.y, fallback.z);
        for (int radius = 4; radius <= 8; radius++) for (int dy = 3; dy >= -8; dy--)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                Vec3 point = new Vec3(origin.getX() + dx + .5, origin.getY() + dy, origin.getZ() + dz + .5);
                if (safe(player, point)) return point;
            }
        return player.position();
    }

    private static boolean safe(ServerPlayer player, Vec3 feet) {
        var level = player.serverLevel();
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1e-5, box.maxY - 1e-5, box.maxZ - 1e-5);
        for (int x = min.getX(); x <= max.getX(); x++)
            for (int y = min.getY(); y <= max.getY(); y++)
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos block = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(block) || !level.getBlockState(block).getFluidState().isEmpty()) return false;
                }
        AABB support = new AABB(box.minX, box.minY - .12, box.minZ, box.maxX, box.minY, box.maxZ);
        return level.noCollision(box) && !level.noCollision(support);
    }
}
