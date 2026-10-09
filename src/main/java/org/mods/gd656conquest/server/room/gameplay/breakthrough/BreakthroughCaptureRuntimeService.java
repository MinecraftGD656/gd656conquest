package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveAreaResolver;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveBlockAreaResolver;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryPolygonSupport;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class BreakthroughCaptureRuntimeService {
    private final MinecraftServer server;
    private final RoomObjectiveAreaResolver objectiveAreaResolver;

    public BreakthroughCaptureRuntimeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.objectiveAreaResolver = new RoomObjectiveBlockAreaResolver();
    }

    public void tick(RoomDefinition room, RunningRuntimeState state, BreakthroughRuntimeState runtimeState, long gameTick) {
        if (room == null || state == null || runtimeState == null) {
            return;
        }
        List<RoomCapturePointDefinition> activePoints = room.breakthroughConfig().stageActivePoints(runtimeState.getCurrentStageIndex());
        Set<String> allowedCodes = new LinkedHashSet<>();
        for (RoomCapturePointDefinition point : activePoints) {
            allowedCodes.add(RoomObjectiveSupport.normalizeCode(point.code()));
        }
        if (allowedCodes.isEmpty()) {
            runtimeState.synchronizeWithConfig(room.breakthroughConfig());
            return;
        }

        Map<String, Integer> campACountByCode = new HashMap<>();
        Map<String, Integer> campBCountByCode = new HashMap<>();
        for (UUID playerId : state.getRoomPlayers()) {
            if (state.isInRespawnFlow(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                continue;
            }
            RoomTeamId teamId = state.getRoomTeams().get(playerId);
            if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
                continue;
            }
            String insideCode = objectiveAreaResolver.resolvePlayerInsideObjectiveCode(player, RoomCaptureConfig.defaults(), allowedCodes);
            if (insideCode == null) {
                continue;
            }
            if (teamId == RoomTeamId.CAMP_A) {
                campACountByCode.merge(insideCode, 1, Integer::sum);
            } else {
                campBCountByCode.merge(insideCode, 1, Integer::sum);
            }
        }

        for (RoomCapturePointDefinition point : activePoints) {
            String code = RoomObjectiveSupport.normalizeCode(point.code());
            int campA = campACountByCode.getOrDefault(code, 0);
            int campB = campBCountByCode.getOrDefault(code, 0);
            runtimeState.getCampACountByCode().put(code, campA);
            runtimeState.getCampBCountByCode().put(code, campB);

            RoomTeamId previousOwner = runtimeState.getOwnerByCode().get(code);
            RoomTeamId driving = resolveDrivingTeam(campA, campB);
            runtimeState.getDrivingTeamByCode().put(code, driving);

            double ratio = resolveCaptureRatio(campA, campB);
            double fullControlDeltaPerTick = resolveProgressDeltaPerTick(room);
            double currentProgress = runtimeState.getProgressByCode().getOrDefault(code, 0.0D);
            double nextProgress = Mth.clamp(currentProgress + ratio * fullControlDeltaPerTick, 0.0D, 100.0D);
            runtimeState.getProgressByCode().put(code, nextProgress);
            RoomTeamId nextOwner = resolveOwner(previousOwner, nextProgress);
            runtimeState.getOwnerByCode().put(code, nextOwner);
            if (nextOwner != previousOwner && (nextOwner == RoomTeamId.CAMP_A || nextOwner == RoomTeamId.CAMP_B)) {
                pushOwnershipChangedEvents(runtimeState, nextOwner, code, gameTick);
            }
        }
    }

    public int countDefendersInStageBoundary(RoomDefinition room, RunningRuntimeState state, int stageIndex) {
        return collectDefendersInStageBoundary(room, state, stageIndex).size();
    }

    public Set<UUID> collectDefendersInStageBoundary(RoomDefinition room, RunningRuntimeState state, int stageIndex) {
        if (room == null || state == null || stageIndex <= 0) {
            return Set.of();
        }
        List<RoomPosition> boundary = room.breakthroughConfig().stageBoundary(stageIndex);
        if (boundary == null || boundary.size() < 3) {
            return Set.of();
        }
        Set<UUID> defenders = new LinkedHashSet<>();
        for (UUID playerId : state.getRoomPlayers()) {
            if (state.isInRespawnFlow(playerId) || state.getRoomTeams().get(playerId) != RoomTeamId.CAMP_B) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (RoomBoundaryPolygonSupport.isPointInsideBoundary(player.getX(), player.getZ(), boundary)) {
                defenders.add(playerId);
            }
        }
        return defenders;
    }

    public boolean isInsideStageBoundary(RoomDefinition room, ServerPlayer player, int stageIndex) {
        if (room == null || player == null || stageIndex <= 0) {
            return false;
        }
        List<RoomPosition> boundary = room.breakthroughConfig().stageBoundary(stageIndex);
        return boundary != null && boundary.size() >= 3
            && RoomBoundaryPolygonSupport.isPointInsideBoundary(player.getX(), player.getZ(), boundary);
    }

    public String resolvePlayerInsideActiveObjectiveCode(RoomDefinition room, BreakthroughRuntimeState runtimeState, ServerPlayer player) {
        if (room == null || runtimeState == null || player == null) {
            return null;
        }
        Set<String> allowedCodes = new LinkedHashSet<>();
        for (RoomCapturePointDefinition point : room.breakthroughConfig().stageActivePoints(runtimeState.getCurrentStageIndex())) {
            allowedCodes.add(RoomObjectiveSupport.normalizeCode(point.code()));
        }
        if (allowedCodes.isEmpty()) {
            return null;
        }
        return objectiveAreaResolver.resolvePlayerInsideObjectiveCode(player, RoomCaptureConfig.defaults(), allowedCodes);
    }

    public ServerPlayer findOnlinePlayer(UUID playerId) {
        return playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    public ServerPlayer resolveKillerPlayer(ServerPlayer deadPlayer, int killerEntityId) {
        if (deadPlayer == null || killerEntityId < 0 || deadPlayer.serverLevel() == null) {
            return null;
        }
        net.minecraft.world.entity.Entity entity = deadPlayer.serverLevel().getEntity(killerEntityId);
        if (entity instanceof ServerPlayer killerPlayer && killerPlayer.isAlive()) {
            return killerPlayer;
        }
        return null;
    }

    private RoomTeamId resolveDrivingTeam(int campA, int campB) {
        if (campA <= 0 && campB <= 0) {
            return null;
        }
        if (campA == campB) {
            return null;
        }
        return campA > campB ? RoomTeamId.CAMP_A : RoomTeamId.CAMP_B;
    }

    private double resolveCaptureRatio(int campA, int campB) {
        if (campA <= 0 && campB <= 0) {
            return 0.0D;
        }
        return (campA - campB) / (double) (campA + campB);
    }

    private double resolveProgressDeltaPerTick(RoomDefinition room) {
        return 100.0D / (resolveEffectiveProgressSeconds(room) * 20.0D);
    }

    private double resolveEffectiveProgressSeconds(RoomDefinition room) {
        if (room == null) {
            return 40.0D;
        }
        double configured = room.commonConfig().progressSpeed();
        if (!Double.isFinite(configured) || configured <= 0.0D) {
            return 40.0D;
        }
        return configured;
    }

    private RoomTeamId resolveOwner(RoomTeamId previousOwner, double nextProgress) {
        if (previousOwner == RoomTeamId.CAMP_A) {
            return nextProgress > 50.0D ? RoomTeamId.CAMP_A : null;
        }
        if (previousOwner == RoomTeamId.CAMP_B) {
            return nextProgress < 50.0D ? RoomTeamId.CAMP_B : null;
        }
        if (nextProgress >= 99.999D) {
            return RoomTeamId.CAMP_A;
        }
        if (nextProgress <= 0.001D) {
            return RoomTeamId.CAMP_B;
        }
        return null;
    }

    private void pushOwnershipChangedEvents(BreakthroughRuntimeState runtimeState, RoomTeamId owner, String code, long gameTick) {
        if (runtimeState == null || owner == null || code == null || code.isBlank()) {
            return;
        }
        RoomTeamId enemy = owner == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        runtimeState.pushTeamEvent(owner, "我军已成功占领据点 " + code, gameTick + 100L);
        runtimeState.pushTeamEvent(enemy, "敌军已成功占领据点 " + code, gameTick + 100L);
    }

}
