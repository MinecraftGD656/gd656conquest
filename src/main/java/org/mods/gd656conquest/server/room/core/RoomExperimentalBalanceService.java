package org.mods.gd656conquest.server.room.core;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.gameplay.RoomAutoBalanceWindowSnapshot;
import org.mods.gd656conquest.server.room.matching.RoomTeamBalanceRatingService;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomTeamSwitchService;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RoomExperimentalBalanceService {
    public static final long CHECK_INTERVAL_TICKS = 60L * 20L;
    private static final long PENDING_TTL_TICKS = CHECK_INTERVAL_TICKS;

    private final MinecraftServer server;
    private final RoomTeamBalanceRatingService ratingService;

    public RoomExperimentalBalanceService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.ratingService = new RoomTeamBalanceRatingService(this.server);
    }

    public RoomTeamBalanceRatingService ratingService() {
        return ratingService;
    }

    public void tick(RoomRuntimeSlot<RoomRuntimeState> slot, RoomTeamSwitchService teamSwitchService, long gameTick) {
        if (slot == null || slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        expirePendingChanges(runningState, gameTick);
        if (runningState.getExperimentalBalanceLastCheckTick() >= 0L
            && gameTick - runningState.getExperimentalBalanceLastCheckTick() < CHECK_INTERVAL_TICKS) {
            return;
        }
        runningState.setExperimentalBalanceLastCheckTick(gameTick);
        RoomAutoBalanceWindowSnapshot snapshot = runningState.getGameplayRuntime()
            .map(runtime -> runtime.autoBalanceWindowSnapshot(slot.room(), gameTick))
            .orElse(RoomAutoBalanceWindowSnapshot.locked("玩法运行器缺失"));
        if (!snapshot.autoSwitchAllowed()) {
            return;
        }
        Map<UUID, Double> ratingByPlayer = ratingService.resolveRatings(runningState.getRoomPlayers());
        BalanceDecision decision = planDecision(runningState, ratingByPlayer);
        if (decision == null) {
            return;
        }
        queuePendingChange(slot.room(), slot.runtime(), teamSwitchService, decision.playerId(), decision.targetTeam(), gameTick);
    }

    private void expirePendingChanges(RunningRuntimeState runningState, long gameTick) {
        Iterator<Map.Entry<UUID, Long>> iterator = runningState.getAutoTeamSwitchOfferExpireTicks().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getKey() == null || entry.getValue() == null || gameTick <= entry.getValue()) {
                continue;
            }
            iterator.remove();
            runningState.getPendingTeamChanges().remove(entry.getKey());
        }
    }

    private BalanceDecision planDecision(RunningRuntimeState runningState, Map<UUID, Double> ratingByPlayer) {
        List<PlayerStrength> campA = collectTeam(runningState, ratingByPlayer, RoomTeamId.CAMP_A);
        List<PlayerStrength> campB = collectTeam(runningState, ratingByPlayer, RoomTeamId.CAMP_B);
        if (campA.isEmpty() || campB.isEmpty() || Math.abs(campA.size() - campB.size()) <= 1) {
            return null;
        }
        // GD656Mods: 只看双方人数差, 从人数优势阵营中选出分数最低且未处于多人 party 的玩家
        List<PlayerStrength> larger = campA.size() >= campB.size() ? campA : campB;
        PlayerStrength candidate = null;
        for (PlayerStrength player : larger) {
            if (!isInMultiMemberParty(player.playerId())
                && (candidate == null || player.strength() < candidate.strength())) {
                candidate = player;
            }
        }
        return candidate == null
            ? null
            : new BalanceDecision(candidate.playerId(), larger == campA ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A);
    }

    private List<PlayerStrength> collectTeam(RunningRuntimeState runningState, Map<UUID, Double> ratingByPlayer, RoomTeamId teamId) {
        List<PlayerStrength> players = new ArrayList<>();
        for (UUID playerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(playerId) == teamId
                && !runningState.getPendingTeamChanges().containsKey(playerId)) {
                players.add(new PlayerStrength(playerId, ratingByPlayer.getOrDefault(playerId, 0.0D)));
            }
        }
        return players;
    }

    private void queuePendingChange(
        RoomDefinition room,
        RoomRuntimeState runtimeState,
        RoomTeamSwitchService teamSwitchService,
        UUID playerId,
        RoomTeamId targetTeam,
        long gameTick
    ) {
        if (playerId == null || targetTeam == null) {
            return;
        }
        RunningRuntimeState runningState = runtimeState.getRunningState();
        runningState.getPendingTeamChanges().put(playerId, targetTeam);
        runningState.getAutoTeamSwitchOfferExpireTicks().put(playerId, gameTick + PENDING_TTL_TICKS);
        if (runningState.isInDeployFlow(playerId)) {
            teamSwitchService.applyRequestedTeam(room, runtimeState, playerId, targetTeam);
        }
    }

    /** GD656Mods: 队伍成员数大于 1 的 party 玩家不参与自动换队, 避免拆散组队玩家。 */
    private boolean isInMultiMemberParty(UUID playerId) {
        return playerId != null
            && InMemoryPartyRepository.of(server)
                .findByPlayerId(playerId)
                .map(party -> party.memberIds().size() > 1)
                .orElse(false);
    }

    private record PlayerStrength(UUID playerId, double strength) {
    }

    private record BalanceDecision(UUID playerId, RoomTeamId targetTeam) {
    }
}
