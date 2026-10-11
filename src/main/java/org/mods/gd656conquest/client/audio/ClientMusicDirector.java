package org.mods.gd656conquest.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.common.audio.AudioManager;
import org.mods.gd656conquest.common.audio.StageBeginMusicCue;
import org.mods.gd656conquest.common.match.MatchResultType;

import java.util.Locale;

final class ClientMusicDirector {
    private static final String NONE = "-";
    private static final StageBeginMusicCue stageBeginMusic = new StageBeginMusicCue();
    private static final int DEPLOY_LOOP_FADE_TICKS = 60;
    private static final int RETREAT_LOOP_FADE_TICKS = 20;
    private static final int OBJECTIVE_LOOP_FADE_TICKS = 20;
    private static final int LAST_OBJECTIVE_LOOP_FADE_TICKS = 40;
    private static final int FINAL_SEGMENT_CROSSFADE_TICKS = 20;
    private static final int LOBBY_LOOP_FADE_TICKS = 40;

    private static String lastRoomId = NONE;
    private static String firstDeployTrackRoomId = NONE;
    private static String activeStrategicLoop = NONE;
    private static String activeFinaleLoop = NONE;
    private static boolean wasInRoom;
    private static boolean firstDeployCompleted;
    private static boolean firstDeployOpenMusicPlayed;
    private static boolean deployLoopRequested;

    private ClientMusicDirector() {
    }

    static void tick() {
        MusicContext context = MusicContext.capture();
        if (!context.inConquestDimension) {
            stageBeginMusic.reset();
            deployLoopRequested = false;
            stopDeployLoop();
            stopObjectiveLoop();
            stopStrategicLoop();
            stopFinaleLoop();
            wasInRoom = false;
            lastRoomId = NONE;
            return;
        }
        boolean roomChanged = wasInRoom && context.inRoom && !context.roomId.equals(lastRoomId);
        boolean justLeft = wasInRoom && !context.inRoom;

        if (context.inRoom && !context.roomId.equals(firstDeployTrackRoomId)) {
            firstDeployTrackRoomId = context.roomId;
            firstDeployCompleted = false;
            firstDeployOpenMusicPlayed = false;
        } else if (!context.inRoom) {
            firstDeployTrackRoomId = NONE;
            firstDeployCompleted = false;
            firstDeployOpenMusicPlayed = false;
        }
        if (context.matchingState) {
            firstDeployCompleted = false;
        }

        if (roomChanged || justLeft) {
            stageBeginMusic.reset();
            deployLoopRequested = false;
            stopDeployLoop();
            stopObjectiveLoop();
            stopStrategicLoop();
            stopFinaleLoop();
        }

        updateDeployLoop(context);
        updateStrategicLoop(context);
        updateObjectiveLoop(context);
        updateFinaleLoop(context);
        if (context.matchingState) stageBeginMusic.reset();
        if (stageBeginMusic.update(context.audioState)) {
            AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_STAGE_BEGIN, 1.0F, false);
        }

        wasInRoom = context.inRoom;
        lastRoomId = context.roomId;
    }

    static void onDeathStarted() {
        stopObjectiveLoop();
        AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_DEATH, 1.0F, false);
    }

    static void onDeployTravelStarted() {
        MusicContext context = MusicContext.capture();
        boolean suppressDeployMusic = shouldSuppressDeployLoopForFinale(context);
        if (!firstDeployCompleted && !firstDeployOpenMusicPlayed) {
            if (suppressDeployMusic) {
                firstDeployOpenMusicPlayed = true;
            } else {
                AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_FIRST_DEPLOY, 1.0F, false);
                firstDeployOpenMusicPlayed = true;
            }
        }
        deployLoopRequested = firstDeployCompleted;
        stopObjectiveLoop();
        stopStrategicLoop();
        if (deployLoopRequested && !suppressDeployMusic) {
            playLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 0);
        } else {
            stopDeployLoop();
        }
    }

    static void onDeployOpened() {
        MusicContext context = MusicContext.capture();
        boolean suppressDeployMusic = shouldSuppressDeployLoopForFinale(context);
        if (!firstDeployCompleted && !firstDeployOpenMusicPlayed) {
            if (suppressDeployMusic) {
                firstDeployOpenMusicPlayed = true;
            } else {
                AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_FIRST_DEPLOY, 1.0F, false);
                firstDeployOpenMusicPlayed = true;
            }
        }
        deployLoopRequested = firstDeployCompleted;
        if (deployLoopRequested && !suppressDeployMusic) {
            playLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 0);
        } else {
            stopDeployLoop();
        }
    }

    static void onRespawnTransitionStarted() {
        stopDeployLoop();
        deployLoopRequested = false;
        if (!firstDeployCompleted && wasInRoom) {
            firstDeployCompleted = true;
        }
    }

    static void onMatchEndTransitionStarted() {
        deployLoopRequested = false;
        stopDeployLoop();
        stopObjectiveLoop();
        stopStrategicLoop();
        stopFinaleLoop();
    }

    static void onMatchResult(MatchResultType resultType) {
        if (resultType == null) {
            return;
        }
        switch (resultType) {
            case VICTORY -> AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_VICTORY, 1.0F, false);
            case DEFEAT -> AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_DEFEAT, 1.0F, false);
            case DRAW -> {
                // 平局不复用失败音，保持结算表现中性。
            }
        }
    }

    static void reset() {
        stageBeginMusic.reset();
        lastRoomId = NONE;
        firstDeployTrackRoomId = NONE;
        activeStrategicLoop = NONE;
        activeFinaleLoop = NONE;
        wasInRoom = false;
        firstDeployCompleted = false;
        firstDeployOpenMusicPlayed = false;
        deployLoopRequested = false;
    }

    private static void updateDeployLoop(MusicContext context) {
        boolean shouldPlay = context.inGameWorld
            && deployLoopRequested
            && ClientDeathFlowState.shouldRenderDeployBoundaries()
            && !context.maintenanceState
            && !shouldSuppressDeployLoopForFinale(context);
        if (shouldPlay) {
            playLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 0);
        } else if (ClientAudioManager.getInstance().isMusicActive(ClientMusicCatalog.LOOP_DEPLOY_SCREEN)) {
            stopDeployLoop();
        }
    }

    private static void updateStrategicLoop(MusicContext context) {
        String desired = NONE;
        if (!deployLoopRequested) {
            if (context.inGameWorld && context.inRoom && context.matchingState) {
                desired = ClientMusicCatalog.LOOP_QUEUE;
            } else if (context.inConquestDimension && context.inGameWorld && (context.maintenanceState || (!context.inRoom && !wasInRoom))) {
                desired = ClientMusicCatalog.LOOP_LOBBY;
            } else if ("BT_RETREAT".equals(context.audioState)) {
                if (context.selfTeam == TeamToken.CAMP_A) {
                    desired = ClientMusicCatalog.LOOP_ATTACK;
                } else if (context.selfTeam == TeamToken.CAMP_B) {
                    desired = ClientMusicCatalog.LOOP_RETREAT;
                }
            }
        }
        if (desired.equals(activeStrategicLoop)) {
            if (!NONE.equals(desired)) {
                playLoop(desired, 0);
            }
            return;
        }
        if (!NONE.equals(activeStrategicLoop)) {
            stopLoop(activeStrategicLoop, ClientMusicCatalog.LOOP_LOBBY.equals(activeStrategicLoop) ? LOBBY_LOOP_FADE_TICKS : RETREAT_LOOP_FADE_TICKS);
        }
        activeStrategicLoop = desired;
        if (!NONE.equals(desired)) {
            if (ClientMusicCatalog.LOOP_LOBBY.equals(desired)) {
                // Returning to lobby should fully clear any residual result/death/stage cue music first,
                // otherwise the lobby loop can overlap with one-shot match-end tracks.
                AudioManager.getInstance().stopAllMusic(0);
            }
            playLoop(desired, 0);
        }
    }

    private static void updateObjectiveLoop(MusicContext context) {
        boolean shouldPlay = !deployLoopRequested && !context.maintenanceState && shouldPlayObjectiveLoop(context);
        if (shouldPlay) {
            playLoop(ClientMusicCatalog.LOOP_IN_OBJECTIVE, 0);
            return;
        }
        stopObjectiveLoop();
    }

    private static void updateFinaleLoop(MusicContext context) {
        String desired = resolveDesiredFinaleLoop(context);
        int fadeOutTicks = isLastObjectiveLoop(activeFinaleLoop)
            ? LAST_OBJECTIVE_LOOP_FADE_TICKS
            : FINAL_SEGMENT_CROSSFADE_TICKS;
        int fadeInTicks = 0;
        if (ClientMusicCatalog.LOOP_FINAL_SEGMENT_VICTORY.equals(desired)
            || ClientMusicCatalog.LOOP_FINAL_SEGMENT_DEFEAT.equals(desired)
            || ClientMusicCatalog.LOOP_DESPERATE_MODE.equals(desired)) {
            fadeOutTicks = FINAL_SEGMENT_CROSSFADE_TICKS;
            fadeInTicks = FINAL_SEGMENT_CROSSFADE_TICKS;
        } else if (isLastObjectiveLoop(desired)) {
            fadeOutTicks = LAST_OBJECTIVE_LOOP_FADE_TICKS;
            fadeInTicks = ClientMusicCatalog.LOOP_FINAL_SEGMENT_VICTORY.equals(activeFinaleLoop)
                || ClientMusicCatalog.LOOP_FINAL_SEGMENT_DEFEAT.equals(activeFinaleLoop)
                || ClientMusicCatalog.LOOP_DESPERATE_MODE.equals(activeFinaleLoop)
                ? FINAL_SEGMENT_CROSSFADE_TICKS
                : 0;
        }
        if (desired.equals(activeFinaleLoop)) {
            if (!NONE.equals(desired)) {
                playLoop(desired, 0);
            }
            return;
        }
        if (!NONE.equals(activeFinaleLoop)) {
            stopLoop(activeFinaleLoop, fadeOutTicks);
        }
        activeFinaleLoop = desired;
        if (!NONE.equals(desired)) {
            playLoop(desired, fadeInTicks);
        }
    }

    private static boolean shouldSuppressDeployLoopForFinale(MusicContext context) {
        return !NONE.equals(resolveDesiredFinaleLoop(context));
    }

    private static String resolveDesiredFinaleLoop(MusicContext context) {
        if (context.maintenanceState) {
            return NONE;
        }
        if ("BT_DESPERATE".equals(context.audioState)) {
            return ClientMusicCatalog.LOOP_DESPERATE_MODE;
        }
        if ("BT_LAST_SEGMENT".equals(context.audioState)) {
            if (context.selfTeam == TeamToken.CAMP_A) {
                return ClientMusicCatalog.LOOP_FINAL_SEGMENT_VICTORY;
            }
            if (context.selfTeam == TeamToken.CAMP_B) {
                return ClientMusicCatalog.LOOP_FINAL_SEGMENT_DEFEAT;
            }
        } else if ("FINAL_OBJECTIVE_VICTORY".equals(context.audioState)) {
            return ClientMusicCatalog.LOOP_LAST_OBJECTIVE_VICTORY;
        } else if ("FINAL_OBJECTIVE_DEFEAT".equals(context.audioState)
            || "FINAL_OBJECTIVE".equals(context.audioState)) {
            return ClientMusicCatalog.LOOP_LAST_OBJECTIVE;
        }
        return NONE;
    }

    private static boolean shouldPlayObjectiveLoop(MusicContext context) {
        if (!context.inRoom || context.selfTeam == TeamToken.NEUTRAL) {
            return false;
        }
        CaptureFocusState focusState = parseCaptureFocusState(ClientHudDebugState.getCaptureFocus());
        if (focusState == null) {
            return false;
        }
        if (focusState.driving != context.selfTeam) {
            return false;
        }
        if (focusState.owner == context.selfTeam) {
            return false;
        }
        return !focusState.isFullyOwnedBy(context.selfTeam);
    }

    private static CaptureFocusState parseCaptureFocusState(String payloadRaw) {
        String payload = normalize(payloadRaw);
        if (NONE.equals(payload)) {
            return null;
        }
        String[] cols = payload.split(",");
        if (cols.length < 7) {
            return null;
        }
        try {
            float progress = Float.parseFloat(cols[4]);
            TeamToken owner = TeamToken.fromToken(cols[5]);
            TeamToken driving = TeamToken.fromToken(cols[6]);
            return new CaptureFocusState(progress, owner, driving);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void playLoop(String soundId, int fadeInTicks) {
        AudioManager.getInstance().playMusic(soundId, 1.0F, true, fadeInTicks);
    }

    private static void stopLoop(String soundId, int fadeOutTicks) {
        if (NONE.equals(soundId)) {
            return;
        }
        AudioManager.getInstance().stopMusic(soundId, fadeOutTicks);
    }

    private static void stopDeployLoop() {
        stopLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, DEPLOY_LOOP_FADE_TICKS);
    }

    private static void stopObjectiveLoop() {
        stopLoop(ClientMusicCatalog.LOOP_IN_OBJECTIVE, OBJECTIVE_LOOP_FADE_TICKS);
    }

    private static void stopStrategicLoop() {
        if (NONE.equals(activeStrategicLoop)) {
            return;
        }
        stopLoop(activeStrategicLoop, ClientMusicCatalog.LOOP_LOBBY.equals(activeStrategicLoop) ? LOBBY_LOOP_FADE_TICKS : RETREAT_LOOP_FADE_TICKS);
        activeStrategicLoop = NONE;
    }

    private static void stopFinaleLoop() {
        if (NONE.equals(activeFinaleLoop)) {
            return;
        }
        stopLoop(activeFinaleLoop, isLastObjectiveLoop(activeFinaleLoop) ? LAST_OBJECTIVE_LOOP_FADE_TICKS : FINAL_SEGMENT_CROSSFADE_TICKS);
        activeFinaleLoop = NONE;
    }

    private static boolean isLastObjectiveLoop(String soundId) {
        return ClientMusicCatalog.LOOP_LAST_OBJECTIVE.equals(soundId)
            || ClientMusicCatalog.LOOP_LAST_OBJECTIVE_VICTORY.equals(soundId);
    }

    private static String normalize(String value) {
        if (value == null) {
            return NONE;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? NONE : trimmed;
    }

    private record MusicContext(
        boolean inConquestDimension,
        boolean inGameWorld,
        boolean inRoom,
        boolean maintenanceState,
        boolean matchingState,
        int remainSeconds,
        String roomId,
        String audioState,
        TeamToken selfTeam
    ) {
        static MusicContext capture() {
            Minecraft minecraft = Minecraft.getInstance();
            boolean inGameWorld = minecraft.player != null && minecraft.level != null;
            ResourceKey<Level> dimensionKey = inGameWorld ? minecraft.level.dimension() : null;
            boolean inConquestDimension = dimensionKey != null
                && "gd656conquest".equalsIgnoreCase(dimensionKey.location().getNamespace());
            String roomId = normalize(ClientHudDebugState.getRoomId());
            String roomState = normalize(ClientHudDebugState.getRoomState());
            return new MusicContext(
                inConquestDimension,
                inGameWorld,
                !roomId.isBlank() && !NONE.equals(roomId) && !roomState.contains("未加入"),
                roomState.contains("维护中"),
                roomState.contains("匹配中"),
                Math.max(0, ClientHudDebugState.getRemainSeconds()),
                roomId,
                normalize(ClientHudDebugState.getAudioState()),
                TeamToken.fromClientDebug(ClientHudDebugState.getSelfTeam())
            );
        }
    }

    private enum TeamToken {
        NEUTRAL,
        CAMP_A,
        CAMP_B;

        static TeamToken fromToken(String token) {
            if ("T".equalsIgnoreCase(token)) {
                return CAMP_A;
            }
            if ("S".equalsIgnoreCase(token)) {
                return CAMP_B;
            }
            return NEUTRAL;
        }

        static TeamToken fromClientDebug(String selfTeam) {
            String normalized = normalize(selfTeam).toLowerCase(Locale.ROOT);
            if (normalized.contains("campa") || normalized.contains("阵营a")) {
                return CAMP_A;
            }
            if (normalized.contains("campb") || normalized.contains("阵营b")) {
                return CAMP_B;
            }
            return NEUTRAL;
        }
    }

    private record CaptureFocusState(float progress, TeamToken owner, TeamToken driving) {
        private boolean isFullyOwnedBy(TeamToken team) {
            if (team == TeamToken.CAMP_A) {
                return progress >= 99.9F;
            }
            if (team == TeamToken.CAMP_B) {
                return progress <= 0.1F;
            }
            return false;
        }
    }
}
