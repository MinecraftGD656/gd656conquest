package org.mods.gd656conquest.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomPosition;

import java.util.LinkedHashMap;
import java.util.Optional;

public final class CapturePointBlock extends Block {
    public static final BooleanProperty CENTER = BooleanProperty.create("center");
    private static final VoxelShape OUTLINE_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public CapturePointBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CENTER, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (ConquestBlocks.isMatchingCapturePointItem(state, context)) {
            return OUTLINE_SHAPE;
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (ConquestBlocks.isMatchingCapturePointItem(state, context)) {
            return OUTLINE_SHAPE;
        }
        return Shapes.empty();
    }

    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Item heldItem = player.getItemInHand(hand).getItem();
        if (!ConquestBlocks.isCapturePointForItem(state, heldItem)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockState next = state.setValue(CENTER, !state.getValue(CENTER));
        level.setBlock(pos, next, 3);
        if (level instanceof ServerLevel serverLevel) {
            Optional<String> captureCode = ConquestBlocks.resolveCaptureCode(next);
            captureCode.ifPresent(code -> applyCaptureCenterToggle(serverLevel, pos, code, next.getValue(CENTER)));
        }
        return InteractionResult.sidedSuccess(false);
    }

    private void applyCaptureCenterToggle(ServerLevel level, BlockPos pos, String code, boolean center) {
        if (level == null || pos == null || code == null || code.isBlank() || level.getServer() == null) {
            return;
        }
        String dimensionId = level.dimension().location().toString();
        String normalizedCode = code.trim().toUpperCase(java.util.Locale.ROOT);
        var roomDataHandler = ConquestDataManager.of(level.getServer()).roomDefinitions();
        var coreManager = RoomCoreRuntimeManager.of(level.getServer());
        RoomDefinition room = roomDataHandler.findByDimensionId(dimensionId).orElse(null);
        if (room == null) {
            return;
        }
        RoomCapturePointDefinition currentPoint = room.findConfiguredObjectivePoint(normalizedCode).orElse(null);
        if (currentPoint == null) {
            return;
        }
        RoomPosition currentCenter = currentPoint.centerPosition();
        boolean samePosition = currentCenter != null
            && currentCenter.x() == pos.getX()
            && Double.compare(currentCenter.y(), pos.getY()) == 0
            && currentCenter.z() == pos.getZ();
        if ((center && samePosition) || (!center && currentCenter == null) || (!center && !samePosition)) {
            return;
        }
        RoomCapturePointDefinition updatedPoint = currentPoint.withCenterPosition(center ? new RoomPosition(pos.getX(), pos.getY(), pos.getZ()) : null);
        RoomDefinition updated = RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())
            ? updateBreakthroughPoint(room, normalizedCode, updatedPoint)
            : updateConquestPoint(room, normalizedCode, updatedPoint);
        RoomDefinition persisted = roomDataHandler.saveDefinition(updated);
        coreManager.hotUpdateRoomDefinition(persisted, level.getServer().getTickCount());
    }

    private RoomDefinition updateConquestPoint(RoomDefinition room, String code, RoomCapturePointDefinition updatedPoint) {
        RoomCaptureConfig capture = room.conquestConfig().captureConfig();
        LinkedHashMap<String, RoomCapturePointDefinition> points = new LinkedHashMap<>(capture.points());
        points.put(code, updatedPoint);
        RoomCaptureConfig updatedCapture = new RoomCaptureConfig(
            points,
            capture.mapId(),
            capture.campADisplayName(),
            capture.campBDisplayName(),
            capture.drawDurationMinutes(),
            capture.fullCaptureEndSeconds(),
            capture.targetScore(),
            capture.captureScoreIntervalSeconds(),
            capture.scoreMethod(),
            capture.campAInitialTroops(),
            capture.campBInitialTroops(),
            capture.initialCaptureOwnership()
        );
        return new RoomDefinition(
            room.roomId(),
            room.displayName(),
            room.gameplayId(),
            room.lifecycleState(),
            room.commonConfig(),
            room.conquestConfig().withCaptureConfig(updatedCapture),
            room.breakthroughConfig()
        );
    }

    private RoomDefinition updateBreakthroughPoint(RoomDefinition room, String code, RoomCapturePointDefinition updatedPoint) {
        RoomBreakthroughConfig config = room.breakthroughConfig();
        LinkedHashMap<String, RoomCapturePointDefinition> points = new LinkedHashMap<>(config.points());
        points.put(code, updatedPoint);
        RoomBreakthroughConfig updatedConfig = new RoomBreakthroughConfig(
            points,
            config.mapId(),
            config.campADisplayName(),
            config.campBDisplayName(),
            config.stageCount(),
            config.campABaseBoundary(),
            config.campBBaseBoundary(),
            config.stageABoundary(),
            config.stageBBoundary(),
            config.stageCBoundary(),
            config.stageDBoundary(),
            config.stageEBoundary(),
            config.stageFBoundary(),
            config.campABaseSpawnGroup(),
            config.campBBaseSpawnGroup(),
            config.campABaseDeployIconPosition(),
            config.campBBaseDeployIconPosition(),
            config.stageADeployViewPosition(),
            config.stageBDeployViewPosition(),
            config.stageCDeployViewPosition(),
            config.stageDDeployViewPosition(),
            config.stageEDeployViewPosition(),
            config.stageFDeployViewPosition(),
            config.stageDurationMinutes(),
            config.defenderRetreatSeconds(),
            config.troopGainPerDefenderKill(),
            config.maxAttackTroopsAt12Players(),
            config.troopBalanceReference()
        );
        return new RoomDefinition(
            room.roomId(),
            room.displayName(),
            room.gameplayId(),
            room.lifecycleState(),
            room.commonConfig(),
            room.conquestConfig(),
            updatedConfig
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CENTER);
    }
}
