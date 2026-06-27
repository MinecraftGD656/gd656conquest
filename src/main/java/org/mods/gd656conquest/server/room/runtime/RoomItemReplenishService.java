package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RoomItemReplenishService {
    private final MinecraftServer server;
    private final RoomPlayerLoadoutService loadoutService;

    public RoomItemReplenishService(MinecraftServer server, RoomPlayerLoadoutService loadoutService) {
        this.server = Objects.requireNonNull(server, "server");
        this.loadoutService = Objects.requireNonNull(loadoutService, "loadoutService");
    }

    public void tick(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || gameTick <= 0L || gameTick % 20L != 0L) {
            return;
        }
        for (UUID playerId : List.copyOf(state.getRoomPlayers())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator() || state.isInRespawnFlow(playerId)) {
                continue;
            }
            if (replenishPlayerItems(state, player, gameTick)) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        }
    }

    private boolean replenishPlayerItems(RunningRuntimeState state, ServerPlayer player, long gameTick) {
        Map<Integer, Long> lastReplenishTickBySlot = state.getLoadoutItemLastReplenishTickByPlayerSlot()
            .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        boolean changed = false;
        for (RoomPlayerLoadoutService.ResolvedLoadoutItem item : loadoutService.resolveConfiguredLoadoutItems(player).values()) {
            changed |= replenishConfiguredItem(player, item, gameTick, lastReplenishTickBySlot);
        }
        return changed;
    }

    private boolean replenishConfiguredItem(
        ServerPlayer player,
        RoomPlayerLoadoutService.ResolvedLoadoutItem item,
        long gameTick,
        Map<Integer, Long> lastReplenishTickBySlot
    ) {
        if (player == null || item == null || item.entry() == null || item.hotbarSlot() < 0 || item.hotbarSlot() >= player.getInventory().items.size()) {
            return false;
        }
        int replenishSeconds = item.entry().getReplenishSeconds();
        if (replenishSeconds <= 0) {
            return false;
        }
        ItemStack configuredStack = item.configuredStack();
        if (configuredStack == null || configuredStack.isEmpty() || loadoutService.isAmmoManagedGunItem(configuredStack)) {
            return false;
        }
        if (configuredStack.is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        long intervalTicks = (long) replenishSeconds * 20L;
        if (intervalTicks <= 0L) {
            return false;
        }
        int slotKey = item.slot().getIndex();
        long lastTick = lastReplenishTickBySlot.computeIfAbsent(slotKey, ignored -> gameTick);
        if (gameTick - lastTick < intervalTicks) {
            return false;
        }
        int targetCount = Math.max(1, Math.min(configuredStack.getCount(), configuredStack.getMaxStackSize()));
        ItemStack currentStack = player.getInventory().getItem(item.hotbarSlot());
        if (currentStack.isEmpty() || !ItemStack.isSameItemSameTags(currentStack, configuredStack)) {
            ItemStack restored = configuredStack.copy();
            restored.setCount(1);
            player.getInventory().setItem(item.hotbarSlot(), restored);
            lastReplenishTickBySlot.put(slotKey, gameTick);
            return true;
        }
        if (currentStack.getCount() >= targetCount) {
            return false;
        }
        currentStack.grow(1);
        if (currentStack.getCount() > targetCount) {
            currentStack.setCount(targetCount);
        }
        player.getInventory().setItem(item.hotbarSlot(), currentStack);
        lastReplenishTickBySlot.put(slotKey, gameTick);
        return true;
    }
}
