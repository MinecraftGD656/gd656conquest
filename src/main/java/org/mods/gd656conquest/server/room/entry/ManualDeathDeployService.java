package org.mods.gd656conquest.server.room.entry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import org.mods.gd656conquest.common.player.ManualDeathDeployModel;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.DownedDeployPacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import net.minecraft.server.level.ServerPlayer;

/** Server-owned gate. The native countdown map retains flow ownership but cannot auto-open. */
public final class ManualDeathDeployService {
    private record Waiting(long deathId, long readyTick, String dimension) {}
    private static final Map<RunningRuntimeState, Map<UUID, Waiting>> WAITING = new WeakHashMap<>();
    private ManualDeathDeployService() {}
    public static void begin(RunningRuntimeState state, ServerPlayer player, long tick, int delay) {
        UUID id = player.getUUID();
        long deathId = RoomDeathCauseService.deathId(player);
        int multiplier = state.getDoubledRespawnPending().remove(id) ? 2 : 1;
        Waiting waiting = new Waiting(deathId, tick + (long)Math.max(0, delay) * multiplier,
            player.level().dimension().location().toString());
        WAITING.computeIfAbsent(state, ignored -> new HashMap<>()).put(id, waiting);
        state.getRespawnCountdownStartTicks().put(id, Long.MAX_VALUE);
        publish(player, waiting, tick);
    }
    private static boolean valid(RunningRuntimeState state, UUID id, Waiting wait, ServerPlayer player) {
        return player != null && state.getRoomPlayers().contains(id) && !state.isPlayerDowned(id)
            && !state.isPlayerPendingDowned(id) && !state.getDeployViewActivePlayers().contains(id)
            && !state.getRespawnFinalizeTicks().containsKey(id)
            && Long.valueOf(Long.MAX_VALUE).equals(state.getRespawnCountdownStartTicks().get(id))
            && wait.dimension().equals(player.level().dimension().location().toString());
    }
    public static void tick(RunningRuntimeState state, net.minecraft.server.MinecraftServer server, long tick) {
        Map<UUID, Waiting> waiting = WAITING.get(state);
        if (waiting == null) return;
        waiting.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (!valid(state, entry.getKey(), entry.getValue(), player)) return true;
            if (tick % 10 == 0) publish(player, entry.getValue(), tick);
            return false;
        });
        if (waiting.isEmpty()) WAITING.remove(state);
    }
    private static void publish(ServerPlayer player, Waiting wait, long tick) {
        if (wait.deathId() > 0) NetworkManager.sendToPlayer(player, new DownedDeployPacket(wait.deathId(),
            player.getId(), wait.dimension(), true, ManualDeathDeployModel.remaining(wait.readyTick(), tick), 0, true));
    }
    public static boolean request(RoomPlayerRespawnService service, RoomDefinition room, RunningRuntimeState state,
                                  ServerPlayer player, String action, long tick) {
        if (room == null || state == null || player == null || action == null || !action.startsWith("death_deploy:")) return false;
        Map<UUID, Waiting> waiting = WAITING.get(state);
        UUID id = player.getUUID();
        Waiting wait = waiting == null ? null : waiting.get(id);
        if (wait == null || !valid(state, id, wait, player)) return false;
        if (!action.equals("death_deploy:" + wait.deathId())) return false;
        // Remove only after the native view has successfully opened; failed views remain retryable.
        service.openManualDeathDeploy(room, state, player, tick, ManualDeathDeployModel.remaining(wait.readyTick(), tick));
        if (state.getDeployViewActivePlayers().contains(id)) {
            state.getRespawnCountdownStartTicks().remove(id);
            state.getDeathKillerEntityIds().remove(id);
            waiting.remove(id);
            return true;
        }
        return false;
    }
}
