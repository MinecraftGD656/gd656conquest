package org.mods.gd656conquest.server.compat.tacz;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.ModList;
import org.joml.Vector2d;
import org.mods.gd656conquest.server.compat.lrtactical.LrTacticalShellImpactCompatBridge;
import org.mods.gd656conquest.server.data.shelling.ShellingDataModel;
import org.mods.gd656conquest.server.data.shelling.ShellingImpactType;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.audio.RoomDistanceAudioService;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.shared.util.ItemStackSnbtCodec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final long MAX_TRACKED_IMPACT_LIFETIME_TICKS = 20L * 30L;
    private static final double FLAME_IMPACT_SOUND_RADIUS = 15.0D;
    private static final float FLAME_IMPACT_SOUND_MAX_VOLUME = 1.0F;
    private static final List<String> FLAME_IMPACT_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.flame_sound_1",
        "gd656conquest:sfx.shelling.flame_sound_2",
        "gd656conquest:sfx.shelling.flame_sound_3",
        "gd656conquest:sfx.shelling.flame_sound_4"
    );
    private static final Map<MinecraftServer, TaczShellingTestService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final RoomDistanceAudioService distanceAudioService;
    private final Map<UUID, ActiveShellingTask> activeTasks = new LinkedHashMap<>();
    private final Map<UUID, PendingImpactEffect> trackedImpactEffects = new LinkedHashMap<>();
    private final TaczShellBridge shellBridge = new TaczShellBridge();

    private TaczShellingTestService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.distanceAudioService = new RoomDistanceAudioService(server);
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
        String availabilityError = shellBridge.ensureAvailable();
        if (availabilityError != null) {
            return StartResult.failure(availabilityError);
        }
        String gunItemSnbt = shellBridge.createGunStackSnbt(M320_GUN_ID);
        if (gunItemSnbt.isBlank()) {
            return StartResult.failure("未能构造 TACZ 默认枪械 m320 物品");
        }
        return startShellingInternal(
            owner,
            durationSeconds,
            intervalSeconds,
            radius,
            shellHeight,
            center,
            pitch,
            yaw,
            M320_GUN_ID.toString(),
            gunItemSnbt,
            "m320 榴弹",
            ShellingImpactType.EMPTY
        );
    }

    public StartResult startShelling(ServerPlayer owner, ShellingDataModel.ShellingEntry entry, Vec3 center) {
        if (owner == null || entry == null || center == null) {
            return StartResult.failure("炮击只能由在线玩家在有效坐标上触发");
        }
        String gunId = entry.getGunId();
        if (gunId.isBlank()) {
            return StartResult.failure("炮击模板缺少 TACZ 枪械 ID");
        }
        String displayName = entry.getDisplayName().isBlank()
            ? (entry.getGunDisplayName().isBlank() ? gunId : entry.getGunDisplayName())
            : entry.getDisplayName();
        return startShellingInternal(
            owner,
            entry.getDurationSeconds(),
            entry.getIntervalSeconds(),
            entry.getRadius(),
            entry.getShellHeight(),
            center,
            entry.getPitch(),
            entry.getYaw(),
            gunId,
            entry.getGunItemSnbt(),
            displayName,
            entry.resolveImpactType()
        );
    }

    public SpawnResult spawnConfiguredShell(ServerPlayer owner, ShellingDataModel.ShellingEntry entry, Vec3 center) {
        if (owner == null || entry == null || center == null) {
            return SpawnResult.failure("炮弹只能由在线玩家在有效坐标上生成");
        }
        String gunId = entry.getGunId();
        if (gunId.isBlank()) {
            return SpawnResult.failure("炮击模板缺少 TACZ 枪械 ID");
        }
        if (!ModList.get().isLoaded(TACZ_MOD_ID)) {
            return SpawnResult.failure("当前服务端未加载 TACZ，无法生成炮弹");
        }
        String availabilityError = shellBridge.ensureAvailable();
        if (availabilityError != null) {
            return SpawnResult.failure(availabilityError);
        }
        try {
            shellBridge.ensureGunResolved(gunId, entry.getGunItemSnbt());
            ServerLevel level = owner.serverLevel();
            Vec3 spawnPos = resolveRandomSpawnPosition(center, entry.getRadius(), entry.getShellHeight());
            Entity shellEntity = shellBridge.spawnShell(
                level,
                owner,
                spawnPos,
                entry.getPitch(),
                entry.getYaw(),
                gunId,
                entry.getGunItemSnbt()
            );
            ShellingImpactType impactType = entry.resolveImpactType();
            if (!impactType.isEmpty() && shellEntity != null) {
                trackedImpactEffects.put(
                    shellEntity.getUUID(),
                    new PendingImpactEffect(
                        shellEntity.getUUID(),
                        owner.getUUID(),
                        level.dimension(),
                        impactType,
                        server.getTickCount()
                    )
                );
            }
            return SpawnResult.success(
                shellEntity == null ? null : shellEntity.getUUID(),
                level.dimension(),
                spawnPos
            );
        } catch (Exception exception) {
            return SpawnResult.failure("炮弹生成失败: " + exception.getMessage());
        }
    }

    private StartResult startShellingInternal(
        ServerPlayer owner,
        double durationSeconds,
        double intervalSeconds,
        double radius,
        double shellHeight,
        Vec3 center,
        float pitch,
        float yaw,
        String gunId,
        String gunItemSnbt,
        String displayName,
        ShellingImpactType impactType
    ) {
        if (owner == null || center == null) {
            return StartResult.failure("炮击只能由在线玩家发起");
        }
        if (!ModList.get().isLoaded(TACZ_MOD_ID)) {
            return StartResult.failure("当前服务端未加载 TACZ，无法测试炮击");
        }
        String availabilityError = shellBridge.ensureAvailable();
        if (availabilityError != null) {
            return StartResult.failure(availabilityError);
        }
        try {
            shellBridge.ensureGunResolved(gunId, gunItemSnbt);
        } catch (Exception exception) {
            return StartResult.failure("炮击模板枪械无效: " + exception.getMessage());
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
            gunId == null ? "" : gunId.trim(),
            gunItemSnbt == null ? "" : gunItemSnbt.trim(),
            impactType == null ? ShellingImpactType.EMPTY : impactType,
            intervalTicks,
            currentTick,
            endTickExclusive
        );
        activeTasks.put(task.taskId(), task);
        ServerLogger.info(
            "已启动炮击测试 task=%s owner=%s durationTicks=%d intervalTicks=%d radius=%.2f height=%.2f center=(%.2f,%.2f,%.2f) pitch=%.2f yaw=%.2f impactType=%s",
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
            task.yaw(),
            task.impactType().id()
        );
        return StartResult.success(
            "已启动炮击测试：持续 "
                + formatSeconds(durationTicks)
                + " 秒，间隔 "
                + formatSeconds(intervalTicks)
                + " 秒，预计生成 "
                + estimatedShells
                + " 枚 "
                + (displayName == null || displayName.isBlank() ? gunId : displayName)
        );
    }

    public void onServerTick(long gameTick) {
        cleanupTrackedImpactEffects(gameTick);
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
            Entity shellEntity = shellBridge.spawnShell(level, owner, spawnPos, task.pitch(), task.yaw(), task.gunId(), task.gunItemSnbt());
            if (!task.impactType().isEmpty() && shellEntity != null) {
                trackedImpactEffects.put(
                    shellEntity.getUUID(),
                    new PendingImpactEffect(
                        shellEntity.getUUID(),
                        owner.getUUID(),
                        level.dimension(),
                        task.impactType(),
                        server.getTickCount()
                    )
                );
            }
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

    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event == null) {
            return;
        }
        Projectile projectile = event.getProjectile();
        if (projectile == null) {
            return;
        }
        onTaczProjectileImpact(projectile);
    }

    public void onTaczProjectileImpact(Entity projectile) {
        if (projectile == null) {
            return;
        }
        PendingImpactEffect pending = trackedImpactEffects.remove(projectile.getUUID());
        if (pending == null || pending.impactType().isEmpty()) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(pending.ownerId());
        if (owner == null || owner.isRemoved()) {
            return;
        }
        ServerLevel level = server.getLevel(pending.dimension());
        if (level == null) {
            return;
        }
        Vec3 impactPos = projectile.position();
        LrTacticalShellImpactCompatBridge.spawnImpactEffect(level, owner, impactPos, pending.impactType());
        applyMissileImpactKill(level, owner, impactPos, pending.impactType());
        playImpactSound(level, impactPos, pending.impactType());
    }

    private void applyMissileImpactKill(ServerLevel level, ServerPlayer owner, Vec3 impactPos, ShellingImpactType impactType) {
        if (level == null || owner == null || impactPos == null || impactType != ShellingImpactType.MISSILE) {
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        double killRadius = org.mods.gd656conquest.server.data.ConquestDataManager.of(server).serverConfig().getMissileKillRadius();
        double maxDistanceSqr = killRadius * killRadius;
        for (ServerPlayer target : level.players()) {
            if (target == null || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
                continue;
            }
            if (target.getUUID().equals(owner.getUUID())) {
                continue;
            }
            if (roomManager.isSameCampPlayer(owner, target)) {
                continue;
            }
            if (target.position().distanceToSqr(impactPos) > maxDistanceSqr) {
                continue;
            }
            target.hurt(owner.damageSources().playerAttack(owner), Float.MAX_VALUE);
        }
    }

    private void playImpactSound(ServerLevel level, Vec3 impactPos, ShellingImpactType impactType) {
        if (level == null || impactPos == null || impactType != ShellingImpactType.FLAMESHELL || FLAME_IMPACT_SOUND_IDS.isEmpty()) {
            return;
        }
        String soundId = FLAME_IMPACT_SOUND_IDS.get(ThreadLocalRandom.current().nextInt(FLAME_IMPACT_SOUND_IDS.size()));
        if (soundId.isBlank()) {
            return;
        }
        distanceAudioService.playPositionalRadiusSound(
            level.players(),
            level.dimension(),
            impactPos,
            soundId,
            FLAME_IMPACT_SOUND_RADIUS,
            FLAME_IMPACT_SOUND_MAX_VOLUME,
            1.0F
        );
    }

    private void cleanupTrackedImpactEffects(long gameTick) {
        if (trackedImpactEffects.isEmpty()) {
            return;
        }
        Iterator<PendingImpactEffect> iterator = trackedImpactEffects.values().iterator();
        while (iterator.hasNext()) {
            PendingImpactEffect pending = iterator.next();
            if (pending == null) {
                iterator.remove();
                continue;
            }
            if (gameTick - pending.createdTick() > MAX_TRACKED_IMPACT_LIFETIME_TICKS) {
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            Entity entity = level.getEntity(pending.projectileId());
            if (entity == null || entity.isRemoved()) {
                iterator.remove();
            }
        }
    }

    private String formatSeconds(long ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0D);
    }

    public static int estimateShellCount(double durationSeconds, double intervalSeconds) {
        long durationTicks = Math.max(1L, Math.round(durationSeconds * 20.0D));
        long intervalTicks = Math.max(1L, Math.round(intervalSeconds * 20.0D));
        return (int) Math.max(1L, (durationTicks - 1L) / intervalTicks + 1L);
    }

    public static int estimateShellCount(ShellingDataModel.ShellingEntry entry) {
        if (entry == null) {
            return 1;
        }
        return estimateShellCount(entry.getDurationSeconds(), entry.getIntervalSeconds());
    }

    public record StartResult(boolean success, String message) {
        public static StartResult success(String message) {
            return new StartResult(true, message == null ? "" : message);
        }

        public static StartResult failure(String message) {
            return new StartResult(false, message == null ? "" : message);
        }
    }

    public record SpawnResult(
        boolean success,
        String message,
        UUID projectileId,
        ResourceKey<Level> dimension,
        Vec3 spawnPosition
    ) {
        public static SpawnResult success(UUID projectileId, ResourceKey<Level> dimension, Vec3 spawnPosition) {
            return new SpawnResult(true, "", projectileId, dimension, spawnPosition);
        }

        public static SpawnResult failure(String message) {
            return new SpawnResult(false, message == null ? "" : message, null, null, null);
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
        String gunId,
        String gunItemSnbt,
        ShellingImpactType impactType,
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
            String gunId,
            String gunItemSnbt,
            ShellingImpactType impactType,
            long intervalTicks,
            long nextSpawnTick,
            long endTickExclusive
        ) {
            this(taskId, ownerId, ownerName, dimension, center, radius, shellHeight, pitch, yaw, gunId, gunItemSnbt, impactType, intervalTicks, new long[]{nextSpawnTick, endTickExclusive});
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

    private record PendingImpactEffect(
        UUID projectileId,
        UUID ownerId,
        ResourceKey<Level> dimension,
        ShellingImpactType impactType,
        long createdTick
    ) {
    }

    private static final class TaczShellBridge {
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
        private final Map<String, ResolvedGunData> resolvedGunDataById = new LinkedHashMap<>();

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
                commonGunIndexGetGunDataMethod = commonGunIndexClass.getMethod("getGunData");
                gunDataGetAmmoIdMethod = gunDataClass.getMethod("getAmmoId");
                gunDataGetBulletDataMethod = gunDataClass.getMethod("getBulletData");
                bulletDataGetSpeedMethod = bulletDataClass.getMethod("getSpeed");
                bulletDataGetLifeSecondMethod = bulletDataClass.getMethod("getLifeSecond");
                bulletDataGetGravityMethod = bulletDataClass.getMethod("getGravity");
                bulletDataGetFrictionMethod = bulletDataClass.getMethod("getFriction");
                bulletDataGetExplosionDataMethod = bulletDataClass.getMethod("getExplosionData");

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
                ItemStack verificationStack = createGunStack(M320_GUN_ID, "");
                if (verificationStack.isEmpty()) {
                    unavailableReason = "未能通过 GunItemBuilder 构造 TACZ m320 枪物品";
                    return unavailableReason;
                }
            } catch (Exception exception) {
                unavailableReason = "TACZ 炮击桥初始化失败: " + exception.getMessage();
            }
            return unavailableReason;
        }

        void ensureGunResolved(String gunIdText, String gunItemSnbt) throws Exception {
            String availabilityError = ensureAvailable();
            if (availabilityError != null) {
                throw new IllegalStateException(availabilityError);
            }
            ResourceLocation gunId = parseGunId(gunIdText);
            ResolvedGunData gunData = resolveGunData(gunId);
            if (gunData == null) {
                throw new IllegalStateException("未找到 TACZ 枪械数据: " + gunId);
            }
            ItemStack stack = createGunStack(gunId, gunItemSnbt);
            if (stack.isEmpty()) {
                throw new IllegalStateException("未能构造枪械物品: " + gunId);
            }
        }

        String createGunStackSnbt(ResourceLocation gunId) {
            try {
                return ItemStackSnbtCodec.serialize(createGunStack(gunId, ""));
            } catch (Exception ignored) {
                return "";
            }
        }

        Entity spawnShell(ServerLevel level, ServerPlayer owner, Vec3 spawnPos, float pitch, float yaw, String gunIdText, String gunItemSnbt) throws Exception {
            String availabilityError = ensureAvailable();
            if (availabilityError != null) {
                throw new IllegalStateException(availabilityError);
            }
            ResourceLocation gunId = parseGunId(gunIdText);
            ResolvedGunData gunData = resolveGunData(gunId);
            if (gunData == null) {
                throw new IllegalStateException("未找到 TACZ 枪械数据: " + gunId);
            }
            ItemStack gunStack = createGunStack(gunId, gunItemSnbt);
            if (gunStack.isEmpty()) {
                throw new IllegalStateException("未能构造枪械物品: " + gunId);
            }
            Object shellObject = bulletConstructor.newInstance(
                level,
                owner,
                gunStack,
                gunData.ammoId(),
                gunId,
                false,
                gunData.gunData(),
                gunData.bulletData()
            );
            if (!(shellObject instanceof Entity shellEntity)) {
                throw new IllegalStateException("TACZ 炮弹实体类型无效");
            }
            shellEntity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            bulletStartPosField.set(shellObject, spawnPos);
            forceShellProperties(shellObject, gunData);
            bulletShootMethod.invoke(shellObject, (double) pitch, (double) yaw, gunData.shellSpeedPerTick(), new Vector2d(0.0D, 0.0D));
            level.addFreshEntity(shellEntity);
            return shellEntity;
        }

        private ResourceLocation parseGunId(String gunIdText) {
            try {
                return ResourceLocation.tryParse(gunIdText == null ? "" : gunIdText.trim());
            } catch (Exception ignored) {
                return null;
            }
        }

        private ResolvedGunData resolveGunData(ResourceLocation gunId) throws Exception {
            if (gunId == null) {
                return null;
            }
            String key = gunId.toString().toLowerCase(Locale.ROOT);
            ResolvedGunData cached = resolvedGunDataById.get(key);
            if (cached != null) {
                return cached;
            }
            Object gunIndexOptional = timelessGetCommonGunIndexMethod.invoke(null, gunId);
            if (!(gunIndexOptional instanceof Optional<?> gunIndexResolved) || gunIndexResolved.isEmpty()) {
                return null;
            }
            Object gunIndex = gunIndexResolved.get();
            Object gunData = commonGunIndexGetGunDataMethod.invoke(gunIndex);
            Object bulletData = gunDataGetBulletDataMethod.invoke(gunData);
            Object explosionData = bulletDataGetExplosionDataMethod.invoke(bulletData);
            List<Method> explosionMethods = new ArrayList<>(6);
            if (explosionData != null) {
                Class<?> explosionDataClass = explosionData.getClass();
                explosionMethods.add(explosionDataClass.getMethod("isExplode"));
                explosionMethods.add(explosionDataClass.getMethod("getDamage"));
                explosionMethods.add(explosionDataClass.getMethod("getRadius"));
                explosionMethods.add(explosionDataClass.getMethod("isKnockback"));
                explosionMethods.add(explosionDataClass.getMethod("getDelay"));
                explosionMethods.add(explosionDataClass.getMethod("isDestroyBlock"));
            } else {
                for (int i = 0; i < 6; i++) {
                    explosionMethods.add(null);
                }
            }
            Number speedValue = (Number) bulletDataGetSpeedMethod.invoke(bulletData);
            float shellSpeedPerTick = speedValue == null ? 3.0F : speedValue.floatValue() / 20.0F;
            ResolvedGunData resolvedGunData = new ResolvedGunData(
                gunData,
                bulletData,
                explosionData,
                (ResourceLocation) gunDataGetAmmoIdMethod.invoke(gunData),
                shellSpeedPerTick,
                explosionMethods.get(0),
                explosionMethods.get(1),
                explosionMethods.get(2),
                explosionMethods.get(3),
                explosionMethods.get(4),
                explosionMethods.get(5)
            );
            resolvedGunDataById.put(key, resolvedGunData);
            return resolvedGunData;
        }

        private ItemStack createGunStack(ResourceLocation gunId, String gunItemSnbt) throws Exception {
            ItemStack stackFromSnbt = ItemStackSnbtCodec.deserialize(gunItemSnbt);
            if (!stackFromSnbt.isEmpty()) {
                return stackFromSnbt;
            }
            Object builder = gunItemBuilderCreateMethod.invoke(null);
            Object configuredBuilder = gunItemBuilderSetIdMethod.invoke(builder, gunId);
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

        private void forceShellProperties(Object shellObject, ResolvedGunData gunData) throws Exception {
            Number lifeSecond = (Number) bulletDataGetLifeSecondMethod.invoke(gunData.bulletData());
            Number gravity = (Number) bulletDataGetGravityMethod.invoke(gunData.bulletData());
            Number friction = (Number) bulletDataGetFrictionMethod.invoke(gunData.bulletData());
            int forcedLifeTicks = Math.max(1, Math.round((lifeSecond == null ? 5.0F : lifeSecond.floatValue()) * 20.0F));
            bulletLifeField.setInt(shellObject, forcedLifeTicks);
            bulletSpeedField.setFloat(shellObject, gunData.shellSpeedPerTick());
            bulletGravityField.setFloat(shellObject, gravity == null ? 0.0F : Math.max(0.0F, gravity.floatValue()));
            bulletFrictionField.setFloat(shellObject, friction == null ? 0.0F : Math.max(0.0F, friction.floatValue()));
            if (gunData.explosionData() == null || gunData.explosionDataIsExplodeMethod() == null) {
                return;
            }
            boolean explode = Boolean.TRUE.equals(gunData.explosionDataIsExplodeMethod().invoke(gunData.explosionData()));
            bulletExplosionField.setBoolean(shellObject, explode);
            if (!explode) {
                return;
            }
            Number explosionDamage = (Number) gunData.explosionDataGetDamageMethod().invoke(gunData.explosionData());
            Number explosionRadius = (Number) gunData.explosionDataGetRadiusMethod().invoke(gunData.explosionData());
            Number explosionDelaySeconds = (Number) gunData.explosionDataGetDelayMethod().invoke(gunData.explosionData());
            boolean knockback = Boolean.TRUE.equals(gunData.explosionDataIsKnockbackMethod().invoke(gunData.explosionData()));
            boolean destroyBlock = Boolean.TRUE.equals(gunData.explosionDataIsDestroyBlockMethod().invoke(gunData.explosionData()));
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

        private record ResolvedGunData(
            Object gunData,
            Object bulletData,
            Object explosionData,
            ResourceLocation ammoId,
            float shellSpeedPerTick,
            Method explosionDataIsExplodeMethod,
            Method explosionDataGetDamageMethod,
            Method explosionDataGetRadiusMethod,
            Method explosionDataIsKnockbackMethod,
            Method explosionDataGetDelayMethod,
            Method explosionDataIsDestroyBlockMethod
        ) {
        }
    }
}
