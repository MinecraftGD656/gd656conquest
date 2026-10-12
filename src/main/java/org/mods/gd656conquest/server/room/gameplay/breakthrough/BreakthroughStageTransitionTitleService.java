package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomCombatCountdownPacket;
import org.mods.gd656conquest.common.match.CombatCountdownState.Kind;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

public final class BreakthroughStageTransitionTitleService {
    private final MinecraftServer server;

    public BreakthroughStageTransitionTitleService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void syncCountdownTitles(RunningRuntimeState state, BreakthroughRuntimeState runtimeState, long gameTick) {
        if (state == null || runtimeState == null || runtimeState.getAdvanceCountdownEndTick() <= 0L) {
            return;
        }
        int remainingTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, runtimeState.getAdvanceCountdownEndTick() - gameTick));
        if (remainingTicks == 0) {
            clearCountdownTitles(state, runtimeState);
            return;
        }
        int remainingSeconds = (int) ((remainingTicks + 19L) / 20L);
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            int lastShown = runtimeState.getAdvanceCountdownLastShownSecondsByPlayer().getOrDefault(playerId, -1);
            if (lastShown == remainingSeconds) {
                continue;
            }
            boolean attacker = state.getRoomTeams().get(playerId) == RoomTeamId.CAMP_A;
            NetworkManager.sendToPlayer(player, new RoomCombatCountdownPacket(Kind.ADVANCE, remainingTicks,
                attacker ? "准备夺取下个阶段" : "准备防守阵地"));
            runtimeState.getAdvanceCountdownLastShownSecondsByPlayer().put(playerId, remainingSeconds);
        }
    }

    public void clearCountdownTitles(RunningRuntimeState state, BreakthroughRuntimeState runtimeState) {
        if (state == null || runtimeState == null) {
            return;
        }
        for (UUID playerId : new ArrayList<>(runtimeState.getAdvanceCountdownLastShownSecondsByPlayer().keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.connection != null) {
                NetworkManager.sendToPlayer(player, RoomCombatCountdownPacket.clear(Kind.ADVANCE));
            }
        }
        runtimeState.getAdvanceCountdownLastShownSecondsByPlayer().clear();
    }

    public void syncRetreatTitles(
        RoomDefinition room,
        RunningRuntimeState state,
        BreakthroughRuntimeState runtimeState,
        BreakthroughCaptureRuntimeService captureRuntimeService,
        long gameTick
    ) {
        if (room == null || state == null || runtimeState == null || captureRuntimeService == null || runtimeState.getRetreatDeadlineTick() <= 0L) {
            return;
        }
        int remainingSeconds = (int) Math.max(1L, (runtimeState.getRetreatDeadlineTick() - gameTick + 19L) / 20L);
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                runtimeState.getRetreatCountdownLastShownSecondsByPlayer().remove(playerId);
                continue;
            }
            boolean shouldShow = state.getRoomTeams().get(playerId) == RoomTeamId.CAMP_B
                && !state.isInRespawnFlow(playerId)
                && player.isAlive()
                && !player.isSpectator()
                && runtimeState.retreatRoster().isRetreating(playerId);
            if (!shouldShow) {
                clearSingleTitle(player, runtimeState.getRetreatCountdownLastShownSecondsByPlayer().remove(playerId) != null);
                continue;
            }
            int lastShown = runtimeState.getRetreatCountdownLastShownSecondsByPlayer().getOrDefault(playerId, -1);
            if (lastShown == remainingSeconds) {
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.retreatUpdate(
                (int) Math.max(0L, runtimeState.getRetreatDeadlineTick() - gameTick)));
            runtimeState.getRetreatCountdownLastShownSecondsByPlayer().put(playerId, remainingSeconds);
        }
    }

    public void clearRetreatTitles(RunningRuntimeState state, BreakthroughRuntimeState runtimeState) {
        if (state == null || runtimeState == null) {
            return;
        }
        for (UUID playerId : new ArrayList<>(runtimeState.getRetreatCountdownLastShownSecondsByPlayer().keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            clearSingleTitle(player, true);
        }
        runtimeState.getRetreatCountdownLastShownSecondsByPlayer().clear();
    }

    private void clearSingleTitle(ServerPlayer player, boolean shouldClear) {
        if (!shouldClear || player == null || player.connection == null) {
            return;
        }
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clearRetreat());
    }
}
