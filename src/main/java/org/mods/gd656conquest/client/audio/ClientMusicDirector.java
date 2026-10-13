package org.mods.gd656conquest.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientBriefingPlayback;
import org.mods.gd656conquest.common.audio.AudioManager;
import org.mods.gd656conquest.common.audio.StageBeginMusicCue;
import org.mods.gd656conquest.common.audio.OperationsIntermissionAudioCue;
import org.mods.gd656conquest.common.audio.OperationsIntermissionMusicCue;
import org.mods.gd656conquest.common.match.BattalionSequence.Phase;
import org.mods.gd656conquest.network.packet.BattalionBriefingPacket;
import org.mods.gd656conquest.common.match.MatchResultType;

import java.util.Locale;

final class ClientMusicDirector {
    private static final String NONE = "-";
    private static final StageBeginMusicCue stageBeginMusic = new StageBeginMusicCue();
    private static final OperationsIntermissionAudioCue intermissionAudio = new OperationsIntermissionAudioCue();
    private static final OperationsIntermissionMusicCue intermissionMusic = new OperationsIntermissionMusicCue();
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
    private static Object intermissionWorld;
    private static float intermissionMusicVolume = 1.0F;
    private static boolean operationExclusive;

    private ClientMusicDirector() {
    }

    static void tick() {
        MusicContext context = MusicContext.capture();
        boolean operations = context.inConquestDimension && context.inRoom && !context.matchingState && !context.maintenanceState
            && ("BREAKTHROUGH".equalsIgnoreCase(ClientHudDebugState.getScoreMethod()) || ClientOperationsMusicDirector.ownsRoom(context.roomId));
        if (operations) {
            if (!operationExclusive) {
                resetIntermissionAudio(); stopObjectiveLoop(); stopStrategicLoop(); stopFinaleLoop();
                AudioManager.getInstance().stopMusic(ClientMusicCatalog.MUSIC_STAGE_BEGIN, 0);
                AudioManager.getInstance().stopMusic(ClientMusicCatalog.MUSIC_DEATH, 0);
            }
            operationExclusive = true;
            ClientOperationsMusicDirector.tick();
            wasInRoom = true; lastRoomId = context.roomId;
            return;
        }
        if (operationExclusive || ClientOperationsMusicDirector.active()) ClientOperationsMusicDirector.reset();
        operationExclusive = false;
        if (!context.inConquestDimension) {
            resetIntermissionAudio();
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
            if (justLeft || !intermissionAudio.isForRoom(context.roomId)) resetIntermissionAudio();
            stageBeginMusic.reset();
            deployLoopRequested = false;
            stopDeployLoop();
            stopObjectiveLoop();
            stopStrategicLoop();
            stopFinaleLoop();
        }

        if (context.maintenanceState || context.matchingState || !context.inRoom) resetIntermissionAudio();
        if (intermissionMusic.isActive() && intermissionWorld != Minecraft.getInstance().level) resetIntermissionAudio();
        if (intermissionMusic.resultFinished(ClientAudioManager.getInstance().isMusicManaged(intermissionResultTrack()))) {
            // Natural completion releases only this track; authored timeline sounds keep playing.
            deployLoopRequested = true;
            playLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 0);
        }

        updateIntermissionMusicVolume(context.roomId);

        updateDeployLoop(context);
        updateStrategicLoop(context);
        updateObjectiveLoop(context);
        updateFinaleLoop(context);
        if (context.matchingState) stageBeginMusic.reset();
        if (!intermissionAudio.isBackgroundActive() && stageBeginMusic.update(context.audioState)) {
            AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_STAGE_BEGIN, 1.0F, false);
        }

        wasInRoom = context.inRoom;
        lastRoomId = context.roomId;
    }

    static void onDeathStarted() {
        if (operationExclusive || ClientOperationsMusicDirector.active()) return;
        stopObjectiveLoop();
        AudioManager.getInstance().playMusic(ClientMusicCatalog.MUSIC_DEATH, 1.0F, false);
    }

    static void onBattalionPhase(BattalionBriefingPacket packet) {
        Object world = Minecraft.getInstance().level;
        if (intermissionWorld != null && intermissionWorld != world) resetIntermissionAudio();
        intermissionWorld = world;
        var update = intermissionAudio.accept(packet.roomId(), packet.matchId(), packet.sequenceId(),
            packet.phase(), packet.elapsedTicks(), packet.battalion(), packet.totalBattalions(), packet.attackersWon());
        if (!update.accepted()) return;
        if (packet.phase() == Phase.RESULT) ClientAudioManager.getInstance().enqueueClearOperationVoices();
        // Main BGM is owned by OperationsMusicPacket; this packet keeps only its UI announcement effects.
        switch (update.announcement()) {
            case NEXT_ASSAULT -> AudioManager.getInstance().playSound(ClientMusicCatalog.SOUND_OPERATION_NEXT_ASSAULT, 1.0F, 1.0F);
            case LAST_ASSAULT -> AudioManager.getInstance().playSound(ClientMusicCatalog.SOUND_OPERATION_LAST_ASSAULT, 1.0F, 1.0F);
            case NONE -> { }
        }
    }

    static void resetIntermissionAudio() {
        ClientAudioManager.getInstance().enqueueClearOperationVoices();
        stopIntermissionMusic(0);
        intermissionAudio.reset();
        intermissionWorld = null;
    }

    static void onDeployTravelStarted() {
        ClientOperationsMusicDirector.onDeployOpened();
        if (ClientOperationsMusicDirector.active()) return;
        if (intermissionMusic.isActive()) {
            deployLoopRequested = true;
            firstDeployOpenMusicPlayed = true;
            return;
        }
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
        ClientOperationsMusicDirector.onDeployOpened();
        if (ClientOperationsMusicDirector.active()) return;
        if (intermissionMusic.isActive()) {
            deployLoopRequested = true;
            firstDeployOpenMusicPlayed = true;
            return;
        }
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
        ClientOperationsMusicDirector.onDeployed();
        if (ClientOperationsMusicDirector.active()) return;
        stopDeployLoop();
        stopIntermissionMusic(0);
        deployLoopRequested = false;
        if (!firstDeployCompleted && wasInRoom) {
            firstDeployCompleted = true;
        }
    }

    static void onMatchEndTransitionStarted() {
        if (ClientOperationsMusicDirector.active()) return;
        resetIntermissionAudio();
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
        ClientOperationsMusicDirector.reset(); operationExclusive = false;
        resetIntermissionAudio();
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
        if (intermissionMusic.music() == OperationsIntermissionMusicCue.Music.DEPLOY) {
            if (!ClientAudioManager.getInstance().isMusicManaged(ClientMusicCatalog.LOOP_DEPLOY_SCREEN)) {
                playLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 0);
            }
            return;
        }
        boolean shouldPlay = context.inGameWorld
            && deployLoopRequested
            && ClientDeathFlowState.shouldRenderDeployBoundaries()
            && !context.maintenanceState
            && !shouldSuppressDeployLoopForFinale(context);
        if (shouldPlay) {
            playLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 0);
        } else if (ClientAudioManager.getInstance().isMusicManaged(ClientMusicCatalog.LOOP_DEPLOY_SCREEN)) {
            stopDeployLoop();
        }
    }

    private static void updateStrategicLoop(MusicContext context) {
        String desired = NONE;
        if (!deployLoopRequested && !intermissionAudio.isBackgroundActive() && !intermissionMusic.isActive()) {
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
        boolean shouldPlay = !deployLoopRequested && !intermissionAudio.isBackgroundActive() && !intermissionMusic.isActive()
            && !context.maintenanceState && shouldPlayObjectiveLoop(context);
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
        return intermissionAudio.isBackgroundActive() || intermissionMusic.isResultPlaying() || !NONE.equals(resolveDesiredFinaleLoop(context));
    }

    private static String resolveDesiredFinaleLoop(MusicContext context) {
        if (context.maintenanceState || intermissionAudio.isBackgroundActive() || intermissionMusic.isActive()) {
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

    private static String intermissionResultTrack() {
        return switch (intermissionMusic.music()) {
            case VICTORY -> ClientMusicCatalog.MUSIC_OPERATION_VICTORY;
            case DEFEAT -> ClientMusicCatalog.MUSIC_OPERATION_DEFEAT;
            default -> NONE;
        };
    }

    private static void updateIntermissionMusicVolume(String roomId) {
        if (!intermissionMusic.isResultPlaying()) return;
        float desired = intermissionMusic.volume(ClientBriefingPlayback.hasActiveSounds(roomId));
        if (desired != intermissionMusicVolume) {
            // Update only an existing instance: never restart the single-shot track to change its volume.
            AudioManager.getInstance().setMusicVolume(intermissionResultTrack(), desired);
            intermissionMusicVolume = desired;
        }
    }

    private static void stopIntermissionMusic(int fadeOutTicks) {
        if (intermissionMusic.isResultPlaying()) stopLoop(intermissionResultTrack(), fadeOutTicks);
        else if (intermissionMusic.music() == OperationsIntermissionMusicCue.Music.DEPLOY)
            stopLoop(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, fadeOutTicks);
        intermissionMusic.reset();
        intermissionMusicVolume = 1.0F;
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
