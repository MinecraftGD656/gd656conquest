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
        return true;
    }

    private void processBoundaryRule(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        UnifiedBoundaryService.process(server, room, state, gameTick);
    }

    private List<List<org.mods.gd656conquest.server.room.model.RoomPosition>> resolveConquestAllowedBoundaries(RoomDefinition room, RoomTeamId teamId) {
        if (room == null) {
            return List.of();
        }
        return room.conquestConfig().allowedBoundariesForTeam(teamId);
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
        Long lastReportTick = state.getBoundaryHeightReportTicks().get(playerId);
        if (lastReportTick == null || gameTick - lastReportTick > CLIENT_BOUNDARY_HEIGHT_REPORT_TTL_TICKS) {
            state.getBoundaryPlayerMaxYReportedPlayers().remove(playerId);
            state.getBoundaryAircraftMaxYReportedPlayers().remove(playerId);
            return false;
        }
        Entity vehicle = player.getVehicle();
        double confirmedY = aircraftVehicle && vehicle != null ? Math.max(player.getY(), vehicle.getY()) : player.getY();
        if (aircraftVehicle) {
            int maxAircraftPassengerY = room.commonConfig().aircraftPassengerMaxY();
            return maxAircraftPassengerY > 0
                && state.getBoundaryAircraftMaxYReportedPlayers().contains(playerId)
                && confirmedY > maxAircraftPassengerY;
        }
        int maxPlayerY = room.commonConfig().playerMaxY();
        return maxPlayerY > 0
            && state.getBoundaryPlayerMaxYReportedPlayers().contains(playerId)
            && confirmedY > maxPlayerY;
    }

    private void clearBoundaryPenalty(ServerPlayer player, RunningRuntimeState state, UUID playerId) {
        UnifiedBoundaryService.clear(player, state, playerId);
    }

}
