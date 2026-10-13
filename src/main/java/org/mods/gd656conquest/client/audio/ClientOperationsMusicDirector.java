package org.mods.gd656conquest.client.audio;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.room.ClientBriefingPlayback;
import org.mods.gd656conquest.common.audio.OperationsMusicCatalog;
import org.mods.gd656conquest.common.audio.OperationsMusicPositions;
import org.mods.gd656conquest.common.audio.OperationsMusicSnapshot;
import org.mods.gd656conquest.common.audio.OperationsMusicSnapshot.Mode;
import org.mods.gd656conquest.common.audio.AudioManager;

/** One primary operation BGM, outgoing fade tails, and one optional upgrade overlay. */
public final class ClientOperationsMusicDirector {
    private static OperationsMusicSnapshot snapshot;
    private static Object world;
    private static final OperationsMusicPositions positions = new OperationsMusicPositions();
    private static final List<OperationMusicSound> tails = new ArrayList<>();
    private static OperationMusicSound main, overlay;
    private static String mainId = "", stage = "", result = "", resultId = "", advance = "", roundId = "", failedId = "";
    private static boolean roundFinished, resultFinished, deployed, deployOpened;
    private static int grace, overlayGrace, playedLevel;
    private static long localTicks, acceptedAt;
    private ClientOperationsMusicDirector() {}

    public static void accept(OperationsMusicSnapshot next) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (snapshot != null && (world != mc.level || !snapshot.roomId().equals(next.roomId()) || snapshot.matchId() != next.matchId())) reset();
        if (snapshot != null && next.serverTick() < snapshot.serverTick()) return;
        boolean fresh = snapshot == null;
        snapshot = next; world = mc.level; acceptedAt = localTicks;
        if (fresh) {
            for (String id : List.of(OperationsMusicCatalog.WIN, OperationsMusicCatalog.LOSE, OperationsMusicCatalog.OVERLAY,
                OperationsMusicCatalog.finale(1), OperationsMusicCatalog.finale(2), OperationsMusicCatalog.finale(3))) {
                if (OperationMusicResources.playable(id)) OperationMusicResources.duration(id);
            }
            for (int n = 1; n <= 3; n++) {
                if (OperationMusicResources.playable(OperationsMusicCatalog.round(n))) OperationMusicResources.duration(OperationsMusicCatalog.round(n));
            }
            for (int n = 1; n <= 5; n++) {
                if (OperationMusicResources.playable(OperationsMusicCatalog.advance(n))) OperationMusicResources.duration(OperationsMusicCatalog.advance(n));
            }
        }
        if (next.mode() == Mode.PREPARATION || next.mode() == Mode.ROUND_START || next.mode() == Mode.COMBAT) {
            int selected = OperationsMusicCatalog.numbered(next.roundVariant(), 3,
                n -> OperationMusicResources.playable(OperationsMusicCatalog.round(n)));
            if (selected > 0) OperationMusicResources.duration(OperationsMusicCatalog.round(selected));
        }
    }
    static boolean ownsRoom(String roomId) { return snapshot != null && snapshot.roomId().equals(roomId); }
    static boolean active() { return snapshot != null; }

    static void tick() {
        localTicks++;
        var mc = Minecraft.getInstance();
        if (snapshot == null) return;
        if (world != mc.level || mc.player == null || !snapshot.roomId().equals(ClientHudDebugState.getRoomId())) { reset(); return; }
        var manager = mc.getSoundManager();
        tails.removeIf(sound -> {
            if (sound.isStopped() || !manager.isActive(sound)) { manager.stop(sound); return true; }
            return false;
        });
        if (overlay != null && --overlayGrace <= 0 && (overlay.isStopped() || !manager.isActive(overlay))) {
            manager.stop(overlay); overlay = null;
        }
        if (main != null && --grace <= 0 && (main.isStopped() || !manager.isActive(main))) {
            positions.save(mainId, main.position());
            main.cancel(); manager.stop(main); main = null;
            if (mainId.equals(roundId)) roundFinished = true;
            if (mainId.equals(OperationsMusicCatalog.WIN) || mainId.equals(OperationsMusicCatalog.LOSE)) resultFinished = true;
        }
        if (snapshot.mode() == Mode.RESULT || (snapshot.mode() == Mode.PREPARATION && !result.isEmpty())) {
            if (snapshot.mode() == Mode.RESULT && !result.equals(snapshot.resultKey())) {
                stopMain(0); stopTails(); stopOverlay(); stopDeployment();
                result = snapshot.resultKey(); resultFinished = deployed = deployOpened = false;
                resultId = snapshot.victory() ? OperationsMusicCatalog.WIN : OperationsMusicCatalog.LOSE;
                playedLevel = 0; failedId = "";
                ClientAudioManager.getInstance().enqueueClearOperationVoices();
            }
            if (!resultFinished && !deployed) {
                ensure(resultId, false, 0, 0, false, false, Math.max(0, snapshot.resultElapsedTicks() + localTicks - acceptedAt) / 20.0);
                if (main != null) main.duck(ClientBriefingPlayback.hasActiveSounds(snapshot.roomId()) ? .25F : 1);
            } else if (resultFinished && !deployed && !snapshot.terminal()) playDeployment();
            return;
        }
        if (snapshot.mode() == Mode.PREPARATION) {
            if (deployOpened && !deployed) playDeployment();
            return;
        }
        if (snapshot.mode() == Mode.ADVANCE) {
            if (!advance.equals(snapshot.stageKey())) {
                advance = snapshot.stageKey(); stopOverlay(); playedLevel = 0;
            }
            int selected = OperationsMusicCatalog.numbered(snapshot.sector(), 5,
                n -> OperationMusicResources.playable(OperationsMusicCatalog.advance(n)));
            if (selected > 0) ensure(OperationsMusicCatalog.advance(selected), true, 0, 0, false, false, 0);
            return;
        }
        if (!stage.equals(snapshot.stageKey())) {
            if (main == null) stopTails();
            boolean resultTrack = mainId.equals(OperationsMusicCatalog.WIN) || mainId.equals(OperationsMusicCatalog.LOSE);
            stopMain(resultTrack ? 0 : 10);
            stage = snapshot.stageKey(); positions.reset(); stopOverlay(); stopDeployment();
            roundFinished = false; result = ""; playedLevel = 0; failedId = "";
            int selected = OperationsMusicCatalog.numbered(snapshot.roundVariant(), 3,
                n -> OperationMusicResources.playable(OperationsMusicCatalog.round(n)));
            roundId = selected == 0 ? "" : OperationsMusicCatalog.round(selected);
            if (roundId.isEmpty()) roundFinished = true;
        }
        int level = snapshot.mode() == Mode.COMBAT ? snapshot.finaleLevel() : 0;
        boolean urgent = level >= 2 || (!snapshot.strongFinale() && level == 1);
        if (!roundFinished && !urgent) {
            ensure(roundId, false, 10, 0, false, false,
                Math.max(0, snapshot.serverTick() + localTicks - acceptedAt - snapshot.roundStartTick()) / 20.0);
            return;
        }
        if (urgent) roundFinished = true;
        if (level > 0) {
            boolean upgrading = level > playedLevel && level >= 2;
            if (ensure(OperationsMusicCatalog.finale(level), true, 20, 20, true, upgrading, 0)) playedLevel = level;
        } else if (playedLevel > 0) {
            stopMain(60); stopOverlay(); playedLevel = 0;
        }
        if (level == 0 && roundFinished && deployOpened && !deployed) playDeployment();
    }

    private static boolean ensure(String id, boolean loop, int outTicks, int inTicks, boolean resume, boolean upgrade, double offset) {
        if (id.isEmpty() || id.equals(failedId)) return false;
        if (main != null && id.equals(mainId)) return true;
        var duration = OperationMusicResources.duration(id);
        if (!duration.isDone()) return false;
        double seconds;
        try { seconds = duration.join(); }
        catch (RuntimeException error) {
            failedId = id;
            com.mojang.logging.LogUtils.getLogger().warn("Unable to load operation music {}", id, error);
            if (id.equals(roundId)) roundFinished = true;
            if (id.equals(OperationsMusicCatalog.WIN) || id.equals(OperationsMusicCatalog.LOSE)) resultFinished = true;
            return false;
        }
        double outgoing = main != null && mainId.startsWith("gd656conquest:music.operation.pre_eor_0") ? main.position() : 0;
        if (main != null) positions.save(mainId, main.position());
        double start = resume ? positions.resume(id, outgoing, seconds) : loop ? offset % seconds : offset;
        if (!loop && start >= seconds) {
            if (id.equals(roundId)) roundFinished = true;
            else resultFinished = true;
            return false;
        }
        stopMain(upgrade ? 6 : outTicks);
        if (outTicks == 0 && !upgrade) stopTails();
        stopDeployment();
        // A rapid return to the same track must not leave an older fading copy underneath it.
        var manager = Minecraft.getInstance().getSoundManager();
        tails.removeIf(sound -> {
            if (sound.getLocation().toString().equals(id)) { sound.cancel(); manager.stop(sound); return true; }
            return false;
        });
        main = new OperationMusicSound(id, loop, start, seconds, upgrade ? 0 : inTicks);
        for (var tail : tails) tail.waitFor(main);
        mainId = id; grace = 20;
        manager.play(main);
        if (upgrade) startOverlay();
        return true;
    }
    private static void startOverlay() {
        if (overlay != null || !OperationMusicResources.playable(OperationsMusicCatalog.OVERLAY)) return;
        overlay = new OperationMusicSound(OperationsMusicCatalog.OVERLAY, false, 0, 0, 0);
        overlayGrace = 20; Minecraft.getInstance().getSoundManager().play(overlay);
    }
    private static void stopOverlay() {
        if (overlay != null) { overlay.cancel(); Minecraft.getInstance().getSoundManager().stop(overlay); overlay = null; }
    }
    private static void stopMain(int fadeTicks) {
        if (main == null) return;
        stopTails();
        positions.save(mainId, main.position()); main.fadeOut(fadeTicks);
        if (fadeTicks > 0 && !main.isStopped()) tails.add(main);
        else Minecraft.getInstance().getSoundManager().stop(main);
        main = null;
    }
    private static void stopTails() {
        for (var sound : tails) { sound.cancel(); Minecraft.getInstance().getSoundManager().stop(sound); }
        tails.clear();
    }
    private static void stopDeployment() {
        AudioManager.getInstance().stopMusic(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 0);
        AudioManager.getInstance().stopMusic(ClientMusicCatalog.MUSIC_FIRST_DEPLOY, 0);
    }
    private static void playDeployment() {
        if (ClientAudioManager.getInstance().isMusicManaged(ClientMusicCatalog.MUSIC_FIRST_DEPLOY)) return;
        AudioManager.getInstance().playMusic(ClientMusicCatalog.LOOP_DEPLOY_SCREEN, 1, true, 0);
    }
    static void onDeployOpened() { deployOpened = true; deployed = false; }
    static void onDeployed() {
        deployed = true;
        if (snapshot == null) return;
        stopDeployment();
        if (!result.isEmpty()) stopMain(0);
    }
    static void reset() {
        stopDeployment();
        stopMain(0); stopOverlay();
        stopTails(); positions.reset();
        snapshot = null; world = null; mainId = stage = result = resultId = advance = roundId = failedId = "";
        roundFinished = resultFinished = deployed = deployOpened = false; playedLevel = 0;
    }
}
