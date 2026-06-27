package org.mods.gd656conquest.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractSupportPropEntity extends PathfinderMob {
    protected AbstractSupportPropEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
        setNoAi(true);
        noPhysics = true;
        blocksBuilding = false;
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide && isInWall()) {
            Vec3 safe = findNearbySafePosition();
            if (safe != null) {
                setPos(safe.x, safe.y, safe.z);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!level().isClientSide && deathTime >= 1) {
            remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source != null && source.is(DamageTypes.IN_WALL)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    private Vec3 findNearbySafePosition() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        BlockPos origin = BlockPos.containing(getX(), getY(), getZ());
        for (int dy = 0; dy <= 6; dy++) {
            for (int radius = 0; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos feet = origin.offset(dx, dy, dz);
                        if (!isOpenBlock(serverLevel, feet) || !isOpenBlock(serverLevel, feet.above())) {
                            continue;
                        }
                        return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                    }
                }
            }
        }
        return null;
    }

    private boolean isOpenBlock(ServerLevel level, BlockPos pos) {
        return level != null
            && pos != null
            && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getFluidState(pos).isEmpty();
    }
}
