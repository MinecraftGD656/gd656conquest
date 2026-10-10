package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.room.core.RoomPlayerSessionService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeRepository;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoomDeathSupportDropService {
    private static final String DEATH_SUPPORT_DROP_OWNER_UUID = "gd656_death_support_drop_owner_uuid";

    private final MinecraftServer server;
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomPlayerSessionService playerSessionService;

    public RoomDeathSupportDropService(
        MinecraftServer server,
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
    }

    public void handlePlayerKillSupportDrop(ServerPlayer victim, ServerPlayer killer) {
        if (victim == null || killer == null || victim.server == null || killer.server == null) {
            return;
        }
        if (victim == killer || victim.level() != killer.level()) {
            return;
        }
        UUID victimId = victim.getUUID();
        UUID killerId = killer.getUUID();
        Optional<String> victimRoomId = playerSessionService.findRoomId(victimId);
        Optional<String> killerRoomId = playerSessionService.findRoomId(killerId);
        if (victimRoomId.isEmpty() || killerRoomId.isEmpty() || !victimRoomId.get().equalsIgnoreCase(killerRoomId.get())) {
            return;
        }
        Optional<RoomDefinition> roomOptional = runtimeRepository.findRoom(victimRoomId.get());
        Optional<RoomRuntimeState> runtimeOptional = runtimeRepository.findRuntime(victimRoomId.get());
        if (roomOptional.isEmpty() || runtimeOptional.isEmpty()) {
            return;
        }
        RoomDefinition room = roomOptional.get();
        if (room == null
            || (room.lifecycleState() != RoomLifecycleState.RUNNING && room.lifecycleState() != RoomLifecycleState.ENDING)
            || !room.commonConfig().enableDeathSupportDrop()) {
            return;
        }
        RoomRuntimeState runtimeState = runtimeOptional.get();
        if (runtimeState == null
            || runtimeState.getRunningState() == null
            || !runtimeState.getRunningState().getRoomPlayers().contains(victimId)
            || !runtimeState.getRunningState().getRoomPlayers().contains(killerId)) {
            return;
        }

        ItemStack dropStack = RoomGameplayId.GUNMASTER.equals(room.gameplayId())
            ? new ItemStack(ConquestItems.MEDIC_BAG.get())
            : (server.overworld().random.nextBoolean()
                ? new ItemStack(ConquestItems.AMMO_BAG.get())
                : new ItemStack(ConquestItems.MEDIC_BAG.get()));
        if (dropStack.isEmpty()) {
            return;
        }

        ItemEntity dropEntity = new ItemEntity(
            victim.level(),
            victim.getX(),
            victim.getY() + 0.25D,
            victim.getZ(),
            dropStack
        );
        dropEntity.setPickUpDelay(0);
        dropEntity.getPersistentData().putUUID(DEATH_SUPPORT_DROP_OWNER_UUID, killerId);
        org.mods.gd656conquest.server.support.SupportRuntime.markDeathReward(dropEntity, victim);
        victim.level().addFreshEntity(dropEntity);
    }

    public static boolean isPickupBlocked(ServerPlayer player, ItemEntity itemEntity) {
        if (player == null || itemEntity == null) {
            return false;
        }
        if (!isDeathSupportDrop(itemEntity)) {
            return false;
        }
        return !player.getUUID().equals(itemEntity.getPersistentData().getUUID(DEATH_SUPPORT_DROP_OWNER_UUID));
    }

    public static boolean isDeathSupportDrop(ItemEntity itemEntity) {
        return itemEntity != null && itemEntity.getPersistentData().hasUUID(DEATH_SUPPORT_DROP_OWNER_UUID);
    }

    public static UUID getPickupOwner(ItemEntity itemEntity) {
        if (!isDeathSupportDrop(itemEntity)) {
            return null;
        }
        return itemEntity.getPersistentData().getUUID(DEATH_SUPPORT_DROP_OWNER_UUID);
    }
}
