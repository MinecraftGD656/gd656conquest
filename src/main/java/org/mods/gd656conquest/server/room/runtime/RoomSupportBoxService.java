package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.common.entity.AmmoBoxEntity;
import org.mods.gd656conquest.common.entity.ConquestEntities;
import org.mods.gd656conquest.common.entity.MedicalBoxEntity;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.facility.support.HeroKitSupportService;
import org.mods.gd656conquest.server.room.core.RoomPlayerSessionService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeRepository;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomConquestConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoomSupportBoxService {
    private static final String ACTIONBAR_SOURCE_AMMO_BOX_INTERACT = "room_ammo_box_interact";
    private static final int AMMO_BOX_MAX_USE_COUNT = 16;
    private static final int AMMO_BOX_PER_PLAYER_CD_TICKS = 30 * 20;
    private static final int PERMANENT_SUPPORT_BOX_PER_PLAYER_CD_TICKS = 60 * 20;
    private static final int AMMO_BOX_RADIUS = 2;
    private static final int AMMO_BOX_INFO_PRIORITY_TICKS = 2 * 20;
    private static final String AMMO_BOX_THROW_TAG = "gd656_ammo_box_throw";
    private static final String AMMO_BOX_OWNER_TAG = "gd656_ammo_box_owner";
    private static final ResourceLocation AMMO_BAG_PICKUP_SOUND_ID = ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.ammo_bag_pickup");

    private static final int MEDICAL_BOX_MAX_USE_COUNT = 16;
    private static final int MEDICAL_BOX_PER_PLAYER_CD_TICKS = 30 * 20;
    private static final int MEDICAL_BOX_RADIUS = 2;
    private static final int MEDICAL_BOX_REGEN_DURATION_TICKS = 5 * 20;
    private static final int MEDICAL_BOX_REGEN_AMPLIFIER = 3;
    private static final int MEDICAL_BOX_INFO_PRIORITY_TICKS = 2 * 20;
    private static final String MEDICAL_BOX_THROW_TAG = "gd656_med_box_throw";
    private static final String MEDICAL_BOX_OWNER_TAG = "gd656_med_box_owner";

    private final MinecraftServer server;
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomPlayerSessionService playerSessionService;
    private final RoomPlayerLoadoutService playerLoadoutService;
    private final RoomGunmasterLoadoutService gunmasterLoadoutService;

    private final Set<UUID> pendingAmmoBoxThrows = new HashSet<>();
    private final Set<UUID> activeAmmoBoxIds = new HashSet<>();
    private final Set<UUID> playersWithAmmoBoxActionbar = new HashSet<>();
    private final Map<UUID, UUID> ownerActiveAmmoBoxId = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> ammoBoxCooldownUntilTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> ammoBoxLastNotifyTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> ammoBoxInfoPriorityUntilTickByBox = new HashMap<>();
    private final Map<String, Set<UUID>> permanentAmmoBoxIdsByRoom = new HashMap<>();

    private final Set<UUID> pendingMedicalBoxThrows = new HashSet<>();
    private final Set<UUID> activeMedicalBoxIds = new HashSet<>();
    private final Map<UUID, UUID> ownerActiveMedicalBoxId = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> medicalBoxCooldownUntilTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> medicalBoxLastNotifyTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> medicalBoxInfoPriorityUntilTickByBox = new HashMap<>();
    private final Map<String, Set<UUID>> permanentMedicalBoxIdsByRoom = new HashMap<>();

    // GD656Mods: 地图配置的永久箱改为"配置点驱动" —— 判定/显示/使用只看坐标, 完全不依赖实体是否加载。
    // 背景: 原来每 tick 用 findEntityByUuid 反查实体, 而 ServerLevel.getEntity 只命中"已加载"实体,
    // 大地图远处箱子在开局时区块未加载 → 被误判为已移除并从追踪表剔除 → 玩家走过去看得见却用不了。
    // 投掷箱(位置随实体)仍走 activeAmmoBoxIds 那套实体驱动逻辑, 两条链互不干扰。
    private final Map<String, List<PermanentBoxPoint>> permanentAmmoPointsByRoom = new HashMap<>();
    private final Map<String, List<PermanentBoxPoint>> permanentMedicalPointsByRoom = new HashMap<>();
    /** GD656Mods: 永久箱的每玩家冷却/提示状态, key = {@link #permanentBoxKey}(与实体无关, 实体重建也不丢)。 */
    private final Map<String, Map<UUID, Long>> permanentAmmoCooldownUntilTickByBox = new HashMap<>();
    private final Map<String, Map<UUID, Long>> permanentAmmoInfoPriorityUntilTickByBox = new HashMap<>();
    private final Map<String, Map<UUID, Long>> permanentAmmoLastNotifyTickByBox = new HashMap<>();
    private final Map<String, Map<UUID, Long>> permanentMedicalCooldownUntilTickByBox = new HashMap<>();
    private final Map<String, Map<UUID, Long>> permanentMedicalInfoPriorityUntilTickByBox = new HashMap<>();
    private final Map<String, Map<UUID, Long>> permanentMedicalLastNotifyTickByBox = new HashMap<>();

    /** GD656Mods: 永久箱的配置点(索引 + 已含 0.5 偏移的箱心坐标)。 */
    private record PermanentBoxPoint(int index, double x, double y, double z) {
    }

    /** GD656Mods: 永久箱的稳定身份 —— 房间 + 配置点索引。 */
    private static String permanentBoxKey(String roomId, int index) {
        return "p|" + (roomId == null ? "" : roomId) + "|" + index;
    }

    public RoomSupportBoxService(
        MinecraftServer server,
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService,
        RoomPlayerLoadoutService playerLoadoutService,
        RoomGunmasterLoadoutService gunmasterLoadoutService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.playerLoadoutService = Objects.requireNonNull(playerLoadoutService, "playerLoadoutService");
        this.gunmasterLoadoutService = Objects.requireNonNull(gunmasterLoadoutService, "gunmasterLoadoutService");
    }

    public void tick(long gameTick) {
        processPendingMedicalBoxThrows();
        processPendingAmmoBoxThrows();
        // GD656Mods: 靠近提示/自动触发不需要 20Hz —— 每 2 tick(10Hz)一次, 延迟 <=100ms 玩家无感,
        // 开销减半(该判定原本是每 tick 全箱子扫描)。
        if (gameTick % 2L != 0L) {
            return;
        }
        processMedicalBoxAura(gameTick);
        processAmmoBoxAura(gameTick);
    }

    /**
     * GD656Mods: 登记"坐标驱动"的永久箱点 —— 与维度无关, 只依赖房间配置。
     *
     * <p>坐标已按实体生成规则补上 +0.5 偏移, 保证判定点与实体位置一致。
     */
    private void registerPermanentPoints(String roomId, List<RoomPosition> positions, Map<String, List<PermanentBoxPoint>> target) {
        for (RoomPosition position : positions) {
            if (position == null) {
                continue;
            }
            List<PermanentBoxPoint> points = target.computeIfAbsent(roomId, ignored -> new ArrayList<>());
            points.add(new PermanentBoxPoint(points.size(), position.x() + 0.5D, position.y(), position.z() + 0.5D));
        }
    }

    public void spawnPermanentSupportBoxes(RoomDefinition room) {
        if (room == null || room.roomId() == null || room.roomId().isBlank()) {
            return;
        }
        clearPermanentSupportBoxes(room.roomId());
        List<RoomPosition> ammoPositions = resolvePermanentAmmoBoxPositions(room);
        List<RoomPosition> medicalPositions = resolvePermanentMedicalBoxPositions(room);
        // GD656Mods: 配置点登记必须在解析维度【之前】完成 —— 登记本身不需要 ServerLevel。
        // 否则当生成那一刻房间维度尚未加载(大地图按需加载、活跃上限锁定卸载维度等)时会直接
        // return 掉, 连"坐标驱动"的数据都没建立, 永久箱在整局里彻底失效: 玩家会看到
        // "实体(随维度加载出现)就在眼前、靠近却毫无反应/按键无效"。
        registerPermanentPoints(room.roomId(), ammoPositions, permanentAmmoPointsByRoom);
        registerPermanentPoints(room.roomId(), medicalPositions, permanentMedicalPointsByRoom);
        // 实体只负责"可见性": 维度没加载就跳过生成, 不影响上面的坐标驱动判定。
        ServerLevel level = resolveRoomLevel(room);
        if (level == null) {
            return;
        }
        for (RoomPosition position : ammoPositions) {
            if (position == null) {
                continue;
            }
            AmmoBoxEntity ammoBox = new AmmoBoxEntity(ConquestEntities.AMMO_BOX.get(), level);
            ammoBox.setPos(position.x() + 0.5D, position.y(), position.z() + 0.5D);
            ammoBox.setYRot(0.0F);
            ammoBox.setOwnerUuid(null);
            ammoBox.setPermanentSupport(true);
            ammoBox.setUseRemaining(Integer.MAX_VALUE);
            ammoBox.setInvulnerable(true);
            ammoBox.setHealth(100.0F);
            level.addFreshEntity(ammoBox);
            activeAmmoBoxIds.add(ammoBox.getUUID());
            permanentAmmoBoxIdsByRoom.computeIfAbsent(room.roomId(), ignored -> new HashSet<>()).add(ammoBox.getUUID());
        }
        for (RoomPosition position : medicalPositions) {
            if (position == null) {
                continue;
            }
            MedicalBoxEntity medicalBox = new MedicalBoxEntity(ConquestEntities.MEDICAL_BOX.get(), level);
            medicalBox.setPos(position.x() + 0.5D, position.y(), position.z() + 0.5D);
            medicalBox.setYRot(0.0F);
            medicalBox.setOwnerUuid(null);
            medicalBox.setPermanentSupport(true);
            medicalBox.setUseRemaining(Integer.MAX_VALUE);
            medicalBox.setInvulnerable(true);
            medicalBox.setHealth(100.0F);
            level.addFreshEntity(medicalBox);
            activeMedicalBoxIds.add(medicalBox.getUUID());
            permanentMedicalBoxIdsByRoom.computeIfAbsent(room.roomId(), ignored -> new HashSet<>()).add(medicalBox.getUUID());
        }
    }

    public void clearPermanentSupportBoxes(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        for (UUID boxId : List.copyOf(permanentAmmoBoxIdsByRoom.getOrDefault(roomId, Set.of()))) {
            removeTrackedAmmoBox(boxId, true);
        }
        for (UUID boxId : List.copyOf(permanentMedicalBoxIdsByRoom.getOrDefault(roomId, Set.of()))) {
            removeTrackedMedicalBox(boxId, true);
        }
        permanentAmmoBoxIdsByRoom.remove(roomId);
        permanentMedicalBoxIdsByRoom.remove(roomId);
        // GD656Mods: 一并清理坐标驱动的永久箱状态(点数表 + 每玩家冷却/提示)
        for (PermanentBoxPoint point : permanentAmmoPointsByRoom.getOrDefault(roomId, List.of())) {
            clearPermanentAmmoBoxState(permanentBoxKey(roomId, point.index()));
        }
        for (PermanentBoxPoint point : permanentMedicalPointsByRoom.getOrDefault(roomId, List.of())) {
            clearPermanentMedicalBoxState(permanentBoxKey(roomId, point.index()));
        }
        permanentAmmoPointsByRoom.remove(roomId);
        permanentMedicalPointsByRoom.remove(roomId);
    }

    private void clearPermanentAmmoBoxState(String boxKey) {
        permanentAmmoCooldownUntilTickByBox.remove(boxKey);
        permanentAmmoInfoPriorityUntilTickByBox.remove(boxKey);
        permanentAmmoLastNotifyTickByBox.remove(boxKey);
    }

    private void clearPermanentMedicalBoxState(String boxKey) {
        permanentMedicalCooldownUntilTickByBox.remove(boxKey);
        permanentMedicalInfoPriorityUntilTickByBox.remove(boxKey);
        permanentMedicalLastNotifyTickByBox.remove(boxKey);
    }

    public boolean tryUseAmmoBoxItem(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        RunningPlayerContext context = findRunningPlayerContext(player.getUUID());
        if (context != null && gunmasterLoadoutService.isGunmasterRoom(context.room())) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() != ConquestItems.AMMO_BOX.get()) {
            return false;
        }
        ItemStack thrownStack = held.copy();
        thrownStack.setCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.2D, player.getZ(), thrownStack);
        Vec3 look = player.getLookAngle();
        Vec3 velocity = look.scale(0.55D).add(player.getDeltaMovement().scale(0.15D));
        dropped.setDeltaMovement(velocity);
        dropped.setPickUpDelay(20);
        dropped.setThrower(player.getUUID());
        CompoundTag data = dropped.getPersistentData();
        data.putBoolean(AMMO_BOX_THROW_TAG, true);
        data.putUUID(AMMO_BOX_OWNER_TAG, player.getUUID());
        player.level().addFreshEntity(dropped);
        pendingAmmoBoxThrows.add(dropped.getUUID());
        player.swing(hand, true);
        return true;
    }

    public boolean tryUseMedicalBoxItem(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() != ConquestItems.MED_BOX.get()) {
            return false;
        }
        ItemStack thrownStack = held.copy();
        thrownStack.setCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.2D, player.getZ(), thrownStack);
        Vec3 look = player.getLookAngle();
        Vec3 velocity = look.scale(0.55D).add(player.getDeltaMovement().scale(0.15D));
        dropped.setDeltaMovement(velocity);
        dropped.setPickUpDelay(20);
        dropped.setThrower(player.getUUID());
        CompoundTag data = dropped.getPersistentData();
        data.putBoolean(MEDICAL_BOX_THROW_TAG, true);
        data.putUUID(MEDICAL_BOX_OWNER_TAG, player.getUUID());
        player.level().addFreshEntity(dropped);
        pendingMedicalBoxThrows.add(dropped.getUUID());
        player.swing(hand, true);
        return true;
    }

    private void processPendingAmmoBoxThrows() {
        if (pendingAmmoBoxThrows.isEmpty()) {
            return;
        }
        List<UUID> done = new ArrayList<>();
        for (UUID entityId : pendingAmmoBoxThrows) {
            Entity raw = findEntityByUuid(entityId);
            if (!(raw instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                done.add(entityId);
                continue;
            }
            CompoundTag data = itemEntity.getPersistentData();
            if (!data.getBoolean(AMMO_BOX_THROW_TAG)) {
                done.add(entityId);
                continue;
            }
            if (!itemEntity.onGround() && itemEntity.tickCount < 200) {
                continue;
            }
            spawnAmmoBoxFromThrownItem(itemEntity);
            done.add(entityId);
        }
        pendingAmmoBoxThrows.removeAll(done);
    }

    private void processPendingMedicalBoxThrows() {
        if (pendingMedicalBoxThrows.isEmpty()) {
            return;
        }
        List<UUID> done = new ArrayList<>();
        for (UUID entityId : pendingMedicalBoxThrows) {
            Entity raw = findEntityByUuid(entityId);
            if (!(raw instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                done.add(entityId);
                continue;
            }
            CompoundTag data = itemEntity.getPersistentData();
            if (!data.getBoolean(MEDICAL_BOX_THROW_TAG)) {
                done.add(entityId);
                continue;
            }
            if (!itemEntity.onGround() && itemEntity.tickCount < 200) {
                continue;
            }
            spawnMedicalBoxFromThrownItem(itemEntity);
            done.add(entityId);
        }
        pendingMedicalBoxThrows.removeAll(done);
    }

    private void spawnAmmoBoxFromThrownItem(ItemEntity itemEntity) {
        if (itemEntity == null || !itemEntity.isAlive() || !(itemEntity.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = itemEntity.getPersistentData();
        if (!data.getBoolean(AMMO_BOX_THROW_TAG) || !data.hasUUID(AMMO_BOX_OWNER_TAG)) {
            return;
        }
        UUID ownerId = data.getUUID(AMMO_BOX_OWNER_TAG);
        removeExistingAmmoBoxOfOwner(ownerId);
        AmmoBoxEntity ammoBox = new AmmoBoxEntity(ConquestEntities.AMMO_BOX.get(), level);
        ammoBox.setPos(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
        ammoBox.setYRot(itemEntity.getYRot());
        ammoBox.setOwnerUuid(ownerId);
        ammoBox.setUseRemaining(AMMO_BOX_MAX_USE_COUNT);
        ammoBox.setHealth(100.0F);
        level.addFreshEntity(ammoBox);
        activeAmmoBoxIds.add(ammoBox.getUUID());
        ownerActiveAmmoBoxId.put(ownerId, ammoBox.getUUID());
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            FrontlineVoiceBridge.playCue(owner, RoomThirdPersonAudioCueRegistry.PLAYER_THROW_AMMO);
        } else {
            emitSupportBoxVoice(ownerId, null, ammoBox.position(), RoomThirdPersonAudioCueRegistry.PLAYER_THROW_AMMO);
        }
        itemEntity.discard();
    }

    private void spawnMedicalBoxFromThrownItem(ItemEntity itemEntity) {
        if (itemEntity == null || !itemEntity.isAlive() || !(itemEntity.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = itemEntity.getPersistentData();
        if (!data.getBoolean(MEDICAL_BOX_THROW_TAG) || !data.hasUUID(MEDICAL_BOX_OWNER_TAG)) {
            return;
        }
        UUID ownerId = data.getUUID(MEDICAL_BOX_OWNER_TAG);
        removeExistingMedicalBoxOfOwner(ownerId);
        MedicalBoxEntity medicalBox = new MedicalBoxEntity(ConquestEntities.MEDICAL_BOX.get(), level);
        medicalBox.setPos(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
        medicalBox.setYRot(itemEntity.getYRot());
        medicalBox.setOwnerUuid(ownerId);
        medicalBox.setUseRemaining(MEDICAL_BOX_MAX_USE_COUNT);
        medicalBox.setHealth(100.0F);
        level.addFreshEntity(medicalBox);
        activeMedicalBoxIds.add(medicalBox.getUUID());
        ownerActiveMedicalBoxId.put(ownerId, medicalBox.getUUID());
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            FrontlineVoiceBridge.playCue(owner, RoomThirdPersonAudioCueRegistry.PLAYER_THROW_MEDKIT);
        } else {
            emitSupportBoxVoice(ownerId, null, medicalBox.position(), RoomThirdPersonAudioCueRegistry.PLAYER_THROW_MEDKIT);
        }
        itemEntity.discard();
    }

    private void processAmmoBoxAura(long gameTick) {
        if (activeAmmoBoxIds.isEmpty()) {
            clearAmmoBoxActionbars();
            return;
        }
        List<UUID> stale = new ArrayList<>();
        Map<UUID, AmmoBoxCandidate> bestCandidateByPlayer = new HashMap<>();
        for (UUID boxId : activeAmmoBoxIds) {
            Entity raw = findEntityByUuid(boxId);
            if (!(raw instanceof AmmoBoxEntity ammoBox) || !ammoBox.isAlive()) {
                stale.add(boxId);
                continue;
            }
            if (!(ammoBox.level() instanceof ServerLevel level)) {
                continue;
            }
            if (ammoBox.getUseRemaining() <= 0) {
                ammoBox.discard();
                stale.add(boxId);
                continue;
            }
            AABB area = ammoBox.getBoundingBox().inflate(AMMO_BOX_RADIUS, 1.0D, AMMO_BOX_RADIUS);
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area, player -> player != null && player.isAlive() && !player.isSpectator());
            for (ServerPlayer player : players) {
                double sqrDistance = player.distanceToSqr(ammoBox);
                if (sqrDistance > (AMMO_BOX_RADIUS * AMMO_BOX_RADIUS + 0.25D)) {
                    continue;
                }
                considerAmmoBoxCandidate(bestCandidateByPlayer, ammoBox, player, gameTick, sqrDistance);
            }
        }
        Set<UUID> shownPlayers = new HashSet<>();
        for (Map.Entry<UUID, AmmoBoxCandidate> entry : bestCandidateByPlayer.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (showAmmoBoxActionbarForPlayer(entry.getValue().ammoBox(), player, gameTick)) {
                shownPlayers.add(player.getUUID());
            }
        }
        // GD656Mods: 追加"配置点驱动"的永久箱 —— 零实体访问, 与投掷箱共用同一追踪表;
        // 放在投掷箱之后, 因此永久箱提示会覆盖投掷箱提示(地图箱优先)。
        shownPlayers.addAll(processPermanentAmmoBoxAura(gameTick));
        syncAmmoBoxActionbarTracking(shownPlayers);
        if (!stale.isEmpty()) {
            for (UUID boxId : stale) {
                removeTrackedAmmoBox(boxId, false);
            }
        }
    }

    public boolean tryUseNearbyAmmoBox(ServerPlayer player, long gameTick) {
        if (player == null) {
            return false;
        }
        // GD656Mods: 先尝试"配置点驱动"的永久箱 —— 不依赖实体/区块加载, 这是"看得见却用不了"的修复点。
        if (tryUseNearbyPermanentAmmoBox(player, gameTick)) {
            return true;
        }
        AmmoBoxCandidate candidate = resolveBestAmmoBoxCandidate(player, gameTick);
        if (candidate == null) {
            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT);
            playersWithAmmoBoxActionbar.remove(player.getUUID());
            return false;
        }
        return tryConsumeAmmoBoxForPlayer(candidate.ammoBox(), player, gameTick);
    }

    private void processMedicalBoxAura(long gameTick) {
        if (activeMedicalBoxIds.isEmpty()) {
            return;
        }
        List<UUID> stale = new ArrayList<>();
        for (UUID boxId : activeMedicalBoxIds) {
            Entity raw = findEntityByUuid(boxId);
            if (!(raw instanceof MedicalBoxEntity medicalBox) || !medicalBox.isAlive()) {
                stale.add(boxId);
                continue;
            }
            if (!(medicalBox.level() instanceof ServerLevel level)) {
                continue;
            }
            if (medicalBox.getUseRemaining() <= 0) {
                medicalBox.discard();
                stale.add(boxId);
                continue;
            }
            AABB area = medicalBox.getBoundingBox().inflate(MEDICAL_BOX_RADIUS, 1.0D, MEDICAL_BOX_RADIUS);
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area, player -> player != null && player.isAlive() && !player.isSpectator());
            for (ServerPlayer player : players) {
                double sqrDistance = player.distanceToSqr(medicalBox);
                if (sqrDistance > (MEDICAL_BOX_RADIUS * MEDICAL_BOX_RADIUS + 0.25D)) {
                    continue;
                }
                if (tryTriggerMedicalBoxForPlayer(medicalBox, player, gameTick) && medicalBox.getUseRemaining() <= 0) {
                    medicalBox.discard();
                    stale.add(boxId);
                    break;
                }
            }
        }
        // GD656Mods: 追加"配置点驱动"的永久医疗箱 —— 零实体访问
        processPermanentMedicalBoxAura(gameTick);
        if (!stale.isEmpty()) {
            for (UUID boxId : stale) {
                removeTrackedMedicalBox(boxId, false);
            }
        }
    }

    private boolean tryConsumeAmmoBoxForPlayer(AmmoBoxEntity ammoBox, ServerPlayer player, long gameTick) {
        RunningPlayerContext context = findRunningPlayerContext(player.getUUID());
        if (context == null) {
            return false;
        }
        if (gunmasterLoadoutService.isGunmasterRoom(context.room())) {
            return false;
        }
        UUID boxId = ammoBox.getUUID();
        UUID playerId = player.getUUID();
        Map<UUID, Long> cooldownUntilTick = ammoBoxCooldownUntilTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        Map<UUID, Long> lastNotifyTick = ammoBoxLastNotifyTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        Map<UUID, Long> infoPriorityUntilTick = ammoBoxInfoPriorityUntilTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        long untilTick = cooldownUntilTick.getOrDefault(playerId, 0L);
        if (untilTick > gameTick) {
            showAmmoBoxActionbarForPlayer(ammoBox, player, gameTick);
            return false;
        }
        if (!HeroKitSupportService.canRefillFromAmmoBox(context.state(), playerId)) {
            ServerActionBarService.showText(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, "精英兵暂时无法进行补给", 30);
            return false;
        }
        if (!playerLoadoutService.tryRefillLoadoutByAmmoBox(context.teamId(), player)) {
            return false;
        }
        player.serverLevel().playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvent.createVariableRangeEvent(AMMO_BAG_PICKUP_SOUND_ID),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
        cooldownUntilTick.put(playerId, gameTick + resolveAmmoBoxCooldownTicks(ammoBox));
        infoPriorityUntilTick.put(playerId, gameTick + AMMO_BOX_INFO_PRIORITY_TICKS);
        lastNotifyTick.put(playerId, gameTick);
        int remainingUse = ammoBox.consumeOneAndGetRemaining();
        ServerActionBarService.showComponent(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, buildAmmoBoxInfoActionbar(ammoBox), AMMO_BOX_INFO_PRIORITY_TICKS);
        playersWithAmmoBoxActionbar.add(playerId);
        // 队友获得弹药箱补给: 给放下者加弹药补给分(63 AMMO_SUPPLY)
        rewardBoxOwner(ammoBox.getOwnerUuid(), player, "AMMO_SUPPLY");
        if (remainingUse <= 0) {
            ammoBox.discard();
            removeTrackedAmmoBox(boxId, false);
        }
        return true;
    }

    private boolean showAmmoBoxActionbarForPlayer(AmmoBoxEntity ammoBox, ServerPlayer player, long gameTick) {
        RunningPlayerContext context = findRunningPlayerContext(player.getUUID());
        if (context == null || ammoBox == null || !ammoBox.isAlive()) {
            return false;
        }
        UUID boxId = ammoBox.getUUID();
        UUID playerId = player.getUUID();
        Map<UUID, Long> cooldownUntilTick = ammoBoxCooldownUntilTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        Map<UUID, Long> lastNotifyTick = ammoBoxLastNotifyTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        Map<UUID, Long> infoPriorityUntilTick = ammoBoxInfoPriorityUntilTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        long infoUntilTick = infoPriorityUntilTick.getOrDefault(playerId, -1L);
        if (infoUntilTick > gameTick) {
            ServerActionBarService.showComponent(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, buildAmmoBoxInfoActionbar(ammoBox), 25);
            lastNotifyTick.put(playerId, gameTick);
            return true;
        }
        long untilTick = cooldownUntilTick.getOrDefault(playerId, 0L);
        if (untilTick > gameTick) {
            int remainSeconds = Math.max(1, (int) Math.ceil((untilTick - gameTick) / 20.0D));
            ServerActionBarService.showComponent(
                player,
                ACTIONBAR_SOURCE_AMMO_BOX_INTERACT,
                ServerActionBarService.Templates.cooldownSeconds("房间弹药箱冷却中: ", remainSeconds),
                25
            );
            lastNotifyTick.put(playerId, gameTick);
            return true;
        }
        ServerActionBarService.showComponent(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, buildAmmoBoxPromptActionbar(), 25);
        lastNotifyTick.put(playerId, gameTick);
        return true;
    }

    private MutableComponent buildAmmoBoxPromptActionbar() {
        return ServerActionBarService.Templates.keyPrompt("F", "进行覆盖补给");
    }

    private boolean tryTriggerMedicalBoxForPlayer(MedicalBoxEntity medicalBox, ServerPlayer player, long gameTick) {
        if (findRunningPlayerContext(player.getUUID()) == null) {
            return false;
        }
        UUID boxId = medicalBox.getUUID();
        UUID playerId = player.getUUID();
        Map<UUID, Long> cooldownUntilTick = medicalBoxCooldownUntilTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        Map<UUID, Long> lastNotifyTick = medicalBoxLastNotifyTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        Map<UUID, Long> infoPriorityUntilTick = medicalBoxInfoPriorityUntilTickByBox.computeIfAbsent(boxId, key -> new HashMap<>());
        long untilTick = cooldownUntilTick.getOrDefault(playerId, 0L);
        if (untilTick > gameTick) {
            long nextNotifyTick = lastNotifyTick.getOrDefault(playerId, -100L) + 20L;
            if (gameTick >= nextNotifyTick) {
                long infoUntilTick = infoPriorityUntilTick.getOrDefault(playerId, -1L);
                if (infoUntilTick > gameTick) {
                    ServerMessageSender.rawActionBar(player, buildMedicalBoxInfoActionbar(medicalBox));
                } else {
                    int remainSeconds = Math.max(1, (int) Math.ceil((untilTick - gameTick) / 20.0D));
                    ServerMessageSender.rawActionBar(player, ServerActionBarService.Templates.cooldownSeconds("房间医疗箱冷却中: ", remainSeconds));
                }
                lastNotifyTick.put(playerId, gameTick);
            }
            return false;
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEDICAL_BOX_REGEN_DURATION_TICKS, MEDICAL_BOX_REGEN_AMPLIFIER, false, true, true));
        cooldownUntilTick.put(playerId, gameTick + resolveMedicalBoxCooldownTicks(medicalBox));
        infoPriorityUntilTick.put(playerId, gameTick + MEDICAL_BOX_INFO_PRIORITY_TICKS);
        lastNotifyTick.put(playerId, gameTick);
        medicalBox.consumeOneAndGetRemaining();
        ServerMessageSender.rawActionBar(player, buildMedicalBoxInfoActionbar(medicalBox));
        // 队友获得医疗箱补给: 给放下者加医疗补给分(64 HEALING)
        rewardBoxOwner(medicalBox.getOwnerUuid(), player, "HEALING");
        return true;
    }

    /**
     * 箱子补给分: 队友(非放下者本人)从箱子获得补给时, 给放下者加对应加分项。
     * 永久箱子(owner=null)、放下者离线、自己拿自己箱子均不加分。
     */
    private void rewardBoxOwner(UUID ownerId, ServerPlayer receiver, String bonusTypeName) {
        if (ownerId == null || receiver == null || ownerId.equals(receiver.getUUID())) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }
        if (!org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager.of(server).isSameCampPlayer(owner, receiver)) {
            return;
        }
        org.mods.gd656conquest.server.integration.KilliconScoreboardBridge.tryAwardConquestBonus(owner, bonusTypeName, 1.0F, "");
    }

    private MutableComponent buildAmmoBoxInfoActionbar(AmmoBoxEntity ammoBox) {
        boolean permanent = ammoBox != null && ammoBox.isPermanentSupport();
        int left = Math.max(0, ammoBox.getUseRemaining());
        String ownerName = resolveOwnerName(ammoBox);
        ChatFormatting remainColor = permanent ? ChatFormatting.LIGHT_PURPLE : (left <= 3 ? ChatFormatting.RED : (left <= 8 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        return ServerActionBarService.Templates.labeledPair(
            "房间弹药箱所属者: ",
            ownerName,
            ChatFormatting.AQUA,
            ChatFormatting.GOLD,
            "剩余使用次数: ",
            permanent ? "∞" : String.valueOf(left),
            ChatFormatting.AQUA,
            remainColor
        );
    }

    private void considerAmmoBoxCandidate(Map<UUID, AmmoBoxCandidate> bestCandidateByPlayer, AmmoBoxEntity ammoBox, ServerPlayer player, long gameTick, double sqrDistance) {
        RunningPlayerContext context = findRunningPlayerContext(player.getUUID());
        if (context == null) {
            return;
        }
        boolean availableNow = isAmmoBoxAvailableForPlayer(ammoBox, player, gameTick);
        AmmoBoxCandidate current = bestCandidateByPlayer.get(player.getUUID());
        if (current == null
            || (availableNow && !current.availableNow())
            || (availableNow == current.availableNow() && sqrDistance < current.sqrDistance())) {
            bestCandidateByPlayer.put(player.getUUID(), new AmmoBoxCandidate(ammoBox, sqrDistance, availableNow));
        }
    }

    private AmmoBoxCandidate resolveBestAmmoBoxCandidate(ServerPlayer player, long gameTick) {
        if (player == null) {
            return null;
        }
        AmmoBoxCandidate bestCandidate = null;
        for (UUID boxId : activeAmmoBoxIds) {
            Entity raw = findEntityByUuid(boxId);
            if (!(raw instanceof AmmoBoxEntity ammoBox) || !ammoBox.isAlive() || ammoBox.getUseRemaining() <= 0) {
                continue;
            }
            if (player.level() != ammoBox.level()) {
                continue;
            }
            double sqrDistance = player.distanceToSqr(ammoBox);
            if (sqrDistance > (AMMO_BOX_RADIUS * AMMO_BOX_RADIUS + 0.25D)) {
                continue;
            }
            boolean availableNow = isAmmoBoxAvailableForPlayer(ammoBox, player, gameTick);
            if (bestCandidate == null
                || (availableNow && !bestCandidate.availableNow())
                || (availableNow == bestCandidate.availableNow() && sqrDistance < bestCandidate.sqrDistance())) {
                bestCandidate = new AmmoBoxCandidate(ammoBox, sqrDistance, availableNow);
            }
        }
        return bestCandidate;
    }

    private boolean isAmmoBoxAvailableForPlayer(AmmoBoxEntity ammoBox, ServerPlayer player, long gameTick) {
        if (ammoBox == null || player == null) {
            return false;
        }
        Map<UUID, Long> cooldownUntilTick = ammoBoxCooldownUntilTickByBox.computeIfAbsent(ammoBox.getUUID(), key -> new HashMap<>());
        return cooldownUntilTick.getOrDefault(player.getUUID(), 0L) <= gameTick;
    }

    private void syncAmmoBoxActionbarTracking(Set<UUID> shownPlayers) {
        if (shownPlayers == null) {
            shownPlayers = Set.of();
        }
        if (!playersWithAmmoBoxActionbar.isEmpty()) {
            List<UUID> stalePlayers = new ArrayList<>();
            for (UUID playerId : playersWithAmmoBoxActionbar) {
                if (!shownPlayers.contains(playerId)) {
                    stalePlayers.add(playerId);
                }
            }
            for (UUID playerId : stalePlayers) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT);
                }
            }
        }
        playersWithAmmoBoxActionbar.clear();
        playersWithAmmoBoxActionbar.addAll(shownPlayers);
    }

    private void clearAmmoBoxActionbars() {
        syncAmmoBoxActionbarTracking(Set.of());
    }

    private MutableComponent buildMedicalBoxInfoActionbar(MedicalBoxEntity medicalBox) {
        boolean permanent = medicalBox != null && medicalBox.isPermanentSupport();
        int left = Math.max(0, medicalBox.getUseRemaining());
        String ownerName = resolveOwnerName(medicalBox);
        ChatFormatting remainColor = permanent ? ChatFormatting.LIGHT_PURPLE : (left <= 3 ? ChatFormatting.RED : (left <= 8 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        return ServerActionBarService.Templates.labeledPair(
            "房间医疗箱所属者: ",
            ownerName,
            ChatFormatting.AQUA,
            ChatFormatting.GOLD,
            "剩余使用次数: ",
            permanent ? "∞" : String.valueOf(left),
            ChatFormatting.AQUA,
            remainColor
        );
    }

    private String resolveOwnerName(AmmoBoxEntity ammoBox) {
        if (ammoBox != null && ammoBox.isPermanentSupport()) {
            return "对局永久弹药箱";
        }
        return resolveOwnerName(ammoBox == null ? null : ammoBox.getOwnerUuid());
    }

    private String resolveOwnerName(MedicalBoxEntity medicalBox) {
        if (medicalBox != null && medicalBox.isPermanentSupport()) {
            return "对局永久医疗箱";
        }
        return resolveOwnerName(medicalBox == null ? null : medicalBox.getOwnerUuid());
    }

    private String resolveOwnerName(UUID ownerId) {
        if (ownerId == null) {
            return "未知";
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        return owner == null ? "未知" : owner.getGameProfile().getName();
    }

    private int resolveAmmoBoxCooldownTicks(AmmoBoxEntity ammoBox) {
        return ammoBox != null && ammoBox.isPermanentSupport()
            ? PERMANENT_SUPPORT_BOX_PER_PLAYER_CD_TICKS
            : AMMO_BOX_PER_PLAYER_CD_TICKS;
    }

    private int resolveMedicalBoxCooldownTicks(MedicalBoxEntity medicalBox) {
        return medicalBox != null && medicalBox.isPermanentSupport()
            ? PERMANENT_SUPPORT_BOX_PER_PLAYER_CD_TICKS
            : MEDICAL_BOX_PER_PLAYER_CD_TICKS;
    }

    private void emitSupportBoxVoice(UUID ownerId, UUID receiverId, Vec3 boxPosition, String cueId) {
        if (ownerId == null || boxPosition == null || cueId == null || cueId.isBlank()) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        ServerPlayer receiver = receiverId == null ? null : server.getPlayerList().getPlayer(receiverId);
        if (receiverId != null && ownerId.equals(receiverId)) {
            receiver = null;
        }
        ServerPlayer sourcePlayer = owner != null ? owner : receiver;
        if (sourcePlayer == null) {
            return;
        }
        FrontlineVoiceBridge.playCue(sourcePlayer, cueId);
    }

    private void removeExistingAmmoBoxOfOwner(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        UUID existingId = ownerActiveAmmoBoxId.get(ownerId);
        if (existingId == null) {
            return;
        }
        Entity raw = findEntityByUuid(existingId);
        if (raw instanceof AmmoBoxEntity ammoBox && ammoBox.isAlive()) {
            ammoBox.discard();
        }
        activeAmmoBoxIds.remove(existingId);
        ammoBoxCooldownUntilTickByBox.remove(existingId);
        ammoBoxLastNotifyTickByBox.remove(existingId);
        ammoBoxInfoPriorityUntilTickByBox.remove(existingId);
        ownerActiveAmmoBoxId.remove(ownerId);
    }

    private void removeExistingMedicalBoxOfOwner(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        UUID existingId = ownerActiveMedicalBoxId.get(ownerId);
        if (existingId == null) {
            return;
        }
        Entity raw = findEntityByUuid(existingId);
        if (raw instanceof MedicalBoxEntity medicalBox && medicalBox.isAlive()) {
            medicalBox.discard();
        }
        activeMedicalBoxIds.remove(existingId);
        medicalBoxCooldownUntilTickByBox.remove(existingId);
        medicalBoxLastNotifyTickByBox.remove(existingId);
        medicalBoxInfoPriorityUntilTickByBox.remove(existingId);
        ownerActiveMedicalBoxId.remove(ownerId);
    }

    private RunningPlayerContext findRunningPlayerContext(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return null;
        }
        Optional<RoomDefinition> room = runtimeRepository.findRoom(roomId.get());
        Optional<RoomRuntimeState> runtime = runtimeRepository.findRuntime(roomId.get());
        if (room.isEmpty() || runtime.isEmpty()) {
            return null;
        }
        if (room.get().lifecycleState() != RoomLifecycleState.RUNNING && room.get().lifecycleState() != RoomLifecycleState.ENDING) {
            return null;
        }
        RunningRuntimeState runningState = runtime.get().getRunningState();
        if (!runningState.getRoomPlayers().contains(playerId)) {
            return null;
        }
        RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
        if (teamId == null) {
            return null;
        }
        return new RunningPlayerContext(room.get(), runningState, teamId);
    }

    private Entity findEntityByUuid(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private void removeTrackedAmmoBox(UUID boxId, boolean discardEntity) {
        if (boxId == null) {
            return;
        }
        if (discardEntity) {
            Entity raw = findEntityByUuid(boxId);
            if (raw instanceof AmmoBoxEntity ammoBox && ammoBox.isAlive()) {
                ammoBox.discard();
            }
        }
        activeAmmoBoxIds.remove(boxId);
        ammoBoxCooldownUntilTickByBox.remove(boxId);
        ammoBoxLastNotifyTickByBox.remove(boxId);
        ammoBoxInfoPriorityUntilTickByBox.remove(boxId);
        ownerActiveAmmoBoxId.entrySet().removeIf(entry -> boxId.equals(entry.getValue()));
        permanentAmmoBoxIdsByRoom.values().forEach(ids -> ids.remove(boxId));
    }

    private void removeTrackedMedicalBox(UUID boxId, boolean discardEntity) {
        if (boxId == null) {
            return;
        }
        if (discardEntity) {
            Entity raw = findEntityByUuid(boxId);
            if (raw instanceof MedicalBoxEntity medicalBox && medicalBox.isAlive()) {
                medicalBox.discard();
            }
        }
        activeMedicalBoxIds.remove(boxId);
        medicalBoxCooldownUntilTickByBox.remove(boxId);
        medicalBoxLastNotifyTickByBox.remove(boxId);
        medicalBoxInfoPriorityUntilTickByBox.remove(boxId);
        ownerActiveMedicalBoxId.entrySet().removeIf(entry -> boxId.equals(entry.getValue()));
        permanentMedicalBoxIdsByRoom.values().forEach(ids -> ids.remove(boxId));
    }

    private ServerLevel resolveRoomLevel(RoomDefinition room) {
        if (room == null || room.commonConfig().dimensionId() == null || room.commonConfig().dimensionId().isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(room.commonConfig().dimensionId());
        if (location == null) {
            return null;
        }
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, location);
        return server.getLevel(key);
    }

    private List<RoomPosition> resolvePermanentAmmoBoxPositions(RoomDefinition room) {
        if (room == null) {
            return List.of();
        }
        // GD656Mods: 枪王模式有自己的配置项(gunmasterConfig.ammoBoxPositions) —— 不能走下面的
        // isArenaGameplay() 分支: 那条路会读到团队死斗(arenaConfig)的配置, 表现为"配置项能填却不生效"。
        if (gunmasterLoadoutService.isGunmasterRoom(room)) {
            org.mods.gd656conquest.server.room.model.RoomGunmasterConfig gunmaster = room.gunmasterConfig();
            return gunmaster == null ? List.of() : gunmaster.ammoBoxPositions();
        }
        if (room.isArenaGameplay()) {
            return room.arenaConfig().ammoBoxPositions();
        }
        if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            RoomBreakthroughConfig breakthrough = room.breakthroughConfig();
            return breakthrough == null ? List.of() : breakthrough.ammoBoxPositions();
        }
        // GD656Mods: 突袭模式同样有配置项(rushConfig.ammoBoxPositions), 原本被硬编码忽略
        if (RoomGameplayId.RUSH.equals(room.gameplayId())) {
            org.mods.gd656conquest.server.room.model.RoomRushConfig rush = room.rushConfig();
            return rush == null ? List.of() : rush.ammoBoxPositions();
        }
        RoomConquestConfig conquest = room.conquestConfig();
        return conquest == null ? List.of() : conquest.ammoBoxPositions();
    }

    private List<RoomPosition> resolvePermanentMedicalBoxPositions(RoomDefinition room) {
        if (room == null) {
            return List.of();
        }
        // GD656Mods: 枪王模式的医疗箱(gmMedicalBoxPositions)必须在这里分派 —— 否则 isArenaGameplay()
        // 会把枪王也算作 arena, 读到团队死斗的配置。
        if (gunmasterLoadoutService.isGunmasterRoom(room)) {
            org.mods.gd656conquest.server.room.model.RoomGunmasterConfig gunmaster = room.gunmasterConfig();
            return gunmaster == null ? List.of() : gunmaster.medicalBoxPositions();
        }
        if (room.isArenaGameplay()) {
            return room.arenaConfig().medicalBoxPositions();
        }
        if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            RoomBreakthroughConfig breakthrough = room.breakthroughConfig();
            return breakthrough == null ? List.of() : breakthrough.medicalBoxPositions();
        }
        // GD656Mods: 突袭模式医疗箱配置(rsMedicalBoxPositions)原本被硬编码忽略
        if (RoomGameplayId.RUSH.equals(room.gameplayId())) {
            org.mods.gd656conquest.server.room.model.RoomRushConfig rush = room.rushConfig();
            return rush == null ? List.of() : rush.medicalBoxPositions();
        }
        RoomConquestConfig conquest = room.conquestConfig();
        return conquest == null ? List.of() : conquest.medicalBoxPositions();
    }

    private record RunningPlayerContext(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
    }

    // ==================== GD656Mods: 永久箱(配置点驱动) ====================
    // 地图配置的弹药箱/医疗箱以"配置点"为身份: 判定、显示、使用全部只看坐标, 不查实体。
    // 实体仍然生成(仅用于玩家可见性), 因此"区块未加载 / 实体被重建 / 维度重载"都不再影响功能。

    /** GD656Mods: 按房间取维度 —— 每 2 tick 一次, findRoom 与 getLevel 都是 O(1), 无遍历。 */
    private ServerLevel resolveRoomLevelByRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager manager =
            org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager.of(server);
        if (!manager.isInitialized()) {
            return null;
        }
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        return room == null ? null : resolveRoomLevel(room);
    }

    private static boolean isWithinBoxReach(ServerPlayer player, double x, double y, double z, int radius) {
        if (player == null) {
            return false;
        }
        return player.distanceToSqr(x, y, z) <= (radius * (double) radius + 0.25D);
    }

    /** GD656Mods: 永久弹药箱的靠近提示 —— 返回"本轮已显示提示"的玩家, 供共用追踪表清理。 */
    private Set<UUID> processPermanentAmmoBoxAura(long gameTick) {
        if (permanentAmmoPointsByRoom.isEmpty()) {
            return Set.of();
        }
        Set<UUID> shown = new HashSet<>();
        for (Map.Entry<String, List<PermanentBoxPoint>> entry : permanentAmmoPointsByRoom.entrySet()) {
            String roomId = entry.getKey();
            ServerLevel level = resolveRoomLevelByRoomId(roomId);
            if (level == null) {
                continue;
            }
            // 只遍历该维度在线玩家(玩家数远小于世界范围), 不做任何世界查询
            List<ServerPlayer> players = List.copyOf(level.players());
            for (PermanentBoxPoint point : entry.getValue()) {
                String boxKey = permanentBoxKey(roomId, point.index());
                for (ServerPlayer player : players) {
                    if (!player.isAlive() || player.isSpectator()) {
                        continue;
                    }
                    if (!isWithinBoxReach(player, point.x(), point.y(), point.z(), AMMO_BOX_RADIUS)) {
                        continue;
                    }
                    if (showPermanentAmmoBoxActionbarForPlayer(boxKey, player, gameTick)) {
                        shown.add(player.getUUID());
                    }
                }
            }
        }
        return shown;
    }

    /** GD656Mods: 永久医疗箱靠近即自动触发。 */
    private void processPermanentMedicalBoxAura(long gameTick) {
        if (permanentMedicalPointsByRoom.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<PermanentBoxPoint>> entry : permanentMedicalPointsByRoom.entrySet()) {
            String roomId = entry.getKey();
            ServerLevel level = resolveRoomLevelByRoomId(roomId);
            if (level == null) {
                continue;
            }
            List<ServerPlayer> players = List.copyOf(level.players());
            for (PermanentBoxPoint point : entry.getValue()) {
                String boxKey = permanentBoxKey(roomId, point.index());
                for (ServerPlayer player : players) {
                    if (!player.isAlive() || player.isSpectator()) {
                        continue;
                    }
                    if (!isWithinBoxReach(player, point.x(), point.y(), point.z(), MEDICAL_BOX_RADIUS)) {
                        continue;
                    }
                    tryTriggerPermanentMedicalBox(boxKey, player, gameTick);
                }
            }
        }
    }

    /** GD656Mods: 按键使用 —— 就近取一个"可用(不在冷却中)"的永久弹药箱。 */
    private boolean tryUseNearbyPermanentAmmoBox(ServerPlayer player, long gameTick) {
        String roomId = resolveRunningRoomId(player.getUUID());
        if (roomId == null) {
            return false;
        }
        List<PermanentBoxPoint> points = permanentAmmoPointsByRoom.get(roomId);
        if (points == null || points.isEmpty()) {
            return false;
        }
        String bestKey = null;
        double bestDistance = Double.MAX_VALUE;
        boolean bestAvailable = false;
        for (PermanentBoxPoint point : points) {
            if (!isWithinBoxReach(player, point.x(), point.y(), point.z(), AMMO_BOX_RADIUS)) {
                continue;
            }
            String boxKey = permanentBoxKey(roomId, point.index());
            double distance = player.distanceToSqr(point.x(), point.y(), point.z());
            boolean available = isPermanentAmmoBoxAvailable(boxKey, player);
            if (bestKey == null
                || (available && !bestAvailable)
                || (available == bestAvailable && distance < bestDistance)) {
                bestKey = boxKey;
                bestDistance = distance;
                bestAvailable = available;
            }
        }
        if (bestKey == null) {
            return false;
        }
        return tryConsumePermanentAmmoBox(bestKey, player, gameTick);
    }

    private boolean isPermanentAmmoBoxAvailable(String boxKey, ServerPlayer player) {
        Map<UUID, Long> cooldown = permanentAmmoCooldownUntilTickByBox.get(boxKey);
        if (cooldown == null) {
            return true;
        }
        return cooldown.getOrDefault(player.getUUID(), 0L) <= server.getTickCount();
    }

    /** GD656Mods: 永久弹药箱的实际补给 —— 与投掷箱同样的补给链路, 但跳过"扣次数/销毁/给放下者计分"(它没有实体与所属者)。 */
    private boolean tryConsumePermanentAmmoBox(String boxKey, ServerPlayer player, long gameTick) {
        RunningPlayerContext context = findRunningPlayerContext(player.getUUID());
        if (context == null || gunmasterLoadoutService.isGunmasterRoom(context.room())) {
            return false;
        }
        UUID playerId = player.getUUID();
        Map<UUID, Long> cooldownUntilTick = permanentAmmoCooldownUntilTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        Map<UUID, Long> lastNotifyTick = permanentAmmoLastNotifyTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        Map<UUID, Long> infoPriorityUntilTick = permanentAmmoInfoPriorityUntilTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        if (cooldownUntilTick.getOrDefault(playerId, 0L) > gameTick) {
            showPermanentAmmoBoxActionbarForPlayer(boxKey, player, gameTick);
            return false;
        }
        if (!HeroKitSupportService.canRefillFromAmmoBox(context.state(), playerId)) {
            ServerActionBarService.showText(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, "精英兵暂时无法进行补给", 30);
            return false;
        }
        if (!playerLoadoutService.tryRefillLoadoutByAmmoBox(context.teamId(), player)) {
            return false;
        }
        player.serverLevel().playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvent.createVariableRangeEvent(AMMO_BAG_PICKUP_SOUND_ID),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
        cooldownUntilTick.put(playerId, gameTick + PERMANENT_SUPPORT_BOX_PER_PLAYER_CD_TICKS);
        infoPriorityUntilTick.put(playerId, gameTick + AMMO_BOX_INFO_PRIORITY_TICKS);
        lastNotifyTick.put(playerId, gameTick);
        ServerActionBarService.showComponent(
            player,
            ACTIONBAR_SOURCE_AMMO_BOX_INTERACT,
            buildPermanentSupportBoxInfoActionbar("房间弹药箱所属者: "),
            AMMO_BOX_INFO_PRIORITY_TICKS
        );
        playersWithAmmoBoxActionbar.add(playerId);
        return true;
    }

    private boolean tryTriggerPermanentMedicalBox(String boxKey, ServerPlayer player, long gameTick) {
        if (findRunningPlayerContext(player.getUUID()) == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        Map<UUID, Long> cooldownUntilTick = permanentMedicalCooldownUntilTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        Map<UUID, Long> lastNotifyTick = permanentMedicalLastNotifyTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        Map<UUID, Long> infoPriorityUntilTick = permanentMedicalInfoPriorityUntilTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        long untilTick = cooldownUntilTick.getOrDefault(playerId, 0L);
        if (untilTick > gameTick) {
            long nextNotifyTick = lastNotifyTick.getOrDefault(playerId, -100L) + 20L;
            if (gameTick >= nextNotifyTick) {
                if (infoPriorityUntilTick.getOrDefault(playerId, -1L) > gameTick) {
                    ServerMessageSender.rawActionBar(player, buildPermanentSupportBoxInfoActionbar("房间医疗箱所属者: "));
                } else {
                    int remainSeconds = Math.max(1, (int) Math.ceil((untilTick - gameTick) / 20.0D));
                    ServerMessageSender.rawActionBar(player, ServerActionBarService.Templates.cooldownSeconds("房间医疗箱冷却中: ", remainSeconds));
                }
                lastNotifyTick.put(playerId, gameTick);
            }
            return false;
        }
        player.addEffect(new MobEffectInstance(
            MobEffects.REGENERATION,
            MEDICAL_BOX_REGEN_DURATION_TICKS,
            MEDICAL_BOX_REGEN_AMPLIFIER,
            false,
            true,
            true
        ));
        cooldownUntilTick.put(playerId, gameTick + PERMANENT_SUPPORT_BOX_PER_PLAYER_CD_TICKS);
        infoPriorityUntilTick.put(playerId, gameTick + MEDICAL_BOX_INFO_PRIORITY_TICKS);
        lastNotifyTick.put(playerId, gameTick);
        ServerMessageSender.rawActionBar(player, buildPermanentSupportBoxInfoActionbar("房间医疗箱所属者: "));
        return true;
    }

    private boolean showPermanentAmmoBoxActionbarForPlayer(String boxKey, ServerPlayer player, long gameTick) {
        if (findRunningPlayerContext(player.getUUID()) == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        Map<UUID, Long> cooldownUntilTick = permanentAmmoCooldownUntilTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        Map<UUID, Long> lastNotifyTick = permanentAmmoLastNotifyTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        Map<UUID, Long> infoPriorityUntilTick = permanentAmmoInfoPriorityUntilTickByBox.computeIfAbsent(boxKey, key -> new HashMap<>());
        long infoUntilTick = infoPriorityUntilTick.getOrDefault(playerId, -1L);
        if (infoUntilTick > gameTick) {
            ServerActionBarService.showComponent(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, buildPermanentSupportBoxInfoActionbar("房间弹药箱所属者: "), 25);
            lastNotifyTick.put(playerId, gameTick);
            return true;
        }
        long untilTick = cooldownUntilTick.getOrDefault(playerId, 0L);
        if (untilTick > gameTick) {
            int remainSeconds = Math.max(1, (int) Math.ceil((untilTick - gameTick) / 20.0D));
            ServerActionBarService.showComponent(
                player,
                ACTIONBAR_SOURCE_AMMO_BOX_INTERACT,
                ServerActionBarService.Templates.cooldownSeconds("房间弹药箱冷却中: ", remainSeconds),
                25
            );
            lastNotifyTick.put(playerId, gameTick);
            return true;
        }
        ServerActionBarService.showComponent(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, buildAmmoBoxPromptActionbar(), 25);
        lastNotifyTick.put(playerId, gameTick);
        return true;
    }

    /**
     * GD656Mods: 永久支援箱(坐标驱动)的通用信息条 —— 弹药箱与医疗箱原本是两份逐字重复的方法,
     * 唯一差别就是开头那一段所属者标签, 这里收敛成一个带标签参数的实现。
     */
    private MutableComponent buildPermanentSupportBoxInfoActionbar(String ownerLabel) {
        return ServerActionBarService.Templates.labeledPair(
            ownerLabel,
            "地图补给",
            ChatFormatting.AQUA,
            ChatFormatting.GOLD,
            "剩余使用次数: ",
            "∞",
            ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE
        );
    }

    /** GD656Mods: 该玩家当前所在房间 id(用于把配置点定位到房间)。 */
    private String resolveRunningRoomId(UUID playerId) {
        RunningPlayerContext context = findRunningPlayerContext(playerId);
        return context == null || context.room() == null ? null : context.room().roomId();
    }

    private record AmmoBoxCandidate(AmmoBoxEntity ammoBox, double sqrDistance, boolean availableNow) {
    }
}
