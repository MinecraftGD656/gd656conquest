package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

public final class BreakthroughStageTransitionTitleService {
    private static final String ACTIONBAR_SOURCE_RETREAT = "breakthrough_retreat";
    private static final int TITLE_FADE_IN_TICKS = 5;
    private static final int TITLE_STAY_TICKS = 20;
    private static final int TITLE_FADE_OUT_TICKS = 10;
    private static final int TITLE_REFRESH_FADE_IN_TICKS = 0;
    private static final int TITLE_REFRESH_STAY_TICKS = 22;
    private static final int TITLE_REFRESH_FADE_OUT_TICKS = 4;
    private static final int COUNTDOWN_NORMAL_COLOR = 0x55FFFF;
    private static final int COUNTDOWN_GREEN_COLOR = 0x55FF55;
    private static final int COUNTDOWN_YELLOW_COLOR = 0xFFFF55;
    private static final int COUNTDOWN_ORANGE_COLOR = 0xFF9900;
    private static final int COUNTDOWN_RED_COLOR = 0xFF5555;
    private static final int SUBTITLE_ATTACKER_COLOR = 0x55FFFF;
    private static final int SUBTITLE_DEFENDER_COLOR = 0xFF5555;
    private final MinecraftServer server;

    public BreakthroughStageTransitionTitleService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void syncCountdownTitles(RunningRuntimeState state, BreakthroughRuntimeState runtimeState, long gameTick) {
        if (state == null || runtimeState == null || runtimeState.getAdvanceCountdownEndTick() <= 0L) {
            return;
        }
        int remainingSeconds = (int) Math.max(1L, (runtimeState.getAdvanceCountdownEndTick() - gameTick + 19L) / 20L);
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            int lastShown = runtimeState.getAdvanceCountdownLastShownSecondsByPlayer().getOrDefault(playerId, -1);
            if (lastShown == remainingSeconds) {
                continue;
            }
            sendTitle(player, state.getRoomTeams().get(playerId), remainingSeconds, lastShown < 0);
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
                player.connection.send(new ClientboundClearTitlesPacket(true));
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
        int retreatStageIndex = runtimeState.getRetreatStageIndex();
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
                && captureRuntimeService.isInsideStageBoundary(room, player, retreatStageIndex);
            if (!shouldShow) {
                clearSingleTitle(player, runtimeState.getRetreatCountdownLastShownSecondsByPlayer().remove(playerId) != null);
                ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_RETREAT);
                continue;
            }
            int lastShown = runtimeState.getRetreatCountdownLastShownSecondsByPlayer().getOrDefault(playerId, -1);
            if (lastShown == remainingSeconds) {
                continue;
            }
            clearSingleTitle(player, lastShown >= 0);
            sendRetreatActionbar(player, remainingSeconds, room.breakthroughConfig().troopGainPerDefenderKill());
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
            ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_RETREAT);
        }
        runtimeState.getRetreatCountdownLastShownSecondsByPlayer().clear();
    }

    private void sendTitle(ServerPlayer player, RoomTeamId teamId, int remainingSeconds, boolean firstShow) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
            firstShow ? TITLE_FADE_IN_TICKS : TITLE_REFRESH_FADE_IN_TICKS,
            firstShow ? TITLE_STAY_TICKS : TITLE_REFRESH_STAY_TICKS,
            firstShow ? TITLE_FADE_OUT_TICKS : TITLE_REFRESH_FADE_OUT_TICKS
        ));
        player.connection.send(new ClientboundSetTitleTextPacket(buildTitle(remainingSeconds)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(buildSubtitle(teamId)));
    }

    private void sendRetreatActionbar(ServerPlayer player, int remainingSeconds, int troopGainPerDefenderKill) {
        if (player == null) {
            return;
        }
        ServerActionBarService.showBuilder(
            player,
            ACTIONBAR_SOURCE_RETREAT,
            ServerActionBarService.builder()
                .text("请在", ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(remainingSeconds))
                    .withStyle(style -> style.withColor(resolveCountdownColor(remainingSeconds)).withBold(true)))
                .text("秒 内撤离至下一阶段", ChatFormatting.GRAY)
                // GD656Mods: 撤退倒计时末尾追加提示, 告知防守方在此死亡会为进攻方补充兵力
                .text(" 若您在此死亡将使敌军恢复", ChatFormatting.RED)
                .append(Component.literal(String.valueOf(Math.max(0, troopGainPerDefenderKill)))
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_RED)))
                .text("兵力", ChatFormatting.RED),
            30
        );
    }

    private Component buildTitle(int remainingSeconds) {
        return Component.literal(String.valueOf(remainingSeconds))
            .withStyle(style -> style.withBold(true).withColor(resolveCountdownColor(remainingSeconds)));
    }

    private Component buildSubtitle(RoomTeamId teamId) {
        boolean attacker = teamId == RoomTeamId.CAMP_A;
        MutableComponent subtitle = Component.literal(attacker ? "准备夺取下个阶段" : "准备防守阵地");
        return subtitle.withStyle(style -> style.withColor(attacker ? SUBTITLE_ATTACKER_COLOR : SUBTITLE_DEFENDER_COLOR).withBold(true));
    }

    private int resolveCountdownColor(int remainingSeconds) {
        if (remainingSeconds <= 1) {
            return COUNTDOWN_RED_COLOR;
        }
        if (remainingSeconds == 2) {
            return COUNTDOWN_ORANGE_COLOR;
        }
        if (remainingSeconds == 3) {
            return COUNTDOWN_YELLOW_COLOR;
        }
        if (remainingSeconds <= 5) {
            return COUNTDOWN_GREEN_COLOR;
        }
        return COUNTDOWN_NORMAL_COLOR;
    }

    private void clearSingleTitle(ServerPlayer player, boolean shouldClear) {
        if (!shouldClear || player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundClearTitlesPacket(true));
    }
}
