package org.mods.gd656conquest.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public class MedicalBoxEntity extends AbstractSupportPropEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(MedicalBoxEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> USE_REMAINING = SynchedEntityData.defineId(MedicalBoxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PERMANENT_SUPPORT = SynchedEntityData.defineId(MedicalBoxEntity.class, EntityDataSerializers.BOOLEAN);

    public MedicalBoxEntity(EntityType<? extends AbstractSupportPropEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER_UUID, Optional.empty());
        entityData.define(USE_REMAINING, 16);
        entityData.define(PERMANENT_SUPPORT, false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isPermanentSupport()) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUuid();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("UseRemaining", getUseRemaining());
        tag.putBoolean("PermanentSupport", isPermanentSupport());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            setOwnerUuid(tag.getUUID("Owner"));
        }
        setUseRemaining(tag.getInt("UseRemaining"));
        setPermanentSupport(tag.getBoolean("PermanentSupport"));
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUuid(UUID ownerUuid) {
        entityData.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    public int getUseRemaining() {
        return entityData.get(USE_REMAINING);
    }

    public void setUseRemaining(int remaining) {
        entityData.set(USE_REMAINING, Math.max(0, remaining));
    }

    public int consumeOneAndGetRemaining() {
        if (isPermanentSupport()) {
            return Integer.MAX_VALUE;
        }
        int left = Math.max(0, getUseRemaining() - 1);
        setUseRemaining(left);
        return left;
    }

    public boolean isPermanentSupport() {
        return entityData.get(PERMANENT_SUPPORT);
    }

    public void setPermanentSupport(boolean permanentSupport) {
        entityData.set(PERMANENT_SUPPORT, permanentSupport);
    }

}
