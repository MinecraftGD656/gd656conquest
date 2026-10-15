package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.breakthrough.BreakthroughObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayDeployPresentationSupport;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveDefinitionProvider;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeProvider;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSpawnSelectionService;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleDeploySelectionService;
import org.mods.gd656conquest.server.room.vehicle.VehicleDeployResolution;

import java.util.Locale;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RoomDeploySelectionService {
    private static final int PLAYER_COMBAT_DURATION_TICKS = 7 * 20;
    private final MinecraftServer server;
    private final RoomSquadBeaconService squadBeaconService;
    private final RoomObjectiveSpawnSelectionService objectiveSpawnSelectionService;
    private final RoomVehicleDeploySelectionService vehicleDeploySelectionService;

    public RoomDeploySelectionService(
        MinecraftServer server,
        RoomSquadBeaconService squadBeaconService,
        RoomVehicleDeploySelectionService vehicleDeploySelectionService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.squadBeaconService = Objects.requireNonNull(squadBeaconService, "squadBeaconService");
        this.objectiveSpawnSelectionService = new RoomObjectiveSpawnSelectionService(server);
        this.vehicleDeploySelectionService = Objects.requireNonNull(vehicleDeploySelectionService, "vehicleDeploySelectionService");
    }

    public boolean updateSelection(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String kindRaw,
        String targetIdRaw
    ) {
        String kind = kindRaw == null ? "" : kindRaw.trim().toLowerCase(Locale.ROOT);
        if ("capture".equals(kind)) {
            return updateCaptureSelection(room, state, playerId, targetIdRaw);
        }
        if ("team".equals(kind)) {
            return (!RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId()) || resolveTeamFallbackSpawn(room, state, server.getPlayerList().getPlayer(playerId), teamId) != null)
                && updateTeamSelection(state, playerId, teamId, targetIdRaw);
        }
        if ("teammate".equals(kind)) {
            return updateTeammateSelection(room, state, playerId, teamId, targetIdRaw);
        }
        if ("beacon".equals(kind)) {
            return updateBeaconSelection(state, playerId, teamId, targetIdRaw);
        }
        if ("vehicle".equals(kind)) {
            return updateVehicleSelection(room, state, playerId, teamId, targetIdRaw);
        }
        return false;
    }

    public String describeSelectionFailure(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String kindRaw,
        String targetIdRaw
    ) {
        String kind = kindRaw == null ? "" : kindRaw.trim().toLowerCase(Locale.ROOT);
        if (!"teammate".equals(kind)) {
            if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId()) && "team".equals(kind) && resolveTeamFallbackSpawn(room, state, server.getPlayerList().getPlayer(playerId), teamId) == null)
                return "本营当前不在合法部署范围内，或没有合法出生点";
            if ("capture".equals(kind)) {
                return describeCaptureSelectionFailure(room, state, playerId, teamId, targetIdRaw);
            }
            if ("beacon".equals(kind)) {
                return squadBeaconService.describeSelectionFailure(state, playerId, teamId, targetIdRaw);
            }
            if ("vehicle".equals(kind)) {
                return resolveVehicleSelection(room, state, playerId, teamId, targetIdRaw).message();
            }
            return null;
        }
        return validateTeammateSelection(room, state, playerId, teamId, targetIdRaw, server.getTickCount()).failureMessage();
    }

    public RoomPosition resolveSelectedSpawn(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        String kindRaw,
        String targetIdRaw,
        boolean allowFallbackOnMissingSelection
    ) {
        String kind = kindRaw == null ? "" : kindRaw.trim().toLowerCase(Locale.ROOT);
        String targetId = targetIdRaw == null ? "" : targetIdRaw.trim();
        if (kind.isBlank() || targetId.isBlank()) {
            return allowFallbackOnMissingSelection ? resolveTeamFallbackSpawn(room, state, player, teamId) : null;
        }
        RoomPosition spawn = null;
        if ("capture".equals(kind)) {
            spawn = resolveCaptureSpawn(room, state, player, teamId, targetId);
        } else if ("team".equals(kind)) {
            spawn = resolveTeamFallbackSpawn(room, state, player, teamId);
        } else if ("teammate".equals(kind)) {
            spawn = resolveTeammateSpawn(room, state, player.getUUID(), teamId, targetId);
        } else if ("beacon".equals(kind)) {
            spawn = squadBeaconService.consumeDeploySpawn(room, state, player.getUUID(), teamId, targetId, server.getTickCount());
        } else if ("vehicle".equals(kind)) {
            return null;
        }
        if (spawn != null && RoomDeployBoundarySupport.allows(room, state, teamId, spawn)) {
            return spawn;
        }
        if (RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) return null;
        return "team".equals(kind) || "teammate".equals(kind) || "beacon".equals(kind)
            ? resolveTeamFallbackSpawn(room, state, player, teamId)
            : null;
    }

    private boolean updateCaptureSelection(RoomDefinition room, RunningRuntimeState state, UUID playerId, String targetIdRaw) {
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        RoomTeamId teamId = playerId == null ? null : state.getRoomTeams().get(playerId);
        String code = targetIdRaw == null ? "" : targetIdRaw.trim().toUpperCase(Locale.ROOT);
        RoomGameplayObjectiveDefinitionProvider definitionProvider = RoomGameplayObjectiveDefinitionProvider.resolve(state);
        RoomCapturePointDefinition point = definitionProvider == null
            ? room.findConfiguredObjectivePoint(code).orElse(null)
            : definitionProvider.findObjectivePoint(room, code);
        if (point == null) {
            return false;
        }
        if (player == null || teamId == null) {
            return false;
        }
        if (!objectiveSpawnSelectionService.assessObjectiveSpawn(room, state, player, teamId, code).success()) {
            return false;
        }
        rememberSelection(state, playerId, "capture", code);
        return true;
    }

    private boolean updateTeamSelection(RunningRuntimeState state, UUID playerId, RoomTeamId teamId, String targetIdRaw) {
        String expected = teamId == RoomTeamId.CAMP_A ? "CAMP_A" : "CAMP_B";
        String token = targetIdRaw == null ? "" : targetIdRaw.trim().toUpperCase(Locale.ROOT);
        if (teamId == RoomTeamId.CAMP_A && !"CAMP_A".equals(token) && !"T".equals(token)) {
            return false;
        }
        if (teamId == RoomTeamId.CAMP_B && !"CAMP_B".equals(token) && !"S".equals(token)) {
            return false;
        }
        rememberSelection(state, playerId, "team", expected);
        return true;
    }

    private boolean updateTeammateSelection(RoomDefinition room, RunningRuntimeState state, UUID playerId, RoomTeamId teamId, String targetIdRaw) {
        TeammateSelectionResult validation = validateTeammateSelection(room, state, playerId, teamId, targetIdRaw, server.getTickCount());
        if (!validation.success()) {
            return false;
        }
        rememberSelection(state, playerId, "teammate", validation.teammateId().toString());
        return true;
    }

    private boolean updateBeaconSelection(RunningRuntimeState state, UUID playerId, RoomTeamId teamId, String targetIdRaw) {
        if (!squadBeaconService.canSelectDeploy(state, playerId, teamId, targetIdRaw)) {
            return false;
        }
        rememberSelection(state, playerId, "beacon", targetIdRaw == null ? "" : targetIdRaw.trim());
        return true;
    }

    private boolean updateVehicleSelection(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String targetIdRaw
    ) {
        VehicleDeployResolution resolution = resolveVehicleSelection(room, state, playerId, teamId, targetIdRaw);
        if (!resolution.success()) {
            return false;
        }
        rememberSelection(state, playerId, "vehicle", targetIdRaw == null ? "" : targetIdRaw.trim());
        return true;
    }

    private RoomPosition resolveTeammateSpawn(RoomDefinition room, RunningRuntimeState state, UUID selfId, RoomTeamId selfTeam, String teammateIdRaw) {
        TeammateSelectionResult validation = validateTeammateSelection(room, state, selfId, selfTeam, teammateIdRaw, server.getTickCount());
        if (!validation.success()) {
            return null;
        }
        ServerPlayer teammate = validation.teammate();
        state.getRespawnTargetYRot().put(selfId, teammate.getYRot());
        state.getRespawnTargetXRot().put(selfId, teammate.getXRot());
        state.getRespawnTargetPose().put(selfId, teammate.getPose());
        return new RoomPosition(Mth.floor(teammate.getX()), Mth.floor(teammate.getY()), Mth.floor(teammate.getZ()));
    }

    public boolean markPlayerDealtDamage(RunningRuntimeState state, UUID attackerId, UUID targetId, long gameTick) {
        if (state == null || attackerId == null || targetId == null || gameTick <= 0L) {
            return false;
        }
        if (attackerId.equals(targetId)) {
            return false;
        }
        if (!state.getRoomPlayers().contains(attackerId) || !state.getRoomPlayers().contains(targetId)) {
            return false;
        }
        state.markPlayerInCombat(attackerId, gameTick + PLAYER_COMBAT_DURATION_TICKS);
        return true;
    }

    public void replaceSelectionWithTeamFallback(RunningRuntimeState state, UUID playerId, RoomTeamId teamId) {
        if (state == null || playerId == null || teamId == null) {
            return;
        }
        rememberSelection(state, playerId, "team", teamId == RoomTeamId.CAMP_A ? "CAMP_A" : "CAMP_B");
    }

    public void applyInitialDefaultSelection(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId
    ) {
        if (state == null || player == null || teamId == null) {
            return;
        }
        if (applyBreakthroughInitialSelection(room, state, player, teamId)) {
            return;
        }
        replaceSelectionWithTeamFallback(state, player.getUUID(), teamId);
    }

    public void clearSelection(RunningRuntimeState state, UUID playerId) {
        if (state == null || playerId == null) {
            return;
        }
        state.getDeploySelectionKinds().remove(playerId);
        state.getDeploySelectionTargetIds().remove(playerId);
        state.getDeploySelectionChangedTicks().remove(playerId);
    }

    public boolean hasTeamFallbackSpawn(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId
    ) {
        return resolveTeamFallbackSpawn(room, state, player, teamId) != null;
    }

    private RoomPosition resolveCaptureSpawn(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId,
        String code
    ) {
        return objectiveSpawnSelectionService.resolveObjectiveSpawn(room, state, player, teamId, code);
    }

    private String describeCaptureSelectionFailure(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String targetIdRaw
    ) {
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        String code = targetIdRaw == null ? "" : targetIdRaw.trim().toUpperCase(Locale.ROOT);
        if (code.isBlank()) {
            return "部署据点无效";
        }
        if (player == null || teamId == null) {
            return "当前玩家不可用于部署";
        }
        return objectiveSpawnSelectionService.assessObjectiveSpawn(room, state, player, teamId, code).failureMessage();
    }

    public VehicleDeployResolution resolveVehicleSelection(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String entryIdRaw
    ) {
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        return vehicleDeploySelectionService.resolve(room, state, player, teamId, entryIdRaw);
    }

    private void rememberSelection(RunningRuntimeState state, UUID playerId, String kind, String targetId) {
        state.getDeploySelectionKinds().put(playerId, kind);
        state.getDeploySelectionTargetIds().put(playerId, targetId);
        state.getDeploySelectionChangedTicks().put(playerId, (long) server.getTickCount());
    }

    private boolean applyBreakthroughInitialSelection(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId
    ) {
        if (room == null || state == null || player == null || teamId == null) {
            return false;
        }
        if (!RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())) {
            return false;
        }
        RoomObjectiveRuntimeProvider runtimeProvider = state.getGameplayRuntime()
            .filter(RoomObjectiveRuntimeProvider.class::isInstance)
            .map(RoomObjectiveRuntimeProvider.class::cast)
            .orElse(null);
        if (runtimeProvider == null || !(runtimeProvider.objectiveRuntimeView() instanceof BreakthroughObjectiveRuntimeView runtimeView)) {
            return false;
        }
        if (teamId == RoomTeamId.CAMP_A && resolveTeamFallbackSpawn(room, state, player, teamId) != null) {
            replaceSelectionWithTeamFallback(state, player.getUUID(), teamId);
            return true;
        }
        int currentStageIndex = runtimeView.deploymentStageIndex(teamId);
        if (currentStageIndex <= 0 || currentStageIndex > runtimeView.maxStageCount()) {
            return false;
        }
        List<RoomCapturePointDefinition> candidates = new ArrayList<>(room.breakthroughConfig().stageActivePoints(currentStageIndex));
        candidates.addAll(room.breakthroughConfig().stageActivePoints(currentStageIndex + (teamId == RoomTeamId.CAMP_A ? -1 : 1)));
        int startIndex = candidates.isEmpty() ? 0 : Math.floorMod(server.getTickCount(), candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            RoomCapturePointDefinition point = candidates.get((startIndex + offset) % candidates.size());
            if (point == null || point.centerPosition() == null) {
                continue;
            }
            RoomPosition spawn = objectiveSpawnSelectionService.resolveObjectiveSpawn(room, state, player, teamId, point.code());
            if (spawn != null) {
                rememberSelection(state, player.getUUID(), "capture", point.code().toUpperCase(Locale.ROOT));
                return true;
            }
        }
        if (resolveTeamFallbackSpawn(room, state, player, teamId) != null) replaceSelectionWithTeamFallback(state, player.getUUID(), teamId);
        else clearSelection(state, player.getUUID());
        return true;
    }

    private RoomPosition resolveTeamFallbackSpawn(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomTeamId teamId
    ) {
        return RoomGameplayDeployPresentationSupport.resolveRespawnAnchorPosition(room, state, player, teamId);
    }

    private boolean isSameSquad(RunningRuntimeState state, UUID firstPlayer, UUID secondPlayer) {
        if (state == null || firstPlayer == null || secondPlayer == null) {
            return false;
        }
        String firstLabel = state.getSquadLabelsByPlayer().get(firstPlayer);
        String secondLabel = state.getSquadLabelsByPlayer().get(secondPlayer);
        return firstLabel != null && !firstLabel.isBlank() && firstLabel.equalsIgnoreCase(secondLabel);
    }

    private TeammateSelectionResult validateTeammateSelection(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        RoomTeamId teamId,
        String targetIdRaw,
        long gameTick
    ) {
        if (room != null && RoomGameplayId.GUNMASTER.equals(room.gameplayId()) && room.gunmasterConfig() != null && room.gunmasterConfig().isSoloGrouping()) {
            return TeammateSelectionResult.failure("个人战模式无法部署到其他玩家位置");
        }
        UUID teammateId;
        try {
            teammateId = UUID.fromString(targetIdRaw == null ? "" : targetIdRaw.trim());
        } catch (Exception ignored) {
            return TeammateSelectionResult.failure("队友部署目标无效");
        }
        if (teammateId.equals(playerId)) {
            return TeammateSelectionResult.failure("不能部署到自己位置");
        }
        if (state == null || !state.getRoomPlayers().contains(teammateId)) {
            return TeammateSelectionResult.failure("该队友已不在房间中");
        }
        if (state.getRoomTeams().get(teammateId) != teamId) {
            return TeammateSelectionResult.failure("该玩家不在你的阵营中");
        }
        if (!isSameSquad(state, playerId, teammateId)) {
            return TeammateSelectionResult.failure("该玩家不在你的小队中");
        }
        ServerPlayer teammate = server.getPlayerList().getPlayer(teammateId);
        if (teammate == null || !teammate.isAlive() || teammate.isSpectator()) {
            return TeammateSelectionResult.failure("该队友当前不可作为部署目标");
        }
        if (state.isInRespawnFlow(teammateId) || state.isPlayerPendingDowned(teammateId) || state.isPlayerDowned(teammateId)) {
            return TeammateSelectionResult.failure("该队友当前处于死亡或倒地流程中，无法部署到其位置");
        }
        if (state.isPlayerInCombat(teammateId, gameTick)) {
            return TeammateSelectionResult.failure("该队友正在战斗中，无法部署到其位置");
        }
        if (!RoomDeployBoundarySupport.allows(room, state, teamId,
            new RoomPosition(Mth.floor(teammate.getX()), teammate.getY(), Mth.floor(teammate.getZ())))) {
            return TeammateSelectionResult.failure("该队友位于当前合法部署范围之外");
        }
        return TeammateSelectionResult.success(teammateId, teammate);
    }

    private record TeammateSelectionResult(UUID teammateId, ServerPlayer teammate, String failureMessage) {
        private static TeammateSelectionResult success(UUID teammateId, ServerPlayer teammate) {
            return new TeammateSelectionResult(teammateId, teammate, null);
        }

        private static TeammateSelectionResult failure(String failureMessage) {
            return new TeammateSelectionResult(null, null, failureMessage);
        }

        private boolean success() {
            return failureMessage == null;
        }
    }
}
