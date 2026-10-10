package org.mods.gd656conquest.server.support;

import java.util.Comparator;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

/** Connect the original supply entry points to deployed reserve targets during staged migration. */
public final class SupportAmmoRefill {
    private SupportAmmoRefill() {}

    public static int bag(ServerPlayer player, int maximum) {
        if (maximum <= 0 || !available(player, null)) return 0;
        var targets = SupportAmmo.targets(player);
        targets.removeIf(target -> target.slow);
        int held = player.getInventory().selected;
        targets.sort(Comparator.comparingInt(target -> target.item.hotbarSlot() == held ? -1 : target.item.hotbarSlot()));
        int remaining = maximum;
        for (var target : targets) {
            if (remaining == 0) break;
            remaining -= target.add(player, remaining);
        }
        int actual = maximum - remaining;
        if (actual > 0) SupportAmmo.sync(player);
        return actual;
    }

    public static boolean box(RoomTeamId team, ServerPlayer player) {
        if (team == null || !available(player, team)) return false;
        boolean supplied = false;
        for (var target : SupportAmmo.targets(player)) {
            if (target.add(player, Integer.MAX_VALUE) > 0) supplied = true;
        }
        if (supplied) SupportAmmo.sync(player);
        return supplied;
    }

    private static boolean available(ServerPlayer player, RoomTeamId expectedTeam) {
        if (player == null || player.server == null || !player.isAlive() || player.isSpectator()) return false;
        var manager = RoomCoreRuntimeManager.of(player.server);
        String id = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (id == null) return false;
        var room = manager.findRoom(id).orElse(null);
        var state = manager.findRunningState(id).orElse(null);
        if (room == null || state == null || room.lifecycleState() != RoomLifecycleState.RUNNING
            || RoomGameplayId.GUNMASTER.equals(room.gameplayId()) || state.isInRespawnFlow(player.getUUID())
            || manager.isPlayerDowned(player.getUUID())) return false;
        var team = state.getRoomTeams().get(player.getUUID());
        return team != null && (expectedTeam == null || expectedTeam == team);
    }
}
