package org.mods.gd656conquest.server.compat.tacz;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Vector2d;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TaczShellingTestService {
    private static final String TACZ_MOD_ID = "tacz";
    private static final ResourceLocation M320_GUN_ID = new ResourceLocation(TACZ_MOD_ID, "m320");
    private static final Map<MinecraftServer, TaczShellingTestService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, ActiveShellingTask> activeTasks = new LinkedHashMap<>();
    private final TaczM320ShellBridge shellBridge = new TaczM320ShellBridge();

    private TaczShellingTestService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static TaczShellingTestService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, TaczShellingTestService::new);
        }
    }

    public static void forget(MinecraftServer server) {
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public StartResult startDebugShelling(
        ServerPlayer owner,
        double durationSeconds,
        double intervalSeconds,
        double radius,
        double shellHeight,
        Vec3 center,
        float pitch,
        float yaw
    ) {
        if (owner == null || center == null) {
            return StartResult.failure("炮击测试只能由在线玩家发起");
        }
        if (!ModList.get().isLoaded(TACZ_MOD_ID)) {
            return StartResult.failure("当前服务端未加载 TACZ，无法测试炮击");
        }
        String availabilityError = shellBridge.ensureAvailable();
        if (availabilityError != null) {
            return StartResult.failure(availabilityError);
        }
        long currentTick = server.getTickCount();
        long durationTicks = Math.max(1L, Math.round(durationSeconds * 20.0D));
        long intervalTicks = Math.max(1L, Math.round(intervalSeconds * 20.0D));
        long endTickExclusive = currentTick + durationTicks;
        int estimatedShells = (int) Math.max(1L, (durationTicks - 1L) / intervalTicks + 1L);
        ActiveShellingTask task = new ActiveShellingTask(
            UUID.randomUUID(),
            owner.getUUID(),
            owner.getScoreboardName(),
            owner.serverLevel().dimension(),
            center,
            Math.max(0.0D, radius),
            Math.max(0.0D, shellHeight),
            pitch,
            yaw,
            intervalTicks,
            currentTick,
            endTickExclusive
        );
        activeTasks.put(task.taskId(), task);
        ServerLogger.info(
            "已启动炮击测试 task=%s owner=%s durationTicks=%d intervalTicks=%d radius=%.2f height=%.2f center=(%.2f,%.2f,%.2f) pitch=%.2f yaw=%.2f",
            task.taskId(),
            task.ownerName(),
            durationTicks,
            intervalTicks,
            task.radius(),
            task.shellHeight(),
            task.center().x,
            task.center().y,
            task.center().z,
            task.pitch(),
            task.yaw()
        );
        return StartResult.success(
            "已启动炮击测试：持续 "
                + formatSeconds(durationTicks)
                + " 秒，间隔 "
                + formatSeconds(intervalTicks)
                + " 秒，预计生成 "
                + estimatedShells
                + " 枚 m320 榴弹"
        );
    }

    public void onServerTick(long gameTick) {
        if (activeTasks.isEmpty()) {
            return;
        }
        Iterator<ActiveShellingTask> iterator = activeTasks.values().iterator();
        while (iterator.hasNext()) {
            ActiveShellingTask task = iterator.next();
            if (task == null) {
                iterator.remove();
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(task.ownerId());
            if (owner == null || owner.isRemoved()) {
                ServerLogger.failure("炮击测试已停止：触发者离线 task=%s owner=%s", task.taskId(), task.ownerName());
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(task.dimension());
            if (level == null || !Objects.equals(owner.serverLevel().dimension(), task.dimension())) {
                ServerLogger.failure("炮击测试已停止：触发者已离开原维度 task=%s owner=%s", task.taskId(), task.ownerName());
                iterator.remove();
                continue;
            }
            boolean hadSpawnFailure = false;
            while (task.nextSpawnTick() <= gameTick && task.nextSpawnTick() < task.endTickExclusive()) {
                if (!spawnSingleShell(task, level, owner)) {
                    hadSpawnFailure = true;
                    break;
                }
                task.advanceNextSpawnTick();
            }
            if (hadSpawnFailure) {
                iterator.remove();
                continue;
            }
            if (task.nextSpawnTick() >= task.endTickExclusive() && gameTick >= task.endTickExclusive()) {
                iterator.remove();
                ServerLogger.info("炮击测试已完成 task=%s owner=%s", task.taskId(), task.ownerName());
            }
        }
    }

    private boolean spawnSingleShell(ActiveShellingTask task, ServerLevel level, ServerPlayer owner) {
        Vec3 spawnPos = resolveRandomSpawnPosition(task.center(), task.radius(), task.shellHeight());
        try {
            shellBridge.spawnShell(level, owner, spawnPos, task.pitch(), task.yaw());
            return true;
        } catch (Exception exception) {
            ServerLogger.failure("炮击测试生成炮弹失败 task=%s owner=%s error=%s", task.taskId(), task.ownerName(), exception.getMessage());
            exception.printStackTrace();
            owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c炮弹生成失败: " + exception.toString()));
            return false;
        }
    }

    private Vec3 resolveRandomSpawnPosition(Vec3 center, double radius, double shellHeight) {
        if (radius <= 1.0E-6D) {
            return center.add(0.0D, shellHeight, 0.0D);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        return center.add(offsetX, shellHeight, offsetZ);
    }

    private String formatSeconds(long ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0D);
    }

    public record StartResult(boolean success, String message) {
        public static StartResult success(String message) {
            return new StartResult(true, message == null ? "" : message);
        }

        public static StartResult failure(String message) {
            return new StartResult(false, message == null ? "" : message);
        }
    }

    private record ActiveShellingTask(
        UUID taskId,
        UUID ownerId,
        String ownerName,
        ResourceKey<Level> dimension,
        Vec3 center,
        double radius,
        double shellHeight,
        float pitch,
        float yaw,
        long intervalTicks,
        long[] timing
    ) {
        private ActiveShellingTask(
            UUID taskId,
            UUID ownerId,
            String ownerName,
            ResourceKey<Level> dimension,
            Vec3 center,
            double radius,
            double shellHeight,
            float pitch,
            float yaw,
            long intervalTicks,
            long nextSpawnTick,
            long endTickExclusive
        ) {
            this(taskId, ownerId, ownerName, dimension, center, radius, shellHeight, pitch, yaw, intervalTicks, new long[]{nextSpawnTick, endTickExclusive});
        }

        long nextSpawnTick() {
            return timing[0];
        }

        long endTickExclusive() {
            return timing[1];
        }

        void advanceNextSpawnTick() {
            timing[0] += intervalTicks;
        }
    }

    private static final class TaczM320ShellBridge {
        private boolean resolved;
        private String unavailableReason;
        private Constructor<?> bulletConstructor;
        private Method bulletShootMethod;
        private Field bulletStartPosField;
        private Method timelessGetCommonGunIndexMethod;
        private Method commonGunIndexGetGunDataMethod;
        private Method gunDataGetAmmoIdMethod;
        private Method gunDataGetBulletDataMethod;
        private Method bulletDataGetSpeedMethod;
        private Method bulletDataGetLifeSecondMethod;
        private Method bulletDataGetGravityMethod;
        private Method bulletDataGetFrictionMethod;
        private Method bulletDataGetExplosionDataMethod;
        private Method explosionDataIsExplodeMethod;
        private Method explosionDataGetDamageMethod;
        private Method explosionDataGetRadiusMethod;
        private Method explosionDataIsKnockbackMethod;
        private Method explosionDataGetDelayMethod;
        private Method explosionDataIsDestroyBlockMethod;
        private Method gunItemBuilderCreateMethod;
        private Method gunItemBuilderSetIdMethod;
        private Method gunItemBuilderBuildMethod;
        private Method gunItemBuilderForceBuildMethod;
        private Field bulletLifeField;
        private Field bulletSpeedField;
        private Field bulletGravityField;
        private Field bulletFrictionField;
        private Field bulletExplosionField;
        private Field bulletExplosionDamageField;
        private Field bulletExplosionRadiusField;
        private Field bulletExplosionDelayCountField;
        private Field bulletExplosionKnockbackField;
        private Field bulletExplosionDestroyBlockField;
        private Object gunData;
        private Object bulletData;
        private Object explosionData;
        private ResourceLocation ammoId;
        private float shellSpeedPerTick;

        String ensureAvailable() {
            if (resolved) {
                return unavailableReason;
            }
            resolved = true;
            try {
                Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
                Class<?> commonGunIndexClass = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
                Class<?> gunDataClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
                Class<?> bulletDataClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData");
                Class<?> entityKineticBulletClass = Class.forName("com.tacz.guns.entity.EntityKineticBullet");
                Class<?> gunItemBuilderClass = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");

                timelessGetCommonGunIndexMethod = timelessApiClass.getMethod("getCommonGunIndex", ResourceLocation.class);
                Object gunIndexOptional = timelessGetCommonGunIndexMethod.invoke(null, M320_GUN_ID);
                if (!(gunIndexOptional instanceof Optional<?> gunIndexResolved) || gunIndexResolved.isEmpty()) {
                    unavailableReason = "未找到 TACZ 默认枪械 m320 数据";
                    return unavailableReason;
                }
                Object gunIndex = gunIndexResolved.get();
                commonGunIndexGetGunDataMethod = commonGunIndexClass.getMethod("getGunData");
                gunData = commonGunIndexGetGunDataMethod.invoke(gunIndex);

                gunDataGetAmmoIdMethod = gunDataClass.getMethod("getAmmoId");
                gunDataGetBulletDataMethod = gunDataClass.getMethod("getBulletData");
                ammoId = (ResourceLocation) gunDataGetAmmoIdMethod.invoke(gunData);
                bulletData = gunDataGetBulletDataMethod.invoke(gunData);

                bulletDataGetSpeedMethod = bulletDataClass.getMethod("getSpeed");
                bulletDataGetLifeSecondMethod = bulletDataClass.getMethod("getLifeSecond");
                bulletDataGetGravityMethod = bulletDataClass.getMethod("getGravity");
                bulletDataGetFrictionMethod = bulletDataClass.getMethod("getFriction");
                bulletDataGetExplosionDataMethod = bulletDataClass.getMethod("getExplosionData");
                Number speedValue = (Number) bulletDataGetSpeedMethod.invoke(bulletData);
                shellSpeedPerTick = speedValue == null ? 3.0F : speedValue.floatValue() / 20.0F;
                explosionData = bulletDataGetExplosionDataMethod.invoke(bulletData);
                if (explosionData != null) {
                    Class<?> explosionDataClass = explosionData.getClass();
                    explosionDataIsExplodeMethod = explosionDataClass.getMethod("isExplode");
                    explosionDataGetDamageMethod = explosionDataClass.getMethod("getDamage");
                    explosionDataGetRadiusMethod = explosionDataClass.getMethod("getRadius");
                    explosionDataIsKnockbackMethod = explosionDataClass.getMethod("isKnockback");
                    explosionDataGetDelayMethod = explosionDataClass.getMethod("getDelay");
                    explosionDataIsDestroyBlockMethod = explosionDataClass.getMethod("isDestroyBlock");
                }

                bulletConstructor = entityKineticBulletClass.getConstructor(
                    Level.class,
                    LivingEntity.class,
                    ItemStack.class,
                    ResourceLocation.class,
                    ResourceLocation.class,
                    boolean.class,
                    gunDataClass,
                    bulletDataClass
                );
                bulletShootMethod = entityKineticBulletClass.getMethod("shoot", double.class, double.class, float.class, Vector2d.class);
                bulletStartPosField = entityKineticBulletClass.getDeclaredField("startPos");
                bulletStartPosField.setAccessible(true);
                bulletLifeField = getDeclaredField(entityKineticBulletClass, "life");
                bulletSpeedField = getDeclaredField(entityKineticBulletClass, "speed");
                bulletGravityField = getDeclaredField(entityKineticBulletClass, "gravity");
                bulletFrictionField = getDeclaredField(entityKineticBulletClass, "friction");
                bulletExplosionField = getDeclaredField(entityKineticBulletClass, "explosion");
                bulletExplosionDamageField = getDeclaredField(entityKineticBulletClass, "explosionDamage");
                bulletExplosionRadiusField = getDeclaredField(entityKineticBulletClass, "explosionRadius");
                bulletExplosionDelayCountField = getDeclaredField(entityKineticBulletClass, "explosionDelayCount");
                bulletExplosionKnockbackField = getDeclaredField(entityKineticBulletClass, "explosionKnockback");
                bulletExplosionDestroyBlockField = getDeclaredField(entityKineticBulletClass, "explosionDestroyBlock");
                gunItemBuilderCreateMethod = gunItemBuilderClass.getMethod("create");
                gunItemBuilderSetIdMethod = gunItemBuilderClass.getMethod("setId", ResourceLocation.class);
                gunItemBuilderBuildMethod = gunItemBuilderClass.getMethod("build");
                gunItemBuilderForceBuildMethod = gunItemBuilderClass.getMethod("forceBuild");
                ItemStack verificationStack = createGunStack();
                if (verificationStack.isEmpty()) {
                    unavailableReason = "未能通过 GunItemBuilder 构造 TACZ m320 枪物品";
                    return unavailableReason;
                }
            } catch (Exception exception) {
                unavailableReason = "TACZ 炮击桥初始化失败: " + exception.getMessage();
            }
            return unavailableReason;
        }

        void spawnShell(ServerLevel level, ServerPlayer owner, Vec3 spawnPos, float pitch, float yaw) throws Exception {
            String availabilityError = ensureAvailable();
            if (availabilityError != null) {
                throw new IllegalStateException(availabilityError);
            }
            Object shellObject = bulletConstructor.newInstance(
                level,
                owner,
                createGunStack(),
                ammoId,
                M320_GUN_ID,
                false,
                gunData,
                bulletData
            );
            if (!(shellObject instanceof Entity shellEntity)) {
                throw new IllegalStateException("TACZ 炮弹实体类型无效");
            }
            shellEntity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            bulletStartPosField.set(shellObject, spawnPos);
            forceShellProperties(shellObject);
            bulletShootMethod.invoke(shellObject, (double) pitch, (double) yaw, shellSpeedPerTick, new Vector2d(0.0D, 0.0D));
            level.addFreshEntity(shellEntity);
        }

        private ItemStack createGunStack() throws Exception {
            Object builder = gunItemBuilderCreateMethod.invoke(null);
            Object configuredBuilder = gunItemBuilderSetIdMethod.invoke(builder, M320_GUN_ID);
            Object built = gunItemBuilderBuildMethod.invoke(configuredBuilder);
            if (built instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
            Object forced = gunItemBuilderForceBuildMethod.invoke(configuredBuilder);
            if (forced instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
            return ItemStack.EMPTY;
        }

        private void forceShellProperties(Object shellObject) throws Exception {
            Number lifeSecond = (Number) bulletDataGetLifeSecondMethod.invoke(bulletData);
            Number gravity = (Number) bulletDataGetGravityMethod.invoke(bulletData);
            Number friction = (Number) bulletDataGetFrictionMethod.invoke(bulletData);
            int forcedLifeTicks = Math.max(1, Math.round((lifeSecond == null ? 5.0F : lifeSecond.floatValue()) * 20.0F));
            bulletLifeField.setInt(shellObject, forcedLifeTicks);
            bulletSpeedField.setFloat(shellObject, shellSpeedPerTick);
            bulletGravityField.setFloat(shellObject, gravity == null ? 0.0F : Math.max(0.0F, gravity.floatValue()));
            bulletFrictionField.setFloat(shellObject, friction == null ? 0.0F : Math.max(0.0F, friction.floatValue()));
            if (explosionData == null || explosionDataIsExplodeMethod == null) {
                return;
            }
            boolean explode = Boolean.TRUE.equals(explosionDataIsExplodeMethod.invoke(explosionData));
            bulletExplosionField.setBoolean(shellObject, explode);
            if (!explode) {
                return;
            }
            Number explosionDamage = (Number) explosionDataGetDamageMethod.invoke(explosionData);
            Number explosionRadius = (Number) explosionDataGetRadiusMethod.invoke(explosionData);
            Number explosionDelaySeconds = (Number) explosionDataGetDelayMethod.invoke(explosionData);
            boolean knockback = Boolean.TRUE.equals(explosionDataIsKnockbackMethod.invoke(explosionData));
            boolean destroyBlock = Boolean.TRUE.equals(explosionDataIsDestroyBlockMethod.invoke(explosionData));
            bulletExplosionDamageField.setFloat(shellObject, explosionDamage == null ? 0.0F : Math.max(0.0F, explosionDamage.floatValue()));
            bulletExplosionRadiusField.setFloat(shellObject, explosionRadius == null ? 0.0F : Math.max(0.0F, explosionRadius.floatValue()));
            bulletExplosionKnockbackField.setBoolean(shellObject, knockback);
            bulletExplosionDestroyBlockField.setBoolean(shellObject, destroyBlock);
            int delayTicks = Math.max(1, Math.round((explosionDelaySeconds == null ? 0.0F : explosionDelaySeconds.floatValue()) * 20.0F));
            if (forcedLifeTicks > 2) {
                delayTicks = Math.min(delayTicks, forcedLifeTicks - 2);
            }
            bulletExplosionDelayCountField.setInt(shellObject, delayTicks);
        }

        private Field getDeclaredField(Class<?> ownerClass, String fieldName) throws NoSuchFieldException {
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        }
    }
}
