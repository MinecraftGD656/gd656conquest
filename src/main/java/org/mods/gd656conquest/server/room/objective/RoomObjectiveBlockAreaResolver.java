package org.mods.gd656conquest.server.room.objective;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class RoomObjectiveBlockAreaResolver implements RoomObjectiveAreaResolver {
    @Override
    public String resolvePlayerInsideObjectiveCode(ServerPlayer player, RoomCaptureConfig config, Set<String> allowedCodes) {
        if (config == null) {
            return null;
        }
        return resolvePlayerInsideObjectiveCodeByBlocks(player, Objects.requireNonNullElseGet(allowedCodes, () -> RoomObjectiveSupport.activeCodes(config)));
    }

    private String resolvePlayerInsideObjectiveCodeByBlocks(ServerPlayer player, Set<String> allowedCodes) {
        if (player == null || player.serverLevel() == null) {
            return null;
        }
        return resolveObjectiveCodeInScanBox(player, allowedCodes, player.getBoundingBox().inflate(0.001D));
    }

    public boolean isPlayerInsideOutOfBoundsBlock(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return false;
        }
        return isMatchingBlockInScanBox(player, player.getBoundingBox().inflate(0.001D), ConquestBlocks::isOutOfBoundsBlock);
    }

    private String resolveObjectiveCodeInScanBox(ServerPlayer player, Set<String> allowedCodes, AABB box) {
        if (player == null || player.serverLevel() == null || box == null) {
            return null;
        }
        BlockScanBounds scanBounds = scanBounds(box);
        Map<String, Double> overlapByCode = new HashMap<>();
        for (int x = scanBounds.minX(); x <= scanBounds.maxX(); x++) {
            for (int y = scanBounds.minY(); y <= scanBounds.maxY(); y++) {
                for (int z = scanBounds.minZ(); z <= scanBounds.maxZ(); z++) {
                    var state = player.serverLevel().getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                    if (!ConquestBlocks.isCapturePoint(state)) {
                        continue;
                    }
                    Optional<String> codeOptional = ConquestBlocks.resolveCaptureCode(state);
                    if (codeOptional.isEmpty()) {
                        continue;
                    }
                    String code = RoomObjectiveSupport.normalizeCode(codeOptional.get());
                    if (!allowedCodes.contains(code)) {
                        continue;
                    }
                    AABB pointBox = new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
                    if (!pointBox.intersects(box)) {
                        continue;
                    }
                    AABB intersection = pointBox.intersect(box);
                    double volume = intersection.getXsize() * intersection.getYsize() * intersection.getZsize();
                    if (volume > 0.0D) {
                        overlapByCode.merge(code, volume, Double::sum);
                    }
                }
            }
        }
        String bestCode = null;
        double bestVolume = 0.0D;
        for (Map.Entry<String, Double> entry : overlapByCode.entrySet()) {
            if (entry.getValue() > bestVolume) {
                bestVolume = entry.getValue();
                bestCode = entry.getKey();
            }
        }
        return bestCode;
    }

    private boolean isMatchingBlockInScanBox(ServerPlayer player, AABB box, Predicate<net.minecraft.world.level.block.state.BlockState> matcher) {
        if (player == null || player.serverLevel() == null || box == null || matcher == null) {
            return false;
        }
        BlockScanBounds scanBounds = scanBounds(box);
        for (int x = scanBounds.minX(); x <= scanBounds.maxX(); x++) {
            for (int y = scanBounds.minY(); y <= scanBounds.maxY(); y++) {
                for (int z = scanBounds.minZ(); z <= scanBounds.maxZ(); z++) {
                    var state = player.serverLevel().getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                    if (!matcher.test(state)) {
                        continue;
                    }
                    AABB blockBox = new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
                    if (blockBox.intersects(box)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private BlockScanBounds scanBounds(AABB box) {
        return new BlockScanBounds(
            Mth.floor(box.minX),
            Mth.floor(box.minY),
            Mth.floor(box.minZ),
            Mth.ceil(box.maxX) - 1,
            Mth.ceil(box.maxY) - 1,
            Mth.ceil(box.maxZ) - 1
        );
    }

    private record BlockScanBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
