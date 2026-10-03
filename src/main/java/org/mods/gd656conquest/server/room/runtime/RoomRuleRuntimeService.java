package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveBlockAreaResolver;
import org.mods.gd656conquest.server.room.vehicle.VehicleBoundaryPenaltyService;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.shared.text.ServerText;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RoomRuleRuntimeService {
    private static final long BOUNDARY_CHECK_INTERVAL_TICKS = 5L;
    private static final long HUNGER_REFRESH_INTERVAL_TICKS = 20L;
    private static final long AUTO_TEAM_SWITCH_EVALUATE_INTERVAL_TICKS = 20L;
    private static final long AUTO_TEAM_SWITCH_PROMPT_INTERVAL_TICKS = 30L * 20L;
    private static final long CLIENT_BOUNDARY_HEIGHT_REPORT_TTL_TICKS = 20L;
    private static final int AUTO_TEAM_SWITCH_MIN_DELTA = 2;
    private final MinecraftServer server;
    private final RoomBoundaryPenaltyHudService boundaryPenaltyHudService = new RoomBoundaryPenaltyHudService();
    private final RoomObjectiveBlockAreaResolver objectiveBlockAreaResolver = new RoomObjectiveBlockAreaResolver();
    private final VehicleBoundaryPenaltyService vehicleBoundaryPenaltyService;

    public RoomRuleRuntimeService(MinecraftServer server, VehicleBoundaryPenaltyService vehicleBoundaryPenaltyService) {
        this.server = Objects.requireNonNull(server, "server");
        this.vehicleBoundaryPenaltyService = Objects.requireNonNull(vehicleBoundaryPenaltyService, "vehicleBoundaryPenaltyService");
    }

    public void tick(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        if (room.commonConfig().disableHunger() && gameTick % HUNGER_REFRESH_INTERVAL_TICKS == 0L) {
            applyNoHungerRule(state);
        }
        if (gameTick % AUTO_TEAM_SWITCH_EVALUATE_INTERVAL_TICKS == 0L) {
            processAutoTeamSwitchPrompt(room, state, gameTick);
        }
        if (gameTick % BOUNDARY_CHECK_INTERVAL_TICKS == 0L) {
            processBoundaryRule(room, state, gameTick);
        }
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        clearBoundaryPenalty(player, null, playerId);
    }

    private void applyNoHungerRule(RunningRuntimeState state) {
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0, true, false, false));
        }
    }

    private void processAutoTeamSwitchPrompt(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (ConquestDataManager.of(server).serverConfig().isExperimentalPlayerBalanceEnabled()) {
            resetAutoTeamSwitchPromptState(state);
            return;
        }
        if (!room.commonConfig().enableAutoTeamSwitchPrompt()) {
            resetAutoTeamSwitchPromptState(state);
            return;
        }
        int campACount = countPlayersInTeam(state, RoomTeamId.CAMP_A);
        int campBCount = countPlayersInTeam(state, RoomTeamId.CAMP_B);
        int delta = campACount - campBCount;
        if (Math.abs(delta) < AUTO_TEAM_SWITCH_MIN_DELTA) {
            resetAutoTeamSwitchPromptState(state);
            return;
        }
        if (state.getAutoTeamBalanceLastDelta() != delta) {
            state.setAutoTeamBalanceLastDelta(delta);
            state.setAutoTeamBalanceImbalanceSinceTick(gameTick);
            state.setAutoTeamBalanceLastPromptTick(-1L);
            state.getAutoTeamSwitchOfferTargetTeams().clear();
            state.getAutoTeamSwitchOfferExpireTicks().clear();
            return;
        }
        long imbalanceSince = state.getAutoTeamBalanceImbalanceSinceTick();
        if (imbalanceSince < 0L) {
            state.setAutoTeamBalanceImbalanceSinceTick(gameTick);
            return;
        }
        if (gameTick - imbalanceSince < AUTO_TEAM_SWITCH_PROMPT_INTERVAL_TICKS) {
            return;
        }
        long lastPromptTick = state.getAutoTeamBalanceLastPromptTick();
        if (lastPromptTick >= 0L && gameTick - lastPromptTick < AUTO_TEAM_SWITCH_PROMPT_INTERVAL_TICKS) {
            return;
        }
        RoomTeamId largerTeam = delta > 0 ? RoomTeamId.CAMP_A : RoomTeamId.CAMP_B;
        RoomTeamId targetTeam = largerTeam == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        List<ServerPlayer> candidates = new ArrayList<>();
        for (UUID playerId : state.getRoomPlayers()) {
            if (state.getRoomTeams().get(playerId) != largerTeam) {
                continue;
            }
            ServerPlayer candidate = server.getPlayerList().getPlayer(playerId);
            if (candidate != null && isAutoTeamSwitchPromptEligible(state, candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        ServerPlayer selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        state.getAutoTeamSwitchOfferTargetTeams().put(selected.getUUID(), targetTeam);
        state.getAutoTeamSwitchOfferExpireTicks().put(selected.getUUID(), gameTick + AUTO_TEAM_SWITCH_PROMPT_INTERVAL_TICKS);
        state.setAutoTeamBalanceLastPromptTick(gameTick);
        String command = "/gdc action acceptcamp";
        MutableComponent prompt = ServerText.sectionPrefix("房间")
            .append(ServerText.colored("当前对局双方人数不等，是否愿意加入敌方阵营 ", ChatFormatting.WHITE))
            .append(ServerText.action(new ClickActionSpec("[我同意]", ChatFormatting.GOLD, command, "点击确认自动换队")));
        ServerMessageSender.rawSystem(selected, prompt);
    }

    private boolean isAutoTeamSwitchPromptEligible(RunningRuntimeState state, ServerPlayer candidate) {
        if (state == null || candidate == null) {
            return false;
        }
        UUID playerId = candidate.getUUID();
        if (state.getPendingTeamChanges().containsKey(playerId) || state.getAutoTeamSwitchOfferTargetTeams().containsKey(playerId)) {
            return false;
        }
        if (state.isInRespawnFlow(playerId)) {
            return false;
        }
        return true;
    }

    private void processBoundaryRule(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        RoomGameplayBoundaryPolicyProvider boundaryPolicyProvider = RoomGameplayBoundaryPolicyProvider.resolve(state);
        int countdownSeconds = boundaryPolicyProvider == null
            ? room.gameplayBoundaryPenaltySeconds()
            : boundaryPolicyProvider.boundaryPenaltySeconds(room);
        if (countdownSeconds <= 0) {
            for (UUID playerId : new ArrayList<>(state.getOutsideBoundarySinceTicks().keySet())) {
                clearBoundaryPenalty(server.getPlayerList().getPlayer(playerId), state, playerId);
            }
            vehicleBoundaryPenaltyService.processAircraftBoundaryTimeouts(room, state, gameTick);
            return;
        }
        boolean hasConfiguredBoundary = boundaryPolicyProvider == null
            ? room != null && room.conquestConfig().hasAnyBoundaryConfigured()
            : boundaryPolicyProvider.hasConfiguredBoundary(room, state);
        if (!hasConfiguredBoundary) {
            for (UUID playerId : new ArrayList<>(state.getOutsideBoundarySinceTicks().keySet())) {
                clearBoundaryPenalty(server.getPlayerList().getPlayer(playerId), state, playerId);
            }
            vehicleBoundaryPenaltyService.processAircraftBoundaryTimeouts(room, state, gameTick);
            return;
        }
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                vehicleBoundaryPenaltyService.updateAircraftBoundaryState(room, state, player, null, false, gameTick);
                clearBoundaryPenalty(player, state, playerId);
                continue;
            }
            if (state.isInRespawnFlow(playerId) || isBoundaryRuleImmune(player)) {
                vehicleBoundaryPenaltyService.updateAircraftBoundaryState(room, state, player, null, false, gameTick);
                clearBoundaryPenalty(player, state, playerId);
                continue;
            }
            RoomTeamId teamId = state.getRoomTeams().get(playerId);
            boolean outsideAllowedBoundary = RoomBoundaryRuleEvaluator.isOutsideAllowedBoundary(room, state, teamId, player);
            boolean aircraftVehicle = VehicleCompatFacade.isManagedVehicleEntity(player.getVehicle())
                && VehicleCompatFacade.isAircraftEntity(player.getVehicle());
            boolean insideOutOfBoundsBlock = objectiveBlockAreaResolver.isPlayerInsideOutOfBoundsBlock(player);
            boolean aboveConfiguredHeight = isAboveConfiguredBoundaryHeight(room, state, player, aircraftVehicle, gameTick);
            boolean forcePlayerBoundaryPenalty = insideOutOfBoundsBlock || aboveConfiguredHeight;
            boolean trackedAircraftBoundary = outsideAllowedBoundary && !forcePlayerBoundaryPenalty;
            vehicleBoundaryPenaltyService.updateAircraftBoundaryState(room, state, player, teamId, trackedAircraftBoundary, gameTick);
            boolean aircraftBoundaryPenalty = aircraftVehicle && trackedAircraftBoundary;
            boolean playerBoundaryPenalty = (!aircraftVehicle && outsideAllowedBoundary) || forcePlayerBoundaryPenalty;
            if (!playerBoundaryPenalty && !aircraftBoundaryPenalty) {
                clearBoundaryPenalty(player, state, playerId);
                continue;
            }
            int effectiveCountdownSeconds = aircraftBoundaryPenalty
                ? room.gameplayAircraftBoundaryCleanupSeconds()
                : countdownSeconds;
            if (effectiveCountdownSeconds <= 0) {
                clearBoundaryPenalty(player, state, playerId);
                continue;
            }
            long outsideSince;
            if (aircraftBoundaryPenalty && player.getVehicle() != null) {
                state.getOutsideBoundarySinceTicks().remove(playerId);
                outsideSince = state.getAircraftOutsideBoundarySinceTicks().getOrDefault(player.getVehicle().getUUID(), gameTick);
            } else {
                outsideSince = state.getOutsideBoundarySinceTicks().computeIfAbsent(playerId, ignored -> gameTick);
            }
            boolean enteringPenalty = state.getBoundaryPenaltyVisualApplied().add(playerId);
            Boolean lastAircraftStyle = state.getBoundaryPenaltyAircraftStyle().get(playerId);
            boolean aircraftStyleChanged = lastAircraftStyle == null || lastAircraftStyle != aircraftBoundaryPenalty;
            if (enteringPenalty || aircraftStyleChanged) {
                // GD656Mods: 把"是否空中载具乘员样式"一并下发 —— 客户端只有步兵出界才降低视角饱和度。
                // 样式在惩罚期间可能变化(出界后上/下飞机), 因此变化时必须重发: 既让饱和度跟上,
                // 也重发提示(空中样式走 actionbar、步兵样式走 title, 只靠秒数变化刷新会残留旧样式的提示)。
                NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(true, aircraftBoundaryPenalty));
                state.getBoundaryPenaltyAircraftStyle().put(playerId, aircraftBoundaryPenalty);
                boundaryPenaltyHudService.sendBoundaryTitle(player, effectiveCountdownSeconds, enteringPenalty, aircraftBoundaryPenalty);
                state.getBoundaryPenaltyLastShownSeconds().put(playerId, effectiveCountdownSeconds);
            }
            long outsideTicks = gameTick - outsideSince;
            long killAfterTicks = (long) effectiveCountdownSeconds * 20L;
            if (outsideTicks >= killAfterTicks) {
                if (aircraftBoundaryPenalty) {
                    continue;
                }
                state.getBoundaryDeathPendingPlayers().add(playerId);
                clearBoundaryPenalty(player, state, playerId);
                vehicleBoundaryPenaltyService.handleBoundaryTimeout(room, state, player, gameTick);
                player.kill();
                continue;
            }
            int remainingSeconds = Math.max(1, effectiveCountdownSeconds - (int) (outsideTicks / 20L));
            int lastShownSeconds = state.getBoundaryPenaltyLastShownSeconds().getOrDefault(playerId, -1);
            if (remainingSeconds != lastShownSeconds) {
                boundaryPenaltyHudService.sendBoundaryTitle(player, remainingSeconds, enteringPenalty, aircraftBoundaryPenalty);
                state.getBoundaryPenaltyLastShownSeconds().put(playerId, remainingSeconds);
            }
        }
        vehicleBoundaryPenaltyService.processAircraftBoundaryTimeouts(room, state, gameTick);
    }

    private int countPlayersInTeam(RunningRuntimeState state, RoomTeamId teamId) {
        int count = 0;
        for (UUID playerId : state.getRoomPlayers()) {
            if (state.getRoomTeams().get(playerId) == teamId) {
                count++;
            }
        }
        return count;
    }

    private void resetAutoTeamSwitchPromptState(RunningRuntimeState state) {
        state.setAutoTeamBalanceLastDelta(0);
        state.setAutoTeamBalanceImbalanceSinceTick(-1L);
        state.setAutoTeamBalanceLastPromptTick(-1L);
        state.getAutoTeamSwitchOfferTargetTeams().clear();
        state.getAutoTeamSwitchOfferExpireTicks().clear();
    }

    private boolean isBoundaryRuleImmune(ServerPlayer player) {
        return player != null
            && player.hasPermissions(2)
            && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    private boolean isAboveConfiguredBoundaryHeight(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        boolean aircraftVehicle,
        long gameTick
    ) {
        if (room == null || state == null || player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        // 客户端上报过期时仅清理陈旧标记, 判定不再依赖客户端上报(服务端权威)
        Long lastReportTick = state.getBoundaryHeightReportTicks().get(playerId);
        if (lastReportTick == null || gameTick - lastReportTick > CLIENT_BOUNDARY_HEIGHT_REPORT_TTL_TICKS) {
            state.getBoundaryPlayerMaxYReportedPlayers().remove(playerId);
            state.getBoundaryAircraftMaxYReportedPlayers().remove(playerId);
        }
        Entity vehicle = player.getVehicle();
        double confirmedY = aircraftVehicle && vehicle != null ? Math.max(player.getY(), vehicle.getY()) : player.getY();
        if (aircraftVehicle) {
            int maxAircraftPassengerY = room.commonConfig().aircraftPassengerMaxY();
            return maxAircraftPassengerY > 0 && confirmedY > maxAircraftPassengerY;
        }
        int maxPlayerY = room.commonConfig().playerMaxY();
        // GD656Mods: 服务端权威判定高度上限(不依赖客户端上报), 支持负数配置
        // (地下房间, 1.17 世界最低 Y 为 -64); 0 表示未配置(不检查高度), 负数同样参与上限判定。
        return maxPlayerY != 0 && confirmedY > maxPlayerY;
    }

    private void clearBoundaryPenalty(ServerPlayer player, RunningRuntimeState state, UUID playerId) {
        boundaryPenaltyHudService.clearBoundaryHud(player);
        if (state != null && playerId != null) {
            state.getOutsideBoundarySinceTicks().remove(playerId);
            state.getBoundaryPenaltyLastShownSeconds().remove(playerId);
            state.getBoundaryPenaltyVisualApplied().remove(playerId);
            state.getBoundaryPenaltyAircraftStyle().remove(playerId);
        }
        if (player != null) {
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
        }
    }

}
