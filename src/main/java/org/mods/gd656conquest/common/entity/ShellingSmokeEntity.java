package org.mods.gd656conquest.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class ShellingSmokeEntity extends Entity {
    private int durationTicks = 600; // 默认 30 秒

    public ShellingSmokeEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setDuration(int ticks) {
        this.durationTicks = ticks;
    }

    public int remainingTicks() {
        return Math.max(1, this.durationTicks - this.tickCount);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("DurationTicks")) {
            this.durationTicks = tag.getInt("DurationTicks");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("DurationTicks", this.durationTicks);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (this.tickCount >= this.durationTicks) {
                this.discard();
            }
            return;
        }

        // Smoke is rendered by the conquest LOD renderer from server-synced metadata.
    }
}
