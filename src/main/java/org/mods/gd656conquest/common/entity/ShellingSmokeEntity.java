package org.mods.gd656conquest.common.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

public class ShellingSmokeEntity extends Entity {
    private int durationTicks = 600; // 默认 30 秒

    public ShellingSmokeEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setDuration(int ticks) {
        this.durationTicks = ticks;
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

        // 客户端每 tick 喷射 3D 浓烟
        // 使用与 LR 完全一致的 3D 三角分布算法，保证体积感
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        net.minecraft.core.particles.ParticleType<?> lrSmoke = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("lrtactical", "smoke_cloud"));
        net.minecraft.core.particles.ParticleOptions particle = (lrSmoke instanceof net.minecraft.core.particles.ParticleOptions) 
            ? (net.minecraft.core.particles.ParticleOptions) lrSmoke 
            : ParticleTypes.CAMPFIRE_COSY_SMOKE;

        for (int i = 0; i < 16; i++) {
            double offsetX = this.random.triangle(0, 5.5);
            double offsetY = this.random.triangle(0, 4.5);
            double offsetZ = this.random.triangle(0, 5.5);

            this.level().addParticle(particle, true, x + offsetX, y + offsetY, z + offsetZ, 0.0D, 0.0D, 0.0D);
        }
    }
}