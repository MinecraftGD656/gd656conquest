package org.mods.gd656conquest.server.vehicle;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleMountedHotbarLockService {
    public static final int LOCKED_HOTBAR_SLOT = 6;

    private final Map<UUID, Integer> previousSlots = new ConcurrentHashMap<>();
    private final Set<UUID> mountedPlayers = ConcurrentHashMap.newKeySet();

    public void onPlayerTick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        boolean mountedVehicle = isMountedVehicle(player);
        if (mountedVehicle && mountedPlayers.add(playerId)) {
            previousSlots.putIfAbsent(playerId, clampHotbarSlot(player.getInventory().selected));
            forceSelectedSlot(player, LOCKED_HOTBAR_SLOT);
        }
        if (shouldLock(player)) {
            forceSelectedSlot(player, LOCKED_HOTBAR_SLOT);
            return;
        }
        if (mountedVehicle) {
            return;
        }
        mountedPlayers.remove(playerId);
        restorePreviousSlot(player);
    }

    public void onPlayerLogout(ServerPlayer player) {
        clear(player);
    }

    public void onPlayerRespawn(ServerPlayer player) {
        clear(player);
    }

    public void onPlayerLogin(ServerPlayer player) {
        clear(player);
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!shouldLock(player)) {
            restorePreviousSlot(player);
        }
    }

    private boolean shouldLock(ServerPlayer player) {
        return player != null && VehicleCompatFacade.shouldLockMountedHotbar(player.getVehicle());
    }

    private boolean isMountedVehicle(ServerPlayer player) {
        return player != null && VehicleCompatFacade.isVehicleEntity(player.getVehicle());
    }

    private void restorePreviousSlot(ServerPlayer player) {
        Integer previousSlot = previousSlots.remove(player.getUUID());
        if (previousSlot == null) {
            return;
        }
        forceSelectedSlot(player, clampHotbarSlot(previousSlot));
    }

    private void clear(ServerPlayer player) {
        if (player != null) {
            previousSlots.remove(player.getUUID());
            mountedPlayers.remove(player.getUUID());
        }
    }

    private void forceSelectedSlot(ServerPlayer player, int slot) {
        if (player == null) {
            return;
        }
        int normalizedSlot = clampHotbarSlot(slot);
        if (player.getInventory().selected == normalizedSlot) {
            return;
        }
        player.getInventory().selected = normalizedSlot;
        if (player.connection != null) {
            player.connection.send(new ClientboundSetCarriedItemPacket(normalizedSlot));
        }
    }

    private static int clampHotbarSlot(int slot) {
        if (slot < 0) {
            return 0;
        }
        if (slot > 8) {
            return 8;
        }
        return slot;
    }
}
