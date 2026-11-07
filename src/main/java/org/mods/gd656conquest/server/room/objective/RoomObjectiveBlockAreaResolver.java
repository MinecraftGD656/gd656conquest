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
        var level=player.serverLevel();var position=new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = scanBounds.minX(); x <= scanBounds.maxX(); x++) {
            for (int y = scanBounds.minY(); y <= scanBounds.maxY(); y++) {
                for (int z = scanBounds.minZ(); z <= scanBounds.maxZ(); z++) {
                    var state = level.getBlockState(position.set(x,y,z));
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
                    double volume=overlapVolume(box,x,y,z);
                    if (volume > 0.0D) {
                        overlapByCode.merge(code, volume, Double::sum);
                    }
                }
            }
        }
        return bestCode(overlapByCode);
    }
    static String bestCode(Map<String,Double> overlapByCode) {
        String bestCode = null;
        double bestVolume = 0.0D;
        for (Map.Entry<String, Double> entry : overlapByCode.entrySet()) {
            if (entry.getValue() > bestVolume || entry.getValue() == bestVolume && bestCode != null && entry.getKey().compareTo(bestCode) < 0) {
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
        var level=player.serverLevel();var position=new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = scanBounds.minX(); x <= scanBounds.maxX(); x++) {
            for (int y = scanBounds.minY(); y <= scanBounds.maxY(); y++) {
                for (int z = scanBounds.minZ(); z <= scanBounds.maxZ(); z++) {
                    var state = level.getBlockState(position.set(x,y,z));
                    if (!matcher.test(state)) {
                        continue;
                    }
                    if (overlapVolume(box,x,y,z)>0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static double overlapVolume(AABB box,int x,int y,int z) {
        double dx=Math.min(box.maxX,x+1d)-Math.max(box.minX,x);
        double dy=Math.min(box.maxY,y+1d)-Math.max(box.minY,y);
        double dz=Math.min(box.maxZ,z+1d)-Math.max(box.minZ,z);
        return dx>0&&dy>0&&dz>0?dx*dy*dz:0;
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
