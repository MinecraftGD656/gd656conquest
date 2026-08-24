package org.mods.gd656conquest.server.vehicle;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GD656Mods: 载具内"物品栏选中槽位"锁定(服务端权威, 通过 ClientboundSetCarriedItemPacket 同步客户端)。
 * 仅 1 号驾驶位(驾驶员)锁定选中槽位; 其他座位(2号炮手/车长/三号位/副驾驶等)一律不锁定、不强制。
 * 绝不拦截滚轮事件本身(滚轮交模组/原版处理), 只保证 selected 槽位不可改变。
 */
public final class VehicleMountedHotbarLockService {
    public static final int LOCKED_HOTBAR_SLOT = 6;
    public static final int POST_DISMOUNT_HOTBAR_SLOT = 0;
    private final Set<UUID> lockedPlayers = ConcurrentHashMap.newKeySet();

    public void onPlayerTick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        boolean lockNow = isDriverSeat(player);
        if (lockNow) {
            if (lockedPlayers.add(playerId)) {
                forceSelectedSlot(player, LOCKED_HOTBAR_SLOT);
            }
            forceSelectedSlot(player, LOCKED_HOTBAR_SLOT);
            return;
        }
        if (lockedPlayers.remove(playerId)) {
            forceSelectedSlot(player, POST_DISMOUNT_HOTBAR_SLOT);
        }
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
        if (!isDriverSeat(player) && lockedPlayers.remove(player.getUUID())) {
            forceSelectedSlot(player, POST_DISMOUNT_HOTBAR_SLOT);
        }
    }

    /** GD656Mods: 仅 1 号驾驶位(驾驶员)锁定物品栏选中槽位 */
    private boolean isDriverSeat(ServerPlayer player) {
        return player != null
            && player.getVehicle() != null
            && VehicleCompatFacade.isDriver(player.getVehicle(), player);
    }

    private void clear(ServerPlayer player) {
        if (player != null) {
            lockedPlayers.remove(player.getUUID());
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
