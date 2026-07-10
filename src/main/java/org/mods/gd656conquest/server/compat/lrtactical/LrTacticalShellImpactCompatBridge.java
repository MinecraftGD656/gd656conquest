package org.mods.gd656conquest.server.compat.lrtactical;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.mods.gd656conquest.server.data.shelling.ShellingImpactType;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.lang.reflect.Constructor;

public final class LrTacticalShellImpactCompatBridge {
    private static final String LRTACTICAL_MOD_ID = "lrtactical";
    private static final ResourceLocation LRTACTICAL_FLAMMABLE_ID = new ResourceLocation("lrtactical", "flammable");
    private static final Bridge BRIDGE = new Bridge();

    private LrTacticalShellImpactCompatBridge() {
    }

    public static boolean spawnImpactEffect(ServerLevel level, ServerPlayer owner, Vec3 impactPos, ShellingImpactType impactType) {
        if (level == null || owner == null || impactPos == null || impactType == null || impactType.isEmpty()) {
            return false;
        }
        if (!ModList.get().isLoaded(LRTACTICAL_MOD_ID)) {
            return false;
        }
        String unavailableReason = BRIDGE.ensureAvailable();
        if (unavailableReason != null) {
            ServerLogger.failure("LR 炮击落点兼容桥不可用: %s", unavailableReason);
            return false;
        }
        try {
            return switch (impactType) {
                case SMOKESHELL -> BRIDGE.spawnSmoke(level, owner, impactPos);
                case FLAMESHELL -> BRIDGE.spawnFlame(level, owner, impactPos);
                case EMPTY, LIGHTSHELL, MEDIUMSHELL, HEAVYSHELL, MISSILE -> false;
            };
        } catch (Exception exception) {
            ServerLogger.failure("生成 LR 炮击落点效果失败 type=%s error=%s", impactType.id(), exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    private static final class Bridge {
        private boolean resolved;
        private String unavailableReason;
        private Constructor<?> flameCloudConstructor;

        String ensureAvailable() {
            if (resolved) {
                return unavailableReason;
            }
            resolved = true;
            try {
                Class<?> flameCloudClass = Class.forName("me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity");
                flameCloudConstructor = flameCloudClass.getConstructor(net.minecraft.world.level.Level.class, double.class, double.class, double.class);
            } catch (Exception exception) {
                unavailableReason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            return unavailableReason;
        }

        private Vec3 findGround(ServerLevel level, Vec3 pos) {
            net.minecraft.world.phys.Vec3 start = pos.add(0, 2.0, 0);
            net.minecraft.world.phys.Vec3 end = pos.add(0, -64.0, 0);
            net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null
            ));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                return hit.getLocation();
            }
            return pos;
        }

        boolean spawnSmoke(ServerLevel level, ServerPlayer owner, Vec3 impactPos) throws Exception {
            Vec3 groundPos = findGround(level, impactPos);
            org.mods.gd656conquest.common.entity.ShellingSmokeEntity smoke = org.mods.gd656conquest.common.entity.ConquestEntities.SHELLING_SMOKE.get().create(level);
            if (smoke == null) {
                return false;
            }
            smoke.setPos(groundPos.x, groundPos.y, groundPos.z);
            smoke.setDuration(600); // 30 秒
            return level.addFreshEntity(smoke);
        }

        boolean spawnFlame(ServerLevel level, ServerPlayer owner, Vec3 impactPos) throws Exception {
            Vec3 groundPos = findGround(level, impactPos);
            Object created = flameCloudConstructor.newInstance(level, groundPos.x, groundPos.y, groundPos.z);
            if (!(created instanceof AreaEffectCloud cloud)) {
                return false;
            }
            cloud.setRadius(3.0F);
            cloud.setRadiusPerTick(0.01F);
            cloud.setDuration(300); // 15 秒
            cloud.setWaitTime(0);
            cloud.setParticle(ParticleTypes.FLAME);
            cloud.setOwner(owner);
            
            // 启用点燃并设置燃烧时长
            cloud.getClass().getMethod("setIgnite", boolean.class).invoke(cloud, true);
            cloud.getClass().getMethod("setIgniteTime", int.class).invoke(cloud, 2);
            cloud.getClass().getMethod("setExtinguishBySmoke", boolean.class).invoke(cloud, true);

            MobEffect flammable = ForgeRegistries.MOB_EFFECTS.getValue(LRTACTICAL_FLAMMABLE_ID);
            if (flammable != null) {
                cloud.addEffect(new MobEffectInstance(flammable, 100, 0, false, true, true));
            }
            return level.addFreshEntity(cloud);
        }
    }
}
