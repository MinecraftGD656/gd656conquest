package org.mods.gd656conquest.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public class GroundSensorEntity extends AbstractSupportPropEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
        SynchedEntityData.defineId(GroundSensorEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> ROOM_ID =
        SynchedEntityData.defineId(GroundSensorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SQUAD_LABEL =
        SynchedEntityData.defineId(GroundSensorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TEAM_TOKEN =
        SynchedEntityData.defineId(GroundSensorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> ARMED =
        SynchedEntityData.defineId(GroundSensorEntity.class, EntityDataSerializers.BOOLEAN);

    public GroundSensorEntity(EntityType<? extends AbstractSupportPropEntity> entityType, Level level) {
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
        entityData.define(ARMED, true);
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
        tag.putBoolean("Armed", isArmed());
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
        setArmed(!tag.contains("Armed") || tag.getBoolean("Armed"));
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

    public boolean isArmed() {
        return entityData.get(ARMED);
    }

    public void setArmed(boolean armed) {
        entityData.set(ARMED, armed);
    }

    public boolean isValidForRoom(ServerLevel level, String roomId) {
        return level != null && roomId != null && !roomId.isBlank() && roomId.equalsIgnoreCase(getRoomId());
    }
}
