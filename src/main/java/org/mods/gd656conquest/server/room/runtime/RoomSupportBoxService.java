package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
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

    private final Set<UUID> pendingAmmoBoxThrows = new HashSet<>();
    private final Set<UUID> activeAmmoBoxIds = new HashSet<>();
    private final Set<UUID> playersWithAmmoBoxActionbar = new HashSet<>();
    private final Map<UUID, UUID> ownerActiveAmmoBoxId = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> ammoBoxCooldownUntilTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> ammoBoxLastNotifyTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> ammoBoxInfoPriorityUntilTickByBox = new HashMap<>();

    private final Set<UUID> pendingMedicalBoxThrows = new HashSet<>();
    private final Set<UUID> activeMedicalBoxIds = new HashSet<>();
    private final Map<UUID, UUID> ownerActiveMedicalBoxId = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> medicalBoxCooldownUntilTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> medicalBoxLastNotifyTickByBox = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> medicalBoxInfoPriorityUntilTickByBox = new HashMap<>();

    public RoomSupportBoxService(
        MinecraftServer server,
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService,
        RoomPlayerLoadoutService playerLoadoutService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.playerLoadoutService = Objects.requireNonNull(playerLoadoutService, "playerLoadoutService");
    }

    public void tick(long gameTick) {
        processPendingMedicalBoxThrows();
        processPendingAmmoBoxThrows();
        processMedicalBoxAura(gameTick);
        processAmmoBoxAura(gameTick);
    }

    public boolean tryUseAmmoBoxItem(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
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
        emitSupportBoxVoice(ownerId, null, ammoBox.position(), RoomThirdPersonAudioCueRegistry.PLAYER_THROW_AMMO);
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
        syncAmmoBoxActionbarTracking(shownPlayers);
        if (!stale.isEmpty()) {
            activeAmmoBoxIds.removeAll(stale);
            for (UUID boxId : stale) {
                ammoBoxCooldownUntilTickByBox.remove(boxId);
                ammoBoxLastNotifyTickByBox.remove(boxId);
                ammoBoxInfoPriorityUntilTickByBox.remove(boxId);
                ownerActiveAmmoBoxId.entrySet().removeIf(entry -> boxId.equals(entry.getValue()));
            }
        }
    }

    public boolean tryUseNearbyAmmoBox(ServerPlayer player, long gameTick) {
        if (player == null) {
            return false;
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
        if (!stale.isEmpty()) {
            activeMedicalBoxIds.removeAll(stale);
            for (UUID boxId : stale) {
                medicalBoxCooldownUntilTickByBox.remove(boxId);
                medicalBoxLastNotifyTickByBox.remove(boxId);
                medicalBoxInfoPriorityUntilTickByBox.remove(boxId);
                ownerActiveMedicalBoxId.entrySet().removeIf(entry -> boxId.equals(entry.getValue()));
            }
        }
    }

    private boolean tryConsumeAmmoBoxForPlayer(AmmoBoxEntity ammoBox, ServerPlayer player, long gameTick) {
        RunningPlayerContext context = findRunningPlayerContext(player.getUUID());
        if (context == null) {
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
        cooldownUntilTick.put(playerId, gameTick + AMMO_BOX_PER_PLAYER_CD_TICKS);
        infoPriorityUntilTick.put(playerId, gameTick + AMMO_BOX_INFO_PRIORITY_TICKS);
        lastNotifyTick.put(playerId, gameTick);
        int remainingUse = ammoBox.consumeOneAndGetRemaining();
        ServerActionBarService.showComponent(player, ACTIONBAR_SOURCE_AMMO_BOX_INTERACT, buildAmmoBoxInfoActionbar(ammoBox), AMMO_BOX_INFO_PRIORITY_TICKS);
        playersWithAmmoBoxActionbar.add(playerId);
        if (remainingUse <= 0) {
            ammoBox.discard();
            activeAmmoBoxIds.remove(boxId);
            ammoBoxCooldownUntilTickByBox.remove(boxId);
            ammoBoxLastNotifyTickByBox.remove(boxId);
            ammoBoxInfoPriorityUntilTickByBox.remove(boxId);
            ownerActiveAmmoBoxId.entrySet().removeIf(entry -> boxId.equals(entry.getValue()));
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
        cooldownUntilTick.put(playerId, gameTick + MEDICAL_BOX_PER_PLAYER_CD_TICKS);
        infoPriorityUntilTick.put(playerId, gameTick + MEDICAL_BOX_INFO_PRIORITY_TICKS);
        lastNotifyTick.put(playerId, gameTick);
        medicalBox.consumeOneAndGetRemaining();
        ServerMessageSender.rawActionBar(player, buildMedicalBoxInfoActionbar(medicalBox));
        return true;
    }

    private MutableComponent buildAmmoBoxInfoActionbar(AmmoBoxEntity ammoBox) {
        int left = Math.max(0, ammoBox.getUseRemaining());
        String ownerName = resolveOwnerName(ammoBox.getOwnerUuid());
        ChatFormatting remainColor = left <= 3 ? ChatFormatting.RED : (left <= 8 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
        return ServerActionBarService.Templates.labeledPair(
            "房间弹药箱所属者: ",
            ownerName,
            ChatFormatting.AQUA,
            ChatFormatting.GOLD,
            "剩余使用次数: ",
            String.valueOf(left),
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
        int left = Math.max(0, medicalBox.getUseRemaining());
        String ownerName = resolveOwnerName(medicalBox.getOwnerUuid());
        ChatFormatting remainColor = left <= 3 ? ChatFormatting.RED : (left <= 8 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
        return ServerActionBarService.Templates.labeledPair(
            "房间医疗箱所属者: ",
            ownerName,
            ChatFormatting.AQUA,
            ChatFormatting.GOLD,
            "剩余使用次数: ",
            String.valueOf(left),
            ChatFormatting.AQUA,
            remainColor
        );
    }

    private String resolveOwnerName(UUID ownerId) {
        if (ownerId == null) {
            return "未知";
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        return owner == null ? "未知" : owner.getGameProfile().getName();
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
        if (!FrontlineVoiceBridge.playCueAt(sourcePlayer, boxPosition, cueId)) {
            FrontlineVoiceBridge.playCue(sourcePlayer, cueId);
        }
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

    private record RunningPlayerContext(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
    }

    private record AmmoBoxCandidate(AmmoBoxEntity ammoBox, double sqrDistance, boolean availableNow) {
    }
}
