package org.mods.gd656conquest.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public class SquadDeployBeaconEntity extends AbstractSupportPropEntity {
    private static final int BEACON_LOOP_INTERVAL_TICKS = 3 * 20;
    private static final ResourceLocation BEACON_LOOP_SOUND_ID = ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.beacon_loop");
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(SquadDeployBeaconEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> ROOM_ID = SynchedEntityData.defineId(SquadDeployBeaconEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SQUAD_LABEL = SynchedEntityData.defineId(SquadDeployBeaconEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TEAM_TOKEN = SynchedEntityData.defineId(SquadDeployBeaconEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DEPLOY_REMAINING = SynchedEntityData.defineId(SquadDeployBeaconEntity.class, EntityDataSerializers.INT);

    public SquadDeployBeaconEntity(EntityType<? extends AbstractSupportPropEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER_UUID, Optional.empty());
        entityData.define(ROOM_ID, "");
        entityData.define(SQUAD_LABEL, "");
        entityData.define(TEAM_TOKEN, "N");
        entityData.define(DEPLOY_REMAINING, 6);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 0 && tickCount % BEACON_LOOP_INTERVAL_TICKS == 0) {
            level().playSound(
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    SoundEvent.createVariableRangeEvent(BEACON_LOOP_SOUND_ID),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F
            );
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUuid();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("RoomId", getRoomId());
        tag.putString("SquadLabel", getSquadLabel());
        tag.putString("TeamToken", getTeamToken());
        tag.putInt("DeployRemaining", getDeployRemaining());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            setOwnerUuid(tag.getUUID("Owner"));
        }
        setRoomId(tag.getString("RoomId"));
        setSquadLabel(tag.getString("SquadLabel"));
        setTeamToken(tag.getString("TeamToken"));
        setDeployRemaining(tag.getInt("DeployRemaining"));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUuid(UUID ownerUuid) {
        entityData.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    public String getRoomId() {
        return entityData.get(ROOM_ID);
    }

    public void setRoomId(String roomId) {
        entityData.set(ROOM_ID, roomId == null ? "" : roomId);
    }

    public String getSquadLabel() {
        return entityData.get(SQUAD_LABEL);
    }

    public void setSquadLabel(String squadLabel) {
        entityData.set(SQUAD_LABEL, squadLabel == null ? "" : squadLabel);
    }

    public String getTeamToken() {
        return entityData.get(TEAM_TOKEN);
    }

    public void setTeamToken(String teamToken) {
        entityData.set(TEAM_TOKEN, teamToken == null ? "N" : teamToken);
    }

    public int getDeployRemaining() {
        return entityData.get(DEPLOY_REMAINING);
    }

    public void setDeployRemaining(int remaining) {
        entityData.set(DEPLOY_REMAINING, Math.max(0, remaining));
    }

    public int consumeOneDeployAndGetRemaining() {
        int left = Math.max(0, getDeployRemaining() - 1);
        setDeployRemaining(left);
        return left;
    }

    public boolean isValidForRoom(ServerLevel level, String roomId) {
        return level != null && roomId != null && !roomId.isBlank() && roomId.equalsIgnoreCase(getRoomId());
    }

}
