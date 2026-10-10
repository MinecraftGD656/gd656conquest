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
    private final RoomGunmasterLoadoutService gunmasterLoadoutService;

    public RoomItemReplenishService(
        MinecraftServer server,
        RoomPlayerLoadoutService loadoutService,
        RoomGunmasterLoadoutService gunmasterLoadoutService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.loadoutService = Objects.requireNonNull(loadoutService, "loadoutService");
        this.gunmasterLoadoutService = Objects.requireNonNull(gunmasterLoadoutService, "gunmasterLoadoutService");
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
        if (gunmasterLoadoutService.isGunmasterPlayer(player)) {
            return false;
        }
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
        return org.mods.gd656conquest.server.support.SupportRuntime.recharge(player, item, gameTick, lastReplenishTickBySlot);
    }
}
