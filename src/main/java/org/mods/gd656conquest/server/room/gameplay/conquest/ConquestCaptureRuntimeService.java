package org.mods.gd656conquest.server.room.gameplay.conquest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveAreaResolver;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveBlockAreaResolver;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ConquestCaptureRuntimeService {
    private final MinecraftServer server;
    private final RoomObjectiveAreaResolver objectiveAreaResolver;

    public ConquestCaptureRuntimeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.objectiveAreaResolver = new RoomObjectiveBlockAreaResolver();
    }

    public Optional<RoomMatchEndRequest> tick(
        RoomDefinition room,
        RunningRuntimeState state,
        ConquestCaptureRuntimeState captureState,
        ConquestScoreRuntimeState scoreState,
        long gameTick
    ) {
        if (room == null || state == null || captureState == null || scoreState == null) {
            return Optional.empty();
        }
        RoomCaptureConfig config = room.conquestConfig().captureConfig();
        captureState.synchronizeWithConfig(config);
        Set<String> allowedCodes = RoomObjectiveSupport.activeCodes(config);
        if (allowedCodes.isEmpty()) {
            scoreState.clearFullCaptureState();
            return Optional.empty();
        }

        Map<String, Integer> campACountByCode = new HashMap<>();
        Map<String, Integer> campBCountByCode = new HashMap<>();
        Map<String, Set<UUID>> activePlayersByCode = new HashMap<>();
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
            String insideCode = resolvePlayerInsideCaptureCode(player, config, allowedCodes);
            if (insideCode == null) {
                continue;
            }
            activePlayersByCode.computeIfAbsent(insideCode, ignored -> new LinkedHashSet<>()).add(playerId);
            if (teamId == RoomTeamId.CAMP_A) {
                campACountByCode.merge(insideCode, 1, Integer::sum);
            } else {
                campBCountByCode.merge(insideCode, 1, Integer::sum);
            }
        }

        Map<RoomTeamId, Integer> pendingTroopLoss = new HashMap<>();
        for (RoomCapturePointDefinition point : RoomObjectiveSupport.activePoints(config)) {
            String code = RoomObjectiveSupport.normalizeCode(point.code());
            int campA = campACountByCode.getOrDefault(code, 0);
            int campB = campBCountByCode.getOrDefault(code, 0);
            Set<UUID> activePlayers = activePlayersByCode.getOrDefault(code, Set.of());
            captureState.getCampACountByCode().put(code, campA);
            captureState.getCampBCountByCode().put(code, campB);

            RoomTeamId previousOwner = captureState.getOwnerByCode().get(code);
            RoomTeamId driving = resolveDrivingTeam(campA, campB);
            captureState.getDrivingTeamByCode().put(code, driving);

            double ratio = resolveCaptureRatio(campA, campB);
            double fullControlDeltaPerTick = resolveProgressDeltaPerTick(room);
            double currentProgress = captureState.getProgressByCode().getOrDefault(code, 50.0D);
            double nextProgress = Mth.clamp(currentProgress + ratio * fullControlDeltaPerTick, 0.0D, 100.0D);
            captureState.getProgressByCode().put(code, nextProgress);

            RoomTeamId owner = resolveOwner(previousOwner, nextProgress);
            captureState.getOwnerByCode().put(code, owner);
            if (owner != null && owner != previousOwner) {
                pushOwnershipChangedEvents(room, scoreState, owner, code, gameTick);
            }
            Optional<RoomMatchEndRequest> scoreEnd = applyScore(room, captureState, scoreState, code, owner, gameTick, pendingTroopLoss);
            if (scoreEnd.isPresent()) {
                return scoreEnd;
            }
        }

        if (isTroopDepletionMode(room) && !pendingTroopLoss.isEmpty()) {
            scoreState.setCampAScore(scoreState.getCampAScore() - pendingTroopLoss.getOrDefault(RoomTeamId.CAMP_A, 0));
            scoreState.setCampBScore(scoreState.getCampBScore() - pendingTroopLoss.getOrDefault(RoomTeamId.CAMP_B, 0));
            Optional<RoomMatchEndRequest> troopEnd = checkTroopDepletionEnd(scoreState);
            if (troopEnd.isPresent()) {
                return troopEnd;
            }
        }

        return processFullCaptureEnd(room, captureState, scoreState, gameTick);
    }

    private Optional<RoomMatchEndRequest> applyScore(
        RoomDefinition room,
        ConquestCaptureRuntimeState captureState,
        ConquestScoreRuntimeState scoreState,
        String code,
        RoomTeamId owner,
        long gameTick,
        Map<RoomTeamId, Integer> pendingTroopLoss
    ) {
        if (owner == null) {
            return Optional.empty();
        }
        long intervalTicks = room.conquestConfig().captureConfig().captureScoreIntervalSeconds() * 20L;
        long lastScoreTick = captureState.getLastScoreTickByCode().getOrDefault(code, -1L);
        if (intervalTicks <= 0 || (lastScoreTick >= 0 && gameTick - lastScoreTick < intervalTicks)) {
            return Optional.empty();
        }
        captureState.getLastScoreTickByCode().put(code, gameTick);
        if (isTroopDepletionMode(room)) {
            RoomTeamId enemy = owner == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
            pendingTroopLoss.merge(enemy, 1, Integer::sum);
            return Optional.empty();
        }
        if (owner == RoomTeamId.CAMP_A) {
            scoreState.setCampAScore(scoreState.getCampAScore() + 1);
            if (scoreState.getCampAScore() >= room.conquestConfig().captureConfig().targetScore()) {
                return Optional.of(new RoomMatchEndRequest(RoomTeamId.CAMP_A, "达到目标得分"));
            }
        } else if (owner == RoomTeamId.CAMP_B) {
            scoreState.setCampBScore(scoreState.getCampBScore() + 1);
            if (scoreState.getCampBScore() >= room.conquestConfig().captureConfig().targetScore()) {
                return Optional.of(new RoomMatchEndRequest(RoomTeamId.CAMP_B, "达到目标得分"));
            }
        }
        return Optional.empty();
    }

    Optional<RoomMatchEndRequest> checkTroopDepletionEnd(ConquestScoreRuntimeState scoreState) {
        int campA = scoreState.getCampAScore();
        int campB = scoreState.getCampBScore();
        if (campA <= 0 && campB <= 0) {
            return Optional.of(new RoomMatchEndRequest((RoomTeamId) null, "双方兵力同时耗尽"));
        }
        if (campA <= 0) {
            return Optional.of(new RoomMatchEndRequest(RoomTeamId.CAMP_B, "CampA兵力耗尽"));
        }
        if (campB <= 0) {
            return Optional.of(new RoomMatchEndRequest(RoomTeamId.CAMP_A, "CampB兵力耗尽"));
        }
        return Optional.empty();
    }

    private Optional<RoomMatchEndRequest> processFullCaptureEnd(
        RoomDefinition room,
        ConquestCaptureRuntimeState captureState,
        ConquestScoreRuntimeState scoreState,
        long gameTick
    ) {
        int fullCaptureEndSeconds = room.conquestConfig().captureConfig().fullCaptureEndSeconds();
        if (fullCaptureEndSeconds < 0) {
            scoreState.clearFullCaptureState();
            return Optional.empty();
        }
        Set<String> activeCodes = RoomObjectiveSupport.activeCodes(room.conquestConfig().captureConfig());
        if (activeCodes.isEmpty()) {
            scoreState.clearFullCaptureState();
            return Optional.empty();
        }
        RoomTeamId dominantTeam = resolveDominantOwner(captureState, activeCodes);
        if (scoreState.getFullCaptureEndTick() > 0) {
            RoomTeamId ownerTeam = scoreState.getFullCaptureOwnerTeam();
            RoomTeamId enemyTeam = scoreState.getFullCaptureEnemyTeam();
            if (ownerTeam == null || enemyTeam == null) {
                scoreState.clearFullCaptureState();
                return Optional.empty();
            }
            if (!isTeamOwningAllActivePoints(captureState, activeCodes, ownerTeam)) {
                scoreState.clearFullCaptureState();
                pushTeamEvent(scoreState, ownerTeam, "我方已失去全部据点控制，决战已中断", gameTick + 100L);
                pushTeamEvent(scoreState, enemyTeam, "敌方未再控制全部据点，决战已中断", gameTick + 100L);
                return Optional.empty();
            }
            if (gameTick >= scoreState.getFullCaptureEndTick()) {
                return Optional.of(new RoomMatchEndRequest(ownerTeam, "控制全部据点并维持到决战结束"));
            }
            if (dominantTeam == enemyTeam) {
                scoreState.clearFullCaptureState();
            }
            return Optional.empty();
        }
        if (dominantTeam == null) {
            return Optional.empty();
        }
        if (fullCaptureEndSeconds == 0) {
            return Optional.of(new RoomMatchEndRequest(dominantTeam, "控制全部据点"));
        }
        RoomTeamId enemy = dominantTeam == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        scoreState.setFullCaptureOwnerTeam(dominantTeam);
        scoreState.setFullCaptureEnemyTeam(enemy);
        scoreState.setFullCaptureEndTick(gameTick + fullCaptureEndSeconds * 20L);
        pushTeamEvent(scoreState, dominantTeam, "我方控制了全部据点", gameTick + 120L);
        pushTeamEvent(scoreState, enemy, "敌方控制了全部据点", gameTick + 120L);
        return Optional.empty();
    }

    ServerPlayer findOnlinePlayer(UUID playerId) {
        return playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    ServerPlayer resolveKillerPlayer(ServerPlayer deadPlayer, int killerEntityId) {
        if (deadPlayer == null || killerEntityId < 0 || deadPlayer.serverLevel() == null) {
            return null;
        }
        net.minecraft.world.entity.Entity entity = deadPlayer.serverLevel().getEntity(killerEntityId);
        if (entity instanceof ServerPlayer killerPlayer && killerPlayer.isAlive()) {
            return killerPlayer;
        }
        return null;
    }

    String resolvePlayerInsideCaptureCode(ServerPlayer player, RoomCaptureConfig config) {
        if (config == null) {
            return null;
        }
        return resolvePlayerInsideCaptureCode(player, config, RoomObjectiveSupport.activeCodes(config));
    }

    private String resolvePlayerInsideCaptureCode(ServerPlayer player, RoomCaptureConfig config, Set<String> allowedCodes) {
        return objectiveAreaResolver.resolvePlayerInsideObjectiveCode(player, config, allowedCodes);
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

    private boolean isTroopDepletionMode(RoomDefinition room) {
        return "TROOP_DEPLETION".equalsIgnoreCase(room.conquestConfig().captureConfig().scoreMethod());
    }

    private RoomTeamId resolveDominantOwner(ConquestCaptureRuntimeState captureState, Set<String> activeCodes) {
        if (captureState == null || activeCodes == null || activeCodes.isEmpty()) {
            return null;
        }
        RoomTeamId candidate = null;
        for (String code : activeCodes) {
            RoomTeamId owner = captureState.getOwnerByCode().get(code);
            if (owner != RoomTeamId.CAMP_A && owner != RoomTeamId.CAMP_B) {
                return null;
            }
            if (candidate == null) {
                candidate = owner;
                continue;
            }
            if (candidate != owner) {
                return null;
            }
        }
        return candidate;
    }

    private boolean isTeamOwningAllActivePoints(ConquestCaptureRuntimeState captureState, Set<String> activeCodes, RoomTeamId teamId) {
        if (captureState == null || activeCodes == null || activeCodes.isEmpty()) {
            return false;
        }
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            return false;
        }
        for (String code : activeCodes) {
            if (captureState.getOwnerByCode().get(code) != teamId) {
                return false;
            }
        }
        return true;
    }

    private void pushOwnershipChangedEvents(RoomDefinition room, ConquestScoreRuntimeState scoreState, RoomTeamId owner, String code, long gameTick) {
        RoomTeamId enemy = owner == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        pushTeamEvent(scoreState, owner, "我军已成功占领据点 " + code, gameTick + 100L);
        pushTeamEvent(scoreState, enemy, "敌军已成功占领据点 " + code, gameTick + 100L);
    }

    private void pushTeamEvent(ConquestScoreRuntimeState scoreState, RoomTeamId teamId, String eventText, long expireTick) {
        if (scoreState == null || teamId == null) {
            return;
        }
        scoreState.setTeamEvent(teamId, eventText, expireTick);
    }
}
