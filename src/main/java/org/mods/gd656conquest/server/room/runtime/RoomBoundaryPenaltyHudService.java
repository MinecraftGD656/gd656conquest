package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;

public final class RoomBoundaryPenaltyHudService {
    private static final String ACTIONBAR_SOURCE_BOUNDARY_AIRCRAFT = "room_boundary_aircraft";
    private static final int BOUNDARY_TITLE_FADE_IN_TICKS = 5;
    private static final int BOUNDARY_TITLE_STAY_TICKS = 20;
    private static final int BOUNDARY_TITLE_FADE_OUT_TICKS = 10;
    private static final int BOUNDARY_TITLE_REFRESH_FADE_IN_TICKS = 0;
    private static final int BOUNDARY_TITLE_REFRESH_STAY_TICKS = 22;
    private static final int BOUNDARY_TITLE_REFRESH_FADE_OUT_TICKS = 4;
    private static final int BOUNDARY_TITLE_COLOR = 0xF2F4F5;
    private static final int BOUNDARY_SUBTITLE_TEXT_COLOR = 0xC6CCD1;
    private static final int BOUNDARY_COUNTDOWN_NORMAL_COLOR = 0x55FFFF;
    private static final int BOUNDARY_COUNTDOWN_GREEN_COLOR = 0x55FF55;
    private static final int BOUNDARY_COUNTDOWN_YELLOW_COLOR = 0xFFFF55;
    private static final int BOUNDARY_COUNTDOWN_ORANGE_COLOR = 0xFF9900;
    private static final int BOUNDARY_COUNTDOWN_RED_COLOR = 0xFF5555;

    public void sendBoundaryTitle(ServerPlayer player, int remainingSeconds, boolean refreshAnimation, boolean aircraftStyle) {
        if (player == null || player.connection == null) {
            return;
        }
        if (aircraftStyle) {
            ServerActionBarService.showBuilder(
                player,
                ACTIONBAR_SOURCE_BOUNDARY_AIRCRAFT,
                ServerActionBarService.builder()
                    .text("回到核心战斗区域 ", ChatFormatting.GRAY)
                    .text(String.valueOf(Math.max(1, remainingSeconds)), ChatFormatting.RED)
                    .text("秒", ChatFormatting.GRAY),
                25
            );
            return;
        }
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_BOUNDARY_AIRCRAFT);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
            refreshAnimation ? BOUNDARY_TITLE_FADE_IN_TICKS : BOUNDARY_TITLE_REFRESH_FADE_IN_TICKS,
            refreshAnimation ? BOUNDARY_TITLE_STAY_TICKS : BOUNDARY_TITLE_REFRESH_STAY_TICKS,
            refreshAnimation ? BOUNDARY_TITLE_FADE_OUT_TICKS : BOUNDARY_TITLE_REFRESH_FADE_OUT_TICKS
        ));
        player.connection.send(new ClientboundSetTitleTextPacket(buildBoundaryTitle()));
        player.connection.send(new ClientboundSetSubtitleTextPacket(buildBoundarySubtitle(remainingSeconds)));
    }

    private Component buildBoundaryTitle() {
        return Component.literal("请回到战斗区域")
            .withStyle(style -> style.withBold(true).withColor(BOUNDARY_TITLE_COLOR));
    }

    private Component buildBoundarySubtitle(int remainingSeconds) {
        int countdownColor = resolveBoundaryCountdownColor(remainingSeconds);
        MutableComponent subtitle = Component.empty();
        subtitle.append(Component.literal("你将在 ").withStyle(style -> style.withColor(BOUNDARY_SUBTITLE_TEXT_COLOR)));
        subtitle.append(Component.literal(String.valueOf(remainingSeconds)).withStyle(style -> style.withColor(countdownColor)));
        subtitle.append(Component.literal(" 秒后被清理").withStyle(style -> style.withColor(BOUNDARY_SUBTITLE_TEXT_COLOR)));
        return subtitle;
    }

    public void clearBoundaryHud(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_BOUNDARY_AIRCRAFT);
    }

    private int resolveBoundaryCountdownColor(int remainingSeconds) {
        if (remainingSeconds <= 1) {
            return BOUNDARY_COUNTDOWN_RED_COLOR;
        }
        if (remainingSeconds == 2) {
            return BOUNDARY_COUNTDOWN_ORANGE_COLOR;
        }
        if (remainingSeconds == 3) {
            return BOUNDARY_COUNTDOWN_YELLOW_COLOR;
        }
        if (remainingSeconds <= 5) {
            return BOUNDARY_COUNTDOWN_GREEN_COLOR;
        }
        return BOUNDARY_COUNTDOWN_NORMAL_COLOR;
    }
}
